package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
fun FinancialCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    containerColor: Color = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardContainer"
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardBorder"
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = animatedContainer),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
    val gradientColors = if (isDark) spec.heroGradientDark else spec.heroGradientLight
    val gradient = Brush.linearGradient(colors = gradientColors)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
    ) {
        // Subtle ambient corner glow for premium depth
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = spec.heroAccentOnGradient.copy(alpha = 0.14f),
                radius = size.maxDimension * 0.42f,
                center = Offset(size.width * 0.92f, size.height * 0.12f)
            )
            drawCircle(
                color = spec.heroBadgeText.copy(alpha = 0.08f),
                radius = size.maxDimension * 0.28f,
                center = Offset(size.width * 0.10f, size.height * 0.95f)
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = spec.heroTitleOnGradient
                )
                IconButton(
                    onClick = onTogglePrivacy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .testTag("privacy_toggle_button")
                ) {
                    Icon(
                        imageVector = if (hideAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (hideAmounts) {
                            AppStrings.text("إظهار المبالغ", "Show amounts", lang)
                        } else {
                            AppStrings.text("إخفاء المبالغ", "Hide amounts", lang)
                        },
                        tint = Color.White
                    )
                }
            }

            AnimatedContent(
                targetState = hideAmounts,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.96f)) togetherWith
                        (fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 0.96f))
                },
                label = "heroBalanceMask"
            ) { isHidden ->
                MoneyText(
                    piasters = balancePiasters,
                    lang = lang,
                    hideAmounts = isHidden,
                    currency = currencyCode,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
            }

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
                    MoneyText(
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
                            .background(Color.White.copy(alpha = 0.11f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = AppStrings.text("مصروف اليوم", "Today", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            MoneyText(
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
                            .background(Color.White.copy(alpha = 0.11f))
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
    modifier: Modifier = Modifier
) {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "kpiAccent"
    )
    FinancialCard(
        modifier = modifier,
        borderColor = animatedAccent.copy(alpha = 0.24f)
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
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
            FilterChip(
                selected = selected,
                onClick = { onSelect(filter) },
                modifier = Modifier.testTag("time_filter_${filter.name.lowercase()}"),
                label = {
                    Text(
                        text = AppStrings.timeFilterLabel(filter, lang),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
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
    FinancialCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
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

fun formatReadableDate(millis: Long, lang: AppLanguage): String {
    val locale = if (lang == AppLanguage.AR) Locale.forLanguageTag("ar") else Locale.US
    return SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis))
}

fun formatReadableDateTime(millis: Long, lang: AppLanguage): String {
    val locale = if (lang == AppLanguage.AR) Locale.forLanguageTag("ar") else Locale.US
    return SimpleDateFormat("dd MMM • hh:mm a", locale).format(Date(millis))
}
