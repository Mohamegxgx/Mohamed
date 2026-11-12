package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.FinancialCard
import com.example.ui.components.MoneyText
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SkyCyan

@Composable
fun PendingTransactionCard(
    item: PendingTransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    lang: AppLanguage,
    hideAmounts: Boolean,
    onQuickApprove: (PendingTransactionEntity, Long, Long?) -> Unit,
    onOpenEdit: (PendingTransactionEntity) -> Unit,
    onReject: (Long) -> Unit,
    onIgnoreSource: (Long) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val resolvedAccountId = item.suggestedAccountId ?: activeAccounts.firstOrNull()?.id ?: 1L
    val accountName = accounts.firstOrNull { it.id == resolvedAccountId }?.name ?: "-"
    val categoryName = item.suggestedCategoryId?.let { cid ->
        categories.firstOrNull { it.id == cid }?.let { if (lang == AppLanguage.AR) it.nameAr else it.nameEn }
    }
    val typeColor = when (item.mappedType) {
        TransactionType.INCOME -> EmeraldPrimary
        TransactionType.EXPENSE -> CoralExpense
        TransactionType.TRANSFER -> SkyCyan
        else -> GoldAccent
    }
    val confPercent = (item.confidenceScore * 100).toInt().coerceIn(10, 100)

    FinancialCard(
        modifier = Modifier.testTag("pending_card_${item.id}"),
        borderColor = typeColor.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${AppStrings.operationKindLabel(item.operationKind, lang)} • ${item.sourceName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${AppStrings.channelLabel(item.channel, lang)} • ${formatReadableDateTime(item.transactionTimestampMillis, lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = typeColor.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "$confPercent%",
                    color = typeColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoneyText(
                piasters = item.amountPiasters,
                currency = item.currency,
                lang = lang,
                hideAmounts = hideAmounts,
                color = typeColor,
                style = MaterialTheme.typography.titleLarge,
                showSign = false
            )
            Text(
                text = AppStrings.transactionTypeLabel(item.mappedType, lang),
                style = MaterialTheme.typography.labelLarge,
                color = typeColor
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.extractedSummary,
            style = MaterialTheme.typography.bodyMedium
        )

        val metaParts = buildList {
            add("${AppStrings.text("الحساب المقترح", "Account", lang)}: $accountName")
            if (categoryName != null) add("${AppStrings.text("الفئة", "Category", lang)}: $categoryName")
            if (!item.counterpartyName.isNullOrBlank()) add("${AppStrings.text("الطرف", "Counterparty", lang)}: ${item.counterpartyName}")
            if (!item.referenceNumber.isNullOrBlank()) add("${AppStrings.text("المرجع", "Ref", lang)}: ${item.referenceNumber}")
            if (item.balanceAfterPiasters != null) {
                val balStr = MoneyFormatter.formatPiasters(item.balanceAfterPiasters, item.currency, lang, hideAmounts)
                add("${AppStrings.text("الرصيد بعد العملية", "Balance after", lang)}: $balStr")
            }
        }
        Text(
            text = metaParts.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onQuickApprove(item, resolvedAccountId, item.suggestedCategoryId) },
                modifier = Modifier.weight(1f).testTag("accept_pending_${item.id}")
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(AppStrings.text("قبول", "Accept", lang), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = { onOpenEdit(item) },
                modifier = Modifier.weight(1f).testTag("edit_pending_${item.id}")
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(AppStrings.text("تعديل", "Edit", lang), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = { onReject(item.id) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralExpense),
                modifier = Modifier.testTag("reject_pending_${item.id}")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = AppStrings.text("رفض", "Reject", lang), modifier = Modifier.size(16.dp))
            }
        }

        if (item.sourceId != null) {
            TextButton(
                onClick = { onIgnoreSource(item.sourceId) },
                modifier = Modifier.align(Alignment.End).testTag("ignore_source_pending_${item.id}")
            ) {
                Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                Text(
                    text = AppStrings.text("تجاهل هذا المصدر مستقبلاً", "Ignore this source", lang),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingReviewEditDialog(
    item: PendingTransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (
        pendingId: Long,
        accountId: Long,
        targetAccountId: Long?,
        categoryId: Long?,
        amountPiasters: Long,
        type: TransactionType,
        description: String
    ) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    var amountInput by remember(item.id) {
        mutableStateOf(MoneyFormatter.piastersToDecimalString(item.amountPiasters))
    }
    var selectedType by remember(item.id) { mutableStateOf(item.mappedType) }
    var selectedAccountId by remember(item.id) {
        mutableStateOf(item.suggestedAccountId ?: activeAccounts.firstOrNull()?.id ?: 1L)
    }
    var targetAccountId by remember(item.id) {
        mutableStateOf(
            item.targetAccountId ?: activeAccounts.firstOrNull { it.id != selectedAccountId }?.id
        )
    }
    var selectedCategoryId by remember(item.id) { mutableStateOf(item.suggestedCategoryId) }
    var description by remember(item.id) { mutableStateOf(item.extractedSummary) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.text("مراجعة وتعديل العملية", "Review & Edit Transaction", lang)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER).forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = {
                                selectedType = t
                                if (t == TransactionType.TRANSFER && targetAccountId == null) {
                                    targetAccountId = activeAccounts.firstOrNull { it.id != selectedAccountId }?.id
                                }
                            },
                            label = { Text(AppStrings.transactionTypeLabel(t, lang)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMsg = null
                    },
                    label = { Text(AppStrings.text("المبلغ", "Amount", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pending_edit_amount")
                )

                Text(
                    text = if (selectedType == TransactionType.TRANSFER) {
                        AppStrings.text("من حساب", "From Account", lang)
                    } else {
                        AppStrings.text("الحساب", "Account", lang)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeAccounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }

                if (selectedType == TransactionType.TRANSFER) {
                    Text(AppStrings.text("إلى حساب", "To Account", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAccounts.filter { it.id != selectedAccountId }.forEach { acc ->
                            FilterChip(
                                selected = targetAccountId == acc.id,
                                onClick = { targetAccountId = acc.id },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                } else {
                    Text(AppStrings.text("الفئة", "Category", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.filter { it.type == selectedType }.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(AppStrings.text("الوصف", "Description", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(amountInput)
                    if (piasters == null || piasters <= 0L) {
                        errorMsg = AppStrings.text("أدخل مبلغًا صحيحًا", "Enter valid positive amount", lang)
                        return@Button
                    }
                    if (selectedType == TransactionType.TRANSFER && (targetAccountId == null || targetAccountId == selectedAccountId)) {
                        errorMsg = AppStrings.text("اختر حسابًا مستلمًا مختلفًا", "Select a distinct target account", lang)
                        return@Button
                    }
                    onConfirm(
                        item.id,
                        selectedAccountId,
                        if (selectedType == TransactionType.TRANSFER) targetAccountId else null,
                        if (selectedType != TransactionType.TRANSFER) selectedCategoryId else null,
                        piasters,
                        selectedType,
                        description
                    )
                },
                modifier = Modifier.testTag("confirm_pending_edit_btn")
            ) {
                Text(AppStrings.text("اعتماد وإضافة", "Approve & Save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
        }
    )
}

@Composable
fun PasteFinancialMessageDialog(
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onAnalyze: (sender: String, body: String) -> Unit
) {
    var sender by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.text("تحليل رسالة مالية محليًا", "Analyze Financial Message", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = AppStrings.text(
                        "الصق نص رسالة بنك أو محفظة (أو عدة رسائل مفصولة بسطر فارغ). يتم التحليل محليًا 100% دون إنترنت.",
                        "Paste bank or wallet message(s). 100% local offline parsing.",
                        lang
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text(AppStrings.text("المرسل (اختياري، مثل VodafoneCash / CIB)", "Sender (optional)", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("paste_sender_input")
                )
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    label = { Text(AppStrings.text("نص الرسالة المالية", "Message text", lang)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("paste_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (messageBody.isNotBlank()) {
                        onAnalyze(sender.ifBlank { "ManualPaste" }, messageBody)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("analyze_pasted_btn")
            ) {
                Text(AppStrings.text("تحليل واستخراج", "Analyze", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFinancialSourceDialog(
    accounts: List<AccountEntity>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (nameAr: String, nameEn: String, keywords: String, defaultAccountId: Long?) -> Unit
) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull { !it.isArchived }?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.text("إضافة مصدر مالي مخصص", "Add Financial Source", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    label = { Text(AppStrings.text("اسم البنك أو المحفظة", "Bank or Wallet Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(AppStrings.text("الاسم بالإنجليزية (اختياري)", "English Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(AppStrings.text("كلمات التعرف (مفصولة بفاصلة)", "Keywords (comma-separated)", lang)) },
                    placeholder = { Text("hsbc,alexbank,telda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(AppStrings.text("الحساب الافتراضي للربط", "Default Linked Account", lang), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.filter { !it.isArchived }.forEach { acc ->
                        FilterChip(
                            selected = selectedAccId == acc.id,
                            onClick = { selectedAccId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameAr.isNotBlank() && keywords.isNotBlank()) {
                    onSave(nameAr, nameEn, keywords, selectedAccId)
                    onDismiss()
                }
            }) {
                Text(AppStrings.text("حفظ", "Save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
        }
    )
}
