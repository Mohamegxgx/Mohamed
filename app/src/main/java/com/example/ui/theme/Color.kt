package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Flagship Gemini Hydrogen Quantum Palette (Default)
val HydrogenBg = Color(0xFF040712)
val HydrogenSurface = Color(0xFF0A1022)
val HydrogenCard = Color(0xFF101933)
val HydrogenCardElevated = Color(0xFF172447)
val HydrogenBorder = Color(0xFF2B3F70)

val HydrogenPrimary = Color(0xFF4F8CFF)
val HydrogenBright = Color(0xFF7CB2FF)
val HydrogenCyan = Color(0xFF38BDF8)
val HydrogenAuroraTeal = Color(0xFF22D3EE)
val HydrogenIris = Color(0xFF8B5CF6)
val HydrogenIrisBright = Color(0xFFA78BFA)
val HydrogenMagenta = Color(0xFFF472B6)

val HydrogenLightBg = Color(0xFFF3F6FF)
val HydrogenLightSurface = Color(0xFFFFFFFF)
val HydrogenLightCardElevated = Color(0xFFEAF0FF)
val HydrogenLightBorder = Color(0xFFCDE0FF)
val HydrogenLightPrimary = Color(0xFF2563EB)
val HydrogenLightIris = Color(0xFF7C3AED)
val HydrogenLightAurora = Color(0xFF0284C7)

// Semantic Accents
val GoldAccent = Color(0xFFF59E0B)
val GoldSoft = Color(0xFFFCD34D)
val SkyCyan = Color(0xFF38BDF8)
val CoralExpense = Color(0xFFEF4444)
val CoralSoft = Color(0xFFF87171)

// Legacy Emerald Obsidian Palette (Retained for backwards-compatibility preset selection)
val ObsidianBg = Color(0xFF070B12)
val MidnightSurface = Color(0xFF0F1624)
val SlateCard = Color(0xFF162032)
val SlateCardElevated = Color(0xFF1D293D)
val SlateBorder = Color(0xFF28354C)

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldBright = Color(0xFF34D399)
val EmeraldDeep = Color(0xFF047857)

// Crisp Financial Light Mode
val LightBg = Color(0xFFF4F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EFF8)
val LightBorder = Color(0xFFD5DFEE)
val EmeraldLightPrimary = Color(0xFF059669)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)

// Royal Sapphire Gold Tokens
val SapphireBg = Color(0xFF060A14)
val SapphireSurface = Color(0xFF0D1526)
val SapphireCard = Color(0xFF142038)
val SapphireCardElevated = Color(0xFF1B2B4B)
val SapphireBorder = Color(0xFF2B3F66)
val SapphirePrimary = Color(0xFF3B82F6)
val SapphireBright = Color(0xFF60A5FA)
val SapphireLightPrimary = Color(0xFF2563EB)

// Amethyst Onyx Tokens
val AmethystBg = Color(0xFF0A0812)
val AmethystSurface = Color(0xFF131020)
val AmethystCard = Color(0xFF1D1830)
val AmethystCardElevated = Color(0xFF272140)
val AmethystBorder = Color(0xFF3A305E)
val AmethystPrimary = Color(0xFFA855F7)
val AmethystBright = Color(0xFFC084FC)
val AmethystLightPrimary = Color(0xFF7C3AED)

// Desert Amber Tokens
val AmberBg = Color(0xFF0E0B08)
val AmberSurface = Color(0xFF18130E)
val AmberCard = Color(0xFF241C14)
val AmberCardElevated = Color(0xFF30261B)
val AmberBorder = Color(0xFF473726)
val AmberPrimary = Color(0xFFF59E0B)
val AmberBright = Color(0xFFFBBF24)
val AmberLightPrimary = Color(0xFFD97706)

fun parseHexColor(hex: String, fallback: Color = HydrogenPrimary): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> (0xFF000000L or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}
