package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.TrendPoint
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor

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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "incomeBar"
    )
    val expRatio by animateFloatAsState(
        targetValue = (expensePiasters / maxVal).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
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
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
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
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(12f, 12f)
            )
            if (ratio > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(size.width * ratio.coerceAtLeast(0.03f), size.height),
                    cornerRadius = CornerRadius(12f, 12f)
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
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp)
        ) {
            val maxVal = points.maxOf { maxOf(it.incomePiasters, it.expensePiasters, 1L) }.toFloat()
            val w = size.width
            val h = size.height
            val stepX = if (points.size <= 1) w else w / (points.size - 1)

            // Subtle horizontal grid lines
            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            fun drawSeries(values: List<Long>, lineColor: Color, fill: Boolean) {
                if (values.size == 1) {
                    val y = h - (values[0] / maxVal) * (h * 0.85f) - h * 0.05f
                    drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(w / 2f, y))
                    return
                }
                val path = Path()
                val fillPath = Path()
                values.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = h - (v / maxVal) * (h * 0.85f) - h * 0.05f
                    if (idx == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (idx - 1) * stepX
                        val prevY = h - (values[idx - 1] / maxVal) * (h * 0.85f) - h * 0.05f
                        val cx = (prevX + x) / 2f
                        path.cubicTo(cx, prevY, cx, y, x, y)
                        fillPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                    if (idx == values.lastIndex) {
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }
                if (fill) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }

            drawSeries(points.map { it.incomePiasters }, EmeraldPrimary, fill = true)
            drawSeries(points.map { it.expensePiasters }, CoralExpense, fill = false)
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

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
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
                val barH = ratio * (midY * 0.85f)
                val isPos = pt.netPiasters >= 0L
                val topY = if (isPos) midY - barH else midY
                drawRoundRect(
                    color = if (isPos) EmeraldPrimary else CoralExpense,
                    topLeft = Offset(centerX - barW / 2f, topY),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
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

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                var startAngle = -90f
                val strokeWidth = 18.dp.toPx()
                topItems.forEach { item ->
                    val sweep = (item.amountPiasters.toFloat() / total.toFloat()) * 360f
                    drawArc(
                        color = parseHexColor(item.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
            Text(
                text = "${topItems.size}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = RobotoMonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            topItems.forEach { item ->
                val color = parseHexColor(item.colorHex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (lang == AppLanguage.AR) item.nameAr else item.nameEn,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (hideAmounts) "••%" else MoneyFormatter.formatPercent(item.percentage),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        animationSpec = tween(550),
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
                color = color,
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
