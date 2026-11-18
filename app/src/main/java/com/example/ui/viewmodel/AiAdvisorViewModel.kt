package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiActionPlan
import com.example.data.ai.AiChatJsonMapper
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiEngineSource
import com.example.data.ai.AiExecutedActionRecord
import com.example.data.ai.AiExtractedLineItem
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.AiProposedOperation
import com.example.data.ai.FinancialAiGateway
import com.example.data.ai.FinancialContextFormatter
import com.example.data.ai.FirebaseGeminiFinancialGateway
import com.example.data.ai.LocalOfflineFinancialAi
import com.example.data.local.ReceiptStorageManager
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AiChatMessageEntity
import com.example.data.local.entity.AiChatSessionEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.NetworkConnectivityChecker
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.OfflineReceiptAnalyzer
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

private const val TAG = "AiAdvisorViewModel"
private const val AI_TIMEOUT_MS = 60_000L

enum class AiEngineMode {
    AUTO,
    CLOUD,
    OFFLINE
}

sealed interface CloudChatSyncState {
    data object Idle : CloudChatSyncState
    data object Syncing : CloudChatSyncState
    data object Synced : CloudChatSyncState
    data object LocalOnly : CloudChatSyncState
    data class Error(val message: String) : CloudChatSyncState
}

sealed interface AiChatStreamState {
    data object Idle : AiChatStreamState
    data object Loading : AiChatStreamState
    data class Streaming(val partialText: String) : AiChatStreamState
    data class Success(val text: String) : AiChatStreamState
    data class Error(val message: String) : AiChatStreamState
}

sealed interface AiActionPlanState {
    data object Idle : AiActionPlanState
    data object Loading : AiActionPlanState
    data class Success(val plan: AiActionPlan) : AiActionPlanState
    data class Error(val message: String) : AiActionPlanState
}

sealed interface AiReceiptExtractState {
    data object Idle : AiReceiptExtractState
    data object Loading : AiReceiptExtractState
    data class Success(val extracted: AiExtractedTransaction, val saved: Boolean = false) : AiReceiptExtractState
    data class Error(val message: String) : AiReceiptExtractState
}

sealed interface GeminiConnectionStatus {
    data object Checking : GeminiConnectionStatus
    data class Connected(val modelName: String, val latencyMs: Long) : GeminiConnectionStatus
    data class OfflineReady(val reason: String) : GeminiConnectionStatus
    data class Disconnected(val reason: String) : GeminiConnectionStatus
}

data class AiAdvisorUiState(
    val activeSessionId: String = "",
    val activeSessionTitle: String = "",
    val sessions: List<AiChatSessionEntity> = emptyList(),
    val messages: List<AiChatMessage> = emptyList(),
    val chatState: AiChatStreamState = AiChatStreamState.Idle,
    val actionPlanState: AiActionPlanState = AiActionPlanState.Idle,
    val receiptExtractState: AiReceiptExtractState = AiReceiptExtractState.Idle,
    val connectionStatus: GeminiConnectionStatus = GeminiConnectionStatus.Checking,
    val engineMode: AiEngineMode = AiEngineMode.AUTO,
    val isDeviceOnline: Boolean = true,
    val cloudSyncState: CloudChatSyncState = CloudChatSyncState.LocalOnly
)

class AiAdvisorViewModel(
    private val repository: MoneyRepository,
    private val gateway: FinancialAiGateway = FirebaseGeminiFinancialGateway(),
    private val connectivityChecker: NetworkConnectivityChecker = NetworkConnectivityChecker { true },
    private val cloudSyncRepository: CloudSyncRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private var lastReceiptBitmap: Bitmap? = null
    private var lastSavedReceiptUri: String? = null

    private val _uiState = MutableStateFlow(
        AiAdvisorUiState(
            activeSessionId = UUID.randomUUID().toString(),
            isDeviceOnline = runCatching { connectivityChecker.isOnline() }.getOrDefault(true),
            cloudSyncState = if (cloudSyncRepository != null) CloudChatSyncState.Idle else CloudChatSyncState.LocalOnly
        )
    )
    val uiState: StateFlow<AiAdvisorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.aiChatSessions.collect { sessions ->
                _uiState.update { state ->
                    val refreshedTitle = sessions.firstOrNull { it.id == state.activeSessionId }?.title
                        ?: state.activeSessionTitle
                    state.copy(
                        sessions = sessions,
                        activeSessionTitle = refreshedTitle
                    )
                }
            }
        }
        if (cloudSyncRepository != null && runCatching { connectivityChecker.isOnline() }.getOrDefault(false)) {
            syncChatsWithCloud()
        }
    }

    fun setEngineMode(mode: AiEngineMode, language: AppLanguage) {
        _uiState.update { it.copy(engineMode = mode) }
        verifyConnection(language)
    }

    fun sendPrompt(mainState: MainUiState, rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isBlank()) return
        val sessionId = _uiState.value.activeSessionId.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
        val userEngine = if (_uiState.value.engineMode == AiEngineMode.OFFLINE || !online) {
            AiEngineSource.LOCAL_OFFLINE
        } else {
            AiEngineSource.CLOUD_GEMINI
        }

        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = question,
            engineSource = userEngine,
            timestampMillis = now
        )
        val priorHistory = _uiState.value.messages
        val updatedWithUser = priorHistory + userMsg
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                messages = updatedWithUser,
                chatState = AiChatStreamState.Loading,
                isDeviceOnline = online
            )
        }
        executeStreamPrompt(
            mainState = mainState,
            sessionId = sessionId,
            question = question,
            priorHistory = priorHistory,
            currentMessagesWithUser = updatedWithUser
        )
    }

    fun retryLastPrompt(
        mainState: MainUiState,
        forceOffline: Boolean = false
    ) {
        val currentMessages = _uiState.value.messages
        val lastUserMsg = currentMessages.lastOrNull { it.isUser } ?: return
        val sessionId = _uiState.value.activeSessionId.ifBlank { UUID.randomUUID().toString() }
        val cleaned = currentMessages.dropLastWhile { !it.isUser }
        val trimmedHistory = cleaned.dropLast(1)
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                messages = cleaned,
                chatState = AiChatStreamState.Loading
            )
        }
        executeStreamPrompt(
            mainState = mainState,
            sessionId = sessionId,
            question = lastUserMsg.text,
            priorHistory = trimmedHistory,
            currentMessagesWithUser = cleaned,
            forceOfflineForTurn = forceOffline
        )
    }

    fun verifyConnection(language: AppLanguage) {
        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
        val mode = _uiState.value.engineMode

        if (mode == AiEngineMode.OFFLINE) {
            _uiState.update {
                it.copy(
                    isDeviceOnline = online,
                    connectionStatus = GeminiConnectionStatus.OfflineReady(
                        if (language == AppLanguage.EN) {
                            "Offline Local AI active — zero internet needed"
                        } else {
                            "وضع الذكاء المحلي مفعّل — يعمل بالكامل بدون إنترنت"
                        }
                    )
                )
            }
            return
        }

        if (!online) {
            _uiState.update {
                it.copy(
                    isDeviceOnline = false,
                    connectionStatus = GeminiConnectionStatus.OfflineReady(
                        if (language == AppLanguage.EN) {
                            "No internet — Offline Local AI is ready"
                        } else {
                            "لا يوجد إنترنت — الذكاء المحلي جاهز للرد والحساب والتسجيل"
                        }
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isDeviceOnline = true,
                connectionStatus = GeminiConnectionStatus.Checking
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                gateway.verifyConnection(language)
            }
            val stillOnline = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
            _uiState.update {
                it.copy(
                    isDeviceOnline = stillOnline,
                    connectionStatus = when {
                        result.isConnected -> GeminiConnectionStatus.Connected(
                            modelName = result.modelName,
                            latencyMs = result.latencyMs
                        )
                        !stillOnline -> GeminiConnectionStatus.OfflineReady(
                            if (language == AppLanguage.EN) {
                                "No internet — Offline Local AI is ready"
                            } else {
                                "لا يوجد إنترنت — الذكاء المحلي جاهز للعمل"
                            }
                        )
                        else -> GeminiConnectionStatus.Disconnected(
                            reason = result.errorMessage ?: if (language == AppLanguage.EN) {
                                "Unable to reach Gemini."
                            } else {
                                "تعذر الاتصال بـ Gemini."
                            }
                        )
                    }
                )
            }
        }
    }

    private fun executeStreamPrompt(
        mainState: MainUiState,
        sessionId: String,
        question: String,
        priorHistory: List<AiChatMessage>,
        currentMessagesWithUser: List<AiChatMessage>,
        forceOfflineForTurn: Boolean = false
    ) {
        val lang = mainState.preferences.language
        val snapshot = FinancialContextFormatter.buildSnapshotPrompt(mainState)

        viewModelScope.launch {
            val turnStart = System.currentTimeMillis()
            val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
            val mode = _uiState.value.engineMode
            val useLocalDirectly = forceOfflineForTurn || mode == AiEngineMode.OFFLINE || (!online && mode != AiEngineMode.CLOUD)

            val activeAccounts = if (mainState.accounts.isNotEmpty()) {
                mainState.accounts.filter { !it.isArchived }
            } else {
                withContext(Dispatchers.IO) { repository.getAllAccountsOnce() }.filter { !it.isArchived }
            }
            val baseCategories = if (mainState.categories.isNotEmpty()) {
                mainState.categories
            } else {
                withContext(Dispatchers.IO) { repository.getAllCategoriesOnce() }
            }
            val merchantRules = mainState.merchantRules.ifEmpty {
                withContext(Dispatchers.IO) { repository.getAllMerchantRulesOnce() }
            }
            val fallbackAccount = activeAccounts.firstOrNull()

            if (useLocalDirectly) {
                runLocalOfflineTurn(
                    mainState = mainState,
                    sessionId = sessionId,
                    question = question,
                    priorHistory = priorHistory,
                    currentMessagesWithUser = currentMessagesWithUser,
                    activeAccounts = activeAccounts,
                    baseCategories = baseCategories,
                    merchantRules = merchantRules,
                    fallbackAccount = fallbackAccount,
                    online = online,
                    lang = lang
                )
                return@launch
            }

            val accumulated = StringBuilder()
            try {
                val agenticResult = if (fallbackAccount != null && hasTransactionLoggingIntent(question)) {
                    withTimeout(AI_TIMEOUT_MS) {
                        gateway.executeAgenticChatTurn(
                            financialContext = snapshot,
                            accountsContext = FinancialContextFormatter.buildAccountsContext(activeAccounts),
                            categoriesContext = FinancialContextFormatter.buildCategoriesContext(baseCategories),
                            validAccountIds = activeAccounts.map { it.id }.toSet(),
                            validCategoryIds = baseCategories.map { it.id }.toSet(),
                            fallbackAccountId = fallbackAccount.id,
                            history = priorHistory,
                            userPrompt = question,
                            primaryCurrency = mainState.preferences.currencyCode,
                            language = lang
                        )
                    }
                } else {
                    null
                }

                if (agenticResult != null) {
                    val executedRecords = executeProposedOperationsInRoom(
                        operations = agenticResult.operations,
                        activeAccounts = activeAccounts,
                        baseCategories = baseCategories,
                        merchantRules = merchantRules,
                        fallbackAccount = fallbackAccount,
                        sourceIdentifier = "Gemini 4 Hydrogen"
                    )

                    val totalPiasters = executedRecords.sumOf { it.amountPiasters + it.feePiasters }
                    val replyText = agenticResult.coachReply.ifBlank {
                        if (executedRecords.isNotEmpty()) {
                            val totalFormatted = MoneyFormatter.formatPiasters(
                                piasters = totalPiasters,
                                currency = mainState.preferences.currencyCode,
                                language = lang
                            )
                            if (lang == AppLanguage.EN) {
                                "Done! Recorded & categorized ${executedRecords.size} transaction(s) totaling $totalFormatted."
                            } else {
                                "تم التنفيذ! تم تسجيل وتصنيف ${executedRecords.size} معاملة تلقائيًا بإجمالي $totalFormatted."
                            }
                        } else {
                            if (lang == AppLanguage.EN) {
                                "Gemini returned an empty response. Please try again."
                            } else {
                                "لم يُعد المساعد ردًا، يرجى المحاولة مرة أخرى."
                            }
                        }
                    }

                    val replyMsg = AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = replyText,
                        executedActions = executedRecords,
                        engineSource = AiEngineSource.CLOUD_GEMINI,
                        timestampMillis = System.currentTimeMillis()
                    )
                    val finalMessages = currentMessagesWithUser + replyMsg
                    val elapsed = (System.currentTimeMillis() - turnStart).coerceAtLeast(1L)
                    _uiState.update { state ->
                        val nextMessages = if (state.activeSessionId == sessionId) finalMessages else state.messages
                        state.copy(
                            messages = nextMessages,
                            chatState = if (state.activeSessionId == sessionId) AiChatStreamState.Success(replyText) else state.chatState,
                            isDeviceOnline = true,
                            connectionStatus = GeminiConnectionStatus.Connected(
                                modelName = FirebaseGeminiFinancialGateway.MODEL_NAME,
                                latencyMs = elapsed
                            )
                        )
                    }
                    persistAndAutoSyncSession(sessionId, finalMessages, lang)
                    return@launch
                }

                withTimeout(AI_TIMEOUT_MS) {
                    gateway.streamFinancialAdvice(
                        financialContext = snapshot,
                        history = priorHistory,
                        userPrompt = question,
                        language = lang
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _uiState.update { state ->
                            if (state.activeSessionId == sessionId) {
                                state.copy(chatState = AiChatStreamState.Streaming(accumulated.toString()))
                            } else state
                        }
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    val replyMsg = AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = finalText,
                        engineSource = AiEngineSource.CLOUD_GEMINI,
                        timestampMillis = System.currentTimeMillis()
                    )
                    val finalMessages = currentMessagesWithUser + replyMsg
                    val elapsed = (System.currentTimeMillis() - turnStart).coerceAtLeast(1L)
                    _uiState.update { state ->
                        state.copy(
                            messages = if (state.activeSessionId == sessionId) finalMessages else state.messages,
                            chatState = if (state.activeSessionId == sessionId) AiChatStreamState.Success(finalText) else state.chatState,
                            isDeviceOnline = true,
                            connectionStatus = GeminiConnectionStatus.Connected(
                                modelName = FirebaseGeminiFinancialGateway.MODEL_NAME,
                                latencyMs = elapsed
                            )
                        )
                    }
                    persistAndAutoSyncSession(sessionId, finalMessages, lang)
                } else {
                    val emptyMsg = if (lang == AppLanguage.EN) {
                        "Gemini returned an empty response. Please try again."
                    } else {
                        "لم يُعد المساعد ردًا، يرجى المحاولة مرة أخرى."
                    }
                    val errReply = AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = emptyMsg,
                        isError = true,
                        engineSource = AiEngineSource.CLOUD_GEMINI,
                        timestampMillis = System.currentTimeMillis()
                    )
                    val finalMessages = currentMessagesWithUser + errReply
                    _uiState.update { state ->
                        state.copy(
                            messages = if (state.activeSessionId == sessionId) finalMessages else state.messages,
                            chatState = if (state.activeSessionId == sessionId) AiChatStreamState.Error(emptyMsg) else state.chatState
                        )
                    }
                    persistAndAutoSyncSession(sessionId, finalMessages, lang)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mode == AiEngineMode.AUTO && shouldFallbackToOffline(e)) {
                    Log.w(TAG, "Cloud AI unreachable, falling back cleanly to LocalOfflineFinancialAi", e)
                    runLocalOfflineTurn(
                        mainState = mainState,
                        sessionId = sessionId,
                        question = question,
                        priorHistory = priorHistory,
                        currentMessagesWithUser = currentMessagesWithUser,
                        activeAccounts = activeAccounts,
                        baseCategories = baseCategories,
                        merchantRules = merchantRules,
                        fallbackAccount = fallbackAccount,
                        online = runCatching { connectivityChecker.isOnline() }.getOrDefault(false),
                        lang = lang
                    )
                } else {
                    Log.e(TAG, "Firebase AI advice failed", e)
                    val errMsg = if (e is TimeoutCancellationException) {
                        if (lang == AppLanguage.EN) {
                            "Request timed out after 60 seconds. Check your connection or switch to Offline AI."
                        } else {
                            "انتهت مهلة الطلب بعد 60 ثانية. يمكنك التبديل إلى الذكاء المحلي للرد الفوري."
                        }
                    } else {
                        FirebaseGeminiFinancialGateway.formatGeminiError(e, lang)
                    }
                    val errReply = AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = errMsg,
                        isError = true,
                        engineSource = AiEngineSource.CLOUD_GEMINI,
                        timestampMillis = System.currentTimeMillis()
                    )
                    val finalMessages = currentMessagesWithUser + errReply
                    _uiState.update { state ->
                        state.copy(
                            messages = if (state.activeSessionId == sessionId) finalMessages else state.messages,
                            chatState = if (state.activeSessionId == sessionId) AiChatStreamState.Error(errMsg) else state.chatState,
                            connectionStatus = GeminiConnectionStatus.Disconnected(errMsg)
                        )
                    }
                    persistAndAutoSyncSession(sessionId, finalMessages, lang)
                }
            }
        }
    }

    private suspend fun runLocalOfflineTurn(
        mainState: MainUiState,
        sessionId: String,
        question: String,
        priorHistory: List<AiChatMessage>,
        currentMessagesWithUser: List<AiChatMessage>,
        activeAccounts: List<AccountEntity>,
        baseCategories: List<CategoryEntity>,
        merchantRules: List<com.example.data.local.entity.MerchantRuleEntity>,
        fallbackAccount: AccountEntity?,
        online: Boolean,
        lang: AppLanguage
    ) {
        val localTurn = LocalOfflineFinancialAi.executeOfflineTurn(
            mainState = mainState,
            accounts = activeAccounts,
            categories = baseCategories,
            merchantRules = merchantRules,
            fallbackAccountId = fallbackAccount?.id,
            priorHistory = priorHistory,
            userPrompt = question
        )
        if (localTurn.teachingDirectives.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                localTurn.teachingDirectives.forEach { directive ->
                    repository.learnOrUpdateMerchantRule(
                        rawKeyword = directive.keyword,
                        categoryId = directive.categoryId,
                        accountId = directive.accountId,
                        preferredType = directive.preferredType
                    )
                }
            }
        }
        val executedRecords = if (localTurn.operations.isNotEmpty() && fallbackAccount != null) {
            executeProposedOperationsInRoom(
                operations = localTurn.operations,
                activeAccounts = activeAccounts,
                baseCategories = baseCategories,
                merchantRules = merchantRules,
                fallbackAccount = fallbackAccount,
                sourceIdentifier = "Offline AI"
            )
        } else {
            emptyList()
        }

        val replyMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = localTurn.coachReply,
            executedActions = executedRecords,
            engineSource = AiEngineSource.LOCAL_OFFLINE,
            timestampMillis = System.currentTimeMillis()
        )
        val finalMessages = currentMessagesWithUser + replyMsg
        _uiState.update { state ->
            state.copy(
                messages = if (state.activeSessionId == sessionId) finalMessages else state.messages,
                chatState = if (state.activeSessionId == sessionId) AiChatStreamState.Success(localTurn.coachReply) else state.chatState,
                isDeviceOnline = online,
                connectionStatus = if (!online || state.engineMode == AiEngineMode.OFFLINE) {
                    GeminiConnectionStatus.OfflineReady(
                        if (lang == AppLanguage.EN) {
                            "Offline Local AI active"
                        } else {
                            "الذكاء المحلي يعمل بدون إنترنت"
                        }
                    )
                } else {
                    state.connectionStatus
                }
            )
        }
        persistAndAutoSyncSession(sessionId, finalMessages, lang)
    }

    private suspend fun executeProposedOperationsInRoom(
        operations: List<AiProposedOperation>,
        activeAccounts: List<AccountEntity>,
        baseCategories: List<CategoryEntity>,
        merchantRules: List<com.example.data.local.entity.MerchantRuleEntity>,
        fallbackAccount: AccountEntity?,
        sourceIdentifier: String
    ): List<AiExecutedActionRecord> = withContext(Dispatchers.IO) {
        if (operations.isEmpty() || fallbackAccount == null) return@withContext emptyList()
        val workingCategories = baseCategories.toMutableList()
        val records = mutableListOf<AiExecutedActionRecord>()
        val nowBase = System.currentTimeMillis()

        operations.take(12).forEachIndexed { idx, op ->
            val sourceAcc = activeAccounts.firstOrNull { it.id == op.sourceAccountId }
                ?: fallbackAccount
            val txTimestamp = nowBase + idx

            if (op.type == TransactionType.TRANSFER && op.targetAccountId != null && op.targetAccountId != sourceAcc.id) {
                val (txId, feeTxId) = repository.addTransferWithFeeReturningIds(
                    fromAccountId = sourceAcc.id,
                    toAccountId = op.targetAccountId,
                    amountPiasters = op.amountPiasters,
                    feePiasters = op.feePiasters,
                    description = op.description,
                    notes = op.notes,
                    timestampMillis = txTimestamp
                )
                val targetAccName = activeAccounts.firstOrNull { it.id == op.targetAccountId }?.name ?: ""
                records.add(
                    AiExecutedActionRecord(
                        transactionId = txId,
                        feeTransactionId = feeTxId,
                        type = TransactionType.TRANSFER,
                        amountPiasters = op.amountPiasters,
                        feePiasters = op.feePiasters,
                        description = op.description,
                        accountName = "${sourceAcc.name} -> $targetAccName"
                    )
                )
            } else {
                val effectiveType = if (op.type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
                var createdNewCat = false
                var resolvedCat = op.categoryId?.let { cid ->
                    workingCategories.firstOrNull { it.id == cid && it.type == effectiveType }
                }
                if (resolvedCat == null && (!op.newCategoryNameAr.isNullOrBlank() || !op.newCategoryNameEn.isNullOrBlank())) {
                    val targetAr = op.newCategoryNameAr?.trim()?.ifBlank { null }
                        ?: op.newCategoryNameEn!!.trim()
                    val targetEn = op.newCategoryNameEn?.trim()?.ifBlank { null }
                        ?: targetAr
                    resolvedCat = workingCategories.firstOrNull {
                        it.type == effectiveType &&
                            (it.nameAr.equals(targetAr, ignoreCase = true) ||
                                it.nameEn.equals(targetEn, ignoreCase = true))
                    }
                    if (resolvedCat == null) {
                        val newCatEntity = CategoryEntity(
                            nameAr = targetAr,
                            nameEn = targetEn,
                            type = effectiveType,
                            iconName = if (effectiveType == TransactionType.INCOME) "payments" else "auto_awesome",
                            colorHex = op.newCategoryColorHex ?: if (effectiveType == TransactionType.INCOME) "#22D3EE" else "#4F8CFF",
                            isDefault = false
                        )
                        val newCatId = repository.createCategory(newCatEntity)
                        resolvedCat = newCatEntity.copy(id = newCatId)
                        workingCategories.add(resolvedCat)
                        createdNewCat = true
                    }
                }
                if (resolvedCat == null) {
                    resolvedCat = VoiceTransactionParser.inferCategory(
                        description = op.description,
                        categories = workingCategories,
                        merchantRules = merchantRules,
                        type = effectiveType
                    )
                }

                val tx = TransactionEntity(
                    amountPiasters = op.amountPiasters,
                    type = effectiveType,
                    accountId = sourceAcc.id,
                    categoryId = resolvedCat?.id,
                    source = TransactionSource.MANUAL_ENTRY,
                    sourceIdentifier = sourceIdentifier,
                    description = op.description,
                    notes = op.notes,
                    timestampMillis = txTimestamp,
                    createdAt = txTimestamp,
                    updatedAt = txTimestamp
                )
                val txId = repository.addTransaction(tx)
                val cleanKey = LocalSelfLearningEngine.extractLearnableMerchantKey(op.description)
                    .ifBlank { op.description.trim() }
                if (resolvedCat != null && cleanKey.length >= 2) {
                    repository.learnOrUpdateMerchantRule(
                        rawKeyword = cleanKey,
                        categoryId = resolvedCat.id,
                        accountId = sourceAcc.id,
                        preferredType = effectiveType
                    )
                }
                records.add(
                    AiExecutedActionRecord(
                        transactionId = txId,
                        type = effectiveType,
                        amountPiasters = op.amountPiasters,
                        description = op.description,
                        categoryNameAr = resolvedCat?.nameAr,
                        categoryNameEn = resolvedCat?.nameEn,
                        categoryColorHex = resolvedCat?.colorHex,
                        accountName = sourceAcc.name,
                        createdNewCategory = createdNewCat
                    )
                )
            }
        }
        records
    }

    private suspend fun persistAndAutoSyncSession(
        sessionId: String,
        messages: List<AiChatMessage>,
        lang: AppLanguage
    ) {
        if (messages.isEmpty()) return
        val existingSession = withContext(Dispatchers.IO) {
            repository.getAiChatSessionById(sessionId)
        }
        val firstUserText = messages.firstOrNull { it.isUser }?.text?.trim().orEmpty()
        val computedTitle = when {
            existingSession != null && existingSession.title.isNotBlank() &&
                existingSession.title != "محادثة جديدة" && existingSession.title != "New Chat" -> existingSession.title
            firstUserText.isNotBlank() -> firstUserText.take(44) + if (firstUserText.length > 44) "..." else ""
            lang == AppLanguage.EN -> "Financial Coach Chat"
            else -> "محادثة مالية"
        }
        val lastPreview = messages.lastOrNull()?.text?.replace('\n', ' ')?.trim()?.take(120).orEmpty()
        val createdMillis = existingSession?.createdAtMillis
            ?: messages.minOfOrNull { it.timestampMillis }
            ?: System.currentTimeMillis()
        val updatedMillis = maxOf(
            createdMillis,
            messages.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        )
        val sessionEntity = AiChatSessionEntity(
            id = sessionId,
            title = computedTitle,
            lastMessagePreview = lastPreview,
            messageCount = messages.size,
            createdAtMillis = createdMillis,
            updatedAtMillis = updatedMillis,
            syncedToCloud = false
        )
        val messageEntities = messages.map { AiChatJsonMapper.toEntity(it, sessionId) }
        withContext(Dispatchers.IO) {
            repository.saveAiChatSessionAndMessages(sessionEntity, messageEntities)
        }
        pushSessionToCloudAsync(sessionEntity, messageEntities)
    }

    private fun pushSessionToCloudAsync(
        session: AiChatSessionEntity,
        messages: List<AiChatMessageEntity>
    ) {
        val cloudRepo = cloudSyncRepository
        if (cloudRepo == null) {
            _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.LocalOnly) }
            return
        }
        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(false)
        if (!online) {
            _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.Idle, isDeviceOnline = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.Syncing) }
            val result = withContext(Dispatchers.IO) {
                cloudRepo.syncAiChatSession(session, messages)
            }
            if (result.isSuccess) {
                withContext(Dispatchers.IO) {
                    repository.markAiChatSessionSynced(session.id, true)
                }
                _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.Synced) }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Sync deferred"
                _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.Error(err)) }
            }
        }
    }

    fun syncChatsWithCloud() {
        val cloudRepo = cloudSyncRepository
        if (cloudRepo == null) {
            _uiState.update { it.copy(cloudSyncState = CloudChatSyncState.LocalOnly) }
            return
        }
        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(false)
        if (!online) {
            _uiState.update { it.copy(isDeviceOnline = false, cloudSyncState = CloudChatSyncState.Idle) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeviceOnline = true, cloudSyncState = CloudChatSyncState.Syncing) }
            try {
                // 1. Pull remote chats & merge cleanly into Room
                val pullResult = withContext(Dispatchers.IO) { cloudRepo.pullAllAiChats() }
                if (pullResult.isSuccess) {
                    val (remoteSessions, remoteMessages) = pullResult.getOrThrow()
                    if (remoteSessions.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            repository.mergeRemoteAiChats(remoteSessions, remoteMessages)
                        }
                    }
                }
                // 2. Push any unsynced local sessions (e.g., created while offline)
                val unsyncedSessions = withContext(Dispatchers.IO) {
                    repository.getAllAiChatSessionsOnce().filter { !it.syncedToCloud }
                }
                for (session in unsyncedSessions) {
                    val msgs = withContext(Dispatchers.IO) {
                        repository.getAiChatMessagesOnce(session.id)
                    }
                    val pushRes = withContext(Dispatchers.IO) {
                        cloudRepo.syncAiChatSession(session, msgs)
                    }
                    if (pushRes.isSuccess) {
                        withContext(Dispatchers.IO) {
                            repository.markAiChatSessionSynced(session.id, true)
                        }
                    }
                }
                // 3. Refresh currently active session if new cloud messages arrived
                val currentSessionId = _uiState.value.activeSessionId
                if (currentSessionId.isNotBlank()) {
                    val refreshed = withContext(Dispatchers.IO) {
                        repository.getAiChatMessagesOnce(currentSessionId).map { AiChatJsonMapper.toDomain(it) }
                    }
                    if (refreshed.isNotEmpty() && _uiState.value.chatState !is AiChatStreamState.Streaming &&
                        _uiState.value.chatState !is AiChatStreamState.Loading
                    ) {
                        _uiState.update { state ->
                            if (state.activeSessionId == currentSessionId && refreshed.size > state.messages.size) {
                                state.copy(messages = refreshed)
                            } else state
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        cloudSyncState = if (pullResult.isSuccess) CloudChatSyncState.Synced else CloudChatSyncState.Error(
                            pullResult.exceptionOrNull()?.localizedMessage ?: "Sync error"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cloudSyncState = CloudChatSyncState.Error(e.localizedMessage ?: "Sync failed"))
                }
            }
        }
    }

    fun selectChatSession(sessionId: String) {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { repository.getAiChatSessionById(sessionId) }
            val msgs = withContext(Dispatchers.IO) {
                repository.getAiChatMessagesOnce(sessionId).map { AiChatJsonMapper.toDomain(it) }
            }
            _uiState.update {
                it.copy(
                    activeSessionId = sessionId,
                    activeSessionTitle = session?.title.orEmpty(),
                    messages = msgs,
                    chatState = AiChatStreamState.Idle
                )
            }
        }
    }

    fun createNewChat() {
        _uiState.update {
            it.copy(
                activeSessionId = UUID.randomUUID().toString(),
                activeSessionTitle = "",
                messages = emptyList(),
                chatState = AiChatStreamState.Idle
            )
        }
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteAiChatSession(sessionId)
            }
            if (_uiState.value.activeSessionId == sessionId) {
                createNewChat()
            }
            val cloudRepo = cloudSyncRepository
            if (cloudRepo != null && runCatching { connectivityChecker.isOnline() }.getOrDefault(false)) {
                withContext(Dispatchers.IO) {
                    cloudRepo.deleteAiChatSessionDoc(sessionId)
                }
            }
        }
    }

    fun generateActionPlan(mainState: MainUiState) {
        val lang = mainState.preferences.language
        val snapshot = FinancialContextFormatter.buildSnapshotPrompt(mainState)
        _uiState.update { it.copy(actionPlanState = AiActionPlanState.Loading) }

        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
        val mode = _uiState.value.engineMode
        if (mode == AiEngineMode.OFFLINE || (!online && mode != AiEngineMode.CLOUD)) {
            val offlinePlan = LocalOfflineFinancialAi.generateOfflineActionPlan(mainState, lang)
            _uiState.update { it.copy(actionPlanState = AiActionPlanState.Success(offlinePlan)) }
            return
        }

        viewModelScope.launch {
            try {
                val plan = withTimeout(AI_TIMEOUT_MS) {
                    gateway.generateActionPlan(snapshot, lang)
                }
                _uiState.update { it.copy(actionPlanState = AiActionPlanState.Success(plan)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mode == AiEngineMode.AUTO && shouldFallbackToOffline(e)) {
                    val offlinePlan = LocalOfflineFinancialAi.generateOfflineActionPlan(mainState, lang)
                    _uiState.update { it.copy(actionPlanState = AiActionPlanState.Success(offlinePlan)) }
                } else {
                    Log.e(TAG, "Failed to generate AI action plan", e)
                    val msg = if (e is TimeoutCancellationException) {
                        if (lang == AppLanguage.EN) {
                            "Action plan generation timed out after 60 seconds."
                        } else {
                            "انتهت مهلة إنشاء الخطة الذكية بعد 60 ثانية."
                        }
                    } else {
                        e.localizedMessage ?: "Failed to generate AI action plan."
                    }
                    _uiState.update { it.copy(actionPlanState = AiActionPlanState.Error(msg)) }
                }
            }
        }
    }

    fun analyzeReceiptOrMessage(
        mainState: MainUiState,
        textInput: String?,
        bitmap: Bitmap?
    ) {
        val lang = mainState.preferences.language
        if (textInput.isNullOrBlank() && bitmap == null) {
            _uiState.update {
                it.copy(
                    receiptExtractState = AiReceiptExtractState.Error(
                        if (lang == AppLanguage.EN) {
                            "Attach a receipt photo or enter transaction text first."
                        } else {
                            "أرفق صورة إيصال أو أدخل نص الرسالة أولًا."
                        }
                    )
                )
            }
            return
        }
        lastReceiptBitmap = bitmap
        lastSavedReceiptUri = null
        val snapshot = FinancialContextFormatter.buildSnapshotPrompt(mainState)
        _uiState.update { it.copy(receiptExtractState = AiReceiptExtractState.Loading) }

        val online = runCatching { connectivityChecker.isOnline() }.getOrDefault(true)
        val mode = _uiState.value.engineMode
        if (mode == AiEngineMode.OFFLINE || (!online && mode != AiEngineMode.CLOUD)) {
            viewModelScope.launch {
                try {
                    val accounts = mainState.accounts.ifEmpty {
                        withContext(Dispatchers.IO) { repository.getAllAccountsOnce() }
                    }
                    val categories = mainState.categories.ifEmpty {
                        withContext(Dispatchers.IO) { repository.getAllCategoriesOnce() }
                    }
                    val rules = mainState.merchantRules.ifEmpty {
                        withContext(Dispatchers.IO) { repository.getAllMerchantRulesOnce() }
                    }
                    val extracted = OfflineReceiptAnalyzer.analyzeOffline(
                        textInput = textInput,
                        hasBitmap = bitmap != null,
                        accounts = accounts,
                        categories = categories,
                        merchantRules = rules,
                        lang = lang
                    )
                    _uiState.update {
                        it.copy(receiptExtractState = AiReceiptExtractState.Success(extracted, saved = false))
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(receiptExtractState = AiReceiptExtractState.Error(e.localizedMessage ?: "Offline analysis error"))
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                val extracted = withTimeout(AI_TIMEOUT_MS) {
                    gateway.analyzeReceiptOrMessage(
                        financialContext = snapshot,
                        textInput = textInput,
                        bitmap = bitmap,
                        language = lang
                    )
                }
                _uiState.update {
                    it.copy(receiptExtractState = AiReceiptExtractState.Success(extracted, saved = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mode == AiEngineMode.AUTO && shouldFallbackToOffline(e)) {
                    runCatching {
                        val accounts = mainState.accounts.ifEmpty {
                            withContext(Dispatchers.IO) { repository.getAllAccountsOnce() }
                        }
                        val categories = mainState.categories.ifEmpty {
                            withContext(Dispatchers.IO) { repository.getAllCategoriesOnce() }
                        }
                        val rules = mainState.merchantRules.ifEmpty {
                            withContext(Dispatchers.IO) { repository.getAllMerchantRulesOnce() }
                        }
                        OfflineReceiptAnalyzer.analyzeOffline(
                            textInput = textInput,
                            hasBitmap = bitmap != null,
                            accounts = accounts,
                            categories = categories,
                            merchantRules = rules,
                            lang = lang
                        )
                    }.onSuccess { extracted ->
                        _uiState.update {
                            it.copy(receiptExtractState = AiReceiptExtractState.Success(extracted, saved = false))
                        }
                        return@launch
                    }
                }
                Log.e(TAG, "Receipt analysis failed", e)
                val msg = if (e is TimeoutCancellationException) {
                    if (lang == AppLanguage.EN) {
                        "Analysis timed out after 60 seconds. Please try again."
                    } else {
                        "انتهت مهلة تحليل الإيصال بعد 60 ثانية."
                    }
                } else {
                    e.localizedMessage ?: "Failed to analyze receipt."
                }
                _uiState.update { it.copy(receiptExtractState = AiReceiptExtractState.Error(msg)) }
            }
        }
    }

    private suspend fun ensureReceiptUriPersisted(): String? {
        if (lastSavedReceiptUri != null) return lastSavedReceiptUri
        val bmp = lastReceiptBitmap ?: return null
        val ctx = appContext ?: return null
        val persisted = ReceiptStorageManager.persistReceiptBitmap(ctx, bmp)
        if (persisted != null) lastSavedReceiptUri = persisted
        return persisted
    }

    fun saveExtractedTransaction(
        mainState: MainUiState,
        extracted: AiExtractedTransaction,
        splitWays: Int = 1
    ) {
        val accountId = mainState.accounts.firstOrNull { !it.isArchived }?.id ?: return
        val matchedCategoryId = matchCategoryId(mainState, extracted.categoryHint, extracted.description, extracted.type)
        val ways = splitWays.coerceIn(1, 20)
        val finalAmount = (extracted.amountPiasters / ways.toLong()).coerceAtLeast(1L)
        val splitNote = if (ways > 1) {
            val lang = mainState.preferences.language
            val tag = if (lang == AppLanguage.EN) "Split share 1/$ways" else "حصة مقسمة (1/$ways)"
            listOf(extracted.notes, tag).filter { it.isNotBlank() }.joinToString(" • ")
        } else {
            extracted.notes
        }

        viewModelScope.launch {
            val receiptUri = ensureReceiptUriPersisted()
            repository.addTransaction(
                TransactionEntity(
                    amountPiasters = finalAmount,
                    type = extracted.type,
                    accountId = accountId,
                    categoryId = matchedCategoryId,
                    description = extracted.description,
                    notes = splitNote,
                    attachmentUri = receiptUri
                )
            )
            _uiState.update { current ->
                val state = current.receiptExtractState
                if (state is AiReceiptExtractState.Success) {
                    current.copy(receiptExtractState = state.copy(saved = true))
                } else {
                    current
                }
            }
        }
    }

    fun saveExtractedLineItems(
        mainState: MainUiState,
        extracted: AiExtractedTransaction,
        splitWays: Int = 1
    ) {
        val accountId = mainState.accounts.firstOrNull { !it.isArchived }?.id ?: return
        val lineItems = extracted.items
        if (lineItems.isEmpty()) {
            saveExtractedTransaction(mainState, extracted, splitWays)
            return
        }
        val ways = splitWays.coerceIn(1, 20)
        val lang = mainState.preferences.language

        viewModelScope.launch {
            val receiptUri = ensureReceiptUriPersisted()
            withContext(Dispatchers.IO) {
                lineItems.forEach { item ->
                    val finalAmt = (item.amountPiasters / ways.toLong()).coerceAtLeast(1L)
                    val catId = matchCategoryId(mainState, item.categoryHint, item.description, extracted.type)
                    val note = if (ways > 1) {
                        if (lang == AppLanguage.EN) "Receipt item • Share 1/$ways" else "بند فاتورة • حصة 1/$ways"
                    } else {
                        if (lang == AppLanguage.EN) "Receipt itemized breakdown" else "تفصيل فاتورة ذكي"
                    }
                    repository.addTransaction(
                        TransactionEntity(
                            amountPiasters = finalAmt,
                            type = extracted.type,
                            accountId = accountId,
                            categoryId = catId,
                            description = item.description,
                            notes = note,
                            attachmentUri = receiptUri
                        )
                    )
                }
            }
            _uiState.update { current ->
                val state = current.receiptExtractState
                if (state is AiReceiptExtractState.Success) {
                    current.copy(receiptExtractState = state.copy(saved = true))
                } else {
                    current
                }
            }
        }
    }

    private fun matchCategoryId(
        mainState: MainUiState,
        categoryHint: String,
        description: String,
        type: TransactionType
    ): Long? {
        val hint = categoryHint.trim().lowercase()
        if (hint.isNotBlank()) {
            val direct = mainState.categories.firstOrNull { cat ->
                cat.nameEn.lowercase().contains(hint) ||
                    cat.nameAr.contains(categoryHint.trim()) ||
                    hint.contains(cat.nameEn.lowercase())
            }
            if (direct != null) return direct.id
        }
        return com.example.domain.engine.VoiceTransactionParser.inferCategory(
            description = "$categoryHint $description",
            categories = mainState.categories,
            merchantRules = mainState.merchantRules,
            type = type
        )?.id
    }

    fun undoExecutedMessageActions(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        if (targetMsg.isUndone || targetMsg.executedActions.isEmpty()) return
        val sessionId = _uiState.value.activeSessionId

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val allTransactions = repository.getAllTransactionsOnce().associateBy { it.id }
                targetMsg.executedActions.forEach { rec ->
                    allTransactions[rec.transactionId]?.let { repository.deleteTransaction(it) }
                    rec.feeTransactionId?.let { feeId ->
                        allTransactions[feeId]?.let { repository.deleteTransaction(it) }
                    }
                }
                if (sessionId.isNotBlank()) {
                    repository.markAiMessageUndone(messageId, sessionId)
                }
            }
            _uiState.update { current ->
                current.copy(
                    messages = current.messages.map { msg ->
                        if (msg.id == messageId) msg.copy(isUndone = true) else msg
                    }
                )
            }
            if (sessionId.isNotBlank()) {
                val updatedSession = withContext(Dispatchers.IO) { repository.getAiChatSessionById(sessionId) }
                val updatedMsgs = withContext(Dispatchers.IO) { repository.getAiChatMessagesOnce(sessionId) }
                if (updatedSession != null && updatedMsgs.isNotEmpty()) {
                    pushSessionToCloudAsync(updatedSession, updatedMsgs)
                }
            }
        }
    }

    fun clearChat() {
        createNewChat()
    }

    private fun shouldFallbackToOffline(error: Throwable): Boolean {
        if (!runCatching { connectivityChecker.isOnline() }.getOrDefault(true)) return true
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur is TimeoutCancellationException ||
                cur is UnknownHostException ||
                cur is ConnectException ||
                cur is NoRouteToHostException ||
                cur is SocketTimeoutException ||
                cur is FirebaseNetworkException
            ) {
                return true
            }
            val msg = cur.message?.lowercase().orEmpty()
            if (msg.contains("unable to resolve host") ||
                msg.contains("failed to connect") ||
                msg.contains("network is unreachable") ||
                msg.contains("connection abort") ||
                msg.contains("no address associated with hostname") ||
                msg.contains("timed out")
            ) {
                return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    companion object {
        private val NUMBER_REGEX = Regex("""\d|[\u0660-\u0669]""")
        private val ACTION_VERB_REGEX = Regex(
            """(صرفت|دفعت|اشتريت|شريت|استلمت|قبضت|حولت|سحبت|أضف|اضف|سجل|spent|paid|bought|received|transferred|log\b|record\b|add\b)""",
            RegexOption.IGNORE_CASE
        )

        fun hasTransactionLoggingIntent(prompt: String): Boolean {
            val trimmed = prompt.trim()
            if (trimmed.isEmpty()) return false
            val hasDigit = NUMBER_REGEX.containsMatchIn(trimmed)
            val hasActionVerb = ACTION_VERB_REGEX.containsMatchIn(trimmed)
            return hasDigit && hasActionVerb
        }
    }

    class Factory(
        private val repository: MoneyRepository,
        private val gateway: FinancialAiGateway = FirebaseGeminiFinancialGateway(),
        private val connectivityChecker: NetworkConnectivityChecker = NetworkConnectivityChecker { true },
        private val cloudSyncRepository: CloudSyncRepository? = null,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiAdvisorViewModel(
                repository = repository,
                gateway = gateway,
                connectivityChecker = connectivityChecker,
                cloudSyncRepository = cloudSyncRepository,
                appContext = appContext
            ) as T
        }
    }
}
