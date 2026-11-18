package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.GoogleAuthManager
import com.example.auth.authStateFlow
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.remote.CloudAutoSyncWorker
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.CloudTransaction
import com.example.data.remote.UserProfile
import com.example.domain.model.MessageChannel
import com.example.domain.model.TransactionType
import com.example.notifications.NotificationHelper
import com.example.security.AppSecurityHelper
import com.example.ui.components.AppStrings
import com.example.ui.components.SmoothReveal
import com.example.ui.components.springPressEffect
import com.example.ui.navigation.AccountsRoute
import com.example.ui.navigation.AiAdvisorRoute
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
import com.example.ui.navigation.WhatIfSimulatorRoute
import com.example.ui.screens.AccountsAndGoalsScreen
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.AuthScreen
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
import com.example.ui.screens.WhatIfSimulatorScreen
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.AiAdvisorViewModel
import com.example.ui.viewmodel.CloudSyncActionState
import com.example.ui.viewmodel.CloudSyncViewModel
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private var incomingSharedText by mutableStateOf<Pair<String, String>?>(null)
    private var incomingNavDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        extractLaunchExtras(intent)
        val app = application as ElbagorryMoneyApp

        setContent {
            val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            LaunchedEffect(prefs.privacyScreenGuardEnabled) {
                if (prefs.privacyScreenGuardEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(currentUser?.uid, prefs.isGuestMode) {
                if (currentUser != null && prefs.isGuestMode) {
                    app.preferencesRepository.setGuestMode(false)
                }
            }

            ElbagorryMoneyTheme(
                themeMode = prefs.themeMode,
                themePreset = prefs.themePreset,
                language = prefs.language
            ) {
                val rootScope = rememberCoroutineScope()
                if (currentUser == null && !prefs.isGuestMode) {
                    AuthScreen(
                        language = prefs.language,
                        onAuthSuccess = {},
                        onContinueAsGuest = {
                            rootScope.launch { app.preferencesRepository.setGuestMode(true) }
                        }
                    )
                } else if (currentUser == null) {
                    key("guest_local") {
                        val mainViewModel: MainViewModel = viewModel(
                            key = "main_guest_local",
                            factory = MainViewModel.Factory(
                                repository = app.repository,
                                prefsRepo = app.preferencesRepository,
                                backupManager = app.backupManager,
                                smartReaderProcessor = app.smartReaderProcessor,
                                appContext = applicationContext
                            )
                        )
                        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
                        val credentialManager = remember { CredentialManager.create(this@MainActivity) }
                        val guestScope = rememberCoroutineScope()
                        var isUnlocked by rememberSaveable("guest_local") { mutableStateOf(false) }

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
                                viewModel = mainViewModel,
                                sharedPayload = incomingSharedText,
                                onSharedPayloadConsumed = { incomingSharedText = null },
                                navDestination = incomingNavDestination,
                                onNavDestinationConsumed = { incomingNavDestination = null },
                                isGuestMode = true,
                                onSignInFromGuest = {
                                    GoogleAuthManager.onGoogleSignInClicked(
                                        context = this@MainActivity,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            guestScope.launch { app.preferencesRepository.setGuestMode(false) }
                                        },
                                        onAuthError = { msg ->
                                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                        },
                                        scope = guestScope
                                    )
                                },
                                onExitGuestMode = {
                                    guestScope.launch { app.preferencesRepository.setGuestMode(false) }
                                }
                            )
                        }
                    }
                } else {
                    val activeUser = currentUser!!
                    val uid = activeUser.uid
                    key(uid) {
                        val mainViewModel: MainViewModel = viewModel(
                            key = "main_$uid",
                            factory = MainViewModel.Factory(
                                repository = app.repository,
                                prefsRepo = app.preferencesRepository,
                                backupManager = app.backupManager,
                                smartReaderProcessor = app.smartReaderProcessor,
                                appContext = applicationContext
                            )
                        )
                        val cloudSyncViewModel: CloudSyncViewModel = viewModel(
                            key = "cloud_$uid",
                            factory = viewModelFactory {
                                initializer {
                                    val application = checkNotNull(
                                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                                    ) as ElbagorryMoneyApp
                                    val databaseId = application.getString(R.string.firestore_database_id)
                                    val firestoreDb = FirebaseFirestore.getInstance(databaseId)
                                    CloudSyncViewModel(
                                        cloudRepository = CloudSyncRepository(firestoreDb),
                                        localDb = application.database,
                                        prefsRepo = application.preferencesRepository,
                                        currentUserId = uid
                                    )
                                }
                            }
                        )

                        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
                        val cloudProfileState by cloudSyncViewModel.profileState.collectAsStateWithLifecycle()
                        val cloudTransactionsState by cloudSyncViewModel.cloudTransactionsState.collectAsStateWithLifecycle()
                        val cloudActionState by cloudSyncViewModel.actionState.collectAsStateWithLifecycle()
                        val credentialManager = remember { CredentialManager.create(this@MainActivity) }
                        val authScope = rememberCoroutineScope()
                        var isUnlocked by rememberSaveable(uid) { mutableStateOf(false) }

                        LaunchedEffect(uid) {
                            cloudSyncViewModel.bootstrapProfileIfNeeded(
                                displayName = activeUser.displayName ?: "ELBAGORRY User",
                                email = activeUser.email
                            )
                        }

                        LaunchedEffect(uid, state.preferences.autoCloudSyncEnabled, state.preferences.isGuestMode) {
                            if (state.preferences.autoCloudSyncEnabled && !state.preferences.isGuestMode) {
                                CloudAutoSyncWorker.schedulePeriodic(applicationContext)
                            } else {
                                CloudAutoSyncWorker.cancelAll(applicationContext)
                            }
                        }

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
                                viewModel = mainViewModel,
                                sharedPayload = incomingSharedText,
                                onSharedPayloadConsumed = { incomingSharedText = null },
                                navDestination = incomingNavDestination,
                                onNavDestinationConsumed = { incomingNavDestination = null },
                                cloudUserName = activeUser.displayName,
                                cloudUserEmail = activeUser.email,
                                cloudProfileState = cloudProfileState,
                                cloudTransactionsState = cloudTransactionsState,
                                cloudActionState = cloudActionState,
                                onSyncToCloud = {
                                    cloudSyncViewModel.syncToCloudNow(
                                        displayName = activeUser.displayName ?: "ELBAGORRY User",
                                        email = activeUser.email
                                    )
                                },
                                onRestoreFromCloud = {
                                    cloudSyncViewModel.restoreFromCloudNow()
                                },
                                onSignOut = {
                                    GoogleAuthManager.signOut(
                                        credentialManager = credentialManager,
                                        scope = authScope
                                    )
                                }
                            )
                        }
                    }
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
    onNavDestinationConsumed: () -> Unit = {},
    cloudUserName: String? = null,
    cloudUserEmail: String? = null,
    cloudProfileState: CloudUiState<UserProfile?> = CloudUiState.Loading,
    cloudTransactionsState: CloudUiState<List<CloudTransaction>> = CloudUiState.Loading,
    cloudActionState: CloudSyncActionState = CloudSyncActionState(),
    onSyncToCloud: () -> Unit = {},
    onRestoreFromCloud: () -> Unit = {},
    onSignOut: () -> Unit = {},
    isGuestMode: Boolean = false,
    onSignInFromGuest: () -> Unit = {},
    onExitGuestMode: () -> Unit = {},
    aiAdvisorViewModel: AiAdvisorViewModel? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickAutoFillState by viewModel.quickAutoFillState.collectAsStateWithLifecycle()
    val smartReaderAiState by viewModel.smartReaderAiState.collectAsStateWithLifecycle()
    val autoCategorizeState by viewModel.autoCategorizeState.collectAsStateWithLifecycle()
    val lang = state.preferences.language
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddInitialType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var detailTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var planningInitialTab by rememberSaveable { mutableIntStateOf(0) }

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
    LaunchedEffect(currentDestination?.route) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
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

        val fabInteraction = remember { MutableInteractionSource() }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(
                        bottom = if (currentDestination?.hasRoute<AiAdvisorRoute>() == true) 80.dp else 0.dp
                    )
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isTopLevel,
                    enter = scaleIn(tween(150)) + fadeIn(tween(140)),
                    exit = scaleOut(tween(120)) + fadeOut(tween(120))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val aiFabInteraction = remember { MutableInteractionSource() }
                        SmallFloatingActionButton(
                            onClick = {
                                navController.navigate(AiAdvisorRoute) {
                                    launchSingleTop = true
                                }
                            },
                            interactionSource = aiFabInteraction,
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                                .springPressEffect(aiFabInteraction, pressedScale = 0.92f)
                                .testTag("fab_ai_coach")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = AppStrings.text("الشات الذكي", "AI Organizer", lang)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                editingTx = null
                                quickAddInitialType = null
                                showQuickAdd = true
                            },
                            interactionSource = fabInteraction,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .springPressEffect(fabInteraction, pressedScale = 0.92f)
                                .testTag("fab_quick_add")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = AppStrings.text("إضافة معاملة", "Add Transaction", lang))
                        }
                    }
                }
            },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
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
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.14f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "navIcon_${dest.tag}"
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = {
                                    Icon(
                                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = AppStrings.text(dest.labelAr, dest.labelEn, lang),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
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
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.14f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "railIcon_${dest.tag}"
                            )
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = AppStrings.text(dest.labelAr, dest.labelEn, lang),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
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
                    enterTransition = {
                        slideInHorizontally(tween(160, easing = FastOutSlowInEasing)) { it / 20 } +
                            fadeIn(tween(140))
                    },
                    exitTransition = {
                        fadeOut(tween(110))
                    },
                    popEnterTransition = {
                        slideInHorizontally(tween(150, easing = FastOutSlowInEasing)) { -it / 22 } +
                            fadeIn(tween(140))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(140, easing = FastOutSlowInEasing)) { it / 18 } +
                            fadeOut(tween(110))
                    },
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
                            onNavigateCategories = { navController.navigate(CategoriesRoute) },
                            onNavigateAiAdvisor = { navController.navigate(AiAdvisorRoute) },
                            onNavigateBnpl = {
                                planningInitialTab = 4
                                navigateTopLevel(PlanningRoute)
                            }
                        )
                    }
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            state = state,
                            onSearchChange = viewModel::setSearchQuery,
                            onTypeFilter = viewModel::setTypeFilter,
                            onAccountFilter = viewModel::setAccountFilter,
                            onSortChange = viewModel::setSort,
                            onTransactionClick = { detailTx = it },
                            autoCategorizeState = autoCategorizeState,
                            onAutoCategorizeUncategorized = viewModel::autoCategorizeUncategorizedWithAi
                        )
                    }
                    composable<AccountsRoute> {
                        AccountsAndGoalsScreen(
                            state = state,
                            onCreateAccount = viewModel::createAccount,
                            onTransfer = { fromId, toId, amount, notes, fee ->
                                viewModel.createTransfer(fromId, toId, amount, notes, fee)
                            },
                            onCreateGoal = viewModel::saveSavingsGoal,
                            onContributeGoal = viewModel::contributeToGoal,
                            onToggleGoalPause = viewModel::toggleGoalPause,
                            onDeleteGoal = viewModel::deleteGoal,
                            onSaveCertificate = { title, kind, inst, principal, yieldPct, cycle, linkedAccId, grams, karat, perGram, notes ->
                                viewModel.saveCertificate(
                                    title = title,
                                    assetKind = kind,
                                    institutionName = inst,
                                    principalPiasters = principal,
                                    annualYieldPercent = yieldPct,
                                    payoutCycle = cycle,
                                    linkedAccountId = linkedAccId,
                                    grams = grams,
                                    karat = karat,
                                    pricePerGramPiasters = perGram,
                                    notes = notes
                                )
                            },
                            onDeleteCertificate = viewModel::deleteCertificate,
                            onUpdateGoldPrice = viewModel::updateGoldPrice,
                            onCreditCertificateInterest = viewModel::creditCertificateInterest
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
                            onDeleteDebt = viewModel::deleteDebt,
                            onSaveBnplProgram = viewModel::saveBnplProgram,
                            onDeleteBnplProgram = viewModel::deleteBnplProgram,
                            initialTab = planningInitialTab,
                            onInitialTabConsumed = { planningInitialTab = 0 },
                            onOpenWhatIfSimulator = { navController.navigate(WhatIfSimulatorRoute) }
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
                            onNavigateReceiptsVault = { navController.navigate(ReceiptsVaultRoute) },
                            onNavigateAiAdvisor = { navController.navigate(AiAdvisorRoute) },
                            onNavigateWhatIfSimulator = { navController.navigate(WhatIfSimulatorRoute) }
                        )
                    }
                    composable<AiAdvisorRoute> {
                        val app = context.applicationContext as ElbagorryMoneyApp
                        val connectivityChecker = remember(context) {
                            com.example.data.remote.AndroidNetworkConnectivityChecker(context.applicationContext)
                        }
                        val cloudSyncRepo = remember(isGuestMode, cloudUserName) {
                            if (!isGuestMode && Firebase.auth.currentUser != null) {
                                val databaseId = context.getString(R.string.firestore_database_id)
                                CloudSyncRepository(FirebaseFirestore.getInstance(databaseId))
                            } else {
                                null
                            }
                        }
                        val aiVm = aiAdvisorViewModel ?: viewModel(
                            key = if (isGuestMode) "ai_guest" else "ai_${Firebase.auth.currentUser?.uid ?: "user"}",
                            factory = AiAdvisorViewModel.Factory(
                                repository = app.repository,
                                connectivityChecker = connectivityChecker,
                                cloudSyncRepository = cloudSyncRepo,
                                appContext = app.applicationContext
                            )
                        )
                        val aiState by aiVm.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(cloudSyncRepo) {
                            if (cloudSyncRepo != null) {
                                aiVm.syncChatsWithCloud()
                            }
                        }
                        AiAdvisorScreen(
                            mainState = state,
                            aiState = aiState,
                            onSendPrompt = { q -> aiVm.sendPrompt(state, q) },
                            onGeneratePlan = { aiVm.generateActionPlan(state) },
                            onAnalyzeReceipt = { text, bmp -> aiVm.analyzeReceiptOrMessage(state, text, bmp) },
                            onSaveExtractedTx = { extracted -> aiVm.saveExtractedTransaction(state, extracted) },
                            onClearChat = aiVm::clearChat,
                            onBack = { navController.popBackStack() },
                            onRetryLastPrompt = { aiVm.retryLastPrompt(state) },
                            onRetryOfflinePrompt = { aiVm.retryLastPrompt(state, forceOffline = true) },
                            onUndoActions = aiVm::undoExecutedMessageActions,
                            onVerifyConnection = { aiVm.verifyConnection(state.preferences.language) },
                            onSetEngineMode = { mode -> aiVm.setEngineMode(mode, state.preferences.language) },
                            onSelectSession = aiVm::selectChatSession,
                            onNewChat = aiVm::createNewChat,
                            onDeleteSession = aiVm::deleteChatSession,
                            onSyncChats = aiVm::syncChatsWithCloud,
                            onSaveExtractedTxSplit = { extracted, ways -> aiVm.saveExtractedTransaction(state, extracted, ways) },
                            onSaveExtractedItems = { extracted, ways -> aiVm.saveExtractedLineItems(state, extracted, ways) }
                        )
                    }
                    composable<WhatIfSimulatorRoute> {
                        WhatIfSimulatorScreen(
                            state = state,
                            onBack = { navController.popBackStack() }
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
                            onSetAutoApprove = viewModel::setSmartReaderAutoApprove,
                            onSetIgnoreOtp = viewModel::setReaderIgnoreOtp,
                            onSetIgnoreBalance = viewModel::setReaderIgnoreBalance,
                            onAnalyzePasted = viewModel::ingestSharedOrPastedText,
                            onAnalyzePastedWithAi = viewModel::ingestTextWithAi,
                            smartReaderAiState = smartReaderAiState,
                            onResetReaderAiState = viewModel::resetSmartReaderAiState,
                            onImportFile = { uri -> viewModel.importMessagesFromFile(context, uri) },
                            onApprovePending = viewModel::approvePending,
                            onRejectPending = viewModel::rejectPending,
                            onIgnoreSource = viewModel::ignoreFinancialSource,
                            onToggleSource = viewModel::toggleFinancialSource,
                            onAddCustomSource = viewModel::addCustomFinancialSource,
                            onClearImportData = viewModel::clearImportData,
                            onRunMockBatch = viewModel::runMockSmsBatch,
                            onBack = { navController.popBackStack() },
                            onReconcileBalance = viewModel::reconcileBalance,
                            onDismissDiscrepancy = viewModel::dismissDiscrepancy,
                            onInspectCsvStatement = { uri -> viewModel.readAndInspectBankStatement(context, uri) },
                            onImportBankStatement = viewModel::importBankStatement
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
                            onBack = { navController.popBackStack() },
                            onSaveEncryptedBackupToUri = { uri, pwd -> viewModel.writeEncryptedBackupToUri(context, uri, pwd) },
                            onInspectBackupUri = { uri -> viewModel.readBackupFileState(context, uri) },
                            onRestoreBackupContent = viewModel::restoreBackupContent
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
                            onSetPrivacyScreenGuard = viewModel::setPrivacyScreenGuard,
                            cloudUserName = cloudUserName,
                            cloudUserEmail = cloudUserEmail,
                            cloudProfileState = cloudProfileState,
                            cloudTransactionsState = cloudTransactionsState,
                            cloudActionState = cloudActionState,
                            onSyncToCloud = onSyncToCloud,
                            onRestoreFromCloud = onRestoreFromCloud,
                            onSignOut = onSignOut,
                            isGuestMode = isGuestMode,
                            onSignInFromGuest = onSignInFromGuest,
                            onExitGuestMode = onExitGuestMode,
                            onSaveCustomFxRates = viewModel::setCustomCurrencyRates,
                            onResetCustomFxRates = viewModel::resetCustomCurrencyRates,
                            onSetAutoCloudSyncEnabled = viewModel::setAutoCloudSyncEnabled
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
            autoFillState = quickAutoFillState,
            onAiAutoFill = viewModel::runAiAutoFill,
            onResetAiAutoFill = viewModel::resetAiAutoFillState,
            onSuggestCategory = viewModel::suggestCategoryWithAi,
            onDismiss = {
                showQuickAdd = false
                editingTx = null
                quickAddInitialType = null
            },
            onSave = { existingId, amountPiasters, type, accountId, targetId, catId, goalId, debtId, desc, notes, uri, learnRule, transferFee ->
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
                    learnMerchantRule = learnRule,
                    transferFeePiasters = transferFee
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
            SmoothReveal(index = 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
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
}
