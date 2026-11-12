package com.example.reader

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.PendingStatus
import com.example.domain.model.TransactionType
import com.example.domain.reader.DuplicateDetector
import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.ParseOutcome
import com.example.domain.reader.ParsedFinancialMessage
import kotlinx.coroutines.flow.first

data class IncomingRawMessage(
    val sender: String,
    val body: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ImportBatchResult(
    val totalScanned: Int,
    val financialDetected: Int,
    val pendingCreated: Int,
    val duplicatesSkipped: Int,
    val ignoredFiltered: Int
)

class SmartReaderProcessor(
    private val db: AppDatabase,
    private val prefsRepo: UserPreferencesRepository,
    private val parser: FinancialMessageParser = FinancialMessageParser()
) {
    private val duplicateDetector = DuplicateDetector(
        transactionDao = db.transactionDao(),
        pendingDao = db.pendingTransactionDao(),
        importedMessageDao = db.importedMessageDao()
    )

    suspend fun ingestMessages(
        messages: List<IncomingRawMessage>,
        channel: MessageChannel
    ): ImportBatchResult {
        val prefs = prefsRepo.preferences.first()
        if (!prefs.smartReaderEnabled || messages.isEmpty()) {
            return ImportBatchResult(messages.size, 0, 0, 0, messages.size)
        }

        val sources = db.financialSourceDao().getAllOnce()
        val accounts = db.accountDao().getAllOnce().filter { !it.isArchived }
        val categories = db.categoryDao().getAllOnce()
        val merchantRules = db.merchantRuleDao().getAllOnce()

        var financialDetected = 0
        var pendingCreated = 0
        var duplicatesSkipped = 0
        var ignoredFiltered = 0

        db.withTransaction {
            for (msg in messages) {
                val outcome = parser.parse(
                    rawBody = msg.body,
                    sender = msg.sender,
                    messageTimestampMillis = msg.timestampMillis
                )

                when (outcome) {
                    is ParseOutcome.IgnoredOtpOrSecurity -> {
                        // Never persist OTP or personal/security messages!
                        ignoredFiltered++
                    }
                    is ParseOutcome.IgnoredNonFinancial -> {
                        // Never persist non-financial personal messages!
                        ignoredFiltered++
                    }
                    is ParseOutcome.BalanceInquiryOnly -> {
                        if (prefs.readerIgnoreBalanceInquiries) {
                            ignoredFiltered++
                        } else {
                            // Still NEVER create a transaction or modify balances; log audit status only
                            val hash = FinancialMessageParser.sha256(
                                "BAL|${outcome.sourceName}|${outcome.balancePiasters}|${msg.timestampMillis / 86_400_000L}"
                            )
                            db.importedMessageDao().insertIgnore(
                                ImportedMessageEntity(
                                    sourceName = outcome.sourceName,
                                    channel = channel,
                                    senderLabel = msg.sender.ifBlank { outcome.sourceName },
                                    fingerprintHash = hash,
                                    status = ImportedMessageStatus.NON_FINANCIAL,
                                    confidenceScore = 1.0f,
                                    messageTimestampMillis = msg.timestampMillis
                                )
                            )
                            ignoredFiltered++
                        }
                    }
                    is ParseOutcome.Financial -> {
                        val parsed = outcome.parsed
                        financialDetected++
                        val result = handleParsedMessage(parsed, channel, sources, accounts, categories, merchantRules, msg.body)
                        when (result) {
                            HandleResult.CREATED -> pendingCreated++
                            HandleResult.DUPLICATE -> duplicatesSkipped++
                            HandleResult.IGNORED_SOURCE -> ignoredFiltered++
                        }
                    }
                    is ParseOutcome.IncompleteNeedsReview -> {
                        val parsed = outcome.partial
                        financialDetected++
                        val result = handleParsedMessage(parsed, channel, sources, accounts, categories, merchantRules, msg.body)
                        when (result) {
                            HandleResult.CREATED -> pendingCreated++
                            HandleResult.DUPLICATE -> duplicatesSkipped++
                            HandleResult.IGNORED_SOURCE -> ignoredFiltered++
                        }
                    }
                }
            }

            db.importHistoryDao().insert(
                ImportHistoryEntity(
                    channel = channel,
                    totalScanned = messages.size,
                    financialDetected = financialDetected,
                    pendingCreated = pendingCreated,
                    duplicatesSkipped = duplicatesSkipped,
                    ignoredFiltered = ignoredFiltered
                )
            )
        }

        val now = System.currentTimeMillis()
        prefsRepo.setLastImportTimestamp(now)

        return ImportBatchResult(
            totalScanned = messages.size,
            financialDetected = financialDetected,
            pendingCreated = pendingCreated,
            duplicatesSkipped = duplicatesSkipped,
            ignoredFiltered = ignoredFiltered
        )
    }

    private enum class HandleResult { CREATED, DUPLICATE, IGNORED_SOURCE }

    private suspend fun handleParsedMessage(
        parsed: ParsedFinancialMessage,
        channel: MessageChannel,
        sources: List<com.example.data.local.entity.FinancialSourceEntity>,
        accounts: List<com.example.data.local.entity.AccountEntity>,
        categories: List<com.example.data.local.entity.CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        rawBody: String
    ): HandleResult {
        val matchedSource = sources.firstOrNull {
            it.code == parsed.sourceCode ||
                it.senderKeywords.split(",").any { kw -> kw.isNotBlank() && parsed.senderLabel.contains(kw.trim(), ignoreCase = true) }
        }
        if (matchedSource != null && (!matchedSource.isEnabled || matchedSource.isIgnored)) {
            return HandleResult.IGNORED_SOURCE
        }

        if (duplicateDetector.isDuplicate(parsed.dedupFingerprint, parsed.referenceNumber, parsed.sourceName)) {
            return HandleResult.DUPLICATE
        }

        val msgId = db.importedMessageDao().insertIgnore(
            ImportedMessageEntity(
                sourceId = matchedSource?.id,
                sourceName = parsed.sourceName,
                channel = channel,
                senderLabel = parsed.senderLabel,
                fingerprintHash = parsed.dedupFingerprint,
                status = ImportedMessageStatus.PENDING,
                confidenceScore = parsed.confidenceScore,
                messageTimestampMillis = parsed.transactionTimestampMillis
            )
        )
        if (msgId == -1L) {
            return HandleResult.DUPLICATE
        }

        val searchHaystack = VoiceTransactionParser.normalizeKeyword(
            "${parsed.counterpartyName.orEmpty()} ${parsed.extractedSummary} $rawBody"
        )
        val matchedMerchantRule = merchantRules.firstOrNull { rule ->
            rule.normalizedKeyword.isNotBlank() && searchHaystack.contains(rule.normalizedKeyword)
        }

        val suggestedAccountId = matchedMerchantRule?.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: matchedSource?.defaultAccountId
            ?: accounts.firstOrNull {
                when (parsed.defaultAccountHint) {
                    "E_WALLET" -> it.type == AccountType.E_WALLET
                    "CASH" -> it.type == AccountType.CASH
                    else -> it.type == AccountType.BANK
                }
            }?.id
            ?: accounts.firstOrNull()?.id

        val suggestedCategoryId = matchedMerchantRule?.categoryId?.takeIf { cid ->
            categories.any { it.id == cid && it.type == parsed.mappedType }
        } ?: suggestCategoryId(parsed.operationKind, parsed.mappedType, categories)

        val insertedPendingId = db.pendingTransactionDao().insertIgnore(
            PendingTransactionEntity(
                importedMessageId = msgId,
                sourceId = matchedSource?.id,
                sourceName = parsed.sourceName,
                channel = channel,
                operationKind = parsed.operationKind,
                mappedType = parsed.mappedType,
                amountPiasters = parsed.amountPiasters,
                feePiasters = parsed.feePiasters,
                currency = parsed.currency,
                referenceNumber = parsed.referenceNumber,
                counterpartyName = parsed.counterpartyName,
                balanceAfterPiasters = parsed.balanceAfterPiasters,
                transactionTimestampMillis = parsed.transactionTimestampMillis,
                suggestedAccountId = suggestedAccountId,
                suggestedCategoryId = suggestedCategoryId,
                confidenceScore = parsed.confidenceScore,
                dedupFingerprint = parsed.dedupFingerprint,
                extractedSummary = parsed.extractedSummary,
                status = PendingStatus.PENDING
            )
        )

        // Record transfer fees independently if present and non-zero
        val feeFp = parsed.feeDedupFingerprint
        if (parsed.feePiasters > 0L && feeFp != null && !duplicateDetector.isDuplicate(feeFp, null, parsed.sourceName)) {
            val feeCategoryId = categories.firstOrNull {
                it.type == TransactionType.EXPENSE && (it.nameEn == "Transfer Fees" || it.nameAr == "رسوم تحويل")
            }?.id ?: suggestedCategoryId
            db.pendingTransactionDao().insertIgnore(
                PendingTransactionEntity(
                    importedMessageId = msgId,
                    sourceId = matchedSource?.id,
                    sourceName = parsed.sourceName,
                    channel = channel,
                    operationKind = MessageOperationKind.TRANSFER_FEE,
                    mappedType = TransactionType.EXPENSE,
                    amountPiasters = parsed.feePiasters,
                    feePiasters = 0L,
                    currency = parsed.currency,
                    referenceNumber = parsed.referenceNumber?.let { "${it}-FEE" },
                    counterpartyName = parsed.sourceName,
                    balanceAfterPiasters = parsed.balanceAfterPiasters,
                    transactionTimestampMillis = parsed.transactionTimestampMillis,
                    suggestedAccountId = suggestedAccountId,
                    suggestedCategoryId = feeCategoryId,
                    confidenceScore = parsed.confidenceScore,
                    dedupFingerprint = feeFp,
                    extractedSummary = "رسوم تحويل • ${parsed.sourceName}",
                    status = PendingStatus.PENDING
                )
            )
        }

        return if (insertedPendingId != -1L) HandleResult.CREATED else HandleResult.DUPLICATE
    }

    private fun suggestCategoryId(
        kind: MessageOperationKind,
        mappedType: TransactionType,
        categories: List<com.example.data.local.entity.CategoryEntity>
    ): Long? {
        val byName = { en: String -> categories.firstOrNull { it.nameEn.equals(en, ignoreCase = true) }?.id }
        return when (kind) {
            MessageOperationKind.SALARY -> byName("Salary")
            MessageOperationKind.REFUND -> byName("Refunds")
            MessageOperationKind.BILL_PAYMENT -> byName("Bills & Utilities")
            MessageOperationKind.CARD_PURCHASE -> byName("Shopping")
            MessageOperationKind.TRANSFER_FEE -> byName("Transfer Fees")
            MessageOperationKind.INCOMING_TRANSFER, MessageOperationKind.DEPOSIT -> byName("Extra Income")
            else -> categories.firstOrNull { it.type == mappedType }?.id
        }
    }
}
