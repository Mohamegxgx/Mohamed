package com.example.domain.reader

import com.example.reader.IncomingRawMessage
import java.text.SimpleDateFormat
import java.util.Locale

data class StatementColumnMapping(
    val dateColumnIndex: Int,
    val descriptionColumnIndex: Int,
    val amountColumnIndex: Int? = null,
    val debitColumnIndex: Int? = null,
    val creditColumnIndex: Int? = null,
    val balanceColumnIndex: Int? = null
)

data class StatementPreview(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val suggestedMapping: StatementColumnMapping,
    val delimiter: Char,
    val totalRowCount: Int
)

/**
 * Parses CSV / TSV bank statement exports (NBE, CIB, QNB, Banque Misr, InstaPay/Wallet exports)
 * into normalized [IncomingRawMessage] entries ready for duplicate-safe Smart Reader ingestion.
 */
object BankStatementImporter {

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "MM/dd/yyyy",
        "dd MMM yyyy"
    )

    fun inspectStatement(rawContent: String): StatementPreview? {
        val clean = rawContent.removePrefix("\uFEFF").trim()
        if (clean.isBlank()) return null
        val lines = clean.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.size < 2) return null

        val delimiter = detectDelimiter(lines.first())
        val headers = splitDelimitedLine(lines.first(), delimiter).map { it.trim() }
        if (headers.size < 2) return null

        val dataLines = lines.drop(1).map { splitDelimitedLine(it, delimiter) }
        val suggested = detectColumnMapping(headers)
        return StatementPreview(
            headers = headers,
            sampleRows = dataLines.take(5),
            suggestedMapping = suggested,
            delimiter = delimiter,
            totalRowCount = dataLines.size
        )
    }

    fun convertToIncomingMessages(
        rawContent: String,
        bankOrSourceLabel: String,
        mappingOverride: StatementColumnMapping? = null
    ): List<IncomingRawMessage> {
        val preview = inspectStatement(rawContent) ?: return emptyList()
        val mapping = mappingOverride ?: preview.suggestedMapping
        val clean = rawContent.removePrefix("\uFEFF").trim()
        val lines = clean.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.drop(1)
        val sender = bankOrSourceLabel.trim().ifBlank { "Bank Statement" }

        val result = mutableListOf<IncomingRawMessage>()
        lines.forEach { line ->
            val cols = splitDelimitedLine(line, preview.delimiter)
            val dateStr = cols.getOrNull(mapping.dateColumnIndex)?.trim().orEmpty()
            val descStr = cols.getOrNull(mapping.descriptionColumnIndex)?.trim().orEmpty()

            val debitVal = mapping.debitColumnIndex?.let { parseAmountValue(cols.getOrNull(it)) } ?: 0.0
            val creditVal = mapping.creditColumnIndex?.let { parseAmountValue(cols.getOrNull(it)) } ?: 0.0
            val signedSingle = mapping.amountColumnIndex?.let { parseSignedAmountValue(cols.getOrNull(it)) }

            val isCredit: Boolean
            val absAmount: Double
            when {
                creditVal > 0.0 -> {
                    isCredit = true
                    absAmount = creditVal
                }
                debitVal > 0.0 -> {
                    isCredit = false
                    absAmount = debitVal
                }
                signedSingle != null && signedSingle != 0.0 -> {
                    isCredit = signedSingle > 0.0
                    absAmount = kotlin.math.abs(signedSingle)
                }
                else -> return@forEach
            }

            if (absAmount <= 0.0) return@forEach
            val formattedAmt = String.format(Locale.US, "%.2f", absAmount)
            val balVal = mapping.balanceColumnIndex?.let { parseAmountValue(cols.getOrNull(it)) }?.takeIf { it > 0.0 }
            val balSuffix = balVal?.let {
                val bFmt = String.format(Locale.US, "%.2f", it)
                " الرصيد المتاح $bFmt EGP"
            }.orEmpty()

            val actionPrefix = if (isCredit) "تم إيداع مبلغ" else "تم خصم مبلغ"
            val body = "$actionPrefix $formattedAmt EGP في حسابك ($sender) • ${descStr.ifBlank { "معاملة كشف حساب" }}$balSuffix • تاريخ $dateStr"
            val ts = parseTimestamp(dateStr) ?: System.currentTimeMillis()
            result += IncomingRawMessage(
                sender = sender,
                body = body,
                timestampMillis = ts
            )
        }
        return result
    }

    fun detectColumnMapping(headers: List<String>): StatementColumnMapping {
        val lower = headers.map { it.trim().lowercase() }
        val dateIdx = lower.indexOfFirst {
            it.contains("date") || it.contains("تاريخ") || it.contains("time") || it.contains("التوقيت")
        }.takeIf { it >= 0 } ?: 0

        val descIdx = lower.indexOfFirst {
            it.contains("desc") || it.contains("detail") || it.contains("narrative") ||
                it.contains("merchant") || it.contains("بيان") || it.contains("وصف") || it.contains("تفاصيل")
        }.takeIf { it >= 0 } ?: (if (headers.size > 1) 1 else 0)

        val debitIdx = lower.indexOfFirst {
            it.contains("debit") || it.contains("withdraw") || it.contains("مدين") || it.contains("خصم") || it.contains("سحب")
        }.takeIf { it >= 0 }

        val creditIdx = lower.indexOfFirst {
            it.contains("credit") || it.contains("deposit") || it.contains("دائن") || it.contains("إيداع") || it.contains("ايداع")
        }.takeIf { it >= 0 }

        val amountIdx = if (debitIdx == null && creditIdx == null) {
            lower.indexOfFirst {
                it.contains("amount") || it.contains("value") || it.contains("مبلغ") || it.contains("القيمة")
            }.takeIf { it >= 0 } ?: headers.lastIndex.takeIf { it > descIdx }
        } else null

        val balanceIdx = lower.indexOfFirst {
            it.contains("balance") || it.contains("رصيد")
        }.takeIf { it >= 0 }

        return StatementColumnMapping(
            dateColumnIndex = dateIdx,
            descriptionColumnIndex = descIdx,
            amountColumnIndex = amountIdx,
            debitColumnIndex = debitIdx,
            creditColumnIndex = creditIdx,
            balanceColumnIndex = balanceIdx
        )
    }

    private fun detectDelimiter(headerLine: String): Char {
        val counts = mapOf(
            ',' to headerLine.count { it == ',' },
            ';' to headerLine.count { it == ';' },
            '\t' to headerLine.count { it == '\t' }
        )
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    cells += sb.toString().trim()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        cells += sb.toString().trim()
        return cells
    }

    private fun parseSignedAmountValue(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("EGP", "", ignoreCase = true)
            .replace("ج.م", "")
            .replace("(", "-")
            .replace(")", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun parseAmountValue(raw: String?): Double? {
        val signed = parseSignedAmountValue(raw) ?: return null
        return kotlin.math.abs(signed)
    }

    private fun parseTimestamp(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        for (pattern in DATE_FORMATS) {
            val res = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(dateStr)?.time
            }.getOrNull()
            if (res != null) return res
        }
        return null
    }
}
