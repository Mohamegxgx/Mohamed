package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupAndExportManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.MessageChannel
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import com.example.reader.IncomingRawMessage
import com.example.reader.SmartReaderProcessor
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.widget.computeWidgetSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4FeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setMainAppContent() {
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
    }

    @Test
    fun `currencyConverter converts across EGP USD SAR EUR accurately`() {
        // 100 USD (10_000 piasters) * 49.00 = 490_000 piasters EGP (4,900 EGP)
        val egpFromUsd = CurrencyConverter.convertPiasters(10_000L, "USD", "EGP")
        assertEquals(490_000L, egpFromUsd)

        // Same currency is identity
        assertEquals(123_456L, CurrencyConverter.convertPiasters(123_456L, "SAR", "SAR"))

        // Round-trip EGP -> USD
        val usdFromEgp = CurrencyConverter.convertPiasters(490_000L, "EGP", "USD")
        assertEquals(10_000L, usdFromEgp)
    }

    @Test
    fun `financialEngine and widget snapshot convert multi-currency accounts into display currency`() {
        val now = 1_760_000_000_000L
        val accounts = listOf(
            AccountEntity(
                id = 1L,
                name = "EGP Cash",
                type = AccountType.CASH,
                openingBalancePiasters = 100_000L,
                currentBalancePiasters = 100_000L,
                currency = "EGP"
            ),
            AccountEntity(
                id = 2L,
                name = "USD Bank",
                type = AccountType.BANK,
                openingBalancePiasters = 10_000L, // $100 -> 4,900 EGP
                currentBalancePiasters = 10_000L,
                currency = "USD"
            )
        )
        val transactions = listOf(
            TransactionEntity(
                id = 1L,
                amountPiasters = 5_000L, // $50 -> 2,450 EGP
                type = TransactionType.EXPENSE,
                accountId = 2L,
                description = "Cloud Hosting",
                timestampMillis = now
            )
        )
        val metricsEgp = FinancialEngine.calculateDashboardMetrics(
            accounts = accounts,
            transactions = transactions,
            categories = emptyList(),
            budgets = emptyList(),
            savingsGoals = emptyList(),
            installments = emptyList(),
            debts = emptyList(),
            range = DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, now),
            nowMillis = now,
            targetCurrency = "EGP"
        )
        assertEquals(590_000L, metricsEgp.netBalancePiasters)
        assertEquals(245_000L, metricsEgp.totalExpensesPiasters)

        val widgetUsd = computeWidgetSnapshot(
            accounts = accounts,
            transactions = transactions,
            pending = emptyList(),
            prefs = UserPreferences(currencyCode = "USD"),
            nowMillis = now
        )
        // 1000 EGP -> 20.41 USD (2041 cents) + 100 USD (10000 cents) = 12041 cents
        assertEquals(12_041L, widgetUsd.netBalancePiasters)
        assertEquals(5_000L, widgetUsd.todayExpensePiasters)
        assertEquals("USD", widgetUsd.currency)
    }

    @Test
    fun `voiceTransactionParser extracts amount type account category and respects merchant rules`() {
        val accounts = listOf(
            AccountEntity(id = 1L, name = "نقدي (كاش)", type = AccountType.CASH, openingBalancePiasters = 0L, currentBalancePiasters = 0L),
            AccountEntity(id = 2L, name = "حساب بنكي", type = AccountType.BANK, openingBalancePiasters = 0L, currentBalancePiasters = 0L)
        )
        val categories = listOf(
            CategoryEntity(id = 10L, nameAr = "طعام ومشروبات", nameEn = "Food & Drinks", type = TransactionType.EXPENSE, isDefault = true),
            CategoryEntity(id = 11L, nameAr = "مواصلات", nameEn = "Transport", type = TransactionType.EXPENSE, isDefault = true),
            CategoryEntity(id = 12L, nameAr = "راتب", nameEn = "Salary", type = TransactionType.INCOME, isDefault = true)
        )
        val rules = listOf(
            MerchantRuleEntity(
                id = 1L,
                keyword = "Starbucks",
                normalizedKeyword = "starbucks",
                categoryId = 10L,
                accountId = 2L,
                preferredType = TransactionType.EXPENSE
            )
        )

        val arabicExpense = VoiceTransactionParser.parse(
            rawPhrase = "صرفت ٢٥٠ جنيه أوبر من الكاش",
            accounts = accounts,
            categories = categories,
            merchantRules = rules
        )
        assertEquals(TransactionType.EXPENSE, arabicExpense.type)
        assertEquals(25_000L, arabicExpense.amountPiasters)
        assertEquals(1L, arabicExpense.suggestedAccountId)
        assertEquals(11L, arabicExpense.suggestedCategoryId)

        val learnedRuleMatch = VoiceTransactionParser.parse(
            rawPhrase = "Spent 120.50 at Starbucks",
            accounts = accounts,
            categories = categories,
            merchantRules = rules
        )
        assertEquals(TransactionType.EXPENSE, learnedRuleMatch.type)
        assertEquals(12_050L, learnedRuleMatch.amountPiasters)
        assertEquals(2L, learnedRuleMatch.suggestedAccountId)
        assertEquals(10L, learnedRuleMatch.suggestedCategoryId)

        val incomeParsed = VoiceTransactionParser.parse(
            rawPhrase = "استلمت 8500 جنيه راتب على البنك",
            accounts = accounts,
            categories = categories,
            merchantRules = rules
        )
        assertEquals(TransactionType.INCOME, incomeParsed.type)
        assertEquals(850_000L, incomeParsed.amountPiasters)
        assertEquals(2L, incomeParsed.suggestedAccountId)
        assertEquals(12L, incomeParsed.suggestedCategoryId)
    }

    @Test
    fun `smartReader learns merchant rule on approval and reuses it plus backup v3 roundtrip`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            AppDatabase.seedDefaults(db)
            val repo = MoneyRepository(db)
            val prefsRepo = (context.applicationContext as ElbagorryMoneyApp).preferencesRepository
            val processor = SmartReaderProcessor(db, prefsRepo)
            val backup = BackupAndExportManager(db)

            val transportCat = db.categoryDao().getAllOnce().first { it.nameEn == "Transport" }
            val bankAcc = db.accountDao().getAllOnce().first { it.type == AccountType.BANK }

            // 1. Ingest a purchase SMS at Carrefour / UberX
            val firstRes = processor.ingestMessages(
                listOf(IncomingRawMessage("CIB", "تم خصم 150.00 EGP لدى Careem Ride ببطاقة البنك")),
                MessageChannel.CLIPBOARD_PASTE
            )
            assertEquals(1, firstRes.pendingCreated)

            val pending1 = db.pendingTransactionDao().getAllOnce().first()
            // User picks custom category (Transport) & Bank account -> system learns rule for counterparty
            val approveRes = repo.approvePendingTransaction(
                pendingId = pending1.id,
                accountId = bankAcc.id,
                categoryId = transportCat.id,
                overrideDescription = "Careem Ride"
            )
            assertTrue(approveRes.isSuccess)

            val rules = repo.getAllMerchantRulesOnce()
            assertTrue(rules.any { it.normalizedKeyword.contains("careem") && it.categoryId == transportCat.id })

            // 2. Second message from Careem Ride automatically applies learned rule
            val secondRes = processor.ingestMessages(
                listOf(IncomingRawMessage("CIB", "تم خصم 95.00 EGP لدى Careem Ride المرجع 99182")),
                MessageChannel.CLIPBOARD_PASTE
            )
            assertEquals(1, secondRes.pendingCreated)
            val pending2 = db.pendingTransactionDao().getAllOnce().first { it.id != pending1.id }
            assertEquals(transportCat.id, pending2.suggestedCategoryId)
            assertEquals(bankAcc.id, pending2.suggestedAccountId)

            // 3. Backup v3 preserves merchant rules
            val json = backup.createJsonBackup()
            assertTrue(backup.validateBackupJson(json).isSuccess)
            db.merchantRuleDao().deleteAll()
            assertEquals(0, repo.getAllMerchantRulesOnce().size)

            val restoreRes = backup.restoreFromJson(json)
            assertTrue(restoreRes.isSuccess)
            assertTrue(repo.getAllMerchantRulesOnce().isNotEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `room migration 2 to 3 creates merchant_rules table safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v2_v3.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, openingBalancePiasters INTEGER NOT NULL, currentBalancePiasters INTEGER NOT NULL, currency TEXT NOT NULL, colorHex TEXT NOT NULL, iconName TEXT NOT NULL, isArchived INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(sqliteDb)
        sqliteDb.execSQL(
            "INSERT INTO merchant_rules (keyword, normalizedKeyword, categoryId, accountId, preferredType, hitCount, createdAt) VALUES ('Uber', 'uber', 1, 1, 'EXPENSE', 2, 1000)"
        )
        val cursor = sqliteDb.query("SELECT keyword, hitCount FROM merchant_rules WHERE normalizedKeyword = 'uber'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Uber", cursor.getString(0))
        assertEquals(2, cursor.getInt(1))
        cursor.close()
        sqliteDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `settings toggles privacy screen guard and switches primary currency`() {
        setMainAppContent()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("nav_settings_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_settings_btn").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("settings_lazy_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("currency_selector_row"))
        composeTestRule.onNodeWithTag("currency_selector_row").performScrollToNode(hasTestTag("primary_currency_usd"))
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("primary_currency_usd").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("primary_currency_usd").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodes(hasTestTag("primary_currency_usd") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("primary_currency_usd").assertIsSelected()

        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("privacy_screen_guard_switch"))
        composeTestRule.onNodeWithTag("privacy_screen_guard_switch").assertIsDisplayed()
    }

    @Test
    fun `quick add smart phrase parses phrase and saves transaction`() {
        setMainAppContent()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("fab_quick_add").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("fab_quick_add").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("toggle_smart_phrase_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("toggle_smart_phrase_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("smart_phrase_input").performScrollTo().performTextInput("صرفت 145 جنيه قهوة")
        composeTestRule.onNodeWithTag("apply_smart_phrase_btn").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("save_transaction_button").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `insights navigates to receipts vault screen`() {
        setMainAppContent()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("nav_tab_insights").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_insights").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("tool_receipts_vault").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tool_receipts_vault").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("receipts_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("receipts_empty_state").assertIsDisplayed()
    }
}
