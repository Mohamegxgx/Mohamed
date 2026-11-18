package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.InsightSeverity
import com.example.domain.model.PendingStatus
import com.example.ui.components.AppStrings
import com.example.ui.components.CashFlowBarChart
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.ScreenHeaderBar
import com.example.ui.components.TrendLineChart
import com.example.ui.components.WeeklyComparisonBarChart
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor
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
    onNavigateAiAdvisor: () -> Unit = {},
    onNavigateWhatIfSimulator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val displayCurrency = state.preferences.currencyCode
    val a = state.analyticsSummary
    val m = state.dashboardMetrics
    val positiveColor = semanticPositiveColor()
    val pendingReaderCount = state.pendingTransactions.count { it.status == PendingStatus.PENDING }
    val receiptsCount = state.transactions.count { !it.attachmentUri.isNullOrBlank() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 600.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "insights_header") {
            ScreenHeaderBar(
                title = AppStrings.text("التحليلات والأدوات الذكية", "Analytics & Smart Tools", lang),
                subtitle = AppStrings.text("الرسوم البيانية، الذكاء الاصطناعي، والتقارير", "Charts, AI coach & exports", lang),
                icon = Icons.Rounded.AutoGraph,
                modifier = Modifier.padding(horizontal = 0.dp)
            )
        }

        // Featured 2x2 AI & Smart Tools Grid
        item(key = "insights_smart_tools") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolQuickCard(
                        title = AppStrings.text("المدرب المالي الذكي", "AI Financial Coach", lang),
                        icon = Icons.Rounded.Psychology,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = onNavigateAiAdvisor,
                        modifier = Modifier.weight(1f).testTag("tool_ai_advisor")
                    )
                    ToolQuickCard(
                        title = if (pendingReaderCount > 0) {
                            AppStrings.text("القارئ الذكي ($pendingReaderCount)", "Smart Reader ($pendingReaderCount)", lang)
                        } else {
                            AppStrings.text("قارئ الرسائل الذكي", "Smart Reader", lang)
                        },
                        icon = Icons.Rounded.AutoAwesome,
                        accentColor = SkyCyan,
                        onClick = onNavigateSmartReader,
                        modifier = Modifier.weight(1f).testTag("tool_smart_reader")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolQuickCard(
                        title = AppStrings.text("خزنة الإيصالات ($receiptsCount)", "Receipts ($receiptsCount)", lang),
                        icon = Icons.Rounded.Receipt,
                        accentColor = GoldAccent,
                        onClick = onNavigateReceiptsVault,
                        modifier = Modifier.weight(1f).testTag("tool_receipts_vault")
                    )
                    ToolQuickCard(
                        title = AppStrings.text("الفئات وقواعد التجار", "Categories & Rules", lang),
                        icon = Icons.Rounded.Category,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateCategories,
                        modifier = Modifier.weight(1f).testTag("tool_categories")
                    )
                }
                ToolQuickCard(
                    title = AppStrings.text("محاكي القرارات المالية (What-If) — اختبر شراء أو قسط قبل التنفيذ", "Hydrogen What-If Financial Simulator", lang),
                    icon = Icons.Rounded.Science,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onNavigateWhatIfSimulator,
                    modifier = Modifier.fillMaxWidth().testTag("tool_what_if_sim")
                )
            }
        }

        // Compact Utilities Strip (Calendar, Reports, Alerts, Settings)
        item(key = "insights_utilities_strip") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactUtilityTile(
                    label = AppStrings.text("التقويم", "Calendar", lang),
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onNavigateCalendar,
                    modifier = Modifier.weight(1f).testTag("tool_calendar")
                )
                CompactUtilityTile(
                    label = AppStrings.text("التقارير", "Reports", lang),
                    icon = Icons.Rounded.Description,
                    onClick = onNavigateReports,
                    modifier = Modifier.weight(1f).testTag("tool_reports")
                )
                CompactUtilityTile(
                    label = AppStrings.text("التنبيهات", "Alerts", lang),
                    icon = Icons.Rounded.Notifications,
                    onClick = onNavigateNotifications,
                    modifier = Modifier.weight(1f).testTag("tool_notifications")
                )
                CompactUtilityTile(
                    label = AppStrings.text("الإعدادات", "Settings", lang),
                    icon = Icons.Rounded.Settings,
                    onClick = onNavigateSettings,
                    modifier = Modifier.weight(1f).testTag("tool_settings")
                )
            }
        }

        // Unified Spend Summary + Weekly Comparison (Side-by-Side on Wide Screens)
        if (isWide) {
            item(key = "insights_wide_summary_and_weekly") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SpendAndForecastCard(a = a, lang = lang, hide = hide, displayCurrency = displayCurrency, positiveColor = positiveColor)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        WeeklyComparisonCard(points = a.weeklyComparison, lang = lang, hide = hide, displayCurrency = displayCurrency)
                    }
                }
            }
            item(key = "insights_wide_charts") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryBreakdownCard(items = m.categoryBreakdown, lang = lang, hide = hide)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TrendAndCashFlowCard(points = m.trendSeries, lang = lang)
                    }
                }
            }
        } else {
            item(key = "insights_spend_and_forecast") {
                SpendAndForecastCard(a = a, lang = lang, hide = hide, displayCurrency = displayCurrency, positiveColor = positiveColor)
            }

            item(key = "insights_weekly_comparison_card") {
                WeeklyComparisonCard(points = a.weeklyComparison, lang = lang, hide = hide, displayCurrency = displayCurrency)
            }

            // Visual Charts Section
            item(key = "insights_category_donut") {
                CategoryBreakdownCard(items = m.categoryBreakdown, lang = lang, hide = hide)
            }

            item(key = "insights_trend_and_cashflow") {
                TrendAndCashFlowCard(points = m.trendSeries, lang = lang)
            }
        }

        // Local Smart Insights List
        if (a.insights.isNotEmpty()) {
            item(key = "insights_local_title") {
                Text(
                    text = AppStrings.text("رؤى ذكية محلية (بدون إنترنت)", "Local Smart Insights", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(a.insights, key = { it.id }) { insight ->
                val accent = when (insight.severity) {
                    InsightSeverity.POSITIVE -> positiveColor
                    InsightSeverity.WARNING -> GoldAccent
                    InsightSeverity.CRITICAL -> CoralExpense
                    InsightSeverity.INFO -> SkyCyan
                }
                FinancialCard(borderColor = accent.copy(alpha = 0.35f)) {
                    Text(
                        text = AppStrings.text(insight.titleAr, insight.titleEn, lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = AppStrings.text(insight.detailAr, insight.detailEn, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SpendAndForecastCard(
    a: com.example.domain.model.AnalyticsSummary,
    lang: AppLanguage,
    hide: Boolean,
    displayCurrency: String,
    positiveColor: Color
) {
    FinancialCard {
        Text(
            text = AppStrings.text("ملخص الإنفاق والتوقعات", "Spending & Monthly Forecast", lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpendCell(
                label = AppStrings.text("اليوم", "Daily Spend", lang),
                value = MoneyFormatter.formatPiasters(a.dailySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                color = CoralExpense,
                modifier = Modifier.weight(1f)
            )
            SpendCell(
                label = AppStrings.text("هذا الشهر", "Monthly Spend", lang),
                value = MoneyFormatter.formatPiasters(a.monthlySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                color = GoldAccent,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpendCell(
                label = AppStrings.text("السنة", "Yearly Spend", lang),
                value = MoneyFormatter.formatPiasters(a.yearlySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                color = SkyCyan,
                modifier = Modifier.weight(1f)
            )
            SpendCell(
                label = AppStrings.text("المتوسط اليومي", "Daily Avg", lang),
                value = MoneyFormatter.formatPiasters(a.averageDailySpendPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                color = positiveColor,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        )
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
            label = AppStrings.text("أكثر يوم إنفاقًا", "Peak Spending Day", lang),
            value = a.highestSpendDayLabel?.let {
                "$it (${MoneyFormatter.formatPiasters(a.highestSpendDayAmountPiasters, currency = displayCurrency, language = lang, hideAmounts = hide)})"
            } ?: "-"
        )
        AnalyticsRow(
            label = AppStrings.text("تغير المصروفات الشهري", "Expense vs Last Month", lang),
            value = a.previousPeriodExpenseDeltaPercent?.let {
                val sign = if (it > 0) "+" else ""
                "$sign${MoneyFormatter.formatPercent(it)}"
            } ?: "-"
        )
        AnalyticsRow(
            label = AppStrings.text("تغير الدخل الشهري", "Income vs Last Month", lang),
            value = a.previousPeriodIncomeDeltaPercent?.let {
                val sign = if (it > 0) "+" else ""
                "$sign${MoneyFormatter.formatPercent(it)}"
            } ?: "-"
        )
    }
}

@Composable
private fun WeeklyComparisonCard(
    points: List<com.example.domain.model.WeeklySpendComparisonPoint>,
    lang: AppLanguage,
    hide: Boolean,
    displayCurrency: String
) {
    FinancialCard(modifier = Modifier.testTag("insights_weekly_comparison_card")) {
        Text(
            text = AppStrings.text("مقارنة الإنفاق الأسبوعي (هذا الشهر مقابل السابق)", "Week-over-Week Expense Comparison", lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        if (points.isEmpty()) {
            EmptyStateCard(
                title = AppStrings.text("لا توجد مقارنة أسبوعية بعد", "No weekly comparison yet", lang),
                subtitle = AppStrings.text("أضف مصروفات لمقارنة أسابيع الشهر الحالي بالسابق", "Record expenses to compare weekly momentum", lang)
            )
        } else {
            WeeklyComparisonBarChart(
                points = points,
                lang = lang,
                hideAmounts = hide,
                currency = displayCurrency
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    items: List<com.example.domain.model.CategoryBreakdownItem>,
    lang: AppLanguage,
    hide: Boolean
) {
    FinancialCard {
        Text(
            text = AppStrings.text("توزيع فئات المصروفات", "Expense Categories", lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Text(
                text = AppStrings.text("لا توجد مصروفات مسجلة في هذه الفترة.", "No expenses recorded in this period.", lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CategoryDonutChart(
                items = items,
                lang = lang,
                hideAmounts = hide
            )
        }
    }
}

@Composable
private fun TrendAndCashFlowCard(
    points: List<com.example.domain.model.TrendPoint>,
    lang: AppLanguage
) {
    FinancialCard {
        Text(
            text = AppStrings.text("الاتجاه المالي والتدفق النقدي", "Financial Trend & Cash Flow", lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (points.isEmpty()) {
            EmptyStateCard(
                title = AppStrings.text("لا توجد بيانات زمنية بعد", "No trend data yet", lang),
                subtitle = AppStrings.text("أضف معاملات لعرض المنحنى المالي", "Add transactions to view financial trend", lang)
            )
        } else {
            TrendLineChart(points = points)
            Spacer(Modifier.height(14.dp))
            CashFlowBarChart(points = points)
        }
    }
}

@Composable
private fun ToolQuickCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinancialCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactUtilityTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpendCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnalyticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily), fontWeight = FontWeight.SemiBold)
    }
}
