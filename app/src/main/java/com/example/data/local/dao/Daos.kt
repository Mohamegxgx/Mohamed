package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
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
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.PendingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET currentBalancePiasters = currentBalancePiasters + :deltaPiasters WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, deltaPiasters: Long)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupFingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE referenceNumber = :refNumber AND sourceIdentifier = :sourceName")
    suspend fun countByReferenceAndSource(refNumber: String, sourceName: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreDuplicate(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC, id DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC, id DESC")
    suspend fun getAllOnce(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun getByMonth(yearMonth: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("UPDATE savings_goals SET savedAmountPiasters = MAX(0, savedAmountPiasters + :deltaPiasters) WHERE id = :goalId")
    suspend fun adjustSavedAmount(goalId: Long, deltaPiasters: Long)

    @Delete
    suspend fun delete(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY nextDueDateMillis ASC")
    fun observeAll(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments ORDER BY nextDueDateMillis ASC")
    suspend fun getAllOnce(): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InstallmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installment: InstallmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(installments: List<InstallmentEntity>)

    @Update
    suspend fun update(installment: InstallmentEntity)

    @Delete
    suspend fun delete(installment: InstallmentEntity)

    @Query("DELETE FROM installments")
    suspend fun deleteAll()
}

@Dao
interface InstallmentPaymentDao {
    @Query("SELECT * FROM installment_payments ORDER BY paidAtMillis DESC")
    fun observeAll(): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installment_payments ORDER BY paidAtMillis DESC")
    suspend fun getAllOnce(): List<InstallmentPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: InstallmentPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<InstallmentPaymentEntity>)

    @Query("DELETE FROM installment_payments")
    suspend fun deleteAll()
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY nextRunDateMillis ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY nextRunDateMillis ASC")
    suspend fun getAllOnce(): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: DebtEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtEntity>)

    @Update
    suspend fun update(debt: DebtEntity)

    @Delete
    suspend fun delete(debt: DebtEntity)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestampMillis DESC")
    suspend fun getAllOnce(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

@Dao
interface FinancialSourceDao {
    @Query("SELECT * FROM financial_sources ORDER BY id ASC")
    fun observeAll(): Flow<List<FinancialSourceEntity>>

    @Query("SELECT * FROM financial_sources ORDER BY id ASC")
    suspend fun getAllOnce(): List<FinancialSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: FinancialSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<FinancialSourceEntity>)

    @Update
    suspend fun update(source: FinancialSourceEntity)

    @Delete
    suspend fun delete(source: FinancialSourceEntity)

    @Query("DELETE FROM financial_sources")
    suspend fun deleteAll()
}

@Dao
interface ImportedMessageDao {
    @Query("SELECT * FROM imported_messages ORDER BY importedAtMillis DESC")
    fun observeAll(): Flow<List<ImportedMessageEntity>>

    @Query("SELECT * FROM imported_messages ORDER BY importedAtMillis DESC")
    suspend fun getAllOnce(): List<ImportedMessageEntity>

    @Query("SELECT COUNT(*) FROM imported_messages WHERE fingerprintHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: ImportedMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ImportedMessageEntity>)

    @Query("UPDATE imported_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ImportedMessageStatus)

    @Query("DELETE FROM imported_messages")
    suspend fun deleteAll()
}

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions ORDER BY createdAt DESC, id DESC")
    suspend fun getAllOnce(): List<PendingTransactionEntity>

    @Query("SELECT * FROM pending_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE dedupFingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: PendingTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PendingTransactionEntity>)

    @Update
    suspend fun update(item: PendingTransactionEntity)

    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PendingStatus)

    @Query("DELETE FROM pending_transactions WHERE status != 'APPROVED'")
    suspend fun deleteUnapproved()

    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAll()
}

@Dao
interface ImportHistoryDao {
    @Query("SELECT * FROM import_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ImportHistoryEntity>>

    @Query("SELECT * FROM import_history ORDER BY timestampMillis DESC")
    suspend fun getAllOnce(): List<ImportHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ImportHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<ImportHistoryEntity>)

    @Query("DELETE FROM import_history")
    suspend fun deleteAll()
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, createdAt DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, createdAt DESC")
    suspend fun getAllOnce(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE normalizedKeyword = :normalizedKeyword LIMIT 1")
    suspend fun findByNormalizedKeyword(normalizedKeyword: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MerchantRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Delete
    suspend fun delete(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
