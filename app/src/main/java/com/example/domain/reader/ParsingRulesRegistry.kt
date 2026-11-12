package com.example.domain.reader

class ParsingRulesRegistry(
    initialRules: List<ParsingRule> = defaultRules()
) {
    private val rules = initialRules.toMutableList()

    fun getRules(): List<ParsingRule> = rules.toList()

    fun registerRule(rule: ParsingRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(0, rule)
    }

    fun matchSource(sender: String, body: String): ParsingRule? {
        val combined = "${sender.lowercase()} ${body.lowercase()}"
        return rules.firstOrNull { rule ->
            rule.senderPatterns.any { combined.contains(it.lowercase()) } ||
                rule.bodyKeywords.any { combined.contains(it.lowercase()) }
        }
    }

    companion object {
        fun defaultRules(): List<ParsingRule> = listOf(
            ParsingRule(
                id = "rule_vf_cash",
                sourceCode = "VODAFONE_CASH",
                displayNameAr = "فودافون كاش",
                displayNameEn = "Vodafone Cash",
                senderPatterns = listOf("vf-cash", "vfcash", "vodafone", "فودافون كاش"),
                bodyKeywords = listOf("فودافون كاش", "vodafone cash", "vf cash"),
                defaultAccountHint = "E_WALLET"
            ),
            ParsingRule(
                id = "rule_orange_cash",
                sourceCode = "ORANGE_CASH",
                displayNameAr = "أورنج كاش",
                displayNameEn = "Orange Cash",
                senderPatterns = listOf("orange cash", "orangecash", "orange", "أورنج كاش", "اورنج كاش"),
                bodyKeywords = listOf("أورنج كاش", "اورنج كاش", "orange cash"),
                defaultAccountHint = "E_WALLET"
            ),
            ParsingRule(
                id = "rule_etisalat_cash",
                sourceCode = "ETISALAT_CASH",
                displayNameAr = "اتصالات كاش",
                displayNameEn = "Etisalat Cash",
                senderPatterns = listOf("etisalat cash", "e& cash", "etisalat", "اتصالات كاش"),
                bodyKeywords = listOf("اتصالات كاش", "etisalat cash", "e& cash"),
                defaultAccountHint = "E_WALLET"
            ),
            ParsingRule(
                id = "rule_instapay",
                sourceCode = "INSTAPAY",
                displayNameAr = "إنستاباي",
                displayNameEn = "InstaPay",
                senderPatterns = listOf("instapay", "ipn", "إنستاباي", "انستاباي"),
                bodyKeywords = listOf("instapay", "إنستاباي", "انستاباي", "ipn"),
                defaultAccountHint = "BANK"
            ),
            ParsingRule(
                id = "rule_nbe",
                sourceCode = "NBE",
                displayNameAr = "البنك الأهلي المصري",
                displayNameEn = "NBE",
                senderPatterns = listOf("nbe", "alahly", "البنك الأهلي", "الأهلي المصري"),
                bodyKeywords = listOf("البنك الأهلي", "nbe", "alahly"),
                defaultAccountHint = "BANK"
            ),
            ParsingRule(
                id = "rule_banque_misr",
                sourceCode = "BANQUE_MISR",
                displayNameAr = "بنك مصر",
                displayNameEn = "Banque Misr",
                senderPatterns = listOf("banque misr", "bm", "بنك مصر"),
                bodyKeywords = listOf("بنك مصر", "banque misr"),
                defaultAccountHint = "BANK"
            ),
            ParsingRule(
                id = "rule_cib",
                sourceCode = "CIB",
                displayNameAr = "بنك CIB",
                displayNameEn = "CIB",
                senderPatterns = listOf("cib", "التجاري الدولي"),
                bodyKeywords = listOf("cib", "التجاري الدولي"),
                defaultAccountHint = "BANK"
            ),
            ParsingRule(
                id = "rule_qnb",
                sourceCode = "QNB",
                displayNameAr = "بنك QNB",
                displayNameEn = "QNB",
                senderPatterns = listOf("qnb", "بنك قطر"),
                bodyKeywords = listOf("qnb"),
                defaultAccountHint = "BANK"
            )
        )
    }
}
