package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.FinancialHealthReport
import com.example.domain.engine.HealthGrade
import com.example.domain.model.AppLanguage
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor

@Composable
fun FinancialHealthPulseCard(
    report: FinancialHealthReport,
    lang: AppLanguage,
    hideAmounts: Boolean,
    currencyCode: String,
    modifier: Modifier = Modifier,
    onAskAiAdvisor: (() -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val positiveColor = semanticPositiveColor()
    val targetColor = when (report.grade) {
        HealthGrade.EXCELLENT -> positiveColor
        HealthGrade.BALANCED -> MaterialTheme.colorScheme.primary
        HealthGrade.NEEDS_ATTENTION -> GoldAccent
        HealthGrade.CRITICAL -> CoralExpense
    }
    val gradeColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(350), label = "healthGradeColor")
    val animatedScoreSweep by animateFloatAsState(
        targetValue = (report.score.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "healthScoreArc"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "healthChevron"
    )

    val gradeLabel = when (report.grade) {
        HealthGrade.EXCELLENT -> AppStrings.text("ممتاز", "Excellent", lang)
        HealthGrade.BALANCED -> AppStrings.text("متوازن", "Balanced", lang)
        HealthGrade.NEEDS_ATTENTION -> AppStrings.text("يحتاج انتباه", "Needs Focus", lang)
        HealthGrade.CRITICAL -> AppStrings.text("حرج", "Critical", lang)
    }

    val runwayLabel = when {
        hideAmounts -> "••"
        report.runwayDays >= 999 -> AppStrings.text("مريح +90 يوم", "90+ days", lang)
        else -> AppStrings.text("${report.runwayDays} يوم", "${report.runwayDays}d", lang)
    }

    FinancialCard(
        modifier = modifier,
        borderColor = gradeColor.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .semantics {
                    stateDescription = if (expanded) "expanded" else "collapsed"
                }
                .testTag("financial_health_pulse_card"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Animated Pulse Gauge
            val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    drawArc(
                        color = trackColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(gradeColor.copy(alpha = 0.65f), gradeColor, gradeColor)
                        ),
                        startAngle = 135f,
                        sweepAngle = 270f * animatedScoreSweep,
                        useCenter = false,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hideAmounts) "••" else "${report.score}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = gradeColor
                    )
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Summary & Safe-to-Spend Today
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.text("نبض الصحة المالية", "Financial Health", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = gradeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = gradeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = gradeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.text("آمن للصرف اليوم", "Safe Today", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedMoneyText(
                            piasters = report.safeToSpendTodayPiasters,
                            lang = lang,
                            hideAmounts = hideAmounts,
                            currency = currencyCode,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = AppStrings.text("أمان السيولة", "Runway", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = runwayLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = gradeColor
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = AppStrings.text("تفاصيل الصحة المالية", "Health details", lang),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("financial_health_breakdown"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                report.pillars.forEach { pillar ->
                    val ratio = (pillar.score.toFloat() / pillar.maxScore.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    val animRatio by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = tween(480, easing = FastOutSlowInEasing),
                        label = "pillar_${pillar.id}"
                    )
                    val pillarColor = when {
                        ratio >= 0.78f -> positiveColor
                        ratio >= 0.50f -> GoldAccent
                        else -> CoralExpense
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.text(pillar.titleAr, pillar.titleEn, lang),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (hideAmounts) "••/${pillar.maxScore}" else "${pillar.score}/${pillar.maxScore}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                                color = pillarColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animRatio },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
                            color = pillarColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        )
                        Text(
                            text = AppStrings.text(pillar.detailAr, pillar.detailEn, lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actionable Advice Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(gradeColor.copy(alpha = 0.11f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TipsAndUpdates,
                        contentDescription = null,
                        tint = gradeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = AppStrings.text(report.adviceAr, report.adviceEn, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (onAskAiAdvisor != null) {
                    OutlinedButton(
                        onClick = onAskAiAdvisor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("health_ask_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = AppStrings.text(
                                "اسأل المستشار المالي الذكي (Gemini)",
                                "Ask Gemini AI Financial Advisor",
                                lang
                            )
                        )
                    }
                }
            }
        }
    }
}
