package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.TransactionType
import com.example.notifications.NotificationHelper
import com.example.security.AppSecurityHelper
import com.example.ui.components.AppStrings
import com.example.ui.navigation.AccountsRoute
import com.example.ui.navigation.CalendarRoute
import com.example.ui.navigation.CategoriesRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.InsightsRoute
import com.example.ui.navigation.NotificationsRoute
import com.example.ui.navigation.PlanningRoute
import com.example.ui.navigation.ReceiptsVaultRoute
import com.example.ui.navigation.ReportsRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SmartReaderRoute
import com.example.ui.navigation.TransactionsRoute
import com.example.ui.screens.AccountsAndGoalsScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PlanningScreen
import com.example.ui.screens.QuickAddTransactionSheet
import com.example.ui.screens.ReceiptsVaultScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartMoneyReaderScreen
import com.example.ui.screens.TransactionDetailSheet
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private var incomingSharedText by mutableStateOf<Pair<String, String>?>(null)
    private var incomingNavDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractLaunchExtras(intent)
        val app = application as ElbagorryMoneyApp

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    repository = app.repository,
                    prefsRepo = app.preferencesRepository,
                    backupManager = app.backupManager,
                    smartReaderProcessor = app.smartReaderProcessor,
                    appContext = applicationContext
                )
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var isUnlocked by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(state.preferences.privacyScreenGuardEnabled) {
                if (state.preferences.privacyScreenGuardEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            ElbagorryMoneyTheme(
                themeMode = state.preferences.themeMode,
                themePreset = state.preferences.themePreset,
                language = state.preferences.language
            ) {
                val requiresLock = state.preferences.biometricLockEnabled && !isUnlocked
                if (requiresLock) {
                    LockGateScreen(
                        onUnlockClick = {
                            AppSecurityHelper.promptBiometricUnlock(
                                activity = this@MainActivity,
                                title = getString(R.string.biometric_prompt_title),
                                subtitle = getString(R.string.biometric_prompt_subtitle),
                                onSuccess = { isUnlocked = true },
                                onFailure = { }
                            )
                        }
                    )
                } else {
                    ElbagorryMoneyRoot(
                        viewModel = viewModel,
                        sharedPayload = incomingSharedText,
                        onSharedPayloadConsumed = { incomingSharedText = null },
                        navDestination = incomingNavDestination,
                        onNavDestinationConsumed = { incomingNavDestination = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractLaunchExtras(intent)
    }

    private fun extractLaunchExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedBody = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedBody.isNullOrBlank()) {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                incomingSharedText = subject.ifBlank { "Shared" } to sharedBody
            }
        }
        val dest = intent.getStringExtra(NotificationHelper.EXTRA_NAV_DESTINATION)
        if (!dest.isNullOrBlank()) {
            incomingNavDestination = dest
            intent.removeExtra(NotificationHelper.EXTRA_NAV_DESTINATION)
        }
    }
}

private data class TopLevelDestination(
    val route: Any,
    val labelAr: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun ElbagorryMoneyRoot(
    viewModel: MainViewModel,
    sharedPayload: Pair<String, String>? = null,
    onSharedPayloadConsumed: () -> Unit = {},
    navDestination: String? = null,
    onNavDestinationConsumed: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lang = state.preferences.language
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddInitialType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var detailTx by remember { mutableStateOf<TransactionEntity?>(null) }

    LaunchedEffect(sharedPayload) {
        if (sharedPayload != null) {
            viewModel.ingestSharedOrPastedText(
                sender = sharedPayload.first,
                rawText = sharedPayload.second,
                channel = MessageChannel.SHARE_INTENT
            )
            onSharedPayloadConsumed()
            navController.navigate(SmartReaderRoute) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navDestination) {
        if (navDestination != null) {
            when (navDestination) {
                NotificationHelper.DEST_QUICK_ADD -> {
                    editingTx = null
                    quickAddInitialType = TransactionType.EXPENSE
                    showQuickAdd = true
                }
                NotificationHelper.DEST_QUICK_ADD_INCOME -> {
                    editingTx = null
                    quickAddInitialType = TransactionType.INCOME
                    showQuickAdd = true
                }
                NotificationHelper.DEST_PLANNING -> {
                    navController.navigate(PlanningRoute) {
                        popUpTo<DashboardRoute> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                NotificationHelper.DEST_INSIGHTS -> {
                    navController.navigate(InsightsRoute) {
                        popUpTo<DashboardRoute> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                NotificationHelper.DEST_SMART_READER -> {
                    navController.navigate(SmartReaderRoute) { launchSingleTop = true }
                }
                NotificationHelper.DEST_NOTIFICATIONS -> {
                    navController.navigate(NotificationsRoute) { launchSingleTop = true }
                }
            }
            onNavDestinationConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(DashboardRoute, "الرئيسية", "Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
            TopLevelDestination(TransactionsRoute, "المعاملات", "Ledger", Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_tab_transactions"),
            TopLevelDestination(AccountsRoute, "الحسابات", "Accounts", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_tab_accounts"),
            TopLevelDestination(PlanningRoute, "الالتزامات", "Planning", Icons.AutoMirrored.Rounded.EventNote, Icons.AutoMirrored.Outlined.EventNote, "nav_tab_planning"),
            TopLevelDestination(InsightsRoute, "التحليلات", "Insights", Icons.Rounded.Analytics, Icons.Outlined.Analytics, "nav_tab_insights")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination == null || destinations.any { dest ->
        when (dest.route) {
            is DashboardRoute -> currentDestination.hasRoute<DashboardRoute>()
            is TransactionsRoute -> currentDestination.hasRoute<TransactionsRoute>()
            is AccountsRoute -> currentDestination.hasRoute<AccountsRoute>()
            is PlanningRoute -> currentDestination.hasRoute<PlanningRoute>()
            is InsightsRoute -> currentDestination.hasRoute<InsightsRoute>()
            else -> false
        }
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo<DashboardRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isTopLevel,
                    enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150))
                ) {
                    FloatingActionButton(
                        onClick = {
                            editingTx = null
                            quickAddInitialType = null
                            showQuickAdd = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_quick_add")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = AppStrings.text("إضافة معاملة", "Add Transaction", lang))
                    }
                }
            },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        destinations.forEach { dest ->
                            val selected = when (dest.route) {
                                is DashboardRoute -> currentDestination?.hasRoute<DashboardRoute>() == true
                                is TransactionsRoute -> currentDestination?.hasRoute<TransactionsRoute>() == true
                                is AccountsRoute -> currentDestination?.hasRoute<AccountsRoute>() == true
                                is PlanningRoute -> currentDestination?.hasRoute<PlanningRoute>() == true
                                is InsightsRoute -> currentDestination?.hasRoute<InsightsRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = AppStrings.text(dest.labelAr, dest.labelEn, lang)
                                    )
                                },
                                label = { Text(AppStrings.text(dest.labelAr, dest.labelEn, lang), maxLines = 1) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (isTopLevel && useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { dest ->
                            val selected = when (dest.route) {
                                is DashboardRoute -> currentDestination?.hasRoute<DashboardRoute>() == true
                                is TransactionsRoute -> currentDestination?.hasRoute<TransactionsRoute>() == true
                                is AccountsRoute -> currentDestination?.hasRoute<AccountsRoute>() == true
                                is PlanningRoute -> currentDestination?.hasRoute<PlanningRoute>() == true
                                is InsightsRoute -> currentDestination?.hasRoute<InsightsRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = AppStrings.text(dest.labelAr, dest.labelEn, lang)
                                    )
                                },
                                label = { Text(AppStrings.text(dest.labelAr, dest.labelEn, lang)) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DashboardRoute,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) },
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable<DashboardRoute> {
                        DashboardScreen(
                            state = state,
                            onSelectTimeFilter = viewModel::setTimeFilter,
                            onTogglePrivacy = viewModel::togglePrivacyAmounts,
                            onNavigateCalendar = { navController.navigate(CalendarRoute) },
                            onNavigateReports = { navController.navigate(ReportsRoute) },
                            onNavigateNotifications = { navController.navigate(NotificationsRoute) },
                            onNavigateSettings = { navController.navigate(SettingsRoute) },
                            onOpenAnalytics = { navigateTopLevel(InsightsRoute) },
                            onNavigateSmartReader = { navController.navigate(SmartReaderRoute) },
                            onNavigateCategories = { navController.navigate(CategoriesRoute) }
                        )
                    }
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            state = state,
                            onSearchChange = viewModel::setSearchQuery,
                            onTypeFilter = viewModel::setTypeFilter,
                            onAccountFilter = viewModel::setAccountFilter,
                            onSortChange = viewModel::setSort,
                            onTransactionClick = { detailTx = it }
                        )
                    }
                    composable<AccountsRoute> {
                        AccountsAndGoalsScreen(
                            state = state,
                            onCreateAccount = viewModel::createAccount,
                            onTransfer = viewModel::createTransfer,
                            onCreateGoal = viewModel::saveSavingsGoal,
                            onContributeGoal = viewModel::contributeToGoal,
                            onToggleGoalPause = viewModel::toggleGoalPause,
                            onDeleteGoal = viewModel::deleteGoal
                        )
                    }
                    composable<PlanningRoute> {
                        PlanningScreen(
                            state = state,
                            onCreateBudget = { name, catId, limit -> viewModel.saveBudget(name, catId, limit) },
                            onDeleteBudget = viewModel::deleteBudget,
                            onCopyPreviousBudgets = viewModel::copyPreviousMonthBudgets,
                            onCreateInstallment = viewModel::saveInstallment,
                            onPayInstallment = viewModel::payInstallment,
                            onSetInstallmentStatus = viewModel::setInstallmentStatus,
                            onCreateRecurring = viewModel::saveRecurringRule,
                            onToggleRecurring = viewModel::toggleRecurringRule,
                            onDeleteRecurring = viewModel::deleteRecurringRule,
                            onCreateDebt = viewModel::saveDebt,
                            onSettleDebt = viewModel::settleDebt,
                            onDeleteDebt = viewModel::deleteDebt
                        )
                    }
                    composable<InsightsRoute> {
                        InsightsScreen(
                            state = state,
                            onNavigateCalendar = { navController.navigate(CalendarRoute) },
                            onNavigateReports = { navController.navigate(ReportsRoute) },
                            onNavigateNotifications = { navController.navigate(NotificationsRoute) },
                            onNavigateSettings = { navController.navigate(SettingsRoute) },
                            onNavigateSmartReader = { navController.navigate(SmartReaderRoute) },
                            onNavigateCategories = { navController.navigate(CategoriesRoute) },
                            onNavigateReceiptsVault = { navController.navigate(ReceiptsVaultRoute) }
                        )
                    }
                    composable<ReceiptsVaultRoute> {
                        ReceiptsVaultScreen(
                            state = state,
                            onTransactionClick = { detailTx = it },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<CategoriesRoute> {
                        CategoriesScreen(
                            state = state,
                            onCreateCategory = viewModel::createCategory,
                            onDeleteCategory = viewModel::deleteCategory,
                            onSaveMerchantRule = viewModel::saveMerchantRule,
                            onDeleteMerchantRule = viewModel::deleteMerchantRule,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<SmartReaderRoute> {
                        SmartMoneyReaderScreen(
                            state = state,
                            onSetReaderEnabled = viewModel::setSmartReaderEnabled,
                            onSetIgnoreOtp = viewModel::setReaderIgnoreOtp,
                            onSetIgnoreBalance = viewModel::setReaderIgnoreBalance,
                            onAnalyzePasted = viewModel::ingestSharedOrPastedText,
                            onImportFile = { uri -> viewModel.importMessagesFromFile(context, uri) },
                            onApprovePending = viewModel::approvePending,
                            onRejectPending = viewModel::rejectPending,
                            onIgnoreSource = viewModel::ignoreFinancialSource,
                            onToggleSource = viewModel::toggleFinancialSource,
                            onAddCustomSource = viewModel::addCustomFinancialSource,
                            onClearImportData = viewModel::clearImportData,
                            onRunMockBatch = viewModel::runMockSmsBatch,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<CalendarRoute> {
                        CalendarScreen(state = state, onBack = { navController.popBackStack() })
                    }
                    composable<ReportsRoute> {
                        ReportsScreen(
                            state = state,
                            onExportCsv = { viewModel.exportAndShareCsv(context) },
                            onExportPdf = { viewModel.exportAndSharePdf(context) },
                            onShareJsonBackup = { viewModel.exportAndShareJsonBackup(context) },
                            onSaveBackupToUri = { uri -> viewModel.writeBackupToUri(context, uri) },
                            onRestoreBackupFromUri = { uri -> viewModel.restoreBackupFromUri(context, uri) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<NotificationsRoute> {
                        NotificationsScreen(
                            state = state,
                            onMarkAllRead = viewModel::markAllNotificationsRead,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            state = state,
                            onSetThemeMode = viewModel::setThemeMode,
                            onSetLanguage = viewModel::setLanguage,
                            onSetBiometricLock = viewModel::setBiometricLock,
                            onToggleNotificationSetting = viewModel::setNotificationToggle,
                            onBack = { navController.popBackStack() },
                            onSetThemePreset = viewModel::setThemePreset,
                            onSetSummaryFrequency = viewModel::setSummaryFrequency,
                            onSendSummaryNow = { viewModel.sendSummaryNotificationNow(context) },
                            onSetPrimaryCurrency = viewModel::setPrimaryCurrency,
                            onSetPrivacyScreenGuard = viewModel::setPrivacyScreenGuard
                        )
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddTransactionSheet(
            state = state,
            editingTransaction = editingTx,
            initialType = quickAddInitialType,
            onDismiss = {
                showQuickAdd = false
                editingTx = null
                quickAddInitialType = null
            },
            onSave = { existingId, amountPiasters, type, accountId, targetId, catId, goalId, debtId, desc, notes, uri, learnRule ->
                viewModel.saveTransaction(
                    existingId = existingId,
                    amountPiasters = amountPiasters,
                    type = type,
                    accountId = accountId,
                    targetAccountId = targetId,
                    categoryId = catId,
                    savingsGoalId = goalId,
                    debtId = debtId,
                    description = desc,
                    notes = notes,
                    attachmentUri = uri,
                    learnMerchantRule = learnRule
                )
                showQuickAdd = false
                editingTx = null
                quickAddInitialType = null
            }
        )
    }

    detailTx?.let { tx ->
        TransactionDetailSheet(
            tx = tx,
            state = state,
            onDismiss = { detailTx = null },
            onEdit = {
                detailTx = null
                editingTx = it
                showQuickAdd = true
            },
            onDuplicate = {
                detailTx = null
                viewModel.duplicateTransaction(it)
            },
            onDelete = {
                detailTx = null
                viewModel.deleteTransaction(it)
            }
        )
    }
}

@Composable
private fun LockGateScreen(onUnlockClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text("ELBAGORRY Money", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "التطبيق مؤمن بالبصمة أو قفل الجهاز",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onUnlockClick) {
                    Text("فتح التطبيق")
                }
            }
        }
    }
}
