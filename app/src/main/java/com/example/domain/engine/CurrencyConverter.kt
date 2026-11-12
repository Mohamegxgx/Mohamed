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

    private val rateMap: Map<String, BigDecimal> = supportedCurrencies.associate { it.code to it.rateToEgp }

    fun specFor(code: String): CurrencySpec {
        val upper = code.uppercase()
        return supportedCurrencies.firstOrNull { it.code == upper } ?: supportedCurrencies.first()
    }

    fun rateToEgp(currencyCode: String): BigDecimal {
        return rateMap[currencyCode.uppercase()] ?: BigDecimal.ONE
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
