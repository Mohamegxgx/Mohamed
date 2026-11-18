package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.TimeFilter
import com.example.ui.theme.CoralSoft
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalThemePresetSpec
import com.example.ui.theme.RobotoMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FinancialCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    containerColor: Color = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(durationMillis = 160),
        label = "cardContainer"
    )
    val borderStroke = if (borderColor != null) {
        val animatedBorder by animateColorAsState(
            targetValue = borderColor,
            animationSpec = tween(durationMillis = 160),
            label = "cardBorder"
        )
        BorderStroke(0.9.dp, SolidColor(animatedBorder))
    } else {
        val bevelBrush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (elevated) 0.36f else 0.24f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = if (elevated) 0.28f else 0.18f)
                )
            } else {
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                )
            }
        )
        BorderStroke(if (elevated) 1.dp else 0.8.dp, bevelBrush)
    }

    val baseModifier = modifier.fillMaxWidth()
    val contentColumnModifier = Modifier.padding(16.dp)

    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = baseModifier.springPressEffect(interactionSource, pressedScale = 0.982f),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = animatedContainer),
            border = borderStroke,
            elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 4.dp else 0.dp)
        ) {
            Column(modifier = contentColumnModifier, content = content)
        }
    } else {
        Card(
            modifier = baseModifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = animatedContainer),
            border = borderStroke,
            elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 4.dp else 0.dp)
        ) {
            Column(modifier = contentColumnModifier, content = content)
        }
    }
}

@Composable
fun HeroBalanceCard(
    title: String,
    balancePiasters: Long,
    subtitle: String,
    availablePiasters: Long,
    hideAmounts: Boolean,
    lang: AppLanguage,
    onTogglePrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    todayExpensePiasters: Long? = null,
    currencyCode: String = "EGP"
) {
    val spec = LocalThemePresetSpec.current
    val isDark = LocalIsDarkTheme.current
    val rawColors = if (isDark) spec.heroGradientDark else spec.heroGradientLight
    val stop0 by animateColorAsState(rawColors.getOrElse(0) { Color(0xFF0E235C) }, tween(400), label = "heroStop0")
    val stop1 by animateColorAsState(rawColors.getOrElse(1) { Color(0xFF050816) }, tween(400), label = "heroStop1")
    val stop2 by animateColorAsState(rawColors.getOrElse(2) { Color(0xFF281454) }, tween(400), label = "heroStop2")
    val gradient = Brush.linearGradient(colors = listOf(stop0, stop1, stop2))
    val toggleInteraction = remember { MutableInteractionSource() }

    val prismColors = spec.prismGlowColors.ifEmpty { listOf(spec.heroAccentOnGradient, Color.White) }
    val prismBorderBrush = Brush.linearGradient(
        colors = listOf(
            prismColors.first().copy(alpha = 0.62f),
            Color.White.copy(alpha = 0.22f),
            prismColors.last().copy(alpha = 0.56f)
        )
    )

    // Subtle slow Hydrogen quantum orbital motion
    val infiniteTransition = rememberInfiniteTransition(label = "hydrogenHeroOrbit")
    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitPhase"
    )
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
            .border(1.15.dp, prismBorderBrush, MaterialTheme.shapes.large)
    ) {
        // Hydrogen crystalline refraction background artwork blended subtly under a readability scrim
        Image(
            painter = painterResource(id = R.drawable.img_hydrogen_hero_aura_1794844130942),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (isDark) 0.40f else 0.28f }
        )

        // Deep scrim & kinetic Hydrogen orbital arcs
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        stop1.copy(alpha = 0.42f),
                        stop1.copy(alpha = 0.58f),
                        stop2.copy(alpha = 0.48f)
                    )
                )
            )
            val orbCenter = Offset(size.width * 0.88f, size.height * 0.20f)
            val outerRadius = size.maxDimension * 0.36f * auraPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        spec.heroAccentOnGradient.copy(alpha = 0.26f),
                        prismColors.last().copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = orbCenter,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = orbCenter
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        spec.heroAccentOnGradient.copy(alpha = 0.32f),
                        prismColors.last().copy(alpha = 0.26f),
                        Color.Transparent
                    ),
                    center = orbCenter
                ),
                radius = size.maxDimension * 0.27f,
                center = orbCenter,
                style = Stroke(width = 1.4.dp.toPx())
            )
            // Orbiting Hydrogen luminous node
            val nodeRadius = size.maxDimension * 0.27f
            val nodeX = orbCenter.x + cos(orbitPhase) * nodeRadius
            val nodeY = orbCenter.y + sin(orbitPhase) * nodeRadius
            drawCircle(
                color = spec.heroAccentOnGradient.copy(alpha = 0.72f),
                radius = 3.2.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
            drawCircle(
                color = spec.heroBadgeText.copy(alpha = 0.12f),
                radius = size.maxDimension * 0.24f,
                center = Offset(size.width * 0.08f, size.height * 0.94f)
            )
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = spec.heroTitleOnGradient,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.75.dp, spec.heroAccentOnGradient.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = spec.heroAccentOnGradient,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "H₂",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = spec.heroAccentOnGradient
                        )
                    }
                }
                IconButton(
                    onClick = onTogglePrivacy,
                    interactionSource = toggleInteraction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .springPressEffect(toggleInteraction, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .testTag("privacy_toggle_button")
                ) {
                    AnimatedContent(
                        targetState = hideAmounts,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.78f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.78f))
                        },
                        label = "privacyEyeIcon"
                    ) { isHidden ->
                        Icon(
                            imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isHidden) {
                                AppStrings.text("إظهار المبالغ", "Show amounts", lang)
                            } else {
                                AppStrings.text("إخفاء المبالغ", "Hide amounts", lang)
                            },
                            tint = Color.White
                        )
                    }
                }
            }

            AnimatedMoneyText(
                piasters = balancePiasters,
                lang = lang,
                hideAmounts = hideAmounts,
                currency = currencyCode,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    AnimatedMoneyText(
                        piasters = availablePiasters,
                        lang = lang,
                        hideAmounts = hideAmounts,
                        currency = currencyCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = spec.heroAccentOnGradient
                    )
                }

                if (todayExpensePiasters != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.13f))
                            .border(0.85.dp, prismBorderBrush, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = AppStrings.text("مصروف اليوم", "Today", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            AnimatedMoneyText(
                                piasters = todayExpensePiasters,
                                lang = lang,
                                hideAmounts = hideAmounts,
                                currency = currencyCode,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (todayExpensePiasters > 0L) CoralSoft else spec.heroBadgeText
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.13f))
                            .border(0.85.dp, prismBorderBrush, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = currencyCode,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                            color = spec.heroBadgeText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoneyText(
    piasters: Long,
    lang: AppLanguage,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showSign: Boolean = false,
    currency: String = "EGP"
) {
    Text(
        text = MoneyFormatter.formatPiasters(
            piasters = piasters,
            currency = currency,
            language = lang,
            hideAmounts = hideAmounts,
            showSign = showSign
        ),
        modifier = modifier,
        style = style.copy(fontFamily = RobotoMonoFontFamily),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun KpiMetricTile(
    title: String,
    valueText: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "kpiAccent"
    )
    FinancialCard(
        modifier = modifier,
        borderColor = animatedAccent.copy(alpha = 0.30f),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(animatedAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = animatedAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedMetricValue(
            valueText = valueText,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TimeFilterChipsBar(
    selectedFilter: TimeFilter,
    lang: AppLanguage,
    onSelect: (TimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TimeFilter.entries) { filter ->
            val selected = filter == selectedFilter
            val chipScale by animateFloatAsState(
                targetValue = if (selected) 1.04f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "chipScale_${filter.name}"
            )
            FilterChip(
                selected = selected,
                onClick = { onSelect(filter) },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .testTag("time_filter_${filter.name.lowercase()}"),
                label = {
                    Text(
                        text = AppStrings.timeFilterLabel(filter, lang),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Outlined.Info,
    modifier: Modifier = Modifier
) {
    SmoothReveal(index = 1, modifier = modifier) {
        FinancialCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScreenHeaderBar(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.24f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions()
        }
    }
}

fun formatReadableDate(millis: Long, lang: AppLanguage): String {
    val locale = if (lang == AppLanguage.AR) Locale.forLanguageTag("ar") else Locale.US
    return SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis))
}

fun formatReadableDateTime(millis: Long, lang: AppLanguage): String {
    val locale = if (lang == AppLanguage.AR) Locale.forLanguageTag("ar") else Locale.US
    return SimpleDateFormat("dd MMM • hh:mm a", locale).format(Date(millis))
}
