package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.BnplAnalysisEngine
import com.example.domain.engine.BurdenLevel
import com.example.domain.model.AccountType
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TransactionType
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BnplAndGuestFeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetGuestPreference() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.preferencesRepository.setGuestMode(false) }
    }

    @Test
    fun `room migration 3 to 4 creates bnpl_programs and alters installments safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v3_v4.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, openingBalancePiasters INTEGER NOT NULL, currentBalancePiasters INTEGER NOT NULL, currency TEXT NOT NULL, colorHex TEXT NOT NULL, iconName TEXT NOT NULL, isArchived INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS installments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, creditor TEXT NOT NULL, accountId INTEGER, totalAmountPiasters INTEGER NOT NULL, installmentAmountPiasters INTEGER NOT NULL, totalCount INTEGER NOT NULL, paidCount INTEGER NOT NULL, nextDueDateMillis INTEGER NOT NULL, frequency TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO installments (name, creditor, accountId, totalAmountPiasters, installmentAmountPiasters, totalCount, paidCount, nextDueDateMillis, frequency, status, createdAt) VALUES ('Phone', 'valU', NULL, 1200000, 100000, 12, 2, 1760000000000, 'MONTHLY', 'ACTIVE', 1000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        AppDatabase.MIGRATION_3_4.migrate(sqliteDb)

        sqliteDb.execSQL(
            "INSERT INTO bnpl_programs (providerCode, nameAr, nameEn, creditLimitPiasters, manualUsedPiasters, defaultAnnualInterestPercent, colorHex, isEnabled, notes, createdAt) VALUES ('VALU', 'فاليو', 'valU', 3000000, 0, 24.0, '#06B6D4', 1, '', 1000)"
        )
        val progCursor = sqliteDb.query("SELECT providerCode, creditLimitPiasters FROM bnpl_programs WHERE providerCode = 'VALU'")
        assertTrue(progCursor.moveToFirst())
        assertEquals("VALU", progCursor.getString(0))
        assertEquals(3_000_000L, progCursor.getLong(1))
        progCursor.close()

        val instCursor = sqliteDb.query("SELECT bnplProgramId, principalAmountPiasters FROM installments WHERE name = 'Phone'")
        assertTrue(instCursor.moveToFirst())
        assertTrue(instCursor.isNull(0))
        assertEquals(0L, instCursor.getLong(1))
        instCursor.close()

        sqliteDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `bnplAnalysisEngine computes limit utilization, interest cost, income ratio, and deficit causes`() {
        val now = 1_760_000_000_000L
        val valu = BnplProgramEntity(
            id = 1L,
            providerCode = "VALU",
            nameAr = "فاليو",
            nameEn = "valU",
            creditLimitPiasters = 2_000_000L, // 20,000 EGP
            manualUsedPiasters = 200_000L // 2,000 EGP extra
        )
        val klivr = BnplProgramEntity(
            id = 2L,
            providerCode = "KLIVR",
            nameAr = "كليفر",
            nameEn = "Klivr",
            creditLimitPiasters = 1_000_000L // 10,000 EGP
        )
        // 12 * 1,000 EGP = 12,000 EGP repayable vs 10,000 EGP cash price -> 2,000 EGP (20%) interest; 4 paid -> 8 remaining = 8,000 EGP
        val valuInst = InstallmentEntity(
            id = 10L,
            name = "iPhone",
            creditor = "valU",
            accountId = 1L,
            totalAmountPiasters = 1_200_000L,
            installmentAmountPiasters = 100_000L, // 1,000 EGP/mo
            totalCount = 12,
            paidCount = 4,
            nextDueDateMillis = now + 86_400_000L,
            frequency = InstallmentFrequency.MONTHLY,
            status = InstallmentStatus.ACTIVE,
            bnplProgramId = 1L,
            principalAmountPiasters = 1_000_000L
        )
        // Klivr plan pushing installments > 50% of 10,000 EGP income -> CRITICAL + deficit
        val klivrInst = InstallmentEntity(
            id = 11L,
            name = "Laptop",
            creditor = "Klivr",
            accountId = 1L,
            totalAmountPiasters = 600_000L,
            installmentAmountPiasters = 500_000L, // 5,000 EGP/mo
            totalCount = 2,
            paidCount = 0,
            nextDueDateMillis = now + 86_400_000L,
            frequency = InstallmentFrequency.MONTHLY,
            status = InstallmentStatus.ACTIVE,
            bnplProgramId = 2L,
            principalAmountPiasters = 550_000L
        )
        val salaryRule = RecurringRuleEntity(
            id = 1L,
            title = "Salary",
            type = TransactionType.INCOME,
            amountPiasters = 1_000_000L, // 10,000 EGP income
            accountId = 1L,
            frequency = RecurringFrequency.MONTHLY,
            nextRunDateMillis = now + 86_400_000L
        )
        val rentRule = RecurringRuleEntity(
            id = 2L,
            title = "Rent",
            type = TransactionType.EXPENSE,
            amountPiasters = 500_000L, // 5,000 EGP rent -> total outflow 6,000 + 5,000 = 11,000 > 10,000 EGP -> 1,000 EGP deficit!
            accountId = 1L,
            frequency = RecurringFrequency.MONTHLY,
            nextRunDateMillis = now + 86_400_000L
        )
        val metrics = DashboardMetrics(
            netBalancePiasters = 0L,
            totalIncomePiasters = 1_000_000L,
            totalExpensesPiasters = 0L,
            expenseToIncomeRatioPercent = 0.0,
            savingsRatePercent = 0.0,
            totalObligationsPiasters = 0L,
            amountsDuePiasters = 0L,
            remainingBudgetPiasters = 0L,
            netCashFlowPiasters = 0L,
            availableFundsPiasters = 0L,
            allocatedSavingsPiasters = 0L,
            categoryBreakdown = emptyList(),
            trendSeries = emptyList()
        )

        val report = BnplAnalysisEngine.analyze(
            programs = listOf(valu, klivr),
            installments = listOf(valuInst, klivrInst),
            recurringRules = listOf(salaryRule, rentRule),
            transactions = emptyList<TransactionEntity>(),
            accounts = listOf(AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, openingBalancePiasters = 0L, currentBalancePiasters = 0L)),
            monthMetrics = metrics,
            nowMillis = now
        )

        val valuReport = report.programAnalytics.first { it.program.id == 1L }
        assertEquals(1_000_000L, valuReport.utilizedLimitPiasters) // 800k remaining + 200k manual
        assertEquals(1_000_000L, valuReport.availableLimitPiasters)
        assertEquals(50.0, valuReport.utilizationPercent, 0.1)
        assertEquals(4, valuReport.paidPartsCount)
        assertEquals(8, valuReport.remainingPartsCount)
        assertEquals(200_000L, valuReport.totalInterestPiasters)
        assertEquals(20.0, valuReport.effectiveInterestPercent, 0.1)

        // Total monthly installments = 1,000 + 5,000 = 6,000 EGP = 60% of 10,000 EGP income
        assertEquals(60.0, report.deficitAnalysis.installmentsToIncomePercent, 0.1)
        assertEquals(BurdenLevel.CRITICAL, report.deficitAnalysis.burdenLevel)
        assertTrue(report.deficitAnalysis.hasDeficit)
        assertEquals(-100_000L, report.deficitAnalysis.netMonthlySurplusOrDeficitPiasters)
        assertTrue(report.deficitAnalysis.causes.any { it.id == "installments_burden" })
        assertTrue(report.deficitAnalysis.causes.any { it.id == "bnpl_interest" })
    }

    @Test
    fun `planning bnpl tab displays preseeded Egyptian BNPL programs and deficit analyzer`() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        composeTestRule.setContent {
            val vm = remember {
                MainViewModel(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = app
                )
            }
            val uiState by vm.uiState.collectAsStateWithLifecycle()
            ElbagorryMoneyTheme(
                themeMode = uiState.preferences.themeMode,
                themePreset = uiState.preferences.themePreset,
                language = uiState.preferences.language
            ) {
                ElbagorryMoneyRoot(viewModel = vm)
            }
        }

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("nav_tab_planning").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_planning").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("planning_tab_4").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("planning_tab_4").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("bnpl_portfolio_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("bnpl_portfolio_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("planning_lazy_list").performScrollToNode(hasTestTag("deficit_analysis_card"))
        composeTestRule.onNodeWithTag("deficit_analysis_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("planning_lazy_list").performScrollToNode(hasText("فاليو", substring = true))
        composeTestRule.onNodeWithText("فاليو", substring = true).assertIsDisplayed()
    }
}
