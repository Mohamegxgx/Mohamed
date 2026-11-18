package com.example.data.ai

import android.graphics.Bitmap
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.TeachingDirective
import com.example.domain.model.AppLanguage
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType
import com.example.ui.viewmodel.MainUiState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class AiExecutedActionRecord(
    val transactionId: Long,
    val feeTransactionId: Long? = null,
    val type: TransactionType,
    val amountPiasters: Long,
    val feePiasters: Long = 0L,
    val description: String,
    val categoryNameAr: String? = null,
    val categoryNameEn: String? = null,
    val categoryColorHex: String? = null,
    val accountName: String,
    val createdNewCategory: Boolean = false
)

object AiEngineSource {
    const val CLOUD_GEMINI = "CLOUD_GEMINI"
    const val LOCAL_OFFLINE = "LOCAL_OFFLINE"
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val executedActions: List<AiExecutedActionRecord> = emptyList(),
    val isUndone: Boolean = false,
    val engineSource: String = AiEngineSource.CLOUD_GEMINI,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class AiChatIntentMode {
    EXECUTE_ACTIONS,
    ADVICE_ONLY,
    EXECUTE_AND_ADVISE
}

data class AiProposedOperation(
    val type: TransactionType,
    val amountPiasters: Long,
    val feePiasters: Long = 0L,
    val description: String,
    val sourceAccountId: Long,
    val targetAccountId: Long? = null,
    val categoryId: Long? = null,
    val newCategoryNameAr: String? = null,
    val newCategoryNameEn: String? = null,
    val newCategoryColorHex: String? = null,
    val notes: String = ""
)

data class AiAgenticTurnResult(
    val intentMode: AiChatIntentMode,
    val operations: List<AiProposedOperation>,
    val coachReply: String,
    val teachingDirectives: List<TeachingDirective> = emptyList()
)

data class AiTransactionCategoryAssignment(
    val transactionId: Long,
    val categoryId: Long,
    val confidencePercent: Int = 90
)

data class AiPlanStep(
    val title: String,
    val impact: String,
    val priority: String
)

data class AiActionPlan(
    val headline: String,
    val budgetFocus: String,
    val savingsOpportunity: String,
    val steps: List<AiPlanStep>
)

data class AiExtractedLineItem(
    val description: String,
    val amountPiasters: Long,
    val categoryHint: String = ""
)

data class AiExtractedTransaction(
    val amountPiasters: Long,
    val type: TransactionType,
    val description: String,
    val categoryHint: String,
    val notes: String,
    val items: List<AiExtractedLineItem> = emptyList()
)

data class AiAutoFillResult(
    val type: TransactionType,
    val amountPiasters: Long?,
    val feePiasters: Long = 0L,
    val sourceAccountId: Long? = null,
    val targetAccountId: Long? = null,
    val categoryId: Long? = null,
    val description: String = "",
    val notes: String = "",
    val confidencePercent: Int = 90
)

data class AiParsedBankMessage(
    val isFinancial: Boolean,
    val operationKind: MessageOperationKind,
    val mappedType: TransactionType,
    val amountPiasters: Long,
    val feePiasters: Long = 0L,
    val currency: String = "EGP",
    val sourceName: String = "AI Reader",
    val counterpartyName: String? = null,
    val referenceNumber: String? = null,
    val balanceAfterPiasters: Long? = null,
    val suggestedAccountId: Long? = null,
    val targetAccountId: Long? = null,
    val suggestedCategoryId: Long? = null,
    val summary: String = "",
    val confidenceScore: Float = 0.9f
)

data class GeminiConnectionResult(
    val isConnected: Boolean,
    val modelName: String,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

interface FinancialAiGateway {
    suspend fun verifyConnection(language: AppLanguage = AppLanguage.AR): GeminiConnectionResult =
        GeminiConnectionResult(
            isConnected = true,
            modelName = FirebaseGeminiFinancialGateway.MODEL_NAME,
            latencyMs = 0L
        )

    fun streamFinancialAdvice(
        financialContext: String,
        history: List<AiChatMessage>,
        userPrompt: String,
        language: AppLanguage
    ): Flow<String>

    suspend fun generateActionPlan(
        financialContext: String,
        language: AppLanguage
    ): AiActionPlan

    suspend fun analyzeReceiptOrMessage(
        financialContext: String,
        textInput: String?,
        bitmap: Bitmap?,
        language: AppLanguage
    ): AiExtractedTransaction

    suspend fun smartAutoFillTransaction(
        phrase: String,
        accountsContext: String,
        categoriesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        primaryCurrency: String,
        language: AppLanguage
    ): AiAutoFillResult

    suspend fun analyzeFinancialMessagesWithAi(
        sender: String,
        rawMessageBlock: String,
        accountsContext: String,
        categoriesContext: String,
        sourcesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        language: AppLanguage
    ): List<AiParsedBankMessage>

    suspend fun executeAgenticChatTurn(
        financialContext: String,
        accountsContext: String,
        categoriesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        fallbackAccountId: Long,
        history: List<AiChatMessage>,
        userPrompt: String,
        primaryCurrency: String,
        language: AppLanguage
    ): AiAgenticTurnResult? = null

    suspend fun autoCategorizeTransactions(
        transactionsSummary: String,
        categoriesContext: String,
        validTransactionIds: Set<Long>,
        validCategoryIds: Set<Long>,
        language: AppLanguage
    ): List<AiTransactionCategoryAssignment> = emptyList()
}

data class MerchantHabitStat(
    val label: String,
    val txCount: Int,
    val totalPiasters: Long,
    val avgPiasters: Long
)

data class RoomHabitHighlights(
    val totalTransactionsCount: Int,
    val activeAccountsCount: Int,
    val topCategoryNameAr: String?,
    val topCategoryNameEn: String?,
    val topCategoryAmountPiasters: Long,
    val topCategorySharePercent: Double,
    val dailyAverageSpendPiasters: Long,
    val topMerchants: List<MerchantHabitStat>,
    val exceededBudgetsCount: Int,
    val warningBudgetsCount: Int,
    val hasDeficitRisk: Boolean
)

object FinancialContextFormatter {

    fun extractHabitHighlights(state: MainUiState): RoomHabitHighlights {
        val expenseTxs = state.transactions.filter {
            it.type == TransactionType.EXPENSE && it.description.isNotBlank()
        }
        val groupedMerchants = expenseTxs.groupBy {
            it.description.trim().lowercase(java.util.Locale.ROOT)
        }.map { (_, group) ->
            val displayLabel = group
                .groupBy { it.description.trim() }
                .maxByOrNull { it.value.size }
                ?.key ?: group.first().description.trim()
            val total = group.sumOf { it.amountPiasters }
            val count = group.size
            MerchantHabitStat(
                label = displayLabel,
                txCount = count,
                totalPiasters = total,
                avgPiasters = if (count > 0) total / count else 0L
            )
        }.sortedWith(compareByDescending<MerchantHabitStat> { it.totalPiasters }.thenByDescending { it.txCount })
            .take(6)

        val topCat = state.dashboardMetrics.categoryBreakdown.firstOrNull()
        val exceededCount = state.budgetEvaluations.count { it.isExceeded }
        val warningCount = state.budgetEvaluations.count { !it.isExceeded && it.isWarning80 }

        return RoomHabitHighlights(
            totalTransactionsCount = state.transactions.size,
            activeAccountsCount = state.accounts.count { !it.isArchived },
            topCategoryNameAr = topCat?.nameAr,
            topCategoryNameEn = topCat?.nameEn,
            topCategoryAmountPiasters = topCat?.amountPiasters ?: 0L,
            topCategorySharePercent = topCat?.percentage ?: 0.0,
            dailyAverageSpendPiasters = state.analyticsSummary.averageDailySpendPiasters,
            topMerchants = groupedMerchants,
            exceededBudgetsCount = exceededCount,
            warningBudgetsCount = warningCount,
            hasDeficitRisk = state.bnplReport.deficitAnalysis.hasDeficit
        )
    }

    fun buildSnapshotPrompt(state: MainUiState): String {
        val m = state.dashboardMetrics
        val health = state.healthReport
        val analytics = state.analyticsSummary
        val currency = state.preferences.currencyCode
        val highlights = extractHabitHighlights(state)
        val accountMap = state.accounts.associateBy { it.id }
        val categoryMap = state.categories.associateBy { it.id }
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        val accountsSummary = state.accounts
            .filter { !it.isArchived }
            .take(8)
            .joinToString("; ") { acc ->
                "${acc.name}(${acc.type.name})=${MoneyFormatter.formatPiasters(acc.currentBalancePiasters, acc.currency, AppLanguage.EN)}"
            }
            .ifBlank { "No active accounts" }

        val topCategories = m.categoryBreakdown
            .take(8)
            .joinToString("; ") { slice ->
                val catName = if (state.preferences.language == AppLanguage.EN) slice.nameEn else "${slice.nameEn}/${slice.nameAr}"
                "$catName=${MoneyFormatter.formatPiasters(slice.amountPiasters, currency, AppLanguage.EN)} (${slice.percentage.roundToInt()}%)"
            }
            .ifBlank { "No expenses recorded yet" }

        val topMerchantsSummary = highlights.topMerchants
            .joinToString("; ") { stat ->
                "${stat.label}(count=${stat.txCount}, total=${MoneyFormatter.formatPiasters(stat.totalPiasters, currency, AppLanguage.EN)}, avg=${MoneyFormatter.formatPiasters(stat.avgPiasters, currency, AppLanguage.EN)})"
            }
            .ifBlank { "No recurring expense descriptions recorded yet" }

        val activeBudgets = state.budgetEvaluations
            .take(8)
            .joinToString("; ") { eval ->
                val status = when {
                    eval.isExceeded -> "EXCEEDED"
                    eval.isWarning80 -> "WARNING"
                    else -> "OK"
                }
                "${eval.name}: spent=${MoneyFormatter.formatPiasters(eval.spentPiasters, currency, AppLanguage.EN)}/limit=${MoneyFormatter.formatPiasters(eval.limitPiasters, currency, AppLanguage.EN)} (${eval.usagePercent.roundToInt()}%, $status)"
            }
            .ifBlank { "None" }

        val goalsSummary = state.savingsGoals
            .take(6)
            .joinToString("; ") { goal ->
                val pct = if (goal.targetAmountPiasters > 0L) {
                    ((goal.savedAmountPiasters.toDouble() / goal.targetAmountPiasters.toDouble()) * 100.0).roundToInt()
                } else 0
                "${goal.name}(${goal.status.name}): ${MoneyFormatter.formatPiasters(goal.savedAmountPiasters, currency, AppLanguage.EN)}/${MoneyFormatter.formatPiasters(goal.targetAmountPiasters, currency, AppLanguage.EN)} ($pct%)"
            }
            .ifBlank { "None" }

        val activeInstallmentsSummary = state.installments
            .take(6)
            .joinToString("; ") { inst ->
                val remainingCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0)
                "${inst.name}[${inst.creditor}]: ${MoneyFormatter.formatPiasters(inst.installmentAmountPiasters, currency, AppLanguage.EN)}/${inst.frequency.name}, paid=${inst.paidCount}/${inst.totalCount} (left=$remainingCount)"
            }
            .ifBlank { "None" }

        val recurringSummary = state.recurringRules
            .filter { it.isActive }
            .take(6)
            .joinToString("; ") { rule ->
                "${rule.title}(${rule.type.name}, ${rule.frequency.name})=${MoneyFormatter.formatPiasters(rule.amountPiasters, currency, AppLanguage.EN)}"
            }
            .ifBlank { "None" }

        val openDebtsSummary = state.debts
            .take(5)
            .joinToString("; ") { debt ->
                val remaining = (debt.totalAmountPiasters - debt.paidAmountPiasters).coerceAtLeast(0L)
                "${debt.personName}(${debt.direction.name}, ${debt.status.name}): remaining=${MoneyFormatter.formatPiasters(remaining, currency, AppLanguage.EN)}"
            }
            .ifBlank { "None" }

        val recentTransactions = state.transactions
            .sortedByDescending { it.timestampMillis }
            .take(12)
            .joinToString("\n") { tx ->
                val dateStr = dateFmt.format(java.util.Date(tx.timestampMillis))
                val cat = tx.categoryId?.let { categoryMap[it] }?.nameEn ?: "Uncategorized"
                val acc = accountMap[tx.accountId]?.let { it.name to it.currency }
                val txCurrency = acc?.second ?: currency
                val accName = acc?.first ?: "Account#${tx.accountId}"
                val amtStr = MoneyFormatter.formatPiasters(tx.amountPiasters, txCurrency, AppLanguage.EN)
                val desc = tx.description.ifBlank { cat }
                "  * $dateStr | ${tx.type.name} | $cat | \"$desc\" | $amtStr | $accName"
            }
            .ifBlank { "  * No transactions recorded yet in Room DB" }

        val bnpl = state.bnplReport
        val deficit = bnpl.deficitAnalysis
        val activeBnplSummary = bnpl.programAnalytics
            .filter { it.program.creditLimitPiasters > 0L || it.activeCount > 0 || it.utilizedLimitPiasters > 0L }
            .take(5)
            .joinToString("; ") { prog ->
                "${prog.program.nameEn}(limit=${MoneyFormatter.formatPiasters(prog.program.creditLimitPiasters, currency, AppLanguage.EN)}, used=${MoneyFormatter.formatPiasters(prog.utilizedLimitPiasters, currency, AppLanguage.EN)}, interest=${prog.effectiveInterestPercent.roundToInt()}%)"
            }
            .ifBlank { "None configured" }

        val expenseTrendText = analytics.previousPeriodExpenseDeltaPercent?.let {
            val sign = if (it > 0) "+" else ""
            "$sign${it.roundToInt()}% vs previous period"
        } ?: "N/A"

        return buildString {
            appendLine("ELBAGORRY Money Live Room Database Snapshot (currency=$currency, totalTransactions=${state.transactions.size}):")
            appendLine("- Accounts (${highlights.activeAccountsCount} active): $accountsSummary")
            appendLine("- Net balance: ${MoneyFormatter.formatPiasters(m.netBalancePiasters, currency, AppLanguage.EN)}")
            appendLine("- Available liquid funds: ${MoneyFormatter.formatPiasters(m.availableFundsPiasters, currency, AppLanguage.EN)}")
            appendLine("- Period income: ${MoneyFormatter.formatPiasters(m.totalIncomePiasters, currency, AppLanguage.EN)}")
            appendLine("- Period expenses: ${MoneyFormatter.formatPiasters(m.totalExpensesPiasters, currency, AppLanguage.EN)} ($expenseTrendText)")
            appendLine("- Today expenses: ${MoneyFormatter.formatPiasters(state.todayExpensePiasters, currency, AppLanguage.EN)} | Daily avg spend: ${MoneyFormatter.formatPiasters(analytics.averageDailySpendPiasters, currency, AppLanguage.EN)}")
            analytics.highestSpendDayLabel?.let { day ->
                appendLine("- Peak spending day: $day (${MoneyFormatter.formatPiasters(analytics.highestSpendDayAmountPiasters, currency, AppLanguage.EN)})")
            }
            appendLine("- Projected end-of-month balance: ${MoneyFormatter.formatPiasters(analytics.projectedEndOfMonthBalancePiasters, currency, AppLanguage.EN)}")
            appendLine("- Financial Health Score: ${health.score}/100 (${health.grade.name})")
            appendLine("- Safe-to-Spend Today: ${MoneyFormatter.formatPiasters(health.safeToSpendTodayPiasters, currency, AppLanguage.EN)}")
            appendLine("- Cash Runway: ${health.runwayDays} days")
            appendLine("- Savings rate: ${analytics.savingsRatePercent.roundToInt()}%")
            appendLine("- Amounts currently due: ${MoneyFormatter.formatPiasters(m.amountsDuePiasters, currency, AppLanguage.EN)}")
            appendLine("- Upcoming 30-day commitments: ${MoneyFormatter.formatPiasters(health.upcoming30DaysCommitmentsPiasters, currency, AppLanguage.EN)}")
            appendLine("- BNPL Programs & Installments: $activeBnplSummary | Installments-to-Income=${deficit.installmentsToIncomePercent.roundToInt()}% | Monthly Installment Interest Cost=${MoneyFormatter.formatPiasters(deficit.monthlyBnplInterestCostPiasters, currency, AppLanguage.EN)}")
            appendLine("- Active Installment Plans: $activeInstallmentsSummary")
            appendLine("- Projected Monthly Surplus/Deficit: ${MoneyFormatter.formatPiasters(deficit.netMonthlySurplusOrDeficitPiasters, currency, AppLanguage.EN, showSign = true)} (${deficit.headlineEn})")
            appendLine("- Category spending breakdown: $topCategories")
            appendLine("- Top recurring spending habits / merchants: $topMerchantsSummary")
            appendLine("- Budgets utilization: $activeBudgets")
            appendLine("- Savings goals: $goalsSummary")
            appendLine("- Active recurring bills/income: $recurringSummary")
            appendLine("- Debts: $openDebtsSummary")
            appendLine("- Recent Room Transactions (newest first):")
            appendLine(recentTransactions)
        }
    }

    fun buildAccountsContext(accounts: List<AccountEntity>): String {
        val active = accounts.filter { !it.isArchived }
        if (active.isEmpty()) return "No active accounts."
        return active.joinToString("\n") { acc ->
            "- id=${acc.id} | name=\"${acc.name}\" | type=${acc.type.name} | currency=${acc.currency}"
        }
    }

    fun buildCategoriesContext(categories: List<CategoryEntity>): String {
        if (categories.isEmpty()) return "No categories."
        return categories.joinToString("\n") { cat ->
            "- id=${cat.id} | ar=\"${cat.nameAr}\" | en=\"${cat.nameEn}\" | type=${cat.type.name}"
        }
    }

    fun buildSourcesContext(sources: List<FinancialSourceEntity>): String {
        val enabled = sources.filter { it.isEnabled && !it.isIgnored }
        if (enabled.isEmpty()) return "No configured financial sources."
        return enabled.joinToString("\n") { src ->
            "- code=${src.code} | ar=\"${src.displayNameAr}\" | en=\"${src.displayNameEn}\" | defaultAccountId=${src.defaultAccountId ?: -1}"
        }
    }
}

object GeminiJsonParser {
    fun parseActionPlan(jsonString: String): AiActionPlan {
        val json = JSONObject(jsonString)
        val headline = json.optString("headline").ifBlank { throw IllegalStateException("Missing plan headline.") }
        val budgetFocus = json.optString("budgetFocus")
        val savingsOpportunity = json.optString("savingsOpportunity")
        val stepsArray = json.optJSONArray("steps")
        val steps = mutableListOf<AiPlanStep>()
        if (stepsArray != null) {
            for (i in 0 until stepsArray.length()) {
                val obj = stepsArray.getJSONObject(i)
                val title = obj.optString("title")
                if (title.isNotBlank()) {
                    steps.add(
                        AiPlanStep(
                            title = title,
                            impact = obj.optString("impact"),
                            priority = obj.optString("priority", "MEDIUM")
                        )
                    )
                }
            }
        }
        return AiActionPlan(
            headline = headline,
            budgetFocus = budgetFocus,
            savingsOpportunity = savingsOpportunity,
            steps = steps
        )
    }

    fun parseExtractedTransaction(jsonString: String): AiExtractedTransaction {
        val json = JSONObject(jsonString)
        val rawType = json.optString("type", "EXPENSE").uppercase()
        val txType = when (rawType) {
            "INCOME" -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        val itemsArray = json.optJSONArray("items")
        val parsedItems = mutableListOf<AiExtractedLineItem>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.optJSONObject(i) ?: continue
                val itemMajor = obj.optDouble("amountMajor", 0.0)
                val itemPiasters = (itemMajor * 100.0).roundToLong()
                if (itemPiasters <= 0L) continue
                val itemDesc = obj.optString("description").trim().ifBlank { "Item ${i + 1}" }
                val itemCat = obj.optString("categoryHint").trim()
                parsedItems.add(
                    AiExtractedLineItem(
                        description = itemDesc,
                        amountPiasters = itemPiasters,
                        categoryHint = itemCat
                    )
                )
            }
        }

        val amountMajor = json.optDouble("amountMajor", 0.0)
        val parsedTopAmount = (amountMajor * 100.0).roundToLong()
        val amountPiasters = if (parsedTopAmount > 0L) {
            parsedTopAmount
        } else {
            parsedItems.sumOf { it.amountPiasters }
        }
        if (amountPiasters <= 0L) {
            throw IllegalStateException("Could not detect a valid transaction amount greater than 0.")
        }

        val description = json.optString("description").ifBlank {
            parsedItems.firstOrNull()?.description ?: "AI Extracted Transaction"
        }
        val categoryHint = json.optString("categoryHint").ifBlank {
            parsedItems.firstOrNull()?.categoryHint.orEmpty()
        }
        val notes = json.optString("notes")
        return AiExtractedTransaction(
            amountPiasters = amountPiasters,
            type = txType,
            description = description,
            categoryHint = categoryHint,
            notes = notes,
            items = parsedItems
        )
    }

    fun parseAutoFillResult(
        jsonString: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>
    ): AiAutoFillResult {
        val json = JSONObject(jsonString)
        val rawType = json.optString("type", "EXPENSE").uppercase()
        val parsedType = runCatching { TransactionType.valueOf(rawType) }
            .getOrDefault(TransactionType.EXPENSE)

        val amountMajor = if (json.has("amountMajor")) json.optDouble("amountMajor", 0.0) else 0.0
        val amountPiasters = if (amountMajor > 0.0) (amountMajor * 100.0).roundToLong() else null

        val feeMajor = if (json.has("feeMajor")) json.optDouble("feeMajor", 0.0) else 0.0
        val feePiasters = if (feeMajor > 0.0) (feeMajor * 100.0).roundToLong() else 0L

        val rawSourceAcc = json.optLong("sourceAccountId", -1L)
        val sourceAccountId = rawSourceAcc.takeIf { it > 0L && it in validAccountIds }

        val rawTargetAcc = json.optLong("targetAccountId", -1L)
        val targetAccountId = rawTargetAcc.takeIf {
            it > 0L && it in validAccountIds && it != sourceAccountId
        }

        val finalType = if (parsedType == TransactionType.TRANSFER && validAccountIds.size < 2) {
            TransactionType.EXPENSE
        } else {
            parsedType
        }

        val rawCatId = json.optLong("categoryId", -1L)
        val categoryId = if (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE) {
            rawCatId.takeIf { it > 0L && it in validCategoryIds }
        } else {
            null
        }

        val description = json.optString("description").trim()
        val notes = json.optString("notes").trim()
        val confidence = json.optInt("confidencePercent", 88).coerceIn(10, 100)

        return AiAutoFillResult(
            type = finalType,
            amountPiasters = amountPiasters,
            feePiasters = feePiasters,
            sourceAccountId = sourceAccountId,
            targetAccountId = if (finalType == TransactionType.TRANSFER) targetAccountId else null,
            categoryId = categoryId,
            description = description,
            notes = notes,
            confidencePercent = confidence
        )
    }

    fun parseFinancialMessagesBatch(
        jsonString: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>
    ): List<AiParsedBankMessage> {
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("transactions") ?: return emptyList()
        val results = mutableListOf<AiParsedBankMessage>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val isFinancial = obj.optBoolean("isFinancial", false)
            if (!isFinancial) continue

            val amountMajor = obj.optDouble("amountMajor", 0.0)
            val amountPiasters = (amountMajor * 100.0).roundToLong()
            if (amountPiasters <= 0L) continue

            val feeMajor = obj.optDouble("feeMajor", 0.0)
            val feePiasters = if (feeMajor > 0.0) (feeMajor * 100.0).roundToLong() else 0L

            val opKindRaw = obj.optString("operationKind", "CARD_PURCHASE").uppercase()
            val operationKind = runCatching { MessageOperationKind.valueOf(opKindRaw) }
                .getOrDefault(MessageOperationKind.CARD_PURCHASE)
            if (operationKind == MessageOperationKind.BALANCE_INQUIRY) continue

            val rawMappedType = obj.optString("mappedType", "EXPENSE").uppercase()
            val parsedMappedType = when (rawMappedType) {
                "INCOME" -> TransactionType.INCOME
                "TRANSFER" -> TransactionType.TRANSFER
                else -> TransactionType.EXPENSE
            }

            val rawSourceAcc = obj.optLong("suggestedAccountId", -1L)
            val suggestedAccountId = rawSourceAcc.takeIf { it > 0L && it in validAccountIds }

            val rawTargetAcc = obj.optLong("targetAccountId", -1L)
            val targetAccountId = rawTargetAcc.takeIf {
                it > 0L && it in validAccountIds && it != suggestedAccountId
            }

            val finalMappedType = if (parsedMappedType == TransactionType.TRANSFER && targetAccountId == null && validAccountIds.size < 2) {
                if (operationKind == MessageOperationKind.INCOMING_TRANSFER) TransactionType.INCOME else TransactionType.EXPENSE
            } else {
                parsedMappedType
            }

            val finalOpKind = if (finalMappedType == TransactionType.TRANSFER) {
                MessageOperationKind.INTERNAL_TRANSFER
            } else {
                operationKind
            }

            val rawCatId = obj.optLong("suggestedCategoryId", -1L)
            val suggestedCategoryId = if (finalMappedType == TransactionType.INCOME || finalMappedType == TransactionType.EXPENSE) {
                rawCatId.takeIf { it > 0L && it in validCategoryIds }
            } else {
                null
            }

            val balMajor = if (obj.has("balanceAfterMajor") && !obj.isNull("balanceAfterMajor")) {
                obj.optDouble("balanceAfterMajor", -1.0)
            } else -1.0
            val balanceAfterPiasters = if (balMajor >= 0.0) (balMajor * 100.0).roundToLong() else null

            val currency = obj.optString("currency", "EGP").uppercase().ifBlank { "EGP" }
            val sourceName = obj.optString("sourceName", "AI Reader").trim().ifBlank { "AI Reader" }
            val counterparty = obj.optString("counterpartyName").trim().ifBlank { null }
            val reference = obj.optString("referenceNumber").trim().ifBlank { null }
            val summary = obj.optString("summary").trim().ifBlank { "$sourceName • $opKindRaw" }
            val confidence = (obj.optInt("confidencePercent", 88).coerceIn(10, 100)) / 100f

            results.add(
                AiParsedBankMessage(
                    isFinancial = true,
                    operationKind = finalOpKind,
                    mappedType = finalMappedType,
                    amountPiasters = amountPiasters,
                    feePiasters = feePiasters,
                    currency = currency,
                    sourceName = sourceName,
                    counterpartyName = counterparty,
                    referenceNumber = reference,
                    balanceAfterPiasters = balanceAfterPiasters,
                    suggestedAccountId = suggestedAccountId,
                    targetAccountId = if (finalMappedType == TransactionType.TRANSFER) targetAccountId else null,
                    suggestedCategoryId = suggestedCategoryId,
                    summary = summary,
                    confidenceScore = confidence
                )
            )
        }
        return results
    }

    fun parseAgenticTurnResult(
        jsonString: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        fallbackAccountId: Long
    ): AiAgenticTurnResult {
        val root = JSONObject(jsonString)
        val rawMode = root.optString("intentMode", "ADVICE_ONLY").uppercase()
        val intentMode = runCatching { AiChatIntentMode.valueOf(rawMode) }
            .getOrDefault(AiChatIntentMode.ADVICE_ONLY)
        val opsArray = root.optJSONArray("operations")
        val parsedOps = mutableListOf<AiProposedOperation>()

        if (opsArray != null) {
            for (i in 0 until opsArray.length()) {
                val obj = opsArray.optJSONObject(i) ?: continue
                val amountMajor = obj.optDouble("amountMajor", 0.0)
                val amountPiasters = (amountMajor * 100.0).roundToLong()
                if (amountPiasters <= 0L) continue

                val feeMajor = if (obj.has("feeMajor")) obj.optDouble("feeMajor", 0.0) else 0.0
                val feePiasters = if (feeMajor > 0.0) (feeMajor * 100.0).roundToLong() else 0L

                val rawType = obj.optString("type", "EXPENSE").uppercase()
                val rawTxType = when (rawType) {
                    "INCOME" -> TransactionType.INCOME
                    "TRANSFER" -> TransactionType.TRANSFER
                    else -> TransactionType.EXPENSE
                }

                val rawSourceAcc = obj.optLong("sourceAccountId", -1L)
                val sourceAccountId = if (rawSourceAcc > 0L && rawSourceAcc in validAccountIds) {
                    rawSourceAcc
                } else {
                    fallbackAccountId
                }

                val rawTargetAcc = obj.optLong("targetAccountId", -1L)
                val targetAccountId = rawTargetAcc.takeIf {
                    it > 0L && it in validAccountIds && it != sourceAccountId
                }

                val finalType = if (rawTxType == TransactionType.TRANSFER && (targetAccountId == null || validAccountIds.size < 2)) {
                    TransactionType.EXPENSE
                } else {
                    rawTxType
                }

                val rawCatId = obj.optLong("categoryId", -1L)
                val categoryId = if (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE) {
                    rawCatId.takeIf { it > 0L && it in validCategoryIds }
                } else {
                    null
                }

                val newCatAr = if (categoryId == null && (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE)) {
                    obj.optString("newCategoryNameAr").trim().ifBlank { null }
                } else null
                val newCatEn = if (categoryId == null && (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE)) {
                    obj.optString("newCategoryNameEn").trim().ifBlank { null }
                } else null
                val newCatColor = obj.optString("newCategoryColorHex").trim().takeIf {
                    it.startsWith("#") && (it.length == 7 || it.length == 9)
                }

                val description = obj.optString("description").trim().ifBlank {
                    when (finalType) {
                        TransactionType.INCOME -> "دخل"
                        TransactionType.TRANSFER -> "تحويل"
                        else -> "مصروف"
                    }
                }
                val notes = obj.optString("notes").trim()

                parsedOps.add(
                    AiProposedOperation(
                        type = finalType,
                        amountPiasters = amountPiasters,
                        feePiasters = if (finalType == TransactionType.TRANSFER) feePiasters else 0L,
                        description = description,
                        sourceAccountId = sourceAccountId,
                        targetAccountId = if (finalType == TransactionType.TRANSFER) targetAccountId else null,
                        categoryId = categoryId,
                        newCategoryNameAr = newCatAr,
                        newCategoryNameEn = newCatEn,
                        newCategoryColorHex = newCatColor,
                        notes = notes
                    )
                )
            }
        }

        val reply = root.optString("coachReply").trim()
        val effectiveMode = if (parsedOps.isEmpty()) AiChatIntentMode.ADVICE_ONLY else intentMode
        return AiAgenticTurnResult(
            intentMode = effectiveMode,
            operations = parsedOps,
            coachReply = reply
        )
    }

    fun parseTransactionCategoryAssignments(
        jsonString: String,
        validTransactionIds: Set<Long>,
        validCategoryIds: Set<Long>
    ): List<AiTransactionCategoryAssignment> {
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("assignments") ?: return emptyList()
        val assignments = mutableListOf<AiTransactionCategoryAssignment>()
        val seenTxIds = mutableSetOf<Long>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val txId = obj.optLong("transactionId", -1L)
            val catId = obj.optLong("categoryId", -1L)
            if (txId in validTransactionIds && catId in validCategoryIds && seenTxIds.add(txId)) {
                val conf = obj.optInt("confidencePercent", 90).coerceIn(10, 100)
                assignments.add(
                    AiTransactionCategoryAssignment(
                        transactionId = txId,
                        categoryId = catId,
                        confidencePercent = conf
                    )
                )
            }
        }
        return assignments
    }
}

class FirebaseGeminiFinancialGateway : FinancialAiGateway {

    private val healthProbeModel by lazy {
        val config = generationConfig {
            maxOutputTokens = 8
            temperature = 0f
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_NAME)
    }

    private val actionPlanModel by lazy {
        val planSchema = Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(description = "Short actionable headline summarizing the financial plan"),
                "budgetFocus" to Schema.string(description = "Specific budget or category to watch closely"),
                "savingsOpportunity" to Schema.string(description = "Practical savings estimate or target"),
                "steps" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Concrete step title"),
                            "impact" to Schema.string(description = "Expected monthly or weekly impact"),
                            "priority" to Schema.enumeration(
                                values = listOf("HIGH", "MEDIUM", "LOW"),
                                description = "Priority level"
                            )
                        )
                    ),
                    description = "3 to 4 concrete prioritized action steps"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = planSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    private val receiptParserModel by lazy {
        val lineItemSchema = Schema.obj(
            properties = mapOf(
                "description" to Schema.string(description = "Item or product name"),
                "amountMajor" to Schema.double(description = "Item line total in major currency units"),
                "categoryHint" to Schema.string(description = "Best category hint for this item such as Food, Drinks, Groceries, Pharmacy, Shopping")
            ),
            optionalProperties = listOf("categoryHint")
        )
        val txSchema = Schema.obj(
            properties = mapOf(
                "amountMajor" to Schema.double(description = "Total transaction numeric amount in major currency units (e.g. 145.50)"),
                "type" to Schema.enumeration(
                    values = listOf("EXPENSE", "INCOME"),
                    description = "Whether the transaction is an EXPENSE or INCOME"
                ),
                "description" to Schema.string(description = "Merchant or short transaction title"),
                "categoryHint" to Schema.string(description = "Suggested primary category name such as Food, Transport, Shopping, Bills, Salary"),
                "notes" to Schema.string(description = "Optional extra details like card ending, reference, or date"),
                "items" to Schema.array(
                    items = lineItemSchema,
                    description = "Optional itemized breakdown when the receipt lists 2 or more distinct items or categories"
                )
            ),
            optionalProperties = listOf("notes", "items")
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = txSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    private val autoFillModel by lazy {
        val autoFillSchema = Schema.obj(
            properties = mapOf(
                "type" to Schema.enumeration(
                    values = listOf("EXPENSE", "INCOME", "TRANSFER", "SAVINGS_CONTRIBUTION", "DEBT_PAYMENT"),
                    description = "Transaction type. Use TRANSFER when moving money between two of the user's own accounts/wallets/banks."
                ),
                "amountMajor" to Schema.double(description = "Primary transaction amount in major currency units, or 0 if not mentioned"),
                "feeMajor" to Schema.double(description = "Transfer or bank fee if mentioned separately (0 if none)"),
                "sourceAccountId" to Schema.integer(description = "Matching account ID from user's Accounts list, or -1 if unclear"),
                "targetAccountId" to Schema.integer(description = "Destination account ID from user's Accounts list for TRANSFER only (must differ from sourceAccountId), or -1"),
                "categoryId" to Schema.integer(description = "Matching category ID from user's Categories list for INCOME or EXPENSE, or -1"),
                "description" to Schema.string(description = "Clean, concise human-readable title/merchant/transfer summary"),
                "notes" to Schema.string(description = "Optional helpful notes such as counterparty, card info, or original context"),
                "confidencePercent" to Schema.integer(description = "Confidence score between 10 and 100")
            ),
            optionalProperties = listOf("feeMajor", "targetAccountId", "categoryId", "notes")
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = autoFillSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = config
        )
    }

    private val messageBatchParserModel by lazy {
        val itemSchema = Schema.obj(
            properties = mapOf(
                "isFinancial" to Schema.boolean(description = "True only for real completed financial movements (never OTP, security alerts, balance-only inquiries, or marketing promos)"),
                "operationKind" to Schema.enumeration(
                    values = listOf(
                        "INCOMING_TRANSFER",
                        "OUTGOING_TRANSFER",
                        "INTERNAL_TRANSFER",
                        "DEPOSIT",
                        "WITHDRAWAL",
                        "CARD_PURCHASE",
                        "BILL_PAYMENT",
                        "TRANSFER_FEE",
                        "REFUND",
                        "SALARY"
                    ),
                    description = "Specific banking or wallet operation type"
                ),
                "mappedType" to Schema.enumeration(
                    values = listOf("EXPENSE", "INCOME", "TRANSFER"),
                    description = "Use TRANSFER ONLY if moving money between two of the user's own accounts; otherwise EXPENSE or INCOME"
                ),
                "amountMajor" to Schema.double(description = "Main transaction amount in major units"),
                "feeMajor" to Schema.double(description = "Separate transfer/service fee in major units (0 if none)"),
                "currency" to Schema.string(description = "3-letter currency code such as EGP, USD, SAR, EUR"),
                "sourceName" to Schema.string(description = "Bank, wallet, or payment provider name (e.g. CIB, NBE, إنستاباي, فودافون كاش)"),
                "counterpartyName" to Schema.string(description = "Merchant, recipient, or sender name if present"),
                "referenceNumber" to Schema.string(description = "Transaction reference/ID if present"),
                "balanceAfterMajor" to Schema.double(description = "Remaining/new account balance if stated, or -1 if absent"),
                "suggestedAccountId" to Schema.integer(description = "Best matching account ID from the user's Accounts list, or -1"),
                "targetAccountId" to Schema.integer(description = "Destination account ID ONLY if mappedType is TRANSFER between two of the user's own accounts, else -1"),
                "suggestedCategoryId" to Schema.integer(description = "Best matching category ID from the user's Categories list for EXPENSE/INCOME, else -1"),
                "summary" to Schema.string(description = "Concise human summary of the transaction"),
                "confidencePercent" to Schema.integer(description = "Confidence from 10 to 100")
            ),
            optionalProperties = listOf(
                "feeMajor",
                "counterpartyName",
                "referenceNumber",
                "balanceAfterMajor",
                "targetAccountId",
                "suggestedCategoryId"
            )
        )
        val batchSchema = Schema.obj(
            properties = mapOf(
                "transactions" to Schema.array(
                    items = itemSchema,
                    description = "List of parsed financial transactions extracted from the message block"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = batchSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = config
        )
    }

    private val agenticTurnModel by lazy {
        val opSchema = Schema.obj(
            properties = mapOf(
                "type" to Schema.enumeration(
                    values = listOf("EXPENSE", "INCOME", "TRANSFER"),
                    description = "Operation type. Default EXPENSE for purchases/spending, INCOME for salary/received money, TRANSFER only for moving between two user accounts."
                ),
                "amountMajor" to Schema.double(description = "Numeric amount in major units (e.g. 50.0)"),
                "feeMajor" to Schema.double(description = "Optional separate transfer fee in major units (0 if none)"),
                "description" to Schema.string(description = "Clean short item or merchant name (e.g. 'أكل', 'مواصلات', 'طلب أونلاين', 'Coffee')"),
                "sourceAccountId" to Schema.integer(description = "Best matching account ID from User Accounts list, or -1 for default"),
                "targetAccountId" to Schema.integer(description = "Destination account ID ONLY for TRANSFER between two user accounts, else -1"),
                "categoryId" to Schema.integer(description = "Best matching category ID from User Categories list for EXPENSE/INCOME, or -1 if a new category is needed"),
                "newCategoryNameAr" to Schema.string(description = "If categoryId is -1, concise Arabic category name (e.g. 'ترفيه', 'تسوق')"),
                "newCategoryNameEn" to Schema.string(description = "If categoryId is -1, concise English category name (e.g. 'Entertainment', 'Shopping')"),
                "newCategoryColorHex" to Schema.string(description = "Optional hex color like #A855F7 if creating a new category"),
                "notes" to Schema.string(description = "Optional extra context such as timeframe or payment note")
            ),
            optionalProperties = listOf(
                "feeMajor",
                "targetAccountId",
                "categoryId",
                "newCategoryNameAr",
                "newCategoryNameEn",
                "newCategoryColorHex",
                "notes"
            )
        )
        val agenticSchema = Schema.obj(
            properties = mapOf(
                "intentMode" to Schema.enumeration(
                    values = listOf("EXECUTE_ACTIONS", "ADVICE_ONLY", "EXECUTE_AND_ADVISE"),
                    description = "Use EXECUTE_ACTIONS when user logs expenses/income/transfers, ADVICE_ONLY when user only asks a question, EXECUTE_AND_ADVISE when doing both."
                ),
                "operations" to Schema.array(
                    items = opSchema,
                    description = "Zero or more financial transactions to record immediately into Room DB. Split compound phrases like '50 اكل و 20 مواصلات و 70 طلب اونلاين' into separate items."
                ),
                "coachReply" to Schema.string(
                    description = "Warm, concise confirmation of what was recorded & organized (with totals/categories) or actionable financial advice grounded in the user's Room snapshot."
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = agenticSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    private val batchCategorizeModel by lazy {
        val assignmentSchema = Schema.obj(
            properties = mapOf(
                "transactionId" to Schema.integer(description = "Exact transaction ID from input"),
                "categoryId" to Schema.integer(description = "Best matching category ID from User Categories list matching transaction type"),
                "confidencePercent" to Schema.integer(description = "Confidence between 10 and 100")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "assignments" to Schema.array(
                    items = assignmentSchema,
                    description = "Category assignments for each provided transaction"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = rootSchema
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = config
        )
    }

    override suspend fun verifyConnection(language: AppLanguage): GeminiConnectionResult {
        val start = System.currentTimeMillis()
        return try {
            val response = kotlinx.coroutines.withTimeout(15_000L) {
                healthProbeModel.generateContent("Reply with OK")
            }
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val txt = response.text?.trim().orEmpty()
            if (txt.isNotEmpty()) {
                GeminiConnectionResult(
                    isConnected = true,
                    modelName = MODEL_NAME,
                    latencyMs = elapsed
                )
            } else {
                GeminiConnectionResult(
                    isConnected = false,
                    modelName = MODEL_NAME,
                    latencyMs = elapsed,
                    errorMessage = if (language == AppLanguage.EN) {
                        "Gemini returned an empty handshake response."
                    } else {
                        "لم يُرجع Gemini استجابة تحقق صالحة."
                    }
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiConnectionResult(
                isConnected = false,
                modelName = MODEL_NAME,
                latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
                errorMessage = formatGeminiError(e, language)
            )
        }
    }

    override fun streamFinancialAdvice(
        financialContext: String,
        history: List<AiChatMessage>,
        userPrompt: String,
        language: AppLanguage
    ): Flow<String> {
        val langInstruction = if (language == AppLanguage.EN) {
            "Respond warmly, clearly, and practically in English using short scannable bullet points."
        } else {
            "أجب باللغة العربية بأسلوب ودي وعملي وواضح مع نقاط مختصرة وسهلة القراءة."
        }
        val coachRules = buildString {
            appendLine("You are ELBAGORRY Money's AI Financial Coach.")
            appendLine("Coaching Rules:")
            appendLine("1. Base EVERY answer strictly on the user's live Room database snapshot below (accounts, categories, recurring merchants/habits, budgets, goals, installments, and recent transactions). Never invent numbers.")
            appendLine("2. When analyzing spending habits, cite exact figures, top categories, frequent merchants, and budget status or Safe-to-Spend Today from the snapshot.")
            appendLine("3. Always finish with 2-3 specific, quantified, actionable steps the user can take this week (e.g., exact EGP amount to trim, cap, or redirect toward savings/installments).")
            appendLine("4. If the Room snapshot has few transactions, state that briefly and offer immediate starter advice.")
            appendLine(langInstruction)
            appendLine()
            append(financialContext)
        }
        val chatHistory = buildList {
            add(content("user") { text(coachRules) })
            add(
                content("model") {
                    text(
                        if (language == AppLanguage.EN) {
                            "Understood. I have reviewed your live Room financial snapshot, recent transactions, and spending habits, and I am ready for an open, in-depth coaching conversation."
                        } else {
                            "تم الاطلاع على بياناتك المالية الفعلية ومعاملاتك وعادات الصرف في قاعدة البيانات، وأنا جاهز لحوار مفتوح وموسّع ونصائح عملية مخصصة لك."
                        }
                    )
                }
            )
            history.filter { !it.isError && it.text.isNotBlank() }.takeLast(24).forEach { msg ->
                add(content(if (msg.isUser) "user" else "model") { text(msg.text) })
            }
        }
        val chat = chatModel.startChat(history = chatHistory)
        return chat.sendMessageStream(userPrompt).mapNotNull { chunk -> chunk.text }
    }

    override suspend fun generateActionPlan(
        financialContext: String,
        language: AppLanguage
    ): AiActionPlan {
        val langPrompt = if (language == AppLanguage.EN) {
            "Write all text fields in concise, practical English."
        } else {
            "اكتب جميع النصوص باللغة العربية الواضحة والعملية."
        }
        val prompt = buildString {
            appendLine("Create a realistic, personalized 7-to-30 day personal finance action plan based strictly on the numbers below.")
            appendLine(langPrompt)
            appendLine(financialContext)
        }
        val response = actionPlanModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned an empty action plan.")
        return GeminiJsonParser.parseActionPlan(jsonText)
    }

    override suspend fun analyzeReceiptOrMessage(
        financialContext: String,
        textInput: String?,
        bitmap: Bitmap?,
        language: AppLanguage
    ): AiExtractedTransaction {
        val scaledBitmap = bitmap?.let { scaleBitmapWithinLimit(it, maxDimension = 1280) }
        val langNote = if (language == AppLanguage.EN) {
            "Use concise English for description and categoryHint unless merchant name is Arabic."
        } else {
            "اجعل الوصف وتصنيف الفئة باللغة العربية الواضحة (مع الحفاظ على اسم المتجر كما هو)."
        }
        val prompt = content {
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
            text(
                buildString {
                    appendLine("Extract the financial transaction details from the attached receipt image and/or message text.")
                    appendLine("If the receipt contains multiple distinct line items (e.g. groceries, pharmacy, restaurant items), populate 'items' with each item's name, amount, and categoryHint, while keeping 'amountMajor' as the grand total.")
                    appendLine(langNote)
                    if (!textInput.isNullOrBlank()) {
                        appendLine("Message / Receipt text:")
                        appendLine(textInput.trim())
                    }
                    appendLine("User context for currency reference:")
                    appendLine(financialContext)
                }
            )
        }
        val response = receiptParserModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty receipt extraction output.")
        return GeminiJsonParser.parseExtractedTransaction(jsonText)
    }

    override suspend fun smartAutoFillTransaction(
        phrase: String,
        accountsContext: String,
        categoriesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        primaryCurrency: String,
        language: AppLanguage
    ): AiAutoFillResult {
        val langRule = if (language == AppLanguage.EN) {
            "Write description and notes in clear, natural English."
        } else {
            "اكتب الوصف والملاحظات باللغة العربية الواضحة والمختصرة (مع إبقاء أسماء المتاجر كما هي)."
        }
        val prompt = buildString {
            appendLine("Parse this natural-language transaction or transfer phrase (Egyptian Arabic dialect, MSA, or English) into structured fields.")
            appendLine("Primary currency: $primaryCurrency. $langRule")
            appendLine("Rules:")
            appendLine("- Match sourceAccountId, targetAccountId, and categoryId strictly from the IDs provided below (-1 if none fits).")
            appendLine("- If the user describes moving money between two of their accounts (e.g. from Bank/CIB to Wallet/Cash/Vodafone Cash/InstaPay), set type=TRANSFER and populate both sourceAccountId and targetAccountId.")
            appendLine("- Extract any separate transfer/service fee into feeMajor (0 if none).")
            appendLine("User Accounts:")
            appendLine(accountsContext)
            appendLine("User Categories:")
            appendLine(categoriesContext)
            appendLine("User phrase:")
            appendLine(phrase.trim())
        }
        val response = autoFillModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty auto-fill output.")
        return GeminiJsonParser.parseAutoFillResult(
            jsonString = jsonText,
            validAccountIds = validAccountIds,
            validCategoryIds = validCategoryIds
        )
    }

    override suspend fun analyzeFinancialMessagesWithAi(
        sender: String,
        rawMessageBlock: String,
        accountsContext: String,
        categoriesContext: String,
        sourcesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        language: AppLanguage
    ): List<AiParsedBankMessage> {
        val langRule = if (language == AppLanguage.EN) {
            "Write summary in clear English."
        } else {
            "اكتب الملخص summary باللغة العربية الواضحة والمختصرة."
        }
        val prompt = buildString {
            appendLine("Analyze the following bank, e-wallet, or InstaPay message(s) and extract every completed financial transaction.")
            appendLine("Ignore OTP codes, balance-only alerts, and marketing promotions by setting isFinancial=false or omitting them.")
            appendLine("Handle Eastern Arabic numerals (١٢٣), unstructured phrasing, transfers, fees, ATM withdrawals, POS purchases, salaries, and refunds.")
            appendLine("IMPORTANT Transfer Rule: Set mappedType=TRANSFER and operationKind=INTERNAL_TRANSFER ONLY when moving money between two of the user's own accounts listed below (and provide distinct suggestedAccountId and targetAccountId). Otherwise, use OUTGOING_TRANSFER (EXPENSE) or INCOMING_TRANSFER (INCOME).")
            appendLine(langRule)
            appendLine("Sender hint: ${sender.ifBlank { "Unknown" }}")
            appendLine("User Accounts:")
            appendLine(accountsContext)
            appendLine("User Categories:")
            appendLine(categoriesContext)
            appendLine("Known Financial Sources:")
            appendLine(sourcesContext)
            appendLine("Messages to analyze:")
            appendLine(rawMessageBlock.trim())
        }
        val response = messageBatchParserModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty message analysis output.")
        return GeminiJsonParser.parseFinancialMessagesBatch(
            jsonString = jsonText,
            validAccountIds = validAccountIds,
            validCategoryIds = validCategoryIds
        )
    }

    override suspend fun executeAgenticChatTurn(
        financialContext: String,
        accountsContext: String,
        categoriesContext: String,
        validAccountIds: Set<Long>,
        validCategoryIds: Set<Long>,
        fallbackAccountId: Long,
        history: List<AiChatMessage>,
        userPrompt: String,
        primaryCurrency: String,
        language: AppLanguage
    ): AiAgenticTurnResult {
        val langRule = if (language == AppLanguage.EN) {
            "Write coachReply and descriptions clearly in natural English (preserving proper names)."
        } else {
            "اكتب coachReply بأسلوب مصري/عربي ودي ومختصر وواضح يؤكد كل عملية تم تسجيلها وتصنيفها وإجماليها أو يقدم نصيحة عملية."
        }
        val historyContext = history
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(16)
            .joinToString("\n") { msg ->
                val role = if (msg.isUser) "User" else "Coach"
                "$role: ${msg.text.take(450)}"
            }
            .ifBlank { "None" }

        val prompt = buildString {
            appendLine("You are ELBAGORRY Money's Smart Agentic Financial Assistant & Coach.")
            appendLine("Primary currency: $primaryCurrency. $langRule")
            appendLine("Behavior Rules:")
            appendLine("1. If the user describes one or more spending, income, or transfer actions (for example: 'صرفت النهاردة 50 جنيه اكل و 20 مواصلات و 70 طلب اونلاين'), extract EVERY single item as a separate object inside 'operations' and set intentMode=EXECUTE_ACTIONS (or EXECUTE_AND_ADVISE if they also asked a question).")
            appendLine("2. Auto-categorize every EXPENSE or INCOME item by picking the closest matching categoryId from User Categories below (e.g., Food/طعام, Transport/مواصلات, Shopping/تسوق, Entertainment/ترفيه). Only supply newCategoryNameAr/En with categoryId=-1 if no existing category fits.")
            appendLine("3. Pick sourceAccountId from User Accounts below (-1 to use the user's primary default account). Use type=TRANSFER only when moving money between two of the user's own accounts.")
            appendLine("4. If the user is purely asking for analysis, insights, or advice without logging a new transaction, set intentMode=ADVICE_ONLY, operations=[], and answer strictly from the Live Room Financial Snapshot.")
            appendLine()
            appendLine("User Accounts:")
            appendLine(accountsContext)
            appendLine("User Categories:")
            appendLine(categoriesContext)
            appendLine("Recent Conversation:")
            appendLine(historyContext)
            appendLine("Live Room Financial Snapshot:")
            appendLine(financialContext)
            appendLine("User Message:")
            appendLine(userPrompt.trim())
        }
        val response = agenticTurnModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty agentic response.")
        return GeminiJsonParser.parseAgenticTurnResult(
            jsonString = jsonText,
            validAccountIds = validAccountIds,
            validCategoryIds = validCategoryIds,
            fallbackAccountId = fallbackAccountId
        )
    }

    override suspend fun autoCategorizeTransactions(
        transactionsSummary: String,
        categoriesContext: String,
        validTransactionIds: Set<Long>,
        validCategoryIds: Set<Long>,
        language: AppLanguage
    ): List<AiTransactionCategoryAssignment> {
        val prompt = buildString {
            appendLine("Assign the best categoryId from User Categories below to each transaction based on its description, notes, type, and amount.")
            appendLine("Rules:")
            appendLine("- Match EXPENSE transactions strictly to EXPENSE category IDs and INCOME transactions strictly to INCOME category IDs.")
            appendLine("- Return only valid transactionId and categoryId pairs from the lists below.")
            appendLine("User Categories:")
            appendLine(categoriesContext)
            appendLine("Transactions to categorize:")
            appendLine(transactionsSummary)
        }
        val response = batchCategorizeModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty categorization output.")
        return GeminiJsonParser.parseTransactionCategoryAssignments(
            jsonString = jsonText,
            validTransactionIds = validTransactionIds,
            validCategoryIds = validCategoryIds
        )
    }

    private fun scaleBitmapWithinLimit(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        val largest = max(w, h)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest.toFloat()
        val targetW = (w * scale).roundToInt().coerceAtLeast(1)
        val targetH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    companion object {
        const val MODEL_NAME = "gemini-4-hydrogen"
        const val MODEL_FAST = "gemini-3.5-flash-lite"

        fun formatGeminiError(error: Throwable, language: AppLanguage): String {
            val raw = error.localizedMessage ?: error.message ?: error.javaClass.simpleName
            val lower = raw.lowercase()
            return when {
                lower.contains("403") || lower.contains("app check") || lower.contains("attestation") -> {
                    if (language == AppLanguage.EN) {
                        "App Check or API permission error (403): $raw"
                    } else {
                        "تعذر التحقق الأمني (App Check 403): $raw"
                    }
                }
                lower.contains("unable to resolve host") || lower.contains("network") || lower.contains("connect") || lower.contains("timeout") -> {
                    if (language == AppLanguage.EN) {
                        "No internet connection or Gemini timed out: $raw"
                    } else {
                        "تحقق من اتصال الإنترنت ثم أعد المحاولة: $raw"
                    }
                }
                lower.contains("quota") || lower.contains("429") || lower.contains("resource_exhausted") -> {
                    if (language == AppLanguage.EN) {
                        "Gemini rate limit reached temporarily. Try again in a moment."
                    } else {
                        "تم الوصول للحد المؤقت لطلبات Gemini، انتظر لحظات وأعد المحاولة."
                    }
                }
                else -> raw
            }
        }
    }
}
