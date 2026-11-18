package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AiChatMessageEntity
import com.example.data.local.entity.AiChatSessionEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.GoalStatus
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.PayoutCycle
import com.example.domain.model.PendingStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.SavingsAssetKind
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MoneyRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val savingsGoalDao = db.savingsGoalDao()
    private val installmentDao = db.installmentDao()
    private val installmentPaymentDao = db.installmentPaymentDao()
    private val recurringRuleDao = db.recurringRuleDao()
    private val debtDao = db.debtDao()
    private val notificationDao = db.notificationDao()
    private val financialSourceDao = db.financialSourceDao()
    private val importedMessageDao = db.importedMessageDao()
    private val pendingTransactionDao = db.pendingTransactionDao()
    private val importHistoryDao = db.importHistoryDao()
    private val merchantRuleDao = db.merchantRuleDao()
    private val bnplProgramDao = db.bnplProgramDao()
    private val aiChatDao = db.aiChatDao()
    private val savingsCertificateDao = db.savingsCertificateDao()

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val budgets: Flow<List<BudgetEntity>> = budgetDao.observeAll()
    val savingsGoals: Flow<List<SavingsGoalEntity>> = savingsGoalDao.observeAll()
    val installments: Flow<List<InstallmentEntity>> = installmentDao.observeAll()
    val installmentPayments: Flow<List<InstallmentPaymentEntity>> = installmentPaymentDao.observeAll()
    val recurringRules: Flow<List<RecurringRuleEntity>> = recurringRuleDao.observeAll()
    val debts: Flow<List<DebtEntity>> = debtDao.observeAll()
    val notifications: Flow<List<NotificationEntity>> = notificationDao.observeAll()
    val financialSources: Flow<List<FinancialSourceEntity>> = financialSourceDao.observeAll()
    val importedMessages: Flow<List<ImportedMessageEntity>> = importedMessageDao.observeAll()
    val pendingTransactions: Flow<List<PendingTransactionEntity>> = pendingTransactionDao.observeAll()
    val importHistory: Flow<List<ImportHistoryEntity>> = importHistoryDao.observeAll()
    val merchantRules: Flow<List<MerchantRuleEntity>> = merchantRuleDao.observeAll()
    val bnplPrograms: Flow<List<BnplProgramEntity>> = bnplProgramDao.observeAll()
    val aiChatSessions: Flow<List<AiChatSessionEntity>> = aiChatDao.observeAllSessions()
    val savingsCertificates: Flow<List<SavingsCertificateEntity>> = savingsCertificateDao.observeAll()

    suspend fun ensureSeeded() {
        AppDatabase.seedDefaults(db)
    }

    // --- Transactions (Atomic Balance Integrity) ---

    suspend fun addTransaction(tx: TransactionEntity): Long {
        require(tx.amountPiasters > 0L) { "Amount must be positive" }
        if (tx.type == TransactionType.TRANSFER) {
            require(tx.targetAccountId != null && tx.targetAccountId != tx.accountId) {
                "Transfer requires a distinct target account"
            }
        }
        return db.withTransaction {
            val id = transactionDao.insert(tx)
            applyTransactionEffect(tx.copy(id = id), multiplier = 1)
            autoLearnFromTransaction(tx)
            id
        }
    }

    suspend fun addTransferWithFee(
        fromAccountId: Long,
        toAccountId: Long,
        amountPiasters: Long,
        feePiasters: Long,
        description: String,
        notes: String = "",
        attachmentUri: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): Long = addTransferWithFeeReturningIds(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountPiasters = amountPiasters,
        feePiasters = feePiasters,
        description = description,
        notes = notes,
        attachmentUri = attachmentUri,
        timestampMillis = timestampMillis
    ).first

    suspend fun addTransferWithFeeReturningIds(
        fromAccountId: Long,
        toAccountId: Long,
        amountPiasters: Long,
        feePiasters: Long,
        description: String,
        notes: String = "",
        attachmentUri: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long?> {
        require(amountPiasters > 0L) { "Amount must be positive" }
        require(toAccountId != fromAccountId) { "Transfer requires a distinct target account" }
        val cleanDesc = description.trim().ifEmpty { "تحويل بين الحسابات" }
        return db.withTransaction {
            val transferTx = TransactionEntity(
                amountPiasters = amountPiasters,
                type = TransactionType.TRANSFER,
                accountId = fromAccountId,
                targetAccountId = toAccountId,
                description = cleanDesc,
                notes = notes.trim(),
                attachmentUri = attachmentUri,
                timestampMillis = timestampMillis,
                createdAt = timestampMillis,
                updatedAt = timestampMillis
            )
            val transferId = transactionDao.insert(transferTx)
            applyTransactionEffect(transferTx.copy(id = transferId), multiplier = 1)

            var feeId: Long? = null
            if (feePiasters > 0L) {
                val feeCategoryId = categoryDao.getAllOnce().firstOrNull {
                    it.type == TransactionType.EXPENSE && (it.nameEn == "Transfer Fees" || it.nameAr == "رسوم تحويل")
                }?.id
                val feeTx = TransactionEntity(
                    amountPiasters = feePiasters,
                    type = TransactionType.EXPENSE,
                    accountId = fromAccountId,
                    categoryId = feeCategoryId,
                    description = "رسوم تحويل • $cleanDesc",
                    notes = notes.trim(),
                    timestampMillis = timestampMillis,
                    createdAt = timestampMillis,
                    updatedAt = timestampMillis
                )
                feeId = transactionDao.insert(feeTx)
                applyTransactionEffect(feeTx.copy(id = feeId), multiplier = 1)
            }
            transferId to feeId
        }
    }

    suspend fun updateTransaction(updated: TransactionEntity) {
        require(updated.amountPiasters > 0L) { "Amount must be positive" }
        if (updated.type == TransactionType.TRANSFER) {
            require(updated.targetAccountId != null && updated.targetAccountId != updated.accountId) {
                "Transfer requires a distinct target account"
            }
        }
        db.withTransaction {
            val existing = transactionDao.getById(updated.id)
                ?: throw IllegalStateException("Transaction not found")
            applyTransactionEffect(existing, multiplier = -1)
            val stamped = updated.copy(updatedAt = System.currentTimeMillis())
            transactionDao.update(stamped)
            applyTransactionEffect(stamped, multiplier = 1)
            autoLearnFromTransaction(stamped)
        }
    }

    private suspend fun autoLearnFromTransaction(tx: TransactionEntity) {
        if ((tx.type == TransactionType.EXPENSE || tx.type == TransactionType.INCOME) && tx.categoryId != null) {
            val merchantKey = LocalSelfLearningEngine.extractLearnableMerchantKey(tx.description)
            if (merchantKey.length >= 2) {
                learnOrUpdateMerchantRule(
                    rawKeyword = merchantKey,
                    categoryId = tx.categoryId,
                    accountId = tx.accountId,
                    preferredType = tx.type
                )
            }
        }
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        db.withTransaction {
            val existing = transactionDao.getById(tx.id) ?: return@withTransaction
            applyTransactionEffect(existing, multiplier = -1)
            transactionDao.delete(existing)
        }
    }

    suspend fun duplicateTransaction(tx: TransactionEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        val copy = tx.copy(
            id = 0,
            recurringRuleId = null,
            recurringCycleKey = null,
            installmentId = null,
            timestampMillis = nowMillis,
            createdAt = nowMillis,
            updatedAt = nowMillis
        )
        return addTransaction(copy)
    }

    private suspend fun applyTransactionEffect(tx: TransactionEntity, multiplier: Int) {
        val amount = tx.amountPiasters * multiplier
        when (tx.type) {
            TransactionType.INCOME -> {
                accountDao.adjustBalance(tx.accountId, amount)
            }
            TransactionType.EXPENSE -> {
                accountDao.adjustBalance(tx.accountId, -amount)
            }
            TransactionType.TRANSFER -> {
                accountDao.adjustBalance(tx.accountId, -amount)
                tx.targetAccountId?.let { target ->
                    accountDao.adjustBalance(target, amount)
                }
            }
            TransactionType.SAVINGS_CONTRIBUTION -> {
                accountDao.adjustBalance(tx.accountId, -amount)
                tx.savingsGoalId?.let { goalId ->
                    savingsGoalDao.adjustSavedAmount(goalId, amount)
                    val goal = savingsGoalDao.getById(goalId)
                    if (goal != null) {
                        val newStatus = when {
                            goal.savedAmountPiasters >= goal.targetAmountPiasters -> GoalStatus.COMPLETED
                            goal.status == GoalStatus.COMPLETED -> GoalStatus.ACTIVE
                            else -> goal.status
                        }
                        if (newStatus != goal.status) {
                            savingsGoalDao.update(goal.copy(status = newStatus))
                        }
                    }
                }
            }
            TransactionType.DEBT_PAYMENT -> {
                val debt = tx.debtId?.let { debtDao.getById(it) }
                if (debt != null) {
                    val accountDelta = if (debt.direction == DebtDirection.OWED_BY_ME) -amount else amount
                    accountDao.adjustBalance(tx.accountId, accountDelta)
                    val newPaid = (debt.paidAmountPiasters + amount).coerceIn(0L, debt.totalAmountPiasters)
                    val newStatus = when {
                        newPaid >= debt.totalAmountPiasters -> DebtStatus.SETTLED
                        newPaid > 0L -> DebtStatus.PARTIAL
                        else -> DebtStatus.OPEN
                    }
                    debtDao.update(debt.copy(paidAmountPiasters = newPaid, status = newStatus))
                } else {
                    accountDao.adjustBalance(tx.accountId, -amount)
                }
            }
        }
    }

    // --- Accounts ---

    suspend fun createAccount(account: AccountEntity): Long = accountDao.insert(account)

    suspend fun updateAccount(account: AccountEntity) = accountDao.update(account)

    suspend fun deleteAccountIfEmpty(account: AccountEntity): Boolean {
        val hasTx = transactionDao.getAllOnce().any { it.accountId == account.id || it.targetAccountId == account.id }
        if (hasTx) {
            accountDao.update(account.copy(isArchived = true))
            return false
        }
        accountDao.delete(account)
        return true
    }

    // --- Categories ---

    suspend fun createCategory(category: CategoryEntity): Long = categoryDao.insert(category)

    suspend fun deleteCategory(category: CategoryEntity) {
        if (!category.isDefault) {
            categoryDao.delete(category)
        }
    }

    suspend fun batchAssignCategories(assignments: Map<Long, Long>): Int {
        if (assignments.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var updatedCount = 0
        db.withTransaction {
            val categoriesById = categoryDao.getAllOnce().associateBy { it.id }
            for ((txId, catId) in assignments) {
                val existing = transactionDao.getById(txId) ?: continue
                if (existing.type != TransactionType.EXPENSE && existing.type != TransactionType.INCOME) continue
                val targetCategory = categoriesById[catId] ?: continue
                if (targetCategory.type != existing.type) continue
                if (existing.categoryId != catId) {
                    transactionDao.update(existing.copy(categoryId = catId, updatedAt = now))
                    updatedCount++
                }
                if (existing.description.trim().length >= 2) {
                    learnOrUpdateMerchantRule(
                        rawKeyword = existing.description.trim(),
                        categoryId = catId,
                        accountId = existing.accountId,
                        preferredType = existing.type
                    )
                }
            }
        }
        return updatedCount
    }

    // --- Smart Merchant Auto-Categorization Rules ---

    suspend fun getAllMerchantRulesOnce(): List<MerchantRuleEntity> = merchantRuleDao.getAllOnce()

    suspend fun learnOrUpdateMerchantRule(
        rawKeyword: String,
        categoryId: Long?,
        accountId: Long?,
        preferredType: TransactionType = TransactionType.EXPENSE
    ): Long {
        val normalized = VoiceTransactionParser.normalizeKeyword(rawKeyword)
        if (normalized.length < 2) return -1L
        val existing = merchantRuleDao.findByNormalizedKeyword(normalized)
        return if (existing != null) {
            val updated = existing.copy(
                keyword = rawKeyword.trim(),
                categoryId = categoryId ?: existing.categoryId,
                accountId = accountId ?: existing.accountId,
                preferredType = preferredType,
                hitCount = existing.hitCount + 1
            )
            merchantRuleDao.insert(updated)
        } else {
            merchantRuleDao.insert(
                MerchantRuleEntity(
                    keyword = rawKeyword.trim(),
                    normalizedKeyword = normalized,
                    categoryId = categoryId,
                    accountId = accountId,
                    preferredType = preferredType,
                    hitCount = 1
                )
            )
        }
    }

    suspend fun deleteMerchantRule(rule: MerchantRuleEntity) = merchantRuleDao.delete(rule)

    // --- Budgets ---

    suspend fun saveBudget(budget: BudgetEntity): Long = budgetDao.insert(budget)

    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)

    suspend fun copyPreviousMonthBudgets(targetYearMonth: String): Int {
        val prevYm = DateRangeResolver.previousYearMonth(targetYearMonth)
        val prevBudgets = budgetDao.getByMonth(prevYm)
        if (prevBudgets.isEmpty()) return 0
        val existingTarget = budgetDao.getByMonth(targetYearMonth)
        val existingKeys = existingTarget.map { it.categoryId to it.name }.toSet()
        val toInsert = prevBudgets
            .filter { (it.categoryId to it.name) !in existingKeys }
            .map {
                it.copy(
                    id = 0,
                    yearMonth = targetYearMonth,
                    isCustomPeriod = false,
                    startDateMillis = null,
                    endDateMillis = null,
                    createdAt = System.currentTimeMillis()
                )
            }
        if (toInsert.isNotEmpty()) {
            budgetDao.insertAll(toInsert)
        }
        return toInsert.size
    }

    // --- Savings Goals ---

    suspend fun saveGoal(goal: SavingsGoalEntity): Long = savingsGoalDao.insert(goal)

    suspend fun updateGoal(goal: SavingsGoalEntity) = savingsGoalDao.update(goal)

    suspend fun deleteGoal(goal: SavingsGoalEntity) = savingsGoalDao.delete(goal)

    // --- Installments (Single Expense Guarantee) ---

    suspend fun saveInstallment(installment: InstallmentEntity): Long = installmentDao.insert(installment)

    suspend fun updateInstallment(installment: InstallmentEntity) = installmentDao.update(installment)

    suspend fun deleteInstallment(installment: InstallmentEntity) = installmentDao.delete(installment)

    // --- BNPL Programs ---

    suspend fun saveBnplProgram(program: BnplProgramEntity): Long = bnplProgramDao.insert(program)

    suspend fun updateBnplProgram(program: BnplProgramEntity) = bnplProgramDao.update(program)

    suspend fun deleteBnplProgram(program: BnplProgramEntity) {
        db.withTransaction {
            installmentDao.detachProgram(program.id)
            bnplProgramDao.delete(program)
        }
    }

    suspend fun getAllBnplProgramsOnce(): List<BnplProgramEntity> = bnplProgramDao.getAllOnce()

    suspend fun payInstallment(
        installmentId: Long,
        accountId: Long,
        nowMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ) {
        db.withTransaction {
            val installment = installmentDao.getById(installmentId)
                ?: throw IllegalStateException("Installment not found")
            if (installment.paidCount >= installment.totalCount || installment.status == InstallmentStatus.COMPLETED) {
                return@withTransaction
            }

            val installmentCategory = categoryDao.getAllOnce()
                .firstOrNull { it.type == TransactionType.EXPENSE && (it.nameEn == "Installments" || it.nameAr == "أقساط") }
                ?: categoryDao.getAllOnce().firstOrNull { it.type == TransactionType.EXPENSE }

            val nextNumber = installment.paidCount + 1
            val tx = TransactionEntity(
                amountPiasters = installment.installmentAmountPiasters,
                type = TransactionType.EXPENSE,
                accountId = accountId,
                categoryId = installmentCategory?.id,
                installmentId = installment.id,
                description = "قسط ${installment.name} ($nextNumber/${installment.totalCount})",
                notes = notes.ifBlank { installment.creditor },
                timestampMillis = nowMillis,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
            val txId = transactionDao.insert(tx)
            accountDao.adjustBalance(accountId, -installment.installmentAmountPiasters)

            installmentPaymentDao.insert(
                InstallmentPaymentEntity(
                    installmentId = installment.id,
                    transactionId = txId,
                    installmentNumber = nextNumber,
                    amountPiasters = installment.installmentAmountPiasters,
                    paidAtMillis = nowMillis,
                    notes = notes
                )
            )

            val newPaidCount = nextNumber
            val newStatus = if (newPaidCount >= installment.totalCount) InstallmentStatus.COMPLETED else installment.status
            val nextDue = advanceInstallmentDate(installment.nextDueDateMillis, installment.frequency)

            installmentDao.update(
                installment.copy(
                    paidCount = newPaidCount,
                    nextDueDateMillis = nextDue,
                    status = newStatus
                )
            )
        }
    }

    // --- Recurring Income & Expenses (Idempotent Cycle Protection) ---

    suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long = recurringRuleDao.insert(rule)

    suspend fun updateRecurringRule(rule: RecurringRuleEntity) = recurringRuleDao.update(rule)

    suspend fun deleteRecurringRule(rule: RecurringRuleEntity) = recurringRuleDao.delete(rule)

    suspend fun executeDueRecurringRules(nowMillis: Long = System.currentTimeMillis()): Int {
        var executedCount = 0
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        db.withTransaction {
            val rules = recurringRuleDao.getAllOnce().filter { it.isActive }
            for (rule in rules) {
                var currentRunMillis = rule.nextRunDateMillis
                var guard = 0
                while (currentRunMillis <= nowMillis && guard < 24) {
                    guard++
                    val cycleDate = dayFmt.format(Date(currentRunMillis))
                    val uniqueKey = "rule_${rule.id}_$cycleDate"
                    val tx = TransactionEntity(
                        amountPiasters = rule.amountPiasters,
                        type = rule.type,
                        accountId = rule.accountId,
                        categoryId = rule.categoryId,
                        recurringRuleId = rule.id,
                        recurringCycleKey = uniqueKey,
                        description = rule.title,
                        notes = "معاملة مجدولة تلقائيًا",
                        timestampMillis = currentRunMillis,
                        createdAt = nowMillis,
                        updatedAt = nowMillis
                    )
                    val insertedId = transactionDao.insertIgnoreDuplicate(tx)
                    if (insertedId != -1L) {
                        val delta = if (rule.type == TransactionType.INCOME) rule.amountPiasters else -rule.amountPiasters
                        accountDao.adjustBalance(rule.accountId, delta)
                        executedCount++
                    }
                    currentRunMillis = advanceRecurringDate(currentRunMillis, rule.frequency)
                }
                if (currentRunMillis != rule.nextRunDateMillis) {
                    recurringRuleDao.update(rule.copy(nextRunDateMillis = currentRunMillis))
                }
            }
        }
        return executedCount
    }

    // --- Debts ---

    suspend fun saveDebt(debt: DebtEntity): Long = debtDao.insert(debt)

    suspend fun updateDebt(debt: DebtEntity) = debtDao.update(debt)

    suspend fun deleteDebt(debt: DebtEntity) = debtDao.delete(debt)

    suspend fun recordDebtSettlement(
        debtId: Long,
        accountId: Long,
        amountPiasters: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val debt = debtDao.getById(debtId) ?: return
        val remaining = (debt.totalAmountPiasters - debt.paidAmountPiasters).coerceAtLeast(0L)
        val clamped = amountPiasters.coerceIn(1L, remaining.coerceAtLeast(1L))
        val desc = if (debt.direction == DebtDirection.OWED_BY_ME) {
            "سداد دين إلى ${debt.personName}"
        } else {
            "تحصيل دين من ${debt.personName}"
        }
        addTransaction(
            TransactionEntity(
                amountPiasters = clamped,
                type = TransactionType.DEBT_PAYMENT,
                accountId = accountId,
                debtId = debt.id,
                description = desc,
                notes = debt.notes,
                timestampMillis = nowMillis,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
        )
    }

    // --- Notifications ---

    suspend fun addNotification(notification: NotificationEntity): Long = notificationDao.insert(notification)

    suspend fun markAllNotificationsRead() = notificationDao.markAllRead()

    suspend fun deleteNotification(id: Long) = notificationDao.deleteById(id)

    suspend fun getAllTransactionsOnce(): List<TransactionEntity> = transactionDao.getAllOnce()
    suspend fun getAllAccountsOnce(): List<AccountEntity> = accountDao.getAllOnce()
    suspend fun getAllCategoriesOnce(): List<CategoryEntity> = categoryDao.getAllOnce()
    suspend fun getAllBudgetsOnce(): List<BudgetEntity> = budgetDao.getAllOnce()
    suspend fun getAllGoalsOnce(): List<SavingsGoalEntity> = savingsGoalDao.getAllOnce()
    suspend fun getAllInstallmentsOnce(): List<InstallmentEntity> = installmentDao.getAllOnce()
    suspend fun getAllInstallmentPaymentsOnce(): List<InstallmentPaymentEntity> = installmentPaymentDao.getAllOnce()
    suspend fun getAllRecurringRulesOnce(): List<RecurringRuleEntity> = recurringRuleDao.getAllOnce()
    suspend fun getAllDebtsOnce(): List<DebtEntity> = debtDao.getAllOnce()
    suspend fun getAllNotificationsOnce(): List<NotificationEntity> = notificationDao.getAllOnce()
    suspend fun getAllFinancialSourcesOnce(): List<FinancialSourceEntity> = financialSourceDao.getAllOnce()
    suspend fun getAllPendingOnce(): List<PendingTransactionEntity> = pendingTransactionDao.getAllOnce()

    // --- Smart Money Reader Actions ---

    suspend fun saveFinancialSource(source: FinancialSourceEntity): Long = financialSourceDao.insert(source)

    suspend fun updateFinancialSource(source: FinancialSourceEntity) = financialSourceDao.update(source)

    suspend fun ignoreFinancialSource(sourceId: Long) {
        val sources = financialSourceDao.getAllOnce()
        val target = sources.firstOrNull { it.id == sourceId } ?: return
        financialSourceDao.update(target.copy(isIgnored = true, isEnabled = false))
    }

    suspend fun approvePendingTransaction(
        pendingId: Long,
        accountId: Long,
        targetAccountId: Long? = null,
        categoryId: Long? = null,
        overrideAmountPiasters: Long? = null,
        overrideType: TransactionType? = null,
        overrideDescription: String? = null
    ): Result<Long> = runCatching {
        db.withTransaction {
            val pending = pendingTransactionDao.getById(pendingId)
                ?: error("Pending transaction not found")
            if (pending.status == PendingStatus.APPROVED) {
                error("Already approved")
            }

            // Extra safeguard against duplicate fingerprint or reference
            if (transactionDao.countByFingerprint(pending.dedupFingerprint) > 0) {
                pendingTransactionDao.updateStatus(pendingId, PendingStatus.REJECTED)
                error("معاملة مكررة موجودة بالفعل")
            }

            val finalType = overrideType ?: pending.mappedType
            val finalAmount = overrideAmountPiasters ?: pending.amountPiasters
            require(finalAmount > 0L) { "المبلغ يجب أن يكون أكبر من صفر" }
            if (finalType == TransactionType.TRANSFER) {
                require(targetAccountId != null && targetAccountId != accountId) {
                    "التحويل بين الحسابات يتطلب حسابًا مستلمًا مختلفًا"
                }
            }

            val txSource = when (pending.channel) {
                MessageChannel.SMS_INBOX -> TransactionSource.SMS_IMPORT
                MessageChannel.NOTIFICATION_LISTENER -> TransactionSource.NOTIFICATION_IMPORT
                MessageChannel.FILE_IMPORT -> TransactionSource.FILE_IMPORT
                MessageChannel.SHARE_INTENT, MessageChannel.CLIPBOARD_PASTE -> TransactionSource.SHARE_IMPORT
            }

            val now = System.currentTimeMillis()
            val tx = TransactionEntity(
                amountPiasters = finalAmount,
                type = finalType,
                accountId = accountId,
                targetAccountId = if (finalType == TransactionType.TRANSFER) targetAccountId else null,
                categoryId = if (finalType == TransactionType.INCOME || finalType == TransactionType.EXPENSE) {
                    categoryId ?: pending.suggestedCategoryId
                } else null,
                source = txSource,
                referenceNumber = pending.referenceNumber,
                sourceIdentifier = pending.sourceName,
                dedupFingerprint = pending.dedupFingerprint,
                description = overrideDescription?.trim()?.takeIf { it.isNotEmpty() } ?: pending.extractedSummary,
                notes = buildString {
                    if (!pending.counterpartyName.isNullOrBlank()) append("الطرف: ${pending.counterpartyName}")
                    if (!pending.referenceNumber.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("مرجع: ${pending.referenceNumber}")
                    }
                },
                timestampMillis = pending.transactionTimestampMillis,
                createdAt = now,
                updatedAt = now
            )

            val txId = transactionDao.insert(tx)
            applyTransactionEffect(tx.copy(id = txId), multiplier = 1)
            pendingTransactionDao.updateStatus(pendingId, PendingStatus.APPROVED)
            pending.importedMessageId?.let { msgId ->
                importedMessageDao.updateStatus(msgId, ImportedMessageStatus.APPROVED)
            }
            val rawMerchantHint = overrideDescription?.trim()?.takeIf { it.length >= 2 }
                ?: pending.counterpartyName?.takeIf { it.isNotBlank() }
                ?: pending.extractedSummary
            val merchantKey = LocalSelfLearningEngine.extractLearnableMerchantKey(rawMerchantHint)
                .ifBlank { pending.counterpartyName?.trim().orEmpty() }
            if (merchantKey.length >= 2 && (tx.categoryId != null || finalType == TransactionType.EXPENSE || finalType == TransactionType.INCOME)) {
                learnOrUpdateMerchantRule(
                    rawKeyword = merchantKey,
                    categoryId = tx.categoryId,
                    accountId = accountId,
                    preferredType = finalType
                )
            }
            txId
        }
    }

    suspend fun rejectPendingTransaction(pendingId: Long) {
        db.withTransaction {
            val pending = pendingTransactionDao.getById(pendingId) ?: return@withTransaction
            pendingTransactionDao.updateStatus(pendingId, PendingStatus.REJECTED)
            pending.importedMessageId?.let { msgId ->
                importedMessageDao.updateStatus(msgId, ImportedMessageStatus.REJECTED)
            }
        }
    }

    suspend fun clearImportData() {
        db.withTransaction {
            pendingTransactionDao.deleteUnapproved()
            importedMessageDao.deleteAll()
            importHistoryDao.deleteAll()
        }
    }

    private fun advanceInstallmentDate(currentMillis: Long, frequency: InstallmentFrequency): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        when (frequency) {
            InstallmentFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            InstallmentFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            InstallmentFrequency.QUARTERLY -> cal.add(Calendar.MONTH, 3)
            InstallmentFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun advanceRecurringDate(currentMillis: Long, frequency: RecurringFrequency): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        when (frequency) {
            RecurringFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurringFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    // --- AI Chat History & Cloud Sync Support ---

    fun observeAiChatMessages(sessionId: String): Flow<List<AiChatMessageEntity>> =
        aiChatDao.observeMessagesForSession(sessionId)

    suspend fun getAllAiChatSessionsOnce(): List<AiChatSessionEntity> =
        aiChatDao.getAllSessionsOnce()

    suspend fun getAiChatSessionById(sessionId: String): AiChatSessionEntity? =
        aiChatDao.getSessionById(sessionId)

    suspend fun getAiChatMessagesOnce(sessionId: String): List<AiChatMessageEntity> =
        aiChatDao.getMessagesForSessionOnce(sessionId)

    suspend fun saveAiChatSessionAndMessages(
        session: AiChatSessionEntity,
        messages: List<AiChatMessageEntity>
    ) {
        db.withTransaction {
            aiChatDao.insertSession(session)
            if (messages.isNotEmpty()) {
                aiChatDao.insertMessages(messages)
            }
        }
    }

    suspend fun markAiMessageUndone(messageId: String, sessionId: String) {
        db.withTransaction {
            aiChatDao.updateMessageUndone(messageId, true)
            aiChatDao.markSessionSynced(sessionId, false)
        }
    }

    suspend fun markAiChatSessionSynced(sessionId: String, synced: Boolean) {
        aiChatDao.markSessionSynced(sessionId, synced)
    }

    suspend fun deleteAiChatSession(sessionId: String) {
        db.withTransaction {
            aiChatDao.deleteMessagesForSession(sessionId)
            aiChatDao.deleteSessionById(sessionId)
        }
    }

    suspend fun mergeRemoteAiChats(
        remoteSessions: List<AiChatSessionEntity>,
        remoteMessagesBySession: Map<String, List<AiChatMessageEntity>>
    ) {
        if (remoteSessions.isEmpty()) return
        db.withTransaction {
            remoteSessions.forEach { remoteSession ->
                val localSession = aiChatDao.getSessionById(remoteSession.id)
                val localMessages = aiChatDao.getMessagesForSessionOnce(remoteSession.id)
                val remoteMessages = remoteMessagesBySession[remoteSession.id].orEmpty()

                val mergedMessagesMap = LinkedHashMap<String, AiChatMessageEntity>()
                localMessages.forEach { mergedMessagesMap[it.id] = it }
                remoteMessages.forEach { remoteMsg ->
                    val existing = mergedMessagesMap[remoteMsg.id]
                    if (existing == null) {
                        mergedMessagesMap[remoteMsg.id] = remoteMsg
                    } else if (remoteMsg.isUndone && !existing.isUndone) {
                        mergedMessagesMap[remoteMsg.id] = existing.copy(isUndone = true)
                    }
                }
                val mergedMessages = mergedMessagesMap.values.sortedBy { it.timestampMillis }
                val latestPreview = mergedMessages.lastOrNull()?.text?.take(120)
                    ?: remoteSession.lastMessagePreview
                val mergedUpdated = maxOf(
                    localSession?.updatedAtMillis ?: 0L,
                    remoteSession.updatedAtMillis,
                    mergedMessages.lastOrNull()?.timestampMillis ?: 0L
                )
                val chosenTitle = when {
                    localSession != null && localSession.title.isNotBlank() &&
                        localSession.title != "محادثة جديدة" && localSession.title != "New Chat" -> localSession.title
                    else -> remoteSession.title
                }
                val isSynced = localSession == null || (mergedMessages.size == localMessages.size && localSession.syncedToCloud)
                aiChatDao.insertSession(
                    AiChatSessionEntity(
                        id = remoteSession.id,
                        title = chosenTitle,
                        lastMessagePreview = latestPreview,
                        messageCount = mergedMessages.size,
                        createdAtMillis = minOf(
                            localSession?.createdAtMillis ?: remoteSession.createdAtMillis,
                            remoteSession.createdAtMillis
                        ),
                        updatedAtMillis = mergedUpdated,
                        syncedToCloud = isSynced
                    )
                )
                if (mergedMessages.isNotEmpty()) {
                    aiChatDao.insertMessages(mergedMessages)
                }
            }
        }
    }

    // --- Bank Savings Certificates & Gold Reserves ---

    suspend fun getAllSavingsCertificatesOnce(): List<SavingsCertificateEntity> =
        savingsCertificateDao.getAllOnce()

    suspend fun saveCertificate(cert: SavingsCertificateEntity): Long =
        savingsCertificateDao.insert(cert)

    suspend fun updateCertificate(cert: SavingsCertificateEntity) =
        savingsCertificateDao.update(cert)

    suspend fun deleteCertificate(cert: SavingsCertificateEntity) =
        savingsCertificateDao.delete(cert)

    suspend fun updateGoldPriceForKarat(karat: Int, pricePerGramPiasters: Long) {
        if (pricePerGramPiasters > 0L) {
            savingsCertificateDao.updateGoldPriceForKarat(karat, pricePerGramPiasters)
        }
    }

    suspend fun creditCertificateInterest(
        certId: Long,
        overrideAmountPiasters: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        return db.withTransaction {
            val cert = savingsCertificateDao.getById(certId) ?: return@withTransaction null
            val targetAccountId = cert.linkedAccountId
                ?: accountDao.getAllOnce().firstOrNull { !it.isArchived }?.id
                ?: return@withTransaction null
            val yieldPiasters = overrideAmountPiasters ?: computeCyclePayoutPiasters(cert)
            if (yieldPiasters <= 0L) return@withTransaction null

            val incomeCategoryId = categoryDao.getAllOnce()
                .firstOrNull { it.type == TransactionType.INCOME && (it.nameEn == "Extra Income" || it.nameAr == "دخل إضافي" || it.nameEn == "Investment") }
                ?.id ?: categoryDao.getAllOnce().firstOrNull { it.type == TransactionType.INCOME }?.id

            val txId = transactionDao.insert(
                TransactionEntity(
                    amountPiasters = yieldPiasters,
                    type = TransactionType.INCOME,
                    accountId = targetAccountId,
                    categoryId = incomeCategoryId,
                    source = TransactionSource.MANUAL_ENTRY,
                    sourceIdentifier = cert.institutionName.ifBlank { "Savings Certificate" },
                    description = "عائد ${cert.title}",
                    notes = "عائد شهادة ادخار (${cert.annualYieldPercent}%)",
                    timestampMillis = nowMillis,
                    createdAt = nowMillis,
                    updatedAt = nowMillis
                )
            )
            accountDao.adjustBalance(targetAccountId, yieldPiasters)

            val nextPayout = advancePayoutDate(cert.nextPayoutMillis, cert.payoutCycle)
            val stillActive = if (cert.payoutCycle == PayoutCycle.MATURITY) false else cert.isActive
            savingsCertificateDao.update(
                cert.copy(
                    nextPayoutMillis = maxOf(nextPayout, nowMillis + 24L * 60L * 60L * 1000L),
                    isActive = stillActive
                )
            )
            txId
        }
    }

    suspend fun executeDueCertificates(nowMillis: Long = System.currentTimeMillis()): Int {
        var creditedCount = 0
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        db.withTransaction {
            val accounts = accountDao.getAllOnce().filter { !it.isArchived }
            val fallbackAccountId = accounts.firstOrNull()?.id ?: return@withTransaction
            val incomeCategoryId = categoryDao.getAllOnce()
                .firstOrNull { it.type == TransactionType.INCOME && (it.nameEn == "Extra Income" || it.nameAr == "دخل إضافي" || it.nameEn == "Investment") }
                ?.id ?: categoryDao.getAllOnce().firstOrNull { it.type == TransactionType.INCOME }?.id

            val certs = savingsCertificateDao.getAllOnce().filter {
                it.isActive &&
                    it.autoCreditInterest &&
                    it.assetKind == SavingsAssetKind.BANK_CERTIFICATE &&
                    it.principalPiasters > 0L &&
                    it.annualYieldPercent > 0.0
            }

            for (cert in certs) {
                val targetAccountId = cert.linkedAccountId?.takeIf { aid -> accounts.any { it.id == aid } }
                    ?: fallbackAccountId
                val payoutPiasters = computeCyclePayoutPiasters(cert)
                if (payoutPiasters <= 0L) continue

                var payoutMillis = cert.nextPayoutMillis
                var guard = 0
                var matured = false
                while (payoutMillis <= nowMillis && guard < 12 && !matured) {
                    guard++
                    val cycleDate = dayFmt.format(Date(payoutMillis))
                    val uniqueFp = "cert_${cert.id}_$cycleDate"
                    val tx = TransactionEntity(
                        amountPiasters = payoutPiasters,
                        type = TransactionType.INCOME,
                        accountId = targetAccountId,
                        categoryId = incomeCategoryId,
                        dedupFingerprint = uniqueFp,
                        source = TransactionSource.MANUAL_ENTRY,
                        sourceIdentifier = cert.institutionName.ifBlank { "Savings Certificate" },
                        description = "عائد ${cert.title}",
                        notes = "صرف عائد شهادة تلقائي (${cert.annualYieldPercent}%)",
                        timestampMillis = payoutMillis,
                        createdAt = nowMillis,
                        updatedAt = nowMillis
                    )
                    val insertedId = transactionDao.insertIgnoreDuplicate(tx)
                    if (insertedId != -1L) {
                        accountDao.adjustBalance(targetAccountId, payoutPiasters)
                        creditedCount++
                    }
                    if (cert.payoutCycle == PayoutCycle.MATURITY) {
                        matured = true
                    } else {
                        payoutMillis = advancePayoutDate(payoutMillis, cert.payoutCycle)
                    }
                }
                if (payoutMillis != cert.nextPayoutMillis || matured) {
                    savingsCertificateDao.update(
                        cert.copy(
                            nextPayoutMillis = payoutMillis,
                            isActive = !matured && cert.isActive
                        )
                    )
                }
            }
        }
        return creditedCount
    }

    private fun computeCyclePayoutPiasters(cert: SavingsCertificateEntity): Long {
        val annualPiasters = cert.principalPiasters.toDouble() * (cert.annualYieldPercent / 100.0)
        val divisor = when (cert.payoutCycle) {
            PayoutCycle.MONTHLY -> 12.0
            PayoutCycle.QUARTERLY -> 4.0
            PayoutCycle.SEMI_ANNUAL -> 2.0
            PayoutCycle.ANNUAL -> 1.0
            PayoutCycle.MATURITY -> 1.0
        }
        return kotlin.math.round(annualPiasters / divisor).toLong().coerceAtLeast(0L)
    }

    private fun advancePayoutDate(currentMillis: Long, cycle: PayoutCycle): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        when (cycle) {
            PayoutCycle.MONTHLY -> cal.add(Calendar.MONTH, 1)
            PayoutCycle.QUARTERLY -> cal.add(Calendar.MONTH, 3)
            PayoutCycle.SEMI_ANNUAL -> cal.add(Calendar.MONTH, 6)
            PayoutCycle.ANNUAL, PayoutCycle.MATURITY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    // --- Automatic Bank Balance Reconciliation ---

    suspend fun reconcileAccountBalance(
        accountId: Long,
        targetBankBalancePiasters: Long,
        sourceName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        return db.withTransaction {
            val acc = accountDao.getById(accountId) ?: return@withTransaction null
            val delta = targetBankBalancePiasters - acc.currentBalancePiasters
            if (delta == 0L) return@withTransaction null
            val type = if (delta > 0L) TransactionType.INCOME else TransactionType.EXPENSE
            val absAmount = kotlin.math.abs(delta)
            val txId = transactionDao.insert(
                TransactionEntity(
                    amountPiasters = absAmount,
                    type = type,
                    accountId = accountId,
                    source = TransactionSource.NOTIFICATION_IMPORT,
                    sourceIdentifier = sourceName,
                    description = "تسوية مطابقة رصيد $sourceName",
                    notes = "مزامنة رصيد التطبيق مع رصيد الرسالة البنكية (${targetBankBalancePiasters / 100.0})",
                    timestampMillis = nowMillis,
                    createdAt = nowMillis,
                    updatedAt = nowMillis
                )
            )
            accountDao.adjustBalance(accountId, delta)
            txId
        }
    }
}
