package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiEngineSource
import com.example.data.ai.AiExecutedActionRecord
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.FinancialContextFormatter
import com.example.data.local.entity.AiChatSessionEntity
import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.components.rememberAiTtsPlayer
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalThemePresetSpec
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.AiActionPlanState
import com.example.ui.viewmodel.AiAdvisorUiState
import com.example.ui.viewmodel.AiChatStreamState
import com.example.ui.viewmodel.AiEngineMode
import com.example.ui.viewmodel.AiReceiptExtractState
import com.example.ui.viewmodel.CloudChatSyncState
import com.example.ui.viewmodel.GeminiConnectionStatus
import com.example.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAdvisorScreen(
    mainState: MainUiState,
    aiState: AiAdvisorUiState,
    onSendPrompt: (String) -> Unit,
    onGeneratePlan: () -> Unit,
    onAnalyzeReceipt: (String?, Bitmap?) -> Unit,
    onSaveExtractedTx: (AiExtractedTransaction) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryLastPrompt: () -> Unit = {},
    onRetryOfflinePrompt: () -> Unit = {},
    onUndoActions: (String) -> Unit = {},
    onVerifyConnection: () -> Unit = {},
    onSetEngineMode: (AiEngineMode) -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onNewChat: () -> Unit = onClearChat,
    onDeleteSession: (String) -> Unit = {},
    onSyncChats: () -> Unit = {},
    onSaveExtractedTxSplit: (AiExtractedTransaction, Int) -> Unit = { tx, _ -> onSaveExtractedTx(tx) },
    onSaveExtractedItems: (AiExtractedTransaction, Int) -> Unit = { tx, _ -> onSaveExtractedTx(tx) }
) {
    val lang = mainState.preferences.language
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onVerifyConnection()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val prismColors = LocalThemePresetSpec.current.prismGlowColors
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, Brush.linearGradient(prismColors), CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hydrogen_ai_core_1794844129687),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Column {
                        Text(
                            text = if (aiState.activeSessionTitle.isNotBlank() && aiState.messages.isNotEmpty()) {
                                aiState.activeSessionTitle
                            } else {
                                AppStrings.text("المساعد والمنسق الذكي", "AI Organizer & Coach", lang)
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = AppStrings.text(
                                "أوفلاين فوري + Gemini 4 Hydrogen + سجل سحابي",
                                "Instant Offline AI + Gemini 4 Hydrogen + Cloud Sync",
                                lang
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("ai_back_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = AppStrings.text("رجوع", "Back", lang)
                    )
                }
            },
            actions = {
                if (selectedTab == 0) {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("ai_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = AppStrings.text("سجل المحادثات", "Chat history", lang)
                        )
                    }
                    if (aiState.messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("ai_clear_chat_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = AppStrings.text("محادثة جديدة", "New chat", lang)
                            )
                        }
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                Triple(0, Icons.Rounded.Psychology, AppStrings.text("المدرب الذكي", "AI Coach", lang)),
                Triple(1, Icons.Rounded.Route, AppStrings.text("خطة العمل", "Action Plan", lang)),
                Triple(2, Icons.Rounded.DocumentScanner, AppStrings.text("فحص إيصال", "Receipt AI", lang))
            )
            tabs.forEach { (idx, icon, label) ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("ai_tab_$idx")
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState >= initialState
                (slideInHorizontally(tween(150, easing = FastOutSlowInEasing)) { if (forward) it / 12 else -it / 12 } +
                    fadeIn(tween(130))) togetherWith
                    (slideOutHorizontally(tween(120)) { if (forward) -it / 14 else it / 14 } + fadeOut(tween(100)))
            },
            label = "aiAdvisorTabs",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { tab ->
            when (tab) {
                0 -> AiCoachTab(
                    mainState = mainState,
                    aiState = aiState,
                    onSendPrompt = onSendPrompt,
                    onRetryLastPrompt = onRetryLastPrompt,
                    onRetryOfflinePrompt = onRetryOfflinePrompt,
                    onUndoActions = onUndoActions,
                    onVerifyConnection = onVerifyConnection,
                    onSetEngineMode = onSetEngineMode,
                    onSelectSession = onSelectSession,
                    onOpenHistory = { showHistorySheet = true },
                    onOpenActionPlan = {
                        selectedTab = 1
                        if (aiState.actionPlanState is AiActionPlanState.Idle) {
                            onGeneratePlan()
                        }
                    }
                )
                1 -> AiActionPlanTab(
                    lang = lang,
                    planState = aiState.actionPlanState,
                    onGeneratePlan = onGeneratePlan
                )
                else -> AiReceiptExtractorTab(
                    mainState = mainState,
                    extractState = aiState.receiptExtractState,
                    onAnalyzeReceipt = onAnalyzeReceipt,
                    onSaveExtractedTxSplit = onSaveExtractedTxSplit,
                    onSaveExtractedItems = onSaveExtractedItems
                )
            }
        }
    }

    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            modifier = Modifier.testTag("ai_history_sheet")
        ) {
            AiChatHistorySheetContent(
                lang = lang,
                sessions = aiState.sessions,
                activeSessionId = aiState.activeSessionId,
                cloudSyncState = aiState.cloudSyncState,
                onNewChat = {
                    onNewChat()
                    showHistorySheet = false
                },
                onSelectSession = { sessionId ->
                    onSelectSession(sessionId)
                    showHistorySheet = false
                },
                onDeleteSession = onDeleteSession,
                onSyncNow = onSyncChats
            )
        }
    }
}

@Composable
private fun AiCoachTab(
    mainState: MainUiState,
    aiState: AiAdvisorUiState,
    onSendPrompt: (String) -> Unit,
    onRetryLastPrompt: () -> Unit,
    onRetryOfflinePrompt: () -> Unit,
    onUndoActions: (String) -> Unit,
    onVerifyConnection: () -> Unit,
    onSetEngineMode: (AiEngineMode) -> Unit,
    onSelectSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenActionPlan: () -> Unit
) {
    val lang = mainState.preferences.language
    val currency = mainState.preferences.currencyCode
    val hide = mainState.preferences.hideAmounts
    val context = LocalContext.current
    val positiveColor = semanticPositiveColor()
    var promptText by rememberSaveable { mutableStateOf("") }
    var showRoomDetails by rememberSaveable { mutableStateOf(false) }
    var copiedMsgId by remember { mutableStateOf<String?>(null) }
    var autoSpeakReplies by rememberSaveable { mutableStateOf(false) }
    var lastAutoSpokenMsgId by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    val ttsPlayer = rememberAiTtsPlayer()
    val listState = rememberLazyListState()
    val isBusy = aiState.chatState is AiChatStreamState.Loading || aiState.chatState is AiChatStreamState.Streaming
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank() && !isBusy) {
                voiceNotice = null
                onSendPrompt(spoken)
            }
        }
    }

    val highlights = remember(
        mainState.transactions,
        mainState.accounts,
        mainState.dashboardMetrics,
        mainState.budgetEvaluations,
        mainState.bnplReport
    ) {
        FinancialContextFormatter.extractHabitHighlights(mainState)
    }

    val topCatName = remember(highlights, lang) {
        if (lang == AppLanguage.EN) highlights.topCategoryNameEn else highlights.topCategoryNameAr
    }

    val quickPrompts = remember(lang, topCatName, highlights.hasDeficitRisk, mainState.installments.size) {
        if (lang == AppLanguage.EN) {
            buildList {
                add("Spent 50 food, 20 transport, and 70 online order")
                if (!topCatName.isNullOrBlank()) {
                    add("Why is my $topCatName spending high and how can I cut it?")
                } else {
                    add("Where is most of my money going lately?")
                }
                add("Calculate 20% of 5000")
                if (highlights.hasDeficitRisk || mainState.installments.isNotEmpty()) {
                    add("Can my income cover upcoming installments & budgets?")
                } else {
                    add("Give me a weekly Safe-to-Spend savings plan")
                }
            }
        } else {
            buildList {
                add("صرفت النهاردة 50 جنيه اكل و 20 مواصلات و 70 طلب اونلاين")
                if (!topCatName.isNullOrBlank()) {
                    add("ليه مصاريف $topCatName عالية وإزاي أقللها؟")
                } else {
                    add("فلوسي بتروح فين أكتر الفترة دي؟")
                }
                add("احسب 20% من 5000")
                if (highlights.hasDeficitRisk || mainState.installments.isNotEmpty()) {
                    add("هل دخلي يكفي الأقساط والميزانيات الجاية؟")
                } else {
                    add("اعمل لي خطة توفير أسبوعية حسب مصروفاتي")
                }
            }
        }
    }

    LaunchedEffect(aiState.messages.size) {
        if (aiState.messages.isNotEmpty() && showRoomDetails) {
            showRoomDetails = false
        }
    }

    val isLoadingOrStreaming = aiState.chatState is AiChatStreamState.Loading || aiState.chatState is AiChatStreamState.Streaming
    LaunchedEffect(aiState.messages.size, aiState.messages.lastOrNull()?.text, isLoadingOrStreaming) {
        if (aiState.messages.isNotEmpty()) {
            val targetIdx = if (isLoadingOrStreaming) aiState.messages.size else aiState.messages.lastIndex
            listState.scrollToItem(targetIdx.coerceAtLeast(0))
        }
    }

    LaunchedEffect(aiState.messages.lastOrNull()?.id, isLoadingOrStreaming, autoSpeakReplies) {
        val last = aiState.messages.lastOrNull()
        if (autoSpeakReplies && !isLoadingOrStreaming && last != null && !last.isUser && !last.isError &&
            last.text.isNotBlank() && last.id != lastAutoSpokenMsgId
        ) {
            lastAutoSpokenMsgId = last.id
            ttsPlayer.speak(last.id, last.text, lang)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Hybrid Connection, Engine Mode & Cloud Sync Status Strip
        GeminiConnectionStatusPill(
            status = aiState.connectionStatus,
            engineMode = aiState.engineMode,
            cloudSyncState = aiState.cloudSyncState,
            lang = lang,
            onVerifyClick = onVerifyConnection,
            onCycleEngineMode = {
                val nextMode = when (aiState.engineMode) {
                    AiEngineMode.AUTO -> AiEngineMode.OFFLINE
                    AiEngineMode.OFFLINE -> AiEngineMode.CLOUD
                    AiEngineMode.CLOUD -> AiEngineMode.AUTO
                }
                onSetEngineMode(nextMode)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Self-Learning Local Brain Pill (live rule count & statistical Naive-Bayes memory)
        val memoryStats = remember(mainState.merchantRules, mainState.transactions) {
            LocalSelfLearningEngine.computeMemoryStats(mainState.merchantRules, mainState.transactions)
        }
        Surface(
            color = SkyCyan.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.75.dp, SkyCyan.copy(alpha = 0.30f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !isBusy) {
                    onSendPrompt(AppStrings.text("إيه اللي اتعلمته؟", "What have you learned?", lang))
                }
                .testTag("ai_local_memory_pill")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = SkyCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = AppStrings.text(
                            "الذاكرة الذاتية (${memoryStats.adaptiveTierAr}): ${memoryStats.learnedRuleCount} قاعدة · ${memoryStats.indexedTransactionCount} معاملة",
                            "Self-Learning (${memoryStats.adaptiveTierEn}): ${memoryStats.learnedRuleCount} rules · ${memoryStats.indexedTransactionCount} tx",
                            lang
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = AppStrings.text("استعراض", "Inspect", lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Compact Room DB Grounding Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("ai_room_context_banner")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(positiveColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = positiveColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column {
                            Text(
                                text = AppStrings.text(
                                    "متصل ببياناتك (${highlights.totalTransactionsCount} معاملة) · صحة ${mainState.healthReport.score}/100",
                                    "Room DB (${highlights.totalTransactionsCount} tx) · Health ${mainState.healthReport.score}/100",
                                    lang
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = AppStrings.text("المسموح اليوم: ", "Safe today: ", lang) +
                                    MoneyFormatter.formatPiasters(
                                        mainState.healthReport.safeToSpendTodayPiasters,
                                        currency,
                                        lang,
                                        hide
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                autoSpeakReplies = !autoSpeakReplies
                                if (!autoSpeakReplies) ttsPlayer.stop()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("ai_tts_auto_toggle")
                        ) {
                            Icon(
                                imageVector = if (autoSpeakReplies) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = AppStrings.text(
                                    "قراءة الردود صوتيًا تلقائيًا",
                                    "Toggle auto-speak replies",
                                    lang
                                ),
                                tint = if (autoSpeakReplies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showRoomDetails = !showRoomDetails },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("ai_toggle_room_preview_btn")
                        ) {
                            Icon(
                                imageVector = if (showRoomDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = AppStrings.text(
                                    "عرض تفاصيل البيانات المقروءة",
                                    "Toggle Room snapshot details",
                                    lang
                                )
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showRoomDetails,
                    enter = expandVertically(tween(160)) + fadeIn(tween(140)),
                    exit = shrinkVertically(tween(140)) + fadeOut(tween(120))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        if (!topCatName.isNullOrBlank()) {
                            Text(
                                text = AppStrings.text(
                                    "أعلى فئة صرف: $topCatName (${MoneyFormatter.formatPiasters(highlights.topCategoryAmountPiasters, currency, lang, hide)} - ${highlights.topCategorySharePercent.roundToInt()}%)",
                                    "Top spending category: $topCatName (${MoneyFormatter.formatPiasters(highlights.topCategoryAmountPiasters, currency, lang, hide)} · ${highlights.topCategorySharePercent.roundToInt()}%)",
                                    lang
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = SkyCyan
                            )
                        }
                        if (highlights.topMerchants.isNotEmpty()) {
                            val topMerchantsText = highlights.topMerchants.take(3).joinToString(" · ") { m ->
                                "${m.label} (${m.txCount}x)"
                            }
                            Text(
                                text = AppStrings.text(
                                    "أكثر العادات تكرارًا: $topMerchantsText",
                                    "Top spending habits: $topMerchantsText",
                                    lang
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val alertsText = when {
                                highlights.exceededBudgetsCount > 0 -> AppStrings.text(
                                    "${highlights.exceededBudgetsCount} ميزانية متجاوزة",
                                    "${highlights.exceededBudgetsCount} budget exceeded",
                                    lang
                                )
                                highlights.warningBudgetsCount > 0 -> AppStrings.text(
                                    "${highlights.warningBudgetsCount} ميزانية قريبة من الحد",
                                    "${highlights.warningBudgetsCount} budget near limit",
                                    lang
                                )
                                else -> AppStrings.text(
                                    "${highlights.activeAccountsCount} حساب نشط متصل",
                                    "${highlights.activeAccountsCount} active accounts synced",
                                    lang
                                )
                            }
                            Text(
                                text = alertsText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (highlights.exceededBudgetsCount > 0) CoralExpense else positiveColor
                            )
                            TextButton(
                                onClick = onOpenActionPlan,
                                modifier = Modifier.testTag("ai_open_action_plan_btn")
                            ) {
                                Text(AppStrings.text("إنشاء خطة عمل ←", "Action Plan →", lang))
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (aiState.messages.isEmpty()) {
                item(key = "coach_intro_card") {
                    val introBorder = Brush.linearGradient(LocalThemePresetSpec.current.prismGlowColors.map { it.copy(alpha = 0.45f) })
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, introBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                                        )
                                    )
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_hydrogen_ai_core_1794844129687),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, introBorder, CircleShape)
                                )
                                Text(
                                    text = AppStrings.text(
                                        "مساعد مالي محلي وسحابي يعمل معك دائمًا",
                                        "Hybrid Offline + Cloud Financial Coach",
                                        lang
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = AppStrings.text(
                                    "• بدون إنترنت: يسجل المصاريف المركبة مثل «صرفت 50 اكل و 20 مواصلات»، يحسب أي معادلة أو نسبة، ويجيبك من قاعدة بياناتك.\n• مع الإنترنت (Gemini 4 Hydrogen): حوار مفتوح أعمق ومزامنة تلقائية لسجل المحادثات على السحابة.",
                                    "• Offline: Instantly logs compound transactions, solves calculations & percentages, and answers from your Room data.\n• Online (Gemini 4 Hydrogen): Rich extended conversations with automatic cloud chat history sync.",
                                    lang
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (aiState.sessions.isNotEmpty()) {
                    item(key = "recent_sessions_card") {
                        FinancialCard(borderColor = SkyCyan.copy(alpha = 0.35f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.text("محادثاتك السابقة (${aiState.sessions.size})", "Recent Saved Chats (${aiState.sessions.size})", lang),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyCyan
                                    )
                                    TextButton(onClick = onOpenHistory) {
                                        Text(AppStrings.text("عرض الكل", "View All", lang))
                                    }
                                }
                                aiState.sessions.take(2).forEach { session ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                            .clickable { onSelectSession(session.id) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (session.lastMessagePreview.isNotBlank()) {
                                                Text(
                                                    text = session.lastMessagePreview,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (session.syncedToCloud) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            tint = if (session.syncedToCloud) positiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(aiState.messages, key = { it.id }) { msg ->
                val isOfflineMsg = msg.engineSource == AiEngineSource.LOCAL_OFFLINE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = when {
                            msg.isError -> CoralExpense.copy(alpha = 0.14f)
                            msg.isUser -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (!msg.isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                msg.isError -> Icons.Rounded.ErrorOutline
                                                isOfflineMsg -> Icons.Rounded.OfflineBolt
                                                else -> Icons.Rounded.AutoAwesome
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                msg.isError -> CoralExpense
                                                isOfflineMsg -> SkyCyan
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = when {
                                                msg.isError -> AppStrings.text("تنبيه", "Notice", lang)
                                                isOfflineMsg -> AppStrings.text("ذكاء محلي (بدون نت)", "Offline Local AI", lang)
                                                else -> AppStrings.text("Gemini 4 Hydrogen", "Gemini 4 Hydrogen", lang)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                msg.isError -> CoralExpense
                                                isOfflineMsg -> SkyCyan
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(msg.timestampMillis)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!msg.isError) {
                                        val isSpeaking = ttsPlayer.speakingMessageId == msg.id
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { ttsPlayer.toggleSpeak(msg.id, msg.text, lang) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("ai_speak_msg_${msg.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                                                    contentDescription = AppStrings.text("استماع للرد", "Read reply aloud", lang),
                                                    tint = if (isSpeaking) SkyCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("AI Coach Advice", msg.text))
                                                    copiedMsgId = msg.id
                                                },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("ai_copy_msg_btn")
                                            ) {
                                                Icon(
                                                    imageVector = if (copiedMsgId == msg.id) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy,
                                                    contentDescription = AppStrings.text("نسخ النص", "Copy advice", lang),
                                                    tint = if (copiedMsgId == msg.id) positiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.isError) CoralExpense else MaterialTheme.colorScheme.onSurface
                            )

                            if (msg.executedActions.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                ExecutedActionsReceiptCard(
                                    messageId = msg.id,
                                    actions = msg.executedActions,
                                    isUndone = msg.isUndone,
                                    currency = currency,
                                    lang = lang,
                                    hideAmounts = hide,
                                    onUndo = { onUndoActions(msg.id) }
                                )
                            }

                            if (msg.isError) {
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = onRetryLastPrompt,
                                        enabled = !isBusy,
                                        modifier = Modifier.testTag("ai_retry_btn")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(AppStrings.text("إعادة المحاولة", "Retry", lang))
                                    }
                                    TextButton(
                                        onClick = onRetryOfflinePrompt,
                                        enabled = !isBusy,
                                        modifier = Modifier.testTag("ai_fallback_offline_btn")
                                    ) {
                                        Icon(Icons.Rounded.OfflineBolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = SkyCyan)
                                        Spacer(Modifier.width(4.dp))
                                        Text(AppStrings.text("رد محلي فوري", "Answer Offline", lang), color = SkyCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (aiState.chatState is AiChatStreamState.Streaming) {
                item(key = "streaming_bubble") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = AppStrings.text("Gemini 4 Hydrogen يكتب...", "Gemini 4 Hydrogen is writing...", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = aiState.chatState.partialText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else if (aiState.chatState is AiChatStreamState.Loading) {
                item(key = "loading_bubble") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (aiState.engineMode == AiEngineMode.OFFLINE || !aiState.isDeviceOnline) {
                                AppStrings.text("جارٍ الحساب والمعالجة محليًا...", "Processing locally offline...", lang)
                            } else {
                                AppStrings.text("جارٍ تحليل معاملاتك في قاعدة البيانات...", "Analyzing your Room spending habits...", lang)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Context-Aware Natural Language Suggestion Chips (shown before conversation starts to keep active chat spacious)
        AnimatedVisibility(visible = aiState.messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, suggestion ->
                    AssistChip(
                        onClick = { if (!isBusy) onSendPrompt(suggestion) },
                        label = { Text(suggestion, maxLines = 1) },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("ai_quick_prompt_$idx")
                    )
                }
            }
        }

        voiceNotice?.let { errText ->
            Text(
                text = errText,
                style = MaterialTheme.typography.labelSmall,
                color = CoralExpense,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }

        // Prompt Input Row with Direct Voice-to-Agent Mic & Send
        val canSend = promptText.isNotBlank() && !isBusy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                shape = RoundedCornerShape(24.dp),
                placeholder = {
                    Text(
                        AppStrings.text(
                            "مثال: صرفت 50 أكل و 20 مواصلات، أو احسب 20% من 5000...",
                            "e.g. Spent 50 food & 20 transport, or ask Coach...",
                            lang
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("ai_prompt_input")
            )
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == AppLanguage.AR) "ar-EG" else "en-US")
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            AppStrings.text("تحدث بأمرك المالي الآن...", "Speak your financial command...", lang)
                        )
                    }
                    runCatching { voiceLauncher.launch(intent) }
                        .onFailure {
                            voiceNotice = AppStrings.text(
                                "الإدخال الصوتي غير متوفر على هذا الجهاز.",
                                "Voice input is unavailable on this device.",
                                lang
                            )
                        }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f), CircleShape)
                    .testTag("ai_voice_mic_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = AppStrings.text("أمر صوتي مباشر", "Direct voice command", lang),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(
                onClick = {
                    val q = promptText.trim()
                    if (q.isNotEmpty() && !isBusy) {
                        promptText = ""
                        onSendPrompt(q)
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("ai_send_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = AppStrings.text("إرسال", "Send", lang),
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GeminiConnectionStatusPill(
    status: GeminiConnectionStatus,
    engineMode: AiEngineMode,
    cloudSyncState: CloudChatSyncState,
    lang: AppLanguage,
    onVerifyClick: () -> Unit,
    onCycleEngineMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positiveColor = semanticPositiveColor()
    val bgColor = when (status) {
        is GeminiConnectionStatus.Checking -> MaterialTheme.colorScheme.surfaceVariant
        is GeminiConnectionStatus.Connected -> positiveColor.copy(alpha = 0.13f)
        is GeminiConnectionStatus.OfflineReady -> SkyCyan.copy(alpha = 0.12f)
        is GeminiConnectionStatus.Disconnected -> CoralExpense.copy(alpha = 0.12f)
    }
    val borderColor = when (status) {
        is GeminiConnectionStatus.Checking -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        is GeminiConnectionStatus.Connected -> positiveColor.copy(alpha = 0.42f)
        is GeminiConnectionStatus.OfflineReady -> SkyCyan.copy(alpha = 0.38f)
        is GeminiConnectionStatus.Disconnected -> CoralExpense.copy(alpha = 0.40f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.75.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_connection_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (status) {
                    is GeminiConnectionStatus.Checking -> {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Text(
                            text = AppStrings.text("فحص جاهزية Gemini 4 Hydrogen...", "Checking Gemini readiness...", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is GeminiConnectionStatus.Connected -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(positiveColor)
                        )
                        Text(
                            text = AppStrings.text(
                                "متصل · ${status.modelName} (${status.latencyMs}ms)",
                                "Online · ${status.modelName} (${status.latencyMs}ms)",
                                lang
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = positiveColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is GeminiConnectionStatus.OfflineReady -> {
                        Icon(
                            imageVector = Icons.Rounded.OfflineBolt,
                            contentDescription = null,
                            tint = SkyCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = status.reason,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SkyCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is GeminiConnectionStatus.Disconnected -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = CoralExpense,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = status.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = CoralExpense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val syncIcon = when (cloudSyncState) {
                    is CloudChatSyncState.Synced -> Icons.Rounded.CloudDone
                    is CloudChatSyncState.Syncing -> Icons.Rounded.CloudSync
                    else -> Icons.Rounded.CloudOff
                }
                val syncTint = when (cloudSyncState) {
                    is CloudChatSyncState.Synced -> positiveColor
                    is CloudChatSyncState.Syncing -> SkyCyan
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = syncIcon,
                    contentDescription = AppStrings.text("حالة مزامنة الشات", "Chat cloud sync state", lang),
                    tint = syncTint,
                    modifier = Modifier.size(14.dp)
                )

                val modeLabel = when (engineMode) {
                    AiEngineMode.AUTO -> AppStrings.text("تلقائي", "Auto", lang)
                    AiEngineMode.OFFLINE -> AppStrings.text("أوفلاين", "Offline", lang)
                    AiEngineMode.CLOUD -> AppStrings.text("سحابي", "Cloud", lang)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCycleEngineMode)
                        .testTag("ai_engine_mode_btn")
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (status is GeminiConnectionStatus.Disconnected) {
                    TextButton(
                        onClick = onVerifyClick,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp).testTag("ai_verify_connection_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = CoralExpense
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatHistorySheetContent(
    lang: AppLanguage,
    sessions: List<AiChatSessionEntity>,
    activeSessionId: String,
    cloudSyncState: CloudChatSyncState,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSyncNow: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = AppStrings.text("سجل المحادثات", "Chat History", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val syncStatusText = when (cloudSyncState) {
                    is CloudChatSyncState.Synced -> AppStrings.text("متزامن تلقائيًا مع السحابة", "Auto-synced to Cloud Firestore", lang)
                    is CloudChatSyncState.Syncing -> AppStrings.text("جارٍ المزامنة السحابية...", "Syncing with Cloud...", lang)
                    is CloudChatSyncState.LocalOnly -> AppStrings.text("محفوظ محليًا — سجّل دخولك للمزامنة السحابية", "Saved locally — Sign in for Cloud sync", lang)
                    is CloudChatSyncState.Idle -> AppStrings.text("محفوظ محليًا وجاهز للمزامنة", "Saved locally & queued for Cloud sync", lang)
                    is CloudChatSyncState.Error -> AppStrings.text("محفوظ محليًا (تعذرت المزامنة مؤقتًا)", "Saved locally (Cloud sync pending)", lang)
                }
                Text(
                    text = syncStatusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (cloudSyncState !is CloudChatSyncState.LocalOnly) {
                    IconButton(
                        onClick = onSyncNow,
                        modifier = Modifier.testTag("ai_sync_chats_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = AppStrings.text("مزامنة الآن", "Sync chats", lang),
                            tint = SkyCyan
                        )
                    }
                }
                Button(
                    onClick = onNewChat,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("ai_sheet_new_chat_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.text("جديدة", "New", lang))
                }
            }
        }

        HorizontalDivider()

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = AppStrings.text("لا توجد محادثات محفوظة بعد.", "No saved conversations yet.", lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == activeSessionId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectSession(session.id) }
                            .testTag("ai_session_item_${session.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = if (session.syncedToCloud) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        tint = if (session.syncedToCloud) semanticPositiveColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (session.lastMessagePreview.isNotBlank()) {
                                    Text(
                                        text = session.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${session.messageCount} ${AppStrings.text("رسالة", "msgs", lang)} · ${dateFmt.format(Date(session.updatedAtMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = AppStrings.text("حذف المحادثة", "Delete chat", lang),
                                    tint = CoralExpense,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutedActionsReceiptCard(
    messageId: String,
    actions: List<AiExecutedActionRecord>,
    isUndone: Boolean,
    currency: String,
    lang: AppLanguage,
    hideAmounts: Boolean,
    onUndo: () -> Unit
) {
    val positiveColor = semanticPositiveColor()
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isUndone) 0.55f else 0.94f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_executed_actions_$messageId")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (isUndone) MaterialTheme.colorScheme.onSurfaceVariant else positiveColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isUndone) {
                            AppStrings.text("تم التراجع عن العمليات (${actions.size})", "Undone (${actions.size})", lang)
                        } else {
                            AppStrings.text("تم التنفيذ والتنظيم (${actions.size})", "Auto-Organized (${actions.size})", lang)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUndone) MaterialTheme.colorScheme.onSurfaceVariant else positiveColor
                    )
                }

                if (!isUndone) {
                    TextButton(
                        onClick = onUndo,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("ai_undo_actions_$messageId")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(AppStrings.text("تراجع", "Undo", lang), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            actions.forEach { rec ->
                val catColor = when {
                    rec.categoryColorHex != null -> parseHexColor(rec.categoryColorHex)
                    rec.type == TransactionType.TRANSFER -> SkyCyan
                    rec.type == TransactionType.INCOME -> positiveColor
                    else -> GoldAccent
                }
                val catLabel = when {
                    rec.type == TransactionType.TRANSFER -> AppStrings.text("تحويل داخلي", "Transfer", lang)
                    lang == AppLanguage.EN -> rec.categoryNameEn ?: "Uncategorized"
                    else -> rec.categoryNameAr ?: "غير مصنف"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(catColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (isUndone) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(catLabel)
                                    if (rec.createdNewCategory) {
                                        append(AppStrings.text(" (جديدة)", " (new)", lang))
                                    }
                                    append(" · ")
                                    append(rec.accountName)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = catColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = MoneyFormatter.formatPiasters(
                            piasters = rec.amountPiasters,
                            currency = currency,
                            language = lang,
                            hideAmounts = hideAmounts
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isUndone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (rec.type == TransactionType.INCOME) positiveColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AiActionPlanTab(
    lang: AppLanguage,
    planState: AiActionPlanState,
    onGeneratePlan: () -> Unit
) {
    val positiveColor = semanticPositiveColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FinancialCard(elevated = true, borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.text("خطة عمل مالية مخصصة", "Personalized Financial Action Plan", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.text(
                            "يُنشئ المساعد خطة مهيكلة من 3-4 خطوات عملية بناءً على دخلك وميزانياتك والتزاماتك الحالية — سواء بالذكاء السحابي أو المحلي.",
                            "Generates a structured 3-4 step action plan tailored to your income, budgets, and obligations — online or offline.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onGeneratePlan,
                        enabled = planState !is AiActionPlanState.Loading,
                        modifier = Modifier.fillMaxWidth().testTag("ai_generate_plan_btn")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("إنشاء الخطة الآن", "Generate Plan Now", lang))
                    }
                }
            }
        }

        when (planState) {
            is AiActionPlanState.Idle -> Unit
            is AiActionPlanState.Loading -> {
                item {
                    FinancialCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(AppStrings.text("جارٍ إعداد خطتك المالية...", "Building your action plan...", lang))
                        }
                    }
                }
            }
            is AiActionPlanState.Error -> {
                item {
                    FinancialCard(borderColor = CoralExpense.copy(alpha = 0.6f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = CoralExpense)
                            Text(
                                text = planState.message,
                                color = CoralExpense,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("ai_plan_error")
                            )
                        }
                    }
                }
            }
            is AiActionPlanState.Success -> {
                val plan = planState.plan
                item {
                    FinancialCard(borderColor = positiveColor.copy(alpha = 0.45f)) {
                        Column(
                            modifier = Modifier.testTag("ai_plan_result"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(plan.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (plan.budgetFocus.isNotBlank()) {
                                Text(
                                    text = "${AppStrings.text("التركيز:", "Focus:", lang)} ${plan.budgetFocus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SkyCyan
                                )
                            }
                            if (plan.savingsOpportunity.isNotBlank()) {
                                Text(
                                    text = "${AppStrings.text("فرصة الادخار:", "Savings target:", lang)} ${plan.savingsOpportunity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = positiveColor
                                )
                            }
                        }
                    }
                }
                items(plan.steps) { step ->
                    val badgeColor = when (step.priority.uppercase()) {
                        "HIGH" -> CoralExpense
                        "LOW" -> SkyCyan
                        else -> GoldAccent
                    }
                    FinancialCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(color = badgeColor.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = step.priority,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (step.impact.isNotBlank()) {
                                Text(
                                    text = step.impact,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiReceiptExtractorTab(
    mainState: MainUiState,
    extractState: AiReceiptExtractState,
    onAnalyzeReceipt: (String?, Bitmap?) -> Unit,
    onSaveExtractedTxSplit: (AiExtractedTransaction, Int) -> Unit,
    onSaveExtractedItems: (AiExtractedTransaction, Int) -> Unit
) {
    val lang = mainState.preferences.language
    val positiveColor = semanticPositiveColor()
    val context = LocalContext.current
    var rawText by rememberSaveable { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitWays by rememberSaveable(extractState) { mutableIntStateOf(1) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedBitmap = decodeUriToBitmap(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FinancialCard(elevated = true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.text("استخراج وتفصيل فاتورة أو رسالة", "Extract & Split Receipt or Message", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.text(
                            "اختر صورة فاتورة أو الصق نص إشعار بنكي أو بنود فاتورة، وسيتم استخراج المبلغ، تفصيل البنود تلقائيًا، وإتاحة تقسيم الفاتورة.",
                            "Attach a receipt photo or paste items/SMS to extract totals, itemized categories, and optional bill split.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("ai_pick_receipt_btn")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.text("اختيار صورة إيصال", "Pick Receipt Photo", lang))
                        }
                        if (selectedBitmap != null) {
                            OutlinedButton(onClick = { selectedBitmap = null }) {
                                Text(AppStrings.text("إزالة", "Remove", lang))
                            }
                        }
                    }

                    selectedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = AppStrings.text("معاينة الإيصال", "Receipt preview", lang),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text(AppStrings.text("أو الصق نص الفاتورة / بنودها", "Or paste receipt / items text", lang)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("ai_receipt_text_input")
                    )

                    Button(
                        onClick = { onAnalyzeReceipt(rawText, selectedBitmap) },
                        enabled = extractState !is AiReceiptExtractState.Loading,
                        modifier = Modifier.fillMaxWidth().testTag("ai_analyze_receipt_btn")
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("تحليل وتفصيل ذكي", "Smart Extract & Itemize", lang))
                    }
                }
            }
        }

        when (extractState) {
            is AiReceiptExtractState.Idle -> Unit
            is AiReceiptExtractState.Loading -> {
                item {
                    FinancialCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(AppStrings.text("جارٍ قراءة وتفصيل البيانات بالذكاء الاصطناعي...", "Extracting & itemizing details...", lang))
                        }
                    }
                }
            }
            is AiReceiptExtractState.Error -> {
                item {
                    FinancialCard(borderColor = CoralExpense.copy(alpha = 0.6f)) {
                        Text(
                            text = extractState.message,
                            color = CoralExpense,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("ai_receipt_error")
                        )
                    }
                }
            }
            is AiReceiptExtractState.Success -> {
                val tx = extractState.extracted
                val perPersonAmount = (tx.amountPiasters / splitWays.coerceAtLeast(1).toLong()).coerceAtLeast(1L)
                item {
                    FinancialCard(borderColor = positiveColor.copy(alpha = 0.5f)) {
                        Column(
                            modifier = Modifier.testTag("ai_receipt_result"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tx.description,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = MoneyFormatter.formatPiasters(
                                    piasters = tx.amountPiasters,
                                    currency = mainState.preferences.currencyCode,
                                    language = lang
                                ),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = RobotoMonoFontFamily),
                                fontWeight = FontWeight.Bold,
                                color = if (tx.type == TransactionType.INCOME) positiveColor else CoralExpense
                            )
                            if (tx.categoryHint.isNotBlank()) {
                                Text(
                                    text = "${AppStrings.text("الفئة المقترحة:", "Suggested category:", lang)} ${tx.categoryHint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SkyCyan
                                )
                            }
                            if (tx.notes.isNotBlank()) {
                                Text(
                                    text = tx.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Bill Splitter Row
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CallSplit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = AppStrings.text("تقسيم الفاتورة", "Split Bill", lang),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (splitWays > 1) {
                                                Text(
                                                    text = AppStrings.text(
                                                        "حصتك: ${MoneyFormatter.formatPiasters(perPersonAmount, mainState.preferences.currencyCode, lang)}",
                                                        "Your share: ${MoneyFormatter.formatPiasters(perPersonAmount, mainState.preferences.currencyCode, lang)}",
                                                        lang
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                                                    color = positiveColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { if (splitWays > 1) splitWays -= 1 },
                                            enabled = splitWays > 1 && !extractState.saved,
                                            modifier = Modifier.minimumInteractiveComponentSize().testTag("receipt_split_minus")
                                        ) {
                                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease split")
                                        }
                                        Text(
                                            text = "$splitWays",
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { if (splitWays < 10) splitWays += 1 },
                                            enabled = splitWays < 10 && !extractState.saved,
                                            modifier = Modifier.minimumInteractiveComponentSize().testTag("receipt_split_plus")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = "Increase split")
                                        }
                                    }
                                }
                            }

                            // Multi-Item Breakdown Card
                            if (tx.items.size >= 2) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(0.8.dp, SkyCyan.copy(alpha = 0.28f)),
                                    modifier = Modifier.fillMaxWidth().testTag("receipt_items_breakdown")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = AppStrings.text(
                                                "تفصيل بنود الفاتورة (${tx.items.size})",
                                                "Itemized Breakdown (${tx.items.size})",
                                                lang
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = SkyCyan
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                                        tx.items.forEach { item ->
                                            val shownAmt = (item.amountPiasters / splitWays.toLong()).coerceAtLeast(1L)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (item.categoryHint.isNotBlank()) {
                                                        Text(
                                                            text = item.categoryHint,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = SkyCyan
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = MoneyFormatter.formatPiasters(
                                                        shownAmt,
                                                        mainState.preferences.currencyCode,
                                                        lang
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (extractState.saved) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = positiveColor)
                                    Text(
                                        text = AppStrings.text("تم حفظ المعاملة في السجل", "Saved to your ledger", lang),
                                        color = positiveColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onSaveExtractedTxSplit(tx, splitWays) },
                                    modifier = Modifier.fillMaxWidth().testTag("ai_save_extracted_btn")
                                ) {
                                    Text(
                                        if (splitWays > 1) {
                                            AppStrings.text("حفظ حصتي (1/$splitWays)", "Save My Share (1/$splitWays)", lang)
                                        } else {
                                            AppStrings.text("حفظ كمعاملة", "Save Transaction", lang)
                                        }
                                    )
                                }
                                if (tx.items.size >= 2) {
                                    OutlinedButton(
                                        onClick = { onSaveExtractedItems(tx, splitWays) },
                                        modifier = Modifier.fillMaxWidth().testTag("ai_save_extracted_items_btn")
                                    ) {
                                        Text(
                                            AppStrings.text(
                                                "حفظ كل بند منفصلاً بتصنيفه (${tx.items.size})",
                                                "Save All ${tx.items.size} Items Separately",
                                                lang
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}
