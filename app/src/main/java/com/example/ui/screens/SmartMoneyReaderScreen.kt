package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.BalanceDiscrepancy
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.PendingStatus
import com.example.domain.model.TransactionType
import com.example.domain.reader.StatementColumnMapping
import com.example.domain.reader.StatementPreview
import com.example.reader.FinancialNotificationListenerService
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.SmartReaderAiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartMoneyReaderScreen(
    state: MainUiState,
    onSetReaderEnabled: (Boolean) -> Unit,
    onSetIgnoreOtp: (Boolean) -> Unit,
    onSetIgnoreBalance: (Boolean) -> Unit,
    onSetAutoApprove: (Boolean) -> Unit = {},
    onAnalyzePasted: (sender: String, body: String, channel: MessageChannel) -> Unit,
    onAnalyzePastedWithAi: (sender: String, body: String, channel: MessageChannel) -> Unit = { _, _, _ -> },
    smartReaderAiState: SmartReaderAiState = SmartReaderAiState.Idle,
    onResetReaderAiState: () -> Unit = {},
    onImportFile: (Uri) -> Unit,
    onApprovePending: (
        pendingId: Long,
        accountId: Long,
        targetAccountId: Long?,
        categoryId: Long?,
        overrideAmountPiasters: Long?,
        overrideType: TransactionType?,
        overrideDescription: String?
    ) -> Unit,
    onRejectPending: (Long) -> Unit,
    onIgnoreSource: (Long) -> Unit,
    onToggleSource: (FinancialSourceEntity) -> Unit,
    onAddCustomSource: (nameAr: String, nameEn: String, keywords: String, defaultAccountId: Long?) -> Unit,
    onClearImportData: () -> Unit,
    onRunMockBatch: () -> Unit = {},
    onBack: () -> Unit,
    onReconcileBalance: (BalanceDiscrepancy) -> Unit = {},
    onDismissDiscrepancy: (Long) -> Unit = {},
    onInspectCsvStatement: suspend (Uri) -> Pair<String, StatementPreview>? = { null },
    onImportBankStatement: (rawContent: String, bankName: String, mapping: StatementColumnMapping?) -> Unit = { _, _, _ -> }
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val positiveColor = semanticPositiveColor()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingPending by remember { mutableStateOf<PendingTransactionEntity?>(null) }
    var pendingCsvStatement by remember { mutableStateOf<Pair<String, StatementPreview>?>(null) }

    var isNotifListenerEnabled by remember {
        mutableStateOf(FinancialNotificationListenerService.isEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotifListenerEnabled = FinancialNotificationListenerService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onImportFile(uri)
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                pendingCsvStatement = onInspectCsvStatement(uri)
            }
        }
    }

    val pendingItems = remember(state.pendingTransactions) {
        state.pendingTransactions.filter { it.status == PendingStatus.PENDING }
    }
    val rejectedOrApprovedPending = remember(state.pendingTransactions) {
        state.pendingTransactions.filter { it.status != PendingStatus.PENDING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(AppStrings.text("قارئ المعاملات الذكي", "Smart Money Reader", lang))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = state.preferences.smartReaderEnabled,
                        onCheckedChange = onSetReaderEnabled,
                        modifier = Modifier.padding(end = 12.dp).testTag("smart_reader_master_switch")
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("reader_tab_pending"),
                    label = {
                        Text(
                            AppStrings.text(
                                "المراجعة (${pendingItems.size})",
                                "Review (${pendingItems.size})",
                                lang
                            )
                        )
                    }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("reader_tab_sources"),
                    label = { Text(AppStrings.text("المصادر والقواعد", "Sources & Rules", lang)) }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("reader_tab_history"),
                    label = { Text(AppStrings.text("السجل", "Logs", lang)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            ReaderIngestionChannelsCard(
                                enabled = state.preferences.smartReaderEnabled,
                                notifListenerEnabled = isNotifListenerEnabled,
                                lang = lang,
                                onOpenPaste = { showPasteDialog = true },
                                onOpenFileImport = {
                                    filePickerLauncher.launch(arrayOf("text/plain", "text/*"))
                                },
                                onOpenCsvImport = {
                                    csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain", "*/*"))
                                },
                                onOpenNotifSettings = {
                                    FinancialNotificationListenerService.openSettings(context)
                                },
                                onRunMockBatch = onRunMockBatch
                            )
                        }

                        if (state.balanceDiscrepancies.isNotEmpty()) {
                            items(state.balanceDiscrepancies, key = { "disc_${it.accountId}" }) { disc ->
                                FinancialCard(
                                    elevated = true,
                                    borderColor = GoldAccent.copy(alpha = 0.7f),
                                    modifier = Modifier.testTag("balance_discrepancy_card_${disc.accountId}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Balance, contentDescription = null, tint = GoldAccent)
                                        Text(
                                            text = AppStrings.text(
                                                "اختلاف رصيد مع ${disc.sourceName} (${disc.accountName})",
                                                "Balance Mismatch with ${disc.sourceName} (${disc.accountName})",
                                                lang
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onDismissDiscrepancy(disc.accountId) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("dismiss_discrepancy_btn_${disc.accountId}")
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = AppStrings.text("تجاهل", "Dismiss", lang))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = AppStrings.text(
                                            "رصيد التطبيق: ${MoneyFormatter.formatPiasters(disc.appBalancePiasters, language = lang)} • رصيد الرسالة البنكية: ${MoneyFormatter.formatPiasters(disc.bankBalancePiasters, language = lang)}",
                                            "App balance: ${MoneyFormatter.formatPiasters(disc.appBalancePiasters, language = lang)} • Bank SMS balance: ${MoneyFormatter.formatPiasters(disc.bankBalancePiasters, language = lang)}",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { onReconcileBalance(disc) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("reconcile_balance_btn_${disc.accountId}")
                                    ) {
                                        Text(AppStrings.text("مطابقة الرصيد تلقائيًا بقيد تسوية", "Reconcile Balance Automatically", lang))
                                    }
                                }
                            }
                        }

                        if (!state.preferences.smartReaderEnabled) {
                            item {
                                EmptyStateCard(
                                    title = AppStrings.text("قارئ المعاملات متوقف حاليًا", "Smart Reader is Disabled", lang),
                                    subtitle = AppStrings.text(
                                        "فعّل المفتاح أعلى الشاشة لتحليل الرسائل ماليًا على جهازك.",
                                        "Enable the toggle above to parse financial messages locally.",
                                        lang
                                    ),
                                    icon = Icons.Rounded.AutoAwesome
                                )
                            }
                        } else if (pendingItems.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = AppStrings.text("لا توجد معاملات معلقة للمراجعة", "No pending transactions", lang),
                                    subtitle = AppStrings.text(
                                        "الصق رسالة بنك أو محفظة، أو شارك رسالة إلى التطبيق، أو استورد ملفًا نصيًا.",
                                        "Paste a bank/wallet alert, share a message to ELBAGORRY Money, or import a file.",
                                        lang
                                    ),
                                    icon = Icons.Rounded.AutoAwesome
                                )
                            }
                        } else {
                            items(pendingItems, key = { it.id }) { item ->
                                PendingTransactionCard(
                                    item = item,
                                    accounts = state.accounts,
                                    categories = state.categories,
                                    lang = lang,
                                    hideAmounts = hide,
                                    onQuickApprove = { pending, accId, targetId, catId ->
                                        if (pending.mappedType == TransactionType.TRANSFER && (targetId == null || targetId == accId)) {
                                            editingPending = pending
                                        } else {
                                            onApprovePending(pending.id, accId, targetId, catId, null, null, null)
                                        }
                                    },
                                    onOpenEdit = { editingPending = it },
                                    onReject = onRejectPending,
                                    onIgnoreSource = onIgnoreSource
                                )
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            FinancialCard {
                                Text(
                                    text = AppStrings.text("إعدادات التصفية والخصوصية", "Privacy & Filter Rules", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                ReaderToggleRow(
                                    title = AppStrings.text("تسجيل تلقائي للإشعارات عالية الثقة (≥ 85%)", "Auto-Record High-Confidence Alerts (≥ 85%)", lang),
                                    subtitle = AppStrings.text(
                                        "عند التفعيل، يتم قيد الرسائل البنكية/المالية الواردة من الإشعارات تلقائياً وتحديث الرصيد فور وصولها",
                                        "Automatically posts incoming/outgoing notification alerts above 85% confidence straight into your ledger",
                                        lang
                                    ),
                                    checked = state.preferences.smartReaderAutoApprove,
                                    onCheckedChange = onSetAutoApprove,
                                    switchModifier = Modifier.testTag("reader_auto_approve_switch")
                                )
                                ReaderToggleRow(
                                    title = AppStrings.text("تجاهل رسائل التحقق OTP والأمان", "Ignore OTP & Security Codes", lang),
                                    subtitle = AppStrings.text("لا يتم حفظ أي رمز تحقق أو رسالة شخصية أبدًا", "OTP codes are never stored or parsed", lang),
                                    checked = state.preferences.readerIgnoreOtp,
                                    onCheckedChange = onSetIgnoreOtp
                                )
                                ReaderToggleRow(
                                    title = AppStrings.text("استبعاد رسائل الاستعلام عن الرصيد فقط", "Filter Balance-Only Inquiries", lang),
                                    subtitle = AppStrings.text("لا تتحول رسائل الرصيد لمعاملة مالية", "Balance alerts never create transactions", lang),
                                    checked = state.preferences.readerIgnoreBalanceInquiries,
                                    onCheckedChange = onSetIgnoreBalance
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.text("المصادر المالية المدعومة", "Supported Financial Sources", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { showAddSourceDialog = true },
                                    modifier = Modifier.testTag("add_custom_source_btn")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(AppStrings.text("إضافة مصدر", "Add Source", lang), modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }

                        items(state.financialSources, key = { it.id }) { src ->
                            val linkedAccount = src.defaultAccountId?.let { aid ->
                                state.accounts.firstOrNull { it.id == aid }?.name
                            }
                            FinancialCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (lang == AppLanguage.AR) src.displayNameAr else src.displayNameEn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = src.senderKeywords,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (linkedAccount != null) {
                                            Text(
                                                text = "${AppStrings.text("الحساب الافتراضي", "Default account", lang)}: $linkedAccount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = positiveColor
                                            )
                                        }
                                        if (src.isIgnored) {
                                            Text(
                                                text = AppStrings.text("تم تجاهل هذا المصدر", "Ignored", lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CoralExpense
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = src.isEnabled && !src.isIgnored,
                                        onCheckedChange = { onToggleSource(src) }
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = { showClearConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralExpense),
                                modifier = Modifier.fillMaxWidth().testTag("clear_import_data_btn")
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = AppStrings.text("مسح بيانات وسجل الاستيراد", "Clear Import Data & Logs", lang),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            FinancialCard {
                                val lastTs = state.preferences.lastImportTimestampMillis
                                val lastLabel = if (lastTs > 0L) {
                                    formatReadableDateTime(lastTs, lang)
                                } else {
                                    AppStrings.text("لم يتم الاستيراد بعد", "Never imported yet", lang)
                                }
                                Text(
                                    text = AppStrings.text("آخر فحص واستيراد: $lastLabel", "Last import: $lastLabel", lang),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        if (state.importHistory.isNotEmpty()) {
                            item {
                                Text(
                                    text = AppStrings.text("سجل عمليات الفحص", "Import Runs History", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(state.importHistory, key = { it.id }) { run ->
                                FinancialCard {
                                    Text(
                                        text = "${AppStrings.channelLabel(run.channel, lang)} • ${formatReadableDateTime(run.timestampMillis, lang)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = AppStrings.text(
                                            "فُحص: ${run.totalScanned} | مالية: ${run.financialDetected} | معلقة: ${run.pendingCreated} | مكررة: ${run.duplicatesSkipped} | مستبعدة: ${run.ignoredFiltered}",
                                            "Scanned: ${run.totalScanned} | Financial: ${run.financialDetected} | Pending: ${run.pendingCreated} | Duplicates: ${run.duplicatesSkipped} | Filtered: ${run.ignoredFiltered}",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (rejectedOrApprovedPending.isNotEmpty()) {
                            item {
                                Text(
                                    text = AppStrings.text("العمليات المعتمدة والمرفوضة", "Reviewed Operations", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(rejectedOrApprovedPending.take(30), key = { it.id }) { item ->
                                val statusColor = if (item.status == PendingStatus.APPROVED) positiveColor else CoralExpense
                                FinancialCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.extractedSummary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            text = if (item.status == PendingStatus.APPROVED) {
                                                AppStrings.text("معتمدة", "Approved", lang)
                                            } else {
                                                AppStrings.text("مرفوضة", "Rejected", lang)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }

                        if (state.importedMessages.isNotEmpty()) {
                            item {
                                Text(
                                    text = AppStrings.text("سجل بصمات الرسائل المستوردة (دون نصوص شخصية)", "Imported Fingerprints Log", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(state.importedMessages.take(30), key = { it.id }) { msg ->
                                FinancialCard {
                                    Text(
                                        text = "${msg.sourceName} • ${AppStrings.channelLabel(msg.channel, lang)}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    val statusLabel = when (msg.status) {
                                        ImportedMessageStatus.PENDING -> AppStrings.text("معلقة", "Pending", lang)
                                        ImportedMessageStatus.APPROVED -> AppStrings.text("معتمدة", "Approved", lang)
                                        ImportedMessageStatus.REJECTED -> AppStrings.text("مرفوضة", "Rejected", lang)
                                        ImportedMessageStatus.IGNORED -> AppStrings.text("تم تجاهلها", "Ignored", lang)
                                        ImportedMessageStatus.NON_FINANCIAL -> AppStrings.text("استعلام رصيد / غير مالية", "Non-transaction", lang)
                                        ImportedMessageStatus.DUPLICATE -> AppStrings.text("مكررة", "Duplicate", lang)
                                    }
                                    Text(
                                        text = "$statusLabel • ${formatReadableDateTime(msg.importedAtMillis, lang)}",
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

    if (showPasteDialog) {
        PasteFinancialMessageDialog(
            lang = lang,
            smartReaderAiState = smartReaderAiState,
            onDismiss = { showPasteDialog = false },
            onAnalyze = { sender, body ->
                onAnalyzePasted(sender, body, MessageChannel.CLIPBOARD_PASTE)
            },
            onAnalyzeWithAi = { sender, body ->
                onAnalyzePastedWithAi(sender, body, MessageChannel.CLIPBOARD_PASTE)
            },
            onResetAiState = onResetReaderAiState
        )
    }

    if (showAddSourceDialog) {
        AddFinancialSourceDialog(
            accounts = state.accounts,
            lang = lang,
            onDismiss = { showAddSourceDialog = false },
            onSave = onAddCustomSource
        )
    }

    editingPending?.let { item ->
        PendingReviewEditDialog(
            item = item,
            accounts = state.accounts,
            categories = state.categories,
            lang = lang,
            onDismiss = { editingPending = null },
            onConfirm = { id, accId, targetId, catId, amount, type, desc ->
                onApprovePending(id, accId, targetId, catId, amount, type, desc)
                editingPending = null
            }
        )
    }

    pendingCsvStatement?.let { (rawCsv, preview) ->
        BankStatementMappingDialog(
            preview = preview,
            lang = lang,
            onDismiss = { pendingCsvStatement = null },
            onConfirmImport = { bankLabel, mapping ->
                onImportBankStatement(rawCsv, bankLabel, mapping)
                pendingCsvStatement = null
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(AppStrings.text("مسح بيانات الاستيراد", "Clear Import Data", lang)) },
            text = {
                Text(
                    AppStrings.text(
                        "سيتم حذف العمليات المعلقة غير المعتمدة وسجل الاستيراد والبصمات، مع الاحتفاظ بالمعاملات المعتمدة مسبقًا في حساباتك.",
                        "Unapproved pending items and import logs will be cleared while keeping already approved transactions.",
                        lang
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearImportData()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralExpense)
                ) {
                    Text(AppStrings.text("تأكيد المسح", "Clear", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }
}

@Composable
private fun ReaderIngestionChannelsCard(
    enabled: Boolean,
    notifListenerEnabled: Boolean,
    lang: AppLanguage,
    onOpenPaste: () -> Unit,
    onOpenFileImport: () -> Unit,
    onOpenCsvImport: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onRunMockBatch: () -> Unit
) {
    val positiveColor = semanticPositiveColor()
    FinancialCard(elevated = true, borderColor = SkyCyan.copy(alpha = 0.50f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = positiveColor)
            Text(
                text = AppStrings.text(
                    "قراءة ذكية محليًا أو بمساعدة Gemini AI",
                    "Local Offline & Optional Gemini AI Parsing",
                    lang
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = AppStrings.text(
                "لا يطلب التطبيق صلاحية READ_SMS. الصق رسالة، شارك نصًا، أو استورد ملفًا أو كشف حساب CSV مع تصفية أكواد OTP تلقائيًا.",
                "Privacy-first without READ_SMS. Paste alerts, share text, or import CSV bank statements with automatic OTP filtering.",
                lang
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenPaste,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("paste_message_btn")
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(AppStrings.text("لصق رسالة", "Paste Alert", lang), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = onOpenFileImport,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("import_file_btn")
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(AppStrings.text("ملف رسائل", "Text File", lang), modifier = Modifier.padding(start = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenCsvImport,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("import_csv_statement_btn")
        ) {
            Icon(Icons.Rounded.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = AppStrings.text("استيراد كشف حساب بنكي (CSV / Excel)", "Import Bank Statement (CSV / TSV)", lang),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            color = (if (notifListenerEnabled) positiveColor else GoldAccent).copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (notifListenerEnabled) positiveColor else GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = AppStrings.text("قارئ إشعارات البنوك والمحافظ", "Notification Listener", lang),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = if (notifListenerEnabled) {
                                AppStrings.text("مفعّل (يعمل في الخلفية)", "Enabled", lang)
                            } else {
                                AppStrings.text("غير مفعّل (اختياري)", "Disabled (Optional)", lang)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (notifListenerEnabled) positiveColor else GoldAccent
                        )
                    }
                }
                TextButton(
                    onClick = onOpenNotifSettings,
                    modifier = Modifier.testTag("enable_notif_listener_btn")
                ) {
                    Text(AppStrings.text("الإعدادات", "Settings", lang))
                }
            }
        }
    }
}

@Composable
private fun ReaderToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankStatementMappingDialog(
    preview: StatementPreview,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onConfirmImport: (bankLabel: String, mapping: StatementColumnMapping) -> Unit
) {
    var bankLabel by remember { mutableStateOf("Bank Statement") }
    var dateIdx by remember { mutableIntStateOf(preview.suggestedMapping.dateColumnIndex) }
    var descIdx by remember { mutableIntStateOf(preview.suggestedMapping.descriptionColumnIndex) }
    var amountIdx by remember { mutableStateOf(preview.suggestedMapping.amountColumnIndex) }
    var debitIdx by remember { mutableStateOf(preview.suggestedMapping.debitColumnIndex) }
    var creditIdx by remember { mutableStateOf(preview.suggestedMapping.creditColumnIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.text("معاينة وتعيين أعمدة كشف الحساب", "Map Bank Statement Columns", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.text(
                        "تم اكتشاف ${preview.totalRowCount} صف • ${preview.headers.size} عمود",
                        "Detected ${preview.totalRowCount} rows • ${preview.headers.size} columns",
                        lang
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyCyan
                )
                OutlinedTextField(
                    value = bankLabel,
                    onValueChange = { bankLabel = it },
                    label = { Text(AppStrings.text("اسم البنك أو المحفظة", "Bank / Wallet Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("csv_bank_name_input")
                )
                Text(AppStrings.text("عمود التاريخ:", "Date Column:", lang), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.headers.forEachIndexed { idx, header ->
                        FilterChip(
                            selected = dateIdx == idx,
                            onClick = { dateIdx = idx },
                            label = { Text(header.ifBlank { "#$idx" }) }
                        )
                    }
                }
                Text(AppStrings.text("عمود البيان / الوصف:", "Description Column:", lang), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.headers.forEachIndexed { idx, header ->
                        FilterChip(
                            selected = descIdx == idx,
                            onClick = { descIdx = idx },
                            label = { Text(header.ifBlank { "#$idx" }) }
                        )
                    }
                }
                Text(
                    text = AppStrings.text(
                        "التعرف التلقائي على المبلغ: " + listOfNotNull(
                            amountIdx?.let { preview.headers.getOrNull(it) },
                            debitIdx?.let { "خصم: ${preview.headers.getOrNull(it)}" },
                            creditIdx?.let { "إيداع: ${preview.headers.getOrNull(it)}" }
                        ).joinToString(" • ").ifBlank { "-" },
                        "Detected amount column(s): " + listOfNotNull(
                            amountIdx?.let { preview.headers.getOrNull(it) },
                            debitIdx?.let { "Debit: ${preview.headers.getOrNull(it)}" },
                            creditIdx?.let { "Credit: ${preview.headers.getOrNull(it)}" }
                        ).joinToString(" • ").ifBlank { "-" },
                        lang
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmImport(
                        bankLabel,
                        StatementColumnMapping(
                            dateColumnIndex = dateIdx,
                            descriptionColumnIndex = descIdx,
                            amountColumnIndex = amountIdx,
                            debitColumnIndex = debitIdx,
                            creditColumnIndex = creditIdx,
                            balanceColumnIndex = preview.suggestedMapping.balanceColumnIndex
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_csv_statement_import_btn")
            ) {
                Text(AppStrings.text("استيراد المعاملات", "Import Rows", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
        }
    )
}
