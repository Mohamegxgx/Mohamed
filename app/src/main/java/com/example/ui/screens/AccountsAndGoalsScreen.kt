package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AccountType
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.domain.model.PayoutCycle
import com.example.domain.model.SavingsAssetKind
import com.example.ui.components.AnimatedMoneyText
import com.example.ui.components.AppStrings
import com.example.ui.components.CircularGoalRing
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.ScreenHeaderBar
import com.example.ui.components.formatReadableDate
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsAndGoalsScreen(
    state: MainUiState,
    onCreateAccount: (String, AccountType, Long, String, String) -> Unit,
    onTransfer: (Long, Long, Long, String, Long) -> Unit,
    onCreateGoal: (String, GoalPreset, Long, Long?, String) -> Unit,
    onContributeGoal: (SavingsGoalEntity, Long, Long) -> Unit,
    onToggleGoalPause: (SavingsGoalEntity) -> Unit,
    onDeleteGoal: (SavingsGoalEntity) -> Unit,
    modifier: Modifier = Modifier,
    onSaveCertificate: (
        title: String,
        assetKind: SavingsAssetKind,
        institutionName: String,
        principalPiasters: Long,
        annualYieldPercent: Double,
        payoutCycle: PayoutCycle,
        linkedAccountId: Long?,
        grams: Double,
        karat: Int,
        pricePerGramPiasters: Long,
        notes: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeleteCertificate: (SavingsCertificateEntity) -> Unit = {},
    onUpdateGoldPrice: (karat: Int, pricePerGramPiasters: Long) -> Unit = { _, _ -> },
    onCreditCertificateInterest: (certId: Long) -> Unit = {}
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val positiveColor = semanticPositiveColor()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showCreateAccount by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var selectedAccountHistory by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreateGoal by remember { mutableStateOf(false) }
    var contributeGoalTarget by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var showAddCertificateDialog by remember { mutableStateOf(false) }
    var showGoldPriceDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeaderBar(
            title = AppStrings.text("الحسابات والادخار", "Accounts & Savings", lang),
            subtitle = AppStrings.text(
                "${state.accounts.count { !it.isArchived }} حساب • ${state.savingsGoals.size} هدف • ${state.savingsCertificates.size} أصل",
                "${state.accounts.count { !it.isArchived }} accounts • ${state.savingsGoals.size} goals • ${state.savingsCertificates.size} assets",
                lang
            ),
            icon = Icons.Rounded.AccountBalance,
            actions = {
                when (selectedTab) {
                    0 -> {
                        OutlinedButton(onClick = { showTransferDialog = true }, modifier = Modifier.testTag("open_transfer_btn")) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("تحويل", "Transfer", lang), modifier = Modifier.padding(start = 4.dp))
                        }
                        Button(onClick = { showCreateAccount = true }, modifier = Modifier.testTag("add_account_btn")) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("حساب", "Account", lang))
                        }
                    }
                    1 -> {
                        Button(onClick = { showCreateGoal = true }, modifier = Modifier.testTag("add_goal_btn")) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("هدف جديد", "New Goal", lang))
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { showGoldPriceDialog = true },
                            modifier = Modifier.testTag("update_gold_price_btn")
                        ) {
                            Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("سعر الذهب", "Gold Rate", lang), modifier = Modifier.padding(start = 4.dp))
                        }
                        Button(
                            onClick = { showAddCertificateDialog = true },
                            modifier = Modifier.testTag("add_certificate_btn")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(AppStrings.text("شهادة / ذهب", "Add Asset", lang))
                        }
                    }
                }
            }
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            listOf(
                AppStrings.text("الحسابات", "Accounts", lang),
                AppStrings.text("أهداف الادخار", "Goals", lang),
                AppStrings.text("الشهادات والذهب", "Certs & Gold", lang)
            ).forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    modifier = Modifier.testTag("accounts_tab_$index")
                ) {
                    Text(title, maxLines = 1)
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
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                .testTag("account_card_${acc.id}"),
                            borderColor = accent.copy(alpha = 0.25f),
                            onClick = { selectedAccountHistory = acc }
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
                                AnimatedMoneyText(
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
            } else if (tab == 1) {
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
                        val color = parseHexColor(goal.colorHex, positiveColor)

                        FinancialCard(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                .testTag("goal_card_${goal.id}")
                        ) {
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
            } else {
                val certsTotalPrincipal = remember(state.savingsCertificates) {
                    state.savingsCertificates
                        .filter { it.assetKind != SavingsAssetKind.GOLD_RESERVE && it.isActive }
                        .sumOf { it.principalPiasters }
                }
                val goldTotalPiasters = remember(state.savingsCertificates) {
                    state.savingsCertificates
                        .filter { it.assetKind == SavingsAssetKind.GOLD_RESERVE && it.isActive }
                        .sumOf { (it.grams * it.pricePerGramPiasters).roundToLong() }
                }
                val expectedMonthlyYield = remember(state.savingsCertificates) {
                    state.savingsCertificates
                        .filter { it.assetKind != SavingsAssetKind.GOLD_RESERVE && it.isActive }
                        .sumOf { ((it.principalPiasters.toDouble() * (it.annualYieldPercent / 100.0)) / 12.0).roundToLong() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FinancialCard(
                            elevated = true,
                            borderColor = GoldAccent.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("certificates_summary_card")
                        ) {
                            Text(
                                text = AppStrings.text("محفظة الشهادات والودائع والذهب", "Certificates, Deposits & Gold Portfolio", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = AppStrings.text("أصل الشهادات", "Certificates Principal", lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedMoneyText(
                                        piasters = certsTotalPrincipal,
                                        lang = lang,
                                        hideAmounts = hide,
                                        color = SkyCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = AppStrings.text("قيمة الذهب الحالية", "Gold Market Value", lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedMoneyText(
                                        piasters = goldTotalPiasters,
                                        lang = lang,
                                        hideAmounts = hide,
                                        color = GoldAccent
                                    )
                                }
                            }
                            if (expectedMonthlyYield > 0L) {
                                Spacer(Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = AppStrings.text("متوسط العائد الشهري المتوقع", "Est. Monthly Yield", lang),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MoneyFormatter.formatPiasters(expectedMonthlyYield, language = lang, hideAmounts = hide),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = positiveColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (state.savingsCertificates.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد شهادات ادخار أو سبائك ذهب مسجلة", "No certificates or gold assets yet", lang),
                                subtitle = AppStrings.text(
                                    "سجل شهاداتك البنكية بعائدها الشهري أو السنوي أو مدخرات الذهب لاحتساب صافي ثروتك بدقة.",
                                    "Track bank certificates with auto-credited interest or gold reserves with live karat valuation.",
                                    lang
                                ),
                                icon = Icons.Rounded.WorkspacePremium
                            )
                        }
                    } else {
                        items(state.savingsCertificates, key = { it.id }) { cert ->
                            val isGold = cert.assetKind == SavingsAssetKind.GOLD_RESERVE
                            val goldVal = (cert.grams * cert.pricePerGramPiasters).roundToLong()
                            val linkedAccName = cert.linkedAccountId?.let { aid ->
                                state.accounts.firstOrNull { it.id == aid }?.name
                            }
                            FinancialCard(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                    .testTag("certificate_card_${cert.id}"),
                                borderColor = (if (isGold) GoldAccent else SkyCyan).copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background((if (isGold) GoldAccent else SkyCyan).copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isGold) Icons.Rounded.Diamond else Icons.Rounded.WorkspacePremium,
                                                contentDescription = null,
                                                tint = if (isGold) GoldAccent else SkyCyan
                                            )
                                        }
                                        Column {
                                            Text(cert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            if (isGold) {
                                                Text(
                                                    text = AppStrings.text(
                                                        "ذهب عيار ${cert.karat} • ${cert.grams} جم × ${MoneyFormatter.formatPiasters(cert.pricePerGramPiasters, language = lang, hideAmounts = hide)}",
                                                        "Gold ${cert.karat}K • ${cert.grams}g",
                                                        lang
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                val cycleLabel = when (cert.payoutCycle) {
                                                    PayoutCycle.MONTHLY -> AppStrings.text("شهري", "Monthly", lang)
                                                    PayoutCycle.QUARTERLY -> AppStrings.text("ربع سنوي", "Quarterly", lang)
                                                    PayoutCycle.SEMI_ANNUAL -> AppStrings.text("نصف سنوي", "Semi-Annual", lang)
                                                    PayoutCycle.ANNUAL -> AppStrings.text("سنوي", "Annual", lang)
                                                    PayoutCycle.MATURITY -> AppStrings.text("عند الاستحقاق", "At Maturity", lang)
                                                }
                                                Text(
                                                    text = buildString {
                                                        if (cert.institutionName.isNotBlank()) append("${cert.institutionName} • ")
                                                        append("${cert.annualYieldPercent}% ($cycleLabel)")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (linkedAccName != null) {
                                                    Text(
                                                        text = "${AppStrings.text("يُصرف في:", "Credits to:", lang)} $linkedAccName • ${formatReadableDate(cert.nextPayoutMillis, lang)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = positiveColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    AnimatedMoneyText(
                                        piasters = if (isGold) goldVal else cert.principalPiasters,
                                        lang = lang,
                                        hideAmounts = hide,
                                        color = if (isGold) GoldAccent else SkyCyan
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isGold) {
                                        Button(
                                            onClick = { onCreditCertificateInterest(cert.id) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("credit_interest_btn_${cert.id}")
                                        ) {
                                            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(AppStrings.text("صرف العائد الآن", "Credit Interest", lang))
                                        }
                                    } else {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = GoldAccent.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = AppStrings.text("مخزون قيمة آمن", "Inflation Hedge", lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldAccent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteCertificate(cert) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = AppStrings.text("حذف الأصل", "Delete asset", lang),
                                            tint = MaterialTheme.colorScheme.error
                                        )
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
        var colorHex by remember { mutableStateOf("#22D3EE") }

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
                        listOf("#22D3EE", "#4F8CFF", "#8B5CF6", "#F59E0B", "#F472B6").forEach { hex ->
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
        var feeText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        val fromAcc = activeAccs.firstOrNull { it.id == fromId }
        val toAcc = activeAccs.firstOrNull { it.id == toId }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text(AppStrings.text("تحويل بين الحسابات", "Account Transfer", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.text("من حساب:", "From:", lang), style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = {
                                val prevFrom = fromId
                                val prevTo = toId
                                fromId = prevTo
                                toId = prevFrom
                            },
                            modifier = Modifier.size(34.dp).testTag("dialog_swap_transfer_btn")
                        ) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = AppStrings.text("عكس التحويل", "Swap accounts", lang),
                                tint = SkyCyan
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.forEach { acc ->
                            FilterChip(
                                selected = fromId == acc.id,
                                onClick = {
                                    fromId = acc.id
                                    if (toId == acc.id) {
                                        toId = activeAccs.firstOrNull { it.id != acc.id }?.id ?: toId
                                    }
                                },
                                label = { Text("${acc.name} (${acc.currency})") }
                            )
                        }
                    }
                    Text(AppStrings.text("إلى حساب:", "To:", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccs.filter { it.id != fromId }.forEach { acc ->
                            FilterChip(
                                selected = toId == acc.id,
                                onClick = { toId = acc.id },
                                label = { Text("${acc.name} (${acc.currency})") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(
                                AppStrings.text(
                                    "المبلغ (${fromAcc?.currency ?: state.preferences.currencyCode})",
                                    "Amount (${fromAcc?.currency ?: state.preferences.currencyCode})",
                                    lang
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("dialog_transfer_amount_input")
                    )
                    OutlinedTextField(
                        value = feeText,
                        onValueChange = { feeText = it },
                        label = {
                            Text(
                                AppStrings.text(
                                    "رسوم التحويل (اختياري)",
                                    "Transfer Fee (optional)",
                                    lang
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("dialog_transfer_fee_input")
                    )
                    if (fromAcc != null && toAcc != null) {
                        val previewPiasters = MoneyFormatter.parseAllowZeroToPiasters(amountText) ?: 0L
                        Text(
                            text = buildString {
                                append("${fromAcc.name} ➔ ${toAcc.name}")
                                if (previewPiasters > 0L && fromAcc.currency != toAcc.currency) {
                                    val conv = CurrencyConverter.convertPiasters(previewPiasters, fromAcc.currency, toAcc.currency)
                                    append(" (~${MoneyFormatter.formatPiasters(conv, toAcc.currency, lang)})")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SkyCyan
                        )
                    }
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
                        val feePiasters = MoneyFormatter.parseAllowZeroToPiasters(feeText) ?: 0L
                        if (piasters != null && fromId != toId) {
                            onTransfer(fromId, toId, piasters, notes, feePiasters)
                            showTransferDialog = false
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_transfer_btn")
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
                            onCreateGoal(name, preset, target, null, "#22D3EE")
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

    // Add Certificate / Deposit / Gold Dialog
    if (showAddCertificateDialog) {
        val activeAccs = state.accounts.filter { !it.isArchived }
        var assetKind by remember { mutableStateOf(SavingsAssetKind.BANK_CERTIFICATE) }
        var title by remember { mutableStateOf("") }
        var institution by remember { mutableStateOf("البنك الأهلي المصري") }
        var principalText by remember { mutableStateOf("") }
        var yieldText by remember { mutableStateOf("27") }
        var cycle by remember { mutableStateOf(PayoutCycle.MONTHLY) }
        var linkedAccId by remember { mutableStateOf(activeAccs.firstOrNull()?.id) }
        var gramsText by remember { mutableStateOf("") }
        var karat by remember { mutableIntStateOf(21) }
        var pricePerGramText by remember { mutableStateOf("4000") }

        AlertDialog(
            onDismissRequest = { showAddCertificateDialog = false },
            title = { Text(AppStrings.text("إضافة شهادة بنكية أو ذهب", "Add Certificate or Gold", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = assetKind == SavingsAssetKind.BANK_CERTIFICATE,
                            onClick = { assetKind = SavingsAssetKind.BANK_CERTIFICATE },
                            label = { Text(AppStrings.text("شهادة / وديعة", "Certificate / Deposit", lang)) },
                            modifier = Modifier.testTag("cert_kind_certificate")
                        )
                        FilterChip(
                            selected = assetKind == SavingsAssetKind.GOLD_RESERVE,
                            onClick = { assetKind = SavingsAssetKind.GOLD_RESERVE },
                            label = { Text(AppStrings.text("ذهب / سبائك", "Gold Reserve", lang)) },
                            modifier = Modifier.testTag("cert_kind_gold")
                        )
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = {
                            Text(
                                if (assetKind == SavingsAssetKind.GOLD_RESERVE) {
                                    AppStrings.text("الوصف (مثال: سبيكة BTC 10 جم)", "Title (e.g., 10g Bar)", lang)
                                } else {
                                    AppStrings.text("اسم الشهادة (مثال: البلاتينية 27%)", "Title", lang)
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.testTag("cert_title_input")
                    )
                    if (assetKind == SavingsAssetKind.GOLD_RESERVE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(24, 21, 18).forEach { k ->
                                FilterChip(
                                    selected = karat == k,
                                    onClick = { karat = k },
                                    label = { Text("${AppStrings.text("عيار", "K", lang)} $k") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = gramsText,
                            onValueChange = { gramsText = it },
                            label = { Text(AppStrings.text("الوزن بالجرام", "Weight (grams)", lang)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.testTag("cert_grams_input")
                        )
                        OutlinedTextField(
                            value = pricePerGramText,
                            onValueChange = { pricePerGramText = it },
                            label = { Text(AppStrings.text("سعر الجرام الحالي (EGP)", "Price / Gram", lang)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.testTag("cert_gram_price_input")
                        )
                    } else {
                        OutlinedTextField(
                            value = institution,
                            onValueChange = { institution = it },
                            label = { Text(AppStrings.text("البنك المصدر", "Bank / Institution", lang)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = principalText,
                            onValueChange = { principalText = it },
                            label = { Text(AppStrings.text("قيمة الشهادة (EGP)", "Principal Amount", lang)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.testTag("cert_principal_input")
                        )
                        OutlinedTextField(
                            value = yieldText,
                            onValueChange = { yieldText = it },
                            label = { Text(AppStrings.text("العائد السنوي (%)", "Annual Yield (%)", lang)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.testTag("cert_yield_input")
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                PayoutCycle.MONTHLY to AppStrings.text("شهري", "Monthly", lang),
                                PayoutCycle.QUARTERLY to AppStrings.text("ربع سنوي", "Quarterly", lang),
                                PayoutCycle.ANNUAL to AppStrings.text("سنوي", "Annual", lang),
                                PayoutCycle.MATURITY to AppStrings.text("نهاية المدة", "Maturity", lang)
                            ).forEach { (c, lbl) ->
                                FilterChip(selected = cycle == c, onClick = { cycle = c }, label = { Text(lbl) })
                            }
                        }
                        if (activeAccs.isNotEmpty()) {
                            Text(AppStrings.text("حساب صرف العائد:", "Credit interest to:", lang), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeAccs.forEach { acc ->
                                    FilterChip(
                                        selected = linkedAccId == acc.id,
                                        onClick = { linkedAccId = acc.id },
                                        label = { Text(acc.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        if (assetKind == SavingsAssetKind.GOLD_RESERVE) {
                            val grams = gramsText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val perGram = MoneyFormatter.parseToPiasters(pricePerGramText) ?: 0L
                            if (grams > 0.0 && perGram > 0L) {
                                onSaveCertificate(title, assetKind, "", 0L, 0.0, PayoutCycle.MATURITY, null, grams, karat, perGram, "")
                                showAddCertificateDialog = false
                            }
                        } else {
                            val principal = MoneyFormatter.parseToPiasters(principalText) ?: 0L
                            val yld = yieldText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (principal > 0L) {
                                onSaveCertificate(title, assetKind, institution, principal, yld, cycle, linkedAccId, 0.0, 21, 0L, "")
                                showAddCertificateDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_save_cert_btn")
                ) {
                    Text(AppStrings.text("حفظ", "Save", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCertificateDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    // Update Gold Price Dialog
    if (showGoldPriceDialog) {
        var selectedKarat by remember { mutableIntStateOf(21) }
        var priceInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showGoldPriceDialog = false },
            title = { Text(AppStrings.text("تحديث سعر الذهب اليوم", "Update Gold Price", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24, 21, 18).forEach { k ->
                            FilterChip(
                                selected = selectedKarat == k,
                                onClick = { selectedKarat = k },
                                label = { Text("${AppStrings.text("عيار", "Karat", lang)} $k") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text(AppStrings.text("السعر الجديد للجرام (EGP)", "New Price / Gram", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("gold_price_update_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val piasters = MoneyFormatter.parseToPiasters(priceInput)
                        if (piasters != null && piasters > 0L) {
                            onUpdateGoldPrice(selectedKarat, piasters)
                            showGoldPriceDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_gold_price_update_btn")
                ) {
                    Text(AppStrings.text("تحديث الكل", "Update All", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoldPriceDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }
}
