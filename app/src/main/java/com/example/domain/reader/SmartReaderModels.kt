package com.example.domain.reader

import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType

data class ParsingRule(
    val id: String,
    val sourceCode: String,
    val displayNameAr: String,
    val displayNameEn: String,
    val senderPatterns: List<String>,
    val bodyKeywords: List<String>,
    val defaultAccountHint: String // "BANK", "E_WALLET", "CASH"
)

data class ParsedFinancialMessage(
    val sourceCode: String,
    val sourceName: String,
    val senderLabel: String,
    val operationKind: MessageOperationKind,
    val mappedType: TransactionType,
    val amountPiasters: Long,
    val feePiasters: Long,
    val currency: String,
    val transactionTimestampMillis: Long,
    val counterpartyName: String?,
    val referenceNumber: String?,
    val balanceAfterPiasters: Long?,
    val confidenceScore: Float,
    val dedupFingerprint: String,
    val feeDedupFingerprint: String?,
    val defaultAccountHint: String,
    val extractedSummary: String,
    val isIncomplete: Boolean = false,
    val matchedStrategyId: String = "generic"
)

sealed class ParseOutcome {
    data class Financial(val parsed: ParsedFinancialMessage) : ParseOutcome()
    data class BalanceInquiryOnly(val sourceName: String, val balancePiasters: Long?) : ParseOutcome()
    data class IgnoredOtpOrSecurity(val reason: String) : ParseOutcome()
    data class IgnoredNonFinancial(val reason: String) : ParseOutcome()
    data class IncompleteNeedsReview(val partial: ParsedFinancialMessage, val missingField: String) : ParseOutcome()
}
