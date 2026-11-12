package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupAndExportManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.domain.model.AccountType
import com.example.domain.model.GoalPreset
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.PendingStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.example.reader.IncomingRawMessage
import com.example.reader.SmartReaderProcessor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repo: MoneyRepository
    private lateinit var backupManager: BackupAndExportManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MoneyRepository(db)
        backupManager = BackupAndExportManager(db)
        runBlocking { repo.ensureSeeded() }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `read app_name from context matches ELBAGORRY Money`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ELBAGORRY Money", appName)
    }

    @Test
    fun `repository add edit delete transfer installment savings and recurring are atomic`() = runBlocking {
        val accounts = repo.getAllAccountsOnce()
        val cash = accounts[0]
        val bank = accounts[1]

        // 1. Add Income & Expense
        val incomeId = repo.addTransaction(
            TransactionEntity(amountPiasters = 50_000L, type = TransactionType.INCOME, accountId = bank.id, description = "Salary")
        )
        val expenseId = repo.addTransaction(
            TransactionEntity(amountPiasters = 10_000L, type = TransactionType.EXPENSE, accountId = bank.id, description = "Coffee")
        )
        assertEquals(40_000L, repo.getAllAccountsOnce().first { it.id == bank.id }.currentBalancePiasters)

        // 2. Edit & Delete transaction (verifies atomic rollback)
        val coffee = repo.getAllTransactionsOnce().first { it.id == expenseId }
        repo.updateTransaction(coffee.copy(amountPiasters = 15_000L))
        assertEquals(35_000L, repo.getAllAccountsOnce().first { it.id == bank.id }.currentBalancePiasters)

        repo.deleteTransaction(repo.getAllTransactionsOnce().first { it.id == incomeId })
        assertEquals(-15_000L, repo.getAllAccountsOnce().first { it.id == bank.id }.currentBalancePiasters)

        // Add fresh funds for transfer/installment/savings tests
        repo.addTransaction(
            TransactionEntity(amountPiasters = 200_000L, type = TransactionType.INCOME, accountId = bank.id, description = "Deposit")
        )
        // Bank = 185_000, Cash = 0

        // 3. Transfer preserves total net balance
        repo.addTransaction(
            TransactionEntity(amountPiasters = 50_000L, type = TransactionType.TRANSFER, accountId = bank.id, targetAccountId = cash.id, description = "Transfer")
        )
        val afterTransferAccounts = repo.getAllAccountsOnce()
        assertEquals(135_000L, afterTransferAccounts.first { it.id == bank.id }.currentBalancePiasters)
        assertEquals(50_000L, afterTransferAccounts.first { it.id == cash.id }.currentBalancePiasters)

        // 4. Pay installment creates exactly one linked expense
        val instId = repo.saveInstallment(
            InstallmentEntity(
                name = "Laptop",
                creditor = "TechStore",
                accountId = bank.id,
                totalAmountPiasters = 60_000L,
                installmentAmountPiasters = 20_000L,
                totalCount = 3,
                nextDueDateMillis = System.currentTimeMillis(),
                frequency = InstallmentFrequency.MONTHLY
            )
        )
        val beforeTxCount = repo.getAllTransactionsOnce().size
        repo.payInstallment(instId, bank.id)
        val afterTxs = repo.getAllTransactionsOnce()
        assertEquals(beforeTxCount + 1, afterTxs.size)
        val linkedExp = afterTxs.first { it.installmentId == instId }
        assertEquals(TransactionType.EXPENSE, linkedExp.type)
        assertEquals(1, repo.getAllInstallmentsOnce().first { it.id == instId }.paidCount)
        assertEquals(115_000L, repo.getAllAccountsOnce().first { it.id == bank.id }.currentBalancePiasters)

        // 5. Savings contribution updates goal without adding expense
        val goalId = repo.saveGoal(
            SavingsGoalEntity(name = "Car", presetType = GoalPreset.CAR, targetAmountPiasters = 100_000L)
        )
        repo.addTransaction(
            TransactionEntity(amountPiasters = 25_000L, type = TransactionType.SAVINGS_CONTRIBUTION, accountId = cash.id, savingsGoalId = goalId, description = "Save")
        )
        assertEquals(25_000L, repo.getAllGoalsOnce().first { it.id == goalId }.savedAmountPiasters)
        assertEquals(25_000L, repo.getAllAccountsOnce().first { it.id == cash.id }.currentBalancePiasters)

        // 6. Recurring rule idempotency: running twice for same cycle never duplicates
        val now = System.currentTimeMillis()
        repo.saveRecurringRule(
            RecurringRuleEntity(
                title = "Monthly Salary",
                type = TransactionType.INCOME,
                incomeSourceType = IncomeSourceType.MONTHLY_SALARY,
                amountPiasters = 100_000L,
                accountId = bank.id,
                frequency = RecurringFrequency.MONTHLY,
                nextRunDateMillis = now - 1000L
            )
        )
        val firstExec = repo.executeDueRecurringRules(now)
        val secondExec = repo.executeDueRecurringRules(now)
        assertEquals(1, firstExec)
        assertEquals(0, secondExec)

        // 7. JSON Backup & Restore + Invalid JSON rejection
        val jsonBackup = backupManager.createJsonBackup()
        assertTrue(backupManager.validateBackupJson(jsonBackup).isSuccess)
        assertTrue(backupManager.validateBackupJson("{invalid_json").isFailure)

        db.transactionDao().deleteAll()
        assertEquals(0, repo.getAllTransactionsOnce().size)
        val restoreResult = backupManager.restoreFromJson(jsonBackup)
        assertTrue(restoreResult.isSuccess)
        assertTrue(repo.getAllTransactionsOnce().isNotEmpty())
    }

    @Test
    fun `smart reader ingests approves rejects deduplicates and never stores OTP or duplicates balances`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        prefs.setSmartReaderEnabled(true)
        val processor = SmartReaderProcessor(db, prefs)

        // Seeded financial sources exist
        val sources = repo.getAllFinancialSourcesOnce()
        assertTrue(sources.any { it.code == "VODAFONE_CASH" })
        assertTrue(sources.any { it.code == "INSTAPAY" })

        val walletAcc = repo.getAllAccountsOnce().first { it.type == AccountType.E_WALLET }
        val bankAcc = repo.getAllAccountsOnce().first { it.type == AccountType.BANK }
        val initialWalletBal = walletAcc.currentBalancePiasters
        val initialBankBal = bankAcc.currentBalancePiasters

        // 1. Ingest OTP + Balance Inquiry + Promotional + Outgoing transfer with Fee
        val batch = listOf(
            IncomingRawMessage("VodafoneCash", "كود التحقق OTP هو 123456 لا تشاركه مع أحد"),
            IncomingRawMessage("VodafoneCash", "فودافون كاش: استعلام عن الرصيد المتاح 5000 ج.م"),
            IncomingRawMessage("OrangeCash", "عرض خاص اكسب خصم يصل 50% عند الاشتراك الآن"),
            IncomingRawMessage("VodafoneCash", "فودافون كاش: تم تحويل مبلغ 500 ج.م إلى علي برسوم 5 ج.م رقم المرجع VF102030")
        )
        val firstRun = processor.ingestMessages(batch, MessageChannel.CLIPBOARD_PASTE)
        assertEquals(4, firstRun.totalScanned)
        assertEquals(1, firstRun.financialDetected)
        assertEquals(1, firstRun.pendingCreated)
        assertEquals(3, firstRun.ignoredFiltered)

        // Two pending rows created: main transfer (500 EGP) + independent fee (5 EGP)
        val pendingList = repo.getAllPendingOnce().filter { it.status == PendingStatus.PENDING }
        assertEquals(2, pendingList.size)
        // Only the 1 financial message stored in imported_messages (OTP/promo never stored!)
        assertEquals(1, db.importedMessageDao().getAllOnce().size)

        // Balance NOT modified yet until user approves
        assertEquals(initialWalletBal, repo.getAllAccountsOnce().first { it.id == walletAcc.id }.currentBalancePiasters)

        // 2. Re-import same transfer message -> deduplicated, zero new pending, zero balance impact
        val dupRun = processor.ingestMessages(
            listOf(IncomingRawMessage("VodafoneCash", "فودافون كاش: تم تحويل مبلغ 500 ج.م إلى علي برسوم 5 ج.م رقم المرجع VF102030")),
            MessageChannel.SHARE_INTENT
        )
        assertEquals(1, dupRun.duplicatesSkipped)
        assertEquals(0, dupRun.pendingCreated)
        assertEquals(2, repo.getAllPendingOnce().filter { it.status == PendingStatus.PENDING }.size)

        // 3. Approve main transfer (500 EGP) and reject fee (5 EGP)
        val mainPending = pendingList.first { it.amountPiasters == 50_000L }
        val feePending = pendingList.first { it.amountPiasters == 500L }

        val approveRes = repo.approvePendingTransaction(mainPending.id, walletAcc.id)
        assertTrue(approveRes.isSuccess)
        repo.rejectPendingTransaction(feePending.id)

        val updatedWallet = repo.getAllAccountsOnce().first { it.id == walletAcc.id }
        assertEquals(initialWalletBal - 50_000L, updatedWallet.currentBalancePiasters)

        val createdTx = repo.getAllTransactionsOnce().first { it.id == approveRes.getOrThrow() }
        assertEquals(TransactionSource.SHARE_IMPORT, createdTx.source)
        assertEquals("VF102030", createdTx.referenceNumber)

        // 4. Re-import ALREADY APPROVED message -> still rejected as duplicate, balance unchanged!
        val afterApproveDup = processor.ingestMessages(
            listOf(IncomingRawMessage("VodafoneCash", "فودافون كاش: تم تحويل مبلغ 500 ج.م إلى علي برسوم 5 ج.م رقم المرجع VF102030")),
            MessageChannel.FILE_IMPORT
        )
        assertEquals(1, afterApproveDup.duplicatesSkipped)
        assertEquals(0, afterApproveDup.pendingCreated)
        assertEquals(initialWalletBal - 50_000L, repo.getAllAccountsOnce().first { it.id == walletAcc.id }.currentBalancePiasters)
        assertEquals(initialBankBal, repo.getAllAccountsOnce().first { it.id == bankAcc.id }.currentBalancePiasters)

        // 5. Custom Category creation & default protection
        val customCatId = repo.createCategory(
            CategoryEntity(nameAr = "قهوة", nameEn = "Coffee", type = TransactionType.EXPENSE, colorHex = "#10B981", isDefault = false)
        )
        assertTrue(repo.getAllCategoriesOnce().any { it.id == customCatId && it.nameAr == "قهوة" })
        val defaultCat = repo.getAllCategoriesOnce().first { it.isDefault }
        repo.deleteCategory(defaultCat)
        assertTrue(repo.getAllCategoriesOnce().any { it.id == defaultCat.id })
    }

    @Test
    fun `compose ui navigates tabs opens quick add sheet smart reader and categories`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("privacy_toggle_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_smart_reader_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_smart_reader_card").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("smart_reader_master_switch").assertExists()
        composeTestRule.onNodeWithTag("paste_message_btn").assertExists()

        // Navigate back to dashboard via activity back
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_categories_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_categories_card").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_category_btn").assertExists()

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("fab_quick_add").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tx_amount_input").assertExists()
        composeTestRule.onNodeWithTag("save_transaction_button").assertExists()
    }
}
