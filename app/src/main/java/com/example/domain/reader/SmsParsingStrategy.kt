package com.example.domain.reader

import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale

data class StrategyExtractedFields(
    val sourceCode: String,
    val bankOrProviderName: String,
    val defaultAccountHint: String,
    val operationKind: MessageOperationKind?,
    val amountPiasters: Long?,
    val feePiasters: Long,
    val currency: String,
    val balanceAfterPiasters: Long?,
    val referenceNumber: String?,
    val counterpartyName: String?,
    val transactionTimestampMillis: Long,
    val confidenceBoost: Float = 0.25f,
    val directionAssessment: DirectionAssessment? = null
)

/**
 * Strategy Pattern contract for extracting structured financial transaction details
 * from bank and e-wallet SMS / notification formats.
 */
interface SmsParsingStrategy {
    val strategyId: String
    val priority: Int
    fun supports(sender: String, normalizedBody: String): Boolean
    fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields?
}

/**
 * Shared compiled regex utilities for Arabic and English financial SMS messages.
 */
object RegexSmsExtractor {
    private val currencyExplicitRegex = Regex(
        """(?:بمبلغ|مبلغ|قيمة|بقيمة|amount|sum|egp|ج\.م|جم|جنيه|usd|sar|eur|aed)\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val currencySuffixRegex = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:ج\.م|جم|جنيه|جنيهاً|egp|le|l\.e\.|usd|sar|eur|aed)""",
        RegexOption.IGNORE_CASE
    )
    private val fallbackAmountRegex = Regex("""(?<!\*)([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""")

    private val feeRegex = Regex(
        """(?:برسوم|رسوم|الرسوم|عمولة|مصاريف إدارية|fees?|charges?)\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:ج\.م|جم|جنيه|egp|le)?""",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        """(?:الرصيد\s*(?:المتاح|الحالي|بعد العملية)?(?:\s*حاليا)?|رصيدك\s*(?:المتاح|الحالي)?(?:\s*حاليا)?|available\s*balance|new\s*balance|current\s*balance|balance)\s*(?:هو|الآن|:|-|is)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val referenceRegex = Regex(
        """(?:رقم\s*(?:المرجع|مرجعي|العملية|الحركة)|المرجع|ref(?:erence)?(?:\s*no\.?)?|trx|txn|id)\s*[:#\-]?\s*([A-Z0-9\-]{4,24})""",
        RegexOption.IGNORE_CASE
    )

    private val counterpartyRegex = Regex(
        """(?:من|إلى|الى|لدى|عند|from|to|at)\s+([^\d\n،,.؛:]{2,32})""",
        RegexOption.IGNORE_CASE
    )

    private val dateRegex = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""")
    private val timeRegex = Regex("""(\d{1,2}:\d{2}(?:\s*[APMapm]{2})?)""")

    private val datePatterns = listOf(
        "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy HH:mm",
        "yyyy-MM-dd HH:mm",
        "dd/MM/yyyy hh:mm a",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "yyyy-MM-dd",
        "dd/MM/yy"
    )

    fun classifyDirectionAndKind(
        normalizedBody: String,
        balanceAfterPiasters: Long? = null,
        knownAccountBalancePiasters: Long? = null,
        hasTransactionAmount: Boolean = false,
        learnedPreferredType: TransactionType? = null
    ): DirectionAssessment = MessageDirectionClassifier.classify(
        rawBody = normalizedBody,
        balanceAfterPiasters = balanceAfterPiasters,
        knownAccountBalancePiasters = knownAccountBalancePiasters,
        hasTransactionAmount = hasTransactionAmount,
        learnedPreferredType = learnedPreferredType
    )

    fun extractOperationKind(normalizedBody: String): MessageOperationKind? {
        val assessment = MessageDirectionClassifier.classify(normalizedBody)
        return if (assessment.direction == MessageDirection.UNKNOWN) null else assessment.operationKind
    }

    fun detectCurrency(normalizedBody: String): String {
        val lower = normalizedBody.lowercase()
        return when {
            lower.contains("usd") || lower.contains("دولار") || lower.contains("$") -> "USD"
            lower.contains("sar") || lower.contains("ريال") -> "SAR"
            lower.contains("eur") || lower.contains("يورو") -> "EUR"
            lower.contains("aed") || lower.contains("درهم") -> "AED"
            else -> "EGP"
        }
    }

    fun extractAmount(normalizedBody: String): Long? {
        currencyExplicitRegex.find(normalizedBody)?.groupValues?.getOrNull(1)?.let { num ->
            MoneyFormatter.parseToPiasters(num)?.let { return it }
        }
        currencySuffixRegex.find(normalizedBody)?.groupValues?.getOrNull(1)?.let { num ->
            MoneyFormatter.parseToPiasters(num)?.let { return it }
        }
        fallbackAmountRegex.findAll(normalizedBody).forEach { match ->
            val raw = match.groupValues[1]
            val clean = raw.replace(",", "")
            if (clean.length == 4 && (clean.startsWith("202") || clean.startsWith("199"))) return@forEach
            MoneyFormatter.parseToPiasters(raw)?.let { return it }
        }
        return null
    }

    fun extractFee(normalizedBody: String): Long =
        feeRegex.find(normalizedBody)?.groupValues?.getOrNull(1)
            ?.let { MoneyFormatter.parseAllowZeroToPiasters(it) }
            ?.takeIf { it > 0L } ?: 0L

    fun extractBalanceAfter(normalizedBody: String): Long? =
        balanceRegex.find(normalizedBody)?.groupValues?.getOrNull(1)
            ?.let { MoneyFormatter.parseAllowZeroToPiasters(it) }

    fun extractReference(normalizedBody: String): String? =
        referenceRegex.find(normalizedBody)?.groupValues?.getOrNull(1)?.trim()

    fun extractCounterparty(normalizedBody: String): String? {
        val candidate = counterpartyRegex.find(normalizedBody)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val stopWords = setOf("حساب", "حسابك", "ماكينة", "البطاقة", "card", "account", "your", "the", "my")
        val cleaned = candidate.split(" ").take(4).joinToString(" ").trim()
        return if (cleaned.lowercase() in stopWords || cleaned.length < 2) null else cleaned
    }

    fun extractDateTimeMillis(normalizedBody: String, fallbackMillis: Long): Long {
        val dateStr = dateRegex.find(normalizedBody)?.groupValues?.getOrNull(1) ?: return fallbackMillis
        val timeStr = timeRegex.find(normalizedBody)?.groupValues?.getOrNull(1)
        val combined = if (timeStr != null) "$dateStr $timeStr" else dateStr

        for (pattern in datePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(combined)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return fallbackMillis
    }
}

/**
 * Strategy 1: InstaPay / IPN Instant Transfers (Arabic & English)
 */
class InstaPaySmsStrategy : SmsParsingStrategy {
    override val strategyId: String = "strategy_instapay"
    override val priority: Int = 100

    private val matchRegex = Regex("""(instapay|إنستاباي|انستاباي|\bipn\b)""", RegexOption.IGNORE_CASE)

    override fun supports(sender: String, normalizedBody: String): Boolean =
        matchRegex.containsMatchIn("$sender $normalizedBody")

    override fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields {
        return StrategyExtractedFields(
            sourceCode = "INSTAPAY",
            bankOrProviderName = "إنستاباي",
            defaultAccountHint = "BANK",
            operationKind = RegexSmsExtractor.extractOperationKind(normalizedBody),
            amountPiasters = RegexSmsExtractor.extractAmount(normalizedBody),
            feePiasters = RegexSmsExtractor.extractFee(normalizedBody),
            currency = RegexSmsExtractor.detectCurrency(normalizedBody),
            balanceAfterPiasters = RegexSmsExtractor.extractBalanceAfter(normalizedBody),
            referenceNumber = RegexSmsExtractor.extractReference(normalizedBody),
            counterpartyName = RegexSmsExtractor.extractCounterparty(normalizedBody),
            transactionTimestampMillis = RegexSmsExtractor.extractDateTimeMillis(normalizedBody, messageTimestampMillis),
            confidenceBoost = 0.25f
        )
    }
}

/**
 * Strategy 2: Egyptian Mobile E-Wallets (Vodafone Cash, Orange Cash, Etisalat Cash, WE Pay)
 */
class EgyptianWalletSmsStrategy : SmsParsingStrategy {
    override val strategyId: String = "strategy_egyptian_wallets"
    override val priority: Int = 90

    private val walletMatchers = listOf(
        Triple("VODAFONE_CASH", "فودافون كاش", Regex("""(vodafone\s*cash|vf-?cash|vodafone|فودافون\s*كاش|فودافون)""", RegexOption.IGNORE_CASE)),
        Triple("ORANGE_CASH", "أورنج كاش", Regex("""(orange\s*cash|orangecash|أورنج\s*كاش|اورنج\s*كاش)""", RegexOption.IGNORE_CASE)),
        Triple("ETISALAT_CASH", "اتصالات كاش", Regex("""(etisalat\s*cash|e&\s*cash|اتصالات\s*كاش)""", RegexOption.IGNORE_CASE)),
        Triple("WE_PAY", "WE Pay", Regex("""(we\s*pay|وي\s*باي)""", RegexOption.IGNORE_CASE))
    )

    override fun supports(sender: String, normalizedBody: String): Boolean {
        val combined = "$sender $normalizedBody"
        return walletMatchers.any { it.third.containsMatchIn(combined) }
    }

    override fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields? {
        val combined = "$sender $normalizedBody"
        val matched = walletMatchers.firstOrNull { it.third.containsMatchIn(combined) } ?: return null
        return StrategyExtractedFields(
            sourceCode = matched.first,
            bankOrProviderName = matched.second,
            defaultAccountHint = "E_WALLET",
            operationKind = RegexSmsExtractor.extractOperationKind(normalizedBody),
            amountPiasters = RegexSmsExtractor.extractAmount(normalizedBody),
            feePiasters = RegexSmsExtractor.extractFee(normalizedBody),
            currency = RegexSmsExtractor.detectCurrency(normalizedBody),
            balanceAfterPiasters = RegexSmsExtractor.extractBalanceAfter(normalizedBody),
            referenceNumber = RegexSmsExtractor.extractReference(normalizedBody),
            counterpartyName = RegexSmsExtractor.extractCounterparty(normalizedBody),
            transactionTimestampMillis = RegexSmsExtractor.extractDateTimeMillis(normalizedBody, messageTimestampMillis),
            confidenceBoost = 0.25f
        )
    }
}

/**
 * Strategy 3: Egyptian Banks & Cards (NBE, Banque Misr, CIB, QNB, HSBC, AlexBank)
 */
class EgyptianBankSmsStrategy : SmsParsingStrategy {
    override val strategyId: String = "strategy_egyptian_banks"
    override val priority: Int = 80

    private val bankMatchers = listOf(
        Triple("NBE", "البنك الأهلي المصري", Regex("""(\bnbe\b|alahly|البنك الأهلي|الأهلي المصري)""", RegexOption.IGNORE_CASE)),
        Triple("BANQUE_MISR", "بنك مصر", Regex("""(banque\s*misr|\bbm\b|بنك مصر)""", RegexOption.IGNORE_CASE)),
        Triple("CIB", "بنك CIB", Regex("""(\bcib\b|التجاري الدولي)""", RegexOption.IGNORE_CASE)),
        Triple("QNB", "بنك QNB", Regex("""(\bqnb\b|بنك قطر)""", RegexOption.IGNORE_CASE)),
        Triple("HSBC", "بنك HSBC", Regex("""(\bhsbc\b)""", RegexOption.IGNORE_CASE)),
        Triple("ALEXBANK", "بنك الإسكندرية", Regex("""(alex\s*bank|alexbank|بنك الإسكندرية|بنك الاسكندرية)""", RegexOption.IGNORE_CASE))
    )

    override fun supports(sender: String, normalizedBody: String): Boolean {
        val combined = "$sender $normalizedBody"
        return bankMatchers.any { it.third.containsMatchIn(combined) }
    }

    override fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields? {
        val combined = "$sender $normalizedBody"
        val matched = bankMatchers.firstOrNull { it.third.containsMatchIn(combined) } ?: return null
        return StrategyExtractedFields(
            sourceCode = matched.first,
            bankOrProviderName = matched.second,
            defaultAccountHint = "BANK",
            operationKind = RegexSmsExtractor.extractOperationKind(normalizedBody),
            amountPiasters = RegexSmsExtractor.extractAmount(normalizedBody),
            feePiasters = RegexSmsExtractor.extractFee(normalizedBody),
            currency = RegexSmsExtractor.detectCurrency(normalizedBody),
            balanceAfterPiasters = RegexSmsExtractor.extractBalanceAfter(normalizedBody),
            referenceNumber = RegexSmsExtractor.extractReference(normalizedBody),
            counterpartyName = RegexSmsExtractor.extractCounterparty(normalizedBody),
            transactionTimestampMillis = RegexSmsExtractor.extractDateTimeMillis(normalizedBody, messageTimestampMillis),
            confidenceBoost = 0.25f
        )
    }
}

/**
 * Strategy 4: Dynamic Registry Rules (Custom User-Added Financial Sources)
 */
class RegistryRuleSmsStrategy(
    private val registry: ParsingRulesRegistry
) : SmsParsingStrategy {
    override val strategyId: String = "strategy_registry_rules"
    override val priority: Int = 50

    override fun supports(sender: String, normalizedBody: String): Boolean =
        registry.matchSource(sender, normalizedBody) != null

    override fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields? {
        val rule = registry.matchSource(sender, normalizedBody) ?: return null
        return StrategyExtractedFields(
            sourceCode = rule.sourceCode,
            bankOrProviderName = rule.displayNameAr,
            defaultAccountHint = rule.defaultAccountHint,
            operationKind = RegexSmsExtractor.extractOperationKind(normalizedBody),
            amountPiasters = RegexSmsExtractor.extractAmount(normalizedBody),
            feePiasters = RegexSmsExtractor.extractFee(normalizedBody),
            currency = RegexSmsExtractor.detectCurrency(normalizedBody),
            balanceAfterPiasters = RegexSmsExtractor.extractBalanceAfter(normalizedBody),
            referenceNumber = RegexSmsExtractor.extractReference(normalizedBody),
            counterpartyName = RegexSmsExtractor.extractCounterparty(normalizedBody),
            transactionTimestampMillis = RegexSmsExtractor.extractDateTimeMillis(normalizedBody, messageTimestampMillis),
            confidenceBoost = 0.20f
        )
    }
}

/**
 * Strategy 5: Generic Bilingual Arabic/English SMS Fallback Strategy
 */
class GenericBilingualSmsStrategy : SmsParsingStrategy {
    override val strategyId: String = "strategy_generic_bilingual"
    override val priority: Int = 10

    override fun supports(sender: String, normalizedBody: String): Boolean = true

    override fun extract(
        normalizedBody: String,
        sender: String,
        messageTimestampMillis: Long
    ): StrategyExtractedFields? {
        val kind = RegexSmsExtractor.extractOperationKind(normalizedBody)
        val amount = RegexSmsExtractor.extractAmount(normalizedBody)
        // If no recognized bank/wallet matched, only accept when explicit operation keywords exist
        if (kind == null) return null

        val lower = normalizedBody.lowercase()
        val inferredCode = when {
            lower.contains("wallet") || lower.contains("محفظة") -> "E_WALLET_GENERIC"
            sender.isNotBlank() -> sender.uppercase().replace(Regex("[^A-Z0-9_]"), "_").take(20)
            else -> "BANK_GENERIC"
        }
        val accountHint = if (inferredCode == "E_WALLET_GENERIC") "E_WALLET" else "BANK"

        return StrategyExtractedFields(
            sourceCode = inferredCode,
            bankOrProviderName = sender.ifBlank { "مصدر مالي" },
            defaultAccountHint = accountHint,
            operationKind = kind,
            amountPiasters = amount,
            feePiasters = RegexSmsExtractor.extractFee(normalizedBody),
            currency = RegexSmsExtractor.detectCurrency(normalizedBody),
            balanceAfterPiasters = RegexSmsExtractor.extractBalanceAfter(normalizedBody),
            referenceNumber = RegexSmsExtractor.extractReference(normalizedBody),
            counterpartyName = RegexSmsExtractor.extractCounterparty(normalizedBody),
            transactionTimestampMillis = RegexSmsExtractor.extractDateTimeMillis(normalizedBody, messageTimestampMillis),
            confidenceBoost = 0.0f
        )
    }
}
