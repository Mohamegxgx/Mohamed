package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * Adds tactile spring scale feedback on press while preserving Material ripple and clickable semantics.
 */
fun Modifier.tactileClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.972f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tactileScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Adds spring press bounce around an existing [MutableInteractionSource] (e.g., for FAB or IconButton).
 */
fun Modifier.springPressEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.93f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressEffect"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Silky staggered entrance that keeps children laid out and accessible from frame 0
 * while smoothly gliding vertically into rest.
 */
@Composable
fun SmoothReveal(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
        )
    }
    val p = progress.value
    Box(
        modifier = modifier.graphicsLayer {
            translationY = (1f - p) * 6f
            alpha = 0.85f + 0.15f * p
        }
    ) {
        content()
    }
}

/**
 * Precision-safe animated monetary text: smoothly interpolates between amounts using an exact
 * integer endpoint so 64-bit piaster values never suffer floating-point drift, and cross-fades cleanly
 * when privacy masking toggles.
 */
@Composable
fun AnimatedMoneyText(
    piasters: Long,
    lang: AppLanguage,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showSign: Boolean = false,
    currency: String = "EGP"
) {
    var startPiasters by remember { mutableLongStateOf(piasters) }
    var targetPiasters by remember { mutableLongStateOf(piasters) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(piasters) {
        if (piasters != targetPiasters) {
            val currentRatio = animProgress.value
            val currentDisplayed = if (currentRatio >= 0.999f) {
                targetPiasters
            } else {
                startPiasters + ((targetPiasters - startPiasters) * currentRatio).roundToLong()
            }
            startPiasters = currentDisplayed
            targetPiasters = piasters
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
    }

    val displayedPiasters = if (animProgress.value >= 0.999f) {
        targetPiasters
    } else {
        startPiasters + ((targetPiasters - startPiasters) * animProgress.value).roundToLong()
    }

    AnimatedContent(
        targetState = hideAmounts,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180))) togetherWith
                (fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140)))
        },
        label = "animatedMoneyPrivacy",
        modifier = modifier
    ) { hidden ->
        Text(
            text = MoneyFormatter.formatPiasters(
                piasters = displayedPiasters,
                currency = currency,
                language = lang,
                hideAmounts = hidden,
                showSign = showSign
            ),
            style = style.copy(fontFamily = RobotoMonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Smooth vertical flip-fade transition for short KPI/metric strings.
 */
@Composable
fun AnimatedMetricValue(
    valueText: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = valueText,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 4 })
        },
        label = "metricValueFlip",
        modifier = modifier
    ) { text ->
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Infinite soft breathing glow ring for AI agent processing & feature beacons.
 */
@Composable
fun AiPulseBeacon(
    isPulsing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isPulsing) {
            val infiniteTransition = rememberInfiniteTransition(label = "aiPulseBeacon")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val haloAlpha by infiniteTransition.animateFloat(
                initialValue = 0.14f,
                targetValue = 0.34f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "haloAlpha"
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = haloAlpha
                    }
                    .background(color = color, shape = CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(color = color.copy(alpha = 0.10f), shape = CircleShape)
            )
        }
        content()
    }
}
