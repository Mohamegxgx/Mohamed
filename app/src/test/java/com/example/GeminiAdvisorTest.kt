package com.example

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiActionPlan
import com.example.data.ai.AiAgenticTurnResult
import com.example.data.ai.AiChatIntentMode
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.AiPlanStep
import com.example.data.ai.AiProposedOperation
import com.example.data.ai.AiTransactionCategoryAssignment
import com.example.data.ai.FinancialAiGateway
import com.example.data.ai.FinancialContextFormatter
import com.example.data.ai.GeminiConnectionResult
import com.example.data.ai.GeminiJsonParser
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.AiActionPlanState
import com.example.ui.viewmodel.AiAdvisorViewModel
import com.example.ui.viewmodel.AiChatStreamState
import com.example.ui.viewmodel.AiReceiptExtractState
import com.example.ui.viewmodel.GeminiConnectionStatus
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAdvisorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeFinancialAiGateway(
        var shouldThrowOnPlan: Boolean = false,
        var shouldThrowOnStream: Boolean = false,
        var shouldFailConnectionCheck: Boolean = false,
        var agenticTurnOverride: ((Set<Long>, Set<Long>, Long, String) -> AiAgenticTurnResult?)? = null,
        var batchCategorizeOverride: ((Set<Long>, Set<Long>) -> List<AiTransactionCategoryAssignment>)? = null
    ) : FinancialAiGateway {
        var lastFinancialContext: String = ""
        var lastUserPrompt: String = ""

        override suspend fun verifyConnection(language: AppLanguage): GeminiConnectionResult {
            return if (shouldFailConnectionCheck) {
                GeminiConnectionResult(
                    isConnected = false,
                    modelName = "gemini-4-hydrogen",
                    latencyMs = 45L,
                    errorMessage = "Simulated App Check failure (403)"
                )
            } else {
                GeminiConnectionResult(
                    isConnected = true,
                    modelName = "gemini-4-hydrogen",
                    latencyMs = 120L
                )
            }
        }

        override suspend fun executeAgenticChatTurn(
            financialContext: String,
            accountsContext: String,
            categoriesContext: String,
            validAccountIds: Set<Long>,
            validCategoryIds: Set<Long>,
            fallbackAccountId: Long,
            history: List<AiChatMessage>,
            userPrompt: String,
            primaryCurrency: String,
            language: AppLanguage
        ): AiAgenticTurnResult? {
            lastFinancialContext = financialContext
            lastUserPrompt = userPrompt
            return agenticTurnOverride?.invoke(validAccountIds, validCategoryIds, fallbackAccountId, userPrompt)
        }

        override suspend fun autoCategorizeTransactions(
            transactionsSummary: String,
            categoriesContext: String,
            validTransactionIds: Set<Long>,
            validCategoryIds: Set<Long>,
            language: AppLanguage
        ): List<AiTransactionCategoryAssignment> {
            return batchCategorizeOverride?.invoke(validTransactionIds, validCategoryIds) ?: emptyList()
        }

        override fun streamFinancialAdvice(
            financialContext: String,
            history: List<AiChatMessage>,
            userPrompt: String,
            language: AppLanguage
        ): Flow<String> = flow {
            lastFinancialContext = financialContext
            lastUserPrompt = userPrompt
            if (shouldThrowOnStream) throw IllegalStateException("Temporary network error")
            emit("Great question! ")
            emit("Try keeping daily spend under budget.")
        }

        override suspend fun generateActionPlan(
            financialContext: String,
            language: AppLanguage
        ): AiActionPlan {
            if (shouldThrowOnPlan) throw IllegalStateException("Simulated Gemini API error")
            return AiActionPlan(
                headline = "Boost monthly savings by 15%",
                budgetFocus = "Food & Coffee",
                savingsOpportunity = "Save 1,200 EGP/month",
                steps = listOf(
                    AiPlanStep("Cap weekly dining out", "Saves 800 EGP", "HIGH"),
                    AiPlanStep("Automate goal transfer", "Saves 400 EGP", "MEDIUM")
                )
            )
        }

        override suspend fun analyzeReceiptOrMessage(
            financialContext: String,
            textInput: String?,
            bitmap: Bitmap?,
            language: AppLanguage
        ): AiExtractedTransaction {
            return AiExtractedTransaction(
                amountPiasters = 24_550L,
                type = TransactionType.EXPENSE,
                description = "Carrefour Market",
                categoryHint = "Groceries",
                notes = "Extracted from message"
            )
        }

        override suspend fun smartAutoFillTransaction(
            phrase: String,
            accountsContext: String,
            categoriesContext: String,
            validAccountIds: Set<Long>,
            validCategoryIds: Set<Long>,
            primaryCurrency: String,
            language: AppLanguage
        ): com.example.data.ai.AiAutoFillResult {
            val accList = validAccountIds.toList()
            return com.example.data.ai.AiAutoFillResult(
                type = TransactionType.EXPENSE,
                amountPiasters = 15_000L,
                sourceAccountId = accList.firstOrNull(),
                categoryId = validCategoryIds.firstOrNull(),
                description = phrase,
                confidencePercent = 92
            )
        }

        override suspend fun analyzeFinancialMessagesWithAi(
            sender: String,
            rawMessageBlock: String,
            accountsContext: String,
            categoriesContext: String,
            sourcesContext: String,
            validAccountIds: Set<Long>,
            validCategoryIds: Set<Long>,
            language: AppLanguage
        ): List<com.example.data.ai.AiParsedBankMessage> = emptyList()
    }

    @Test
    fun geminiJsonParser_andContextFormatter_parseStructuredOutputsCorrectly() {
        val planJson = """
            {
              "headline": "Trim discretionary spend",
              "budgetFocus": "Dining",
              "savingsOpportunity": "1,500 EGP/month",
              "steps": [
                {"title": "Pack coffee twice a week", "impact": "300 EGP/mo", "priority": "HIGH"}
              ]
            }
        """.trimIndent()
        val plan = GeminiJsonParser.parseActionPlan(planJson)
        assertEquals("Trim discretionary spend", plan.headline)
        assertEquals(1, plan.steps.size)
        assertEquals("HIGH", plan.steps.first().priority)

        val txJson = """
            {
              "amountMajor": 145.50,
              "type": "EXPENSE",
              "description": "Metro Market",
              "categoryHint": "Food",
              "notes": "Card *4412"
            }
        """.trimIndent()
        val extracted = GeminiJsonParser.parseExtractedTransaction(txJson)
        assertEquals(14_550L, extracted.amountPiasters)
        assertEquals(TransactionType.EXPENSE, extracted.type)
        assertEquals("Metro Market", extracted.description)

        val emptyPrompt = FinancialContextFormatter.buildSnapshotPrompt(MainUiState())
        assertTrue(emptyPrompt.contains("Financial Health Score"))
        assertTrue(emptyPrompt.contains("Safe-to-Spend Today"))

        // Populate Room-like MainUiState and verify habits, merchants, accounts, goals, and recent txs
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity(id = 1L, name = "CIB Bank", type = AccountType.BANK, openingBalancePiasters = 500_000L, currentBalancePiasters = 420_000L)
        )
        val categories = listOf(
            CategoryEntity(id = 10L, nameAr = "قهوة وكافيهات", nameEn = "Coffee & Dining", type = TransactionType.EXPENSE)
        )
        val txs = listOf(
            TransactionEntity(id = 100L, amountPiasters = 18_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 10L, description = "Starbucks Maadi", timestampMillis = now - 1_000L),
            TransactionEntity(id = 101L, amountPiasters = 12_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 10L, description = "Starbucks Maadi", timestampMillis = now - 2_000L),
            TransactionEntity(id = 102L, amountPiasters = 50_000L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 10L, description = "Carrefour Hyper", timestampMillis = now - 3_000L)
        )
        val goals = listOf(
            SavingsGoalEntity(id = 5L, name = "Emergency Buffer", targetAmountPiasters = 1_000_000L, savedAmountPiasters = 350_000L)
        )
        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts = accounts,
            transactions = txs,
            categories = categories,
            budgets = emptyList(),
            savingsGoals = goals,
            installments = emptyList(),
            debts = emptyList(),
            range = DateRangeResolver.resolve(TimeFilter.ALL_TIME)
        )
        val richState = MainUiState(
            accounts = accounts,
            categories = categories,
            transactions = txs,
            savingsGoals = goals,
            dashboardMetrics = metrics
        )

        val highlights = FinancialContextFormatter.extractHabitHighlights(richState)
        assertEquals(3, highlights.totalTransactionsCount)
        assertEquals("Carrefour Hyper", highlights.topMerchants.first().label)
        assertEquals(2, highlights.topMerchants.first { it.label == "Starbucks Maadi" }.txCount)

        val richPrompt = FinancialContextFormatter.buildSnapshotPrompt(richState)
        assertTrue(richPrompt.contains("CIB Bank"))
        assertTrue(richPrompt.contains("Starbucks Maadi(count=2"))
        assertTrue(richPrompt.contains("Emergency Buffer"))
        assertTrue(richPrompt.contains("Recent Room Transactions"))
        assertTrue(richPrompt.contains("Carrefour Hyper"))
    }

    @Test
    fun aiAdvisorViewModel_streamsAdvice_retriesOnFailure_andSavesExtractedTransaction() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        app.repository.ensureSeeded()
        val accounts = app.repository.accounts.first()
        val categories = app.repository.categories.first()
        val mainState = MainUiState(accounts = accounts, categories = categories)
        val gateway = FakeFinancialAiGateway()
        val vm = AiAdvisorViewModel(app.repository, gateway)

        // Simulate transient error then retry
        gateway.shouldThrowOnStream = true
        vm.sendPrompt(mainState, "How am I doing?")
        val streamErrDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.chatState !is AiChatStreamState.Error && System.currentTimeMillis() < streamErrDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(vm.uiState.value.chatState is AiChatStreamState.Error)

        gateway.shouldThrowOnStream = false
        vm.retryLastPrompt(mainState)
        val deadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.chatState !is AiChatStreamState.Success && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(vm.uiState.value.chatState is AiChatStreamState.Success)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("Try keeping daily spend under budget."))

        vm.generateActionPlan(mainState)
        val planDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.actionPlanState !is AiActionPlanState.Success && System.currentTimeMillis() < planDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(vm.uiState.value.actionPlanState is AiActionPlanState.Success)

        gateway.shouldThrowOnPlan = true
        vm.generateActionPlan(mainState)
        val errDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.actionPlanState !is AiActionPlanState.Error && System.currentTimeMillis() < errDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(vm.uiState.value.actionPlanState is AiActionPlanState.Error)

        vm.analyzeReceiptOrMessage(mainState, "Spent 245.50 EGP at Carrefour", null)
        val rxDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.receiptExtractState !is AiReceiptExtractState.Success && System.currentTimeMillis() < rxDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        val successExtract = vm.uiState.value.receiptExtractState as AiReceiptExtractState.Success
        val beforeCount = app.repository.transactions.first().size
        vm.saveExtractedTransaction(mainState, successExtract.extracted)
        val saveDeadline = System.currentTimeMillis() + 3_000L
        while (app.repository.transactions.first().size == beforeCount && System.currentTimeMillis() < saveDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertEquals(beforeCount + 1, app.repository.transactions.first().size)
    }

    @Test
    fun insights_navigatesToAiAdvisorScreen_andStreamsPrompt() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        val gateway = FakeFinancialAiGateway()

        composeTestRule.setContent {
            val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val mainVm = remember {
                MainViewModel(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = app
                )
            }
            val aiVm = remember { AiAdvisorViewModel(app.repository, gateway) }
            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                ElbagorryMoneyRoot(viewModel = mainVm, aiAdvisorViewModel = aiVm)
            }
        }

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("nav_tab_insights").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_insights").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("tool_ai_advisor").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tool_ai_advisor").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("ai_prompt_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ai_connection_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_room_context_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_prompt_input").performTextInput("Analyze my month")
        composeTestRule.onNodeWithTag("ai_send_btn").performClick()

        composeTestRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeTestRule.onNodeWithText("Great question! Try keeping daily spend under budget.")
                    .assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Great question! Try keeping daily spend under budget.").assertIsDisplayed()
    }

    @Test
    fun dashboard_opensAiFinancialCoach_togglesRoomPreview_andTapsHabitChip() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        val gateway = FakeFinancialAiGateway()

        composeTestRule.setContent {
            val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val mainVm = remember {
                MainViewModel(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = app
                )
            }
            val aiVm = remember { AiAdvisorViewModel(app.repository, gateway) }
            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                ElbagorryMoneyRoot(viewModel = mainVm, aiAdvisorViewModel = aiVm)
            }
        }

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_ai_coach_card"))
        composeTestRule.onNodeWithTag("dashboard_ai_coach_card").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("ai_room_context_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ai_toggle_room_preview_btn").performClick()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("ai_open_action_plan_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ai_quick_prompt_0").performClick()

        composeTestRule.waitUntil(5_000L) {
            gateway.lastFinancialContext.contains("ELBAGORRY Money Live Room Database Snapshot") &&
                runCatching {
                    composeTestRule.onNodeWithText("Great question! Try keeping daily spend under budget.")
                        .assertIsDisplayed()
                    true
                }.getOrDefault(false)
        }
        assertTrue(gateway.lastUserPrompt.isNotBlank())
        composeTestRule.onNodeWithText("Great question! Try keeping daily spend under budget.").assertIsDisplayed()
    }

    @Test
    fun geminiJsonParser_parsesAgenticMultiOps_andBatchCategoryAssignments() {
        val agenticJson = """
            {
              "intentMode": "EXECUTE_ACTIONS",
              "operations": [
                {"type": "EXPENSE", "amountMajor": 50.0, "description": "أكل", "sourceAccountId": 1, "categoryId": 10},
                {"type": "EXPENSE", "amountMajor": 20.0, "description": "مواصلات", "sourceAccountId": 1, "categoryId": 11},
                {"type": "EXPENSE", "amountMajor": 70.0, "description": "طلب اونلاين", "sourceAccountId": 999, "categoryId": -1, "newCategoryNameAr": "تسوق أونلاين", "newCategoryNameEn": "Online Shopping"}
              ],
              "coachReply": "تم تسجيل 3 مصاريف وتصنيفها تلقائيًا بإجمالي 140 جنيه."
            }
        """.trimIndent()

        val parsed = GeminiJsonParser.parseAgenticTurnResult(
            jsonString = agenticJson,
            validAccountIds = setOf(1L, 2L),
            validCategoryIds = setOf(10L, 11L),
            fallbackAccountId = 2L
        )
        assertEquals(AiChatIntentMode.EXECUTE_ACTIONS, parsed.intentMode)
        assertEquals(3, parsed.operations.size)
        assertEquals(5_000L, parsed.operations[0].amountPiasters)
        assertEquals(10L, parsed.operations[0].categoryId)
        assertEquals(2_000L, parsed.operations[1].amountPiasters)
        assertEquals(11L, parsed.operations[1].categoryId)
        // Out-of-scope account ID falls back cleanly to fallbackAccountId
        assertEquals(2L, parsed.operations[2].sourceAccountId)
        assertEquals("تسوق أونلاين", parsed.operations[2].newCategoryNameAr)

        val assignJson = """
            {
              "assignments": [
                {"transactionId": 100, "categoryId": 10, "confidencePercent": 95},
                {"transactionId": 100, "categoryId": 11, "confidencePercent": 80},
                {"transactionId": 999, "categoryId": 10, "confidencePercent": 90},
                {"transactionId": 101, "categoryId": 11, "confidencePercent": 88}
              ]
            }
        """.trimIndent()
        val assignments = GeminiJsonParser.parseTransactionCategoryAssignments(
            jsonString = assignJson,
            validTransactionIds = setOf(100L, 101L),
            validCategoryIds = setOf(10L, 11L)
        )
        assertEquals(2, assignments.size)
        assertEquals(10L, assignments.first { it.transactionId == 100L }.categoryId)
        assertEquals(11L, assignments.first { it.transactionId == 101L }.categoryId)
    }

    @Test
    fun agenticChatbot_executesCompoundExpensesInRoom_andUndoRestoresBalances() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        val (accounts, categories, primaryAcc) = runBlocking {
            app.repository.ensureSeeded()
            val accs = app.repository.accounts.first()
            val cats = app.repository.categories.first()
            Triple(accs, cats, accs.first { !it.isArchived })
        }
        val foodCat = categories.first { it.nameEn == "Food & Drinks" }
        val transportCat = categories.first { it.nameEn == "Transport" }
        val shoppingCat = categories.first { it.nameEn == "Shopping" }

        val initialBalance = primaryAcc.currentBalancePiasters
        val initialTxCount = runBlocking { app.repository.transactions.first().size }

        val gateway = FakeFinancialAiGateway(
            agenticTurnOverride = { _, _, fallbackAcc, _ ->
                AiAgenticTurnResult(
                    intentMode = AiChatIntentMode.EXECUTE_ACTIONS,
                    operations = listOf(
                        AiProposedOperation(TransactionType.EXPENSE, 5_000L, description = "أكل", sourceAccountId = fallbackAcc, categoryId = foodCat.id),
                        AiProposedOperation(TransactionType.EXPENSE, 2_000L, description = "مواصلات", sourceAccountId = fallbackAcc, categoryId = transportCat.id),
                        AiProposedOperation(TransactionType.EXPENSE, 7_000L, description = "طلب اونلاين", sourceAccountId = fallbackAcc, categoryId = shoppingCat.id)
                    ),
                    coachReply = "تم تسجيل وتنظيم 3 مصاريف تلقائيًا بإجمالي 140 جنيه."
                )
            }
        )
        val vm = AiAdvisorViewModel(app.repository, gateway)
        val mainState = MainUiState(accounts = accounts, categories = categories)

        vm.sendPrompt(mainState, "صرفت النهاردة 50 جنيه اكل و 20 مواصلات و 70 طلب اونلاين")
        composeTestRule.waitUntil(10_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.chatState is AiChatStreamState.Success
        }

        val assistantMsg = vm.uiState.value.messages.last()
        assertEquals(3, assistantMsg.executedActions.size)
        assertEquals(initialTxCount + 3, runBlocking { app.repository.transactions.first().size })

        val afterAccBalance = runBlocking {
            app.repository.accounts.first().first { it.id == primaryAcc.id }.currentBalancePiasters
        }
        assertEquals(initialBalance - 14_000L, afterAccBalance)

        // Undo in 1 tap and verify exact transaction & balance restoration
        vm.undoExecutedMessageActions(assistantMsg.id)
        composeTestRule.waitUntil(10_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.messages.last().isUndone
        }
        assertTrue(vm.uiState.value.messages.last().isUndone)
        assertEquals(initialTxCount, runBlocking { app.repository.transactions.first().size })
        val restoredBalance = runBlocking {
            app.repository.accounts.first().first { it.id == primaryAcc.id }.currentBalancePiasters
        }
        assertEquals(initialBalance, restoredBalance)
    }

    @Test
    fun mainViewModel_autoCategorizesUncategorizedTransactions_andShowsAgenticReceiptsInUi() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        val (accId, foodCatId, uncatTxId) = runBlocking {
            app.repository.ensureSeeded()
            val acc = app.repository.accounts.first().first { !it.isArchived }
            val food = app.repository.categories.first().first { it.nameEn == "Food & Drinks" }
            val txId = app.repository.addTransaction(
                TransactionEntity(
                    amountPiasters = 8_500L,
                    type = TransactionType.EXPENSE,
                    accountId = acc.id,
                    categoryId = null,
                    description = "وجبة غداء"
                )
            )
            Triple(acc.id, food.id, txId)
        }

        val gateway = FakeFinancialAiGateway(
            agenticTurnOverride = { _, _, fallbackAcc, _ ->
                AiAgenticTurnResult(
                    intentMode = AiChatIntentMode.EXECUTE_ACTIONS,
                    operations = listOf(
                        AiProposedOperation(TransactionType.EXPENSE, 5_000L, description = "أكل", sourceAccountId = fallbackAcc, categoryId = foodCatId)
                    ),
                    coachReply = "تم تسجيل مصروف الأكل تلقائيًا."
                )
            },
            batchCategorizeOverride = { validTxIds, validCatIds ->
                val catToAssign = if (foodCatId in validCatIds) foodCatId else validCatIds.first()
                validTxIds.map { id -> AiTransactionCategoryAssignment(transactionId = id, categoryId = catToAssign, confidencePercent = 96) }
            }
        )

        lateinit var mainVmRef: MainViewModel
        lateinit var aiVmRef: AiAdvisorViewModel
        composeTestRule.setContent {
            val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val mainVm = remember {
                MainViewModel(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = app,
                    aiGateway = gateway
                )
            }
            mainVmRef = mainVm
            val aiVm = remember { AiAdvisorViewModel(app.repository, gateway) }
            aiVmRef = aiVm
            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                ElbagorryMoneyRoot(viewModel = mainVm, aiAdvisorViewModel = aiVm)
            }
        }

        // Navigate to Transactions tab & trigger Auto-Organize banner
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("nav_tab_transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_transactions").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("tx_auto_categorize_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tx_auto_categorize_btn").performClick()

        composeTestRule.waitUntil(10_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val st = mainVmRef.autoCategorizeState.value
            st is com.example.ui.viewmodel.AutoCategorizeState.Done ||
                st is com.example.ui.viewmodel.AutoCategorizeState.Error
        }
        val catState = mainVmRef.autoCategorizeState.value
        assertTrue(
            "Expected AutoCategorizeState.Done but was $catState",
            catState is com.example.ui.viewmodel.AutoCategorizeState.Done
        )
        composeTestRule.waitUntil(10_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            mainVmRef.uiState.value.transactions.firstOrNull { it.id == uncatTxId }?.categoryId == foodCatId
        }

        // Launch AI Chatbot via persistent floating AI button and execute an action
        composeTestRule.onNodeWithTag("fab_ai_coach").performClick()
        composeTestRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag("ai_prompt_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ai_prompt_input").performTextInput("صرفت 50 اكل")
        composeTestRule.onNodeWithTag("ai_send_btn").performClick()

        try {
            composeTestRule.waitUntil(10_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeTestRule.onAllNodesWithText("تم تسجيل مصروف الأكل تلقائيًا.").fetchSemanticsNodes().isNotEmpty() &&
                    mainVmRef.uiState.value.transactions.any { it.description == "أكل" && it.categoryId == foodCatId }
            }
        } catch (t: Throwable) {
            val aiState = aiVmRef.uiState.value
            val hasText = composeTestRule.onAllNodesWithText("تم تسجيل مصروف الأكل تلقائيًا.").fetchSemanticsNodes().isNotEmpty()
            val hasTx = mainVmRef.uiState.value.transactions.any { it.description == "أكل" && it.categoryId == foodCatId }
            throw AssertionError(
                "Timed out waiting for agentic UI. chatState=${aiState.chatState}, msgs=${aiState.messages.map { it.text to it.executedActions.size }}, hasText=$hasText, hasTx=$hasTx, txs=${mainVmRef.uiState.value.transactions.map { it.description to it.categoryId }}",
                t
            )
        }
        composeTestRule.onNodeWithText("تم تسجيل مصروف الأكل تلقائيًا.").assertIsDisplayed()
        assertTrue(accId > 0L)
        assertTrue(mainVmRef.uiState.value.transactions.any { it.description == "أكل" && it.categoryId == foodCatId })
    }

    @Test
    fun hasTransactionLoggingIntent_andVerifyConnection_workAccurately() = runBlocking {
        // Pure coaching questions bypass blocking agentic turn for fast streaming
        assertTrue(!AiAdvisorViewModel.hasTransactionLoggingIntent("فلوسي بتروح فين أكتر الفترة دي؟"))
        assertTrue(!AiAdvisorViewModel.hasTransactionLoggingIntent("حلل عادات الصرف وأكتر أماكن بصرف فيها"))
        assertTrue(!AiAdvisorViewModel.hasTransactionLoggingIntent("How can I save 500 this month?"))
        assertTrue(!AiAdvisorViewModel.hasTransactionLoggingIntent("Analyze my top spending habits"))

        // Action prompts with amount + verb trigger agentic Room execution
        assertTrue(AiAdvisorViewModel.hasTransactionLoggingIntent("صرفت النهاردة 50 جنيه اكل و 20 مواصلات"))
        assertTrue(AiAdvisorViewModel.hasTransactionLoggingIntent("Spent 50 food and 20 transport"))
        assertTrue(AiAdvisorViewModel.hasTransactionLoggingIntent("قبضت 5000 جنيه مرتب"))

        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        app.repository.ensureSeeded()
        val gateway = FakeFinancialAiGateway()
        val vm = AiAdvisorViewModel(app.repository, gateway)

        vm.verifyConnection(AppLanguage.AR)
        val connectedDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.connectionStatus !is GeminiConnectionStatus.Connected &&
            System.currentTimeMillis() < connectedDeadline
        ) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        val connected = vm.uiState.value.connectionStatus as GeminiConnectionStatus.Connected
        assertEquals("gemini-4-hydrogen", connected.modelName)
        assertEquals(120L, connected.latencyMs)

        gateway.shouldFailConnectionCheck = true
        vm.verifyConnection(AppLanguage.AR)
        val disconnectedDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.connectionStatus !is GeminiConnectionStatus.Disconnected &&
            System.currentTimeMillis() < disconnectedDeadline
        ) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        val disconnected = vm.uiState.value.connectionStatus as GeminiConnectionStatus.Disconnected
        assertTrue(disconnected.reason.contains("403"))
    }

    @Test
    fun offlineLocalAi_executesCompoundExpenses_mathAndPercentages_andPersistsChatHistoryInRoom() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        app.repository.ensureSeeded()
        val accounts = app.repository.accounts.first()
        val categories = app.repository.categories.first()
        val primaryAcc = accounts.first { !it.isArchived }
        val initialBalance = primaryAcc.currentBalancePiasters
        val initialTxCount = app.repository.transactions.first().size
        val mainState = MainUiState(accounts = accounts, categories = categories)

        // Gateway that throws if unexpectedly called when offline
        val offlineGateway = FakeFinancialAiGateway(shouldThrowOnStream = true, shouldFailConnectionCheck = true)
        val offlineVm = AiAdvisorViewModel(
            repository = app.repository,
            gateway = offlineGateway,
            connectivityChecker = com.example.data.remote.NetworkConnectivityChecker { false }
        )

        // Verify connection immediately reports OfflineReady without waiting on network
        offlineVm.verifyConnection(AppLanguage.AR)
        assertTrue(offlineVm.uiState.value.connectionStatus is GeminiConnectionStatus.OfflineReady)

        // 1. Compound Arabic expenses logged completely offline
        offlineVm.sendPrompt(mainState, "صرفت النهاردة 50 اكل و 20 مواصلات و 70 طلب اونلاين")
        val step1Deadline = System.currentTimeMillis() + 5_000L
        while (offlineVm.uiState.value.chatState !is AiChatStreamState.Success && System.currentTimeMillis() < step1Deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(offlineVm.uiState.value.chatState is AiChatStreamState.Success)
        val offlineMsg1 = offlineVm.uiState.value.messages.last()
        assertEquals(com.example.data.ai.AiEngineSource.LOCAL_OFFLINE, offlineMsg1.engineSource)
        assertEquals(3, offlineMsg1.executedActions.size)
        assertEquals(initialTxCount + 3, app.repository.transactions.first().size)
        val updatedBal = app.repository.accounts.first().first { it.id == primaryAcc.id }.currentBalancePiasters
        assertEquals(initialBalance - 14_000L, updatedBal)

        // 2. Math & percentage calculations completely offline
        offlineVm.sendPrompt(mainState, "احسب 20% من 5000")
        val step2Deadline = System.currentTimeMillis() + 5_000L
        while (offlineVm.uiState.value.messages.size < 4 && System.currentTimeMillis() < step2Deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        val pctReply = offlineVm.uiState.value.messages.last()
        assertEquals(com.example.data.ai.AiEngineSource.LOCAL_OFFLINE, pctReply.engineSource)
        assertTrue("Expected 1,000 in percentage reply but got: ${pctReply.text}", pctReply.text.contains("1,000") || pctReply.text.contains("1000"))

        offlineVm.sendPrompt(mainState, "50 + 20 * 3")
        val step3Deadline = System.currentTimeMillis() + 5_000L
        while (offlineVm.uiState.value.messages.size < 6 && System.currentTimeMillis() < step3Deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        val mathReply = offlineVm.uiState.value.messages.last()
        assertTrue("Expected 110 in arithmetic reply but got: ${mathReply.text}", mathReply.text.contains("110"))

        // 3. Multi-session persistence in Room: check session list, start new chat, and reload previous session
        val sessionId = offlineVm.uiState.value.activeSessionId
        val savedSessionsDeadline = System.currentTimeMillis() + 5_000L
        while (app.repository.getAiChatMessagesOnce(sessionId).size < 6 && System.currentTimeMillis() < savedSessionsDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertEquals(6, app.repository.getAiChatMessagesOnce(sessionId).size)
        assertTrue(app.repository.getAllAiChatSessionsOnce().any { it.id == sessionId && !it.syncedToCloud })

        offlineVm.createNewChat()
        assertEquals(0, offlineVm.uiState.value.messages.size)

        offlineVm.selectChatSession(sessionId)
        val reloadDeadline = System.currentTimeMillis() + 5_000L
        while (offlineVm.uiState.value.messages.size < 6 && System.currentTimeMillis() < reloadDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertEquals(6, offlineVm.uiState.value.messages.size)
        assertEquals(3, offlineVm.uiState.value.messages[1].executedActions.size)

        // 4. Clean delete session removes from Room (with CASCADE on messages)
        offlineVm.deleteChatSession(sessionId)
        val deleteDeadline = System.currentTimeMillis() + 3_000L
        while (app.repository.getAiChatSessionById(sessionId) != null && System.currentTimeMillis() < deleteDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertEquals(null, app.repository.getAiChatSessionById(sessionId))
        assertTrue(app.repository.getAiChatMessagesOnce(sessionId).isEmpty())
    }

    @Test
    fun hybridAutoMode_fallsBackToLocalOfflineAiOnNetworkHostFailure() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        app.repository.ensureSeeded()
        val accounts = app.repository.accounts.first()
        val categories = app.repository.categories.first()
        val mainState = MainUiState(accounts = accounts, categories = categories)

        // Gateway throwing UnknownHostException wrapped in RuntimeException simulates dropped internet during request
        val flakyGateway = object : FinancialAiGateway by FakeFinancialAiGateway() {
            override fun streamFinancialAdvice(
                financialContext: String,
                history: List<AiChatMessage>,
                userPrompt: String,
                language: AppLanguage
            ): Flow<String> = flow {
                throw RuntimeException(java.net.UnknownHostException("Unable to resolve host generativelanguage.googleapis.com"))
            }
        }
        val vm = AiAdvisorViewModel(
            repository = app.repository,
            gateway = flakyGateway,
            connectivityChecker = com.example.data.remote.NetworkConnectivityChecker { true }
        )

        vm.sendPrompt(mainState, "رصيدي كام دلوقتي؟")
        val deadline = System.currentTimeMillis() + 5_000L
        while (vm.uiState.value.chatState !is AiChatStreamState.Success && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(25)
        }
        assertTrue(vm.uiState.value.chatState is AiChatStreamState.Success)
        val reply = vm.uiState.value.messages.last()
        assertEquals(com.example.data.ai.AiEngineSource.LOCAL_OFFLINE, reply.engineSource)
        assertTrue(!reply.isError)
    }
}
