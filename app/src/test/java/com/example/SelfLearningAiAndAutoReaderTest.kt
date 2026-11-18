package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiChatIntentMode
import com.example.data.ai.LocalOfflineFinancialAi
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.PendingStatus
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.MessageDirection
import com.example.domain.reader.MessageDirectionClassifier
import com.example.domain.reader.ParseOutcome
import com.example.reader.IncomingRawMessage
import com.example.reader.SmartReaderProcessor
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.PendingTransactionCard
import com.example.ui.screens.SmartMoneyReaderScreen
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.AiAdvisorUiState
import com.example.ui.viewmodel.MainUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SelfLearningAiAndAutoReaderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repo: MoneyRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { AppDatabase.seedDefaults(db) }
        repo = MoneyRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `direction classifier accurately distinguishes tricky arabic incoming vs outgoing phrases and balance deltas`() {
        val parser = FinancialMessageParser()

        // Tricky incoming message containing "تم تحويل" BUT destination is "إلى حسابك"
        val incomingTricky = "إنستاباي: تم تحويل مبلغ 1500 جنيه من 01012345678 إلى حسابك رقم المرجع IPN9921"
        val resIn = parser.parse(incomingTricky, sender = "InstaPay")
        assertTrue(resIn is ParseOutcome.Financial)
        val parsedIn = (resIn as ParseOutcome.Financial).parsed
        assertEquals(MessageDirection.INCOMING, parsedIn.direction)
        assertEquals(TransactionType.INCOME, parsedIn.mappedType)

        // Tricky outgoing message containing "تم تحويل" and external recipient "إلى رقم"
        val outgoingTricky = "إنستاباي: تم تحويل مبلغ 750 جنيه إلى رقم 01099887766 رقم المرجع IPN9922"
        val resOut = parser.parse(outgoingTricky, sender = "InstaPay")
        assertTrue(resOut is ParseOutcome.Financial)
        val parsedOut = (resOut as ParseOutcome.Financial).parsed
        assertEquals(MessageDirection.OUTGOING, parsedOut.direction)
        assertEquals(TransactionType.EXPENSE, parsedOut.mappedType)

        // Balance delta disambiguation when text is ambiguous
        val ambiguousIn = MessageDirectionClassifier.classify(
            rawBody = "إنستاباي: حركة بمبلغ 500 ج.م الرصيد المتاح 5500 ج.م",
            balanceAfterPiasters = 550_000L,
            knownAccountBalancePiasters = 500_000L,
            hasTransactionAmount = true
        )
        assertEquals(MessageDirection.INCOMING, ambiguousIn.direction)

        val ambiguousOut = MessageDirectionClassifier.classify(
            rawBody = "إنستاباي: حركة بمبلغ 500 ج.م الرصيد المتاح 4500 ج.م",
            balanceAfterPiasters = 450_000L,
            knownAccountBalancePiasters = 500_000L,
            hasTransactionAmount = true
        )
        assertEquals(MessageDirection.OUTGOING, ambiguousOut.direction)
    }

    @Test
    fun `financial notification prefilter catches bank sms alerts and ignores chat and otp`() {
        assertTrue(FinancialMessageParser.looksLikeFinancialNotification("NBE", "تم خصم 320 جنيه من بطاقتك لدى كارفور"))
        assertTrue(FinancialMessageParser.looksLikeFinancialNotification("VodafoneCash", "تم استلام 1200 EGP من 01022334455"))
        assertFalse(FinancialMessageParser.looksLikeFinancialNotification("Ahmed", "ازيك يا صاحبي نتقابل الساعة 8 النهارده؟"))
        assertFalse(FinancialMessageParser.looksLikeFinancialNotification("NBE", "رمز التحقق OTP هو 884421 لا تشاركه"))
    }

    @Test
    fun `self learning engine predicts categories via naive bayes from room history, fuzzy dice, franco-arab and teaching commands`() {
        val accounts = listOf(
            AccountEntity(id = 1L, name = "بنك CIB", type = AccountType.BANK, openingBalancePiasters = 1_000_000L, currentBalancePiasters = 1_000_000L),
            AccountEntity(id = 2L, name = "فودافون كاش", type = AccountType.E_WALLET, openingBalancePiasters = 200_000L, currentBalancePiasters = 200_000L)
        )
        val foodCat = CategoryEntity(id = 10L, nameAr = "طعام وشراب", nameEn = "Food & Drinks", type = TransactionType.EXPENSE)
        val transportCat = CategoryEntity(id = 11L, nameAr = "مواصلات", nameEn = "Transport", type = TransactionType.EXPENSE)
        val salaryCat = CategoryEntity(id = 12L, nameAr = "راتب", nameEn = "Salary", type = TransactionType.INCOME)
        val categories = listOf(foodCat, transportCat, salaryCat)

        // Zero explicit MerchantRuleEntity: train purely on Room historical transactions!
        val history = listOf(
            TransactionEntity(id = 1L, amountPiasters = 12_000L, type = TransactionType.EXPENSE, accountId = 2L, categoryId = foodCat.id, description = "أبو مازن السوري غداء"),
            TransactionEntity(id = 2L, amountPiasters = 15_000L, type = TransactionType.EXPENSE, accountId = 2L, categoryId = foodCat.id, description = "كشري التحرير وسط البلد"),
            TransactionEntity(id = 3L, amountPiasters = 6_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = transportCat.id, description = "اشتراك ديدي اليومي")
        )

        val nbPred = LocalSelfLearningEngine.predict(
            queryText = "دفعت 90 أبو مازن",
            typeFilter = TransactionType.EXPENSE,
            merchantRules = emptyList(),
            historicalTransactions = history,
            categories = categories,
            accounts = accounts
        )
        assertEquals(foodCat.id, nbPred.categoryId)
        assertEquals(2L, nbPred.accountId)
        assertEquals("NAIVE_BAYES", nbPred.source)

        // Fuzzy Dice rule match handles typo ("كارفوور" vs "كارفور")
        val rules = listOf(
            MerchantRuleEntity(id = 1L, keyword = "كارفور", normalizedKeyword = "كارفور", categoryId = foodCat.id, accountId = 1L, preferredType = TransactionType.EXPENSE, hitCount = 3)
        )
        val fuzzyPred = LocalSelfLearningEngine.predict(
            queryText = "كارفوور المعادي",
            typeFilter = TransactionType.EXPENSE,
            merchantRules = rules,
            historicalTransactions = emptyList(),
            categories = categories,
            accounts = accounts
        )
        assertEquals(foodCat.id, fuzzyPred.categoryId)
        assertEquals("RULE_FUZZY", fuzzyPred.source)

        // Franco-Arab normalization + VoiceTransactionParser
        val francoParsed = VoiceTransactionParser.parse(
            rawPhrase = "85 ahwa",
            accounts = accounts,
            categories = categories,
            merchantRules = emptyList(),
            historicalTransactions = history
        )
        assertEquals(8_500L, francoParsed.amountPiasters)
        assertEquals(foodCat.id, francoParsed.suggestedCategoryId)

        // Conversational teaching command & offline turn execution
        val baseState = MainUiState(isLoading = false, preferences = UserPreferences(language = AppLanguage.AR), accounts = accounts, categories = categories, transactions = history, merchantRules = rules)
        val teachTurn = LocalOfflineFinancialAi.executeOfflineTurn(
            mainState = baseState,
            accounts = accounts,
            categories = categories,
            merchantRules = rules,
            fallbackAccountId = 1L,
            priorHistory = emptyList(),
            userPrompt = "علمني ان طلبات مصروف طعام وشراب"
        )
        assertEquals(AiChatIntentMode.ADVICE_ONLY, teachTurn.intentMode)
        assertEquals(1, teachTurn.teachingDirectives.size)
        assertEquals("طلبات", teachTurn.teachingDirectives.first().keyword)
        assertEquals(foodCat.id, teachTurn.teachingDirectives.first().categoryId)

        // Memory inspection query
        val memTurn = LocalOfflineFinancialAi.executeOfflineTurn(
            mainState = baseState,
            accounts = accounts,
            categories = categories,
            merchantRules = rules,
            fallbackAccountId = 1L,
            priorHistory = emptyList(),
            userPrompt = "إيه اللي اتعلمته؟"
        )
        assertTrue(memTurn.coachReply.contains("كارفور"))
        assertTrue(memTurn.coachReply.contains("3"))
    }

    @Test
    fun `repository automatically learns and reinforces clean merchant rules on addTransaction and updateTransaction`() = runBlocking {
        val acc = repo.getAllAccountsOnce().first()
        val cats = repo.getAllCategoriesOnce().filter { it.type == TransactionType.EXPENSE }
        val foodCat = cats.first()
        val secondCat = cats.last()

        val txId = repo.addTransaction(
            TransactionEntity(
                amountPiasters = 14_500L,
                type = TransactionType.EXPENSE,
                accountId = acc.id,
                categoryId = foodCat.id,
                description = "شراء بالبطاقة • بنك مصر (Talabat Egypt) #8841"
            )
        )
        var rules = repo.getAllMerchantRulesOnce()
        val learned = rules.firstOrNull { it.normalizedKeyword.contains("talabat") }
        assertNotNull(learned)
        assertEquals(foodCat.id, learned!!.categoryId)
        assertEquals(1, learned.hitCount)

        // User edits transaction category -> self-learning updates rule immediately
        val inserted = repo.getAllTransactionsOnce().first { it.id == txId }
        repo.updateTransaction(inserted.copy(categoryId = secondCat.id))
        rules = repo.getAllMerchantRulesOnce()
        val updatedRule = rules.first { it.normalizedKeyword.contains("talabat") }
        assertEquals(secondCat.id, updatedRule.categoryId)
        assertEquals(2, updatedRule.hitCount)
    }

    @Test
    fun `smart reader auto-approves high confidence notification arrivals when setting enabled and updates balance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        prefs.setSmartReaderEnabled(true)
        prefs.setSmartReaderAutoApprove(true)
        val processor = SmartReaderProcessor(db, prefs)

        val walletAcc = repo.getAllAccountsOnce().first { it.type == AccountType.E_WALLET }
        val startBalance = walletAcc.currentBalancePiasters

        val result = processor.ingestMessages(
            messages = listOf(
                IncomingRawMessage(
                    sender = "VodafoneCash",
                    body = "فودافون كاش: تم استلام مبلغ 1000 ج.م تحويل وارد من سارة رقم المرجع VF771199"
                )
            ),
            channel = MessageChannel.NOTIFICATION_LISTENER
        )
        assertEquals(1, result.autoApprovedCount)
        assertEquals(0, result.pendingCreated)
        assertEquals(MessageDirection.INCOMING, result.latestDirection)

        val endBalance = repo.getAllAccountsOnce().first { it.id == walletAcc.id }.currentBalancePiasters
        assertEquals(startBalance + 100_000L, endBalance)

        // Reset preference so other tests aren't affected by shared DataStore state
        prefs.setSmartReaderAutoApprove(false)
    }

    @Test
    fun `pending card displays incoming direction badge`() {
        val accounts = listOf(
            AccountEntity(id = 1L, name = "CIB", type = AccountType.BANK, openingBalancePiasters = 500_000L, currentBalancePiasters = 500_000L)
        )
        val categories = listOf(
            CategoryEntity(id = 1L, nameAr = "راتب", nameEn = "Salary", type = TransactionType.INCOME)
        )
        val pendingIncoming = PendingTransactionEntity(
            id = 55L,
            sourceName = "إنستاباي",
            channel = MessageChannel.NOTIFICATION_LISTENER,
            operationKind = MessageOperationKind.INCOMING_TRANSFER,
            mappedType = TransactionType.INCOME,
            amountPiasters = 150_000L,
            transactionTimestampMillis = System.currentTimeMillis(),
            confidenceScore = 0.92f,
            dedupFingerprint = "test_fp_55",
            extractedSummary = "تحويل وارد • إنستاباي",
            status = PendingStatus.PENDING
        )

        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                PendingTransactionCard(
                    item = pendingIncoming,
                    accounts = accounts,
                    categories = categories,
                    lang = AppLanguage.AR,
                    hideAmounts = false,
                    onQuickApprove = { _, _, _, _ -> },
                    onOpenEdit = {},
                    onReject = {},
                    onIgnoreSource = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pending_direction_badge_55").assertIsDisplayed()
        composeTestRule.onNodeWithText("استقبال (وارد)", substring = true).assertIsDisplayed()
    }

    @Test
    fun `smart reader screen toggles auto approve switch`() {
        var autoApproveToggledTo: Boolean? = null
        val mainState = MainUiState(
            isLoading = false,
            preferences = UserPreferences(language = AppLanguage.AR, smartReaderEnabled = true, smartReaderAutoApprove = false),
            accounts = listOf(
                AccountEntity(id = 1L, name = "CIB", type = AccountType.BANK, openingBalancePiasters = 500_000L, currentBalancePiasters = 500_000L)
            )
        )

        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                SmartMoneyReaderScreen(
                    state = mainState,
                    onSetReaderEnabled = {},
                    onSetIgnoreOtp = {},
                    onSetIgnoreBalance = {},
                    onSetAutoApprove = { autoApproveToggledTo = it },
                    onAnalyzePasted = { _, _, _ -> },
                    onImportFile = {},
                    onApprovePending = { _, _, _, _, _, _, _ -> },
                    onRejectPending = {},
                    onIgnoreSource = {},
                    onToggleSource = {},
                    onAddCustomSource = { _, _, _, _ -> },
                    onClearImportData = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_tab_sources").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_auto_approve_switch").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, autoApproveToggledTo)
    }

    @Test
    fun `ai advisor screen shows self learning memory pill`() {
        var sentPrompt: String? = null
        val mainState = MainUiState(
            isLoading = false,
            preferences = UserPreferences(language = AppLanguage.AR),
            merchantRules = listOf(
                MerchantRuleEntity(id = 1L, keyword = "طلبات", normalizedKeyword = "طلبات", categoryId = 1L, hitCount = 4)
            )
        )

        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                AiAdvisorScreen(
                    mainState = mainState,
                    aiState = AiAdvisorUiState(),
                    onSendPrompt = { sentPrompt = it },
                    onGeneratePlan = {},
                    onAnalyzeReceipt = { _, _ -> },
                    onSaveExtractedTx = {},
                    onClearChat = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_local_memory_pill").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("إيه اللي اتعلمته؟", sentPrompt)
    }
}
