package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.formatReadableDate
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalThemePresetSpec
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState
import java.util.Calendar

data class CalendarFinancialEvent(
    val dayOfMonth: Int,
    val title: String,
    val amountPiasters: Long,
    val isIncome: Boolean,
    val timestampMillis: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: MainUiState,
    onBack: () -> Unit
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val positiveColor = semanticPositiveColor()

    val cal = remember { Calendar.getInstance() }
    val currentMonth = cal.get(Calendar.MONTH)
    val currentYear = cal.get(Calendar.YEAR)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val events = remember(state.installments, state.recurringRules, state.debts) {
        val list = mutableListOf<CalendarFinancialEvent>()
        fun sameMonthDay(millis: Long): Int? {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            return if (c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) == currentMonth) {
                c.get(Calendar.DAY_OF_MONTH)
            } else null
        }

        state.installments.filter { it.status == InstallmentStatus.ACTIVE }.forEach { inst ->
            val day = sameMonthDay(inst.nextDueDateMillis) ?: Calendar.getInstance().apply { timeInMillis = inst.nextDueDateMillis }.get(Calendar.DAY_OF_MONTH)
            list += CalendarFinancialEvent(day, inst.name, inst.installmentAmountPiasters, false, inst.nextDueDateMillis)
        }
        state.recurringRules.filter { it.isActive }.forEach { rule ->
            val day = sameMonthDay(rule.nextRunDateMillis) ?: Calendar.getInstance().apply { timeInMillis = rule.nextRunDateMillis }.get(Calendar.DAY_OF_MONTH)
            list += CalendarFinancialEvent(day, rule.title, rule.amountPiasters, rule.type == TransactionType.INCOME, rule.nextRunDateMillis)
        }
        state.debts.filter { it.dueDateMillis != null }.forEach { debt ->
            debt.dueDateMillis?.let { due ->
                val day = sameMonthDay(due) ?: Calendar.getInstance().apply { timeInMillis = due }.get(Calendar.DAY_OF_MONTH)
                val rem = (debt.totalAmountPiasters - debt.paidAmountPiasters).coerceAtLeast(0L)
                if (rem > 0L) {
                    list += CalendarFinancialEvent(day, debt.personName, rem, debt.direction.name == "OWED_TO_ME", due)
                }
            }
        }
        list.sortedBy { it.dayOfMonth }
    }

    val eventDays = remember(events) { events.groupBy { it.dayOfMonth } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.text("التقويم المالي", "Financial Calendar", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val prismColors = LocalThemePresetSpec.current.prismGlowColors
                FinancialCard(
                    elevated = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                ) {
                    Text(
                        text = AppStrings.text("جدول الشهر الحالي", "Current Month Schedule", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    val rows = (1..daysInMonth).chunked(7)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                week.forEach { day ->
                                    val dayEvents = eventDays[day].orEmpty()
                                    val hasIncome = dayEvents.any { it.isIncome }
                                    val hasExpense = dayEvents.any { !it.isIncome }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (dayEvents.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .then(
                                                if (dayEvents.isNotEmpty()) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        brush = Brush.linearGradient(prismColors),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$day",
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily),
                                                fontWeight = if (dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                if (hasIncome) Box(Modifier.size(5.dp).clip(CircleShape).background(positiveColor))
                                                if (hasExpense) Box(Modifier.size(5.dp).clip(CircleShape).background(GoldAccent))
                                            }
                                        }
                                    }
                                }
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = AppStrings.text("المواعيد والالتزامات القادمة", "Scheduled Financial Events", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (events.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = AppStrings.text("لا توجد مواعيد مجدولة", "No scheduled events", lang),
                        subtitle = AppStrings.text("أضف رواتب، فواتير متكررة، أو أقساط لعرضها هنا", "Add salaries, recurring bills, or installments to view them here", lang)
                    )
                }
            } else {
                items(events) { ev ->
                    FinancialCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${AppStrings.text("يوم", "Day", lang)} ${ev.dayOfMonth} • ${formatReadableDate(ev.timestampMillis, lang)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = MoneyFormatter.formatPiasters(ev.amountPiasters, language = lang, hideAmounts = hide),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                color = if (ev.isIncome) positiveColor else CoralExpense
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    state: MainUiState,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onShareJsonBackup: () -> Unit,
    onSaveBackupToUri: (Uri) -> Unit,
    onRestoreBackupFromUri: (Uri) -> Unit,
    onBack: () -> Unit,
    onSaveEncryptedBackupToUri: (Uri, String) -> Unit = { _, _ -> },
    onInspectBackupUri: suspend (Uri) -> Pair<String, Boolean>? = { null },
    onRestoreBackupContent: (rawText: String, password: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val m = state.dashboardMetrics

    var showEncryptPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptPassword by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedRestoreContent by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onSaveBackupToUri(uri)
    }

    val createEncryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingEncryptPassword
        pendingEncryptPassword = null
        if (uri != null && !pwd.isNullOrBlank()) {
            onSaveEncryptedBackupToUri(uri, pwd)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val inspected = onInspectBackupUri(uri)
                if (inspected == null) {
                    onRestoreBackupFromUri(uri)
                } else if (inspected.second) {
                    pendingEncryptedRestoreContent = inspected.first
                } else {
                    onRestoreBackupContent(inspected.first, null)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.text("التقارير والنسخ الاحتياطي", "Reports & Backup", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FinancialCard(
                    elevated = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                ) {
                    Text(
                        AppStrings.text("ملخص التقرير المالي للفترة المختارة", "Selected Period Report Summary", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    ReportLine(AppStrings.text("الفترة", "Period", lang), AppStrings.timeFilterLabel(state.timeFilter, lang))
                    ReportLine(AppStrings.text("تقرير الدخل", "Income Total", lang), MoneyFormatter.formatPiasters(m.totalIncomePiasters, language = lang, hideAmounts = hide))
                    ReportLine(AppStrings.text("تقرير المصروفات", "Expenses Total", lang), MoneyFormatter.formatPiasters(m.totalExpensesPiasters, language = lang, hideAmounts = hide))
                    ReportLine(AppStrings.text("تقرير الالتزامات", "Obligations", lang), MoneyFormatter.formatPiasters(m.totalObligationsPiasters, language = lang, hideAmounts = hide))
                    ReportLine(AppStrings.text("تقرير الادخار", "Savings Allocated", lang), MoneyFormatter.formatPiasters(m.allocatedSavingsPiasters, language = lang, hideAmounts = hide))
                    ReportLine(AppStrings.text("عدد المعاملات", "Transactions Count", lang), "${state.filteredTransactions.size}")
                }
            }

            item {
                FinancialCard(borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f)) {
                    Text(
                        AppStrings.text("تصدير ومشاركة التقارير", "Export & Share Reports", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportCsv, modifier = Modifier.testTag("export_csv_btn")) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("تصدير CSV", "Export CSV", lang), modifier = Modifier.padding(start = 6.dp))
                        }
                        Button(onClick = onExportPdf, modifier = Modifier.testTag("export_pdf_btn")) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("تصدير PDF", "Export PDF", lang), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            item {
                FinancialCard {
                    Text(AppStrings.text("النسخ الاحتياطي والاستعادة (JSON)", "JSON Backup & Restore", lang), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = context.getString(com.example.R.string.data_loss_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showEncryptPasswordDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_encrypted_backup_btn")
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                AppStrings.text("حفظ نسخة مشفرة (AES-256)", "Save Encrypted (AES-256)", lang),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { createBackupLauncher.launch("elbagorry_backup_${System.currentTimeMillis()}.json") },
                            modifier = Modifier.testTag("save_json_backup_btn")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("حفظ نسخة JSON", "Save JSON", lang), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = onShareJsonBackup, modifier = Modifier.testTag("share_json_backup_btn")) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("مشاركة النسخة", "Share JSON", lang), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                            modifier = Modifier.testTag("restore_json_backup_btn")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("استعادة نسخة احتياطية", "Restore Backup", lang), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEncryptPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        val valid = password.length >= 4 && password == confirmPassword

        AlertDialog(
            onDismissRequest = { showEncryptPasswordDialog = false },
            title = { Text(AppStrings.text("تشفير النسخة الاحتياطية (AES-256)", "Encrypt Backup (AES-256)", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = AppStrings.text(
                            "أدخل كلمة مرور قوية (4 أحرف على الأقل). ستحتاجها عند استعادة هذه النسخة.",
                            "Enter a strong passphrase (min 4 chars). You will need it to restore this backup.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(AppStrings.text("كلمة المرور", "Password", lang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("encrypt_backup_password_input")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(AppStrings.text("تأكيد كلمة المرور", "Confirm Password", lang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingEncryptPassword = password
                        showEncryptPasswordDialog = false
                        createEncryptedBackupLauncher.launch("elbagorry_secure_${System.currentTimeMillis()}.ebk")
                    },
                    enabled = valid,
                    modifier = Modifier.testTag("confirm_encrypt_backup_password_btn")
                ) {
                    Text(AppStrings.text("تشفير وحفظ", "Encrypt & Save", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptPasswordDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    pendingEncryptedRestoreContent?.let { rawEncrypted ->
        var restorePwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingEncryptedRestoreContent = null },
            title = { Text(AppStrings.text("نسخة احتياطية مشفرة", "Encrypted Backup", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = AppStrings.text(
                            "هذه النسخة محمية بتشفير AES-256. أدخل كلمة المرور لفك التشفير والاستعادة.",
                            "This backup is encrypted with AES-256. Enter its password to restore.",
                            lang
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restorePwd,
                        onValueChange = { restorePwd = it },
                        label = { Text(AppStrings.text("كلمة المرور", "Password", lang)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("restore_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreBackupContent(rawEncrypted, restorePwd)
                        pendingEncryptedRestoreContent = null
                    },
                    enabled = restorePwd.isNotBlank(),
                    modifier = Modifier.testTag("restore_password_dialog_confirm")
                ) {
                    Text(AppStrings.text("فك التشفير والاستعادة", "Decrypt & Restore", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEncryptedRestoreContent = null }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFamily = RobotoMonoFontFamily))
    }
}
