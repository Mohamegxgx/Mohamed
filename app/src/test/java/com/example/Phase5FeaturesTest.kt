package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.AnalyticsEngine
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.FinancialHealthEngine
import com.example.domain.engine.HealthGrade
import com.example.domain.model.AccountType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5FeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setMainAppContent() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        composeTestRule.setContent {
            val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val vm = remember {
                MainViewModel(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = app
                )
            }
            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                ElbagorryMoneyRoot(viewModel = vm)
            }
        }
    }

    @Test
    fun financialHealthEngine_computesScoreRunwaySafeSpendAndTemplates() {
        val now = 1_760_000_000_000L
        val accounts = listOf(
            AccountEntity(
                id = 1L,
                name = "Bank",
                type = AccountType.BANK,
                openingBalancePiasters = 500_000L,
                currentBalancePiasters = 500_000L,
                currency = "EGP"
            )
        )
        val transactions = listOf(
            TransactionEntity(id = 1L, amountPiasters = 1_000_000L, type = TransactionType.INCOME, accountId = 1L, description = "Salary", timestampMillis = now - 86_400_000L),
            TransactionEntity(id = 2L, amountPiasters = 10_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 1L, description = "Coffee Shop", timestampMillis = now - 3_600_000L),
            TransactionEntity(id = 3L, amountPiasters = 12_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 1L, description = "Coffee Shop", timestampMillis = now - 1_800_000L),
            TransactionEntity(id = 4L, amountPiasters = 25_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 2L, description = "Supermarket", timestampMillis = now - 7_200_000L)
        )
        val budgets = listOf(
            BudgetEntity(
                id = 1L,
                name = "Monthly Limit",
                categoryId = null,
                limitPiasters = 300_000L,
                yearMonth = DateRangeResolver.currentYearMonth(now)
            )
        )
        val evals = FinancialEngine.evaluateBudgets(budgets, transactions, DateRangeResolver.currentYearMonth(now), now, accounts, "EGP")
        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts, transactions, emptyList(), budgets, emptyList(), emptyList(), emptyList(),
            DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, now), now, "EGP"
        )
        val analytics = AnalyticsEngine.analyze(accounts, transactions, emptyList(), budgets, emptyList(), now, "EGP")

        val report = FinancialHealthEngine.analyzeHealth(
            accounts = accounts,
            transactions = transactions,
            budgets = budgets,
            budgetEvaluations = evals,
            savingsGoals = emptyList(),
            installments = emptyList(),
            recurringRules = emptyList(),
            debts = emptyList(),
            monthMetrics = metrics,
            analyticsSummary = analytics,
            nowMillis = now,
            targetCurrency = "EGP"
        )

        assertTrue("Score should be strong for high savings and safe budget", report.score >= 80)
        assertEquals(HealthGrade.EXCELLENT, report.grade)
        assertEquals(4, report.pillars.size)
        assertTrue(report.safeToSpendTodayPiasters > 0L)
        assertTrue(report.runwayDays > 0)

        // Verify quick templates group Coffee Shop first with count = 2 and latest amount 12_000
        val templates = FinancialHealthEngine.extractQuickTemplates(transactions)
        assertEquals(3, templates.size)
        assertEquals("Coffee Shop", templates.first().description)
        assertEquals(2, templates.first().usageCount)
        assertEquals(12_000L, templates.first().lastAmountPiasters)

        // Verify overdue installment penalizes obligation pillar
        val overdueInst = InstallmentEntity(
            id = 9L,
            name = "Laptop",
            creditor = "Store",
            accountId = 1L,
            totalAmountPiasters = 600_000L,
            installmentAmountPiasters = 100_000L,
            totalCount = 6,
            paidCount = 1,
            nextDueDateMillis = now - 86_400_000L,
            frequency = InstallmentFrequency.MONTHLY,
            status = InstallmentStatus.ACTIVE
        )
        val stressed = FinancialHealthEngine.analyzeHealth(
            accounts, transactions, budgets, evals, emptyList(), listOf(overdueInst), emptyList(), emptyList(),
            metrics, analytics, now, "EGP"
        )
        assertTrue(stressed.score < report.score)
    }

    @Test
    fun dashboard_healthPulseCard_expandsBreakdownOnTap() {
        setMainAppContent()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_list").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("financial_health_pulse_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("financial_health_pulse_card").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("financial_health_breakdown").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("financial_health_breakdown"))
        composeTestRule.onNodeWithTag("financial_health_breakdown").assertIsDisplayed()
    }

    @Test
    fun quickAddSheet_presetAmountsAndFrequentTemplate_saveTransaction() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking {
            app.repository.ensureSeeded()
            val accId = app.repository.accounts.first().first().id
            app.repository.addTransaction(
                TransactionEntity(
                    amountPiasters = 8_500L,
                    type = TransactionType.EXPENSE,
                    accountId = accId,
                    description = "QuickCoffee"
                )
            )
        }

        setMainAppContent()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("fab_quick_add").fetchSemanticsNodes().isNotEmpty()
        }

        val initialCount = runBlocking { app.repository.transactions.first().size }

        composeTestRule.onNodeWithTag("fab_quick_add").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithTag("quick_template_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("quick_template_0").performClick()

        // Add +100 via quick amount chip (85 + 100 = 185 EGP => 18_500 piasters)
        composeTestRule.onNodeWithTag("quick_amount_100").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val after = runBlocking { app.repository.transactions.first() }
        assertEquals(initialCount + 1, after.size)
        val saved = after.first()
        assertEquals("QuickCoffee", saved.description)
        assertEquals(18_500L, saved.amountPiasters)
    }
}
