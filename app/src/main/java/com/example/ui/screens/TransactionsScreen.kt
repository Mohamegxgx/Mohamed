package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.components.MoneyText
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan
import com.example.ui.viewmodel.MainUiState

@Composable
fun TransactionsScreen(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onTypeFilter: (TransactionType?) -> Unit,
    onAccountFilter: (Long?) -> Unit,
    onSortChange: (TransactionSort) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.text("سجل المعاملات", "Transactions", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        TransactionType.INCOME -> EmeraldPrimary
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
                            .clickable { onTransactionClick(tx) }
                            .testTag("tx_item_${tx.id}")
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
                                    Text(
                                        text = tx.description,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = buildString {
                                        append(account?.name ?: "")
                                        if (cat != null) {
                                            append(" • ")
                                            append(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn)
                                        }
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
