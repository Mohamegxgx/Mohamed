package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.WhatIfMode
import com.example.domain.engine.WhatIfPurchaseInput
import com.example.domain.engine.WhatIfSavingsInput
import com.example.domain.engine.WhatIfSimulatorEngine
import com.example.domain.model.AppLanguage
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalThemePresetSpec
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhatIfSimulatorScreen(
    state: MainUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val currency = state.preferences.currencyCode
    val hide = state.preferences.hideAmounts
    val positiveColor = semanticPositiveColor()
    val prismColors = LocalThemePresetSpec.current.prismGlowColors

    var mode by rememberSaveable { mutableStateOf(WhatIfMode.INSTALLMENT_PURCHASE) }
    var cashPriceText by rememberSaveable { mutableStateOf("10000") }
    var monthsFloat by rememberSaveable { mutableFloatStateOf(12f) }
    var aprFloat by rememberSaveable { mutableFloatStateOf(18f) }
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(state.bnplPrograms.firstOrNull()?.id) }

    var boostAmountText by rememberSaveable { mutableStateOf("1500") }
    var selectedGoalId by rememberSaveable { mutableStateOf<Long?>(state.savingsGoals.firstOrNull()?.id) }

    val cashPricePiasters = remember(cashPriceText) { MoneyFormatter.parseToPiasters(cashPriceText) ?: 0L }
    val boostPiasters = remember(boostAmountText) { MoneyFormatter.parseToPiasters(boostAmountText) ?: 0L }

    val purchaseResult = remember(
        cashPricePiasters,
        monthsFloat,
        aprFloat,
        selectedProgramId,
        state.bnplReport,
        state.dashboardMetrics,
        state.healthReport,
        currency
    ) {
        WhatIfSimulatorEngine.simulatePurchase(
            input = WhatIfPurchaseInput(
                cashPricePiasters = cashPricePiasters,
                months = monthsFloat.roundToInt().coerceIn(3, 36),
                annualInterestRatePercent = aprFloat.toDouble(),
                bnplProgramId = selectedProgramId
            ),
            bnplReport = state.bnplReport,
            metrics = state.dashboardMetrics,
            healthReport = state.healthReport,
            currencyCode = currency
        )
    }

    val savingsResult = remember(
        boostPiasters,
        selectedGoalId,
        state.dashboardMetrics,
        state.bnplReport,
        state.savingsGoals,
        currency
    ) {
        WhatIfSimulatorEngine.simulateSavingsBoost(
            input = WhatIfSavingsInput(
                monthlyBoostPiasters = boostPiasters,
                targetGoalId = selectedGoalId
            ),
            metrics = state.dashboardMetrics,
            bnplReport = state.bnplReport,
            goals = state.savingsGoals,
            currencyCode = currency
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            AppStrings.text("محاكي القرارات المالية (ماذا لو؟)", "What-If Financial Simulator", lang),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("whatif_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = AppStrings.text("رجوع", "Back", lang))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = modifier.fillMaxSize().padding(padding)) {
            val isWide = maxWidth >= 600.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("whatif_lazy_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "whatif_banner") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(prismColors.map { it.copy(alpha = 0.52f) })
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                Text(
                                    text = AppStrings.text("اختبر قرارك قبل التنفيذ", "Test Decisions Before Committing", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = AppStrings.text(
                                        "حساب فوري على رصيدك الحالي، التزاماتك، وحدود برامج التقسيط دون أي تعديل على بياناتك.",
                                        "Live simulation using your actual income, obligations & BNPL credit limits without altering records.",
                                        lang
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "whatif_mode_switch") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == WhatIfMode.INSTALLMENT_PURCHASE,
                            onClick = { mode = WhatIfMode.INSTALLMENT_PURCHASE },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            modifier = Modifier.testTag("whatif_mode_purchase")
                        ) {
                            Text(AppStrings.text("قسط / شراء جديد", "New Installment", lang), maxLines = 1)
                        }
                        SegmentedButton(
                            selected = mode == WhatIfMode.SAVINGS_BOOST,
                            onClick = { mode = WhatIfMode.SAVINGS_BOOST },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            modifier = Modifier.testTag("whatif_mode_savings")
                        ) {
                            Text(AppStrings.text("تسريع الادخار", "Boost Savings", lang), maxLines = 1)
                        }
                    }
                }

                item(key = "whatif_body") {
                    val controlsBlock: @Composable () -> Unit = {
                        if (mode == WhatIfMode.INSTALLMENT_PURCHASE) {
                            FinancialCard(elevated = true) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = AppStrings.text("تفاصيل الشراء أو القسط المقترح", "Proposed Purchase Parameters", lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = cashPriceText,
                                        onValueChange = { cashPriceText = it },
                                        label = { Text(AppStrings.text("السعر كاش ($currency)", "Cash Price ($currency)", lang)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("whatif_price_input")
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("3000", "10000", "25000", "50000").forEach { preset ->
                                            FilterChip(
                                                selected = cashPriceText == preset,
                                                onClick = { cashPriceText = preset },
                                                label = { Text(preset) }
                                            )
                                        }
                                    }

                                    Text(
                                        text = AppStrings.text(
                                            "مدة التقسيط: ${monthsFloat.roundToInt()} شهر",
                                            "Tenure: ${monthsFloat.roundToInt()} months",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Slider(
                                        value = monthsFloat,
                                        onValueChange = { monthsFloat = it },
                                        valueRange = 3f..36f,
                                        steps = 10
                                    )

                                    Text(
                                        text = AppStrings.text(
                                            "الفائدة السنوية (APR): ${aprFloat.roundToInt()}%",
                                            "Annual Interest Rate: ${aprFloat.roundToInt()}%",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Slider(
                                        value = aprFloat,
                                        onValueChange = { aprFloat = it },
                                        valueRange = 0f..45f
                                    )

                                    if (state.bnplPrograms.isNotEmpty()) {
                                        Text(
                                            text = AppStrings.text("ربط ببرنامج تقسيط (اختياري):", "Test Against BNPL Program:", lang),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = selectedProgramId == null,
                                                onClick = { selectedProgramId = null },
                                                label = { Text(AppStrings.text("بدون برنامج", "None", lang)) }
                                            )
                                            state.bnplPrograms.forEach { prog ->
                                                FilterChip(
                                                    selected = selectedProgramId == prog.id,
                                                    onClick = { selectedProgramId = prog.id },
                                                    label = { Text(if (lang == AppLanguage.AR) prog.nameAr else prog.nameEn) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            FinancialCard(elevated = true) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = AppStrings.text("خطة خفض المصروفات أو زيادة الادخار", "Monthly Savings Boost Plan", lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = boostAmountText,
                                        onValueChange = { boostAmountText = it },
                                        label = { Text(AppStrings.text("المبلغ الإضافي شهريًا ($currency)", "Extra Monthly Saving ($currency)", lang)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("whatif_boost_input")
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("500", "1500", "3000", "5000").forEach { preset ->
                                            FilterChip(
                                                selected = boostAmountText == preset,
                                                onClick = { boostAmountText = preset },
                                                label = { Text("+$preset") }
                                            )
                                        }
                                    }
                                    if (state.savingsGoals.isNotEmpty()) {
                                        Text(
                                            text = AppStrings.text("الهدف المستهدف:", "Target Savings Goal:", lang),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            state.savingsGoals.forEach { goal ->
                                                FilterChip(
                                                    selected = selectedGoalId == goal.id,
                                                    onClick = { selectedGoalId = goal.id },
                                                    label = { Text(goal.name) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val outcomeBlock: @Composable () -> Unit = {
                        if (mode == WhatIfMode.INSTALLMENT_PURCHASE) {
                            val accent = if (purchaseResult.feasible) positiveColor else CoralExpense
                            FinancialCard(
                                borderColor = accent.copy(alpha = 0.55f),
                                modifier = Modifier.testTag("whatif_purchase_result")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                imageVector = if (purchaseResult.feasible) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = accent
                                            )
                                            Text(
                                                text = if (lang == AppLanguage.AR) purchaseResult.verdictAr else purchaseResult.verdictEn,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (lang == AppLanguage.AR) purchaseResult.adviceAr else purchaseResult.adviceEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                    SimMetricRow(
                                        label = AppStrings.text("القسط الشهري المتوقع", "Est. Monthly Installment", lang),
                                        value = MoneyFormatter.formatPiasters(purchaseResult.monthlyInstallmentPiasters, currency, lang, hide),
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("إجمالي الفوائد الإضافية", "Total Interest Cost", lang),
                                        value = MoneyFormatter.formatPiasters(purchaseResult.totalInterestPiasters, currency, lang, hide),
                                        valueColor = GoldAccent
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("نسبة الأقساط للدخل بعد الشراء", "Installments / Income Ratio", lang),
                                        value = if (hide) "••%" else MoneyFormatter.formatPercent(purchaseResult.projectedInstallmentsToIncomePercent),
                                        valueColor = accent
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("الفائض الشهري المتوقع", "Projected Monthly Surplus", lang),
                                        value = MoneyFormatter.formatPiasters(purchaseResult.projectedMonthlySurplusPiasters, currency, lang, hide, showSign = true),
                                        valueColor = if (purchaseResult.projectedMonthlySurplusPiasters >= 0L) positiveColor else CoralExpense
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("المسموح لصرف اليوم بعد الالتزام", "Safe-to-Spend Today After", lang),
                                        value = MoneyFormatter.formatPiasters(purchaseResult.projectedSafeToSpendDailyPiasters, currency, lang, hide),
                                        valueColor = SkyCyan
                                    )

                                    purchaseResult.selectedProgramUtilizationAfterPercent?.let { utilAfter ->
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = SkyCyan, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = AppStrings.text(
                                                    "استهلاك ليمت البرنامج: ${MoneyFormatter.formatPercent(utilAfter)}",
                                                    "Program Credit Limit Usage: ${MoneyFormatter.formatPercent(utilAfter)}",
                                                    lang
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (purchaseResult.exceedsBnplLimit) CoralExpense else SkyCyan
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (utilAfter.toFloat() / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                                            color = if (purchaseResult.exceedsBnplLimit) CoralExpense else SkyCyan,
                                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                        )
                                    }
                                }
                            }
                        } else {
                            FinancialCard(
                                borderColor = positiveColor.copy(alpha = 0.55f),
                                modifier = Modifier.testTag("whatif_savings_result")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Savings, contentDescription = null, tint = positiveColor)
                                        Text(
                                            text = AppStrings.text("أثر خطة الادخار التراكمي", "Compounding Savings Impact", lang),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = positiveColor
                                        )
                                    }
                                    Text(
                                        text = if (lang == AppLanguage.AR) savingsResult.adviceAr else savingsResult.adviceEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    SimMetricRow(
                                        label = AppStrings.text("إضافي متوقع خلال سنة", "Extra Saved in 12 Months", lang),
                                        value = MoneyFormatter.formatPiasters(savingsResult.annualExtraSavedPiasters, currency, lang, hide),
                                        valueColor = positiveColor
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("الفائض الشهري الجديد", "New Monthly Surplus", lang),
                                        value = MoneyFormatter.formatPiasters(savingsResult.newMonthlySurplusPiasters, currency, lang, hide, showSign = true),
                                        valueColor = SkyCyan
                                    )
                                    SimMetricRow(
                                        label = AppStrings.text("معدل الادخار الجديد", "Projected Savings Rate", lang),
                                        value = if (hide) "••%" else MoneyFormatter.formatPercent(savingsResult.newSavingsRatePercent),
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                    if (savingsResult.targetGoalName != null && savingsResult.monthsToGoalAfter != null) {
                                        SimMetricRow(
                                            label = AppStrings.text(
                                                "الوصول لهدف «${savingsResult.targetGoalName}»",
                                                "Reach '${savingsResult.targetGoalName}'"
                                                , lang
                                            ),
                                            value = AppStrings.text(
                                                "${savingsResult.monthsToGoalAfter} شهر",
                                                "${savingsResult.monthsToGoalAfter} mo",
                                                lang
                                            ),
                                            valueColor = GoldAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1f)) { controlsBlock() }
                            Box(modifier = Modifier.weight(1f)) { outcomeBlock() }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            controlsBlock()
                            outcomeBlock()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimMetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
