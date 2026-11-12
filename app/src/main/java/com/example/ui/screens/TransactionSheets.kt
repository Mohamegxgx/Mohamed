package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.MoneyText
import com.example.ui.components.formatReadableDateTime
import com.example.ui.theme.CoralExpense
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddTransactionSheet(
    state: MainUiState,
    editingTransaction: TransactionEntity?,
    initialType: TransactionType? = null,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        amountPiasters: Long,
        type: TransactionType,
        accountId: Long,
        targetAccountId: Long?,
        categoryId: Long?,
        savingsGoalId: Long?,
        debtId: Long?,
        description: String,
        notes: String,
        attachmentUri: String?,
        learnMerchantRule: Boolean
    ) -> Unit
) {
    val lang = state.preferences.language
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type ?: initialType ?: TransactionType.EXPENSE)
    }
    var amountInput by remember(editingTransaction) {
        mutableStateOf(
            editingTransaction?.let { MoneyFormatter.piastersToDecimalString(it.amountPiasters) } ?: ""
        )
    }
    var selectedAccountId by remember(editingTransaction, state.accounts) {
        mutableStateOf(
            editingTransaction?.accountId
                ?: state.accounts.firstOrNull { !it.isArchived }?.id
                ?: 1L
        )
    }
    var targetAccountId by remember(editingTransaction, state.accounts) {
        mutableStateOf(
            editingTransaction?.targetAccountId
                ?: state.accounts.filter { !it.isArchived && it.id != selectedAccountId }.firstOrNull()?.id
        )
    }
    var selectedCategoryId by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.categoryId)
    }
    var selectedGoalId by remember(editingTransaction, state.savingsGoals) {
        mutableStateOf(editingTransaction?.savingsGoalId ?: state.savingsGoals.firstOrNull()?.id)
    }
    var selectedDebtId by remember(editingTransaction, state.debts) {
        mutableStateOf(editingTransaction?.debtId ?: state.debts.firstOrNull()?.id)
    }
    var description by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.description ?: "")
    }
    var notes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }
    var attachmentUri by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.attachmentUri)
    }
    var learnMerchantRule by remember { mutableStateOf(false) }
    var showSmartPhraseBox by remember { mutableStateOf(false) }
    var smartPhraseInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun applySmartPhrase(phrase: String) {
        if (phrase.isBlank()) return
        val parsed = VoiceTransactionParser.parse(
            rawPhrase = phrase,
            accounts = state.accounts,
            categories = state.categories,
            merchantRules = state.merchantRules
        )
        selectedType = parsed.type
        parsed.amountPiasters?.let { amountInput = MoneyFormatter.piastersToDecimalString(it) }
        parsed.suggestedAccountId?.let { selectedAccountId = it }
        parsed.targetAccountId?.let { targetAccountId = it }
        selectedCategoryId = parsed.suggestedCategoryId
        if (parsed.description.isNotBlank()) {
            description = parsed.description
        }
        errorMsg = null
    }

    // Zero-permission Android Speech Recognizer Intent (no RECORD_AUDIO permission required)
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                smartPhraseInput = spoken
                showSmartPhraseBox = true
                applySmartPhrase(spoken)
            }
        }
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachmentUri = uri.toString()
        }
    }

    val selectedAccountCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency
        ?: state.preferences.currencyCode

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingTransaction != null) {
                    AppStrings.text("تعديل المعاملة", "Edit Transaction", lang)
                } else {
                    AppStrings.text("تسجيل معاملة سريعة", "Quick Transaction", lang)
                },
                style = MaterialTheme.typography.titleLarge
            )

            // Voice & Smart Phrase Quick-Fill Controls
            if (editingTransaction == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE,
                                    if (lang == AppLanguage.AR) "ar-EG" else "en-US"
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    AppStrings.text("مثال: صرفت 250 جنيه أكل من الكاش", "e.g., Spent 250 food from cash", lang)
                                )
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure {
                                    showSmartPhraseBox = true
                                    errorMsg = AppStrings.text(
                                        "خدمة الإملاء الصوتي غير متوفرة هنا — يمكنك كتابة الجملة الذكية مباشرة.",
                                        "Speech service unavailable here — type a smart phrase below.",
                                        lang
                                    )
                                }
                        },
                        modifier = Modifier.weight(1f).testTag("voice_input_btn")
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = AppStrings.text("إملاء صوتي", "Voice Fill", lang),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    FilterChip(
                        selected = showSmartPhraseBox,
                        onClick = { showSmartPhraseBox = !showSmartPhraseBox },
                        modifier = Modifier.testTag("toggle_smart_phrase_btn"),
                        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(AppStrings.text("جملة ذكية", "Smart Text", lang)) }
                    )
                }

                AnimatedVisibility(visible = showSmartPhraseBox) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = smartPhraseInput,
                            onValueChange = { smartPhraseInput = it },
                            label = {
                                Text(
                                    AppStrings.text(
                                        "اكتب جملة: مثل «صرفت 180 جنيه أوبر من الكاش»",
                                        "Type phrase: e.g. \"Spent 180 Uber cash\"",
                                        lang
                                    )
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("smart_phrase_input")
                        )
                        Button(
                            onClick = { applySmartPhrase(smartPhraseInput) },
                            modifier = Modifier.fillMaxWidth().testTag("apply_smart_phrase_btn")
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = AppStrings.text("تحليل وتعبئة الحقول تلقائيًا", "Parse & Auto-Fill", lang),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }

            // Type Selector
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            selectedCategoryId = null
                        },
                        modifier = Modifier.testTag("tx_type_${type.name.lowercase()}"),
                        label = { Text(AppStrings.transactionTypeLabel(type, lang)) }
                    )
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    errorMsg = null
                },
                label = { Text(AppStrings.text("المبلغ ($selectedAccountCurrency)", "Amount ($selectedAccountCurrency)", lang)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tx_amount_input")
            )

            // Source Account Picker
            Text(
                text = if (selectedType == TransactionType.TRANSFER) {
                    AppStrings.text("من حساب", "From Account", lang)
                } else {
                    AppStrings.text("الحساب", "Account", lang)
                },
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.filter { !it.isArchived }.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text("${acc.name} (${acc.currency})") }
                    )
                }
            }

            // Target Account for Transfer
            if (selectedType == TransactionType.TRANSFER) {
                Text(AppStrings.text("إلى حساب", "To Account", lang), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.filter { !it.isArchived && it.id != selectedAccountId }.forEach { acc ->
                        FilterChip(
                            selected = targetAccountId == acc.id,
                            onClick = { targetAccountId = acc.id },
                            label = { Text("${acc.name} (${acc.currency})") }
                        )
                    }
                }
            }

            // Category chips for Income / Expense
            if (selectedType == TransactionType.INCOME || selectedType == TransactionType.EXPENSE) {
                val cats = state.categories.filter { it.type == selectedType }
                Text(AppStrings.text("الفئة", "Category", lang), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn) }
                        )
                    }
                }
            }

            // Savings Goal selector
            if (selectedType == TransactionType.SAVINGS_CONTRIBUTION) {
                Text(AppStrings.text("هدف الادخار", "Savings Goal", lang), style = MaterialTheme.typography.labelLarge)
                if (state.savingsGoals.isEmpty()) {
                    Text(
                        text = AppStrings.text("أضف هدف ادخار أولًا من تبويب الحسابات والادخار.", "Create a savings goal first.", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.savingsGoals.forEach { g ->
                            FilterChip(
                                selected = selectedGoalId == g.id,
                                onClick = { selectedGoalId = g.id },
                                label = { Text(g.name) }
                            )
                        }
                    }
                }
            }

            // Debt selector
            if (selectedType == TransactionType.DEBT_PAYMENT) {
                Text(AppStrings.text("الدين المرتبط", "Linked Debt", lang), style = MaterialTheme.typography.labelLarge)
                if (state.debts.isEmpty()) {
                    Text(
                        text = AppStrings.text("أضف دينًا أولًا من تبويب الالتزامات.", "Create a debt record first.", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.debts.forEach { d ->
                            FilterChip(
                                selected = selectedDebtId == d.id,
                                onClick = { selectedDebtId = d.id },
                                label = { Text(d.personName) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(AppStrings.text("الوصف", "Description", lang)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tx_description_input")
            )

            if (editingTransaction == null &&
                (selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME) &&
                selectedCategoryId != null &&
                description.trim().length >= 2
            ) {
                FilterChip(
                    selected = learnMerchantRule,
                    onClick = { learnMerchantRule = !learnMerchantRule },
                    modifier = Modifier.testTag("learn_merchant_rule_chip"),
                    leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = {
                        Text(
                            AppStrings.text(
                                "حفظ قاعدة لتصنيف «${description.trim()}» تلقائيًا مستقبلاً",
                                "Auto-categorize \"${description.trim()}\" in the future",
                                lang
                            )
                        )
                    }
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(AppStrings.text("ملاحظات إضافية (اختياري)", "Notes (optional)", lang)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = AppStrings.text("إرفاق إيصال", "Attach Receipt", lang),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                if (attachmentUri != null) {
                    AsyncImage(
                        model = attachmentUri,
                        contentDescription = "Receipt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val piasters = MoneyFormatter.parseToPiasters(amountInput)
                    if (piasters == null || piasters <= 0L) {
                        errorMsg = AppStrings.text("يرجى إدخال مبلغ صحيح أكبر من صفر", "Please enter a valid positive amount", lang)
                        return@Button
                    }
                    if (selectedType == TransactionType.TRANSFER && (targetAccountId == null || targetAccountId == selectedAccountId)) {
                        errorMsg = AppStrings.text("اختر حسابًا مختلفًا للتحويل", "Select a different target account", lang)
                        return@Button
                    }
                    onSave(
                        editingTransaction?.id,
                        piasters,
                        selectedType,
                        selectedAccountId,
                        if (selectedType == TransactionType.TRANSFER) targetAccountId else null,
                        if (selectedType == TransactionType.INCOME || selectedType == TransactionType.EXPENSE) selectedCategoryId else null,
                        if (selectedType == TransactionType.SAVINGS_CONTRIBUTION) selectedGoalId else null,
                        if (selectedType == TransactionType.DEBT_PAYMENT) selectedDebtId else null,
                        description,
                        notes,
                        attachmentUri,
                        learnMerchantRule
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_transaction_button")
            ) {
                Text(AppStrings.text("حفظ المعاملة", "Save Transaction", lang))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: TransactionEntity,
    state: MainUiState,
    onDismiss: () -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onDuplicate: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit
) {
    val lang = state.preferences.language
    val hide = state.preferences.hideAmounts
    val account = state.accounts.firstOrNull { it.id == tx.accountId }
    val targetAccount = tx.targetAccountId?.let { tid -> state.accounts.firstOrNull { it.id == tid } }
    val category = tx.categoryId?.let { cid -> state.categories.firstOrNull { it.id == cid } }
    val txCurrency = account?.currency ?: state.preferences.currencyCode

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tx.description, style = MaterialTheme.typography.titleLarge)
            MoneyText(
                piasters = tx.amountPiasters,
                lang = lang,
                hideAmounts = hide,
                currency = txCurrency,
                style = MaterialTheme.typography.headlineMedium
            )
            HorizontalDivider()
            DetailLine(AppStrings.text("النوع", "Type", lang), AppStrings.transactionTypeLabel(tx.type, lang))
            DetailLine(AppStrings.text("المصدر", "Source", lang), AppStrings.txSourceLabel(tx.source, lang))
            if (!tx.sourceIdentifier.isNullOrBlank()) {
                DetailLine(AppStrings.text("الجهة المالية", "Provider", lang), tx.sourceIdentifier)
            }
            if (!tx.referenceNumber.isNullOrBlank()) {
                DetailLine(AppStrings.text("الرقم المرجعي", "Reference", lang), tx.referenceNumber)
            }
            DetailLine(AppStrings.text("التاريخ والوقت", "Date & Time", lang), formatReadableDateTime(tx.timestampMillis, lang))
            DetailLine(AppStrings.text("الحساب", "Account", lang), account?.let { "${it.name} (${it.currency})" } ?: "-")
            if (targetAccount != null) {
                DetailLine(AppStrings.text("الحساب المستلم", "Target Account", lang), "${targetAccount.name} (${targetAccount.currency})")
            }
            if (category != null) {
                DetailLine(AppStrings.text("الفئة", "Category", lang), if (lang == AppLanguage.AR) category.nameAr else category.nameEn)
            }
            if (tx.notes.isNotBlank()) {
                DetailLine(AppStrings.text("ملاحظات", "Notes", lang), tx.notes)
            }
            if (!tx.attachmentUri.isNullOrBlank()) {
                AsyncImage(
                    model = tx.attachmentUri,
                    contentDescription = "Receipt Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEdit(tx) },
                    modifier = Modifier.weight(1f).testTag("edit_tx_btn")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(AppStrings.text("تعديل", "Edit", lang), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { onDuplicate(tx) },
                    modifier = Modifier.weight(1f).testTag("duplicate_tx_btn")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(AppStrings.text("تكرار", "Duplicate", lang), modifier = Modifier.padding(start = 4.dp))
                }
                Button(
                    onClick = { onDelete(tx) },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralExpense),
                    modifier = Modifier.weight(1f).testTag("delete_tx_btn")
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(AppStrings.text("حذف", "Delete", lang), modifier = Modifier.padding(start = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
