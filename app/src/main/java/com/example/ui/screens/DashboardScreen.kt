package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.InsightSeverity
import com.example.domain.model.PendingStatus
import com.example.domain.model.TimeFilter
import com.example.domain.model.TimeRange
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.components.FinancialHealthPulseCard
import com.example.ui.components.HeroBalanceCard
import com.example.ui.components.IncomeVsExpenseBarChart
import com.example.ui.components.TimeFilterChipsBar
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalThemePresetSpec
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onSelectTimeFilter: (TimeFilter, TimeRange?) -> Unit,
    onTogglePrivacy: () -> Unit,
    onNavigateCalendar: () -> Unit = {},
    onNavigateReports: () -> Unit = {},
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onNavigateSmartReader: () -> Unit = {},
    onNavigateCategories: () -> Unit = {},
    onNavigateAiAdvisor: () -> Unit = {},
    onNavigateBnpl: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val displayCurrency = state.preferences.currencyCode
    val m = state.dashboardMetrics
    val unreadNotifications = state.notifications.count { !it.isRead }
    val pendingReaderCount = state.pendingTransactions.count { it.status == PendingStatus.PENDING }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    val positiveColor = semanticPositiveColor()
    val themeSpec = LocalThemePresetSpec.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 600.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_lazy_list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hydrogen Flagship Top Header (Crystal Core Orb + Brand + Notifications & Settings)
        item(key = "dash_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            .clip(CircleShape)
                            .border(
                                width = 1.25.dp,
                                brush = Brush.linearGradient(themeSpec.prismGlowColors),
                                shape = CircleShape
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hydrogen_ai_core_1794844129687),
                            contentDescription = "ELBAGORRY Hydrogen Core",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    Column {
                        Text(
                            text = "ELBAGORRY Money",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = AppStrings.text("محرك Gemini 4 Hydrogen", "Gemini 4 Hydrogen Suite", lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
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

        // 2. Period Filter Chips
        item(key = "dash_time_filters") {
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

        // 3. Hero Balance Card
        item(key = "dash_hero_balance") {
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

        // 4. Compact Smart Hub (AI Coach CTA + Smart Reader & Categories row)
        item(key = "dashboard_ai_coach_card_item") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val prismBorder = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                        positiveColor.copy(alpha = 0.50f)
                    )
                )
                Surface(
                    onClick = onNavigateAiAdvisor,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.1.dp, prismBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_ai_coach_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.2.dp, prismBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_hydrogen_ai_core_1794844129687),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = AppStrings.text("الشات الذكي والمنسق التلقائي", "AI Chat & Auto-Organizer", lang),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Text(
                                        text = AppStrings.text(
                                            "Gemini 4 Hydrogen + محلي بدون نت",
                                            "Gemini 4 Hydrogen + Offline AI",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = positiveColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = AppStrings.text("دردش الآن", "Open", lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinancialCard(
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("dashboard_smart_reader_card"),
                        borderColor = (if (pendingReaderCount > 0) GoldAccent else MaterialTheme.colorScheme.outline).copy(alpha = 0.25f),
                        onClick = onNavigateSmartReader
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
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SkyCyan.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SkyCyan, modifier = Modifier.size(17.dp))
                                }
                                Column {
                                    Text(
                                        text = AppStrings.text("القارئ الذكي", "Smart Reader", lang),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (pendingReaderCount > 0) {
                                            AppStrings.text("$pendingReaderCount للمراجعة", "$pendingReaderCount pending", lang)
                                        } else {
                                            AppStrings.text("قراءة الرسائل", "Parse SMS", lang)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (pendingReaderCount > 0) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = pendingReaderCount > 0,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Badge { Text("$pendingReaderCount") }
                            }
                        }
                    }

                    FinancialCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_categories_card"),
                        onClick = onNavigateCategories
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }
                            Column {
                                Text(
                                    text = AppStrings.text("الفئات", "Categories", lang),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${state.categories.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5 & 6. Period Cashflow Summary + Financial Health Pulse (Side-by-Side on wide screens)
        if (isWide) {
            item(key = "dash_wide_cashflow_and_health") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PeriodCashflowSummaryCard(
                            m = m,
                            lang = lang,
                            hide = hide,
                            displayCurrency = displayCurrency,
                            positiveColor = positiveColor
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FinancialHealthPulseCard(
                            report = state.healthReport,
                            lang = lang,
                            hideAmounts = hide,
                            currencyCode = displayCurrency,
                            onAskAiAdvisor = onNavigateAiAdvisor
                        )
                    }
                }
            }
        } else {
            item(key = "dash_cashflow_summary") {
                PeriodCashflowSummaryCard(
                    m = m,
                    lang = lang,
                    hide = hide,
                    displayCurrency = displayCurrency,
                    positiveColor = positiveColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item(key = "health_pulse_card") {
                FinancialHealthPulseCard(
                    report = state.healthReport,
                    lang = lang,
                    hideAmounts = hide,
                    currencyCode = displayCurrency,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onAskAiAdvisor = onNavigateAiAdvisor
                )
            }
        }

        // 7. Conditional BNPL & Deficit Pulse Card
        item(key = "bnpl_pulse_card") {
            val bnpl = state.bnplReport
            val def = bnpl.deficitAnalysis
            if (bnpl.totalCreditLimitPiasters > 0L || bnpl.activeInstallmentsCount > 0 || def.hasDeficit) {
                val pulseColor = when {
                    def.netMonthlySurplusOrDeficitPiasters < 0L -> CoralExpense
                    def.hasDeficit -> GoldAccent
                    else -> SkyCyan
                }
                FinancialCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("dashboard_bnpl_pulse_card"),
                    borderColor = pulseColor.copy(alpha = 0.35f),
                    onClick = onNavigateBnpl
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(pulseColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = pulseColor, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = AppStrings.text("تحليل برامج التقسيط والعجز", "BNPL & Deficit Analyzer", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (lang == AppLanguage.AR) def.headlineAr else def.headlineEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pulseColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = if (hide) "••%" else MoneyFormatter.formatPercent(def.installmentsToIncomePercent),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = pulseColor
                        )
                    }
                }
            }
        }

        // 8. Conditional Savings Goals Progress (only shown if user has goals)
        if (state.savingsGoals.isNotEmpty()) {
            item(key = "dash_savings_goals") {
                FinancialCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = AppStrings.text("تقدم أهداف الادخار", "Savings Progress", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.savingsGoals.take(3).forEach { goal ->
                            val ratio = if (goal.targetAmountPiasters > 0L) {
                                (goal.savedAmountPiasters.toFloat() / goal.targetAmountPiasters.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val animatedRatio by animateFloatAsState(
                                targetValue = ratio,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                                label = "goalProgress_${goal.id}"
                            )
                            val goalColor = parseHexColor(goal.colorHex, positiveColor)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(goal.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (hide) "••%" else "${(ratio * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                                        color = goalColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { animatedRatio },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
                                    color = goalColor,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9. Smart Insights + Link to Deep Analytics (No duplicate chart stack)
        item(key = "dash_analytics_cta") {
            val topInsight = state.analyticsSummary.insights.firstOrNull()
            val accentColor = when (topInsight?.severity) {
                InsightSeverity.POSITIVE -> positiveColor
                InsightSeverity.WARNING -> GoldAccent
                InsightSeverity.CRITICAL -> CoralExpense
                else -> SkyCyan
            }
            FinancialCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("dashboard_smart_insight_card"),
                borderColor = accentColor.copy(alpha = 0.30f),
                onClick = onOpenAnalytics
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
                        Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (topInsight != null) {
                                AppStrings.text(topInsight.titleAr, topInsight.titleEn, lang)
                            } else {
                                AppStrings.text("الرسوم البيانية والتحليلات الكاملة", "Full Charts & Deep Analytics", lang)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = AppStrings.text("التحليلات", "Analytics", lang),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (topInsight != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = AppStrings.text(topInsight.detailAr, topInsight.detailEn, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 10. Clean Borderless Accounts Strip
        item(key = "dash_accounts_strip") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.text("الحسابات النشطة", "Active Accounts", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.accounts.filter { !it.isArchived }, key = { it.id }) { acc ->
                        val accColor = parseHexColor(acc.colorHex)
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.width(168.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(accColor)
                                    )
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.formatPiasters(acc.currentBalancePiasters, acc.currency, lang, hide),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

@Composable
private fun PeriodCashflowSummaryCard(
    m: com.example.domain.model.DashboardMetrics,
    lang: AppLanguage,
    hide: Boolean,
    displayCurrency: String,
    positiveColor: Color,
    modifier: Modifier = Modifier
) {
    FinancialCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.text("ملخص التدفق للفترة", "Period Cashflow", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = (if (m.netCashFlowPiasters >= 0L) positiveColor else CoralExpense).copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = MoneyFormatter.formatPiasters(
                        m.netCashFlowPiasters,
                        currency = displayCurrency,
                        language = lang,
                        hideAmounts = hide,
                        showSign = true
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = if (m.netCashFlowPiasters >= 0L) positiveColor else CoralExpense,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = positiveColor, modifier = Modifier.size(14.dp))
                    Text(
                        text = AppStrings.text("الدخل", "Income", lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = MoneyFormatter.formatPiasters(m.totalIncomePiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = positiveColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = CoralExpense, modifier = Modifier.size(14.dp))
                    Text(
                        text = AppStrings.text("المصروفات", "Expenses", lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = MoneyFormatter.formatPiasters(m.totalExpensesPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = CoralExpense,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        IncomeVsExpenseBarChart(
            incomePiasters = m.totalIncomePiasters,
            expensePiasters = m.totalExpensesPiasters,
            lang = lang,
            hideAmounts = hide,
            currency = displayCurrency
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactInlineMetric(
                label = AppStrings.text("الميزانية المتبقية", "Budget Left", lang),
                value = MoneyFormatter.formatPiasters(m.remainingBudgetPiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                valueColor = if (m.remainingBudgetPiasters >= 0) positiveColor else CoralExpense
            )
            CompactInlineMetric(
                label = AppStrings.text("مستحقات (7 أيام)", "Due (7d)", lang),
                value = MoneyFormatter.formatPiasters(m.amountsDuePiasters, currency = displayCurrency, language = lang, hideAmounts = hide),
                valueColor = if (m.amountsDuePiasters > 0L) GoldAccent else MaterialTheme.colorScheme.onSurface
            )
            CompactInlineMetric(
                label = AppStrings.text("الادخار", "Savings Rate", lang),
                value = if (hide) "••%" else MoneyFormatter.formatPercent(m.savingsRatePercent),
                valueColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CompactInlineMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}
