package com.example.domain.engine

import com.example.data.ai.AiEngineSource
import com.example.data.ai.AiExtractedLineItem
import com.example.data.ai.AiExtractedTransaction
import com.example.data.ai.LocalOfflineFinancialAi
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import java.util.Base64
import kotlin.math.roundToLong

data class ZatcaQrPayload(
    val sellerName: String,
    val vatNumber: String?,
    val timestamp: String?,
    val totalWithVatPiasters: Long?,
    val vatAmountPiasters: Long?
)

/**
 * Offline Receipt & E-Invoice QR Analyzer:
 * 1) Decodes ZATCA / Gulf / Egyptian Base64 TLV QR payloads (Tag 1=Seller, 2=VAT, 3=Time, 4=Total, 5=VAT Amount).
 * 2) Extracts merchant/total/line-items from URLs or multi-line receipt text cleanly without internet.
 */
object OfflineReceiptAnalyzer {

    private val LINE_AMOUNT_REGEX = Regex(
        """(?i)(.+?)\s+(\d{1,7}(?:[.,]\d{1,2})?)\s*(?:ج\.?م|جنيه|egp|sar|ريال|ر\.?س|usd|le)?\s*$"""
    )
    private val IGNORE_LINE_KEYWORDS = listOf(
        "total", "subtotal", "tax", "vat", "discount", "change", "cash", "card",
        "إجمالي", "الاجمالي", "الإجمالي", "المجموع", "ضريبة", "القيمة المضافة", "الخصم", "الباقي", "مدفوع"
    )

    fun analyzeOffline(
        textInput: String?,
        hasBitmap: Boolean,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        lang: AppLanguage
    ): AiExtractedTransaction {
        val raw = textInput?.trim().orEmpty()

        // 1. Try ZATCA / TLV Base64 QR payload
        decodeZatcaTlv(raw)?.let { qr ->
            val totalPiasters = (qr.totalWithVatPiasters ?: 0L).coerceAtLeast(100L)
            val pred = LocalSelfLearningEngine.predict(
                queryText = qr.sellerName,
                typeFilter = TransactionType.EXPENSE,
                merchantRules = merchantRules,
                historicalTransactions = emptyList(),
                categories = categories,
                accounts = accounts
            )
            val catHint = pred.categoryId?.let { cid ->
                categories.firstOrNull { it.id == cid }?.let {
                    if (lang == AppLanguage.EN) it.nameEn else it.nameAr
                }
            } ?: ""
            return AiExtractedTransaction(
                amountPiasters = totalPiasters,
                type = TransactionType.EXPENSE,
                description = qr.sellerName.ifBlank {
                    if (lang == AppLanguage.EN) "Electronic Receipt" else "فاتورة إلكترونية (QR)"
                },
                categoryHint = catHint,
                notes = buildString {
                    append(if (lang == AppLanguage.EN) "Decoded from e-Receipt QR offline" else "تم فك رمز QR للفاتورة محليًا")
                    qr.vatNumber?.let { append(" • الضريبي: $it") }
                    qr.vatAmountPiasters?.takeIf { it > 0 }?.let {
                        append(" • ضريبة: ${MoneyFormatter.piastersToDecimalString(it)}")
                    }
                },
                items = emptyList()
            )
        }

        // 2. Try Egyptian ETA / receipt URL hint
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            val urlTotal = Regex("""(?i)(?:total|amount|amt)=([0-9]+(?:\.[0-9]{1,2})?)""").find(raw)
                ?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
            if (urlTotal != null && urlTotal > 0L) {
                return AiExtractedTransaction(
                    amountPiasters = urlTotal,
                    type = TransactionType.EXPENSE,
                    description = if (lang == AppLanguage.EN) "E-Receipt Link" else "فاتورة إلكترونية",
                    categoryHint = "",
                    notes = raw.take(160),
                    items = emptyList()
                )
            }
        }

        // 3. Text parsing with explicit Total / multi-line item extraction + LocalOfflineFinancialAi
        if (raw.isNotBlank()) {
            val base = LocalOfflineFinancialAi.analyzeOfflineReceiptText(
                textInput = raw,
                accounts = accounts,
                categories = categories,
                merchantRules = merchantRules,
                lang = lang
            )
            val lineItems = extractLineItems(raw, categories, merchantRules, lang)
            val explicitTotal = extractExplicitTotalPiasters(raw)
            val resolvedTotal = when {
                explicitTotal != null && explicitTotal > 0L -> explicitTotal
                lineItems.isNotEmpty() -> lineItems.sumOf { it.amountPiasters }
                else -> base.amountPiasters
            }
            return base.copy(
                amountPiasters = resolvedTotal.coerceAtLeast(0L),
                items = lineItems
            )
        }

        // 4. Image-only offline fallback when no network/OCR text was provided
        if (hasBitmap) {
            return AiExtractedTransaction(
                amountPiasters = 0L,
                type = TransactionType.EXPENSE,
                description = if (lang == AppLanguage.EN) "Receipt Photo (Review Amount Offline)" else "صورة إيصال (أدخل المبلغ أو الصق نص/QR)",
                categoryHint = "",
                notes = if (lang == AppLanguage.EN) {
                    "Offline mode: paste receipt lines or QR payload for instant extraction, or save directly."
                } else {
                    "الوضع المحلي بدون نت: الصق نص الإيصال أو رمز QR للاستخراج التلقائي الفوري أو احفظ الصورة مع المبلغ."
                },
                items = emptyList()
            )
        }

        return LocalOfflineFinancialAi.analyzeOfflineReceiptText(
            textInput = raw,
            accounts = accounts,
            categories = categories,
            merchantRules = merchantRules,
            lang = lang
        )
    }

    fun decodeZatcaTlv(rawText: String): ZatcaQrPayload? {
        val candidate = rawText.trim().lines().firstOrNull { it.trim().length >= 16 && !it.contains(' ') } ?: rawText.trim()
        if (candidate.length < 12 || candidate.contains(' ')) return null
        val bytes = runCatching { Base64.getDecoder().decode(candidate) }.getOrNull() ?: return null
        if (bytes.size < 6) return null

        var index = 0
        var seller: String? = null
        var vatNumber: String? = null
        var timestamp: String? = null
        var totalStr: String? = null
        var vatStr: String? = null
        var tagCount = 0

        while (index + 2 <= bytes.size) {
            val tag = bytes[index].toInt() and 0xFF
            val len = bytes[index + 1].toInt() and 0xFF
            index += 2
            if (len < 0 || index + len > bytes.size) return null
            val value = String(bytes, index, len, Charsets.UTF_8).trim()
            index += len
            if (tag !in 1..9) continue
            tagCount++
            when (tag) {
                1 -> seller = value
                2 -> vatNumber = value
                3 -> timestamp = value
                4 -> totalStr = value
                5 -> vatStr = value
            }
        }
        if (tagCount < 2 || seller.isNullOrBlank()) return null

        fun parsePiasters(v: String?): Long? {
            val dbl = v?.replace(",", ".")?.toDoubleOrNull() ?: return null
            return (dbl * 100.0).roundToLong().takeIf { it > 0L }
        }

        return ZatcaQrPayload(
            sellerName = seller,
            vatNumber = vatNumber?.takeIf { it.isNotBlank() },
            timestamp = timestamp?.takeIf { it.isNotBlank() },
            totalWithVatPiasters = parsePiasters(totalStr),
            vatAmountPiasters = parsePiasters(vatStr)
        )
    }

    private fun extractLineItems(
        rawText: String,
        categories: List<CategoryEntity>,
        merchantRules: List<MerchantRuleEntity>,
        lang: AppLanguage
    ): List<AiExtractedLineItem> {
        val lines = rawText.lines().map { it.trim() }.filter { it.length >= 4 }
        if (lines.size < 2) return emptyList()
        val items = mutableListOf<AiExtractedLineItem>()
        for (line in lines) {
            val normalizedLine = normalizeDigits(line)
            val lower = normalizedLine.lowercase()
            if (IGNORE_LINE_KEYWORDS.any { lower.contains(it) }) continue
            val match = LINE_AMOUNT_REGEX.find(normalizedLine) ?: continue
            val itemName = match.groupValues[1].trim().trim('-', ':', '•', '*')
            val rawAmt = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: continue
            val piasters = (rawAmt * 100.0).roundToLong()
            if (itemName.length >= 2 && piasters in 50L..50_000_000L) {
                val pred = LocalSelfLearningEngine.predict(
                    queryText = itemName,
                    typeFilter = TransactionType.EXPENSE,
                    merchantRules = merchantRules,
                    historicalTransactions = emptyList(),
                    categories = categories,
                    accounts = emptyList()
                )
                val catHint = pred.categoryId?.let { cid ->
                    categories.firstOrNull { it.id == cid }?.let {
                        if (lang == AppLanguage.EN) it.nameEn else it.nameAr
                    }
                } ?: ""
                items += AiExtractedLineItem(
                    description = itemName.take(80),
                    amountPiasters = piasters,
                    categoryHint = catHint
                )
            }
        }
        return items.take(25)
    }

    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when (ch) {
                    '٠', '۰' -> '0'
                    '١', '۱' -> '1'
                    '٢', '۲' -> '2'
                    '٣', '۳' -> '3'
                    '٤', '۴' -> '4'
                    '٥', '۵' -> '5'
                    '٦', '۶' -> '6'
                    '٧', '۷' -> '7'
                    '٨', '۸' -> '8'
                    '٩', '۹' -> '9'
                    '٫' -> '.'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
