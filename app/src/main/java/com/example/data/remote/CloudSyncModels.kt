package com.example.data.remote

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AiChatMessageEntity
import com.example.data.local.entity.AiChatSessionEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AccountType
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.PayoutCycle
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.SavingsAssetKind
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "ELBAGORRY User",
    val email: String? = null,
    val currencyCode: String = "EGP",
    val themePreset: String? = null,
    val lastSyncedAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAccount(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val accountType: String = AccountType.CASH.name,
    val openingBalancePiasters: Long = 0L,
    val currentBalancePiasters: Long = 0L,
    val currency: String = "EGP",
    val colorHex: String = "#10B981",
    val iconName: String = "wallet",
    @get:PropertyName("isArchived")
    @field:PropertyName("isArchived")
    val isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudCategory(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val nameAr: String = "",
    val nameEn: String = "",
    val transactionType: String = TransactionType.EXPENSE.name,
    val iconName: String = "category",
    val colorHex: String = "#38BDF8",
    @get:PropertyName("isDefault")
    @field:PropertyName("isDefault")
    val isDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudTransaction(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val amountPiasters: Long = 0L,
    val transactionType: String = TransactionType.EXPENSE.name,
    val accountLocalId: Long = 0L,
    val targetAccountLocalId: Long? = null,
    val categoryLocalId: Long? = null,
    val description: String = "",
    val notes: String = "",
    val source: String = TransactionSource.MANUAL_ENTRY.name,
    val referenceNumber: String? = null,
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBudget(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val categoryLocalId: Long? = null,
    val limitPiasters: Long = 1L,
    val yearMonth: String = "2026-11",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudSavingsGoal(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val presetType: String = GoalPreset.CUSTOM.name,
    val targetAmountPiasters: Long = 1L,
    val savedAmountPiasters: Long = 0L,
    val status: String = GoalStatus.ACTIVE.name,
    val colorHex: String = "#10B981",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMerchantRule(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val keyword: String = "",
    val normalizedKeyword: String = "",
    val categoryLocalId: Long? = null,
    val accountLocalId: Long? = null,
    val preferredType: String = TransactionType.EXPENSE.name,
    val hitCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun AccountEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudAccount = CloudAccount(
    id = "acc_$id",
    localId = id,
    name = name.trim().ifBlank { "Account" }.take(100),
    accountType = type.name.take(40),
    openingBalancePiasters = openingBalancePiasters,
    currentBalancePiasters = currentBalancePiasters,
    currency = currency.trim().ifBlank { "EGP" }.take(6),
    colorHex = colorHex.trim().ifBlank { "#10B981" }.take(12),
    iconName = iconName.trim().ifBlank { "wallet" }.take(40),
    isArchived = isArchived,
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudAccount.toEntity(): AccountEntity = AccountEntity(
    id = localId,
    name = name.ifBlank { "Account" },
    type = runCatching { AccountType.valueOf(accountType) }.getOrDefault(AccountType.CASH),
    openingBalancePiasters = openingBalancePiasters,
    currentBalancePiasters = currentBalancePiasters,
    currency = currency.ifBlank { "EGP" },
    colorHex = colorHex.ifBlank { "#10B981" },
    iconName = iconName.ifBlank { "wallet" },
    isArchived = isArchived
)

fun CategoryEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudCategory = CloudCategory(
    id = "cat_$id",
    localId = id,
    nameAr = nameAr.trim().ifBlank { "فئة" }.take(80),
    nameEn = nameEn.trim().ifBlank { "Category" }.take(80),
    transactionType = type.name,
    iconName = iconName.trim().ifBlank { "category" }.take(40),
    colorHex = colorHex.trim().ifBlank { "#38BDF8" }.take(12),
    isDefault = isDefault,
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudCategory.toEntity(): CategoryEntity = CategoryEntity(
    id = localId,
    nameAr = nameAr.ifBlank { "فئة" },
    nameEn = nameEn.ifBlank { "Category" },
    type = runCatching { TransactionType.valueOf(transactionType) }.getOrDefault(TransactionType.EXPENSE),
    iconName = iconName.ifBlank { "category" },
    colorHex = colorHex.ifBlank { "#38BDF8" },
    isDefault = isDefault
)

fun TransactionEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudTransaction = CloudTransaction(
    id = "tx_$id",
    localId = id,
    amountPiasters = amountPiasters.coerceAtLeast(0L),
    transactionType = type.name,
    accountLocalId = accountId,
    targetAccountLocalId = targetAccountId?.takeIf { it > 0L },
    categoryLocalId = categoryId?.takeIf { it > 0L },
    description = description.trim().ifBlank { "Transaction" }.take(240),
    notes = notes.take(500),
    source = source.name.take(40),
    referenceNumber = referenceNumber?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
    timestampMillis = timestampMillis.coerceAtLeast(0L),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudTransaction.toEntity(): TransactionEntity = TransactionEntity(
    id = localId,
    amountPiasters = amountPiasters.coerceAtLeast(1L),
    type = runCatching { TransactionType.valueOf(transactionType) }.getOrDefault(TransactionType.EXPENSE),
    accountId = accountLocalId,
    targetAccountId = targetAccountLocalId,
    categoryId = categoryLocalId,
    description = description.ifBlank { "Transaction" },
    notes = notes,
    source = runCatching { TransactionSource.valueOf(source) }.getOrDefault(TransactionSource.MANUAL_ENTRY),
    referenceNumber = referenceNumber,
    timestampMillis = timestampMillis
)

fun BudgetEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudBudget = CloudBudget(
    id = "bud_$id",
    localId = id,
    name = name.trim().ifBlank { "Budget" }.take(100),
    categoryLocalId = categoryId?.takeIf { it > 0L },
    limitPiasters = limitPiasters.coerceAtLeast(1L),
    yearMonth = yearMonth.trim().ifBlank { "2026-11" }.take(10),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudBudget.toEntity(): BudgetEntity = BudgetEntity(
    id = localId,
    name = name.ifBlank { "Budget" },
    categoryId = categoryLocalId,
    limitPiasters = limitPiasters.coerceAtLeast(1L),
    yearMonth = yearMonth
)

fun SavingsGoalEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudSavingsGoal = CloudSavingsGoal(
    id = "goal_$id",
    localId = id,
    name = name.trim().ifBlank { "Goal" }.take(100),
    presetType = presetType.name.take(40),
    targetAmountPiasters = targetAmountPiasters.coerceAtLeast(1L),
    savedAmountPiasters = savedAmountPiasters.coerceAtLeast(0L),
    status = status.name.take(30),
    colorHex = colorHex.trim().ifBlank { "#10B981" }.take(12),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudSavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = localId,
    name = name.ifBlank { "Goal" },
    presetType = runCatching { GoalPreset.valueOf(presetType) }.getOrDefault(GoalPreset.CUSTOM),
    targetAmountPiasters = targetAmountPiasters.coerceAtLeast(1L),
    savedAmountPiasters = savedAmountPiasters.coerceAtLeast(0L),
    status = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.ACTIVE),
    colorHex = colorHex.ifBlank { "#10B981" }
)

fun MerchantRuleEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudMerchantRule = CloudMerchantRule(
    id = "rule_$id",
    localId = id,
    keyword = keyword.trim().ifBlank { "Rule" }.take(100),
    normalizedKeyword = normalizedKeyword.trim().ifBlank { "rule" }.take(100),
    categoryLocalId = categoryId?.takeIf { it > 0L },
    accountLocalId = accountId?.takeIf { it > 0L },
    preferredType = preferredType.name,
    hitCount = hitCount.toLong().coerceAtLeast(0L),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudMerchantRule.toEntity(): MerchantRuleEntity = MerchantRuleEntity(
    id = localId,
    keyword = keyword,
    normalizedKeyword = normalizedKeyword,
    categoryId = categoryLocalId,
    accountId = accountLocalId,
    preferredType = runCatching { TransactionType.valueOf(preferredType) }.getOrDefault(TransactionType.EXPENSE),
    hitCount = hitCount.toInt().coerceAtLeast(1)
)

@IgnoreExtraProperties
data class CloudAiChatSession(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val title: String = "",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAiChatMessage(
    @DocumentId val id: String = "",
    val messageId: String = "",
    val sessionId: String = "",
    @get:PropertyName("isUser")
    @field:PropertyName("isUser")
    val isUser: Boolean = false,
    val text: String = "",
    @get:PropertyName("isError")
    @field:PropertyName("isError")
    val isError: Boolean = false,
    @get:PropertyName("isUndone")
    @field:PropertyName("isUndone")
    val isUndone: Boolean = false,
    val engineSource: String = "CLOUD_GEMINI",
    val executedActionsJson: String = "[]",
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun AiChatSessionEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudAiChatSession {
    val cleanId = id.trim().take(120)
    val safeCreated = createdAtMillis.coerceAtLeast(0L)
    val safeUpdated = updatedAtMillis.coerceAtLeast(safeCreated)
    return CloudAiChatSession(
        id = cleanId,
        sessionId = cleanId,
        title = title.trim().ifBlank { "Chat" }.take(120),
        lastMessagePreview = lastMessagePreview.trim().take(200),
        messageCount = messageCount.toLong().coerceIn(0L, 5000L),
        createdAtMillis = safeCreated,
        updatedAtMillis = safeUpdated,
        createdAt = existingCreatedAt,
        updatedAt = null
    )
}

fun CloudAiChatSession.toEntity(): AiChatSessionEntity {
    val resolvedId = sessionId.ifBlank { id }
    return AiChatSessionEntity(
        id = resolvedId,
        title = title.ifBlank { "محادثة" },
        lastMessagePreview = lastMessagePreview,
        messageCount = messageCount.toInt().coerceAtLeast(0),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis.coerceAtLeast(createdAtMillis),
        syncedToCloud = true
    )
}

fun AiChatMessageEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudAiChatMessage {
    val safeEngine = if (engineSource == "LOCAL_OFFLINE") "LOCAL_OFFLINE" else "CLOUD_GEMINI"
    return CloudAiChatMessage(
        id = id.trim().take(120),
        messageId = id.trim().take(120),
        sessionId = sessionId.trim().take(120),
        isUser = isUser,
        text = text.ifBlank { " " }.take(12000),
        isError = isError,
        isUndone = isUndone,
        engineSource = safeEngine,
        executedActionsJson = executedActionsJson.ifBlank { "[]" }.take(8000),
        timestampMillis = timestampMillis.coerceAtLeast(0L),
        createdAt = existingCreatedAt,
        updatedAt = null
    )
}

fun CloudAiChatMessage.toEntity(): AiChatMessageEntity = AiChatMessageEntity(
    id = messageId.ifBlank { id },
    sessionId = sessionId,
    isUser = isUser,
    text = text,
    isError = isError,
    isUndone = isUndone,
    engineSource = if (engineSource == "LOCAL_OFFLINE") "LOCAL_OFFLINE" else "CLOUD_GEMINI",
    executedActionsJson = executedActionsJson.ifBlank { "[]" },
    timestampMillis = timestampMillis.coerceAtLeast(0L)
)

@IgnoreExtraProperties
data class CloudInstallment(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val creditor: String = "",
    val accountLocalId: Long? = null,
    val totalAmountPiasters: Long = 0L,
    val installmentAmountPiasters: Long = 0L,
    val totalCount: Long = 1L,
    val paidCount: Long = 0L,
    val nextDueDateMillis: Long = 0L,
    val frequency: String = InstallmentFrequency.MONTHLY.name,
    val status: String = InstallmentStatus.ACTIVE.name,
    val bnplProgramId: Long? = null,
    val principalAmountPiasters: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun InstallmentEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudInstallment = CloudInstallment(
    id = "inst_$id",
    localId = id,
    name = name.trim().ifBlank { "Installment" }.take(120),
    creditor = creditor.trim().ifBlank { "Creditor" }.take(120),
    accountLocalId = accountId?.takeIf { it > 0L },
    totalAmountPiasters = totalAmountPiasters.coerceAtLeast(0L),
    installmentAmountPiasters = installmentAmountPiasters.coerceAtLeast(0L),
    totalCount = totalCount.toLong().coerceAtLeast(1L),
    paidCount = paidCount.toLong().coerceAtLeast(0L),
    nextDueDateMillis = nextDueDateMillis.coerceAtLeast(0L),
    frequency = frequency.name.take(30),
    status = status.name.take(30),
    bnplProgramId = bnplProgramId?.takeIf { it > 0L },
    principalAmountPiasters = principalAmountPiasters.coerceAtLeast(0L),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudInstallment.toEntity(): InstallmentEntity = InstallmentEntity(
    id = localId,
    name = name.ifBlank { "Installment" },
    creditor = creditor,
    accountId = accountLocalId,
    totalAmountPiasters = totalAmountPiasters.coerceAtLeast(0L),
    installmentAmountPiasters = installmentAmountPiasters.coerceAtLeast(0L),
    totalCount = totalCount.toInt().coerceAtLeast(1),
    paidCount = paidCount.toInt().coerceAtLeast(0),
    nextDueDateMillis = nextDueDateMillis,
    frequency = runCatching { InstallmentFrequency.valueOf(frequency) }.getOrDefault(InstallmentFrequency.MONTHLY),
    status = runCatching { InstallmentStatus.valueOf(status) }.getOrDefault(InstallmentStatus.ACTIVE),
    bnplProgramId = bnplProgramId,
    principalAmountPiasters = principalAmountPiasters.coerceAtLeast(0L)
)

@IgnoreExtraProperties
data class CloudRecurringRule(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val title: String = "",
    val transactionType: String = TransactionType.EXPENSE.name,
    val incomeSourceType: String = IncomeSourceType.CUSTOM.name,
    val amountPiasters: Long = 0L,
    val accountLocalId: Long = 0L,
    val categoryLocalId: Long? = null,
    val frequency: String = RecurringFrequency.MONTHLY.name,
    val nextRunDateMillis: Long = 0L,
    val notifyDaysBefore: Long = 2L,
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun RecurringRuleEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudRecurringRule = CloudRecurringRule(
    id = "rec_$id",
    localId = id,
    title = title.trim().ifBlank { "Recurring Rule" }.take(120),
    transactionType = type.name,
    incomeSourceType = incomeSourceType.name.take(40),
    amountPiasters = amountPiasters.coerceAtLeast(0L),
    accountLocalId = accountId,
    categoryLocalId = categoryId?.takeIf { it > 0L },
    frequency = frequency.name.take(30),
    nextRunDateMillis = nextRunDateMillis.coerceAtLeast(0L),
    notifyDaysBefore = notifyDaysBefore.toLong().coerceIn(0L, 60L),
    isActive = isActive,
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudRecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = localId,
    title = title.ifBlank { "Recurring Rule" },
    type = runCatching { TransactionType.valueOf(transactionType) }.getOrDefault(TransactionType.EXPENSE),
    incomeSourceType = runCatching { IncomeSourceType.valueOf(incomeSourceType) }.getOrDefault(IncomeSourceType.CUSTOM),
    amountPiasters = amountPiasters.coerceAtLeast(1L),
    accountId = accountLocalId,
    categoryId = categoryLocalId,
    frequency = runCatching { RecurringFrequency.valueOf(frequency) }.getOrDefault(RecurringFrequency.MONTHLY),
    nextRunDateMillis = nextRunDateMillis,
    notifyDaysBefore = notifyDaysBefore.toInt().coerceIn(0, 60),
    isActive = isActive
)

@IgnoreExtraProperties
data class CloudDebt(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val personName: String = "",
    val direction: String = DebtDirection.OWED_BY_ME.name,
    val totalAmountPiasters: Long = 0L,
    val paidAmountPiasters: Long = 0L,
    val dueDateMillis: Long? = null,
    val status: String = DebtStatus.OPEN.name,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun DebtEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudDebt = CloudDebt(
    id = "debt_$id",
    localId = id,
    personName = personName.trim().ifBlank { "Contact" }.take(120),
    direction = direction.name.take(30),
    totalAmountPiasters = totalAmountPiasters.coerceAtLeast(0L),
    paidAmountPiasters = paidAmountPiasters.coerceAtLeast(0L),
    dueDateMillis = dueDateMillis?.takeIf { it > 0L },
    status = status.name.take(30),
    notes = notes.take(500),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudDebt.toEntity(): DebtEntity = DebtEntity(
    id = localId,
    personName = personName.ifBlank { "Contact" },
    direction = runCatching { DebtDirection.valueOf(direction) }.getOrDefault(DebtDirection.OWED_BY_ME),
    totalAmountPiasters = totalAmountPiasters.coerceAtLeast(0L),
    paidAmountPiasters = paidAmountPiasters.coerceAtLeast(0L),
    dueDateMillis = dueDateMillis,
    status = runCatching { DebtStatus.valueOf(status) }.getOrDefault(DebtStatus.OPEN),
    notes = notes
)

@IgnoreExtraProperties
data class CloudBnplProgram(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val providerCode: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val creditLimitPiasters: Long = 0L,
    val manualUsedPiasters: Long = 0L,
    val defaultAnnualInterestPercent: Double = 0.0,
    val colorHex: String = "#38BDF8",
    @get:PropertyName("isEnabled")
    @field:PropertyName("isEnabled")
    val isEnabled: Boolean = true,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun BnplProgramEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudBnplProgram = CloudBnplProgram(
    id = "bnpl_$id",
    localId = id,
    providerCode = providerCode.trim().ifBlank { "BNPL_$id" }.take(50),
    nameAr = nameAr.trim().ifBlank { "تقسيط" }.take(100),
    nameEn = nameEn.trim().ifBlank { "BNPL" }.take(100),
    creditLimitPiasters = creditLimitPiasters.coerceAtLeast(0L),
    manualUsedPiasters = manualUsedPiasters.coerceAtLeast(0L),
    defaultAnnualInterestPercent = defaultAnnualInterestPercent.coerceIn(0.0, 200.0),
    colorHex = colorHex.trim().ifBlank { "#38BDF8" }.take(12),
    isEnabled = isEnabled,
    notes = notes.take(500),
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudBnplProgram.toEntity(): BnplProgramEntity = BnplProgramEntity(
    id = localId,
    providerCode = providerCode.ifBlank { "BNPL_$localId" },
    nameAr = nameAr.ifBlank { "تقسيط" },
    nameEn = nameEn.ifBlank { "BNPL" },
    creditLimitPiasters = creditLimitPiasters.coerceAtLeast(0L),
    manualUsedPiasters = manualUsedPiasters.coerceAtLeast(0L),
    defaultAnnualInterestPercent = defaultAnnualInterestPercent.coerceAtLeast(0.0),
    colorHex = colorHex.ifBlank { "#38BDF8" },
    isEnabled = isEnabled,
    notes = notes
)

@IgnoreExtraProperties
data class CloudFinancialSource(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val code: String = "",
    val displayNameAr: String = "",
    val displayNameEn: String = "",
    val senderKeywords: String = "",
    val packageNames: String = "",
    val defaultAccountLocalId: Long? = null,
    @get:PropertyName("isEnabled")
    @field:PropertyName("isEnabled")
    val isEnabled: Boolean = true,
    @get:PropertyName("isIgnored")
    @field:PropertyName("isIgnored")
    val isIgnored: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun FinancialSourceEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudFinancialSource = CloudFinancialSource(
    id = "src_$id",
    localId = id,
    code = code.trim().ifBlank { "SRC_$id" }.take(60),
    displayNameAr = displayNameAr.trim().ifBlank { "مصدر مالي" }.take(100),
    displayNameEn = displayNameEn.trim().ifBlank { "Financial Source" }.take(100),
    senderKeywords = senderKeywords.take(500),
    packageNames = packageNames.take(500),
    defaultAccountLocalId = defaultAccountId?.takeIf { it > 0L },
    isEnabled = isEnabled,
    isIgnored = isIgnored,
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudFinancialSource.toEntity(): FinancialSourceEntity = FinancialSourceEntity(
    id = localId,
    code = code.ifBlank { "SRC_$localId" },
    displayNameAr = displayNameAr.ifBlank { "مصدر مالي" },
    displayNameEn = displayNameEn.ifBlank { "Financial Source" },
    senderKeywords = senderKeywords,
    packageNames = packageNames,
    defaultAccountId = defaultAccountLocalId,
    isEnabled = isEnabled,
    isIgnored = isIgnored
)

@IgnoreExtraProperties
data class CloudSavingsCertificate(
    @DocumentId val id: String = "",
    val localId: Long = 0L,
    val title: String = "",
    val assetKind: String = SavingsAssetKind.BANK_CERTIFICATE.name,
    val institutionName: String = "",
    val principalPiasters: Long = 0L,
    val annualYieldPercent: Double = 0.0,
    val payoutCycle: String = PayoutCycle.MONTHLY.name,
    val linkedAccountLocalId: Long? = null,
    val nextPayoutMillis: Long = 0L,
    val maturityMillis: Long? = null,
    val autoCreditInterest: Boolean = true,
    val grams: Double = 0.0,
    val karat: Long = 21L,
    val pricePerGramPiasters: Long = 0L,
    val notes: String = "",
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun SavingsCertificateEntity.toCloudModel(existingCreatedAt: Timestamp? = null): CloudSavingsCertificate = CloudSavingsCertificate(
    id = "cert_$id",
    localId = id,
    title = title.trim().ifBlank { "Asset" }.take(120),
    assetKind = assetKind.name.take(40),
    institutionName = institutionName.trim().take(120),
    principalPiasters = principalPiasters.coerceAtLeast(0L),
    annualYieldPercent = annualYieldPercent.coerceIn(0.0, 200.0),
    payoutCycle = payoutCycle.name.take(30),
    linkedAccountLocalId = linkedAccountId?.takeIf { it > 0L },
    nextPayoutMillis = nextPayoutMillis.coerceAtLeast(0L),
    maturityMillis = maturityMillis?.takeIf { it > 0L },
    autoCreditInterest = autoCreditInterest,
    grams = grams.coerceAtLeast(0.0),
    karat = karat.toLong().coerceIn(1L, 24L),
    pricePerGramPiasters = pricePerGramPiasters.coerceAtLeast(0L),
    notes = notes.take(500),
    isActive = isActive,
    createdAt = existingCreatedAt,
    updatedAt = null
)

fun CloudSavingsCertificate.toEntity(): SavingsCertificateEntity = SavingsCertificateEntity(
    id = localId,
    title = title.ifBlank { "Asset" },
    assetKind = runCatching { SavingsAssetKind.valueOf(assetKind) }.getOrDefault(SavingsAssetKind.BANK_CERTIFICATE),
    institutionName = institutionName,
    principalPiasters = principalPiasters.coerceAtLeast(0L),
    annualYieldPercent = annualYieldPercent.coerceAtLeast(0.0),
    payoutCycle = runCatching { PayoutCycle.valueOf(payoutCycle) }.getOrDefault(PayoutCycle.MONTHLY),
    linkedAccountId = linkedAccountLocalId,
    nextPayoutMillis = nextPayoutMillis,
    maturityMillis = maturityMillis,
    autoCreditInterest = autoCreditInterest,
    grams = grams.coerceAtLeast(0.0),
    karat = karat.toInt().coerceIn(1, 24),
    pricePerGramPiasters = pricePerGramPiasters.coerceAtLeast(0L),
    notes = notes,
    isActive = isActive
)

