package com.example.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.CurrencyConverter
import com.example.domain.model.AppLanguage
import com.example.domain.model.NotificationType
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThemePreset
import com.example.notifications.NotificationHelper
import com.example.security.AppSecurityHelper
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.data.remote.CloudTransaction
import com.example.data.remote.UserProfile
import com.example.ui.theme.ThemePalettes
import com.example.ui.theme.ThemePresetSpec
import com.example.ui.viewmodel.CloudSyncActionState
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.MainUiState
import com.example.widget.ElbagorryWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: MainUiState,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit
) {
    val lang = state.preferences.language
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.text("سجل الإشعارات", "Notifications", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.notifications.isNotEmpty()) {
                        TextButton(onClick = onMarkAllRead, modifier = Modifier.testTag("mark_all_read_btn")) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(AppStrings.text("تحديد كمقروء", "Mark Read", lang))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.notifications.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = AppStrings.text("لا توجد إشعارات حاليًا", "No notifications yet", lang),
                        subtitle = AppStrings.text("ستظهر هنا تنبيهات الأقساط، الميزانيات، والملخص المالي الدوري تلقائيًا", "Installment, budget, and periodic financial summaries will appear here", lang),
                        icon = Icons.Rounded.NotificationsNone
                    )
                }
            } else {
                items(state.notifications, key = { it.id }) { notif ->
                    val (icon, tint) = when (notif.type) {
                        NotificationType.FINANCIAL_SUMMARY -> Icons.Rounded.Summarize to MaterialTheme.colorScheme.primary
                        NotificationType.INSTALLMENT_DUE -> Icons.Rounded.CreditCard to GoldAccent
                        NotificationType.BUDGET_ALERT -> Icons.Rounded.PieChart to CoralExpense
                        NotificationType.GOAL_NEAR_COMPLETE -> Icons.Rounded.Savings to MaterialTheme.colorScheme.primary
                        NotificationType.SMART_READER_ALERT -> Icons.Rounded.AutoAwesome to SkyCyan
                        else -> Icons.Rounded.NotificationsActive to MaterialTheme.colorScheme.secondary
                    }
                    FinancialCard(
                        elevated = !notif.isRead,
                        borderColor = if (!notif.isRead) tint.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    formatReadableDateTime(notif.timestampMillis, lang),
                                    style = MaterialTheme.typography.labelSmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetBiometricLock: (Boolean) -> Unit,
    onToggleNotificationSetting: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onSetThemePreset: (ThemePreset) -> Unit = {},
    onSetSummaryFrequency: (SummaryFrequency) -> Unit = {},
    onSendSummaryNow: () -> Unit = {},
    onSetPrimaryCurrency: (String) -> Unit = {},
    onSetPrivacyScreenGuard: (Boolean) -> Unit = {},
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
    onSaveCustomFxRates: (String) -> Unit = {},
    onResetCustomFxRates: () -> Unit = {},
    onSetAutoCloudSyncEnabled: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = state.preferences
    val lang = prefs.language
    var showFxDialog by remember { mutableStateOf(false) }
    var hasNotifPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }
    val biometricSupported = remember { AppSecurityHelper.canAuthenticate(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.text("الإعدادات والأمان", "Settings & Security", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cloud Sync & Google Account (or Local Guest Mode Banner)
            item {
                if (isGuestMode) {
                    FinancialCard(
                        elevated = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        modifier = Modifier.testTag("guest_mode_settings_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                AppStrings.text("وضع الضيف (حفظ محلي فقط)", "Guest Mode (Local Storage Only)", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = AppStrings.text(
                                "بياناتك الحالية محفوظة محليًا على هذا الجهاز. سجل الدخول بحساب Google في أي وقت لرفع بياناتك ومزامنتها سحابيًا.",
                                "Your data is currently stored locally on this device. Sign in with Google anytime to enable Cloud Sync.",
                                lang
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onSignInFromGuest,
                            modifier = Modifier.fillMaxWidth().testTag("guest_sign_in_google_btn")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.text("ربط بحساب Google للمزامنة", "Sign in with Google to Sync", lang))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onExitGuestMode,
                            modifier = Modifier.fillMaxWidth().testTag("exit_guest_mode_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.text("العودة لشاشة تسجيل الدخول", "Return to Sign-In Screen", lang))
                        }
                    }
                } else {
                FinancialCard(elevated = true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            AppStrings.text("المزامنة السحابية وحساب Google", "Cloud Sync & Google Account", lang),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    val profileData = (cloudProfileState as? CloudUiState.Success)?.data
                    val displayName = cloudUserName?.takeIf { it.isNotBlank() }
                        ?: profileData?.displayName
                        ?: AppStrings.text("مستخدم ELBAGORRY", "ELBAGORRY User", lang)
                    val emailText = cloudUserEmail?.takeIf { it.isNotBlank() }
                        ?: profileData?.email
                        ?: AppStrings.text("متصل بالمزامنة السحابية الآمنة", "Connected to Firebase Cloud Sync", lang)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = emailText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    val cloudStatusText = when (cloudTransactionsState) {
                        is CloudUiState.Loading -> AppStrings.text("جاري مزامنة الحالة السحابية...", "Checking cloud ledger...", lang)
                        is CloudUiState.Success -> AppStrings.text(
                            "المعاملات المحفوظة سحابيًا: ${cloudTransactionsState.data.size} معاملة",
                            "Synced in cloud: ${cloudTransactionsState.data.size} transactions",
                            lang
                        )
                        is CloudUiState.Error -> AppStrings.text(
                            "حالة السحابة: ${cloudTransactionsState.message}",
                            "Cloud status: ${cloudTransactionsState.message}",
                            lang
                        )
                    }
                    Text(
                        text = cloudStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    profileData?.lastSyncedAtMillis?.let { syncedAt ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = AppStrings.text(
                                "آخر مزامنة: ${formatReadableDateTime(syncedAt, lang)}",
                                "Last synced: ${formatReadableDateTime(syncedAt, lang)}",
                                lang
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = cloudActionState.statusMessage != null || cloudActionState.errorMessage != null) {
                        val isError = cloudActionState.errorMessage != null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Text(
                                text = cloudActionState.errorMessage ?: cloudActionState.statusMessage.orEmpty(),
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = AppStrings.text(
                            "المزامنة السحابية التلقائية الشاملة (13 جدول)",
                            "Automatic Full Cloud Sync (13 Collections)",
                            lang
                        ),
                        checked = prefs.autoCloudSyncEnabled,
                        enabled = true,
                        onCheckedChange = onSetAutoCloudSyncEnabled,
                        modifier = Modifier.testTag("auto_cloud_sync_switch")
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onSyncToCloud,
                        enabled = !cloudActionState.isSyncing,
                        modifier = Modifier.fillMaxWidth().testTag("sync_to_cloud_btn")
                    ) {
                        if (cloudActionState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(AppStrings.text("رفع ومزامنة البيانات للسحابة", "Sync Local Data to Cloud", lang))
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRestoreFromCloud,
                        enabled = !cloudActionState.isSyncing,
                        modifier = Modifier.fillMaxWidth().testTag("restore_from_cloud_btn")
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("استعادة البيانات من السحابة", "Restore Data from Cloud", lang))
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = !cloudActionState.isSyncing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().testTag("google_sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("تسجيل الخروج من Google", "Sign Out of Google", lang))
                    }
                }
                }
            }

            // Appearance, Theme Presets & Language
            item {
                FinancialCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(AppStrings.text("المظهر والثيمات اللونية", "Appearance & Color Themes", lang), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(AppStrings.text("الوضع البصري:", "Display Mode:", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            label = { Text(AppStrings.text("داكن", "Dark", lang)) },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            label = { Text(AppStrings.text("فاتح", "Light", lang)) },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            label = { Text(AppStrings.text("النظام", "System", lang)) },
                            modifier = Modifier.testTag("theme_system_chip")
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(AppStrings.text("اختر لوحة الألوان:", "Choose Color Preset:", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemePalettes.presets.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowPresets.forEach { spec ->
                                    ThemeSwatchCard(
                                        spec = spec,
                                        selected = prefs.themePreset == spec.preset,
                                        lang = lang,
                                        onClick = { onSetThemePreset(spec.preset) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowPresets.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Language & Primary Currency
            item {
                FinancialCard {
                    Text(AppStrings.text("اللغة:", "Language:", lang), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.language == AppLanguage.AR,
                            onClick = { onSetLanguage(AppLanguage.AR) },
                            label = { Text("العربية (RTL)") },
                            modifier = Modifier.testTag("lang_ar_chip")
                        )
                        FilterChip(
                            selected = prefs.language == AppLanguage.EN,
                            onClick = { onSetLanguage(AppLanguage.EN) },
                            label = { Text("English") },
                            modifier = Modifier.testTag("lang_en_chip")
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = AppStrings.text("العملة الأساسية للعرض والتحويل:", "Primary Display Currency:", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().testTag("currency_selector_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CurrencyConverter.supportedCurrencies, key = { it.code }) { spec ->
                            FilterChip(
                                selected = prefs.currencyCode == spec.code,
                                onClick = { onSetPrimaryCurrency(spec.code) },
                                label = { Text("${spec.code} • ${if (lang == AppLanguage.AR) spec.nameAr else spec.nameEn}") },
                                modifier = Modifier.testTag("primary_currency_${spec.code.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    val effectiveSpecs = remember(prefs.customCurrencyRatesRaw) {
                        CurrencyConverter.allSpecsWithEffectiveRates().filter { it.code != "EGP" }
                    }
                    Text(
                        text = effectiveSpecs.take(3).joinToString(" • ") {
                            "1 ${it.code} = ${it.rateToEgp.stripTrailingZeros().toPlainString()} EGP"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showFxDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("edit_fx_rates_btn")
                    ) {
                        Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("تعديل أسعار الصرف المخصصة", "Edit Live FX Rates", lang))
                    }
                }
            }

            // Home Screen Widget Card
            item {
                FinancialCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(AppStrings.text("ويدجت الشاشة الرئيسية", "Home Screen Widget", lang), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = AppStrings.text(
                            "أضف ويدجت الرصيد ومصروف اليوم إلى شاشتك الرئيسية مع زر إضافة سريعة وإخفاء الأرقام بنقرة واحدة.",
                            "Add the Net Balance & Today's Spend widget to your home screen with Quick Add and one-tap privacy toggle.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val manager = AppWidgetManager.getInstance(context)
                                val provider = ComponentName(context, ElbagorryWidgetReceiver::class.java)
                                if (manager.isRequestPinAppWidgetSupported) {
                                    manager.requestPinAppWidget(provider, null, null)
                                } else {
                                    Toast.makeText(
                                        context,
                                        AppStrings.text("يمكنك إضافة الويدجت بالضغط مطولًا على الشاشة الرئيسية", "Long-press your home screen to add the widget", lang),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    AppStrings.text("أضف الويدجت من قائمة الويدجات على الشاشة الرئيسية", "Add from your launcher's widgets menu", lang),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pin_widget_btn")
                    ) {
                        Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.text("إضافة الويدجت للشاشة الرئيسية", "Pin Widget to Home Screen", lang))
                    }
                }
            }

            // Security & Privacy
            item {
                FinancialCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(AppStrings.text("الأمان والخصوصية", "Security & Privacy", lang), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = AppStrings.text("قفل التطبيق بالبصمة / قفل الجهاز", "Biometric / Device Lock", lang),
                        checked = prefs.biometricLockEnabled,
                        enabled = biometricSupported,
                        onCheckedChange = onSetBiometricLock
                    )
                    if (!biometricSupported) {
                        Text(
                            text = AppStrings.text("يرجى تفعيل بصمة أو رمز قفل على الجهاز أولًا لتفعيل قفل التطبيق.", "Enable device PIN or biometrics in Android settings first.", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = AppStrings.text("حماية الشاشة ومنع لقطات الشاشة (FLAG_SECURE)", "Screen Privacy Guard (Block Screenshots)", lang),
                        checked = prefs.privacyScreenGuardEnabled,
                        modifier = Modifier.testTag("privacy_screen_guard_switch"),
                        onCheckedChange = onSetPrivacyScreenGuard
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = AppStrings.text(
                            "بياناتك محفوظة محليًا داخل قاعدة بيانات Room وتتم مزامنتها بشكل خاص ومعزول عبر حسابك في Firebase Firestore فقط، مع تهيئة Android Keystore لحماية مفاتيح الأمان.",
                            "Your data is stored locally in Room and privately synced to your isolated Firebase Firestore account, with Android Keystore protecting vault keys.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Interactive Notifications & Periodic Summary
            item {
                FinancialCard {
                    Text(AppStrings.text("الإشعارات الذكية والملخص الدوري", "Smart Notifications & Periodic Summary", lang), style = MaterialTheme.typography.titleMedium)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(AppStrings.text("السماح بالإشعارات", "Grant Notification Permission", lang))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = AppStrings.text("ملخص مالي دوري (يومي / أسبوعي)", "Periodic Financial Summary", lang),
                        checked = prefs.notifyPeriodicSummary,
                        modifier = Modifier.testTag("notify_summary_switch")
                    ) {
                        onToggleNotificationSetting("summary", it)
                    }
                    AnimatedVisibility(visible = prefs.notifyPeriodicSummary) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = prefs.summaryFrequency == SummaryFrequency.DAILY,
                                    onClick = { onSetSummaryFrequency(SummaryFrequency.DAILY) },
                                    label = { Text(AppStrings.text("ملخص يومي", "Daily", lang)) },
                                    modifier = Modifier.testTag("summary_freq_daily")
                                )
                                FilterChip(
                                    selected = prefs.summaryFrequency == SummaryFrequency.WEEKLY,
                                    onClick = { onSetSummaryFrequency(SummaryFrequency.WEEKLY) },
                                    label = { Text(AppStrings.text("ملخص أسبوعي", "Weekly", lang)) },
                                    modifier = Modifier.testTag("summary_freq_weekly")
                                )
                            }
                            OutlinedButton(
                                onClick = onSendSummaryNow,
                                modifier = Modifier.fillMaxWidth().testTag("send_summary_now_btn")
                            ) {
                                Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(AppStrings.text("إرسال الملخص المالي الآن", "Send Financial Summary Now", lang))
                            }
                        }
                    }

                    SettingSwitchRow(AppStrings.text("مواعيد الأقساط (مع زر تأكيد السداد)", "Installment Due (with Quick Pay)", lang), prefs.notifyInstallments) {
                        onToggleNotificationSetting("installments", it)
                    }
                    SettingSwitchRow(AppStrings.text("قرب استحقاق الفواتير", "Upcoming Bills", lang), prefs.notifyBills) {
                        onToggleNotificationSetting("bills", it)
                    }
                    SettingSwitchRow(AppStrings.text("تجاوز الميزانية (80% / 100%)", "Budget Warnings", lang), prefs.notifyBudgets) {
                        onToggleNotificationSetting("budgets", it)
                    }
                    SettingSwitchRow(AppStrings.text("تذكير إضافة المصروفات", "Expense Reminder", lang), prefs.notifyDailyReminder) {
                        onToggleNotificationSetting("daily", it)
                    }
                    SettingSwitchRow(AppStrings.text("موعد الراتب", "Salary Reminders", lang), prefs.notifySalary) {
                        onToggleNotificationSetting("salary", it)
                    }
                    SettingSwitchRow(AppStrings.text("اقتراب اكتمال هدف الادخار", "Goal Near Completion", lang), prefs.notifyGoals) {
                        onToggleNotificationSetting("goals", it)
                    }
                }
            }
        }
    }

    if (showFxDialog) {
        val editableSpecs = remember(prefs.customCurrencyRatesRaw) {
            CurrencyConverter.allSpecsWithEffectiveRates().filter { it.code != "EGP" }
        }
        val rateInputs = remember(showFxDialog, prefs.customCurrencyRatesRaw) {
            mutableStateMapOf<String, String>().apply {
                editableSpecs.forEach { spec ->
                    put(spec.code, spec.rateToEgp.stripTrailingZeros().toPlainString())
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showFxDialog = false },
            modifier = Modifier.testTag("fx_rates_dialog"),
            title = { Text(AppStrings.text("أسعار الصرف مقابل الجنيه (EGP)", "Exchange Rates vs EGP", lang)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    editableSpecs.forEach { spec ->
                        OutlinedTextField(
                            value = rateInputs[spec.code].orEmpty(),
                            onValueChange = { rateInputs[spec.code] = it },
                            label = { Text("${spec.code} (${if (lang == AppLanguage.AR) spec.nameAr else spec.nameEn})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fx_rate_input_${spec.code.lowercase()}")
                        )
                    }
                    TextButton(
                        onClick = {
                            onResetCustomFxRates()
                            showFxDialog = false
                        },
                        modifier = Modifier.testTag("fx_reset_btn")
                    ) {
                        Text(AppStrings.text("استعادة الأسعار الافتراضية", "Reset to Defaults", lang))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = mutableMapOf<String, BigDecimal>()
                        editableSpecs.forEach { spec ->
                            val rawVal = rateInputs[spec.code].orEmpty().replace(",", ".").trim()
                            val parsed = rawVal.toBigDecimalOrNull()
                            if (parsed != null && parsed > BigDecimal.ZERO) {
                                updated[spec.code] = parsed
                            }
                        }
                        onSaveCustomFxRates(CurrencyConverter.serializeRates(updated))
                        showFxDialog = false
                    },
                    modifier = Modifier.testTag("fx_save_btn")
                ) {
                    Text(AppStrings.text("حفظ الأسعار", "Save Rates", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFxDialog = false }) {
                    Text(AppStrings.text("إلغاء", "Cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun ThemeSwatchCard(
    spec: ThemePresetSpec,
    selected: Boolean,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) spec.swatchPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swatchBorder"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("theme_preset_${spec.preset.name.lowercase()}")
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(spec.swatchPrimary))
                    Box(Modifier.size(16.dp).clip(CircleShape).background(spec.swatchSecondary))
                    Box(Modifier.size(16.dp).clip(CircleShape).background(spec.swatchSurface))
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = spec.swatchPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = if (lang == AppLanguage.AR) spec.labelAr else spec.labelEn,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
