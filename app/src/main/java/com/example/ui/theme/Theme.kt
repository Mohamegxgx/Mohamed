package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThemePreset

@Immutable
data class ThemePresetSpec(
    val preset: ThemePreset,
    val labelAr: String,
    val labelEn: String,
    val darkScheme: ColorScheme,
    val lightScheme: ColorScheme,
    val heroGradientDark: List<Color>,
    val heroGradientLight: List<Color>,
    val heroTitleOnGradient: Color,
    val heroAccentOnGradient: Color,
    val heroBadgeText: Color,
    val swatchPrimary: Color,
    val swatchSecondary: Color,
    val swatchSurface: Color
)

object ThemePalettes {

    val presets: List<ThemePresetSpec> = listOf(
        ThemePresetSpec(
            preset = ThemePreset.EMERALD_OBSIDIAN,
            labelAr = "الزمردي الداكن",
            labelEn = "Emerald Obsidian",
            darkScheme = darkColorScheme(
                primary = EmeraldPrimary,
                onPrimary = Color(0xFF022C22),
                primaryContainer = Color(0xFF064E3B),
                onPrimaryContainer = Color(0xFFA7F3D0),
                secondary = GoldAccent,
                onSecondary = Color(0xFF2B1B00),
                secondaryContainer = Color(0xFF422C06),
                onSecondaryContainer = GoldSoft,
                tertiary = SkyCyan,
                onTertiary = Color(0xFF082F49),
                tertiaryContainer = Color(0xFF0C4A6E),
                onTertiaryContainer = Color(0xFFBAE6FD),
                background = ObsidianBg,
                onBackground = TextPrimaryDark,
                surface = MidnightSurface,
                onSurface = TextPrimaryDark,
                surfaceVariant = SlateCard,
                onSurfaceVariant = TextSecondaryDark,
                surfaceContainerHigh = SlateCardElevated,
                outline = SlateBorder,
                error = CoralExpense,
                onError = Color.White,
                errorContainer = Color(0xFF450A0A),
                onErrorContainer = CoralSoft
            ),
            lightScheme = lightColorScheme(
                primary = EmeraldLightPrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD1FAE5),
                onPrimaryContainer = Color(0xFF064E3B),
                secondary = Color(0xFFD97706),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFEF3C7),
                onSecondaryContainer = Color(0xFF78350F),
                tertiary = Color(0xFF0284C7),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE0F2FE),
                onTertiaryContainer = Color(0xFF0C4A6E),
                background = LightBg,
                onBackground = TextPrimaryLight,
                surface = LightSurface,
                onSurface = TextPrimaryLight,
                surfaceVariant = Color(0xFFFFFFFF),
                onSurfaceVariant = TextSecondaryLight,
                surfaceContainerHigh = Color(0xFFEFF4FB),
                outline = LightBorder,
                error = Color(0xFFDC2626),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF991B1B)
            ),
            heroGradientDark = listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF1E293B)),
            heroGradientLight = listOf(Color(0xFF065F46), Color(0xFF0F172A), Color(0xFF1E293B)),
            heroTitleOnGradient = Color(0xFFA7F3D0),
            heroAccentOnGradient = EmeraldBright,
            heroBadgeText = GoldSoft,
            swatchPrimary = EmeraldPrimary,
            swatchSecondary = GoldAccent,
            swatchSurface = ObsidianBg
        ),
        ThemePresetSpec(
            preset = ThemePreset.ROYAL_SAPPHIRE,
            labelAr = "الياقوتي الذهبي",
            labelEn = "Royal Sapphire",
            darkScheme = darkColorScheme(
                primary = SapphirePrimary,
                onPrimary = Color(0xFFEFF6FF),
                primaryContainer = Color(0xFF1E3A8A),
                onPrimaryContainer = Color(0xFFDBEAFE),
                secondary = GoldAccent,
                onSecondary = Color(0xFF2B1B00),
                secondaryContainer = Color(0xFF422C06),
                onSecondaryContainer = GoldSoft,
                tertiary = EmeraldBright,
                onTertiary = Color(0xFF022C22),
                tertiaryContainer = Color(0xFF064E3B),
                onTertiaryContainer = Color(0xFFA7F3D0),
                background = SapphireBg,
                onBackground = TextPrimaryDark,
                surface = SapphireSurface,
                onSurface = TextPrimaryDark,
                surfaceVariant = SapphireCard,
                onSurfaceVariant = TextSecondaryDark,
                surfaceContainerHigh = SapphireCardElevated,
                outline = SapphireBorder,
                error = CoralExpense,
                onError = Color.White,
                errorContainer = Color(0xFF450A0A),
                onErrorContainer = CoralSoft
            ),
            lightScheme = lightColorScheme(
                primary = SapphireLightPrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF1E3A8A),
                secondary = Color(0xFFD97706),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFEF3C7),
                onSecondaryContainer = Color(0xFF78350F),
                tertiary = EmeraldLightPrimary,
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFD1FAE5),
                onTertiaryContainer = Color(0xFF064E3B),
                background = Color(0xFFF3F6FC),
                onBackground = TextPrimaryLight,
                surface = Color.White,
                onSurface = TextPrimaryLight,
                surfaceVariant = Color.White,
                onSurfaceVariant = TextSecondaryLight,
                surfaceContainerHigh = Color(0xFFEAF0FA),
                outline = Color(0xFFD2DDF0),
                error = Color(0xFFDC2626),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF991B1B)
            ),
            heroGradientDark = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A), Color(0xFF172554)),
            heroGradientLight = listOf(Color(0xFF1D4ED8), Color(0xFF0F172A), Color(0xFF1E3A8A)),
            heroTitleOnGradient = Color(0xFFBFDBFE),
            heroAccentOnGradient = SapphireBright,
            heroBadgeText = GoldSoft,
            swatchPrimary = SapphirePrimary,
            swatchSecondary = GoldAccent,
            swatchSurface = SapphireBg
        ),
        ThemePresetSpec(
            preset = ThemePreset.AMETHYST_ONYX,
            labelAr = "الجمشتي الفاخر",
            labelEn = "Amethyst Onyx",
            darkScheme = darkColorScheme(
                primary = AmethystPrimary,
                onPrimary = Color(0xFFFAF5FF),
                primaryContainer = Color(0xFF581C87),
                onPrimaryContainer = Color(0xFFF3E8FF),
                secondary = Color(0xFF22D3EE),
                onSecondary = Color(0xFF083344),
                secondaryContainer = Color(0xFF164E63),
                onSecondaryContainer = Color(0xFFCFFAFE),
                tertiary = GoldSoft,
                onTertiary = Color(0xFF2B1B00),
                tertiaryContainer = Color(0xFF422C06),
                onTertiaryContainer = Color(0xFFFEF3C7),
                background = AmethystBg,
                onBackground = TextPrimaryDark,
                surface = AmethystSurface,
                onSurface = TextPrimaryDark,
                surfaceVariant = AmethystCard,
                onSurfaceVariant = TextSecondaryDark,
                surfaceContainerHigh = AmethystCardElevated,
                outline = AmethystBorder,
                error = CoralExpense,
                onError = Color.White,
                errorContainer = Color(0xFF450A0A),
                onErrorContainer = CoralSoft
            ),
            lightScheme = lightColorScheme(
                primary = AmethystLightPrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEDE9FE),
                onPrimaryContainer = Color(0xFF4C1D95),
                secondary = Color(0xFF0891B2),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCFFAFE),
                onSecondaryContainer = Color(0xFF164E63),
                tertiary = Color(0xFFD97706),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFEF3C7),
                onTertiaryContainer = Color(0xFF78350F),
                background = Color(0xFFF7F5FC),
                onBackground = TextPrimaryLight,
                surface = Color.White,
                onSurface = TextPrimaryLight,
                surfaceVariant = Color.White,
                onSurfaceVariant = TextSecondaryLight,
                surfaceContainerHigh = Color(0xFFF0ECFA),
                outline = Color(0xFFDDD6F3),
                error = Color(0xFFDC2626),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF991B1B)
            ),
            heroGradientDark = listOf(Color(0xFF581C87), Color(0xFF141122), Color(0xFF2E1065)),
            heroGradientLight = listOf(Color(0xFF6D28D9), Color(0xFF1E1B4B), Color(0xFF4C1D95)),
            heroTitleOnGradient = Color(0xFFE9D5FF),
            heroAccentOnGradient = Color(0xFF22D3EE),
            heroBadgeText = Color(0xFFFDE68A),
            swatchPrimary = AmethystPrimary,
            swatchSecondary = Color(0xFF22D3EE),
            swatchSurface = AmethystBg
        ),
        ThemePresetSpec(
            preset = ThemePreset.DESERT_AMBER,
            labelAr = "الكهرماني الصحراوي",
            labelEn = "Desert Amber",
            darkScheme = darkColorScheme(
                primary = AmberPrimary,
                onPrimary = Color(0xFF291800),
                primaryContainer = Color(0xFF78350F),
                onPrimaryContainer = Color(0xFFFEF3C7),
                secondary = EmeraldPrimary,
                onSecondary = Color(0xFF022C22),
                secondaryContainer = Color(0xFF064E3B),
                onSecondaryContainer = Color(0xFFA7F3D0),
                tertiary = Color(0xFFFB7185),
                onTertiary = Color(0xFF4C0519),
                tertiaryContainer = Color(0xFF881337),
                onTertiaryContainer = Color(0xFFFFE4E6),
                background = AmberBg,
                onBackground = Color(0xFFFAF6F0),
                surface = AmberSurface,
                onSurface = Color(0xFFFAF6F0),
                surfaceVariant = AmberCard,
                onSurfaceVariant = Color(0xFFB8A99A),
                surfaceContainerHigh = AmberCardElevated,
                outline = AmberBorder,
                error = CoralExpense,
                onError = Color.White,
                errorContainer = Color(0xFF450A0A),
                onErrorContainer = CoralSoft
            ),
            lightScheme = lightColorScheme(
                primary = AmberLightPrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F),
                secondary = EmeraldLightPrimary,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD1FAE5),
                onSecondaryContainer = Color(0xFF064E3B),
                tertiary = Color(0xFFE11D48),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFE4E6),
                onTertiaryContainer = Color(0xFF881337),
                background = Color(0xFFFBF8F4),
                onBackground = Color(0xFF1F160E),
                surface = Color.White,
                onSurface = Color(0xFF1F160E),
                surfaceVariant = Color.White,
                onSurfaceVariant = Color(0xFF6B5B4D),
                surfaceContainerHigh = Color(0xFFF5EFE6),
                outline = Color(0xFFE5DACB),
                error = Color(0xFFDC2626),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF991B1B)
            ),
            heroGradientDark = listOf(Color(0xFF78350F), Color(0xFF1C150E), Color(0xFF451A03)),
            heroGradientLight = listOf(Color(0xFFB45309), Color(0xFF291D12), Color(0xFF78350F)),
            heroTitleOnGradient = Color(0xFFFDE68A),
            heroAccentOnGradient = AmberBright,
            heroBadgeText = Color(0xFFA7F3D0),
            swatchPrimary = AmberPrimary,
            swatchSecondary = EmeraldPrimary,
            swatchSurface = AmberBg
        )
    )

    fun specFor(preset: ThemePreset): ThemePresetSpec =
        presets.firstOrNull { it.preset == preset } ?: presets.first()
}

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.AR }
val LocalThemePresetSpec = staticCompositionLocalOf { ThemePalettes.presets.first() }
val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun ElbagorryMoneyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    themePreset: ThemePreset = ThemePreset.EMERALD_OBSIDIAN,
    language: AppLanguage = AppLanguage.AR,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val spec = ThemePalettes.specFor(themePreset)
    val colorScheme = if (isDark) spec.darkScheme else spec.lightScheme
    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides language,
        LocalThemePresetSpec provides spec,
        LocalIsDarkTheme provides isDark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
