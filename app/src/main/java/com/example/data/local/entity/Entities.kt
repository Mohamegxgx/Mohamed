package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountType
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.NotificationType
import com.example.domain.model.PendingStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalancePiasters: Long,
    val currentBalancePiasters: Long,
    val currency: String = "EGP",
    val colorHex: String = "#10B981",
    val iconName: String = "wallet",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameAr: String,
    val nameEn: String,
    val type: TransactionType, // INCOME or EXPENSE
    val iconName: String = "category",
    val colorHex: String = "#38BDF8",
    val isDefault: Boolean = false
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["savingsGoalId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InstallmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["installmentId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["targetAccountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["savingsGoalId"]),
        Index(value = ["installmentId"]),
        Index(value = ["debtId"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["recurringCycleKey"], unique = true),
        Index(value = ["dedupFingerprint"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPiasters: Long,
    val type: TransactionType,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val categoryId: Long? = null,
    val savingsGoalId: Long? = null,
    val installmentId: Long? = null,
    val debtId: Long? = null,
    val recurringRuleId: Long? = null,
    val recurringCycleKey: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL_ENTRY,
    val referenceNumber: String? = null,
    val sourceIdentifier: String? = null,
    val dedupFingerprint: String? = null,
    val description: String,
    val notes: String = "",
    val attachmentUri: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["yearMonth"])]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long? = null, // null = general monthly budget
    val limitPiasters: Long,
    val yearMonth: String, // e.g., "2026-11"
    val isCustomPeriod: Boolean = false,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val presetType: GoalPreset = GoalPreset.CUSTOM,
    val targetAmountPiasters: Long,
    val savedAmountPiasters: Long = 0L,
    val targetDateMillis: Long? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val colorHex: String = "#10B981",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["nextDueDateMillis"])]
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val creditor: String,
    val accountId: Long? = null,
    val totalAmountPiasters: Long,
    val installmentAmountPiasters: Long,
    val totalCount: Int,
    val paidCount: Int = 0,
    val nextDueDateMillis: Long,
    val frequency: InstallmentFrequency = InstallmentFrequency.MONTHLY,
    val status: InstallmentStatus = InstallmentStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "installment_payments",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["installmentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["installmentId"]), Index(value = ["transactionId"])]
)
data class InstallmentPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val installmentId: Long,
    val transactionId: Long? = null,
    val installmentNumber: Int,
    val amountPiasters: Long,
    val paidAtMillis: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Serializable
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["categoryId"]), Index(value = ["nextRunDateMillis"])]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TransactionType, // INCOME or EXPENSE
    val incomeSourceType: IncomeSourceType = IncomeSourceType.CUSTOM,
    val amountPiasters: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val nextRunDateMillis: Long,
    val notifyDaysBefore: Int = 2,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val direction: DebtDirection,
    val totalAmountPiasters: Long,
    val paidAmountPiasters: Long = 0L,
    val dueDateMillis: Long? = null,
    val status: DebtStatus = DebtStatus.OPEN,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NotificationType,
    val title: String,
    val message: String,
    val relatedEntityId: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Serializable
@Entity(tableName = "financial_sources")
data class FinancialSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val displayNameAr: String,
    val displayNameEn: String,
    val senderKeywords: String, // comma-separated keywords
    val packageNames: String = "", // comma-separated Android package names for notification listener
    val defaultAccountId: Long? = null,
    val isEnabled: Boolean = true,
    val isIgnored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "imported_messages",
    indices = [Index(value = ["fingerprintHash"], unique = true)]
)
data class ImportedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long? = null,
    val sourceName: String,
    val channel: MessageChannel,
    val senderLabel: String,
    val fingerprintHash: String,
    val status: ImportedMessageStatus,
    val confidenceScore: Float,
    val messageTimestampMillis: Long,
    val importedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "pending_transactions",
    foreignKeys = [
        ForeignKey(
            entity = ImportedMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["importedMessageId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestedAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestedCategoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["importedMessageId"]),
        Index(value = ["suggestedAccountId"]),
        Index(value = ["suggestedCategoryId"]),
        Index(value = ["dedupFingerprint"], unique = true)
    ]
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importedMessageId: Long? = null,
    val sourceId: Long? = null,
    val sourceName: String,
    val channel: MessageChannel,
    val operationKind: MessageOperationKind,
    val mappedType: TransactionType,
    val amountPiasters: Long,
    val feePiasters: Long = 0L,
    val currency: String = "EGP",
    val referenceNumber: String? = null,
    val counterpartyName: String? = null,
    val balanceAfterPiasters: Long? = null,
    val transactionTimestampMillis: Long,
    val suggestedAccountId: Long? = null,
    val targetAccountId: Long? = null,
    val suggestedCategoryId: Long? = null,
    val confidenceScore: Float,
    val dedupFingerprint: String,
    val extractedSummary: String,
    val status: PendingStatus = PendingStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: MessageChannel,
    val totalScanned: Int,
    val financialDetected: Int,
    val pendingCreated: Int,
    val duplicatesSkipped: Int,
    val ignoredFiltered: Int,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "merchant_rules",
    indices = [Index(value = ["normalizedKeyword"], unique = true)]
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val normalizedKeyword: String,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val preferredType: TransactionType = TransactionType.EXPENSE,
    val hitCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
