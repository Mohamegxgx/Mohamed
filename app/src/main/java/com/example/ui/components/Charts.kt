package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.TrendPoint
import com.example.domain.model.WeeklySpendComparisonPoint
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor

@Composable
fun IncomeVsExpenseBarChart(
    incomePiasters: Long,
    expensePiasters: Long,
    lang: AppLanguage,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    currency: String = "EGP"
) {
    val maxVal = maxOf(incomePiasters, expensePiasters, 1L).toFloat()
    val incRatio by animateFloatAsState(
        targetValue = (incomePiasters / maxVal).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "incomeBar"
    )
    val expRatio by animateFloatAsState(
        targetValue = (expensePiasters / maxVal).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "expenseBar"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BarRow(
            label = AppStrings.text("الدخل", "Income", lang),
            valueText = MoneyFormatter.formatPiasters(incomePiasters, currency = currency, language = lang, hideAmounts = hideAmounts),
            ratio = incRatio,
            color = MaterialTheme.colorScheme.primary
        )
        BarRow(
            label = AppStrings.text("المصروفات", "Expenses", lang),
            valueText = MoneyFormatter.formatPiasters(expensePiasters, currency = currency, language = lang, hideAmounts = hideAmounts),
            ratio = expRatio,
            color = CoralExpense
        )
    }
}

@Composable
private fun BarRow(
    label: String,
    valueText: String,
    ratio: Float,
    color: Color
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                color = color
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(11.dp)) {
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(14f, 14f)
            )
            if (ratio > 0f) {
                val fillW = (size.width * ratio.coerceAtLeast(0.03f)).coerceAtMost(size.width)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.72f), color)
                    ),
                    size = Size(fillW, size.height),
                    cornerRadius = CornerRadius(14f, 14f)
                )
            }
        }
    }
}

@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val drawProgress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
    }
    val positiveColor = semanticPositiveColor()
    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    val progress = drawProgress.value

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .padding(vertical = 8.dp)
        ) {
            val maxVal = points.maxOf { maxOf(it.incomePiasters, it.expensePiasters, 1L) }.toFloat()
            val w = size.width
            val h = size.height
            val stepX = if (points.size <= 1) w else w / (points.size - 1)

            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            fun drawSeries(values: List<Long>, lineColor: Color, fill: Boolean) {
                if (values.size == 1) {
                    val y = h - (values[0] / maxVal) * (h * 0.85f) - h * 0.05f
                    drawCircle(lineColor.copy(alpha = 0.22f * progress), radius = 11.dp.toPx() * progress, center = Offset(w / 2f, y))
                    drawCircle(lineColor, radius = 6.dp.toPx() * progress, center = Offset(w / 2f, y))
                    return
                }
                val fullPath = Path()
                val fillPath = Path()
                var endPoint = Offset(0f, h)
                values.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = h - (v / maxVal) * (h * 0.85f) - h * 0.05f
                    if (idx == 0) {
                        fullPath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (idx - 1) * stepX
                        val prevY = h - (values[idx - 1] / maxVal) * (h * 0.85f) - h * 0.05f
                        val cx = (prevX + x) / 2f
                        fullPath.cubicTo(cx, prevY, cx, y, x, y)
                        fillPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                    if (idx == values.lastIndex) {
                        endPoint = Offset(x, y)
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }
                if (fill) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.24f * progress), Color.Transparent)
                        )
                    )
                }
                val measure = PathMeasure()
                measure.setPath(fullPath, false)
                val segPath = Path()
                measure.getSegment(0f, measure.length * progress, segPath, true)
                drawPath(
                    path = segPath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                if (progress > 0.85f) {
                    val dotAlpha = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    drawCircle(
                        color = lineColor.copy(alpha = 0.24f * dotAlpha),
                        radius = 7.dp.toPx(),
                        center = endPoint
                    )
                    drawCircle(
                        color = lineColor.copy(alpha = dotAlpha),
                        radius = 3.5.dp.toPx(),
                        center = endPoint
                    )
                }
            }

            drawSeries(points.map { it.incomePiasters }, positiveColor, fill = true)
            drawSeries(points.map { it.expensePiasters }, CoralExpense, fill = false)
            if (points.size > 1) {
                // Subtle plasma highlight dot over latest positive point
                val maxIncome = points.maxOf { maxOf(it.incomePiasters, it.expensePiasters, 1L) }.toFloat()
                val lastIncomeY = size.height - (points.last().incomePiasters / maxIncome) * (size.height * 0.85f) - size.height * 0.05f
                if (progress > 0.9f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(primaryAccent.copy(alpha = 0.25f), secondaryAccent.copy(alpha = 0.08f), Color.Transparent)
                        ),
                        radius = 14.dp.toPx(),
                        center = Offset(size.width, lastIncomeY)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = if (points.size <= 5) points else listOf(points.first(), points[points.size / 2], points.last())
            labels.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CashFlowBarChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val visible = points.takeLast(7)
    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val growProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        growProgress.snapTo(0f)
        growProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }
    val progress = growProgress.value
    val positiveColor = semanticPositiveColor()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(vertical = 6.dp)
        ) {
            val maxAbs = visible.maxOf { kotlin.math.abs(it.netPiasters) }.coerceAtLeast(1L).toFloat()
            val midY = size.height / 2f
            drawLine(zeroLineColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.5f)
            val slotW = size.width / visible.size
            val barW = (slotW * 0.48f).coerceAtMost(28.dp.toPx())

            visible.forEachIndexed { idx, pt ->
                val centerX = slotW * idx + slotW / 2f
                val ratio = (kotlin.math.abs(pt.netPiasters) / maxAbs).coerceIn(0.08f, 1f)
                val barH = (ratio * (midY * 0.85f)) * progress
                val isPos = pt.netPiasters >= 0L
                val topY = if (isPos) midY - barH else midY
                val barColor = if (isPos) positiveColor else CoralExpense
                if (barH > 0.5f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (isPos) {
                                listOf(barColor, barColor.copy(alpha = 0.65f))
                            } else {
                                listOf(barColor.copy(alpha = 0.65f), barColor)
                            }
                        ),
                        topLeft = Offset(centerX - barW / 2f, topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            visible.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    items: List<CategoryBreakdownItem>,
    lang: AppLanguage,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val topItems = items.take(5)
    val total = topItems.sumOf { it.amountPiasters }.coerceAtLeast(1L)
    var selectedIdx by remember(topItems) { mutableIntStateOf(-1) }

    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(topItems) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(128.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                var startAngle = -90f
                val baseStroke = 17.dp.toPx()
                topItems.forEachIndexed { idx, item ->
                    val rawSweep = (item.amountPiasters.toFloat() / total.toFloat()) * 360f
                    val animatedSweep = rawSweep * sweepProgress.value
                    val isSelected = selectedIdx == idx
                    val hasSelection = selectedIdx >= 0
                    val arcColor = parseHexColor(item.colorHex).let { c ->
                        if (hasSelection && !isSelected) c.copy(alpha = 0.35f) else c
                    }
                    if (animatedSweep > 0.5f) {
                        drawArc(
                            color = arcColor,
                            startAngle = startAngle,
                            sweepAngle = (animatedSweep - 2f).coerceAtLeast(1.5f),
                            useCenter = false,
                            style = Stroke(
                                width = if (isSelected) baseStroke * 1.28f else baseStroke,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    startAngle += rawSweep
                }
            }

            AnimatedContent(
                targetState = selectedIdx,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "donutCenter"
            ) { idx ->
                val focused = topItems.getOrNull(idx)
                if (focused != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (hideAmounts) "••%" else MoneyFormatter.formatPercent(focused.percentage),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = parseHexColor(focused.colorHex)
                        )
                        Text(
                            text = if (lang == AppLanguage.AR) focused.nameAr else focused.nameEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp)
                        )
                    }
                } else {
                    Text(
                        text = "${topItems.size}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = RobotoMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            topItems.forEachIndexed { idx, item ->
                val color = parseHexColor(item.colorHex)
                val isSelected = selectedIdx == idx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) color.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable {
                            selectedIdx = if (selectedIdx == idx) -1 else idx
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 11.dp else 9.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (lang == AppLanguage.AR) item.nameAr else item.nameEn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (hideAmounts) "••%" else MoneyFormatter.formatPercent(item.percentage),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CircularGoalRing(
    progress: Float,
    color: Color,
    size: Dp = 62.dp,
    label: String
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "goalRing"
    )
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(color.copy(alpha = 0.72f), color, color.copy(alpha = 0.9f))
                ),
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun WeeklyComparisonBarChart(
    points: List<WeeklySpendComparisonPoint>,
    lang: AppLanguage,
    hideAmounts: Boolean,
    currency: String = "EGP",
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
    }
    val progress = animProgress.value
    val maxVal = points.maxOf { maxOf(it.currentMonthSpendPiasters, it.previousMonthSpendPiasters, 1L) }.toFloat()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(currentColor))
                Text(AppStrings.text("الشهر الحالي", "This Month", lang), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(previousColor))
                Text(AppStrings.text("الشهر السابق", "Last Month", lang), style = MaterialTheme.typography.labelSmall)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .padding(vertical = 4.dp)
        ) {
            drawLine(trackColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
            val slotW = size.width / points.size
            val barW = (slotW * 0.28f).coerceAtMost(22.dp.toPx())
            val pairGap = 4.dp.toPx()

            points.forEachIndexed { idx, pt ->
                val centerX = slotW * idx + slotW / 2f
                val prevRatio = (pt.previousMonthSpendPiasters / maxVal).coerceIn(0f, 1f)
                val currRatio = (pt.currentMonthSpendPiasters / maxVal).coerceIn(0f, 1f)
                val prevH = (prevRatio * (size.height * 0.90f)) * progress
                val currH = (currRatio * (size.height * 0.90f)) * progress

                if (prevH > 0.5f) {
                    drawRoundRect(
                        color = previousColor,
                        topLeft = Offset(centerX - barW - pairGap / 2f, size.height - prevH),
                        size = Size(barW, prevH),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
                if (currH > 0.5f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(currentColor, currentColor.copy(alpha = 0.72f))),
                        topLeft = Offset(centerX + pairGap / 2f, size.height - currH),
                        size = Size(barW, currH),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            points.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == AppLanguage.AR) pt.weekLabelAr else pt.weekLabelEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = MoneyFormatter.formatPiasters(
                            pt.currentMonthSpendPiasters,
                            currency = currency,
                            language = lang,
                            hideAmounts = hideAmounts
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                        fontWeight = FontWeight.SemiBold,
                        color = currentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
