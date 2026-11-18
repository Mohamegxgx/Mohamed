package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.BnplProgramEntity
import com.example.domain.engine.BnplPortfolioReport
import com.example.domain.engine.BnplProgramAnalytics
import com.example.domain.engine.BurdenLevel
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.InsightSeverity
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.components.SmoothReveal
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor

fun LazyListScope.bnplPortfolioAndDeficitItems(
    report: BnplPortfolioReport,
    currency: String,
    lang: AppLanguage,
    hideAmounts: Boolean,
    onEditProgram: (BnplProgramEntity) -> Unit,
    onAddInstallmentForProgram: (BnplProgramEntity) -> Unit,
    onOpenWhatIfSimulator: () -> Unit = {}
) {
    // 1. Portfolio Summary Card
    item(key = "bnpl_portfolio_card") {
        SmoothReveal(index = 0) {
            BnplPortfolioHeroCard(
                report = report,
                currency = currency,
                lang = lang,
                hideAmounts = hideAmounts,
                onOpenWhatIfSimulator = onOpenWhatIfSimulator
            )
        }
    }

    // 2. Financial Deficit Root-Cause Card
    item(key = "deficit_analysis_card") {
        SmoothReveal(index = 1) {
            DeficitRootCauseCard(report = report, currency = currency, lang = lang, hideAmounts = hideAmounts)
        }
    }

    // 3. Section Heading for BNPL Programs
    item(key = "bnpl_programs_header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.text("برامج التقسيط والليمت الائتماني", "BNPL Programs & Credit Limits", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${report.programAnalytics.size}",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // 4. Individual Program Cards
    items(report.programAnalytics, key = { "bnpl_${it.program.id}" }) { analytics ->
        BnplProgramCard(
            analytics = analytics,
            currency = currency,
            lang = lang,
            hideAmounts = hideAmounts,
            onEdit = { onEditProgram(analytics.program) },
            onAddInstallment = { onAddInstallmentForProgram(analytics.program) },
            modifier = Modifier.animateItem(
                fadeInSpec = tween(220),
                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        )
    }
}

@Composable
private fun BnplPortfolioHeroCard(
    report: BnplPortfolioReport,
    currency: String,
    lang: AppLanguage,
    hideAmounts: Boolean,
    onOpenWhatIfSimulator: () -> Unit = {}
) {
    val utilRatio = (report.overallUtilizationPercent.toFloat() / 100f).coerceIn(0f, 1f)
    val animUtil by animateFloatAsState(
        targetValue = utilRatio,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "bnplPortfolioUtil"
    )
    val positiveColor = semanticPositiveColor()
    val burdenColor = when (report.deficitAnalysis.burdenLevel) {
        BurdenLevel.CRITICAL -> CoralExpense
        BurdenLevel.HIGH -> GoldAccent
        BurdenLevel.MODERATE -> SkyCyan
        BurdenLevel.HEALTHY, BurdenLevel.NONE -> positiveColor
    }

    FinancialCard(
        elevated = true,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        modifier = Modifier.testTag("bnpl_portfolio_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        text = AppStrings.text("محفظة التقسيط الكلية", "Total BNPL Portfolio", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${report.activeInstallmentsCount} ${AppStrings.text("خطة نشطة", "active plans", lang)} • ${report.totalPaidInstallmentParts}/${report.totalPaidInstallmentParts + report.totalRemainingInstallmentParts} ${AppStrings.text("قسط مدفوع", "paid", lang)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = burdenColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, burdenColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = if (hideAmounts) "••%" else "${MoneyFormatter.formatPercent(report.deficitAnalysis.installmentsToIncomePercent)} ${AppStrings.text("من الدخل", "of income", lang)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = burdenColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStatPill(
                label = AppStrings.text("إجمالي الليمت", "Total Limit", lang),
                value = MoneyFormatter.formatPiasters(report.totalCreditLimitPiasters, currency, lang, hideAmounts),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MiniStatPill(
                label = AppStrings.text("المسحوب", "Utilized", lang),
                value = MoneyFormatter.formatPiasters(report.totalUtilizedPiasters, currency, lang, hideAmounts),
                color = GoldAccent,
                modifier = Modifier.weight(1f)
            )
            MiniStatPill(
                label = AppStrings.text("المتاح", "Available", lang),
                value = MoneyFormatter.formatPiasters(report.totalAvailableLimitPiasters, currency, lang, hideAmounts),
                color = positiveColor,
                modifier = Modifier.weight(1f)
            )
        }

        if (report.totalCreditLimitPiasters > 0L) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animUtil },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                color = if (report.overallUtilizationPercent >= 80.0) CoralExpense else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppStrings.text(
                    "نسبة استهلاك الليمت: ${MoneyFormatter.formatPercent(report.overallUtilizationPercent)}",
                    "Limit utilization: ${MoneyFormatter.formatPercent(report.overallUtilizationPercent)}",
                    lang
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (report.totalInterestPiasters > 0L || report.unlinkedInstallmentsCount > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppStrings.text(
                        "فوائد الأقساط: ${MoneyFormatter.formatPiasters(report.totalInterestPiasters, currency, lang, hideAmounts)} (${MoneyFormatter.formatPercent(report.averageInterestPercent)})",
                        "Financing Cost: ${MoneyFormatter.formatPiasters(report.totalInterestPiasters, currency, lang, hideAmounts)} (${MoneyFormatter.formatPercent(report.averageInterestPercent)})",
                        lang
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldAccent,
                    fontWeight = FontWeight.Medium
                )
                if (report.unlinkedInstallmentsCount > 0) {
                    Text(
                        text = AppStrings.text("+${report.unlinkedInstallmentsCount} غير مربوط", "+${report.unlinkedInstallmentsCount} unlinked", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenWhatIfSimulator,
            modifier = Modifier.fillMaxWidth().testTag("bnpl_open_what_if_btn")
        ) {
            Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(AppStrings.text("محاكاة قسط جديد (What-If)", "Simulate New Purchase / BNPL (What-If)", lang))
        }
    }
}

@Composable
private fun DeficitRootCauseCard(
    report: BnplPortfolioReport,
    currency: String,
    lang: AppLanguage,
    hideAmounts: Boolean
) {
    val analysis = report.deficitAnalysis
    val positiveColor = semanticPositiveColor()
    val accent = when {
        analysis.netMonthlySurplusOrDeficitPiasters < 0L -> CoralExpense
        analysis.hasDeficit -> GoldAccent
        else -> positiveColor
    }

    FinancialCard(
        borderColor = accent.copy(alpha = 0.45f),
        modifier = Modifier.testTag("deficit_analysis_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (analysis.hasDeficit) Icons.AutoMirrored.Rounded.TrendingDown else Icons.Rounded.Analytics,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = AppStrings.text("تحليل العجز المالي وتأثير الأقساط", "Deficit & Cashflow Root-Cause", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    text = MoneyFormatter.formatPiasters(analysis.netMonthlySurplusOrDeficitPiasters, currency, lang, hideAmounts, showSign = true),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (lang == AppLanguage.AR) analysis.headlineAr else analysis.headlineEn,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStatPill(
                label = AppStrings.text("الدخل الشهري", "Est. Income", lang),
                value = MoneyFormatter.formatPiasters(analysis.estimatedMonthlyIncomePiasters, currency, lang, hideAmounts),
                color = positiveColor,
                modifier = Modifier.weight(1f)
            )
            MiniStatPill(
                label = AppStrings.text("أقساط شهرية", "Installments", lang),
                value = MoneyFormatter.formatPiasters(analysis.monthlyInstallmentsLoadPiasters, currency, lang, hideAmounts),
                color = GoldAccent,
                modifier = Modifier.weight(1f)
            )
            MiniStatPill(
                label = AppStrings.text("التزامات + صرف", "Bills + Spend", lang),
                value = MoneyFormatter.formatPiasters(
                    analysis.monthlyRecurringExpensesPiasters + analysis.monthlyDiscretionarySpendPiasters,
                    currency,
                    lang,
                    hideAmounts
                ),
                color = SkyCyan,
                modifier = Modifier.weight(1f)
            )
        }

        if (analysis.causes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = AppStrings.text("أسباب الضغط / العجز المالي:", "Key Financial Pressure Drivers:", lang),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                analysis.causes.forEach { cause ->
                    val causeColor = when (cause.severity) {
                        InsightSeverity.CRITICAL -> CoralExpense
                        InsightSeverity.WARNING -> GoldAccent
                        InsightSeverity.INFO -> SkyCyan
                        InsightSeverity.POSITIVE -> positiveColor
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, causeColor.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (cause.severity == InsightSeverity.POSITIVE) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = causeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.AR) cause.titleAr else cause.titleEn,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = causeColor
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.formatPiasters(cause.amountPiasters, currency, lang, hideAmounts),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (lang == AppLanguage.AR) cause.detailAr else cause.detailEn,
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

@Composable
private fun BnplProgramCard(
    analytics: BnplProgramAnalytics,
    currency: String,
    lang: AppLanguage,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
    onAddInstallment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prog = analytics.program
    val accent = parseHexColor(prog.colorHex, SkyCyan)
    val utilFraction = (analytics.utilizationPercent.toFloat() / 100f).coerceIn(0f, 1f)
    val animUtil by animateFloatAsState(
        targetValue = utilFraction,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "progUtil_${prog.id}"
    )

    FinancialCard(
        borderColor = accent.copy(alpha = 0.42f),
        modifier = modifier.testTag("bnpl_program_card_${prog.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Column {
                    Text(
                        text = if (lang == AppLanguage.AR) prog.nameAr else prog.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (prog.creditLimitPiasters > 0L) {
                            "${AppStrings.text("الليمت:", "Limit:", lang)} ${MoneyFormatter.formatPiasters(prog.creditLimitPiasters, currency, lang, hideAmounts)}"
                        } else {
                            AppStrings.text("لم يتم تحديد الليمت بعد — اضغط تعديل", "Limit not set yet — tap Edit", lang)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_bnpl_btn_${prog.id}")) {
                    Icon(Icons.Rounded.Edit, contentDescription = AppStrings.text("تعديل الليمت", "Edit Limit", lang), tint = accent)
                }
                IconButton(onClick = onAddInstallment, modifier = Modifier.testTag("add_bnpl_inst_btn_${prog.id}")) {
                    Icon(Icons.Rounded.Add, contentDescription = AppStrings.text("إضافة قسط", "Add Plan", lang), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (prog.creditLimitPiasters > 0L || analytics.utilizedLimitPiasters > 0L) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animUtil },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
                color = if (analytics.utilizationPercent >= 85.0) CoralExpense else accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatPill(
                    label = AppStrings.text("المسحوب", "Drawn", lang),
                    value = MoneyFormatter.formatPiasters(analytics.utilizedLimitPiasters, currency, lang, hideAmounts),
                    color = GoldAccent,
                    modifier = Modifier.weight(1f)
                )
                MiniStatPill(
                    label = AppStrings.text("المتاح", "Remaining", lang),
                    value = MoneyFormatter.formatPiasters(analytics.availableLimitPiasters, currency, lang, hideAmounts),
                    color = semanticPositiveColor(),
                    modifier = Modifier.weight(1f)
                )
                MiniStatPill(
                    label = AppStrings.text("القسط الشهري", "Monthly", lang),
                    value = MoneyFormatter.formatPiasters(analytics.monthlyInstallmentLoadPiasters, currency, lang, hideAmounts),
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (analytics.activeCount > 0 || analytics.completedCount > 0 || analytics.totalInterestPiasters > 0L) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppStrings.text(
                        "مدفوع: ${analytics.paidPartsCount} • متبقي: ${analytics.remainingPartsCount} قسط",
                        "Paid: ${analytics.paidPartsCount} • Left: ${analytics.remainingPartsCount} parts",
                        lang
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (analytics.totalInterestPiasters > 0L) {
                    Text(
                        text = AppStrings.text(
                            "فائدة ${MoneyFormatter.formatPercent(analytics.effectiveInterestPercent)}",
                            "APR ${MoneyFormatter.formatPercent(analytics.effectiveInterestPercent)}",
                            lang
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        color = CoralExpense,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (analytics.incomeSharePercent > 0.0) {
                    Text(
                        text = "${MoneyFormatter.formatPercent(analytics.incomeSharePercent)} ${AppStrings.text("من الدخل", "of income", lang)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily), fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBnplProgramDialog(
    initialProgram: BnplProgramEntity?,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (existingId: Long?, nameAr: String, nameEn: String, creditLimitPiasters: Long, manualUsedPiasters: Long, defaultAnnualInterestPercent: Double, colorHex: String, notes: String) -> Unit,
    onDelete: ((BnplProgramEntity) -> Unit)? = null
) {
    var nameAr by remember(initialProgram) { mutableStateOf(initialProgram?.nameAr.orEmpty()) }
    var nameEn by remember(initialProgram) { mutableStateOf(initialProgram?.nameEn.orEmpty()) }
    var limitInput by remember(initialProgram) {
        mutableStateOf(
            if (initialProgram != null && initialProgram.creditLimitPiasters > 0L) {
                MoneyFormatter.piastersToDecimalString(initialProgram.creditLimitPiasters)
            } else ""
        )
    }
    var manualUsedInput by remember(initialProgram) {
        mutableStateOf(
            if (initialProgram != null && initialProgram.manualUsedPiasters > 0L) {
                MoneyFormatter.piastersToDecimalString(initialProgram.manualUsedPiasters)
            } else ""
        )
    }
    var interestInput by remember(initialProgram) {
        mutableStateOf(
            if (initialProgram != null && initialProgram.defaultAnnualInterestPercent > 0.0) {
                initialProgram.defaultAnnualInterestPercent.toString()
            } else ""
        )
    }
    var notes by remember(initialProgram) { mutableStateOf(initialProgram?.notes.orEmpty()) }
    var selectedColor by remember(initialProgram) { mutableStateOf(initialProgram?.colorHex ?: "#38BDF8") }

    val colorChoices = listOf("#22D3EE", "#4F8CFF", "#8B5CF6", "#F59E0B", "#38BDF8", "#F472B6", "#64748B")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initialProgram == null) AppStrings.text("إضافة برنامج تقسيط", "New BNPL Program", lang)
                    else AppStrings.text("تعديل ${initialProgram.nameAr}", "Edit ${initialProgram.nameEn}", lang)
                )
                if (initialProgram != null && onDelete != null && initialProgram.providerCode.startsWith("CUSTOM_")) {
                    IconButton(onClick = {
                        onDelete(initialProgram)
                        onDismiss()
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    label = { Text(AppStrings.text("اسم البرنامج (عربي)", "Program Name (AR)", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(AppStrings.text("الاسم (إنجليزي)", "Program Name (EN)", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text(AppStrings.text("الحد الائتماني الكلي / الليمت (EGP)", "Total Credit Limit (EGP)", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualUsedInput,
                    onValueChange = { manualUsedInput = it },
                    label = { Text(AppStrings.text("رصيد مسحوب خارج الأقساط المسجلة (اختياري)", "Extra Drawn Balance (Optional)", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = interestInput,
                    onValueChange = { interestInput = it },
                    label = { Text(AppStrings.text("معدل الفائدة الافتراضي (%)", "Default Interest Rate (%)", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(AppStrings.text("ملاحظات", "Notes", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorChoices.forEach { hex ->
                        val c = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 1.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameAr.isNotBlank() || nameEn.isNotBlank()) {
                    val limitP = MoneyFormatter.parseToPiasters(limitInput) ?: 0L
                    val manualP = MoneyFormatter.parseToPiasters(manualUsedInput) ?: 0L
                    val apr = interestInput.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSave(
                        initialProgram?.id,
                        nameAr.ifBlank { nameEn },
                        nameEn.ifBlank { nameAr },
                        limitP,
                        manualP,
                        apr,
                        selectedColor,
                        notes
                    )
                    onDismiss()
                }
            }) {
                Text(AppStrings.text("حفظ", "Save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.text("إلغاء", "Cancel", lang))
            }
        }
    )
}
