package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionSort
import com.example.domain.model.TransactionType
import com.example.ui.components.AiPulseBeacon
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.MoneyText
import com.example.ui.components.ScreenHeaderBar
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.AutoCategorizeState
import com.example.ui.viewmodel.MainUiState

@Composable
fun TransactionsScreen(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onTypeFilter: (TransactionType?) -> Unit,
    onAccountFilter: (Long?) -> Unit,
    onSortChange: (TransactionSort) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    autoCategorizeState: AutoCategorizeState = AutoCategorizeState.Idle,
    onAutoCategorizeUncategorized: () -> Unit = {}
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val positiveColor = semanticPositiveColor()
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeaderBar(
            title = AppStrings.text("سجل المعاملات", "Transactions", lang),
            subtitle = AppStrings.text("${state.filteredTransactions.size} معاملة مطابقة", "${state.filteredTransactions.size} matching records", lang),
            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
            actions = {
                Box {
                    OutlinedButton(onClick = { sortMenuExpanded = true }) {
                        Text(AppStrings.sortLabel(state.selectedSort, lang))
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        TransactionSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(AppStrings.sortLabel(sort, lang)) },
                                onClick = {
                                    onSortChange(sort)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(AppStrings.text("ابحث بالوصف أو الملاحظة...", "Search description or notes...", lang)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("tx_search_input")
        )

        // Type filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedTypeFilter == null,
                    onClick = { onTypeFilter(null) },
                    label = { Text(AppStrings.text("الكل", "All", lang)) }
                )
            }
            items(TransactionType.entries) { type ->
                FilterChip(
                    selected = state.selectedTypeFilter == type,
                    onClick = { onTypeFilter(if (state.selectedTypeFilter == type) null else type) },
                    label = { Text(AppStrings.transactionTypeLabel(type, lang)) }
                )
            }
        }

        // Account filter chips
        if (state.accounts.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountFilterId == null,
                        onClick = { onAccountFilter(null) },
                        label = { Text(AppStrings.text("كل الحسابات", "All Accounts", lang)) }
                    )
                }
                items(state.accounts.filter { !it.isArchived }) { acc ->
                    FilterChip(
                        selected = state.selectedAccountFilterId == acc.id,
                        onClick = { onAccountFilter(if (state.selectedAccountFilterId == acc.id) null else acc.id) },
                        label = { Text(acc.name) }
                    )
                }
            }
        }

        val uncategorizedCount = remember(state.transactions) {
            state.transactions.count {
                (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) && it.categoryId == null
            }
        }
        val isCategorizing = autoCategorizeState is AutoCategorizeState.Loading

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uncategorizedCount > 0 || isCategorizing) {
                item(key = "auto_categorize_banner") {
                    FinancialCard(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                            .testTag("tx_auto_categorize_banner"),
                        borderColor = SkyCyan.copy(alpha = 0.45f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AiPulseBeacon(
                                    isPulsing = isCategorizing,
                                    color = SkyCyan,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = SkyCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppStrings.text(
                                            "$uncategorizedCount معاملة بدون فئة",
                                            "$uncategorizedCount uncategorized transaction(s)",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = AppStrings.text(
                                            "دع Gemini يصنفها تلقائيًا حسب الوصف",
                                            "Let Gemini auto-categorize by description",
                                            lang
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onAutoCategorizeUncategorized,
                                enabled = !isCategorizing,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tx_auto_categorize_btn")
                            ) {
                                if (isCategorizing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(AppStrings.text("جاري التصنيف...", "Categorizing...", lang))
                                } else {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(AppStrings.text("تصنيف وتنظيم الآن", "Auto-Organize Now", lang))
                                }
                            }
                        }
                    }
                }
            }
            if (state.filteredTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = AppStrings.text("لا توجد معاملات مطابقة", "No matching transactions", lang),
                        subtitle = AppStrings.text("اضغط على زر + لإضافة معاملة جديدة بسرعة", "Tap + to quickly record a transaction", lang),
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong
                    )
                }
            } else {
                items(state.filteredTransactions, key = { it.id }) { tx ->
                    val account = accountMap[tx.accountId]
                    val cat = tx.categoryId?.let { categoryMap[it] }
                    val accent = when (tx.type) {
                        TransactionType.INCOME -> positiveColor
                        TransactionType.EXPENSE -> CoralExpense
                        TransactionType.TRANSFER -> SkyCyan
                        TransactionType.SAVINGS_CONTRIBUTION -> GoldAccent
                        TransactionType.DEBT_PAYMENT -> MaterialTheme.colorScheme.secondary
                    }
                    val icon = when (tx.type) {
                        TransactionType.INCOME -> Icons.Rounded.ArrowDownward
                        TransactionType.EXPENSE -> Icons.Rounded.ArrowUpward
                        TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
                        TransactionType.SAVINGS_CONTRIBUTION -> Icons.Rounded.Savings
                        TransactionType.DEBT_PAYMENT -> Icons.AutoMirrored.Rounded.ReceiptLong
                    }

                    FinancialCard(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                            .testTag("tx_item_${tx.id}"),
                        onClick = { onTransactionClick(tx) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = tx.description,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (cat != null) {
                                            val catColor = parseHexColor(cat.colorHex, accent)
                                            Surface(
                                                color = catColor.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = catColor,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    val sub = buildString {
                                        append(account?.name ?: "")
                                        if (tx.source != com.example.domain.model.TransactionSource.MANUAL_ENTRY) {
                                            append(" • ")
                                            append(AppStrings.txSourceLabel(tx.source, lang))
                                        }
                                        if (!tx.referenceNumber.isNullOrBlank()) {
                                            append(" #${tx.referenceNumber}")
                                        }
                                        append(" • ")
                                        append(formatReadableDateTime(tx.timestampMillis, lang))
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            val signedAmount = when (tx.type) {
                                TransactionType.INCOME -> tx.amountPiasters
                                TransactionType.EXPENSE -> -tx.amountPiasters
                                else -> tx.amountPiasters
                            }
                            MoneyText(
                                piasters = signedAmount,
                                lang = lang,
                                hideAmounts = hide,
                                currency = account?.currency ?: state.preferences.currencyCode,
                                color = accent,
                                showSign = tx.type == TransactionType.INCOME || tx.type == TransactionType.EXPENSE
                            )
                        }
                    }
                }
            }
        }
    }
}
