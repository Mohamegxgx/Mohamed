package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.domain.engine.BurdenLevel
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
import com.example.ui.components.ScreenHeaderBar
import com.example.ui.components.formatReadableDate
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(
    state: MainUiState,
    onCreateBudget: (String, Long?, Long) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onCopyPreviousBudgets: () -> Unit,
    onCreateInstallment: (String, String, Long?, Long, Int, Long, InstallmentFrequency, Long?, Long) -> Unit,
    onPayInstallment: (InstallmentEntity, Long) -> Unit,
    onSetInstallmentStatus: (InstallmentEntity, InstallmentStatus) -> Unit,
    onCreateRecurring: (String, TransactionType, IncomeSourceType, Long, Long, Long?, RecurringFrequency, Long, Int) -> Unit,
    onToggleRecurring: (RecurringRuleEntity) -> Unit,
    onDeleteRecurring: (RecurringRuleEntity) -> Unit,
    onCreateDebt: (String, DebtDirection, Long, Long?, String) -> Unit,
    onSettleDebt: (DebtEntity, Long, Long) -> Unit,
    onDeleteDebt: (DebtEntity) -> Unit,
    onSaveBnplProgram: (Long?, String, String, Long, Long, Double, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteBnplProgram: (BnplProgramEntity) -> Unit = {},
    initialTab: Int = 0,
    onInitialTabConsumed: () -> Unit = {},
    onOpenWhatIfSimulator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val currency = state.preferences.currencyCode
    val positiveColor = semanticPositiveColor()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 4)) }

    LaunchedEffect(initialTab) {
        if (initialTab in 0..4 && initialTab != 0) {
            selectedTab = initialTab
            onInitialTabConsumed()
        }
    }

    var showAddBudget by remember { mutableStateOf(false) }
    var showAddInstallment by remember { mutableStateOf(false) }
    var preselectedBnplProgramForInst by remember { mutableStateOf<BnplProgramEntity?>(null) }
    var payInstallmentTarget by remember { mutableStateOf<InstallmentEntity?>(null) }
    var showAddRecurring by remember { mutableStateOf(false) }
    var showAddDebt by remember { mutableStateOf(false) }
    var settleDebtTarget by remember { mutableStateOf<DebtEntity?>(null) }
    var editingBnplProgram by remember { mutableStateOf<BnplProgramEntity?>(null) }
    var showCreateBnplDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeaderBar(
            title = AppStrings.text("الميزانية والالتزامات", "Budgets & Commitments", lang),
            subtitle = AppStrings.text("الميزانيات، الأقساط، وبرامج التقسيط", "Budgets, plans & BNPL analyzer", lang),
            icon = Icons.AutoMirrored.Rounded.EventNote,
            actions = {
                Button(
                    onClick = {
                        when (selectedTab) {
                            0 -> showAddBudget = true
                            1 -> {
                                preselectedBnplProgramForInst = null
                                showAddInstallment = true
                            }
                            2 -> showAddRecurring = true
                            3 -> showAddDebt = true
                            else -> {
                                editingBnplProgram = null
                                showCreateBnplDialog = true
                            }
                        }
                    },
                    modifier = Modifier.testTag("planning_add_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(AppStrings.text("إضافة", "Add", lang))
                }
            }
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            listOf(
                AppStrings.text("الميزانية", "Budgets", lang),
                AppStrings.text("الأقساط", "Plans", lang),
                AppStrings.text("المتكررة", "Bills", lang),
                AppStrings.text("الديون", "Debts", lang),
                AppStrings.text("البرامج", "BNPL", lang)
            ).forEachIndexed { idx, title ->
                SegmentedButton(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 5),
                    modifier = Modifier.testTag("planning_tab_$idx")
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { dir * it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { -dir * it / 6 } + fadeOut(tween(160)))
            },
            label = "planningTabs",
            modifier = Modifier.weight(1f)
        ) { tab ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("planning_lazy_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
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
                    } else if (isWide) {
                        items(state.budgetEvaluations.chunked(2), key = { row -> "row_${row.first().budgetId}" }) { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair.forEach { eval ->
                                    val rawBudget = state.budgets.firstOrNull { it.id == eval.budgetId }
                                    Box(modifier = Modifier.weight(1f)) {
                                        BudgetEvaluationCardItem(
                                            eval = eval,
                                            rawBudget = rawBudget,
                                            lang = lang,
                                            hide = hide,
                                            currency = currency,
                                            positiveColor = positiveColor,
                                            onDeleteBudget = onDeleteBudget
                                        )
                                    }
                                }
                                if (rowPair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(state.budgetEvaluations, key = { it.budgetId }) { eval ->
                            val rawBudget = state.budgets.firstOrNull { it.id == eval.budgetId }
                            BudgetEvaluationCardItem(
                                eval = eval,
                                rawBudget = rawBudget,
                                lang = lang,
                                hide = hide,
                                currency = currency,
                                positiveColor = positiveColor,
                                onDeleteBudget = onDeleteBudget,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                        }
                    }
                }

                1 -> {
                    val activeMonthlySum = state.installments
                        .filter { it.status == InstallmentStatus.ACTIVE }
                        .sumOf { it.installmentAmountPiasters }
                    val bnplReport = state.bnplReport
                    val deficitColor = when (bnplReport.deficitAnalysis.burdenLevel) {
                        BurdenLevel.CRITICAL -> CoralExpense
                        BurdenLevel.HIGH -> GoldAccent
                        else -> SkyCyan
                    }
                    item {
                        FinancialCard(
                            borderColor = deficitColor.copy(alpha = 0.4f),
                            onClick = { selectedTab = 4 }
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(AppStrings.text("الالتزامات الدورية النشطة", "Active Periodic Obligations", lang), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = AppStrings.text(
                                            "اضغط لفتح تحليل برامج التقسيط وأسباب العجز (${MoneyFormatter.formatPercent(bnplReport.deficitAnalysis.installmentsToIncomePercent)} من الدخل)",
                                            "Tap for BNPL Limits & Deficit Analyzer (${MoneyFormatter.formatPercent(bnplReport.deficitAnalysis.installmentsToIncomePercent)} of income)",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = deficitColor
                                    )
                                }
                                Text(
                                    MoneyFormatter.formatPiasters(activeMonthlySum, currency = currency, language = lang, hideAmounts = hide),
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
                        val programById = state.bnplPrograms.associateBy { it.id }
                        items(state.installments, key = { it.id }) { inst ->
                            val now = System.currentTimeMillis()
                            val isOverdue = inst.status == InstallmentStatus.ACTIVE && inst.paidCount < inst.totalCount && inst.nextDueDateMillis < now
                            val remCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0)
                            val remAmount = remCount.toLong() * inst.installmentAmountPiasters
                            val linkedProgram = inst.bnplProgramId?.let { programById[it] }
                            val interestPiasters = if (inst.principalAmountPiasters in 1 until inst.totalAmountPiasters) {
                                inst.totalAmountPiasters - inst.principalAmountPiasters
                            } else 0L

                            FinancialCard(
                                borderColor = if (isOverdue) CoralExpense else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    .testTag("installment_card_${inst.id}")
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(inst.name, style = MaterialTheme.typography.titleMedium)
                                            if (linkedProgram != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = if (lang == AppLanguage.AR) linkedProgram.nameAr else linkedProgram.nameEn,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text("${inst.creditor} • ${AppStrings.installmentFreqLabel(inst.frequency, lang)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (isOverdue) AppStrings.text("متأخر", "Overdue", lang) else AppStrings.installmentStatusLabel(inst.status, lang),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isOverdue) CoralExpense else positiveColor
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
                                if (interestPiasters > 0L) {
                                    val interestPct = interestPiasters.toDouble() * 100.0 / inst.principalAmountPiasters.toDouble()
                                    Text(
                                        text = AppStrings.text(
                                            "فائدة التقسيط: ${MoneyFormatter.formatPiasters(interestPiasters, language = lang, hideAmounts = hide)} (${MoneyFormatter.formatPercent(interestPct)})",
                                            "Interest cost: ${MoneyFormatter.formatPiasters(interestPiasters, language = lang, hideAmounts = hide)} (${MoneyFormatter.formatPercent(interestPct)})",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldAccent
                                    )
                                }
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
                            FinancialCard(
                                modifier = Modifier
                                    .animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    .testTag("recurring_card_${rule.id}")
                            ) {
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
                                            color = if (isIncome) positiveColor else CoralExpense
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

                3 -> {
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
                            val accent = if (debt.direction == DebtDirection.OWED_TO_ME) positiveColor else CoralExpense
                            FinancialCard(
                                modifier = Modifier
                                    .animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    .testTag("debt_card_${debt.id}")
                            ) {
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

                else -> {
                    bnplPortfolioAndDeficitItems(
                        report = state.bnplReport,
                        currency = currency,
                        lang = lang,
                        hideAmounts = hide,
                        onEditProgram = { prog ->
                            editingBnplProgram = prog
                            showCreateBnplDialog = true
                        },
                        onAddInstallmentForProgram = { prog ->
                            preselectedBnplProgramForInst = prog
                            showAddInstallment = true
                        },
                        onOpenWhatIfSimulator = onOpenWhatIfSimulator
                    )
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

    // Add Installment Dialog (with BNPL Program selector, Cash Price, and live interest + income impact preview)
    if (showAddInstallment) {
        var name by remember(preselectedBnplProgramForInst) { mutableStateOf("") }
        var selectedBnplId by remember(preselectedBnplProgramForInst) { mutableStateOf(preselectedBnplProgramForInst?.id) }
        var creditor by remember(preselectedBnplProgramForInst) {
            mutableStateOf(
                preselectedBnplProgramForInst?.let { if (lang == AppLanguage.AR) it.nameAr else it.nameEn }.orEmpty()
            )
        }
        var instAmount by remember { mutableStateOf("") }
        var countText by remember { mutableStateOf("12") }
        var principalText by remember { mutableStateOf("") }
        var freq by remember { mutableStateOf(InstallmentFrequency.MONTHLY) }

        val previewInstPiasters = MoneyFormatter.parseToPiasters(instAmount) ?: 0L
        val previewCount = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val previewTotal = previewInstPiasters * previewCount.toLong()
        val previewPrincipal = MoneyFormatter.parseToPiasters(principalText) ?: 0L
        val previewInterest = if (previewPrincipal in 1 until previewTotal) previewTotal - previewPrincipal else 0L
        val previewIncome = state.bnplReport.deficitAnalysis.estimatedMonthlyIncomePiasters
        val previewIncomePct = if (previewIncome > 0L && previewInstPiasters > 0L) {
            previewInstPiasters.toDouble() * 100.0 / previewIncome.toDouble()
        } else 0.0

        AlertDialog(
            onDismissRequest = { showAddInstallment = false },
            title = { Text(AppStrings.text("تسجيل قسط / التزام جديد", "New Installment", lang)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.bnplPrograms.isNotEmpty()) {
                        Text(AppStrings.text("برنامج التقسيط:", "BNPL Program:", lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedBnplId == null,
                                onClick = { selectedBnplId = null },
                                label = { Text(AppStrings.text("قسط عام", "General", lang)) }
                            )
                            state.bnplPrograms.forEach { prog ->
                                FilterChip(
                                    selected = selectedBnplId == prog.id,
                                    onClick = {
                                        selectedBnplId = prog.id
                                        creditor = if (lang == AppLanguage.AR) prog.nameAr else prog.nameEn
                                    },
                                    label = { Text(if (lang == AppLanguage.AR) prog.nameAr else prog.nameEn) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(AppStrings.text("اسم الالتزام", "Title", lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = creditor, onValueChange = { creditor = it }, label = { Text(AppStrings.text("الجهة المستحقة", "Creditor", lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = instAmount,
                        onValueChange = { instAmount = it },
                        label = { Text(AppStrings.text("قيمة القسط (EGP)", "Installment Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it },
                        label = { Text(AppStrings.text("عدد الأقساط", "Installment Count", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = { principalText = it },
                        label = { Text(AppStrings.text("السعر كاش لحساب الفائدة (اختياري)", "Cash Price for APR (Optional)", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InstallmentFrequency.entries.forEach { f ->
                            FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(AppStrings.installmentFreqLabel(f, lang)) })
                        }
                    }
                    if (previewTotal > 0L) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${AppStrings.text("الإجمالي:", "Total:", lang)} ${MoneyFormatter.formatPiasters(previewTotal, currency, lang)}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (previewInterest > 0L) {
                                    val apr = previewInterest.toDouble() * 100.0 / previewPrincipal.toDouble()
                                    Text(
                                        text = "${AppStrings.text("الفائدة:", "Interest:", lang)} ${MoneyFormatter.formatPiasters(previewInterest, currency, lang)} (${MoneyFormatter.formatPercent(apr)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoldAccent
                                    )
                                }
                                if (previewIncomePct > 0.0) {
                                    Text(
                                        text = "${AppStrings.text("نسبة القسط من الدخل:", "Income Share:", lang)} ${MoneyFormatter.formatPercent(previewIncomePct)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (previewIncomePct >= 25.0) CoralExpense else positiveColor
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(instAmount)
                    val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val principalPiasters = MoneyFormatter.parseToPiasters(principalText) ?: 0L
                    if (name.isNotBlank() && piasters != null) {
                        onCreateInstallment(
                            name,
                            creditor.ifBlank { "-" },
                            state.accounts.firstOrNull()?.id,
                            piasters,
                            count,
                            System.currentTimeMillis(),
                            freq,
                            selectedBnplId,
                            principalPiasters
                        )
                        showAddInstallment = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddInstallment = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) } }
        )
    }

    if (showCreateBnplDialog) {
        EditBnplProgramDialog(
            initialProgram = editingBnplProgram,
            lang = lang,
            onDismiss = {
                showCreateBnplDialog = false
                editingBnplProgram = null
            },
            onSave = onSaveBnplProgram,
            onDelete = onDeleteBnplProgram
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

@Composable
private fun BudgetEvaluationCardItem(
    eval: com.example.domain.model.BudgetEvaluation,
    rawBudget: BudgetEntity?,
    lang: AppLanguage,
    hide: Boolean,
    currency: String,
    positiveColor: androidx.compose.ui.graphics.Color,
    onDeleteBudget: (BudgetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        eval.isExceeded -> CoralExpense
        eval.isWarning80 || eval.burnRateWarning -> GoldAccent
        else -> positiveColor
    }
    val animUsage by animateFloatAsState(
        targetValue = (eval.usagePercent.toFloat() / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "budgetProgress_${eval.budgetId}"
    )
    FinancialCard(
        borderColor = statusColor.copy(alpha = 0.55f),
        modifier = modifier.testTag("budget_card_${eval.budgetId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (eval.isExceeded || eval.isWarning80 || eval.burnRateWarning) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                }
                Text(eval.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (rawBudget != null) {
                IconButton(onClick = { onDeleteBudget(rawBudget) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        LinearProgressIndicator(
            progress = { animUsage },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${AppStrings.text("المستهلك:", "Spent:", lang)} ${MoneyFormatter.formatPiasters(eval.spentPiasters, currency = currency, language = lang, hideAmounts = hide)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${AppStrings.text("المتبقي:", "Left:", lang)} ${MoneyFormatter.formatPiasters(eval.remainingPiasters, currency = currency, language = lang, hideAmounts = hide)}",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budget_burn_telemetry_${eval.budgetId}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${AppStrings.text("وتيرة يومية:", "Pace/day:", lang)} ${MoneyFormatter.formatPiasters(eval.dailySpendPacePiasters, currency = currency, language = lang, hideAmounts = hide)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${AppStrings.text("السقف الآمن:", "Safe cap:", lang)} ${MoneyFormatter.formatPiasters(eval.recommendedDailyCapPiasters, currency = currency, language = lang, hideAmounts = hide)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = positiveColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val alertText = when {
            eval.isExceeded -> AppStrings.text("تحذير: تم تجاوز حد الميزانية!", "Alert: Budget limit exceeded!", lang)
            eval.isWarning80 -> AppStrings.text("تنبيه: استهلكت 80% أو أكثر من الميزانية", "Warning: Reached 80%+ of budget", lang)
            eval.burnRateWarning && eval.estimatedDepletionDay != null -> AppStrings.text(
                "إنذار مبكر: بهذه الوتيرة تنفد الميزانية يوم ${eval.estimatedDepletionDay}",
                "Early pace alert: projected to deplete by day ${eval.estimatedDepletionDay}",
                lang
            )
            else -> "${AppStrings.text("الاستهلاك:", "Usage:", lang)} ${MoneyFormatter.formatPercent(eval.usagePercent)}"
        }
        Text(
            text = alertText,
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
            modifier = if (eval.burnRateWarning) Modifier.testTag("budget_burn_warning_${eval.budgetId}") else Modifier
        )
    }
}
