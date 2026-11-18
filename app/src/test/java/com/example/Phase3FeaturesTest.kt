package com.example

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag as glanceHasTestTag
import androidx.glance.testing.unit.hasText as glanceHasText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.PendingStatus
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.ThemePreset
import com.example.domain.model.TransactionType
import com.example.notifications.FinancialSummaryBuilder
import com.example.notifications.FinancialSummarySnapshot
import com.example.notifications.NotificationActionReceiver
import com.example.notifications.NotificationHelper
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.widget.ElbagorryWidgetContent
import com.example.widget.WidgetSnapshot
import com.example.widget.computeWidgetSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3FeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `computeWidgetSnapshot computes net balance today spend month income and pending count`() {
        val now = 1_760_000_000_000L
        val accounts = listOf(
            AccountEntity(id = 1L, name = "Cash", type = AccountType.CASH, openingBalancePiasters = 100_000L, currentBalancePiasters = 100_000L, isArchived = false),
            AccountEntity(id = 2L, name = "Bank", type = AccountType.BANK, openingBalancePiasters = 250_000L, currentBalancePiasters = 250_000L, isArchived = false),
            AccountEntity(id = 3L, name = "Old", type = AccountType.CUSTOM, openingBalancePiasters = 99_900L, currentBalancePiasters = 99_900L, isArchived = true)
        )
        val transactions = listOf(
            TransactionEntity(id = 1L, amountPiasters = 15_000L, type = TransactionType.EXPENSE, accountId = 1L, description = "Lunch", timestampMillis = now),
            TransactionEntity(id = 2L, amountPiasters = 500_000L, type = TransactionType.INCOME, accountId = 2L, description = "Salary", timestampMillis = now),
            TransactionEntity(id = 3L, amountPiasters = 5_000L, type = TransactionType.EXPENSE, accountId = 1L, description = "Old Coffee", timestampMillis = now - 40L * 24L * 60L * 60L * 1000L)
        )
        val pending = listOf(
            PendingTransactionEntity(
                id = 1L,
                importedMessageId = 1L,
                sourceName = "VodafoneCash",
                channel = MessageChannel.CLIPBOARD_PASTE,
                operationKind = MessageOperationKind.CARD_PURCHASE,
                mappedType = TransactionType.EXPENSE,
                amountPiasters = 10_000L,
                transactionTimestampMillis = now,
                confidenceScore = 0.9f,
                dedupFingerprint = "fp1",
                extractedSummary = "100 EGP",
                status = PendingStatus.PENDING
            ),
            PendingTransactionEntity(
                id = 2L,
                importedMessageId = 2L,
                sourceName = "InstaPay",
                channel = MessageChannel.CLIPBOARD_PASTE,
                operationKind = MessageOperationKind.CARD_PURCHASE,
                mappedType = TransactionType.EXPENSE,
                amountPiasters = 20_000L,
                transactionTimestampMillis = now,
                confidenceScore = 0.9f,
                dedupFingerprint = "fp2",
                extractedSummary = "200 EGP",
                status = PendingStatus.APPROVED
            )
        )
        val prefs = UserPreferences(
            currencyCode = "EGP",
            hideAmounts = false,
            language = AppLanguage.AR,
            themePreset = ThemePreset.ROYAL_SAPPHIRE
        )

        val snapshot = computeWidgetSnapshot(accounts, transactions, pending, prefs, nowMillis = now)

        assertEquals(350_000L, snapshot.netBalancePiasters)
        assertEquals(15_000L, snapshot.todayExpensePiasters)
        assertEquals(500_000L, snapshot.monthIncomePiasters)
        assertEquals(15_000L, snapshot.monthExpensePiasters)
        assertEquals(1, snapshot.pendingCount)
        assertEquals(ThemePreset.ROYAL_SAPPHIRE, snapshot.themePreset)
        assertTrue(snapshot.hasData)
    }

    @Test
    fun `financialSummaryBuilder evaluates daily vs weekly schedule and masks text when hidden`() {
        val now = 1_760_000_000_000L
        assertTrue(FinancialSummaryBuilder.isSummaryDue(0L, SummaryFrequency.DAILY, now))
        assertFalse(FinancialSummaryBuilder.isSummaryDue(now - 2L * 60L * 60L * 1000L, SummaryFrequency.DAILY, now))
        assertTrue(FinancialSummaryBuilder.isSummaryDue(now - 21L * 60L * 60L * 1000L, SummaryFrequency.DAILY, now))
        assertFalse(FinancialSummaryBuilder.isSummaryDue(now - 3L * 24L * 60L * 60L * 1000L, SummaryFrequency.WEEKLY, now))
        assertTrue(FinancialSummaryBuilder.isSummaryDue(now - 7L * 24L * 60L * 60L * 1000L, SummaryFrequency.WEEKLY, now))

        val accounts = listOf(
            AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, openingBalancePiasters = 400_000L, currentBalancePiasters = 400_000L)
        )
        val transactions = listOf(
            TransactionEntity(id = 1L, amountPiasters = 25_000L, type = TransactionType.EXPENSE, accountId = 1L, description = "Groceries", timestampMillis = now)
        )
        val installments = listOf(
            InstallmentEntity(
                id = 1L,
                name = "Phone",
                creditor = "Store",
                accountId = 1L,
                totalAmountPiasters = 100_000L,
                installmentAmountPiasters = 10_000L,
                totalCount = 10,
                nextDueDateMillis = now + 24L * 60L * 60L * 1000L,
                frequency = InstallmentFrequency.MONTHLY
            )
        )
        val summary = FinancialSummaryBuilder.build(
            accounts = accounts,
            transactions = transactions,
            budgets = emptyList<BudgetEntity>(),
            installments = installments,
            pending = emptyList(),
            nowMillis = now
        )
        assertEquals(400_000L, summary.netBalancePiasters)
        assertEquals(25_000L, summary.todaySpendPiasters)
        assertEquals(1, summary.dueInstallmentsCount)

        val visibleText = FinancialSummaryBuilder.formatMessageText(summary, isWeekly = false, currency = "EGP", hideAmounts = false, lang = AppLanguage.EN)
        val hiddenText = FinancialSummaryBuilder.formatMessageText(summary, isWeekly = false, currency = "EGP", hideAmounts = true, lang = AppLanguage.EN)
        assertTrue(visibleText.contains("4,000") || visibleText.contains("4000"))
        assertTrue(hiddenText.contains("••••"))
        assertFalse(hiddenText.contains("4,000"))
    }

    @Test
    fun `glance widget renders net balance today spend month income and quick add`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        setAppWidgetSize(DpSize(310.dp, 180.dp))

        val visibleSnapshot = WidgetSnapshot(
            netBalancePiasters = 125_000L,
            todayExpensePiasters = 25_000L,
            monthIncomePiasters = 300_000L,
            pendingCount = 2,
            currency = "EGP",
            hideAmounts = false,
            language = AppLanguage.EN,
            themePreset = ThemePreset.EMERALD_OBSIDIAN,
            hasData = true
        )

        provideComposable {
            ElbagorryWidgetContent(snapshot = visibleSnapshot, isWide = true, isTall = true)
        }

        onNode(glanceHasTestTag("widget_net_balance")).assertExists()
        onNode(glanceHasTestTag("widget_today_expense")).assertExists()
        onNode(glanceHasTestTag("widget_month_income")).assertExists()
        onNode(glanceHasTestTag("widget_pending_badge")).assertExists()
        onNode(glanceHasTestTag("widget_privacy_toggle")).assertExists()
        onNode(glanceHasTestTag("widget_quick_add_btn")).assertExists()
        onNode(glanceHasText("+ Quick Add")).assertExists()
    }

    @Test
    fun `glance widget masks financial values when privacy mode is enabled`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 110.dp))

        val maskedSnapshot = WidgetSnapshot(
            netBalancePiasters = 125_000L,
            todayExpensePiasters = 25_000L,
            monthIncomePiasters = 300_000L,
            pendingCount = 2,
            currency = "EGP",
            hideAmounts = true,
            language = AppLanguage.EN,
            themePreset = ThemePreset.EMERALD_OBSIDIAN,
            hasData = true
        )

        provideComposable {
            ElbagorryWidgetContent(snapshot = maskedSnapshot, isWide = false, isTall = false)
        }

        onNode(glanceHasTestTag("widget_net_balance") and glanceHasText("••••")).assertExists()
        onNode(glanceHasTestTag("widget_today_expense") and glanceHasText("••••")).assertExists()
        onNode(glanceHasText("Show")).assertExists()
    }

    @Test
    fun `notification deep link intent and pay installment action receiver update state`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val quickAddPi = NotificationHelper.createDeepLinkPendingIntent(context, 101, NotificationHelper.DEST_QUICK_ADD)
        val savedIntent = Shadows.shadowOf(quickAddPi).savedIntent
        assertEquals(NotificationHelper.DEST_QUICK_ADD, savedIntent.getStringExtra(NotificationHelper.EXTRA_NAV_DESTINATION))

        val db = AppDatabase.getInstance(context)
        val repo = MoneyRepository(db)
        repo.ensureSeeded()
        val bank = repo.getAllAccountsOnce().first()
        val beforeBalance = bank.currentBalancePiasters
        val instId = repo.saveInstallment(
            InstallmentEntity(
                name = "Fiber Plan",
                creditor = "ISP",
                accountId = bank.id,
                totalAmountPiasters = 40_000L,
                installmentAmountPiasters = 10_000L,
                totalCount = 4,
                nextDueDateMillis = System.currentTimeMillis(),
                frequency = InstallmentFrequency.MONTHLY
            )
        )

        val receiver = NotificationActionReceiver()
        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_INSTALLMENT
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 505)
            putExtra(NotificationActionReceiver.EXTRA_INSTALLMENT_ID, instId)
            putExtra(NotificationActionReceiver.EXTRA_ACCOUNT_ID, bank.id)
        }
        receiver.onReceive(context, payIntent)

        // Allow goAsync IO coroutine to complete including widget updater
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline) {
            val updated = repo.getAllInstallmentsOnce().first { it.id == instId }
            if (updated.paidCount == 1) {
                Thread.sleep(100)
                break
            }
            Thread.sleep(50)
        }

        val updatedInstallment = repo.getAllInstallmentsOnce().first { it.id == instId }
        val updatedAccount = repo.getAllAccountsOnce().first { it.id == bank.id }
        assertEquals(1, updatedInstallment.paidCount)
        assertEquals(beforeBalance - 10_000L, updatedAccount.currentBalancePiasters)
    }

    @Test
    fun `settings screen switches theme preset persists preference and configures summary frequency`() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
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
            composeTestRule.onAllNodesWithTag("nav_settings_btn").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("nav_settings_btn").assertExists().performClick()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("sync_to_cloud_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("sync_to_cloud_btn"))
        composeTestRule.onNodeWithTag("sync_to_cloud_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("google_sign_out_btn"))
        composeTestRule.onNodeWithTag("google_sign_out_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("theme_preset_royal_sapphire"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_preset_royal_sapphire").assertExists().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodes(hasTestTag("theme_preset_royal_sapphire") and isSelected())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("theme_preset_royal_sapphire").assertIsSelected()

        val savedPreset = runBlocking { app.preferencesRepository.preferences.first().themePreset }
        assertEquals(ThemePreset.ROYAL_SAPPHIRE, savedPreset)

        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("summary_freq_weekly"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("summary_freq_weekly").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodes(hasTestTag("summary_freq_weekly") and isSelected())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("summary_freq_weekly").assertIsSelected()

        val savedFreq = runBlocking { app.preferencesRepository.preferences.first().summaryFrequency }
        assertEquals(SummaryFrequency.WEEKLY, savedFreq)

        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("send_summary_now_btn"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("send_summary_now_btn").assertExists().performClick()
        composeTestRule.waitForIdle()
    }
}
