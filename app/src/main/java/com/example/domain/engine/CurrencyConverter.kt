package com.example.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

data class CurrencySpec(
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val rateToEgp: BigDecimal
)

object CurrencyConverter {

    val supportedCurrencies: List<CurrencySpec> = listOf(
        CurrencySpec("EGP", "الجنيه المصري", "Egyptian Pound", BigDecimal("1.00")),
        CurrencySpec("USD", "الدولار الأمريكي", "US Dollar", BigDecimal("49.00")),
        CurrencySpec("SAR", "الريال السعودي", "Saudi Riyal", BigDecimal("13.06")),
        CurrencySpec("EUR", "اليورو", "Euro", BigDecimal("53.00")),
        CurrencySpec("AED", "الدرهم الإماراتي", "UAE Dirham", BigDecimal("13.34")),
        CurrencySpec("GBP", "الجنيه الإسترليني", "British Pound", BigDecimal("62.50")),
        CurrencySpec("KWD", "الدينار الكويتي", "Kuwaiti Dinar", BigDecimal("159.50"))
    )

    private val defaultRateMap: Map<String, BigDecimal> = supportedCurrencies.associate { it.code to it.rateToEgp }

    @Volatile
    private var customRates: Map<String, BigDecimal> = emptyMap()

    fun specFor(code: String): CurrencySpec {
        val upper = code.uppercase()
        val base = supportedCurrencies.firstOrNull { it.code == upper } ?: supportedCurrencies.first()
        val override = customRates[base.code]
        return if (override != null) base.copy(rateToEgp = override) else base
    }

    fun allSpecsWithEffectiveRates(): List<CurrencySpec> =
        supportedCurrencies.map { spec ->
            val override = customRates[spec.code]
            if (override != null) spec.copy(rateToEgp = override) else spec
        }

    fun defaultRateToEgp(currencyCode: String): BigDecimal =
        defaultRateMap[currencyCode.uppercase()] ?: BigDecimal.ONE

    fun rateToEgp(currencyCode: String): BigDecimal {
        val upper = currencyCode.uppercase()
        return customRates[upper] ?: defaultRateMap[upper] ?: BigDecimal.ONE
    }

    fun applyCustomRates(rates: Map<String, BigDecimal>) {
        customRates = rates.entries
            .filter { (code, value) -> code.uppercase() != "EGP" && value > BigDecimal.ZERO }
            .associate { (code, value) -> code.uppercase() to value.setScale(4, RoundingMode.HALF_UP) }
    }

    fun applyCustomRatesFromRaw(raw: String) {
        if (raw.isBlank()) {
            customRates = emptyMap()
            return
        }
        val parsed = mutableMapOf<String, BigDecimal>()
        raw.split(";", ",", "\n").forEach { entry ->
            val parts = entry.split("=", ":")
            if (parts.size == 2) {
                val code = parts[0].trim().uppercase()
                val rate = parts[1].trim().toBigDecimalOrNull()
                if (code != "EGP" && defaultRateMap.containsKey(code) && rate != null && rate > BigDecimal.ZERO) {
                    parsed[code] = rate.setScale(4, RoundingMode.HALF_UP)
                }
            }
        }
        customRates = parsed
    }

    fun serializeRates(rates: Map<String, BigDecimal>): String {
        return rates.entries
            .filter { (code, value) -> code.uppercase() != "EGP" && value > BigDecimal.ZERO }
            .sortedBy { it.key }
            .joinToString(";") { (code, value) ->
                "${code.uppercase()}=${value.stripTrailingZeros().toPlainString()}"
            }
    }

    fun currentCustomRates(): Map<String, BigDecimal> = customRates

    fun resetCustomRates() {
        customRates = emptyMap()
    }

    fun convertPiasters(
        amountPiasters: Long,
        fromCurrency: String,
        toCurrency: String
    ): Long {
        if (amountPiasters == 0L) return 0L
        val from = fromCurrency.uppercase()
        val to = toCurrency.uppercase()
        if (from == to) return amountPiasters

        val fromRate = rateToEgp(from)
        val toRate = rateToEgp(to)
        if (toRate <= BigDecimal.ZERO) return amountPiasters

        return BigDecimal(amountPiasters)
            .multiply(fromRate)
            .divide(toRate, 0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}
