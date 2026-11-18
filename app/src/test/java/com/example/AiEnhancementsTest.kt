package com.example

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiActionPlan
import com.example.data.ai.AiAutoFillResult
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.AiParsedBankMessage
import com.example.data.ai.AiPlanStep
import com.example.data.ai.FinancialAiGateway
import com.example.data.ai.GeminiJsonParser
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.PendingStatus
import com.example.domain.model.TransactionType
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.QuickAutoFillState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
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
class AiEnhancementsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class MockEnhancedAiGateway(
        var throwAutoFillError: Boolean = false
    ) : FinancialAiGateway {
        var lastAnalyzedMessageBlock: String = ""

        override fun streamFinancialAdvice(
            financialContext: String,
            history: List<AiChatMessage>,
            userPrompt: String,
            language: AppLanguage
        ): Flow<String> = flow { emit("ok") }

        override suspend fun generateActionPlan(
            financialContext: String,
            language: AppLanguage
        ): AiActionPlan = AiActionPlan("Plan", "Food", "500 EGP", listOf(AiPlanStep("Step", "500", "HIGH")))

        override suspend fun analyzeReceiptOrMessage(
            financialContext: String,
            textInput: String?,
            bitmap: Bitmap?,
            language: AppLanguage
        ): AiExtractedTransaction = AiExtractedTransaction(1000L, TransactionType.EXPENSE, "Test", "Food", "")

        override suspend fun smartAutoFillTransaction(
            phrase: String,
            accountsContext: String,
            categoriesContext: String,
            validAccountIds: Set<Long>,
            validCategoryIds: Set<Long>,
            primaryCurrency: String,
            language: AppLanguage
        ): AiAutoFillResult {
            if (throwAutoFillError) throw IllegalStateException("Network error during Gemini auto-fill")
            val accIds = validAccountIds.sorted()
            val fromId = accIds.getOrNull(0)
            val toId = accIds.getOrNull(1)
            return AiAutoFillResult(
                type = TransactionType.TRANSFER,
                amountPiasters = 150_000L,
                feePiasters = 1_000L,
                sourceAccountId = fromId,
                targetAccountId = toId,
                categoryId = null,
                description = "تحويل بنكي إلى المحفظة",
                notes = "ملء تلقائي ذكي",
                confidencePercent = 96
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
        ): List<AiParsedBankMessage> {
            lastAnalyzedMessageBlock = rawMessageBlock
            val accIds = validAccountIds.sorted()
            return listOf(
                AiParsedBankMessage(
                    isFinancial = true,
                    operationKind = MessageOperationKind.INTERNAL_TRANSFER,
                    mappedType = TransactionType.TRANSFER,
                    amountPiasters = 200_000L,
                    feePiasters = 500L,
                    currency = "EGP",
                    sourceName = "إنستاباي",
                    counterpartyName = "محفظتي",
                    referenceNumber = "IPN-AI-9911",
                    balanceAfterPiasters = 800_000L,
                    suggestedAccountId = accIds.getOrNull(0),
                    targetAccountId = accIds.getOrNull(1),
                    suggestedCategoryId = null,
                    summary = "تحويل داخلي من البنك إلى المحفظة",
                    confidenceScore = 0.95f
                )
            )
        }
    }

    @Test
    fun geminiJsonParser_parsesAutoFillAndBatchMessagesWithTransferValidation() {
        val autoFillJson = """
            {
              "type": "TRANSFER",
              "amountMajor": 1250.50,
              "feeMajor": 10.0,
              "sourceAccountId": 2,
              "targetAccountId": 3,
              "categoryId": 5,
              "description": "تحويل لإنستاباي",
              "notes": "تحويل شهري",
              "confidencePercent": 94
            }
        """.trimIndent()
        val autoFill = GeminiJsonParser.parseAutoFillResult(
            jsonString = autoFillJson,
            validAccountIds = setOf(1L, 2L, 3L),
            validCategoryIds = setOf(5L, 6L)
        )
        assertEquals(TransactionType.TRANSFER, autoFill.type)
        assertEquals(125_050L, autoFill.amountPiasters)
        assertEquals(1_000L, autoFill.feePiasters)
        assertEquals(2L, autoFill.sourceAccountId)
        assertEquals(3L, autoFill.targetAccountId)
        assertEquals(null, autoFill.categoryId) // Transfers do not keep expense/income categories
        assertEquals(94, autoFill.confidencePercent)

        val batchJson = """
            {
              "transactions": [
                {
                  "isFinancial": true,
                  "operationKind": "INTERNAL_TRANSFER",
                  "mappedType": "TRANSFER",
                  "amountMajor": 500.0,
                  "feeMajor": 5.0,
                  "currency": "EGP",
                  "sourceName": "CIB",
                  "counterpartyName": "Vodafone Cash",
                  "referenceNumber": "REF777",
                  "balanceAfterMajor": 4500.0,
                  "suggestedAccountId": 2,
                  "targetAccountId": 3,
                  "summary": "تحويل إلى المحفظة"
                },
                {
                  "isFinancial": false,
                  "operationKind": "CARD_PURCHASE",
                  "mappedType": "EXPENSE",
                  "amountMajor": 100.0,
                  "summary": "Ignore non financial"
                }
              ]
            }
        """.trimIndent()
        val parsedBatch = GeminiJsonParser.parseFinancialMessagesBatch(
            jsonString = batchJson,
            validAccountIds = setOf(1L, 2L, 3L),
            validCategoryIds = setOf(4L)
        )
        assertEquals(1, parsedBatch.size)
        val first = parsedBatch.first()
        assertEquals(TransactionType.TRANSFER, first.mappedType)
        assertEquals(50_000L, first.amountPiasters)
        assertEquals(500L, first.feePiasters)
        assertEquals(2L, first.suggestedAccountId)
        assertEquals(3L, first.targetAccountId)
        assertEquals(450_000L, first.balanceAfterPiasters)
    }

    @Test
    fun voiceTransactionParser_handlesAttachedArabicPrefixesAndTransferFees() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        val accounts = runBlocking { app.repository.accounts.first() }
        val categories = runBlocking { app.repository.categories.first() }

        val parsed = VoiceTransactionParser.parse(
            rawPhrase = "حولت 1500 جنيه من البنك للمحفظة برسوم 10",
            accounts = accounts,
            categories = categories
        )
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals(150_000L, parsed.amountPiasters)
        assertEquals(1_000L, parsed.feePiasters)
        val fromAcc = accounts.firstOrNull { it.id == parsed.suggestedAccountId }
        val toAcc = accounts.firstOrNull { it.id == parsed.targetAccountId }
        assertNotNull(fromAcc)
        assertNotNull(toAcc)
        assertEquals(AccountType.BANK, fromAcc!!.type)
        assertEquals(AccountType.E_WALLET, toAcc!!.type)
    }

    @Test
    fun smartReaderProcessor_aiIngestionBlocksOtpAndCreatesTransferWithFeeAndApprovesCleanly() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        app.repository.ensureSeeded()
        app.repository.clearImportData()
        val gateway = MockEnhancedAiGateway()

        val rawInput = """
            كود التحقق OTP الخاص بك هو 918273 لا تشارك هذا الرمز

            تم تحويل مبلغ 2000 جنيه عبر إنستاباي من البنك للمحفظة برسوم 5 جنيه مرجع IPN-AI-9911
        """.trimIndent()

        val result = app.smartReaderProcessor.ingestMessagesWithAi(
            sender = "InstaPay",
            rawText = rawInput,
            channel = MessageChannel.CLIPBOARD_PASTE,
            aiGateway = gateway,
            language = AppLanguage.AR
        )

        // Verify OTP never even reached Gemini AI!
        assertFalse(gateway.lastAnalyzedMessageBlock.contains("918273"))
        assertFalse(gateway.lastAnalyzedMessageBlock.contains("OTP"))
        assertTrue(gateway.lastAnalyzedMessageBlock.contains("IPN-AI-9911"))

        assertEquals(1, result.pendingCreated)
        val pendingList = app.repository.pendingTransactions.first().filter { it.status == PendingStatus.PENDING }
        val transferPending = pendingList.firstOrNull { it.mappedType == TransactionType.TRANSFER }
        val feePending = pendingList.firstOrNull { it.operationKind == MessageOperationKind.TRANSFER_FEE }
        assertNotNull(transferPending)
        assertNotNull(feePending)
        assertNotNull(transferPending!!.targetAccountId)
        assertEquals(500L, feePending!!.amountPiasters)

        val beforeAccounts = app.repository.accounts.first().associateBy { it.id }
        val srcBefore = beforeAccounts[transferPending.suggestedAccountId!!]!!.currentBalancePiasters
        val dstBefore = beforeAccounts[transferPending.targetAccountId!!]!!.currentBalancePiasters

        val approveRes = app.repository.approvePendingTransaction(
            pendingId = transferPending.id,
            accountId = transferPending.suggestedAccountId!!,
            targetAccountId = transferPending.targetAccountId
        )
        assertTrue(approveRes.isSuccess)

        val afterAccounts = app.repository.accounts.first().associateBy { it.id }
        assertEquals(srcBefore - 200_000L, afterAccounts[transferPending.suggestedAccountId!!]!!.currentBalancePiasters)
        assertEquals(dstBefore + 200_000L, afterAccounts[transferPending.targetAccountId!!]!!.currentBalancePiasters)
    }

    @Test
    fun quickAddSheet_aiAutoFillPopulatesTransferWithFeeAndHandlesErrors() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.repository.ensureSeeded() }
        val gateway = MockEnhancedAiGateway()
        lateinit var mainVmRef: MainViewModel

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
            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                ElbagorryMoneyRoot(viewModel = mainVm)
            }
        }

        // Verify error surfacing if AI throws
        gateway.throwAutoFillError = true
        mainVmRef.runAiAutoFill("حولت 500 من البنك للمحفظة")
        composeTestRule.waitUntil(3_000L) {
            mainVmRef.quickAutoFillState.value is QuickAutoFillState.Error
        }
        gateway.throwAutoFillError = false
        mainVmRef.resetAiAutoFillState()

        // Open Quick Add Sheet via FAB
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("fab_quick_add").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("fab_quick_add").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("toggle_smart_phrase_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("toggle_smart_phrase_btn").performClick()
        composeTestRule.onNodeWithTag("smart_phrase_input").performTextInput("حولت 1500 من الكاش للمحفظة برسوم 10")
        composeTestRule.onNodeWithTag("ai_autofill_btn").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("ai_autofill_success_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ai_autofill_success_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tx_transfer_fee_input").performScrollTo().assertIsDisplayed()

        val txCountBefore = runBlocking { app.repository.transactions.first().size }
        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()

        composeTestRule.waitUntil(5_000L) {
            runBlocking { app.repository.transactions.first().size } == txCountBefore + 2
        }
        val latestTxs = runBlocking { app.repository.transactions.first().take(2) }
        assertTrue(latestTxs.any { it.type == TransactionType.TRANSFER && it.amountPiasters == 150_000L })
        assertTrue(latestTxs.any { it.type == TransactionType.EXPENSE && it.amountPiasters == 1_000L })
    }
}
