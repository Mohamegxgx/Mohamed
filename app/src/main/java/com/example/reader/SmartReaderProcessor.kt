package com.example.reader

import androidx.room.withTransaction
import com.example.data.ai.FinancialAiGateway
import com.example.data.ai.FinancialContextFormatter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.BalanceDiscrepancy
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.PendingStatus
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.example.domain.reader.BankStatementImporter
import com.example.domain.reader.DuplicateDetector
import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.MessageDirection
import com.example.domain.reader.ParseOutcome
import com.example.domain.reader.ParsedFinancialMessage
import com.example.domain.reader.StatementColumnMapping
import kotlinx.coroutines.flow.first
import kotlin.math.abs

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
    val ignoredFiltered: Int,
    val autoApprovedCount: Int = 0,
    val latestPendingId: Long? = null,
    val latestSuggestedAccountId: Long? = null,
    val latestDirection: MessageDirection? = null,
    val latestSummaryText: String = "",
    val latestBalanceDiscrepancy: BalanceDiscrepancy? = null
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
        val historicalTransactions = db.transactionDao().getAllOnce().take(200)
        val runningBalances = accounts.associate { it.id to it.currentBalancePiasters }.toMutableMap()

        var financialDetected = 0
        var pendingCreated = 0
        var autoApprovedCount = 0
        var duplicatesSkipped = 0
        var ignoredFiltered = 0
        var latestPendingId: Long? = null
        var latestSuggestedAccountId: Long? = null
        var latestDirection: MessageDirection? = null
        var latestSummaryText = ""
        var latestBalanceDiscrepancy: BalanceDiscrepancy? = null

        db.withTransaction {
            for (msg in messages) {
                val matchedSourcePre = sources.firstOrNull { src ->
                    src.senderKeywords.split(",").any { kw ->
                        val c = kw.trim()
                        c.isNotBlank() && (msg.sender.contains(c, ignoreCase = true) || msg.body.contains(c, ignoreCase = true))
                    }
                }
                val preAccount = matchedSourcePre?.defaultAccountId?.let { aid ->
                    accounts.firstOrNull { it.id == aid }
                }
                val preAccountBalance = preAccount?.let { runningBalances[it.id] ?: it.currentBalancePiasters }
                val prePrediction = LocalSelfLearningEngine.predict(
                    queryText = "${msg.sender} ${msg.body}",
                    typeFilter = null,
                    merchantRules = merchantRules,
                    historicalTransactions = historicalTransactions,
                    categories = categories,
                    accounts = accounts
                )

                val outcome = parser.parse(
                    rawBody = msg.body,
                    sender = msg.sender,
                    messageTimestampMillis = msg.timestampMillis,
                    knownAccountBalancePiasters = preAccountBalance,
                    learnedPreferredType = prePrediction.preferredType
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
                        val bankBal = outcome.balancePiasters
                        val matchedAcc = preAccount ?: accounts.firstOrNull { acc ->
                            acc.name.contains(outcome.sourceName, ignoreCase = true) ||
                                outcome.sourceName.contains(acc.name, ignoreCase = true)
                        }
                        if (bankBal != null && matchedAcc != null) {
                            val appBal = runningBalances[matchedAcc.id] ?: matchedAcc.currentBalancePiasters
                            val diff = bankBal - appBal
                            if (abs(diff) >= 100L) {
                                latestBalanceDiscrepancy = BalanceDiscrepancy(
                                    accountId = matchedAcc.id,
                                    accountName = matchedAcc.name,
                                    appBalancePiasters = appBal,
                                    bankBalancePiasters = bankBal,
                                    diffPiasters = diff,
                                    sourceName = outcome.sourceName,
                                    detectedAtMillis = msg.timestampMillis
                                )
                            }
                        }
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
                        val result = handleParsedMessage(
                            parsed = parsed,
                            channel = channel,
                            sources = sources,
                            accounts = accounts,
                            categories = categories,
                            merchantRules = merchantRules,
                            historicalTransactions = historicalTransactions,
                            rawBody = msg.body,
                            allowAutoApprove = (channel == MessageChannel.NOTIFICATION_LISTENER && prefs.smartReaderAutoApprove),
                            language = prefs.language
                        )
                        when (result) {
                            is HandleResult.CreatedPending -> {
                                pendingCreated++
                                latestPendingId = result.pendingId
                                latestSuggestedAccountId = result.accountId
                                latestDirection = parsed.direction
                                latestSummaryText = result.summaryText
                                val bankBal = parsed.balanceAfterPiasters
                                val accId = result.accountId
                                if (bankBal != null && accId != null) {
                                    val acc = accounts.firstOrNull { it.id == accId }
                                    if (acc != null) {
                                        val baseBal = runningBalances[acc.id] ?: acc.currentBalancePiasters
                                        val delta = if (parsed.mappedType == TransactionType.INCOME) {
                                            parsed.amountPiasters
                                        } else if (parsed.mappedType == TransactionType.EXPENSE || parsed.mappedType == TransactionType.TRANSFER) {
                                            -(parsed.amountPiasters + parsed.feePiasters)
                                        } else 0L
                                        val expectedAppBal = baseBal + delta
                                        val diff = bankBal - expectedAppBal
                                        if (abs(diff) >= 100L) {
                                            latestBalanceDiscrepancy = BalanceDiscrepancy(
                                                accountId = acc.id,
                                                accountName = acc.name,
                                                appBalancePiasters = expectedAppBal,
                                                bankBalancePiasters = bankBal,
                                                diffPiasters = diff,
                                                sourceName = parsed.sourceName,
                                                detectedAtMillis = msg.timestampMillis
                                            )
                                        }
                                    }
                                }
                            }
                            is HandleResult.AutoApproved -> {
                                autoApprovedCount++
                                latestSuggestedAccountId = result.accountId
                                latestDirection = parsed.direction
                                latestSummaryText = result.summaryText
                                val baseBal = runningBalances[result.accountId] ?: 0L
                                val delta = if (parsed.mappedType == TransactionType.INCOME) {
                                    parsed.amountPiasters - parsed.feePiasters
                                } else {
                                    -(parsed.amountPiasters + parsed.feePiasters)
                                }
                                val updatedAppBal = baseBal + delta
                                runningBalances[result.accountId] = updatedAppBal
                                val bankBal = parsed.balanceAfterPiasters
                                if (bankBal != null) {
                                    val acc = accounts.firstOrNull { it.id == result.accountId }
                                    val diff = bankBal - updatedAppBal
                                    if (acc != null && abs(diff) >= 100L) {
                                        latestBalanceDiscrepancy = BalanceDiscrepancy(
                                            accountId = acc.id,
                                            accountName = acc.name,
                                            appBalancePiasters = updatedAppBal,
                                            bankBalancePiasters = bankBal,
                                            diffPiasters = diff,
                                            sourceName = parsed.sourceName,
                                            detectedAtMillis = msg.timestampMillis
                                        )
                                    }
                                }
                            }
                            HandleResult.Duplicate -> duplicatesSkipped++
                            HandleResult.IgnoredSource -> ignoredFiltered++
                        }
                    }
                    is ParseOutcome.IncompleteNeedsReview -> {
                        val parsed = outcome.partial
                        financialDetected++
                        val result = handleParsedMessage(
                            parsed = parsed,
                            channel = channel,
                            sources = sources,
                            accounts = accounts,
                            categories = categories,
                            merchantRules = merchantRules,
                            historicalTransactions = historicalTransactions,
                            rawBody = msg.body,
                            allowAutoApprove = false,
                            language = prefs.language
                        )
                        when (result) {
                            is HandleResult.CreatedPending -> {
                                pendingCreated++
                                latestPendingId = result.pendingId
                                latestSuggestedAccountId = result.accountId
                                latestDirection = parsed.direction
                                latestSummaryText = result.summaryText
                            }
                            is HandleResult.AutoApproved -> {
                                autoApprovedCount++
                                latestSuggestedAccountId = result.accountId
                                latestDirection = parsed.direction
                                latestSummaryText = result.summaryText
                            }
                            HandleResult.Duplicate -> duplicatesSkipped++
                            HandleResult.IgnoredSource -> ignoredFiltered++
                        }
                    }
                }
            }

            db.importHistoryDao().insert(
                ImportHistoryEntity(
                    channel = channel,
                    totalScanned = messages.size,
                    financialDetected = financialDetected,
                    pendingCreated = pendingCreated + autoApprovedCount,
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
            ignoredFiltered = ignoredFiltered,
            autoApprovedCount = autoApprovedCount,
            latestPendingId = latestPendingId,
            latestSuggestedAccountId = latestSuggestedAccountId,
            latestDirection = latestDirection,
            latestSummaryText = latestSummaryText,
            latestBalanceDiscrepancy = latestBalanceDiscrepancy
        )
    }

    suspend fun ingestBankStatement(
        rawContent: String,
        bankOrSourceLabel: String,
        mappingOverride: StatementColumnMapping? = null
    ): ImportBatchResult {
        val converted = BankStatementImporter.convertToIncomingMessages(
            rawContent = rawContent,
            bankOrSourceLabel = bankOrSourceLabel,
            mappingOverride = mappingOverride
        )
        if (converted.isEmpty()) {
            return ImportBatchResult(
                totalScanned = 0,
                financialDetected = 0,
                pendingCreated = 0,
                duplicatesSkipped = 0,
                ignoredFiltered = 0
            )
        }
        return ingestMessages(converted, MessageChannel.FILE_IMPORT)
    }

    suspend fun ingestMessagesWithAi(
        sender: String,
        rawText: String,
        channel: MessageChannel,
        aiGateway: FinancialAiGateway,
        language: AppLanguage
    ): ImportBatchResult {
        val prefs = prefsRepo.preferences.first()
        val blocks = rawText.split(Regex("""\n{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (!prefs.smartReaderEnabled || blocks.isEmpty()) {
            return ImportBatchResult(blocks.size, 0, 0, 0, blocks.size)
        }

        // Strict privacy gate: NEVER send OTP or verification codes to Gemini AI!
        val safeBlocks = mutableListOf<String>()
        var otpFiltered = 0
        for (block in blocks) {
            if (FinancialMessageParser.isOtpOrSecurityMessage(block)) {
                otpFiltered++
            } else {
                safeBlocks.add(block)
            }
        }

        if (safeBlocks.isEmpty()) {
            return ImportBatchResult(
                totalScanned = blocks.size,
                financialDetected = 0,
                pendingCreated = 0,
                duplicatesSkipped = 0,
                ignoredFiltered = otpFiltered
            )
        }

        val sources = db.financialSourceDao().getAllOnce()
        val accounts = db.accountDao().getAllOnce().filter { !it.isArchived }
        val categories = db.categoryDao().getAllOnce()
        val validAccountIds = accounts.map { it.id }.toSet()
        val validCategoryIds = categories.map { it.id }.toSet()

        val aiItems = aiGateway.analyzeFinancialMessagesWithAi(
            sender = sender,
            rawMessageBlock = safeBlocks.joinToString("\n\n"),
            accountsContext = FinancialContextFormatter.buildAccountsContext(accounts),
            categoriesContext = FinancialContextFormatter.buildCategoriesContext(categories),
            sourcesContext = FinancialContextFormatter.buildSourcesContext(sources),
            validAccountIds = validAccountIds,
            validCategoryIds = validCategoryIds,
            language = language
        )

        val now = System.currentTimeMillis()
        val dayBucket = now / 86_400_000L
        var financialDetected = 0
        var pendingCreated = 0
        var duplicatesSkipped = 0
        var ignoredFiltered = otpFiltered

        db.withTransaction {
            for (item in aiItems) {
                if (!item.isFinancial || item.amountPiasters <= 0L) {
                    ignoredFiltered++
                    continue
                }
                financialDetected++

                val matchedSource = sources.firstOrNull { src ->
                    src.displayNameAr.equals(item.sourceName, ignoreCase = true) ||
                        src.displayNameEn.equals(item.sourceName, ignoreCase = true) ||
                        src.senderKeywords.split(",").any { kw ->
                            val clean = kw.trim()
                            clean.isNotBlank() && (sender.contains(clean, ignoreCase = true) || item.sourceName.contains(clean, ignoreCase = true))
                        }
                }
                if (matchedSource != null && (!matchedSource.isEnabled || matchedSource.isIgnored)) {
                    ignoredFiltered++
                    continue
                }

                val ref = item.referenceNumber
                val basis = if (!ref.isNullOrBlank()) {
                    "AI_REF|${item.sourceName.uppercase()}|${ref.uppercase()}|${item.operationKind}|${item.amountPiasters}"
                } else {
                    val cp = item.counterpartyName?.lowercase()?.trim() ?: item.summary.take(36).lowercase()
                    "AI_MSG|${item.sourceName.uppercase()}|${item.mappedType}|${item.amountPiasters}|$dayBucket|$cp"
                }
                val fingerprint = FinancialMessageParser.sha256(basis)

                if (duplicateDetector.isDuplicate(fingerprint, ref, item.sourceName)) {
                    duplicatesSkipped++
                    continue
                }

                val resolvedAccountId = item.suggestedAccountId?.takeIf { it in validAccountIds }
                    ?: matchedSource?.defaultAccountId?.takeIf { it in validAccountIds }
                    ?: accounts.firstOrNull()?.id

                val resolvedTargetId = if (item.mappedType == TransactionType.TRANSFER) {
                    item.targetAccountId?.takeIf { it in validAccountIds && it != resolvedAccountId }
                        ?: inferTargetAccountId(
                            rawBody = "${item.summary} ${item.counterpartyName.orEmpty()} ${safeBlocks.joinToString(" ")}",
                            sourceAccountId = resolvedAccountId,
                            accounts = accounts
                        )
                } else null

                val msgId = db.importedMessageDao().insertIgnore(
                    ImportedMessageEntity(
                        sourceId = matchedSource?.id,
                        sourceName = item.sourceName,
                        channel = channel,
                        senderLabel = sender.ifBlank { item.sourceName },
                        fingerprintHash = fingerprint,
                        status = ImportedMessageStatus.PENDING,
                        confidenceScore = item.confidenceScore,
                        messageTimestampMillis = now
                    )
                )
                if (msgId == -1L) {
                    duplicatesSkipped++
                    continue
                }

                val suggestedCatId = if (item.mappedType == TransactionType.INCOME || item.mappedType == TransactionType.EXPENSE) {
                    item.suggestedCategoryId?.takeIf { id ->
                        categories.any { it.id == id && it.type == item.mappedType }
                    } ?: suggestCategoryId(item.operationKind, item.mappedType, categories)
                } else null

                val insertedId = db.pendingTransactionDao().insertIgnore(
                    PendingTransactionEntity(
                        importedMessageId = msgId,
                        sourceId = matchedSource?.id,
                        sourceName = item.sourceName,
                        channel = channel,
                        operationKind = item.operationKind,
                        mappedType = item.mappedType,
                        amountPiasters = item.amountPiasters,
                        feePiasters = item.feePiasters,
                        currency = item.currency,
                        referenceNumber = ref,
                        counterpartyName = item.counterpartyName,
                        balanceAfterPiasters = item.balanceAfterPiasters,
                        transactionTimestampMillis = now,
                        suggestedAccountId = resolvedAccountId,
                        targetAccountId = resolvedTargetId,
                        suggestedCategoryId = suggestedCatId,
                        confidenceScore = item.confidenceScore,
                        dedupFingerprint = fingerprint,
                        extractedSummary = item.summary,
                        status = PendingStatus.PENDING
                    )
                )

                if (item.feePiasters > 0L && item.operationKind != MessageOperationKind.TRANSFER_FEE) {
                    val feeFp = "${fingerprint}_fee"
                    if (!duplicateDetector.isDuplicate(feeFp, null, item.sourceName)) {
                        val feeCatId = categories.firstOrNull {
                            it.type == TransactionType.EXPENSE && (it.nameEn == "Transfer Fees" || it.nameAr == "رسوم تحويل")
                        }?.id ?: suggestedCatId
                        db.pendingTransactionDao().insertIgnore(
                            PendingTransactionEntity(
                                importedMessageId = msgId,
                                sourceId = matchedSource?.id,
                                sourceName = item.sourceName,
                                channel = channel,
                                operationKind = MessageOperationKind.TRANSFER_FEE,
                                mappedType = TransactionType.EXPENSE,
                                amountPiasters = item.feePiasters,
                                feePiasters = 0L,
                                currency = item.currency,
                                referenceNumber = ref?.let { "${it}-FEE" },
                                counterpartyName = item.sourceName,
                                balanceAfterPiasters = item.balanceAfterPiasters,
                                transactionTimestampMillis = now,
                                suggestedAccountId = resolvedAccountId,
                                targetAccountId = null,
                                suggestedCategoryId = feeCatId,
                                confidenceScore = item.confidenceScore,
                                dedupFingerprint = feeFp,
                                extractedSummary = "رسوم تحويل • ${item.sourceName}",
                                status = PendingStatus.PENDING
                            )
                        )
                    }
                }

                if (insertedId != -1L) pendingCreated++ else duplicatesSkipped++
            }

            if (financialDetected == 0 && ignoredFiltered == otpFiltered) {
                ignoredFiltered = blocks.size
            }

            db.importHistoryDao().insert(
                ImportHistoryEntity(
                    channel = channel,
                    totalScanned = blocks.size,
                    financialDetected = financialDetected,
                    pendingCreated = pendingCreated,
                    duplicatesSkipped = duplicatesSkipped,
                    ignoredFiltered = ignoredFiltered
                )
            )
        }

        prefsRepo.setLastImportTimestamp(now)

        return ImportBatchResult(
            totalScanned = blocks.size,
            financialDetected = financialDetected,
            pendingCreated = pendingCreated,
            duplicatesSkipped = duplicatesSkipped,
            ignoredFiltered = ignoredFiltered
        )
    }

    private sealed interface HandleResult {
        data class CreatedPending(val pendingId: Long, val accountId: Long?, val summaryText: String) : HandleResult
        data class AutoApproved(val txId: Long, val accountId: Long, val summaryText: String) : HandleResult
        data object Duplicate : HandleResult
        data object IgnoredSource : HandleResult
    }

    private suspend fun handleParsedMessage(
        parsed: ParsedFinancialMessage,
        channel: MessageChannel,
        sources: List<com.example.data.local.entity.FinancialSourceEntity>,
        accounts: List<AccountEntity>,
        categories: List<com.example.data.local.entity.CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        historicalTransactions: List<TransactionEntity>,
        rawBody: String,
        allowAutoApprove: Boolean,
        language: AppLanguage
    ): HandleResult {
        val matchedSource = sources.firstOrNull {
            it.code == parsed.sourceCode ||
                it.senderKeywords.split(",").any { kw -> kw.isNotBlank() && parsed.senderLabel.contains(kw.trim(), ignoreCase = true) }
        }
        if (matchedSource != null && (!matchedSource.isEnabled || matchedSource.isIgnored)) {
            return HandleResult.IgnoredSource
        }

        if (duplicateDetector.isDuplicate(parsed.dedupFingerprint, parsed.referenceNumber, parsed.sourceName)) {
            return HandleResult.Duplicate
        }

        val canAutoApprove = allowAutoApprove &&
            parsed.confidenceScore >= 0.85f &&
            !parsed.isIncomplete &&
            parsed.amountPiasters > 0L &&
            parsed.mappedType != TransactionType.TRANSFER

        val queryHaystack = "${parsed.counterpartyName.orEmpty()} ${parsed.extractedSummary} $rawBody"
        val aiPrediction = LocalSelfLearningEngine.predict(
            queryText = queryHaystack,
            typeFilter = if (parsed.mappedType == TransactionType.TRANSFER) null else parsed.mappedType,
            merchantRules = merchantRules,
            historicalTransactions = historicalTransactions,
            categories = categories,
            accounts = accounts
        )
        val searchHaystack = VoiceTransactionParser.normalizeKeyword(queryHaystack)
        val matchedMerchantRule = merchantRules.firstOrNull { rule ->
            rule.normalizedKeyword.isNotBlank() && searchHaystack.contains(rule.normalizedKeyword)
        }

        val suggestedAccountId = matchedMerchantRule?.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: aiPrediction.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: matchedSource?.defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull {
                when (parsed.defaultAccountHint) {
                    "E_WALLET" -> it.type == AccountType.E_WALLET
                    "CASH" -> it.type == AccountType.CASH
                    else -> it.type == AccountType.BANK
                }
            }?.id
            ?: accounts.firstOrNull()?.id

        val inferredTargetAccountId = if (parsed.mappedType == TransactionType.TRANSFER) {
            inferTargetAccountId(rawBody, suggestedAccountId, accounts)
        } else null

        val suggestedCategoryId = aiPrediction.categoryId?.takeIf { cid ->
            categories.any { it.id == cid && it.type == parsed.mappedType }
        } ?: matchedMerchantRule?.categoryId?.takeIf { cid ->
            categories.any { it.id == cid && it.type == parsed.mappedType }
        } ?: suggestCategoryId(parsed.operationKind, parsed.mappedType, categories)

        val dirTag = when (parsed.direction) {
            MessageDirection.INCOMING -> if (language == AppLanguage.EN) "Incoming" else "استقبال (وارد)"
            MessageDirection.OUTGOING -> if (language == AppLanguage.EN) "Outgoing" else "إرسال (صادر)"
            MessageDirection.INTERNAL_TRANSFER -> if (language == AppLanguage.EN) "Internal" else "تحويل داخلي"
            else -> ""
        }
        val amtFormatted = MoneyFormatter.formatPiasters(parsed.amountPiasters, parsed.currency, language)
        val alertSummary = if (dirTag.isNotBlank()) {
            "$dirTag • $amtFormatted • ${parsed.sourceName}"
        } else {
            "$amtFormatted • ${parsed.sourceName}"
        }

        if (canAutoApprove && suggestedAccountId != null) {
            val msgId = db.importedMessageDao().insertIgnore(
                ImportedMessageEntity(
                    sourceId = matchedSource?.id,
                    sourceName = parsed.sourceName,
                    channel = channel,
                    senderLabel = parsed.senderLabel,
                    fingerprintHash = parsed.dedupFingerprint,
                    status = ImportedMessageStatus.APPROVED,
                    confidenceScore = parsed.confidenceScore,
                    messageTimestampMillis = parsed.transactionTimestampMillis
                )
            )
            if (msgId == -1L) return HandleResult.Duplicate

            val now = System.currentTimeMillis()
            val tx = TransactionEntity(
                amountPiasters = parsed.amountPiasters,
                type = parsed.mappedType,
                accountId = suggestedAccountId,
                categoryId = suggestedCategoryId,
                source = TransactionSource.NOTIFICATION_IMPORT,
                referenceNumber = parsed.referenceNumber,
                sourceIdentifier = parsed.sourceName,
                dedupFingerprint = parsed.dedupFingerprint,
                description = parsed.extractedSummary,
                notes = buildString {
                    append("تسجيل فوري تلقائي")
                    if (!parsed.counterpartyName.isNullOrBlank()) append(" • الطرف: ${parsed.counterpartyName}")
                    if (!parsed.referenceNumber.isNullOrBlank()) append(" • مرجع: ${parsed.referenceNumber}")
                },
                timestampMillis = parsed.transactionTimestampMillis,
                createdAt = now,
                updatedAt = now
            )
            val txId = db.transactionDao().insert(tx)
            val delta = if (parsed.mappedType == TransactionType.INCOME) parsed.amountPiasters else -parsed.amountPiasters
            db.accountDao().adjustBalance(suggestedAccountId, delta)

            // Auto-learn merchant rule
            val keySource = parsed.counterpartyName?.takeIf { it.isNotBlank() } ?: parsed.extractedSummary
            val merchantKey = LocalSelfLearningEngine.extractLearnableMerchantKey(keySource)
            val normKey = VoiceTransactionParser.normalizeKeyword(merchantKey)
            if (normKey.length >= 2) {
                val existingRule = db.merchantRuleDao().findByNormalizedKeyword(normKey)
                db.merchantRuleDao().insert(
                    existingRule?.copy(
                        categoryId = suggestedCategoryId ?: existingRule.categoryId,
                        accountId = suggestedAccountId,
                        preferredType = parsed.mappedType,
                        hitCount = existingRule.hitCount + 1
                    ) ?: MerchantRuleEntity(
                        keyword = merchantKey,
                        normalizedKeyword = normKey,
                        categoryId = suggestedCategoryId,
                        accountId = suggestedAccountId,
                        preferredType = parsed.mappedType,
                        hitCount = 1
                    )
                )
            }

            // Record fee automatically too if present
            val feeFp = parsed.feeDedupFingerprint
            if (parsed.feePiasters > 0L && feeFp != null && !duplicateDetector.isDuplicate(feeFp, null, parsed.sourceName)) {
                val feeCatId = categories.firstOrNull {
                    it.type == TransactionType.EXPENSE && (it.nameEn == "Transfer Fees" || it.nameAr == "رسوم تحويل")
                }?.id ?: suggestedCategoryId
                db.transactionDao().insert(
                    TransactionEntity(
                        amountPiasters = parsed.feePiasters,
                        type = TransactionType.EXPENSE,
                        accountId = suggestedAccountId,
                        categoryId = feeCatId,
                        source = TransactionSource.NOTIFICATION_IMPORT,
                        referenceNumber = parsed.referenceNumber?.let { "${it}-FEE" },
                        sourceIdentifier = parsed.sourceName,
                        dedupFingerprint = feeFp,
                        description = "رسوم تحويل • ${parsed.sourceName}",
                        timestampMillis = parsed.transactionTimestampMillis,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                db.accountDao().adjustBalance(suggestedAccountId, -parsed.feePiasters)
            }

            return HandleResult.AutoApproved(txId, suggestedAccountId, alertSummary)
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
            return HandleResult.Duplicate
        }

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
                targetAccountId = inferredTargetAccountId,
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

        return if (insertedPendingId != -1L) {
            HandleResult.CreatedPending(insertedPendingId, suggestedAccountId, alertSummary)
        } else {
            HandleResult.Duplicate
        }
    }

    private fun inferTargetAccountId(
        rawBody: String,
        sourceAccountId: Long?,
        accounts: List<AccountEntity>
    ): Long? {
        val candidates = accounts.filter { it.id != sourceAccountId }
        if (candidates.isEmpty()) return null
        val lower = rawBody.lowercase()
        candidates.firstOrNull { lower.contains(it.name.lowercase()) }?.let { return it.id }
        return when {
            lower.contains("محفظ") || lower.contains("فودافون") || lower.contains("wallet") ->
                candidates.firstOrNull { it.type == AccountType.E_WALLET }?.id
            lower.contains("كاش") || lower.contains("cash") ->
                candidates.firstOrNull { it.type == AccountType.CASH }?.id
            lower.contains("بنك") || lower.contains("bank") ->
                candidates.firstOrNull { it.type == AccountType.BANK }?.id
            else -> candidates.firstOrNull()?.id
        }
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
