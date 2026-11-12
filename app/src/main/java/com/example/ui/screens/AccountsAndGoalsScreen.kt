package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AccountType
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.ui.components.AppStrings
import com.example.ui.components.CircularGoalRing
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.MoneyText
import com.example.ui.components.formatReadableDate
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsAndGoalsScreen(
    state: MainUiState,
    onCreateAccount: (String, AccountType, Long, String, String) -> Unit,
    onTransfer: (Long, Long, Long, String) -> Unit,
    onCreateGoal: (String, GoalPreset, Long, Long?, String) -> Unit,
    onContributeGoal: (SavingsGoalEntity, Long, Long) -> Unit,
    onToggleGoalPause: (SavingsGoalEntity) -> Unit,
    onDeleteGoal: (SavingsGoalEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    var selectedTab by remember { mutableIntStateOf(0) }

    var showCreateAccount by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var selectedAccountHistory by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreateGoal by remember { mutableStateOf(false) }
    var contributeGoalTarget by remember { mutableStateOf<SavingsGoalEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.text("الحسابات والادخار", "Accounts & Savings", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == 0) {
                    OutlinedButton(onClick = { showTransferDialog = true }, modifier = Modifier.testTag("open_transfer_btn")) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(AppStrings.text("تحويل", "Transfer", lang), modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(onClick = { showCreateAccount = true }, modifier = Modifier.testTag("add_account_btn")) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(AppStrings.text("حساب", "Account", lang))
                    }
                } else {
                    Button(onClick = { showCreateGoal = true }, modifier = Modifier.testTag("add_goal_btn")) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(AppStrings.text("هدف جديد", "New Goal", lang))
                    }
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            listOf(
                AppStrings.text("الحسابات", "Accounts", lang),
                AppStrings.text("أهداف الادخار", "Savings Goals", lang)
            ).forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    modifier = Modifier.testTag("accounts_tab_$index")
                ) {
                    Text(title)
                }
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "accountsGoalsTab",
            modifier = Modifier.weight(1f)
        ) { tab ->
            if (tab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.accounts.filter { !it.isArchived }, key = { it.id }) { acc ->
                        val accent = parseHexColor(acc.colorHex)
                        FinancialCard(
                            modifier = Modifier.clickable { selectedAccountHistory = acc }.testTag("account_card_${acc.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = accent)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(acc.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = AppStrings.accountTypeLabel(acc.type, lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                MoneyText(
                                    piasters = acc.currentBalancePiasters,
                                    lang = lang,
                                    hideAmounts = hide,
                                    currency = acc.currency,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                if (state.savingsGoals.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = AppStrings.text("لا توجد أهداف ادخار بعد", "No savings goals yet", lang),
                            subtitle = AppStrings.text("أنشئ هدفًا مثل صندوق الطوارئ أو شراء هاتف", "Create a goal like Emergency Fund or Phone", lang),
                            icon = Icons.Rounded.Savings
                        )
                    }
                } else {
                    items(state.savingsGoals, key = { it.id }) { goal ->
                        val ratio = if (goal.targetAmountPiasters > 0L) {
                            (goal.savedAmountPiasters.toFloat() / goal.targetAmountPiasters.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val remaining = (goal.targetAmountPiasters - goal.savedAmountPiasters).coerceAtLeast(0L)
                        val color = parseHexColor(goal.colorHex, EmeraldPrimary)

                        FinancialCard(modifier = Modifier.testTag("goal_card_${goal.id}")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularGoalRing(
                                    progress = ratio,
                                    color = color,
                                    label = if (hide) "••%" else "${(ratio * 100).toInt()}%"
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = AppStrings.goalPresetLabel(goal.presetType, lang),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${MoneyFormatter.formatPiasters(goal.savedAmountPiasters, language = lang, hideAmounts = hide)} / ${MoneyFormatter.formatPiasters(goal.targetAmountPiasters, language = lang, hideAmounts = hide)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = color
                                    )
                                    Text(
                                        text = "${AppStrings.text("المتبقي:", "Remaining:", lang)} ${MoneyFormatter.formatPiasters(remaining, language = lang, hideAmounts = hide)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { contributeGoalTarget = goal },
                                    enabled = goal.status == GoalStatus.ACTIVE,
                                    modifier = Modifier.testTag("contribute_goal_${goal.id}")
                                ) {
                                    Text(AppStrings.text("إضافة مساهمة", "Contribute", lang))
                                }
                                Row {
                                    IconButton(onClick = { onToggleGoalPause(goal) }) {
                                        Icon(
                                            if (goal.status == GoalStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            contentDescription = "Pause/Resume"
                                        )
                                    }
                                    IconButton(onClick = { onDeleteGoal(goal) }) {
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

    // Create Account Dialog
    if (showCreateAccount) {
        var name by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("0") }
        var type by remember { mutableStateOf(AccountType.CUSTOM) }
        var currency by remember { mutableStateOf(state.preferences.currencyCode) }
        var colorHex by remember { mutableStateOf("#10B981") }

        AlertDialog(
            onDismissRequest = { showCreateAccount = false },
            title = { Text(AppStrings.text("حساب مالي جديد", "New Account", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(AppStrings.text("اسم الحساب", "Account Name", lang)) },
                        singleLine = true,
                        modifier = Modifier.testTag("new_account_name")
                    )
                    OutlinedTextField(
                        value = opening,
                        onValueChange = { opening = it },
                        label = { Text(AppStrings.text("الرصيد الافتتاحي ($currency)", "Opening Balance ($currency)", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CurrencyConverter.supportedCurrencies.take(5).forEach { spec ->
                            FilterChip(
                                selected = currency == spec.code,
                                onClick = { currency = spec.code },
                                label = { Text(spec.code) },
                                modifier = Modifier.testTag("new_account_currency_${spec.code.lowercase()}")
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(AppStrings.accountTypeLabel(t, lang)) }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("#10B981", "#38BDF8", "#F59E0B", "#A855F7").forEach { hex ->
                            FilterChip(
                                selected = colorHex == hex,
                                onClick = { colorHex = hex },
                                label = {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(hex)))
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = MoneyFormatter.parseAllowZeroToPiasters(opening) ?: 0L
                        if (name.isNotBlank()) {
                            onCreateAccount(name, type, bal, colorHex, currency)
                            showCreateAccount = false
                        }
                    }
                ) {
                    Text(AppStrings.text("إنشاء", "Create", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAccount = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    // Account Transfer Dialog
    if (showTransferDialog && state.accounts.size >= 2) {
        val activeAccs = state.accounts.filter { !it.isArchived }
        var fromId by remember { mutableStateOf(activeAccs.firstOrNull()?.id ?: 1L) }
        var toId by remember { mutableStateOf(activeAccs.drop(1).firstOrNull()?.id ?: 2L) }
        var amountText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text(AppStrings.text("تحويل بين الحسابات", "Account Transfer", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppStrings.text("من حساب:", "From:", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.forEach { acc ->
                            FilterChip(selected = fromId == acc.id, onClick = { fromId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                    Text(AppStrings.text("إلى حساب:", "To:", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.filter { it.id != fromId }.forEach { acc ->
                            FilterChip(selected = toId == acc.id, onClick = { toId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(AppStrings.text("المبلغ (EGP)", "Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(AppStrings.text("ملاحظات", "Notes", lang)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val piasters = MoneyFormatter.parseToPiasters(amountText)
                        if (piasters != null && fromId != toId) {
                            onTransfer(fromId, toId, piasters, notes)
                            showTransferDialog = false
                        }
                    }
                ) {
                    Text(AppStrings.text("تأكيد التحويل", "Confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    // Account Movements Dialog
    selectedAccountHistory?.let { acc ->
        val movements = state.transactions.filter { it.accountId == acc.id || it.targetAccountId == acc.id }.take(15)
        AlertDialog(
            onDismissRequest = { selectedAccountHistory = null },
            title = { Text("${acc.name} — ${AppStrings.text("سجل الحركات", "Movements", lang)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (movements.isEmpty()) {
                        Text(AppStrings.text("لا توجد حركات على هذا الحساب بعد.", "No movements yet.", lang))
                    } else {
                        movements.forEach { tx ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tx.description, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(MoneyFormatter.formatPiasters(tx.amountPiasters, currency = acc.currency, language = lang, hideAmounts = hide))
                            }
                            Text(formatReadableDate(tx.timestampMillis, lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAccountHistory = null }) { Text(AppStrings.text("إغلاق", "Close", lang)) }
            }
        )
    }

    // Create Savings Goal Dialog
    if (showCreateGoal) {
        var preset by remember { mutableStateOf(GoalPreset.EMERGENCY) }
        var name by remember { mutableStateOf(AppStrings.goalPresetLabel(GoalPreset.EMERGENCY, lang)) }
        var targetInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateGoal = false },
            title = { Text(AppStrings.text("هدف ادخار جديد", "New Savings Goal", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GoalPreset.entries.forEach { p ->
                            FilterChip(
                                selected = preset == p,
                                onClick = {
                                    preset = p
                                    if (p != GoalPreset.CUSTOM) name = AppStrings.goalPresetLabel(p, lang)
                                },
                                label = { Text(AppStrings.goalPresetLabel(p, lang)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(AppStrings.text("اسم الهدف", "Goal Name", lang)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text(AppStrings.text("المبلغ المطلوب (EGP)", "Target Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = MoneyFormatter.parseToPiasters(targetInput)
                        if (name.isNotBlank() && target != null) {
                            onCreateGoal(name, preset, target, null, "#10B981")
                            showCreateGoal = false
                        }
                    }
                ) {
                    Text(AppStrings.text("حفظ الهدف", "Save Goal", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGoal = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    // Contribute to Goal Dialog
    contributeGoalTarget?.let { goal ->
        val activeAccs = state.accounts.filter { !it.isArchived }
        var accId by remember { mutableStateOf(activeAccs.firstOrNull()?.id ?: 1L) }
        var amountInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { contributeGoalTarget = null },
            title = { Text("${AppStrings.text("مساهمة في", "Contribute to", lang)} ${goal.name}") },
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
                        label = { Text(AppStrings.text("قيمة المساهمة (EGP)", "Amount", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val piasters = MoneyFormatter.parseToPiasters(amountInput)
                        if (piasters != null) {
                            onContributeGoal(goal, accId, piasters)
                            contributeGoalTarget = null
                        }
                    }
                ) {
                    Text(AppStrings.text("تأكيد المساهمة", "Contribute", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { contributeGoalTarget = null }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }
}
