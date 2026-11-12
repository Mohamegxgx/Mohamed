package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Settings
import com.example.domain.model.PendingStatus
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.InsightSeverity
import com.example.domain.model.TimeFilter
import com.example.domain.model.TimeRange
import com.example.ui.components.AppStrings
import com.example.ui.components.CashFlowBarChart
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.HeroBalanceCard
import com.example.ui.components.IncomeVsExpenseBarChart
import com.example.ui.components.KpiMetricTile
import com.example.ui.components.TimeFilterChipsBar
import com.example.ui.components.TrendLineChart
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onSelectTimeFilter: (TimeFilter, TimeRange?) -> Unit,
    onTogglePrivacy: () -> Unit,
    onNavigateCalendar: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onNavigateSmartReader: () -> Unit = {},
    onNavigateCategories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val displayCurrency = state.preferences.currencyCode
    val m = state.dashboardMetrics
    val unreadNotifications = state.notifications.count { !it.isRead }
    val pendingReaderCount = state.pendingTransactions.count { it.status == PendingStatus.PENDING }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("dashboard_lazy_list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ELBAGORRY Money",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = AppStrings.text("مركز التحكم المالي", "Financial Control Center", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateCalendar, modifier = Modifier.testTag("nav_calendar_btn")) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = AppStrings.text("التقويم المالي", "Calendar", lang))
                    }
                    IconButton(onClick = onNavigateReports, modifier = Modifier.testTag("nav_reports_btn")) {
                        Icon(Icons.Rounded.Description, contentDescription = AppStrings.text("التقارير", "Reports", lang))
                    }
                    IconButton(onClick = onNavigateNotifications, modifier = Modifier.testTag("nav_notifications_btn")) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge { Text("$unreadNotifications") }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = AppStrings.text("الإشعارات", "Notifications", lang))
                        }
                    }
                    IconButton(onClick = onNavigateSettings, modifier = Modifier.testTag("nav_settings_btn")) {
                        Icon(Icons.Rounded.Settings, contentDescription = AppStrings.text("الإعدادات", "Settings", lang))
                    }
                }
            }
        }

        // Time Filter Chips
        item {
            TimeFilterChipsBar(
                selectedFilter = state.timeFilter,
                lang = lang,
                onSelect = { filter ->
                    if (filter == TimeFilter.CUSTOM) {
                        showCustomRangePicker = true
                    } else {
                        onSelectTimeFilter(filter, null)
                    }
                }
            )
        }

        // Hero Net Balance Card
        item {
            HeroBalanceCard(
                title = AppStrings.text("صافي الرصيد الحالي", "Net Current Balance", lang),
                balancePiasters = m.netBalancePiasters,
                subtitle = AppStrings.text("الأموال المتاحة للصرف", "Available Liquid Funds", lang),
                availablePiasters = m.availableFundsPiasters,
                hideAmounts = hide,
                lang = lang,
                onTogglePrivacy = onTogglePrivacy,
                todayExpensePiasters = state.todayExpensePiasters,
                currencyCode = state.preferences.currencyCode,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Smart Money Reader & Categories Quick Access Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinancialCard(
                    modifier = Modifier
                        .weight(1.4f)
                        .clickable { onNavigateSmartReader() }
                        .testTag("dashboard_smart_reader_card"),
                    elevated = pendingReaderCount > 0,
                    borderColor = (if (pendingReaderCount > 0) GoldAccent else MaterialTheme.colorScheme.primary).copy(alpha = 0.45f)
                ) {
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
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SkyCyan)
                            Column {
                                Text(
                                    text = AppStrings.text("القارئ الذكي", "Smart Reader", lang),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (pendingReaderCount > 0) {
                                        AppStrings.text("$pendingReaderCount معلقة للمراجعة", "$pendingReaderCount pending", lang)
                                    } else {
                                        AppStrings.text("تحليل الرسائل ماليًا", "Parse alerts", lang)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pendingReaderCount > 0) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = pendingReaderCount > 0,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Badge { Text("$pendingReaderCount") }
                        }
                    }
                }

                FinancialCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateCategories() }
                        .testTag("dashboard_categories_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = AppStrings.text("الفئات", "Categories", lang),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.categories.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 10 Key Financial Indicators (5 Clean 2-column rows)
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Total Income & Total Expenses
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricTile(
                        title = AppStrings.text("إجمالي الدخل", "Total Income", lang),
                        valueText = MoneyFormatter.formatPiasters(m.totalIncomePiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.ArrowDownward,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).testTag("kpi_income")
                    )
                    KpiMetricTile(
                        title = AppStrings.text("إجمالي المصروفات", "Total Expenses", lang),
                        valueText = MoneyFormatter.formatPiasters(m.totalExpensesPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.ArrowUpward,
                        accentColor = CoralExpense,
                        modifier = Modifier.weight(1f).testTag("kpi_expenses")
                    )
                }

                // Row 2: Expense/Income Ratio & Savings Rate
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricTile(
                        title = AppStrings.text("نسبة المصروفات للدخل", "Expense/Income Ratio", lang),
                        valueText = if (hide) "••%" else MoneyFormatter.formatPercent(m.expenseToIncomeRatioPercent),
                        icon = Icons.Rounded.Percent,
                        accentColor = GoldAccent,
                        modifier = Modifier.weight(1f).testTag("kpi_expense_ratio")
                    )
                    KpiMetricTile(
                        title = AppStrings.text("معدل الادخار", "Savings Rate", lang),
                        valueText = if (hide) "••%" else MoneyFormatter.formatPercent(m.savingsRatePercent),
                        icon = Icons.Rounded.Savings,
                        accentColor = SkyCyan,
                        modifier = Modifier.weight(1f).testTag("kpi_savings_rate")
                    )
                }

                // Row 3: Total Obligations & Due Amounts
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricTile(
                        title = AppStrings.text("إجمالي الالتزامات", "Total Obligations", lang),
                        valueText = MoneyFormatter.formatPiasters(m.totalObligationsPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.CreditCard,
                        accentColor = CoralExpense,
                        modifier = Modifier.weight(1f).testTag("kpi_obligations")
                    )
                    KpiMetricTile(
                        title = AppStrings.text("المبالغ المستحقة", "Amounts Due (7d)", lang),
                        valueText = MoneyFormatter.formatPiasters(m.amountsDuePiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.Schedule,
                        accentColor = GoldAccent,
                        modifier = Modifier.weight(1f).testTag("kpi_due_amounts")
                    )
                }

                // Row 4: Remaining Budget & Net Cash Flow (Financial Growth)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricTile(
                        title = AppStrings.text("الميزانية المتبقية", "Remaining Budget", lang),
                        valueText = MoneyFormatter.formatPiasters(m.remainingBudgetPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.PieChart,
                        accentColor = if (m.remainingBudgetPiasters >= 0) EmeraldPrimary else CoralExpense,
                        modifier = Modifier.weight(1f).testTag("kpi_remaining_budget")
                    )
                    KpiMetricTile(
                        title = AppStrings.text("تطور الوضع (التدفق)", "Net Cash Flow", lang),
                        valueText = MoneyFormatter.formatPiasters(m.netCashFlowPiasters, currency = displayCurrency, language = lang, hideAmounts = hide, showSign = true),
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        accentColor = if (m.netCashFlowPiasters >= 0) EmeraldPrimary else CoralExpense,
                        modifier = Modifier.weight(1f).testTag("kpi_cash_flow")
                    )
                }

                // Row 5: Available Funds & Allocated Savings
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricTile(
                        title = AppStrings.text("الأموال المتاحة", "Available Funds", lang),
                        valueText = MoneyFormatter.formatPiasters(m.availableFundsPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f).testTag("kpi_available_funds")
                    )
                    KpiMetricTile(
                        title = AppStrings.text("المدخرات المخصصة", "Savings Allocated", lang),
                        valueText = MoneyFormatter.formatPiasters(m.allocatedSavingsPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                        icon = Icons.Rounded.Savings,
                        accentColor = SkyCyan,
                        modifier = Modifier.weight(1f).testTag("kpi_allocated_savings")
                    )
                }
            }
        }

        // Smart Insights Preview
        item {
            val topInsight = state.analyticsSummary.insights.firstOrNull()
            if (topInsight != null) {
                val badgeColor = when (topInsight.severity) {
                    InsightSeverity.POSITIVE -> EmeraldPrimary
                    InsightSeverity.WARNING -> GoldAccent
                    InsightSeverity.CRITICAL -> CoralExpense
                    InsightSeverity.INFO -> SkyCyan
                }
                FinancialCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { onOpenAnalytics() }
                        .testTag("dashboard_smart_insight_card"),
                    borderColor = badgeColor.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.text(topInsight.titleAr, topInsight.titleEn, lang),
                            style = MaterialTheme.typography.titleMedium,
                            color = badgeColor
                        )
                        Text(
                            text = AppStrings.text("عرض التحليلات ←", "Analytics →", lang),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = AppStrings.text(topInsight.detailAr, topInsight.detailEn, lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chart 1: Income vs Expenses
        item {
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = AppStrings.text("الدخل مقابل المصروفات", "Income vs Expenses", lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                IncomeVsExpenseBarChart(
                    incomePiasters = m.totalIncomePiasters,
                    expensePiasters = m.totalExpensesPiasters,
                    lang = lang,
                    hideAmounts = hide,
                    currency = displayCurrency
                )
            }
        }

        // Chart 2: Monthly Financial Trend
        item {
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = AppStrings.text("الاتجاه المالي", "Financial Trend", lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                if (m.trendSeries.isEmpty()) {
                    EmptyStateCard(
                        title = AppStrings.text("لا توجد بيانات زمنية بعد", "No trend data yet", lang),
                        subtitle = AppStrings.text("أضف معاملات لعرض المنحنى المالي", "Add transactions to view financial trend", lang)
                    )
                } else {
                    TrendLineChart(points = m.trendSeries)
                }
            }
        }

        // Chart 3: Cash Flow Bars
        item {
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = AppStrings.text("صافي التدفق النقدي", "Cash Flow", lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                CashFlowBarChart(points = m.trendSeries)
            }
        }

        // Chart 4: Expense Categories Donut
        item {
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = AppStrings.text("توزيع فئات المصروفات", "Expense Categories", lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                if (m.categoryBreakdown.isEmpty()) {
                    Text(
                        text = AppStrings.text("لا توجد مصروفات مسجلة في هذه الفترة.", "No expenses recorded in this period.", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CategoryDonutChart(
                        items = m.categoryBreakdown,
                        lang = lang,
                        hideAmounts = hide
                    )
                }
            }
        }

        // Chart 5: Savings Progress
        item {
            FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = AppStrings.text("تقدم أهداف الادخار", "Savings Progress", lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(10.dp))
                if (state.savingsGoals.isEmpty()) {
                    Text(
                        text = AppStrings.text("لم تقم بإنشاء أهداف ادخار بعد.", "No savings goals created yet.", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.savingsGoals.take(4).forEach { goal ->
                            val ratio = if (goal.targetAmountPiasters > 0L) {
                                (goal.savedAmountPiasters.toFloat() / goal.targetAmountPiasters.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val goalColor = parseHexColor(goal.colorHex, EmeraldPrimary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (hide) "••%" else "${(ratio * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                                        color = goalColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                                    color = goalColor,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Accounts quick horizontal strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.text("الحسابات", "Accounts", lang),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.accounts.filter { !it.isArchived }) { acc ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.width(170.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(acc.colorHex))
                                    )
                                    Text(acc.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                                Text(
                                    text = MoneyFormatter.formatPiasters(acc.currentBalancePiasters, acc.currency, lang, hide),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = pickerState.selectedStartDateMillis
                        val e = pickerState.selectedEndDateMillis
                        if (s != null && e != null) {
                            onSelectTimeFilter(TimeFilter.CUSTOM, TimeRange(s, e + 86_399_999L))
                        }
                        showCustomRangePicker = false
                    }
                ) {
                    Text(AppStrings.text("تطبيق", "Apply", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangePicker = false }) {
                    Text(AppStrings.text("إلغاء", "Cancel", lang))
                }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
        }
    }
}
