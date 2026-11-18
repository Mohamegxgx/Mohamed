package com.example.data.ai

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.engine.WhatIfPurchaseInput
import com.example.domain.engine.WhatIfSimulatorEngine
import com.example.domain.model.AppLanguage
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TransactionType
import com.example.ui.viewmodel.MainUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object LocalOfflineFinancialAi {

    private val arabicDigitMap = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    private val compoundSplitRegex = Regex("""(?:\s+و\s+|\s*,\s*|\s*،\s*|\s+\band\b\s+|\s*\+\s*|\n+)""", RegexOption.IGNORE_CASE)
    private val leadingExpenseVerbRegex = Regex("""^(?:صرفت|دفعت|اشتريت|شريت|أكلت|طلبت|مصروف|spent|paid|bought|expense|log|record|add)\b""", RegexOption.IGNORE_CASE)
    private val leadingIncomeVerbRegex = Regex("""^(?:قبضت|استلمت|جالي|وصلني|دخل|راتب|مرتب|received|earned|salary|income|bonus)\b""", RegexOption.IGNORE_CASE)
    private val transferIntentRegex = Regex("""(?:حولت|تحويل|نقلت|سحبت|transfer|moved)\b""", RegexOption.IGNORE_CASE)
    private val questionCueRegex = Regex("""[?؟]|^(?:هل|كم|كام|إزاي|ازاي|ليه|فين|ايه|أيه|ما|متى|احسب|كم\s|how|what|why|where|can\s|calculate|analyze|حلل|اشرح)""", RegexOption.IGNORE_CASE)
    private val actionCueRegex = Regex("""(صرفت|دفعت|اشتريت|شريت|استلمت|قبضت|حولت|نقلت|سحبت|جالي|أضف|اضف|سجل|spent|paid|bought|received|transferred|log\b|record\b|add\b)""", RegexOption.IGNORE_CASE)
    private val amountPattern = Regex("""\d+(?:[.,]\d{1,2})?""")

    fun executeOfflineTurn(
        mainState: MainUiState,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        fallbackAccountId: Long?,
        priorHistory: List<AiChatMessage>,
        userPrompt: String
    ): AiAgenticTurnResult {
        val lang = mainState.preferences.language
        val trimmed = userPrompt.trim()
        val normalized = normalizeDigits(trimmed)
        val lower = normalized.lowercase()

        // 0. Check interactive teaching command ("علمني ان ...", "learn that ...")
        LocalSelfLearningEngine.parseTeachingCommand(normalized, categories, accounts)?.let { directive ->
            val catDisplay = if (lang == AppLanguage.EN) {
                directive.categoryNameEn ?: directive.preferredType.name
            } else {
                directive.categoryNameAr ?: if (directive.preferredType == TransactionType.INCOME) "دخل" else "مصروف"
            }
            val accSuffix = directive.accountName?.let { acc ->
                if (lang == AppLanguage.EN) " on account «$acc»" else " على حساب «$acc»"
            }.orEmpty()
            val reply = if (lang == AppLanguage.EN) {
                "Learned! From now on, «${directive.keyword}» will auto-classify as $catDisplay$accSuffix and train my local AI predictions."
            } else {
                "تم التعلّم وحفظ القاعدة محلياً! من الآن فصاعداً سيتم تصنيف «${directive.keyword}» تلقائياً كـ «$catDisplay»$accSuffix، وتدريب توقعاتي المستقبلية عليه."
            }
            return AiAgenticTurnResult(
                intentMode = AiChatIntentMode.ADVICE_ONLY,
                operations = emptyList(),
                coachReply = reply,
                teachingDirectives = listOf(directive)
            )
        }

        // 0.5 Memory inspection query ("إيه اللي اتعلمته؟", "what did you learn?")
        if (LocalSelfLearningEngine.isMemoryInspectionQuery(lower)) {
            val rules = merchantRules.ifEmpty { mainState.merchantRules }
            val stats = LocalSelfLearningEngine.computeMemoryStats(rules, mainState.transactions)
            val reply = LocalSelfLearningEngine.formatMemoryInspectionReply(stats, categories, lang)
            return AiAgenticTurnResult(
                intentMode = AiChatIntentMode.ADVICE_ONLY,
                operations = emptyList(),
                coachReply = reply
            )
        }

        val hasActionCue = actionCueRegex.containsMatchIn(normalized)
        val hasNumber = amountPattern.containsMatchIn(normalized)
        val isExplicitMathOrQuestion = isPureMathOrAnalyticsQuestion(normalized) ||
            evaluateWhatIfOrNull(mainState, normalized, mainState.preferences.currencyCode, lang) != null

        val proposedOps = if (fallbackAccountId != null && hasNumber && (hasActionCue || (!isExplicitMathOrQuestion && looksLikeCompactExpenseList(normalized)))) {
            parseCompoundOperations(
                normalizedPrompt = normalized,
                accounts = accounts,
                categories = categories,
                merchantRules = merchantRules,
                historicalTransactions = mainState.transactions,
                fallbackAccountId = fallbackAccountId
            )
        } else {
            emptyList()
        }

        if (proposedOps.isNotEmpty()) {
            val totalPiasters = proposedOps.sumOf { it.amountPiasters + it.feePiasters }
            val currency = mainState.preferences.currencyCode
            val totalFmt = MoneyFormatter.formatPiasters(totalPiasters, currency, lang)
            val categoryMap = categories.associateBy { it.id }

            val itemSummaries = proposedOps.joinToString("\n") { op ->
                val cat = op.categoryId?.let { categoryMap[it] }
                val catLabel = when {
                    op.type == TransactionType.TRANSFER -> if (lang == AppLanguage.EN) "Transfer" else "تحويل"
                    lang == AppLanguage.EN -> cat?.nameEn ?: "Uncategorized"
                    else -> cat?.nameAr ?: "غير مصنف"
                }
                val amt = MoneyFormatter.formatPiasters(op.amountPiasters, currency, lang)
                "• ${op.description} — $amt ($catLabel)"
            }

            val proactiveNote = buildProactiveOfflineWarning(mainState, proposedOps, categoryMap, currency, lang)
            val alsoAskedQuestion = trimmed.contains("?") || trimmed.contains("؟")
            val adviceTail = if (alsoAskedQuestion) "\n\n" + generateConversationalReply(mainState, priorHistory, trimmed) else ""
            val newNetBalance = computeProjectedNetAfterOps(mainState, proposedOps)
            val netFmt = MoneyFormatter.formatPiasters(newNetBalance, currency, lang)

            val reply = if (lang == AppLanguage.EN) {
                buildString {
                    appendLine("Recorded offline & learned (${proposedOps.size} item(s), total $totalFmt):")
                    appendLine(itemSummaries)
                    append("Estimated net balance: $netFmt.")
                    if (proactiveNote.isNotBlank()) append("\n$proactiveNote")
                    if (adviceTail.isNotBlank()) append(adviceTail)
                }
            } else {
                buildString {
                    appendLine("تم التنفيذ والتعلّم محليًا بدون إنترنت (${proposedOps.size} عملية بإجمالي $totalFmt):")
                    appendLine(itemSummaries)
                    append("رصيدك الصافي التقريبي الآن: $netFmt.")
                    if (proactiveNote.isNotBlank()) append("\n$proactiveNote")
                    if (adviceTail.isNotBlank()) append(adviceTail)
                }
            }

            return AiAgenticTurnResult(
                intentMode = if (alsoAskedQuestion) AiChatIntentMode.EXECUTE_AND_ADVISE else AiChatIntentMode.EXECUTE_ACTIONS,
                operations = proposedOps,
                coachReply = reply
            )
        }

        val reply = generateConversationalReply(mainState, priorHistory, trimmed)
        return AiAgenticTurnResult(
            intentMode = AiChatIntentMode.ADVICE_ONLY,
            operations = emptyList(),
            coachReply = reply
        )
    }

    fun generateConversationalReply(
        mainState: MainUiState,
        priorHistory: List<AiChatMessage>,
        rawPrompt: String
    ): String {
        val lang = mainState.preferences.language
        val currency = mainState.preferences.currencyCode
        val trimmed = rawPrompt.trim()
        val normalized = normalizeDigits(trimmed)
        val lower = normalized.lowercase()

        // 0. Check memory inspection query even if called via generateConversationalReply directly
        if (LocalSelfLearningEngine.isMemoryInspectionQuery(lower)) {
            val stats = LocalSelfLearningEngine.computeMemoryStats(mainState.merchantRules, mainState.transactions)
            return LocalSelfLearningEngine.formatMemoryInspectionReply(stats, mainState.categories, lang)
        }

        // 0.5 What-If quick offline installment affordability check ("لو اشتريت ب 12000 على 6 شهور")
        evaluateWhatIfOrNull(mainState, normalized, currency, lang)?.let { whatIfAnswer ->
            return whatIfAnswer
        }

        // 1. Check for financial calculations / math first
        evaluateCalculationOrNull(normalized, currency, lang)?.let { mathAnswer ->
            return mathAnswer
        }

        // 2. Greetings & conversational openers
        if (isGreetingOrSmallTalk(lower)) {
            return buildGreetingReply(mainState, lower, currency, lang)
        }

        // 3. Specific category spend query (including multi-turn short follow-ups like "والمواصلات؟" or "and food?")
        val effectiveQueryLower = resolveMultiTurnFollowUp(lower, priorHistory)
        findMentionedCategoryReply(mainState, effectiveQueryLower, currency, lang)?.let { catReply ->
            return catReply
        }

        // 4. Route based on financial topic
        return when {
            containsAny(effectiveQueryLower, "قسط", "اقساط", "أقساط", "فاليو", "كليفر", "كلفر", "أمان", "امان", "سهولة", "فرصة", "حالاً", "عجز", "ديون", "دين", "علي", "installment", "bnpl", "valu", "klivr", "aman", "souhoola", "deficit", "debt") ->
                buildBnplAndObligationsReply(mainState, currency, lang)

            containsAny(effectiveQueryLower, "رصيد", "حساب", "حسابات", "معايا كام", "معي كام", "فلوسي كام", "كاش", "بنك", "محفظ", "balance", "account", "liquid", "cash") &&
                !containsAny(effectiveQueryLower, "بتروح فين", "مصاريف", "صرفت", "where") ->
                buildBalanceReply(mainState, currency, lang)

            containsAny(effectiveQueryLower, "المسموح", "النهاردة", "اليوم", "أقدر أصرف", "اقدر اصرف", "ميزانية", "الميزانيات", "باقي كام", "يكفي", "safe", "today", "budget", "runway") ->
                buildSafeToSpendAndBudgetsReply(mainState, currency, lang)

            containsAny(effectiveQueryLower, "هدف", "أهداف", "ادخار", "أوفر", "اوفر", "توفير", "حوش", "صحة", "صحتي", "تقييم", "خطة", "goal", "saving", "save", "health", "plan", "score") ->
                buildSavingsAndHealthReply(mainState, currency, lang)

            containsAny(effectiveQueryLower, "صرفت", "مصروف", "مصاريف", "دخلي", "دخل", "فين", "عادات", "تجار", "أماكن", "اماكن", "أكتر", "اعلى", "أعلى", "تحليل", "حلل", "معاملات", "spend", "expense", "habit", "merchant", "category", "analy") ->
                buildSpendingHabitsReply(mainState, currency, lang)

            else -> buildContextualOverviewReply(mainState, priorHistory, currency, lang)
        }
    }

    fun generateOfflineActionPlan(mainState: MainUiState, lang: AppLanguage): AiActionPlan {
        val currency = mainState.preferences.currencyCode
        val m = mainState.dashboardMetrics
        val health = mainState.healthReport
        val highlights = FinancialContextFormatter.extractHabitHighlights(mainState)
        val topCatName = if (lang == AppLanguage.EN) highlights.topCategoryNameEn else highlights.topCategoryNameAr
        val safeToday = MoneyFormatter.formatPiasters(health.safeToSpendTodayPiasters, currency, lang)
        val topCatAmt = MoneyFormatter.formatPiasters(highlights.topCategoryAmountPiasters, currency, lang)
        val monthlySurplus = m.netCashFlowPiasters

        val steps = mutableListOf<AiPlanStep>()
        steps.add(
            AiPlanStep(
                title = if (lang == AppLanguage.EN) "Stick to $safeToday Daily Cap" else "التزم بحد صرف يومي $safeToday",
                impact = if (lang == AppLanguage.EN) {
                    "Keeps ${health.runwayDays} days of cash runway intact."
                } else {
                    "يحافظ على استقرار رصيدك لمدة ${health.runwayDays} يوم."
                },
                priority = "HIGH"
            )
        )
        if (!topCatName.isNullOrBlank() && highlights.topCategoryAmountPiasters > 0L) {
            val trim15 = (highlights.topCategoryAmountPiasters * 0.15).roundToLong().coerceAtLeast(100L)
            val trimFmt = MoneyFormatter.formatPiasters(trim15, currency, lang)
            steps.add(
                AiPlanStep(
                    title = if (lang == AppLanguage.EN) "Trim $topCatName by 15%" else "خفض مصاريف $topCatName بنسبة 15%",
                    impact = if (lang == AppLanguage.EN) {
                        "Frees about $trimFmt this period (from $topCatAmt)."
                    } else {
                        "يوفر حوالي $trimFmt (من أصل $topCatAmt حالياً)."
                    },
                    priority = if (highlights.exceededBudgetsCount > 0) "HIGH" else "MEDIUM"
                )
            )
        }
        if (mainState.bnplReport.deficitAnalysis.hasDeficit || mainState.installments.isNotEmpty()) {
            val pct = mainState.bnplReport.deficitAnalysis.installmentsToIncomePercent.roundToInt()
            steps.add(
                AiPlanStep(
                    title = if (lang == AppLanguage.EN) "Freeze New BNPL Purchases" else "أوقف أي تقسيط جديد مؤقتاً",
                    impact = if (lang == AppLanguage.EN) {
                        "Installments currently absorb $pct% of monthly income."
                    } else {
                        "الأقساط تستهلك حالياً $pct% من الدخل الشهري."
                    },
                    priority = "HIGH"
                )
            )
        }
        steps.add(
            AiPlanStep(
                title = if (lang == AppLanguage.EN) "Log Every Expense via Offline Chat" else "سجل كل مصروف فورياً بالشات المحلي",
                impact = if (lang == AppLanguage.EN) {
                    "Prevents untracked leaks and auto-trains merchant rules."
                } else {
                    "يمنع تسرب المصاريف الصغيرة ويدرب قواعد التصنيف تلقائياً."
                },
                priority = "LOW"
            )
        )

        val focus = when {
            highlights.exceededBudgetsCount > 0 -> if (lang == AppLanguage.EN) {
                "${highlights.exceededBudgetsCount} exceeded budget(s)"
            } else {
                "${highlights.exceededBudgetsCount} ميزانية متجاوزة للحد"
            }
            !topCatName.isNullOrBlank() -> topCatName
            else -> if (lang == AppLanguage.EN) "Daily discretionary spending" else "المصاريف اليومية المتغيرة"
        }
        val savingsOpp = if (monthlySurplus > 0L) {
            MoneyFormatter.formatPiasters((monthlySurplus * 0.25).roundToLong(), currency, lang)
        } else {
            MoneyFormatter.formatPiasters((m.totalExpensesPiasters * 0.10).roundToLong(), currency, lang)
        }

        return AiActionPlan(
            headline = if (lang == AppLanguage.EN) {
                "Offline Financial Plan • Health ${health.score}/100"
            } else {
                "خطة عمل محلية فورية • الصحة المالية ${health.score}/100"
            },
            budgetFocus = focus,
            savingsOpportunity = savingsOpp,
            steps = steps.take(4)
        )
    }

    fun analyzeOfflineReceiptText(
        textInput: String?,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        lang: AppLanguage
    ): AiExtractedTransaction {
        val raw = textInput?.trim().orEmpty()
        if (raw.isBlank()) {
            throw IllegalStateException(
                if (lang == AppLanguage.EN) {
                    "Image receipt scanning needs internet (Gemini). While offline, paste receipt or SMS text."
                } else {
                    "تحليل صور الفواتير يحتاج إنترنت، لكن يمكنك لصق نص الرسالة أو الفاتورة ليتم استخراجها أوفلاين فورًا."
                }
            )
        }
        val normalized = normalizeDigits(raw)
        val clauses = compoundSplitRegex.split(normalized)
            .map { it.trim() }
            .filter { it.isNotBlank() && amountPattern.containsMatchIn(it) }

        if (clauses.size >= 2) {
            val lineItems = mutableListOf<AiExtractedLineItem>()
            for (clause in clauses) {
                val lowerClause = clause.lowercase()
                if (lowerClause.startsWith("إجمالي") || lowerClause.startsWith("الاجمالي") ||
                    lowerClause.startsWith("total") || lowerClause.startsWith("grand total")
                ) {
                    continue
                }
                val itemParsed = VoiceTransactionParser.parse(clause, accounts, categories, merchantRules)
                val itemAmt = itemParsed.amountPiasters?.takeIf { it > 0L } ?: continue
                val cat = categories.firstOrNull { it.id == itemParsed.suggestedCategoryId }
                val catHint = if (lang == AppLanguage.EN) cat?.nameEn ?: "Other" else cat?.nameAr ?: "أخرى"
                lineItems.add(
                    AiExtractedLineItem(
                        description = itemParsed.description.ifBlank { clause.take(40) },
                        amountPiasters = itemAmt,
                        categoryHint = catHint
                    )
                )
            }
            if (lineItems.size >= 2) {
                val sumItems = lineItems.sumOf { it.amountPiasters }
                val primaryDesc = if (lang == AppLanguage.EN) {
                    "Receipt (${lineItems.size} items)"
                } else {
                    "فاتورة مجمعة (${lineItems.size} بنود)"
                }
                return AiExtractedTransaction(
                    amountPiasters = sumItems,
                    type = TransactionType.EXPENSE,
                    description = primaryDesc,
                    categoryHint = lineItems.first().categoryHint,
                    notes = if (lang == AppLanguage.EN) {
                        "Split into ${lineItems.size} items offline"
                    } else {
                        "تم تفصيل ${lineItems.size} بنود محليًا بدون إنترنت"
                    },
                    items = lineItems
                )
            }
        }

        val parsed = VoiceTransactionParser.parse(normalized, accounts, categories, merchantRules)
        val amt = parsed.amountPiasters ?: throw IllegalStateException(
            if (lang == AppLanguage.EN) "Could not detect an amount in the pasted text."
            else "لم يتم العثور على مبلغ رقمي واضح في النص."
        )
        val cat = categories.firstOrNull { it.id == parsed.suggestedCategoryId }
        val catHint = if (lang == AppLanguage.EN) cat?.nameEn ?: "Other Expenses" else cat?.nameAr ?: "مصروفات أخرى"
        return AiExtractedTransaction(
            amountPiasters = amt,
            type = parsed.type,
            description = parsed.description,
            categoryHint = catHint,
            notes = if (lang == AppLanguage.EN) "Extracted locally offline" else "تم الاستخراج محليًا بدون إنترنت"
        )
    }

    private fun parseCompoundOperations(
        normalizedPrompt: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        historicalTransactions: List<TransactionEntity>,
        fallbackAccountId: Long
    ): List<AiProposedOperation> {
        val activeAccounts = accounts.filter { !it.isArchived }
        if (activeAccounts.isEmpty()) return emptyList()

        // If the entire phrase is a single transfer command, don't split across "و" inside account names
        val clauses = if (transferIntentRegex.containsMatchIn(normalizedPrompt) && amountPattern.findAll(normalizedPrompt).count() == 1) {
            listOf(normalizedPrompt)
        } else {
            compoundSplitRegex.split(normalizedPrompt).map { it.trim() }.filter { it.isNotBlank() }
        }

        val defaultInheritedPrefix = when {
            leadingIncomeVerbRegex.containsMatchIn(normalizedPrompt) -> "قبضت "
            transferIntentRegex.containsMatchIn(normalizedPrompt) -> "حولت "
            leadingExpenseVerbRegex.containsMatchIn(normalizedPrompt) -> "صرفت "
            else -> "صرفت "
        }

        val operations = mutableListOf<AiProposedOperation>()
        for (clause in clauses) {
            if (!amountPattern.containsMatchIn(clause)) continue
            val hasOwnVerb = actionCueRegex.containsMatchIn(clause)
            val effectivePhrase = if (hasOwnVerb) clause else "$defaultInheritedPrefix$clause"
            val parsed = VoiceTransactionParser.parse(
                rawPhrase = effectivePhrase,
                accounts = activeAccounts,
                categories = categories,
                merchantRules = merchantRules,
                historicalTransactions = historicalTransactions
            )
            val amt = parsed.amountPiasters?.takeIf { it > 0L } ?: continue
            val sourceAccId = parsed.suggestedAccountId ?: fallbackAccountId
            val targetAccId = if (parsed.type == TransactionType.TRANSFER) {
                parsed.targetAccountId?.takeIf { it != sourceAccId }
            } else null
            val finalType = if (parsed.type == TransactionType.TRANSFER && targetAccId == null) {
                TransactionType.EXPENSE
            } else {
                parsed.type
            }
            val inferredCatId = if (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE) {
                parsed.suggestedCategoryId ?: VoiceTransactionParser.inferCategory(
                    description = parsed.description,
                    categories = categories,
                    merchantRules = merchantRules,
                    type = finalType,
                    historicalTransactions = historicalTransactions
                )?.id
            } else null

            operations.add(
                AiProposedOperation(
                    type = finalType,
                    amountPiasters = amt,
                    feePiasters = if (finalType == TransactionType.TRANSFER) parsed.feePiasters else 0L,
                    description = parsed.description.take(80),
                    sourceAccountId = sourceAccId,
                    targetAccountId = targetAccId,
                    categoryId = inferredCatId,
                    notes = "تسجيل وتصنيف ذاتي محلي"
                )
            )
        }
        return operations
    }

    private fun isPureMathOrAnalyticsQuestion(normalized: String): Boolean {
        if (questionCueRegex.containsMatchIn(normalized)) return true
        if (Regex("""\d+\s*[+\-*/×÷%]\s*\d+""").containsMatchIn(normalized)) return true
        return false
    }

    private fun looksLikeCompactExpenseList(normalized: String): Boolean {
        val words = normalized.split(Regex("""\s+"""))
        return words.size in 2..14 && !normalized.contains("?") && !normalized.contains("؟")
    }

    private fun evaluateCalculationOrNull(
        normalized: String,
        currency: String,
        lang: AppLanguage
    ): String? {
        // Pattern 1: X% of Y (e.g. "20% من 5000" or "15% of 4000")
        val pctOfRegex = Regex("""(\d+(?:\.\d+)?)\s*%\s*(?:من|of)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        pctOfRegex.find(normalized)?.let { match ->
            val pct = match.groupValues[1].toDoubleOrNull() ?: return@let
            val total = match.groupValues[2].toDoubleOrNull() ?: return@let
            val result = (pct / 100.0) * total
            val resPiasters = (result * 100.0).roundToLong()
            val fmt = MoneyFormatter.formatPiasters(resPiasters, currency, lang)
            return if (lang == AppLanguage.EN) {
                "Calculation: ${formatNum(pct)}% of ${formatNum(total)} = $fmt."
            } else {
                "نتيجة الحساب: ${formatNum(pct)}% من ${formatNum(total)} تساوي $fmt."
            }
        }

        // Pattern 2: ratio "نسبة X من Y"
        val ratioRegex = Regex("""(?:نسبة|ratio)\s+(\d+(?:\.\d+)?)\s*(?:من|إلى|الى|to|of|from)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        ratioRegex.find(normalized)?.let { match ->
            val part = match.groupValues[1].toDoubleOrNull() ?: return@let
            val whole = match.groupValues[2].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return@let
            val pct = (part / whole) * 100.0
            return if (lang == AppLanguage.EN) {
                "Ratio calculation: ${formatNum(part)} out of ${formatNum(whole)} is ${MoneyFormatter.formatPercent(pct)}."
            } else {
                "النسبة الحسابية: ${formatNum(part)} من ${formatNum(whole)} تمثل ${MoneyFormatter.formatPercent(pct)}."
            }
        }

        // Pattern 3: Pure or embedded arithmetic expression e.g. "50 + 20 * 3" or "(1200 - 350) / 5"
        val exprCandidate = extractArithmeticExpression(normalized)
        if (exprCandidate != null) {
            val result = evaluateArithmetic(exprCandidate)
            if (result != null && !result.isNaN() && !result.isInfinite()) {
                val resFormatted = formatNum(result)
                val asMoney = MoneyFormatter.formatPiasters((result * 100.0).roundToLong(), currency, lang)
                return if (lang == AppLanguage.EN) {
                    "Offline calculation: $exprCandidate = $resFormatted ($asMoney)."
                } else {
                    "الحساب المحلي: $exprCandidate = $resFormatted (يعادل $asMoney)."
                }
            }
        }
        return null
    }

    private fun extractArithmeticExpression(input: String): String? {
        val cleaned = input.replace('×', '*').replace('÷', '/').replace('٫', '.')
        val mathRegex = Regex("""[\d.()\s+\-*/]*\d[\d.\s]*[+\-*/][\d.()\s+\-*/]*\d[\d.\s)]*""")
        val match = mathRegex.find(cleaned)?.value?.trim() ?: return null
        if (!match.any { it == '+' || it == '-' || it == '*' || it == '/' }) return null
        return match.replace(Regex("""\s+"""), " ")
    }

    private class ArithmeticEvaluator(private val compact: String) {
        private var pos = 0

        fun evaluate(): Double? = runCatching {
            val res = parseExpression()
            if (pos != compact.length) null else res
        }.getOrNull()

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (pos < compact.length) {
                when (compact[pos]) {
                    '+' -> { pos++; x += parseTerm() }
                    '-' -> { pos++; x -= parseTerm() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (pos < compact.length) {
                when (compact[pos]) {
                    '*' -> { pos++; x *= parseFactor() }
                    '/' -> {
                        pos++
                        val d = parseFactor()
                        if (abs(d) < 1e-12) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): Double {
            if (pos < compact.length && (compact[pos] == '+' || compact[pos] == '-')) {
                val neg = compact[pos] == '-'
                pos++
                val v = parseFactor()
                return if (neg) -v else v
            }
            if (pos < compact.length && compact[pos] == '(') {
                pos++
                val v = parseExpression()
                if (pos < compact.length && compact[pos] == ')') pos++
                return v
            }
            val start = pos
            while (pos < compact.length && (compact[pos].isDigit() || compact[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Unexpected token")
            return compact.substring(start, pos).toDouble()
        }
    }

    private fun evaluateArithmetic(expr: String): Double? {
        return ArithmeticEvaluator(expr.replace(" ", "")).evaluate()
    }

    private fun isGreetingOrSmallTalk(lower: String): Boolean {
        val tokens = listOf("اهلا", "أهلا", "السلام", "ازيك", "إزيك", "عامل ايه", "صباح", "مساء", "مرحبا", "هاي", "هالو", "شكرا", "شكراً", "مين انت", "hello", "hi", "hey", "thanks", "thank you", "who are you")
        return tokens.any { lower == it || lower.startsWith("$it ") || (lower.length < 32 && lower.contains(it)) }
    }

    private fun buildGreetingReply(mainState: MainUiState, lower: String, currency: String, lang: AppLanguage): String {
        val netFmt = MoneyFormatter.formatPiasters(mainState.dashboardMetrics.netBalancePiasters, currency, lang)
        val safeFmt = MoneyFormatter.formatPiasters(mainState.healthReport.safeToSpendTodayPiasters, currency, lang)
        if (lower.contains("شكر") || lower.contains("thank")) {
            return if (lang == AppLanguage.EN) {
                "You're always welcome! Remember you can log expenses or ask about your balances and budgets anytime—even completely offline."
            } else {
                "العفو في أي وقت! أنا معاك دايمًا حتى بدون إنترنت لتسجيل مصاريفك، الحسابات السريعة، ومراجعة ميزانيتك أول بأول."
            }
        }
        return if (lang == AppLanguage.EN) {
            "Hello! I'm your local offline & cloud financial assistant.\n• Current net balance: $netFmt\n• Safe-to-Spend Today: $safeFmt\nEven offline, you can ask me to log expenses (e.g. \"Spent 50 food and 20 transport\"), run calculations, or check your budgets & installments."
        } else {
            "أهلاً بك! أنا مساعدك المالي المحلي والسحابي.\n• صافي رصيدك الحالي: $netFmt\n• المسموح لصرف اليوم: $safeFmt\nحتى بدون إنترنت، أقدر أسجل مصاريفك تلقائياً (مثلاً: «صرفت 50 اكل و 20 مواصلات»)، أحسب أي أرقام أو نسب، وأجاوبك من بياناتك الفعلية!"
        }
    }

    private fun findMentionedCategoryReply(
        mainState: MainUiState,
        lower: String,
        currency: String,
        lang: AppLanguage
    ): String? {
        if (!containsAny(lower, "كام", "مصاريف", "صرفت", "مجموع", "فئة", "how much", "spent on", "total")) return null
        val matchedCat = mainState.categories.firstOrNull { cat ->
            lower.contains(cat.nameAr.lowercase()) ||
                lower.contains(cat.nameEn.lowercase()) ||
                (cat.nameEn == "Food & Drinks" && containsAny(lower, "اكل", "أكل", "طعام", "مطعم", "كافيه", "food", "coffee")) ||
                (cat.nameEn == "Transport" && containsAny(lower, "مواصلات", "اوبر", "أوبر", "بنزين", "transport", "uber")) ||
                (cat.nameEn == "Shopping" && containsAny(lower, "تسوق", "اونلاين", "أونلاين", "ملابس", "shopping")) ||
                (cat.nameEn == "Bills & Utilities" && containsAny(lower, "فاتورة", "فواتير", "كهرباء", "انترنت", "نت", "bills"))
        } ?: return null

        val catTxs = mainState.transactions.filter { it.categoryId == matchedCat.id }
        val total = catTxs.sumOf { it.amountPiasters }
        val totalFmt = MoneyFormatter.formatPiasters(total, currency, lang)
        val catName = if (lang == AppLanguage.EN) matchedCat.nameEn else matchedCat.nameAr
        val slice = mainState.dashboardMetrics.categoryBreakdown.firstOrNull { it.categoryId == matchedCat.id }
        val shareText = slice?.let { " (${MoneyFormatter.formatPercent(it.percentage)})" }.orEmpty()

        return if (lang == AppLanguage.EN) {
            "Total in $catName: $totalFmt across ${catTxs.size} transaction(s)$shareText."
        } else {
            "إجمالي معاملات فئة «$catName»: $totalFmt خلال ${catTxs.size} معاملة$shareText."
        }
    }

    private fun buildBalanceReply(mainState: MainUiState, currency: String, lang: AppLanguage): String {
        val active = mainState.accounts.filter { !it.isArchived }
        val netFmt = MoneyFormatter.formatPiasters(mainState.dashboardMetrics.netBalancePiasters, currency, lang)
        val availFmt = MoneyFormatter.formatPiasters(mainState.dashboardMetrics.availableFundsPiasters, currency, lang)
        val lines = active.joinToString("\n") { acc ->
            val bal = MoneyFormatter.formatPiasters(acc.currentBalancePiasters, acc.currency, lang)
            "• ${acc.name}: $bal"
        }.ifBlank { if (lang == AppLanguage.EN) "• No active accounts" else "• لا توجد حسابات نشطة" }

        return if (lang == AppLanguage.EN) {
            "Your live balances (offline Room DB):\n$lines\n\nNet Balance: $netFmt\nAvailable Liquid Funds (after upcoming dues): $availFmt."
        } else {
            "أرصدتك الحالية من قاعدة البيانات:\n$lines\n\n• صافي الرصيد الإجمالي: $netFmt\n• السيولة المتاحة (بعد خصم المستحقات القريبة): $availFmt."
        }
    }

    private fun buildSpendingHabitsReply(mainState: MainUiState, currency: String, lang: AppLanguage): String {
        val m = mainState.dashboardMetrics
        val highlights = FinancialContextFormatter.extractHabitHighlights(mainState)
        val incFmt = MoneyFormatter.formatPiasters(m.totalIncomePiasters, currency, lang)
        val expFmt = MoneyFormatter.formatPiasters(m.totalExpensesPiasters, currency, lang)
        val todayFmt = MoneyFormatter.formatPiasters(mainState.todayExpensePiasters, currency, lang)
        val dailyAvgFmt = MoneyFormatter.formatPiasters(highlights.dailyAverageSpendPiasters, currency, lang)

        if (mainState.transactions.isEmpty()) {
            return if (lang == AppLanguage.EN) {
                "Your ledger has no transactions yet. Try sending \"Spent 50 food and 20 transport\" and I will record and categorize them right away—even offline!"
            } else {
                "لا توجد معاملات مسجلة بعد في سجلّك. اكتب لي مثلاً: «صرفت 50 اكل و 20 مواصلات» وسأسجلها وأصنفها لك فورًا حتى بدون إنترنت!"
            }
        }

        val topCats = m.categoryBreakdown.take(3).joinToString("\n") { slice ->
            val name = if (lang == AppLanguage.EN) slice.nameEn else slice.nameAr
            val amt = MoneyFormatter.formatPiasters(slice.amountPiasters, currency, lang)
            "• $name: $amt (${slice.percentage.roundToInt()}%)"
        }.ifBlank { "-" }

        val topMerchants = highlights.topMerchants.take(3).joinToString(" · ") { stat ->
            "${stat.label} (${stat.txCount}x • ${MoneyFormatter.formatPiasters(stat.totalPiasters, currency, lang)})"
        }

        return if (lang == AppLanguage.EN) {
            buildString {
                appendLine("Spending & Habits Summary (Offline Calculation):")
                appendLine("• Period Income: $incFmt | Expenses: $expFmt")
                appendLine("• Spent Today: $todayFmt | Daily Average: $dailyAvgFmt")
                appendLine("Top Categories:")
                appendLine(topCats)
                if (topMerchants.isNotBlank()) append("Frequent Habits/Merchants: $topMerchants")
            }.trim()
        } else {
            buildString {
                appendLine("ملخص مصاريفك وعاداتك (حساب محلي فوري):")
                appendLine("• الدخل للفترة: $incFmt | المصروفات: $expFmt")
                appendLine("• مصروف اليوم: $todayFmt | المتوسط اليومي: $dailyAvgFmt")
                appendLine("أعلى فئات الصرف:")
                appendLine(topCats)
                if (topMerchants.isNotBlank()) append("أكثر الأماكن/العادات تكراراً: $topMerchants")
            }.trim()
        }
    }

    private fun buildSafeToSpendAndBudgetsReply(mainState: MainUiState, currency: String, lang: AppLanguage): String {
        val health = mainState.healthReport
        val safeFmt = MoneyFormatter.formatPiasters(health.safeToSpendTodayPiasters, currency, lang)
        val remBudgetFmt = MoneyFormatter.formatPiasters(mainState.dashboardMetrics.remainingBudgetPiasters, currency, lang)
        val budgetsList = mainState.budgetEvaluations.take(4).joinToString("\n") { eval ->
            val rem = MoneyFormatter.formatPiasters(eval.remainingPiasters, currency, lang)
            val statusLabel = when {
                eval.isExceeded -> if (lang == AppLanguage.EN) "EXCEEDED" else "متجاوزة"
                eval.isWarning80 -> if (lang == AppLanguage.EN) "Near Limit" else "قريبة من الحد"
                else -> if (lang == AppLanguage.EN) "OK" else "آمنة"
            }
            "• ${eval.name}: ${eval.usagePercent.roundToInt()}% ($statusLabel • ${if (lang == AppLanguage.EN) "left" else "المتبقي"} $rem)"
        }

        return if (lang == AppLanguage.EN) {
            buildString {
                appendLine("Safe-to-Spend & Budgets:")
                appendLine("• Safe-to-Spend Today: $safeFmt (${health.daysRemainingInMonth} days left in month)")
                appendLine("• Cash Runway: ${health.runwayDays} days")
                if (budgetsList.isNotBlank()) {
                    appendLine("• Remaining Budget Total: $remBudgetFmt")
                    append(budgetsList)
                } else {
                    append("No monthly budgets configured yet—add one in Planning to track limits automatically.")
                }
            }.trim()
        } else {
            buildString {
                appendLine("تحليل المسموح اليوم والميزانيات:")
                appendLine("• المسموح لصرف اليوم: $safeFmt (باقي ${health.daysRemainingInMonth} يوم في الشهر)")
                appendLine("• الرصيد يكفي نظرياً لمدة: ${health.runwayDays} يوم")
                if (budgetsList.isNotBlank()) {
                    appendLine("• إجمالي المتبقي بالميزانيات: $remBudgetFmt")
                    append(budgetsList)
                } else {
                    append("لا توجد ميزانيات مضافة بعد؛ يمكنك إضافة ميزانية شهرية من قسم الالتزامات.")
                }
            }.trim()
        }
    }

    private fun buildBnplAndObligationsReply(mainState: MainUiState, currency: String, lang: AppLanguage): String {
        val bnpl = mainState.bnplReport
        val deficit = bnpl.deficitAnalysis
        val activeInstallments = mainState.installments.filter { it.status == InstallmentStatus.ACTIVE }
        val activeDebts = mainState.debts.filter { it.status != DebtStatus.SETTLED }
        val surplusFmt = MoneyFormatter.formatPiasters(deficit.netMonthlySurplusOrDeficitPiasters, currency, lang, showSign = true)
        val interestFmt = MoneyFormatter.formatPiasters(deficit.monthlyBnplInterestCostPiasters, currency, lang)
        val due7dFmt = MoneyFormatter.formatPiasters(mainState.dashboardMetrics.amountsDuePiasters, currency, lang)
        val owedByMe = activeDebts.filter { it.direction == DebtDirection.OWED_BY_ME }.sumOf { (it.totalAmountPiasters - it.paidAmountPiasters).coerceAtLeast(0L) }
        val owedFmt = MoneyFormatter.formatPiasters(owedByMe, currency, lang)

        val instLines = activeInstallments.take(4).joinToString("\n") { inst ->
            val remCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0)
            val amt = MoneyFormatter.formatPiasters(inst.installmentAmountPiasters, currency, lang)
            "• ${inst.name} (${inst.creditor}): $amt — ${inst.paidCount}/${inst.totalCount} (${if (lang == AppLanguage.EN) "$remCount left" else "باقي $remCount"})"
        }

        return if (lang == AppLanguage.EN) {
            buildString {
                appendLine("Installments, BNPL & Deficit Check:")
                appendLine("• Status: ${deficit.headlineEn} (Net Monthly Balance: $surplusFmt)")
                appendLine("• Installments-to-Income Ratio: ${deficit.installmentsToIncomePercent.roundToInt()}% | Est. Monthly Interest: $interestFmt")
                appendLine("• Due in 7 days: $due7dFmt | Open Debts You Owe: $owedFmt")
                if (instLines.isNotBlank()) append("Active Plans:\n$instLines")
            }.trim()
        } else {
            buildString {
                appendLine("تحليل الأقساط وبرامج التقسيط والعجز:")
                appendLine("• الوضع: ${deficit.headlineAr} (الصافي الشهري: $surplusFmt)")
                appendLine("• نسبة الأقساط من الدخل: ${deficit.installmentsToIncomePercent.roundToInt()}% | الفائدة الشهرية التقريبية: $interestFmt")
                appendLine("• مستحق خلال 7 أيام: $due7dFmt | ديون مستحقة عليك: $owedFmt")
                if (instLines.isNotBlank()) append("الأقساط النشطة:\n$instLines")
            }.trim()
        }
    }

    private fun buildSavingsAndHealthReply(mainState: MainUiState, currency: String, lang: AppLanguage): String {
        val health = mainState.healthReport
        val goalsLines = mainState.savingsGoals.take(4).joinToString("\n") { goal ->
            val pct = if (goal.targetAmountPiasters > 0L) {
                ((goal.savedAmountPiasters.toDouble() / goal.targetAmountPiasters.toDouble()) * 100.0).roundToInt()
            } else 0
            val savedFmt = MoneyFormatter.formatPiasters(goal.savedAmountPiasters, currency, lang)
            val targetFmt = MoneyFormatter.formatPiasters(goal.targetAmountPiasters, currency, lang)
            "• ${goal.name}: $savedFmt / $targetFmt ($pct%)"
        }
        return if (lang == AppLanguage.EN) {
            buildString {
                appendLine("Financial Health: ${health.score}/100 (${health.grade.name})")
                appendLine("Coach Tip: ${health.adviceEn}")
                if (goalsLines.isNotBlank()) append("Savings Goals:\n$goalsLines")
            }.trim()
        } else {
            buildString {
                appendLine("مؤشر صحتك المالية: ${health.score}/100")
                appendLine("النصيحة الذكية: ${health.adviceAr}")
                if (goalsLines.isNotBlank()) append("أهداف الادخار:\n$goalsLines")
            }.trim()
        }
    }

    private fun buildContextualOverviewReply(
        mainState: MainUiState,
        priorHistory: List<AiChatMessage>,
        currency: String,
        lang: AppLanguage
    ): String {
        val m = mainState.dashboardMetrics
        val health = mainState.healthReport
        val netFmt = MoneyFormatter.formatPiasters(m.netBalancePiasters, currency, lang)
        val expFmt = MoneyFormatter.formatPiasters(m.totalExpensesPiasters, currency, lang)
        val incFmt = MoneyFormatter.formatPiasters(m.totalIncomePiasters, currency, lang)
        val safeFmt = MoneyFormatter.formatPiasters(health.safeToSpendTodayPiasters, currency, lang)
        val hasPrior = priorHistory.any { !it.isError }

        return if (lang == AppLanguage.EN) {
            buildString {
                if (hasPrior) appendLine("Continuing our offline financial check:")
                appendLine("• Net Balance: $netFmt | Safe Today: $safeFmt")
                appendLine("• Period Income: $incFmt | Period Expenses: $expFmt")
                appendLine("• Financial Health Score: ${health.score}/100")
                append("Tip: You can ask me specific math (e.g. \"20% of 5000\"), log transactions (e.g. \"Spent 65 coffee\"), or inspect your budgets and BNPL plans offline.")
            }.trim()
        } else {
            buildString {
                if (hasPrior) appendLine("استكمالاً لمحادثتنا محليًا بدون إنترنت:")
                appendLine("• صافي الرصيد: $netFmt | المسموح اليوم: $safeFmt")
                appendLine("• الدخل للفترة: $incFmt | المصروفات: $expFmt")
                appendLine("• الصحة المالية: ${health.score}/100")
                append("يمكنك تجربة: كتابة مصروف لتسجيله («صرفت 65 قهوة»)، أو سؤال حسابي («15% من 8000» أو «1200 / 4»)، أو السؤال عن الأقساط والميزانية.")
            }.trim()
        }
    }

    private fun resolveMultiTurnFollowUp(
        lower: String,
        priorHistory: List<AiChatMessage>
    ): String {
        val lastUserText = priorHistory.lastOrNull { it.isUser }?.text?.lowercase().orEmpty()
        if (lastUserText.isBlank()) return lower
        val isShortFollowUp = lower.length <= 28 && (lower.startsWith("و") || lower.startsWith("طب") || lower.startsWith("and ") || lower.startsWith("what about"))
        return if (isShortFollowUp && containsAny(lastUserText, "كام", "صرفت", "مصاريف", "how much", "spent")) {
            "$lower صرفت كام"
        } else {
            lower
        }
    }

    private fun evaluateWhatIfOrNull(
        mainState: MainUiState,
        normalized: String,
        currency: String,
        lang: AppLanguage
    ): String? {
        val lower = normalized.lowercase()
        val hasScenarioCue = containsAny(lower, "لو اشتريت", "لو شريت", "لو قسط", "اقسط", "تقسيط", "على كام شهر", "what if", "installment plan", "buy on")
        if (!hasScenarioCue) return null
        val nums = Regex("""(\d+(?:\.\d{1,2})?)""").findAll(normalized).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (nums.isEmpty()) return null
        val price = nums.maxOrNull()?.takeIf { it > 0.0 } ?: return null
        val monthsMatch = Regex("""(\d{1,2})\s*(?:شهر|شهور|أشهر|months?)""", RegexOption.IGNORE_CASE).find(normalized)
        val months = monthsMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 60) ?: 12
        val aprMatch = Regex("""(\d+(?:\.\d+)?)\s*%""").find(normalized)
        val apr = aprMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0

        val sim = WhatIfSimulatorEngine.simulatePurchase(
            input = WhatIfPurchaseInput(
                cashPricePiasters = (price * 100.0).roundToLong(),
                months = months,
                annualInterestRatePercent = apr
            ),
            bnplReport = mainState.bnplReport,
            metrics = mainState.dashboardMetrics,
            healthReport = mainState.healthReport,
            currencyCode = currency
        )
        val instFmt = MoneyFormatter.formatPiasters(sim.monthlyInstallmentPiasters, currency, lang)
        val safeAfterFmt = MoneyFormatter.formatPiasters(sim.projectedSafeToSpendDailyPiasters, currency, lang)
        return if (lang == AppLanguage.EN) {
            "Offline What-If Simulation ($months months):\n• ${sim.verdictEn}\n• Monthly Installment: $instFmt\n• New Daily Safe-to-Spend: $safeAfterFmt\n• ${sim.adviceEn}"
        } else {
            "محاكاة «ماذا لو» فورية ($months شهر):\n• التقييم: ${sim.verdictAr}\n• القسط الشهري: $instFmt\n• المسموح اليومي بعد القسط: $safeAfterFmt\n• ${sim.adviceAr}"
        }
    }

    private fun buildProactiveOfflineWarning(
        mainState: MainUiState,
        proposedOps: List<AiProposedOperation>,
        categoryMap: Map<Long, CategoryEntity>,
        currency: String,
        lang: AppLanguage
    ): String {
        val expenseAddedByCat = proposedOps
            .filter { it.type == TransactionType.EXPENSE && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { it.amountPiasters } }

        for ((catId, added) in expenseAddedByCat) {
            val budgetEval = mainState.budgetEvaluations.firstOrNull { it.categoryId == catId }
            if (budgetEval != null && budgetEval.limitPiasters > 0L) {
                val projectedUsed = budgetEval.spentPiasters + added
                val pct = ((projectedUsed.toDouble() / budgetEval.limitPiasters.toDouble()) * 100.0).roundToInt()
                if (pct >= 80) {
                    val catName = if (lang == AppLanguage.EN) categoryMap[catId]?.nameEn ?: budgetEval.name else categoryMap[catId]?.nameAr ?: budgetEval.name
                    val rem = (budgetEval.limitPiasters - projectedUsed).coerceAtLeast(0L)
                    val remFmt = MoneyFormatter.formatPiasters(rem, currency, lang)
                    return if (lang == AppLanguage.EN) {
                        "Budget Alert: «$catName» reached $pct% of monthly limit (left: $remFmt)."
                    } else {
                        "تنبيه ذكي: ميزانية «$catName» وصلت إلى $pct% من الحد الشهري (المتبقي: $remFmt)."
                    }
                }
            }
        }

        val addedTodayExpense = proposedOps.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPiasters }
        val dailyCap = mainState.healthReport.safeToSpendTodayPiasters
        if (addedTodayExpense > 0L && dailyCap > 0L && (mainState.todayExpensePiasters + addedTodayExpense) > dailyCap) {
            val capFmt = MoneyFormatter.formatPiasters(dailyCap, currency, lang)
            return if (lang == AppLanguage.EN) {
                "Pace Notice: Today's expenses exceed your Safe-to-Spend cap ($capFmt)."
            } else {
                "تنبيه وتيرة الصرف: مصروفات اليوم تجاوزت المسموح اليومي الموصى به ($capFmt)."
            }
        }
        return ""
    }

    private fun computeProjectedNetAfterOps(mainState: MainUiState, ops: List<AiProposedOperation>): Long {
        var net = mainState.dashboardMetrics.netBalancePiasters
        ops.forEach { op ->
            when (op.type) {
                TransactionType.INCOME -> net += op.amountPiasters
                TransactionType.EXPENSE -> net -= op.amountPiasters
                TransactionType.TRANSFER -> net -= op.feePiasters
                else -> {}
            }
        }
        return net
    }

    private fun containsAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(it) }

    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(arabicDigitMap[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun formatNum(value: Double): String {
        val rounded = value.roundToLong()
        return if (abs(value - rounded.toDouble()) < 1e-6) {
            rounded.toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }
}
