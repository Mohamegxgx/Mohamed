package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiEngineSource
import com.example.data.ai.AiExtractedLineItem
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.GeminiJsonParser
import com.example.data.ai.LocalOfflineFinancialAi
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.AnalyticsEngine
import com.example.domain.engine.BnplAnalysisEngine
import com.example.domain.engine.BurdenLevel
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.FinancialHealthEngine
import com.example.domain.engine.WhatIfPurchaseInput
import com.example.domain.engine.WhatIfSavingsInput
import com.example.domain.engine.WhatIfSimulatorEngine
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.PlanningScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WhatIfSimulatorScreen
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.AiAdvisorUiState
import com.example.ui.viewmodel.AiReceiptExtractState
import com.example.ui.viewmodel.MainUiState
import java.math.BigDecimal
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StepRoadmapFeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun cleanupFxOverrides() {
        CurrencyConverter.resetCustomRates()
    }

    private fun sampleState(): MainUiState {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val accounts = listOf(
            AccountEntity(
                id = 1L,
                name = "CIB Main",
                type = AccountType.BANK,
                openingBalancePiasters = 3_000_000L,
                currentBalancePiasters = 3_000_000L,
                currency = "EGP"
            )
        )
        val categories = listOf(
            CategoryEntity(id = 1L, nameAr = "طعام وشراب", nameEn = "Food & Dining", type = TransactionType.EXPENSE, colorHex = "#F59E0B", iconName = "restaurant"),
            CategoryEntity(id = 2L, nameAr = "مواصلات", nameEn = "Transport", type = TransactionType.EXPENSE, colorHex = "#38BDF8", iconName = "directions_car"),
            CategoryEntity(id = 3L, nameAr = "تسوق", nameEn = "Shopping", type = TransactionType.EXPENSE, colorHex = "#A78BFA", iconName = "shopping_bag")
        )
        val prevMonthCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 4)
        }.timeInMillis
        val transactions = listOf(
            TransactionEntity(id = 1L, amountPiasters = 2_000_000L, type = TransactionType.INCOME, accountId = 1L, description = "Salary", timestampMillis = now - 86_400_000L),
            TransactionEntity(id = 2L, amountPiasters = 250_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 1L, description = "Groceries", timestampMillis = now - 3_600_000L),
            TransactionEntity(id = 3L, amountPiasters = 120_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 2L, description = "Uber", timestampMillis = prevMonthCal)
        )
        val budgets = listOf(
            BudgetEntity(
                id = 10L,
                name = "Monthly Budget",
                categoryId = null,
                limitPiasters = 500_000L,
                yearMonth = DateRangeResolver.currentYearMonth(now)
            )
        )
        val goals = listOf(
            SavingsGoalEntity(
                id = 1L,
                name = "Emergency Fund",
                targetAmountPiasters = 2_000_000L,
                savedAmountPiasters = 500_000L,
                colorHex = "#22D3EE"
            )
        )
        val bnplPrograms = listOf(
            BnplProgramEntity(
                id = 1L,
                providerCode = "VALU",
                nameAr = "فاليو",
                nameEn = "valU",
                creditLimitPiasters = 1_500_000L,
                manualUsedPiasters = 200_000L,
                defaultAnnualInterestPercent = 18.0,
                colorHex = "#38BDF8"
            )
        )
        val installments = listOf(
            InstallmentEntity(
                id = 1L,
                name = "Phone",
                creditor = "valU",
                accountId = 1L,
                totalAmountPiasters = 600_000L,
                installmentAmountPiasters = 50_000L,
                totalCount = 12,
                paidCount = 4,
                nextDueDateMillis = now + 5 * 86_400_000L,
                frequency = InstallmentFrequency.MONTHLY,
                status = InstallmentStatus.ACTIVE,
                bnplProgramId = 1L
            )
        )

        val ym = DateRangeResolver.currentYearMonth(now)
        val budgetEvals = FinancialEngine.evaluateBudgets(budgets, transactions, ym, now, accounts, "EGP")
        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts, transactions, categories, budgets, goals, installments, emptyList(),
            DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, now), now, "EGP"
        )
        val analytics = AnalyticsEngine.analyze(accounts, transactions, categories, budgets, installments, now, "EGP")
        val health = FinancialHealthEngine.analyzeHealth(
            accounts, transactions, budgets, budgetEvals, goals, installments, emptyList(), emptyList(),
            metrics, analytics, now, "EGP"
        )
        val bnpl = BnplAnalysisEngine.analyze(
            programs = bnplPrograms,
            installments = installments,
            recurringRules = emptyList(),
            transactions = transactions,
            accounts = accounts,
            monthMetrics = metrics,
            nowMillis = now
        )

        return MainUiState(
            isLoading = false,
            preferences = UserPreferences(language = AppLanguage.EN, currencyCode = "EGP"),
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            filteredTransactions = transactions,
            budgets = budgets,
            budgetEvaluations = budgetEvals,
            savingsGoals = goals,
            installments = installments,
            bnplPrograms = bnplPrograms,
            dashboardMetrics = metrics,
            analyticsSummary = analytics,
            healthReport = health,
            bnplReport = bnpl
        )
    }

    @Test
    fun whatIfSimulator_evaluatesSafePurchaseLimitExceededAndSavingsAcceleration() {
        val s = sampleState()

        val safePurchase = WhatIfSimulatorEngine.simulatePurchase(
            input = WhatIfPurchaseInput(
                cashPricePiasters = 400_000L,
                months = 12,
                annualInterestRatePercent = 12.0,
                bnplProgramId = 1L
            ),
            bnplReport = s.bnplReport,
            metrics = s.dashboardMetrics,
            healthReport = s.healthReport,
            currencyCode = "EGP"
        )
        assertTrue(safePurchase.feasible)
        assertFalse(safePurchase.exceedsBnplLimit)
        assertTrue(safePurchase.monthlyInstallmentPiasters > 0L)

        // Attempt purchase exceeding valU remaining credit limit
        val overLimitPurchase = WhatIfSimulatorEngine.simulatePurchase(
            input = WhatIfPurchaseInput(
                cashPricePiasters = 2_000_000L,
                months = 12,
                annualInterestRatePercent = 20.0,
                bnplProgramId = 1L
            ),
            bnplReport = s.bnplReport,
            metrics = s.dashboardMetrics,
            healthReport = s.healthReport,
            currencyCode = "EGP"
        )
        assertTrue(overLimitPurchase.exceedsBnplLimit)
        assertFalse(overLimitPurchase.feasible)
        assertEquals(BurdenLevel.CRITICAL, overLimitPurchase.burdenAfter)

        // Savings boost accelerates goal timeline
        val boost = WhatIfSimulatorEngine.simulateSavingsBoost(
            input = WhatIfSavingsInput(monthlyBoostPiasters = 300_000L, targetGoalId = 1L),
            metrics = s.dashboardMetrics,
            bnplReport = s.bnplReport,
            goals = s.savingsGoals,
            currencyCode = "EGP"
        )
        assertEquals(3_600_000L, boost.annualExtraSavedPiasters)
        assertEquals("Emergency Fund", boost.targetGoalName)
        assertNotNull(boost.monthsToGoalAfter)
    }

    @Test
    fun multiItemReceiptParsing_handlesJsonAndOfflineCompoundText() {
        val json = """
            {
              "amountMajor": 220.0,
              "type": "EXPENSE",
              "description": "Carrefour Market",
              "categoryHint": "Food & Dining",
              "notes": "Weekly groceries",
              "items": [
                {"description": "Dairy & Bread", "amountMajor": 120.0, "categoryHint": "Food & Dining"},
                {"description": "Dish Soap", "amountMajor": 100.0, "categoryHint": "Shopping"}
              ]
            }
        """.trimIndent()
        val parsedJson = GeminiJsonParser.parseExtractedTransaction(json)
        assertEquals(22_000L, parsedJson.amountPiasters)
        assertEquals(2, parsedJson.items.size)
        assertEquals(12_000L, parsedJson.items[0].amountPiasters)

        val s = sampleState()
        val offlineExtracted = LocalOfflineFinancialAi.analyzeOfflineReceiptText(
            textInput = "50 اكل و 25 مواصلات و 100 تسوق",
            accounts = s.accounts,
            categories = s.categories,
            merchantRules = emptyList(),
            lang = AppLanguage.AR
        )
        assertEquals(17_500L, offlineExtracted.amountPiasters)
        assertEquals(3, offlineExtracted.items.size)
    }

    @Test
    fun budgetBurnRateAndWeeklyComparisonAndFxOverride_computeAccurately() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 10)
        }
        val now = cal.timeInMillis
        val accounts = listOf(
            AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, openingBalancePiasters = 500_000L, currentBalancePiasters = 500_000L, currency = "EGP")
        )
        val categories = listOf(
            CategoryEntity(id = 1L, nameAr = "طعام", nameEn = "Food", type = TransactionType.EXPENSE, colorHex = "#F59E0B", iconName = "restaurant")
        )
        // Spent 60% of budget by day 10 => projected to spend 180% -> burnRateWarning triggers before 80%!
        val prevMonthSameDay = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
        }.timeInMillis
        val transactions = listOf(
            TransactionEntity(id = 1L, amountPiasters = 60_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 1L, description = "Dining", timestampMillis = now - 3_600_000L),
            TransactionEntity(id = 2L, amountPiasters = 40_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 1L, description = "Prev Dining", timestampMillis = prevMonthSameDay)
        )
        val budgets = listOf(
            BudgetEntity(id = 1L, name = "Food Cap", categoryId = 1L, limitPiasters = 100_000L, yearMonth = DateRangeResolver.currentYearMonth(now))
        )
        val evals = FinancialEngine.evaluateBudgets(budgets, transactions, DateRangeResolver.currentYearMonth(now), now, accounts, "EGP")
        val eval = evals.first()
        assertFalse(eval.isWarning80)
        assertTrue("Early burnRateWarning should trigger before 80%", eval.burnRateWarning)
        assertNotNull(eval.estimatedDepletionDay)
        assertTrue(eval.recommendedDailyCapPiasters > 0L)

        val summary = AnalyticsEngine.analyze(accounts, transactions, categories, budgets, emptyList(), now, "EGP")
        assertEquals(4, summary.weeklyComparison.size)
        // Day 10 falls into week 2 (index 1: days 8..14)
        assertEquals(60_000L, summary.weeklyComparison[1].currentMonthSpendPiasters)
        assertEquals(40_000L, summary.weeklyComparison[1].previousMonthSpendPiasters)
        assertTrue(summary.insights.any { it.id == "budget_burn_1" })

        // FX rate override round-trip
        CurrencyConverter.applyCustomRates(mapOf("USD" to BigDecimal("52.50")))
        assertEquals(52_500L, CurrencyConverter.convertPiasters(1_000L, "USD", "EGP"))
        val serialized = CurrencyConverter.serializeRates(CurrencyConverter.currentCustomRates())
        CurrencyConverter.resetCustomRates()
        CurrencyConverter.applyCustomRatesFromRaw(serialized)
        assertEquals(52_500L, CurrencyConverter.convertPiasters(1_000L, "USD", "EGP"))
    }

    @Test
    fun whatIfSimulatorScreen_rendersAndSwitchesBetweenPurchaseAndSavingsModes() {
        val state = sampleState()
        composeTestRule.setContent {
            ElbagorryMoneyTheme(language = AppLanguage.EN) {
                WhatIfSimulatorScreen(state = state, onBack = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("whatif_purchase_result").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("whatif_lazy_list").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("whatif_mode_savings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("whatif_lazy_list").performScrollToNode(hasTestTag("whatif_savings_result"))
        composeTestRule.onNodeWithTag("whatif_savings_result").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aiAdvisorScreen_rendersVoiceMicTtsToggleAndMultiItemReceiptSplit() {
        val state = sampleState()
        val msg = AiChatMessage(
            id = "m1",
            text = "Your budget is on track.",
            isUser = false,
            engineSource = AiEngineSource.LOCAL_OFFLINE
        )
        val extracted = AiExtractedTransaction(
            amountPiasters = 20_000L,
            type = TransactionType.EXPENSE,
            description = "Supermarket",
            categoryHint = "Food",
            notes = "",
            items = listOf(
                AiExtractedLineItem("Milk & Bread", 8_000L, "Food & Dining"),
                AiExtractedLineItem("Cleaning Supplies", 12_000L, "Shopping")
            )
        )
        val aiState = AiAdvisorUiState(
            messages = listOf(msg),
            receiptExtractState = AiReceiptExtractState.Success(extracted)
        )
        composeTestRule.setContent {
            ElbagorryMoneyTheme(language = AppLanguage.EN) {
                AiAdvisorScreen(
                    mainState = state,
                    aiState = aiState,
                    onSendPrompt = {},
                    onGeneratePlan = {},
                    onAnalyzeReceipt = { _, _ -> },
                    onSaveExtractedTx = {},
                    onClearChat = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_tts_auto_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_voice_mic_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_speak_msg_m1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("ai_tab_2").performClick()
        composeTestRule.waitForIdle()
        val lazyScrollMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
        composeTestRule.onAllNodes(lazyScrollMatcher).onFirst().performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("receipt_split_plus").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("receipt_items_breakdown").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_save_extracted_items_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsFxDialog_insightsWeeklyChart_andPlanningBurnTelemetry_renderCorrectly() {
        var savedRaw = ""
        val state = sampleState()
        composeTestRule.setContent {
            ElbagorryMoneyTheme(language = AppLanguage.EN) {
                SettingsScreen(
                    state = state,
                    onSetThemeMode = {},
                    onSetLanguage = {},
                    onSetBiometricLock = {},
                    onToggleNotificationSetting = { _, _ -> },
                    onBack = {},
                    onSaveCustomFxRates = { savedRaw = it },
                    onResetCustomFxRates = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .onFirst()
            .performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_fx_rates_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fx_rate_input_usd").performScrollTo().performTextReplacement("55.25")
        composeTestRule.onNodeWithTag("fx_save_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedRaw.contains("USD=55.25"))
    }

    @Test
    fun insightsAndPlanningScreens_displayWeeklyComparisonAndBurnTelemetry() {
        val state = sampleState()
        var whatIfOpened = false
        composeTestRule.setContent {
            ElbagorryMoneyTheme(language = AppLanguage.EN) {
                InsightsScreen(
                    state = state,
                    onNavigateCalendar = {},
                    onNavigateReports = {},
                    onNavigateNotifications = {},
                    onNavigateSettings = {},
                    onNavigateWhatIfSimulator = { whatIfOpened = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_what_if_sim").performScrollTo().performClick()
        assertTrue(whatIfOpened)
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasTestTag("insights_weekly_comparison_card"))
        composeTestRule.onNodeWithTag("insights_weekly_comparison_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun planningScreen_displaysBudgetBurnRateTelemetry() {
        val state = sampleState()
        composeTestRule.setContent {
            ElbagorryMoneyTheme(language = AppLanguage.EN) {
                PlanningScreen(
                    state = state,
                    onCreateBudget = { _, _, _ -> },
                    onDeleteBudget = {},
                    onCopyPreviousBudgets = {},
                    onCreateInstallment = { _, _, _, _, _, _, _, _, _ -> },
                    onPayInstallment = { _, _ -> },
                    onSetInstallmentStatus = { _, _ -> },
                    onCreateRecurring = { _, _, _, _, _, _, _, _, _ -> },
                    onToggleRecurring = {},
                    onDeleteRecurring = {},
                    onCreateDebt = { _, _, _, _, _ -> },
                    onSettleDebt = { _, _, _ -> },
                    onDeleteDebt = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("budget_burn_telemetry_10").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pace/day:", substring = true).assertIsDisplayed()
    }
}
