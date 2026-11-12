package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import com.example.domain.model.PendingStatus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.InsightSeverity
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.components.KpiMetricTile
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.viewmodel.MainUiState

@Composable
fun InsightsScreen(
    state: MainUiState,
    onNavigateCalendar: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateSmartReader: () -> Unit = {},
    onNavigateCategories: () -> Unit = {},
    onNavigateReceiptsVault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val displayCurrency = state.preferences.currencyCode
    val a = state.analyticsSummary
    val pendingReaderCount = state.pendingTransactions.count { it.status == PendingStatus.PENDING }
    val receiptsCount = state.transactions.count { !it.attachmentUri.isNullOrBlank() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = AppStrings.text("مركز التحليلات والخدمات", "Financial Analytics & Tools", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Quick Tool Cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolQuickCard(
                    title = AppStrings.text("التقويم المالي", "Financial Calendar", lang),
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onNavigateCalendar,
                    modifier = Modifier.weight(1f).testTag("tool_calendar")
                )
                ToolQuickCard(
                    title = AppStrings.text("التقارير والتصدير", "Reports & Export", lang),
                    icon = Icons.Rounded.Description,
                    onClick = onNavigateReports,
                    modifier = Modifier.weight(1f).testTag("tool_reports")
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolQuickCard(
                    title = AppStrings.text("سجل التنبيهات", "Notifications", lang),
                    icon = Icons.Rounded.Notifications,
                    onClick = onNavigateNotifications,
                    modifier = Modifier.weight(1f).testTag("tool_notifications")
                )
                ToolQuickCard(
                    title = AppStrings.text("الأمان والإعدادات", "Security & Settings", lang),
                    icon = Icons.Rounded.Settings,
                    onClick = onNavigateSettings,
                    modifier = Modifier.weight(1f).testTag("tool_settings")
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolQuickCard(
                    title = if (pendingReaderCount > 0) {
                        AppStrings.text("القارئ الذكي ($pendingReaderCount)", "Smart Reader ($pendingReaderCount)", lang)
                    } else {
                        AppStrings.text("قارئ المعاملات الذكي", "Smart Money Reader", lang)
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = onNavigateSmartReader,
                    modifier = Modifier.weight(1f).testTag("tool_smart_reader")
                )
                ToolQuickCard(
                    title = AppStrings.text("إدارة الفئات وقواعد التجار", "Categories & Rules", lang),
                    icon = Icons.Rounded.Category,
                    onClick = onNavigateCategories,
                    modifier = Modifier.weight(1f).testTag("tool_categories")
                )
            }
        }
        item {
            ToolQuickCard(
                title = AppStrings.text(
                    "خزنة الإيصالات المرفقة ($receiptsCount)",
                    "Receipts Vault ($receiptsCount)",
                    lang
                ),
                icon = Icons.Rounded.Receipt,
                onClick = onNavigateReceiptsVault,
                modifier = Modifier.fillMaxWidth().testTag("tool_receipts_vault")
            )
        }

        // Spend Summary Metrics
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricTile(
                    title = AppStrings.text("الإنفاق اليومي", "Daily Spend", lang),
                    valueText = MoneyFormatter.formatPiasters(a.dailySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    accentColor = CoralExpense,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    title = AppStrings.text("الإنفاق الشهري", "Monthly Spend", lang),
                    valueText = MoneyFormatter.formatPiasters(a.monthlySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    accentColor = GoldAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricTile(
                    title = AppStrings.text("الإنفاق السنوي", "Yearly Spend", lang),
                    valueText = MoneyFormatter.formatPiasters(a.yearlySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    icon = Icons.Rounded.AutoGraph,
                    accentColor = SkyCyan,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    title = AppStrings.text("متوسط الإنفاق اليومي", "Daily Avg Spend", lang),
                    valueText = MoneyFormatter.formatPiasters(a.averageDailySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    accentColor = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Forecast & Extremes Card
        item {
            FinancialCard {
                Text(AppStrings.text("توقعات ومؤشرات الشهر", "Monthly Forecast & Extremes", lang), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                AnalyticsRow(
                    label = AppStrings.text("توقع الرصيد بنهاية الشهر", "Projected End-of-Month Balance", lang),
                    value = MoneyFormatter.formatPiasters(a.projectedEndOfMonthBalancePiasters, currency = displayCurrency, language = lang, hideAmounts = hide)
                )
                AnalyticsRow(
                    label = AppStrings.text("أعلى فئة إنفاق", "Highest Spend Category", lang),
                    value = a.highestCategory?.let {
                        val n = if (lang == AppLanguage.AR) it.nameAr else it.nameEn
                        "$n (${MoneyFormatter.formatPiasters(it.amountPiasters, currency = displayCurrency, language = lang, hideAmounts = hide)})"
                    } ?: "-"
                )
                AnalyticsRow(
                    label = AppStrings.text("أقل فئة إنفاق", "Lowest Spend Category", lang),
                    value = a.lowestCategory?.let {
                        val n = if (lang == AppLanguage.AR) it.nameAr else it.nameEn
                        "$n (${MoneyFormatter.formatPiasters(it.amountPiasters, currency = displayCurrency, language = lang, hideAmounts = hide)})"
                    } ?: "-"
                )
                AnalyticsRow(
                    label = AppStrings.text("أكثر يوم إنفاقًا", "Peak Spending Day", lang),
                    value = a.highestSpendDayLabel?.let {
                        "$it (${MoneyFormatter.formatPiasters(a.highestSpendDayAmountPiasters, currency = displayCurrency, language = lang, hideAmounts = hide)})"
                    } ?: "-"
                )
                AnalyticsRow(
                    label = AppStrings.text("اتجاه المصروفات عن الشهر السابق", "Expense Trend vs Last Month", lang),
                    value = a.previousPeriodExpenseDeltaPercent?.let {
                        val sign = if (it > 0) "+" else ""
                        "$sign${MoneyFormatter.formatPercent(it)}"
                    } ?: "-"
                )
                AnalyticsRow(
                    label = AppStrings.text("اتجاه الدخل عن الشهر السابق", "Income Trend vs Last Month", lang),
                    value = a.previousPeriodIncomeDeltaPercent?.let {
                        val sign = if (it > 0) "+" else ""
                        "$sign${MoneyFormatter.formatPercent(it)}"
                    } ?: "-"
                )
            }
        }

        // Local Smart Insights List
        item {
            Text(
                text = AppStrings.text("رؤى ذكية محلية (بدون إنترنت)", "Local Smart Insights", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(a.insights, key = { it.id }) { insight ->
            val accent = when (insight.severity) {
                InsightSeverity.POSITIVE -> EmeraldPrimary
                InsightSeverity.WARNING -> GoldAccent
                InsightSeverity.CRITICAL -> CoralExpense
                InsightSeverity.INFO -> SkyCyan
            }
            FinancialCard(borderColor = accent.copy(alpha = 0.5f)) {
                Text(AppStrings.text(insight.titleAr, insight.titleEn, lang), style = MaterialTheme.typography.titleMedium, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(AppStrings.text(insight.detailAr, insight.detailEn, lang), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ToolQuickCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinancialCard(modifier = modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AnalyticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily))
    }
}
