package com.example.domain.reader

import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType
import java.security.MessageDigest

/**
 * Local offline financial message parser using a regex-based Strategy Pattern.
 * Handles Arabic (including Eastern Arabic digits) and English bank/wallet SMS formats.
 */
class FinancialMessageParser(
    private val registry: ParsingRulesRegistry = ParsingRulesRegistry(),
    customStrategies: List<SmsParsingStrategy>? = null
) {
    private val strategies: List<SmsParsingStrategy> =
        (customStrategies ?: defaultStrategies(registry))
            .sortedByDescending { it.priority }

    fun getRegisteredStrategies(): List<SmsParsingStrategy> = strategies

    fun parseBatch(
        messages: List<Triple<String, String, Long>>
    ): List<ParseOutcome> = messages.map { (sender, body, ts) ->
        parse(rawBody = body, sender = sender, messageTimestampMillis = ts)
    }

    fun parse(
        rawBody: String,
        sender: String = "",
        messageTimestampMillis: Long = System.currentTimeMillis(),
        knownAccountBalancePiasters: Long? = null,
        learnedPreferredType: TransactionType? = null
    ): ParseOutcome {
        val body = rawBody.trim()
        if (body.isBlank()) {
            return ParseOutcome.IgnoredNonFinancial("Empty message")
        }

        val normalizedBody = normalizeArabicDigits(body)
        val lower = normalizedBody.lowercase()

        // 1. Strict OTP / Verification / Security code filtering (NEVER store or treat as financial)
        if (Companion.isOtpOrSecurityMessage(lower)) {
            return ParseOutcome.IgnoredOtpOrSecurity("OTP / Security verification message")
        }

        // 2. Select highest-priority matching regex parsing strategy
        var selectedStrategy: SmsParsingStrategy? = null
        var extracted: StrategyExtractedFields? = null

        for (strategy in strategies) {
            if (strategy.supports(sender, normalizedBody)) {
                val attempt = strategy.extract(normalizedBody, sender, messageTimestampMillis)
                if (attempt != null) {
                    selectedStrategy = strategy
                    extracted = attempt
                    break
                }
            }
        }

        if (extracted == null) {
            val promoReason = if (isMarketingPromo(lower)) {
                "Promotional or marketing message"
            } else {
                "No financial transaction markers found"
            }
            return ParseOutcome.IgnoredNonFinancial(promoReason)
        }

        // 3. Balance inquiry check (must NOT be recorded as a transaction)
        if (extracted.operationKind == MessageOperationKind.BALANCE_INQUIRY) {
            return ParseOutcome.BalanceInquiryOnly(
                sourceName = extracted.bankOrProviderName,
                balancePiasters = extracted.balanceAfterPiasters ?: extracted.amountPiasters
            )
        }

        val finalAmount = extracted.amountPiasters ?: 0L
        val directionAssessment = RegexSmsExtractor.classifyDirectionAndKind(
            normalizedBody = normalizedBody,
            balanceAfterPiasters = extracted.balanceAfterPiasters,
            knownAccountBalancePiasters = knownAccountBalancePiasters,
            hasTransactionAmount = finalAmount > 0L,
            learnedPreferredType = learnedPreferredType
        )

        // Re-check pure balance inquiry after full contextual direction analysis
        if (directionAssessment.direction == MessageDirection.BALANCE_ONLY && finalAmount <= 0L) {
            return ParseOutcome.BalanceInquiryOnly(
                sourceName = extracted.bankOrProviderName,
                balancePiasters = extracted.balanceAfterPiasters ?: extracted.amountPiasters
            )
        }

        // 4. Reject marketing promo messages that have no clear transaction kind
        val resolvedOpKind = when {
            directionAssessment.direction != MessageDirection.UNKNOWN -> directionAssessment.operationKind
            extracted.operationKind != null -> extracted.operationKind
            else -> null
        }
        if (isMarketingPromo(lower) && resolvedOpKind == null) {
            return ParseOutcome.IgnoredNonFinancial("Promotional or marketing message")
        }

        if (resolvedOpKind == null && extracted.amountPiasters == null) {
            return ParseOutcome.IgnoredNonFinancial("No financial transaction markers found")
        }

        val finalKind = resolvedOpKind ?: MessageOperationKind.CARD_PURCHASE
        val mappedType = mapKindToTransactionType(finalKind)

        // Confidence scoring
        var confidence = 0.30f + extracted.confidenceBoost
        if (finalAmount > 0L) confidence += 0.25f
        if (resolvedOpKind != null) confidence += 0.12f
        if (directionAssessment.confidence >= 0.80f) confidence += 0.08f
        if (!extracted.referenceNumber.isNullOrBlank()) confidence += 0.10f
        confidence = confidence.coerceIn(0.1f, 1.0f)

        val dayBucket = extracted.transactionTimestampMillis / 86_400_000L
        val dedupFingerprint = buildFingerprint(
            sourceCode = extracted.sourceCode,
            kind = finalKind,
            amountPiasters = finalAmount,
            referenceNumber = extracted.referenceNumber,
            dayBucket = dayBucket,
            counterparty = extracted.counterpartyName,
            rawNormalized = normalizedBody
        )
        val feeFingerprint = if (extracted.feePiasters > 0L && finalKind != MessageOperationKind.TRANSFER_FEE) {
            "${dedupFingerprint}_fee"
        } else null

        val summary = buildSummary(
            sourceName = extracted.bankOrProviderName,
            kind = finalKind,
            counterparty = extracted.counterpartyName,
            referenceNumber = extracted.referenceNumber
        )

        val parsed = ParsedFinancialMessage(
            sourceCode = extracted.sourceCode,
            sourceName = extracted.bankOrProviderName,
            senderLabel = sender.ifBlank { extracted.bankOrProviderName },
            operationKind = finalKind,
            mappedType = mappedType,
            amountPiasters = finalAmount,
            feePiasters = extracted.feePiasters,
            currency = extracted.currency,
            transactionTimestampMillis = extracted.transactionTimestampMillis,
            counterpartyName = extracted.counterpartyName,
            referenceNumber = extracted.referenceNumber,
            balanceAfterPiasters = extracted.balanceAfterPiasters,
            confidenceScore = confidence,
            dedupFingerprint = dedupFingerprint,
            feeDedupFingerprint = feeFingerprint,
            defaultAccountHint = extracted.defaultAccountHint,
            extractedSummary = summary,
            isIncomplete = (finalAmount <= 0L || resolvedOpKind == null),
            matchedStrategyId = selectedStrategy?.strategyId ?: "generic",
            direction = directionAssessment.direction,
            directionReasonAr = directionAssessment.reasonAr,
            directionReasonEn = directionAssessment.reasonEn
        )

        return if (parsed.isIncomplete) {
            val missing = if (finalAmount <= 0L) "amount" else "operation_type"
            ParseOutcome.IncompleteNeedsReview(parsed, missing)
        } else {
            ParseOutcome.Financial(parsed)
        }
    }

    private fun isOtpOrSecurityMessage(lower: String): Boolean =
        Companion.isOtpOrSecurityMessage(lower)

    private fun isMarketingPromo(lower: String): Boolean {
        val promoTerms = listOf(
            "عرض خاص", "خصم يصل", "اكسب", "اشترك الآن", "فرصة", "كاش باك يصل",
            "special offer", "win ", "discount", "subscribe now", "promo"
        )
        return promoTerms.any { lower.contains(it) }
    }

    private fun mapKindToTransactionType(kind: MessageOperationKind): TransactionType = when (kind) {
        MessageOperationKind.INCOMING_TRANSFER,
        MessageOperationKind.DEPOSIT,
        MessageOperationKind.REFUND,
        MessageOperationKind.SALARY -> TransactionType.INCOME

        MessageOperationKind.OUTGOING_TRANSFER,
        MessageOperationKind.WITHDRAWAL,
        MessageOperationKind.CARD_PURCHASE,
        MessageOperationKind.BILL_PAYMENT,
        MessageOperationKind.TRANSFER_FEE,
        MessageOperationKind.BALANCE_INQUIRY -> TransactionType.EXPENSE

        MessageOperationKind.INTERNAL_TRANSFER -> TransactionType.TRANSFER
    }

    private fun buildFingerprint(
        sourceCode: String,
        kind: MessageOperationKind,
        amountPiasters: Long,
        referenceNumber: String?,
        dayBucket: Long,
        counterparty: String?,
        rawNormalized: String
    ): String {
        val basis = if (!referenceNumber.isNullOrBlank()) {
            "REF|$sourceCode|${referenceNumber.uppercase()}|$kind|$amountPiasters"
        } else {
            val cp = counterparty?.lowercase()?.trim() ?: rawNormalized.take(40).lowercase()
            "MSG|$sourceCode|$kind|$amountPiasters|$dayBucket|$cp"
        }
        return sha256(basis)
    }

    private fun buildSummary(
        sourceName: String,
        kind: MessageOperationKind,
        counterparty: String?,
        referenceNumber: String?
    ): String {
        val kindAr = when (kind) {
            MessageOperationKind.INCOMING_TRANSFER -> "تحويل وارد"
            MessageOperationKind.OUTGOING_TRANSFER -> "تحويل صادر"
            MessageOperationKind.DEPOSIT -> "إيداع"
            MessageOperationKind.WITHDRAWAL -> "سحب"
            MessageOperationKind.CARD_PURCHASE -> "شراء بالبطاقة"
            MessageOperationKind.BILL_PAYMENT -> "دفع فاتورة"
            MessageOperationKind.TRANSFER_FEE -> "رسوم تحويل"
            MessageOperationKind.REFUND -> "استرداد مبلغ"
            MessageOperationKind.SALARY -> "استلام راتب"
            MessageOperationKind.INTERNAL_TRANSFER -> "تحويل داخلي"
            MessageOperationKind.BALANCE_INQUIRY -> "استعلام رصيد"
        }
        return buildString {
            append(kindAr)
            append(" • ")
            append(sourceName)
            if (!counterparty.isNullOrBlank()) {
                append(" (")
                append(counterparty)
                append(")")
            }
            if (!referenceNumber.isNullOrBlank()) {
                append(" #")
                append(referenceNumber)
            }
        }
    }

    private fun normalizeArabicDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '٫' -> sb.append('.')
                '،' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
        private val otpTerms = listOf(
            "otp", "verification code", "one-time password", "one time password",
            "passcode", "do not share", "security code", "auth code",
            "رمز التحقق", "كود التحقق", "كود التفعيل", "رمز التفعيل",
            "الرقم السري المتغير", "لا تشارك", "كود التأكيد", "رمز التأكيد", "رقم سري"
        )

        fun isOtpOrSecurityMessage(rawOrLower: String): Boolean {
            val lower = rawOrLower.lowercase()
            return otpTerms.any { lower.contains(it) }
        }

        private val financialCurrencyOrCueRegex = Regex(
            """(ج\.م|جنيه|egp|usd|sar|eur|instapay|انستاباي|فودافون\s*كاش|vodafone\s*cash|تم خصم|تم استلام|تم تحويل|تم إيداع|تم سحب|عملية شراء|debited|credited|withdrawn|transferred|purchase)""",
            RegexOption.IGNORE_CASE
        )

        fun looksLikeFinancialNotification(sender: String, body: String): Boolean {
            val combined = "$sender $body".trim()
            if (combined.length < 10) return false
            if (isOtpOrSecurityMessage(combined)) return false
            val hasDigit = combined.any { it.isDigit() || it in '٠'..'٩' }
            return hasDigit && financialCurrencyOrCueRegex.containsMatchIn(combined)
        }

        fun defaultStrategies(registry: ParsingRulesRegistry = ParsingRulesRegistry()): List<SmsParsingStrategy> = listOf(
            InstaPaySmsStrategy(),
            EgyptianWalletSmsStrategy(),
            EgyptianBankSmsStrategy(),
            RegistryRuleSmsStrategy(registry),
            GenericBilingualSmsStrategy()
        )

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
