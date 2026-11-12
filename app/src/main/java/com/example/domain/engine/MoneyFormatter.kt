package com.example.domain.engine

import com.example.domain.model.AppLanguage
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {

    private val piastersPerUnit = BigDecimal(100)

    fun parseToPiasters(rawInput: String): Long? {
        val normalized = normalizeDigits(rawInput)
            .replace(",", "")
            .replace("٫", ".")
            .trim()
        if (normalized.isEmpty()) return null
        return try {
            val bd = BigDecimal(normalized)
            if (bd <= BigDecimal.ZERO) return null
            bd.multiply(piastersPerUnit)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun parseAllowZeroToPiasters(rawInput: String): Long? {
        val normalized = normalizeDigits(rawInput)
            .replace(",", "")
            .replace("٫", ".")
            .trim()
        if (normalized.isEmpty()) return 0L
        return try {
            val bd = BigDecimal(normalized)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(piastersPerUnit)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun piastersToDecimalString(piasters: Long): String {
        val bd = BigDecimal(piasters).divide(piastersPerUnit, 2, RoundingMode.HALF_UP)
        val stripped = bd.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
        } else {
            bd.toPlainString()
        }
    }

    fun formatPiasters(
        piasters: Long,
        currency: String = "EGP",
        language: AppLanguage = AppLanguage.AR,
        hideAmounts: Boolean = false,
        showSign: Boolean = false
    ): String {
        if (hideAmounts) return "•••••"
        val isNegative = piasters < 0
        val absPiasters = kotlin.math.abs(piasters)
        val bd = BigDecimal(absPiasters).divide(piastersPerUnit, 2, RoundingMode.HALF_UP)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val hasFraction = absPiasters % 100L != 0L
        val pattern = if (hasFraction) "#,##0.00" else "#,##0"
        val formattedNumber = DecimalFormat(pattern, symbols).format(bd)
        val sign = when {
            isNegative -> "-"
            showSign && piasters > 0 -> "+"
            else -> ""
        }
        val currencyLabel = when {
            currency == "EGP" && language == AppLanguage.AR -> "ج.م"
            else -> currency
        }
        return "$sign$formattedNumber $currencyLabel"
    }

    fun formatPercent(value: Double): String {
        val bd = BigDecimal.valueOf(if (value.isNaN() || value.isInfinite()) 0.0 else value)
            .setScale(1, RoundingMode.HALF_UP)
        return "${bd.toPlainString()}%"
    }

    private fun normalizeDigits(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                else -> ch
            }
            builder.append(mapped)
        }
        return builder.toString()
    }
}
