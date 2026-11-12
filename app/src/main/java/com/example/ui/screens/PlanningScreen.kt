package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.formatReadableDate
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(
    state: MainUiState,
    onCreateBudget: (String, Long?, Long) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onCopyPreviousBudgets: () -> Unit,
    onCreateInstallment: (String, String, Long?, Long, Int, Long, InstallmentFrequency) -> Unit,
    onPayInstallment: (InstallmentEntity, Long) -> Unit,
    onSetInstallmentStatus: (InstallmentEntity, InstallmentStatus) -> Unit,
    onCreateRecurring: (String, TransactionType, IncomeSourceType, Long, Long, Long?, RecurringFrequency, Long, Int) -> Unit,
    onToggleRecurring: (RecurringRuleEntity) -> Unit,
    onDeleteRecurring: (RecurringRuleEntity) -> Unit,
    onCreateDebt: (String, DebtDirection, Long, Long?, String) -> Unit,
    onSettleDebt: (DebtEntity, Long, Long) -> Unit,
    onDeleteDebt: (DebtEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    var selectedTab by remember { mutableIntStateOf(0) }

    var showAddBudget by remember { mutableStateOf(false) }
    var showAddInstallment by remember { mutableStateOf(false) }
    var payInstallmentTarget by remember { mutableStateOf<InstallmentEntity?>(null) }
    var showAddRecurring by remember { mutableStateOf(false) }
    var showAddDebt by remember { mutableStateOf(false) }
    var settleDebtTarget by remember { mutableStateOf<DebtEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.text("الميزانية والالتزامات", "Budgets & Commitments", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddBudget = true
                        1 -> showAddInstallment = true
                        2 -> showAddRecurring = true
                        else -> showAddDebt = true
                    }
                },
                modifier = Modifier.testTag("planning_add_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(AppStrings.text("إضافة", "Add", lang))
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            listOf(
                AppStrings.text("الميزانية", "Budgets", lang),
                AppStrings.text("الأقساط", "Installments", lang),
                AppStrings.text("المتكررة", "Recurring", lang),
                AppStrings.text("الديون", "Debts", lang)
            ).forEachIndexed { idx, title ->
                SegmentedButton(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 4),
                    modifier = Modifier.testTag("planning_tab_$idx")
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        OutlinedButton(
                            onClick = onCopyPreviousBudgets,
                            modifier = Modifier.fillMaxWidth().testTag("copy_prev_budgets_btn")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                AppStrings.text("نسخ ميزانيات الشهر السابق", "Copy Previous Month Budgets", lang),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    if (state.budgetEvaluations.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد ميزانيات نشطة", "No active budgets", lang),
                                subtitle = AppStrings.text("حدد ميزانية عامة أو حسب الفئة لتجنب تجاوز الإنفاق", "Set monthly or category budgets to track limits", lang)
                            )
                        }
                    } else {
                        items(state.budgetEvaluations, key = { it.budgetId }) { eval ->
                            val rawBudget = state.budgets.firstOrNull { it.id == eval.budgetId }
                            val statusColor = when {
                                eval.isExceeded -> CoralExpense
                                eval.isWarning80 -> GoldAccent
                                else -> EmeraldPrimary
                            }
                            FinancialCard(
                                borderColor = statusColor.copy(alpha = 0.55f),
                                modifier = Modifier.testTag("budget_card_${eval.budgetId}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (eval.isExceeded || eval.isWarning80) {
                                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                                        }
                                        Text(eval.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (rawBudget != null) {
                                        IconButton(onClick = { onDeleteBudget(rawBudget) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (eval.usagePercent.toFloat() / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)),
                                    color = statusColor,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "${AppStrings.text("المستهلك:", "Spent:", lang)} ${MoneyFormatter.formatPiasters(eval.spentPiasters, language = lang, hideAmounts = hide)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${AppStrings.text("المتبقي:", "Left:", lang)} ${MoneyFormatter.formatPiasters(eval.remainingPiasters, language = lang, hideAmounts = hide)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColor
                                    )
                                }
                                val alertText = when {
                                    eval.isExceeded -> AppStrings.text("تحذير: تم تجاوز حد الميزانية!", "Alert: Budget limit exceeded!", lang)
                                    eval.isWarning80 -> AppStrings.text("تنبيه: استهلكت 80% أو أكثر من الميزانية", "Warning: Reached 80%+ of budget", lang)
                                    else -> "${AppStrings.text("الاستهلاك:", "Usage:", lang)} ${MoneyFormatter.formatPercent(eval.usagePercent)}"
                                }
                                Text(alertText, style = MaterialTheme.typography.labelLarge, color = statusColor)
                            }
                        }
                    }
                }

                1 -> {
                    val activeMonthlySum = state.installments
                        .filter { it.status == InstallmentStatus.ACTIVE }
                        .sumOf { it.installmentAmountPiasters }
                    item {
                        FinancialCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(AppStrings.text("الالتزامات الدورية النشطة", "Active Periodic Obligations", lang))
                                Text(
                                    MoneyFormatter.formatPiasters(activeMonthlySum, language = lang, hideAmounts = hide),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                    color = GoldAccent
                                )
                            }
                        }
                    }
                    if (state.installments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد أقساط مسجلة", "No installments yet", lang),
                                subtitle = AppStrings.text("أضف أقساطك والتزاماتك لمتابعة مواعيد السداد", "Add installments to track due dates and balances", lang)
                            )
                        }
                    } else {
                        items(state.installments, key = { it.id }) { inst ->
                            val now = System.currentTimeMillis()
                            val isOverdue = inst.status == InstallmentStatus.ACTIVE && inst.paidCount < inst.totalCount && inst.nextDueDateMillis < now
                            val remCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0)
                            val remAmount = remCount.toLong() * inst.installmentAmountPiasters

                            FinancialCard(
                                borderColor = if (isOverdue) CoralExpense else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                modifier = Modifier.testTag("installment_card_${inst.id}")
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(inst.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${inst.creditor} • ${AppStrings.installmentFreqLabel(inst.frequency, lang)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (isOverdue) AppStrings.text("متأخر", "Overdue", lang) else AppStrings.installmentStatusLabel(inst.status, lang),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isOverdue) CoralExpense else EmeraldPrimary
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${AppStrings.text("القسط:", "Installment:", lang)} ${MoneyFormatter.formatPiasters(inst.installmentAmountPiasters, language = lang, hideAmounts = hide)} • (${inst.paidCount}/${inst.totalCount})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${AppStrings.text("المتبقي:", "Remaining:", lang)} ${MoneyFormatter.formatPiasters(remAmount, language = lang, hideAmounts = hide)} • ${AppStrings.text("الاستحقاق:", "Due:", lang)} ${formatReadableDate(inst.nextDueDateMillis, lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { payInstallmentTarget = inst },
                                        enabled = inst.status == InstallmentStatus.ACTIVE && inst.paidCount < inst.totalCount,
                                        modifier = Modifier.testTag("pay_installment_${inst.id}")
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(AppStrings.text("تسجيل سداد القسط", "Pay Installment", lang), modifier = Modifier.padding(start = 4.dp))
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            val next = if (inst.status == InstallmentStatus.PAUSED) InstallmentStatus.ACTIVE else InstallmentStatus.PAUSED
                                            onSetInstallmentStatus(inst, next)
                                        }) {
                                            Icon(
                                                if (inst.status == InstallmentStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                                contentDescription = "Pause/Resume"
                                            )
                                        }
                                        IconButton(onClick = { onSetInstallmentStatus(inst, InstallmentStatus.ARCHIVED) }) {
                                            Icon(Icons.Rounded.Archive, contentDescription = "Archive")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (state.recurringRules.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد قواعد دخل أو مصروف متكرر", "No recurring rules yet", lang),
                                subtitle = AppStrings.text("أضف راتبك الشهري أو فواتير الكهرباء والإنترنت والإيجار", "Add monthly salary or recurring bills safely without duplicates", lang)
                            )
                        }
                    } else {
                        items(state.recurringRules, key = { it.id }) { rule ->
                            val isIncome = rule.type == TransactionType.INCOME
                            FinancialCard(modifier = Modifier.testTag("recurring_card_${rule.id}")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.title, style = MaterialTheme.typography.titleMedium)
                                        val sub = if (isIncome) AppStrings.incomeSourceLabel(rule.incomeSourceType, lang) else AppStrings.text("مصروف متكرر", "Recurring Expense", lang)
                                        Text(
                                            text = "$sub • ${AppStrings.recurringFreqLabel(rule.frequency, lang)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${AppStrings.text("الموعد القادم:", "Next Run:", lang)} ${formatReadableDate(rule.nextRunDateMillis, lang)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = MoneyFormatter.formatPiasters(rule.amountPiasters, language = lang, hideAmounts = hide),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = RobotoMonoFontFamily),
                                            color = if (isIncome) EmeraldPrimary else CoralExpense
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(checked = rule.isActive, onCheckedChange = { onToggleRecurring(rule) })
                                            IconButton(onClick = { onDeleteRecurring(rule) }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (state.debts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد ديون مسجلة", "No debts recorded", lang),
                                subtitle = AppStrings.text("تابع المستحقات لك أو عليك بشكل مستقل عن المصروفات اليومية", "Track owed amounts separately from regular expenses", lang)
                            )
                        }
                    } else {
                        items(state.debts, key = { it.id }) { debt ->
                            val remaining = (debt.totalAmountPiasters - debt.paidAmountPiasters).coerceAtLeast(0L)
                            val accent = if (debt.direction == DebtDirection.OWED_TO_ME) EmeraldPrimary else CoralExpense
                            FinancialCard(modifier = Modifier.testTag("debt_card_${debt.id}")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(debt.personName, style = MaterialTheme.typography.titleMedium)
                                        Text(AppStrings.debtDirectionLabel(debt.direction, lang), style = MaterialTheme.typography.bodyMedium, color = accent)
                                        Text(
                                            text = "${AppStrings.text("المتبقي:", "Remaining:", lang)} ${MoneyFormatter.formatPiasters(remaining, language = lang, hideAmounts = hide)}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Row {
                                        if (debt.status != DebtStatus.SETTLED) {
                                            Button(onClick = { settleDebtTarget = debt }) {
                                                Text(AppStrings.text("سداد", "Settle", lang))
                                            }
                                        }
                                        IconButton(onClick = { onDeleteDebt(debt) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Budget Dialog
    if (showAddBudget) {
        var name by remember { mutableStateOf("") }
        var limitText by remember { mutableStateOf("") }
        var selectedCatId by remember { mutableStateOf<Long?>(null) }
        val expenseCats = state.categories.filter { it.type == TransactionType.EXPENSE }

        AlertDialog(
            onDismissRequest = { showAddBudget = false },
            title = { Text(AppStrings.text("إنشاء ميزانية", "New Budget", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(AppStrings.text("اسم الميزانية", "Budget Name", lang)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text(AppStrings.text("الحد الأقصى (EGP)", "Limit (EGP)", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedCatId == null,
                            onClick = { selectedCatId = null },
                            label = { Text(AppStrings.text("ميزانية عامة", "General", lang)) }
                        )
                        expenseCats.forEach { cat ->
                            FilterChip(
                                selected = selectedCatId == cat.id,
                                onClick = {
                                    selectedCatId = cat.id
                                    if (name.isBlank()) name = if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn
                                },
                                label = { Text(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limit = MoneyFormatter.parseToPiasters(limitText)
                    if (limit != null) {
                        onCreateBudget(name.ifBlank { AppStrings.text("الميزانية الشهرية", "Monthly Budget", lang) }, selectedCatId, limit)
                        showAddBudget = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddBudget = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    // Add Installment Dialog
    if (showAddInstallment) {
        var name by remember { mutableStateOf("") }
        var creditor by remember { mutableStateOf("") }
        var instAmount by remember { mutableStateOf("") }
        var countText by remember { mutableStateOf("12") }
        var freq by remember { mutableStateOf(InstallmentFrequency.MONTHLY) }

        AlertDialog(
            onDismissRequest = { showAddInstallment = false },
            title = { Text(AppStrings.text("تسجيل قسط / التزام جديد", "New Installment", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(AppStrings.text("اسم الالتزام", "Title", lang)) }, singleLine = true)
                    OutlinedTextField(value = creditor, onValueChange = { creditor = it }, label = { Text(AppStrings.text("الجهة المستحقة", "Creditor", lang)) }, singleLine = true)
                    OutlinedTextField(
                        value = instAmount,
                        onValueChange = { instAmount = it },
                        label = { Text(AppStrings.text("قيمة القسط (EGP)", "Installment Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it },
                        label = { Text(AppStrings.text("عدد الأقساط", "Installment Count", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InstallmentFrequency.entries.forEach { f ->
                            FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(AppStrings.installmentFreqLabel(f, lang)) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(instAmount)
                    val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    if (name.isNotBlank() && piasters != null) {
                        onCreateInstallment(name, creditor.ifBlank { "-" }, state.accounts.firstOrNull()?.id, piasters, count, System.currentTimeMillis(), freq)
                        showAddInstallment = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddInstallment = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    // Pay Installment Account Picker Dialog
    payInstallmentTarget?.let { inst ->
        val activeAccs = state.accounts.filter { !it.isArchived }
        var accId by remember { mutableStateOf(inst.accountId ?: activeAccs.firstOrNull()?.id ?: 1L) }

        AlertDialog(
            onDismissRequest = { payInstallmentTarget = null },
            title = { Text(AppStrings.text("اختر حساب السداد", "Select Payment Account", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${inst.name} — ${MoneyFormatter.formatPiasters(inst.installmentAmountPiasters, language = lang, hideAmounts = hide)}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.forEach { acc ->
                            FilterChip(selected = accId == acc.id, onClick = { accId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onPayInstallment(inst, accId)
                    payInstallmentTarget = null
                }) { Text(AppStrings.text("تأكيد السداد", "Confirm Pay", lang)) }
            },
            dismissButton = { TextButton(onClick = { payInstallmentTarget = null }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    // Add Recurring Income / Expense Dialog
    if (showAddRecurring) {
        var title by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(TransactionType.INCOME) }
        var source by remember { mutableStateOf(IncomeSourceType.MONTHLY_SALARY) }
        var freq by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
        val activeAccs = state.accounts.filter { !it.isArchived }
        var accId by remember { mutableStateOf(activeAccs.firstOrNull()?.id ?: 1L) }

        AlertDialog(
            onDismissRequest = { showAddRecurring = false },
            title = { Text(AppStrings.text("دخل أو مصروف متكرر", "Recurring Rule", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, label = { Text(AppStrings.text("دخل متكرر", "Recurring Income", lang)) })
                        FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, label = { Text(AppStrings.text("مصروف متكرر", "Recurring Expense", lang)) })
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(AppStrings.text("العنوان (مثل: الراتب / الإيجار)", "Title", lang)) }, singleLine = true)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(AppStrings.text("القيمة (EGP)", "Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (type == TransactionType.INCOME) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(IncomeSourceType.MONTHLY_SALARY, IncomeSourceType.FREELANCE, IncomeSourceType.BONUS, IncomeSourceType.INVESTMENT).forEach { s ->
                                FilterChip(selected = source == s, onClick = { source = s }, label = { Text(AppStrings.incomeSourceLabel(s, lang)) })
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurringFrequency.entries.forEach { f ->
                            FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(AppStrings.recurringFreqLabel(f, lang)) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.forEach { acc ->
                            FilterChip(selected = accId == acc.id, onClick = { accId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(amountText)
                    if (title.isNotBlank() && piasters != null) {
                        val catId = state.categories.firstOrNull { it.type == type }?.id
                        onCreateRecurring(title, type, source, piasters, accId, catId, freq, System.currentTimeMillis() + 86_400_000L, 2)
                        showAddRecurring = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddRecurring = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    // Add Debt Dialog
    if (showAddDebt) {
        var person by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var dir by remember { mutableStateOf(DebtDirection.OWED_BY_ME) }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDebt = false },
            title = { Text(AppStrings.text("تسجيل دين", "New Debt", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DebtDirection.entries.forEach { d ->
                            FilterChip(selected = dir == d, onClick = { dir = d }, label = { Text(AppStrings.debtDirectionLabel(d, lang)) })
                        }
                    }
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text(AppStrings.text("اسم الشخص / الجهة", "Person / Entity", lang)) }, singleLine = true)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(AppStrings.text("قيمة الدين (EGP)", "Total Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(AppStrings.text("ملاحظات", "Notes", lang)) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(amountText)
                    if (person.isNotBlank() && piasters != null) {
                        onCreateDebt(person, dir, piasters, null, notes)
                        showAddDebt = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddDebt = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    // Settle Debt Dialog
    settleDebtTarget?.let { debt ->
        val activeAccs = state.accounts.filter { !it.isArchived }
        var accId by remember { mutableStateOf(activeAccs.firstOrNull()?.id ?: 1L) }
        val rem = (debt.totalAmountPiasters - debt.paidAmountPiasters).coerceAtLeast(0L)
        var amountInput by remember { mutableStateOf(MoneyFormatter.piastersToDecimalString(rem)) }

        AlertDialog(
            onDismissRequest = { settleDebtTarget = null },
            title = { Text("${AppStrings.text("سداد دين", "Settle Debt", lang)}: ${debt.personName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.forEach { acc ->
                            FilterChip(selected = accId == acc.id, onClick = { accId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text(AppStrings.text("المبلغ المدفوع (EGP)", "Payment Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(amountInput)
                    if (piasters != null) {
                        onSettleDebt(debt, accId, piasters)
                        settleDebtTarget = null
                    }
                }) { Text(AppStrings.text("تأكيد السداد", "Confirm", lang)) }
            },
            dismissButton = { TextButton(onClick = { settleDebtTarget = null }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }
}
