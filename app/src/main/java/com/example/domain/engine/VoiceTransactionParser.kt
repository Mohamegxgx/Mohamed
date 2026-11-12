package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.domain.model.AccountType
import com.example.domain.model.TransactionType

data class ParsedVoiceTransaction(
    val type: TransactionType,
    val amountPiasters: Long?,
    val suggestedAccountId: Long?,
    val targetAccountId: Long?,
    val suggestedCategoryId: Long?,
    val description: String
)

object VoiceTransactionParser {

    private val numberRegex = Regex("""([0-9٠-٩]+(?:[.,٫][0-9٠-٩]{1,2})?)""")

    private val incomeKeywords = listOf(
        "جالي", "استلمت", "وصلني", "دخل", "راتب", "مرتب", "قبضت", "مكافأة", "فريلانس", "أرباح",
        "received", "salary", "income", "bonus", "freelance", "earned", "refund", "استرداد"
    )
    private val transferKeywords = listOf(
        "حولت", "تحويل", "نقلت", "transfer", "moved"
    )
    private val expenseKeywords = listOf(
        "صرفت", "دفعت", "اشتريت", "شراء", "أكلت", "طلبت", "فواتير", "مصروف", "spent", "paid", "bought", "expense"
    )

    private val categoryKeywordMap: List<Pair<String, List<String>>> = listOf(
        "Food & Drinks" to listOf("أكل", "اكل", "مطعم", "غدا", "فطار", "عشا", "قهوة", "كافيه", "سوبرماركت", "طلبات", "food", "coffee", "restaurant", "lunch", "dinner", "talabat", "groceries"),
        "Transport" to listOf("مواصلات", "أوبر", "اوبر", "كريم", "تاكسي", "بنزين", "مترو", "باص", "توصيل", "uber", "careem", "taxi", "fuel", "transport", "metro"),
        "Bills & Utilities" to listOf("فاتورة", "فواتير", "كهرباء", "مياه", "غاز", "إنترنت", "انترنت", "شحن", "رصيد", "موبايل", "bill", "electricity", "water", "internet", "mobile"),
        "Shopping" to listOf("تسوق", "ملابس", "شراء", "أمازون", "امازون", "نون", "مول", "shopping", "amazon", "noon", "clothes"),
        "Healthcare" to listOf("دواء", "صيدلية", "دكتور", "مستشفى", "علاج", "كشف", "health", "pharmacy", "doctor", "medicine"),
        "Housing & Rent" to listOf("إيجار", "ايجار", "سكن", "شقة", "صيانة", "rent", "housing", "apartment"),
        "Education" to listOf("كورس", "كتاب", "تعليم", "جامعة", "مدرسة", "course", "book", "education"),
        "Subscriptions" to listOf("اشتراك", "نتفلكس", "سبوتيفاي", "يوتيوب", "subscription", "netflix", "spotify"),
        "Salary" to listOf("راتب", "مرتب", "salary", "payroll"),
        "Freelance" to listOf("فريلانس", "مشروع", "عمل حر", "freelance", "project")
    )

    fun normalizeKeyword(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun parse(
        rawPhrase: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity> = emptyList()
    ): ParsedVoiceTransaction {
        val trimmed = rawPhrase.trim()
        val lower = trimmed.lowercase()

        // 1. Amount extraction
        val numberMatch = numberRegex.find(trimmed)
        val amountPiasters = numberMatch?.groupValues?.getOrNull(1)?.let { MoneyFormatter.parseToPiasters(it) }

        // 2. Learned Merchant Rule match (highest priority)
        val matchedRule = merchantRules.firstOrNull { rule ->
            rule.normalizedKeyword.isNotBlank() && normalizeKeyword(trimmed).contains(rule.normalizedKeyword)
        }

        // 3. Transaction Type
        val type = when {
            transferKeywords.any { lower.contains(it) } -> TransactionType.TRANSFER
            incomeKeywords.any { lower.contains(it) } -> TransactionType.INCOME
            expenseKeywords.any { lower.contains(it) } -> TransactionType.EXPENSE
            matchedRule != null -> matchedRule.preferredType
            else -> TransactionType.EXPENSE
        }

        // 4. Accounts matching
        val activeAccounts = accounts.filter { !it.isArchived }
        val matchedAccount = matchedRule?.accountId?.let { id -> activeAccounts.firstOrNull { it.id == id } }
            ?: findAccountByHint(lower, activeAccounts)
            ?: activeAccounts.firstOrNull()

        var targetAccountId: Long? = null
        if (type == TransactionType.TRANSFER && activeAccounts.size >= 2) {
            val toSplit = lower.split(Regex("""\b(?:إلى|الى|لـ|ل|to)\b"""), limit = 2)
            val targetHintText = toSplit.getOrNull(1).orEmpty()
            val targetAcc = findAccountByHint(targetHintText, activeAccounts.filter { it.id != matchedAccount?.id })
                ?: activeAccounts.firstOrNull { it.id != matchedAccount?.id }
            targetAccountId = targetAcc?.id
        }

        // 5. Category matching
        val matchedCategory = if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            matchedRule?.categoryId?.let { cid -> categories.firstOrNull { it.id == cid && it.type == type } }
                ?: findCategoryByKeywords(lower, categories, type)
        } else {
            null
        }

        // 6. Clean human description
        val cleanedDesc = cleanDescription(trimmed, numberMatch?.value)

        return ParsedVoiceTransaction(
            type = type,
            amountPiasters = amountPiasters,
            suggestedAccountId = matchedAccount?.id,
            targetAccountId = targetAccountId,
            suggestedCategoryId = matchedCategory?.id,
            description = cleanedDesc
        )
    }

    private fun findAccountByHint(text: String, accounts: List<AccountEntity>): AccountEntity? {
        if (text.isBlank() || accounts.isEmpty()) return null
        accounts.firstOrNull { text.contains(it.name.lowercase()) }?.let { return it }
        return when {
            text.contains("كاش") || text.contains("نقد") || text.contains("cash") ->
                accounts.firstOrNull { it.type == AccountType.CASH }
            text.contains("بنك") || text.contains("فيزا") || text.contains("بطاقة") || text.contains("bank") || text.contains("card") ->
                accounts.firstOrNull { it.type == AccountType.BANK }
            text.contains("محفظ") || text.contains("فودافون") || text.contains("إنستا") || text.contains("wallet") || text.contains("cash") ->
                accounts.firstOrNull { it.type == AccountType.E_WALLET }
            else -> null
        }
    }

    private fun findCategoryByKeywords(
        lower: String,
        categories: List<CategoryEntity>,
        type: TransactionType
    ): CategoryEntity? {
        val typed = categories.filter { it.type == type }
        // Direct match against user category names
        typed.firstOrNull {
            lower.contains(it.nameAr.lowercase()) || lower.contains(it.nameEn.lowercase())
        }?.let { return it }

        // Synonym match
        for ((enName, keywords) in categoryKeywordMap) {
            if (keywords.any { lower.contains(it) }) {
                typed.firstOrNull { it.nameEn.equals(enName, ignoreCase = true) }?.let { return it }
            }
        }
        return null
    }

    private fun cleanDescription(raw: String, amountToken: String?): String {
        var out = raw
        if (!amountToken.isNullOrBlank()) {
            out = out.replace(amountToken, " ")
        }
        val fillerWords = listOf(
            "جنيه مصري", "جنيه", "ج.م", "ريال", "دولار", "egp", "usd", "sar",
            "صرفت", "دفعت", "اشتريت", "جالي", "استلمت", "حولت", "spent", "paid", "received"
        )
        for (w in fillerWords) {
            out = out.replace(Regex("""(?i)\b${Regex.escape(w)}\b"""), " ")
        }
        return out.replace(Regex("""\s+"""), " ").trim().ifBlank { raw }
    }
}
