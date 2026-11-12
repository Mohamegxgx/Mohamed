package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 3,
    val appName: String = "ELBAGORRY Money",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList(),
    val installments: List<InstallmentEntity> = emptyList(),
    val installmentPayments: List<InstallmentPaymentEntity> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val financialSources: List<FinancialSourceEntity> = emptyList(),
    val importedMessages: List<ImportedMessageEntity> = emptyList(),
    val pendingTransactions: List<PendingTransactionEntity> = emptyList(),
    val importHistory: List<ImportHistoryEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList()
)

class BackupAndExportManager(private val db: AppDatabase) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createJsonBackup(): String {
        val payload = BackupPayload(
            schemaVersion = 3,
            accounts = db.accountDao().getAllOnce(),
            categories = db.categoryDao().getAllOnce(),
            transactions = db.transactionDao().getAllOnce(),
            budgets = db.budgetDao().getAllOnce(),
            savingsGoals = db.savingsGoalDao().getAllOnce(),
            installments = db.installmentDao().getAllOnce(),
            installmentPayments = db.installmentPaymentDao().getAllOnce(),
            recurringRules = db.recurringRuleDao().getAllOnce(),
            debts = db.debtDao().getAllOnce(),
            notifications = db.notificationDao().getAllOnce(),
            financialSources = db.financialSourceDao().getAllOnce(),
            importedMessages = db.importedMessageDao().getAllOnce(),
            pendingTransactions = db.pendingTransactionDao().getAllOnce(),
            importHistory = db.importHistoryDao().getAllOnce(),
            merchantRules = db.merchantRuleDao().getAllOnce()
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    fun validateBackupJson(rawJson: String): Result<BackupPayload> {
        return runCatching {
            require(rawJson.isNotBlank()) { "الملف فارغ" }
            val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
            require(payload.schemaVersion in 1..3) { "إصدار النسخة الاحتياطية غير مدعوم" }
            require(payload.accounts.isNotEmpty() || payload.categories.isNotEmpty()) {
                "النسخة الاحتياطية لا تحتوي على بيانات صالحة"
            }
            val accountIds = payload.accounts.map { it.id }.toSet()
            payload.transactions.forEach { tx ->
                require(tx.amountPiasters > 0L) { "معاملة تحتوي على مبلغ غير صحيح" }
                require(tx.accountId in accountIds) { "معاملة تشير إلى حساب غير موجود" }
            }
            payload
        }
    }

    suspend fun restoreFromJson(rawJson: String): Result<Int> {
        val parsed = validateBackupJson(rawJson)
        val payload = parsed.getOrElse { return Result.failure(it) }

        return runCatching {
            db.withTransaction {
                db.pendingTransactionDao().deleteAll()
                db.importedMessageDao().deleteAll()
                db.importHistoryDao().deleteAll()
                db.merchantRuleDao().deleteAll()
                db.notificationDao().deleteAll()
                db.installmentPaymentDao().deleteAll()
                db.transactionDao().deleteAll()
                db.budgetDao().deleteAll()
                db.recurringRuleDao().deleteAll()
                db.installmentDao().deleteAll()
                db.savingsGoalDao().deleteAll()
                db.debtDao().deleteAll()
                db.categoryDao().deleteAll()
                db.accountDao().deleteAll()

                if (payload.accounts.isNotEmpty()) db.accountDao().insertAll(payload.accounts)
                if (payload.categories.isNotEmpty()) db.categoryDao().insertAll(payload.categories)
                if (payload.savingsGoals.isNotEmpty()) db.savingsGoalDao().insertAll(payload.savingsGoals)
                if (payload.installments.isNotEmpty()) db.installmentDao().insertAll(payload.installments)
                if (payload.debts.isNotEmpty()) db.debtDao().insertAll(payload.debts)
                if (payload.recurringRules.isNotEmpty()) db.recurringRuleDao().insertAll(payload.recurringRules)
                if (payload.budgets.isNotEmpty()) db.budgetDao().insertAll(payload.budgets)
                if (payload.transactions.isNotEmpty()) db.transactionDao().insertAll(payload.transactions)
                if (payload.installmentPayments.isNotEmpty()) db.installmentPaymentDao().insertAll(payload.installmentPayments)
                if (payload.notifications.isNotEmpty()) db.notificationDao().insertAll(payload.notifications)
                if (payload.financialSources.isNotEmpty()) db.financialSourceDao().insertAll(payload.financialSources)
                if (payload.importedMessages.isNotEmpty()) db.importedMessageDao().insertAll(payload.importedMessages)
                if (payload.pendingTransactions.isNotEmpty()) db.pendingTransactionDao().insertAll(payload.pendingTransactions)
                if (payload.importHistory.isNotEmpty()) db.importHistoryDao().insertAll(payload.importHistory)
                if (payload.merchantRules.isNotEmpty()) db.merchantRuleDao().insertAll(payload.merchantRules)
            }
            payload.transactions.size
        }
    }

    fun exportCsvToFile(
        context: Context,
        title: String,
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "elbagorry_report_${System.currentTimeMillis()}.csv")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        // UTF-8 BOM for proper Arabic rendering in Excel
        sb.append('\uFEFF')
        sb.append("ID,Date,Type,Amount_EGP,Account,Category,Description,Notes\n")
        transactions.forEach { tx ->
            val accName = accounts[tx.accountId]?.name ?: ""
            val catName = tx.categoryId?.let { categories[it]?.nameAr } ?: "-"
            val amountStr = MoneyFormatter.piastersToDecimalString(tx.amountPiasters)
            sb.append(
                listOf(
                    tx.id.toString(),
                    sdf.format(Date(tx.timestampMillis)),
                    tx.type.name,
                    amountStr,
                    escapeCsv(accName),
                    escapeCsv(catName),
                    escapeCsv(tx.description),
                    escapeCsv(tx.notes)
                ).joinToString(",")
            ).append('\n')
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportPdfToFile(
        context: Context,
        reportTitle: String,
        periodLabel: String,
        totalIncome: Long,
        totalExpense: Long,
        netBalance: Long,
        transactions: List<TransactionEntity>,
        language: AppLanguage
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "elbagorry_report_${System.currentTimeMillis()}.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            isFakeBoldText = true
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 12f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        var y = 48f
        canvas.drawText("ELBAGORRY Money - $reportTitle", 36f, y, headerPaint)
        y += 22f
        canvas.drawText("Period: $periodLabel", 36f, y, subPaint)
        y += 20f
        canvas.drawText("Income: ${MoneyFormatter.formatPiasters(totalIncome, language = AppLanguage.EN)}", 36f, y, subPaint)
        y += 18f
        canvas.drawText("Expenses: ${MoneyFormatter.formatPiasters(totalExpense, language = AppLanguage.EN)}", 36f, y, subPaint)
        y += 18f
        canvas.drawText("Net Balance: ${MoneyFormatter.formatPiasters(netBalance, language = AppLanguage.EN)}", 36f, y, subPaint)
        y += 16f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 22f

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        transactions.take(35).forEach { tx ->
            if (y > 790f) {
                pdf.finishPage(page)
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            val amt = MoneyFormatter.formatPiasters(tx.amountPiasters, language = AppLanguage.EN)
            val row = "${sdf.format(Date(tx.timestampMillis))} | ${tx.type.name} | $amt | ${tx.description.take(28)}"
            canvas.drawText(row, 36f, y, subPaint)
            y += 18f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        return file
    }

    fun exportJsonToFile(context: Context, rawJson: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "elbagorry_backup_${System.currentTimeMillis()}.json")
        file.writeText(rawJson, Charsets.UTF_8)
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\"", "\"\"")
        return "\"$sanitized\""
    }
}
