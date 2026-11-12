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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.ui.theme.ThemePalettes
import com.example.ui.theme.ThemePresetSpec
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
    onSetPrivacyScreenGuard: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = state.preferences
    val lang = prefs.language
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(AppStrings.text("اللغة:", "Language:", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CurrencyConverter.supportedCurrencies, key = { it.code }) { spec ->
                            FilterChip(
                                selected = prefs.currencyCode == spec.code,
                                onClick = { onSetPrimaryCurrency(spec.code) },
                                label = { Text("${spec.code} • ${if (lang == AppLanguage.AR) spec.nameAr else spec.nameEn}") },
                                modifier = Modifier.testTag("primary_currency_${spec.code.lowercase()}")
                            )
                        }
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
                            "بياناتك محفوظة محليًا داخل قاعدة بيانات Room بدون إرسال لأي خادم خارجي. تم تهيئة Android Keystore لحماية مفاتيح الأمان.",
                            "Your data stays strictly local in Room with zero external servers. Android Keystore is initialized for vault protection.",
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
