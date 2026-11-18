package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import kotlin.math.ln

data class LocalPrediction(
    val categoryId: Long?,
    val accountId: Long?,
    val preferredType: TransactionType?,
    val confidence: Float,
    val source: String, // RULE_EXACT, RULE_FUZZY, NAIVE_BAYES, KEYWORD_HINT, NONE
    val matchedRuleKeyword: String? = null
)

data class TeachingDirective(
    val keyword: String,
    val categoryId: Long?,
    val categoryNameAr: String?,
    val categoryNameEn: String?,
    val accountId: Long?,
    val accountName: String?,
    val preferredType: TransactionType
)

data class LocalMemoryStats(
    val learnedRuleCount: Int,
    val totalRuleHits: Int,
    val indexedTransactionCount: Int,
    val topRules: List<MerchantRuleEntity>,
    val adaptiveTierAr: String,
    val adaptiveTierEn: String
)

/**
 * Lightweight, zero-dependency on-device self-learning engine:
 * 1. Morpho-Arabic + Franco-Arab normalizer & stemmer
 * 2. Weighted Exact & Dice-coefficient Fuzzy Matcher over MerchantRuleEntity
 * 3. Live Multinomial Naive Bayes classifier trained over Room TransactionEntity history
 * 4. Natural-language interactive teaching command parser ("علمني أن ...", "learn that ...")
 */
object LocalSelfLearningEngine {

    private val arabicDiacriticsRegex = Regex("""[\u064B-\u065F\u0670\u0640]""")
    private val nonWordRegex = Regex("""[^\p{L}\p{Nd}\s]""")
    private val multiSpaceRegex = Regex("""\s+""")

    private val francoArabicMap: Map<String, String> = mapOf(
        "akl" to "اكل", "akle" to "اكل", "matam" to "مطعم", "ghada" to "غدا",
        "fatar" to "فطار", "asha" to "عشا", "ahwa" to "قهوة", "qahwa" to "قهوة",
        "kahwa" to "قهوة", "cafe" to "كافيه", "kafe" to "كافيه",
        "mwaslat" to "مواصلات", "mowasalat" to "مواصلات", "benzin" to "بنزين", "banzin" to "بنزين",
        "uber" to "اوبر", "careem" to "كريم", "taxi" to "تاكسي", "metro" to "مترو",
        "fatora" to "فاتورة", "fawater" to "فواتير", "kahraba" to "كهرباء", "kahrba" to "كهرباء",
        "miah" to "مياه", "mayah" to "مياه", "ghaz" to "غاز", "gaz" to "غاز",
        "shahn" to "شحن", "rased" to "رصيد", "net" to "انترنت", "internet" to "انترنت",
        "dawa" to "دواء", "saydalia" to "صيدلية", "doktor" to "دكتور", "doctor" to "دكتور",
        "tasawok" to "تسوق", "malabes" to "ملابس", "hedom" to "ملابس", "talabat" to "طلبات",
        "amazon" to "امازون", "noon" to "نون", "carrefour" to "كارفور",
        "mortb" to "راتب", "ratb" to "راتب", "qest" to "قسط", "aqsad" to "اقساط",
        "egar" to "ايجار", "ijar" to "ايجار", "tawfeer" to "ادخار", "edkhar" to "ادخار",
        "vf" to "فودافون", "vodafon" to "فودافون", "vodafone" to "فودافون",
        "instapay" to "انستاباي", "ipn" to "انستاباي", "valu" to "فاليو"
    )

    private val fillerTokensForKey = setOf(
        "شراء", "بالبطاقة", "البطاقة", "بطاقة", "تحويل", "وارد", "صادر", "داخلي", "إيداع", "ايداع",
        "سحب", "دفع", "فاتورة", "رسوم", "استرداد", "مبلغ", "من", "الى", "إلى", "في", "على", "عن",
        "عند", "لدى", "مع", "جنيه", "جم", "ج.م", "مصري", "دولار", "ريال", "egp", "usd", "sar",
        "صرفت", "دفعت", "اشتريت", "استلمت", "جالي", "حولت", "مصروف", "دخل", "العملية", "عملية",
        "مرجع", "رقم", "المرجع", "card", "purchase", "transfer", "incoming", "outgoing", "payment",
        "from", "to", "at", "ref", "trx", "txn", "id", "bank", "wallet", "cash", "بنك", "محفظة"
    )

    fun normalizeRich(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder(raw.length)
        val noDiacritics = arabicDiacriticsRegex.replace(raw, "")
        for (ch in noDiacritics.lowercase()) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                else -> sb.append(ch)
            }
        }
        val cleaned = multiSpaceRegex.replace(nonWordRegex.replace(sb.toString(), " "), " ").trim()
        if (cleaned.isEmpty()) return ""
        val expanded = cleaned.split(" ").flatMap { tok ->
            val mapped = francoArabicMap[tok]
            if (mapped != null && mapped != tok) listOf(tok, normalizeSimpleArabic(mapped)) else listOf(tok)
        }
        return expanded.joinToString(" ").trim()
    }

    private fun normalizeSimpleArabic(word: String): String {
        val sb = StringBuilder(word.length)
        for (ch in word.lowercase()) {
            when (ch) {
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun tokenizeWithStems(normalized: String): Set<String> {
        if (normalized.isBlank()) return emptySet()
        val rawTokens = normalized.split(" ").filter { it.length >= 2 }
        val out = LinkedHashSet<String>(rawTokens.size * 2)
        for (tok in rawTokens) {
            out.add(tok)
            if (tok.startsWith("ال") && tok.length >= 5) {
                out.add(tok.removePrefix("ال"))
            }
            if ((tok.startsWith("و") || tok.startsWith("ب") || tok.startsWith("ل") || tok.startsWith("ف")) && tok.length >= 4) {
                val stripped = tok.substring(1)
                out.add(stripped)
                if (stripped.startsWith("ال") && stripped.length >= 5) {
                    out.add(stripped.removePrefix("ال"))
                }
            }
        }
        for (i in 0 until rawTokens.size - 1) {
            out.add("${rawTokens[i]}_${rawTokens[i + 1]}")
        }
        return out
    }

    fun extractLearnableMerchantKey(rawDescription: String): String {
        val trimmed = rawDescription.trim()
        if (trimmed.length < 2) return ""
        // Prefer counterparty inside parentheses if SMS summary formatted like "شراء بالبطاقة • بنك (Uber) #123"
        val parenMatch = Regex("""\(([^)]{2,30})\)""").find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = if (!parenMatch.isNullOrBlank() && parenMatch.any { it.isLetter() }) {
            parenMatch
        } else {
            trimmed
                .replace(Regex("""#[A-Za-z0-9\-]+"""), " ")
                .replace(Regex("""[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?"""), " ")
        }
        val words = nonWordRegex.replace(candidate, " ")
            .split(multiSpaceRegex)
            .map { it.trim() }
            .filter { w ->
                w.length >= 2 &&
                    !w.all { it.isDigit() } &&
                    normalizeSimpleArabic(w) !in fillerTokensForKey &&
                    w.lowercase() !in fillerTokensForKey
            }
        val joined = words.take(3).joinToString(" ").trim()
        return if (joined.length >= 2) joined else ""
    }

    fun diceSimilarity(a: String, b: String): Double {
        val na = normalizeRich(a).replace(" ", "")
        val nb = normalizeRich(b).replace(" ", "")
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        if (na.length < 2 || nb.length < 2) {
            return if (na.contains(nb) || nb.contains(na)) 0.8 else 0.0
        }
        val bigramsA = HashSet<String>(na.length)
        for (i in 0 until na.length - 1) bigramsA.add(na.substring(i, i + 2))
        val bigramsB = HashSet<String>(nb.length)
        for (i in 0 until nb.length - 1) bigramsB.add(nb.substring(i, i + 2))
        var intersection = 0
        for (bg in bigramsA) {
            if (bg in bigramsB) intersection++
        }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size).toDouble()
    }

    fun predict(
        queryText: String,
        typeFilter: TransactionType? = null,
        merchantRules: List<MerchantRuleEntity>,
        historicalTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>
    ): LocalPrediction {
        val cleanQuery = queryText.trim()
        if (cleanQuery.isBlank()) {
            return LocalPrediction(null, null, typeFilter, 0f, "NONE")
        }
        val richQuery = normalizeRich(cleanQuery)
        val queryTokens = tokenizeWithStems(richQuery)
        val legacyNorm = VoiceTransactionParser.normalizeKeyword(cleanQuery)
        val activeAccountIds = accounts.filter { !it.isArchived }.map { it.id }.toSet()
        val validCategories = categories.filter { typeFilter == null || it.type == typeFilter }
        val validCatMap = validCategories.associateBy { it.id }

        // Layer 1: Exact / Stem Match against learned MerchantRules (longest & highest hitCount wins)
        val exactMatch = merchantRules
            .filter { rule ->
                val ruleRich = normalizeRich(rule.keyword)
                val ruleTokens = tokenizeWithStems(ruleRich)
                val matchesText = (rule.normalizedKeyword.isNotBlank() && legacyNorm.contains(rule.normalizedKeyword)) ||
                    (ruleRich.isNotBlank() && (richQuery.contains(ruleRich) || ruleTokens.any { it in queryTokens && it.length >= 3 }))
                val matchesType = typeFilter == null || rule.preferredType == typeFilter ||
                    (rule.categoryId != null && validCatMap.containsKey(rule.categoryId))
                matchesText && matchesType
            }
            .maxByOrNull { it.normalizedKeyword.length * 10 + it.hitCount.coerceAtMost(50) }

        if (exactMatch != null) {
            val resolvedCat = exactMatch.categoryId?.takeIf { validCatMap.containsKey(it) }
            val resolvedAcc = exactMatch.accountId?.takeIf { it in activeAccountIds }
            return LocalPrediction(
                categoryId = resolvedCat,
                accountId = resolvedAcc,
                preferredType = typeFilter ?: exactMatch.preferredType,
                confidence = (0.86f + (exactMatch.hitCount.coerceAtMost(10) * 0.012f)).coerceAtMost(0.99f),
                source = "RULE_EXACT",
                matchedRuleKeyword = exactMatch.keyword
            )
        }

        // Layer 2: Fuzzy Dice similarity over MerchantRules (handles typos & dialect variants)
        val fuzzyCandidate = merchantRules
            .mapNotNull { rule ->
                val sim = maxOf(
                    diceSimilarity(cleanQuery, rule.keyword),
                    queryTokens.maxOfOrNull { tok -> diceSimilarity(tok, rule.keyword) } ?: 0.0
                )
                if (sim >= 0.65) rule to sim else null
            }
            .filter { (rule, _) ->
                typeFilter == null || rule.preferredType == typeFilter || (rule.categoryId != null && validCatMap.containsKey(rule.categoryId))
            }
            .maxByOrNull { it.second * 100 + it.first.hitCount }

        if (fuzzyCandidate != null) {
            val (rule, sim) = fuzzyCandidate
            return LocalPrediction(
                categoryId = rule.categoryId?.takeIf { validCatMap.containsKey(it) },
                accountId = rule.accountId?.takeIf { it in activeAccountIds },
                preferredType = typeFilter ?: rule.preferredType,
                confidence = (0.72f + (sim.toFloat() - 0.65f) * 0.6f).coerceIn(0.70f, 0.92f),
                source = "RULE_FUZZY",
                matchedRuleKeyword = rule.keyword
            )
        }

        // Layer 3: Dynamic Multinomial Naive Bayes across categorized historical Room transactions
        val relevantHistory = historicalTransactions.filter { tx ->
            tx.categoryId != null &&
                validCatMap.containsKey(tx.categoryId) &&
                tx.description.isNotBlank() &&
                (typeFilter == null || tx.type == typeFilter)
        }

        if (relevantHistory.isNotEmpty() && queryTokens.isNotEmpty()) {
            val docCountsByCat = HashMap<Long, Int>()
            val tokenFreqByCat = HashMap<Long, HashMap<String, Int>>()
            val totalTokensByCat = HashMap<Long, Int>()
            val accountFreqByCat = HashMap<Long, HashMap<Long, Int>>()
            val vocabulary = HashSet<String>()

            for (tx in relevantHistory) {
                val cid = tx.categoryId ?: continue
                docCountsByCat[cid] = (docCountsByCat[cid] ?: 0) + 1
                val txTokens = tokenizeWithStems(normalizeRich(tx.description))
                val map = tokenFreqByCat.getOrPut(cid) { HashMap() }
                for (t in txTokens) {
                    vocabulary.add(t)
                    map[t] = (map[t] ?: 0) + 1
                }
                totalTokensByCat[cid] = (totalTokensByCat[cid] ?: 0) + txTokens.size
                if (tx.accountId in activeAccountIds) {
                    val accMap = accountFreqByCat.getOrPut(cid) { HashMap() }
                    accMap[tx.accountId] = (accMap[tx.accountId] ?: 0) + 1
                }
            }

            val totalDocs = relevantHistory.size.toDouble()
            val vocabSize = vocabulary.size.coerceAtLeast(1).toDouble()

            var bestCatId: Long? = null
            var bestScore = Double.NEGATIVE_INFINITY
            var secondScore = Double.NEGATIVE_INFINITY
            var sharedTokenHitsBest = 0

            for ((cid, docCount) in docCountsByCat) {
                val catTokenMap = tokenFreqByCat[cid].orEmpty()
                val denom = (totalTokensByCat[cid] ?: 0).toDouble() + vocabSize
                var score = ln(docCount.toDouble() / totalDocs)
                var overlapCount = 0
                for (qt in queryTokens) {
                    val count = catTokenMap[qt] ?: 0
                    if (count > 0) overlapCount++
                    score += ln((count + 1.0) / denom)
                }
                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestCatId = cid
                    sharedTokenHitsBest = overlapCount
                } else if (score > secondScore) {
                    secondScore = score
                }
            }

            if (bestCatId != null && sharedTokenHitsBest > 0) {
                val margin = if (secondScore > Double.NEGATIVE_INFINITY) (bestScore - secondScore).coerceIn(0.0, 4.0) else 2.0
                val nbConfidence = (0.64f + (margin.toFloat() / 8.0f) + (sharedTokenHitsBest.coerceAtMost(3) * 0.04f))
                    .coerceIn(0.62f, 0.94f)
                val topAccount = accountFreqByCat[bestCatId]?.maxByOrNull { it.value }?.key
                val catType = validCatMap[bestCatId]?.type
                return LocalPrediction(
                    categoryId = bestCatId,
                    accountId = topAccount,
                    preferredType = typeFilter ?: catType,
                    confidence = nbConfidence,
                    source = "NAIVE_BAYES"
                )
            }
        }

        return LocalPrediction(null, null, typeFilter, 0f, "NONE")
    }

    fun parseTeachingCommand(
        rawPrompt: String,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>
    ): TeachingDirective? {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank()) return null

        val arPattern = Regex(
            """^(?:علمني|اتعلم|تذكر|احفظ|لما\s+اكتب|لو\s+لقيت|لما\s+يجي(?:لك)?)\s+(?:ان|أن|إن|كلمة)?\s*(.+?)\s+(?:ده|دي|هو|هي|يكون|تكون|مصروف|دخل|فئة|تبع|صنفه|صنفها|على\s+حساب)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val enPattern = Regex(
            """^(?:learn|teach|remember)(?:\s+that)?\s+(.+?)\s+(?:is|as|->|means|category)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )

        val match = arPattern.find(trimmed) ?: enPattern.find(trimmed) ?: return null
        val rawKeyword = match.groupValues[1]
            .replace(Regex("""^(?:كلمة|اسم|المحل|تاجر)\s+"""), "")
            .trim('"', '\'', '«', '»', ' ')
        val targetPhrase = match.groupValues[2].trim()
        if (rawKeyword.length < 2 || targetPhrase.isBlank()) return null

        val normTarget = normalizeRich(targetPhrase)
        val requestedType = when {
            normTarget.contains("دخل") || normTarget.contains("راتب") || normTarget.contains("income") || normTarget.contains("salary") -> TransactionType.INCOME
            normTarget.contains("مصروف") || normTarget.contains("expense") -> TransactionType.EXPENSE
            else -> null
        }

        val candidateCats = categories.filter { requestedType == null || it.type == requestedType }
        val matchedCategory = candidateCats.firstOrNull { cat ->
            val ar = normalizeRich(cat.nameAr)
            val en = cat.nameEn.lowercase()
            normTarget.contains(ar) || targetPhrase.lowercase().contains(en)
        } ?: candidateCats.maxByOrNull { cat ->
            maxOf(
                diceSimilarity(targetPhrase, cat.nameAr),
                diceSimilarity(targetPhrase, cat.nameEn)
            )
        }?.takeIf { cat ->
            maxOf(diceSimilarity(targetPhrase, cat.nameAr), diceSimilarity(targetPhrase, cat.nameEn)) >= 0.45
        } ?: VoiceTransactionParser.inferCategory(targetPhrase, candidateCats, emptyList(), requestedType ?: TransactionType.EXPENSE)

        val matchedAccount = accounts.filter { !it.isArchived }.firstOrNull { acc ->
            normTarget.contains(normalizeRich(acc.name))
        }

        val resolvedType = matchedCategory?.type ?: requestedType ?: TransactionType.EXPENSE
        return TeachingDirective(
            keyword = rawKeyword,
            categoryId = matchedCategory?.id,
            categoryNameAr = matchedCategory?.nameAr,
            categoryNameEn = matchedCategory?.nameEn,
            accountId = matchedAccount?.id,
            accountName = matchedAccount?.name,
            preferredType = resolvedType
        )
    }

    fun isMemoryInspectionQuery(lower: String): Boolean {
        val norm = normalizeRich(lower)
        val cues = listOf(
            "اتعلمت", "تعلمت", "ذاكرتك", "قواعد التجار", "القواعد المتعلمة", "مستوى ذكاء",
            "ذكائك", "اتعلمت ايه", "ايه اللي اتعلمته", "what did you learn", "what have you learned",
            "learned rules", "local memory", "ai memory", "merchant rules"
        )
        return cues.any { norm.contains(normalizeRich(it)) }
    }

    fun computeMemoryStats(
        merchantRules: List<MerchantRuleEntity>,
        transactions: List<TransactionEntity>
    ): LocalMemoryStats {
        val ruleCount = merchantRules.size
        val totalHits = merchantRules.sumOf { it.hitCount }
        val indexedTxCount = transactions.count { it.categoryId != null && it.description.isNotBlank() }
        val totalSignals = ruleCount * 2 + indexedTxCount
        val (tierAr, tierEn) = when {
            totalSignals >= 40 -> "خبير متكيف" to "Adaptive Expert"
            totalSignals >= 15 -> "نشط ومتطور" to "Active Learner"
            totalSignals >= 4 -> "في مرحلة التعلّم" to "Learning"
            else -> "جاهز للتعلّم" to "Ready to Learn"
        }
        return LocalMemoryStats(
            learnedRuleCount = ruleCount,
            totalRuleHits = totalHits,
            indexedTransactionCount = indexedTxCount,
            topRules = merchantRules.sortedByDescending { it.hitCount }.take(5),
            adaptiveTierAr = tierAr,
            adaptiveTierEn = tierEn
        )
    }

    fun formatMemoryInspectionReply(
        stats: LocalMemoryStats,
        categories: List<CategoryEntity>,
        lang: AppLanguage
    ): String {
        val catMap = categories.associateBy { it.id }
        val topLines = stats.topRules.joinToString("\n") { rule ->
            val catName = rule.categoryId?.let { cid ->
                catMap[cid]?.let { if (lang == AppLanguage.EN) it.nameEn else it.nameAr }
            } ?: if (lang == AppLanguage.EN) rule.preferredType.name else "عام"
            "• «${rule.keyword}» ← $catName (${rule.hitCount}x)"
        }
        return if (lang == AppLanguage.EN) {
            buildString {
                appendLine("Local Self-Learning Brain (${stats.adaptiveTierEn}):")
                appendLine("• Learned Merchant Rules: ${stats.learnedRuleCount} (${stats.totalRuleHits} reinforcement hits)")
                appendLine("• Indexed Historical Transactions (Naive-Bayes): ${stats.indexedTransactionCount}")
                if (topLines.isNotBlank()) {
                    appendLine("Top Learned Patterns:")
                    appendLine(topLines)
                }
                append("Tip: You can teach me anytime by saying: \"Learn that Talabat is Food\".")
            }.trim()
        } else {
            buildString {
                appendLine("ذاكرة الذكاء المحلي الذاتي (المستوى: ${stats.adaptiveTierAr}):")
                appendLine("• قواعد التجار والعادات المتعلمة تلقائياً: ${stats.learnedRuleCount} قاعدة (بإجمالي ${stats.totalRuleHits} مرة تطابق)")
                appendLine("• المعاملات المفهرسة للتعلّم الإحصائي (Naive Bayes): ${stats.indexedTransactionCount} معاملة")
                if (topLines.isNotBlank()) {
                    appendLine("أبرز ما تعلمته من استخدامك:")
                    appendLine(topLines)
                }
                append("تقدر تعلمني قاعدة جديدة فوراً بجملة مثل: «علمني ان طلبات مصروف أكل».")
            }.trim()
        }
    }
}
