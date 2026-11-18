package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ReceiptStorageManager
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.launch
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.MoneyText
import com.example.ui.components.formatReadableDateTime
import com.example.ui.components.springPressEffect
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.semanticPositiveColor
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.QuickAutoFillState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddTransactionSheet(
    state: MainUiState,
    editingTransaction: TransactionEntity?,
    initialType: TransactionType? = null,
    autoFillState: QuickAutoFillState = QuickAutoFillState.Idle,
    onAiAutoFill: (String) -> Unit = {},
    onResetAiAutoFill: () -> Unit = {},
    onSuggestCategory: (String, TransactionType, (Long) -> Unit) -> Unit = { _, _, _ -> },
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
        learnMerchantRule: Boolean,
        transferFeePiasters: Long
    ) -> Unit
) {
    val lang = state.preferences.language
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type ?: initialType ?: TransactionType.EXPENSE)
    }
    var amountInput by remember(editingTransaction) {
        mutableStateOf(
            editingTransaction?.let { MoneyFormatter.piastersToDecimalString(it.amountPiasters) } ?: ""
        )
    }
    var transferFeeInput by remember(editingTransaction) { mutableStateOf("") }
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
    var aiConfidenceBadge by remember { mutableStateOf<Int?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { onResetAiAutoFill() }
    }

    LaunchedEffect(autoFillState) {
        if (autoFillState is QuickAutoFillState.Ready) {
            val res = autoFillState.result
            selectedType = res.type
            res.amountPiasters?.let { amountInput = MoneyFormatter.piastersToDecimalString(it) }
            transferFeeInput = if (res.feePiasters > 0L) {
                MoneyFormatter.piastersToDecimalString(res.feePiasters)
            } else {
                ""
            }
            res.sourceAccountId?.let { selectedAccountId = it }
            if (res.type == TransactionType.TRANSFER) {
                targetAccountId = res.targetAccountId
                    ?: state.accounts.firstOrNull { !it.isArchived && it.id != selectedAccountId }?.id
            }
            selectedCategoryId = res.categoryId
            if (res.description.isNotBlank()) description = res.description
            if (res.notes.isNotBlank()) notes = res.notes
            aiConfidenceBadge = res.confidencePercent
            errorMsg = null
        }
    }

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
        transferFeeInput = if (parsed.feePiasters > 0L) {
            MoneyFormatter.piastersToDecimalString(parsed.feePiasters)
        } else {
            ""
        }
        parsed.suggestedAccountId?.let { selectedAccountId = it }
        parsed.targetAccountId?.let { targetAccountId = it }
        selectedCategoryId = parsed.suggestedCategoryId
        if (parsed.description.isNotBlank()) {
            description = parsed.description
        }
        aiConfidenceBadge = null
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

    // Zero-permission Android Photo Picker (Play Policy compliant) + durable local copy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val durableUri = ReceiptStorageManager.persistReceiptUri(context, uri.toString())
                attachmentUri = durableUri ?: uri.toString()
            }
        }
    }

    val selectedAccount = state.accounts.firstOrNull { it.id == selectedAccountId }
    val selectedAccountCurrency = selectedAccount?.currency ?: state.preferences.currencyCode
    val targetAccount = targetAccountId?.let { tid -> state.accounts.firstOrNull { it.id == tid } }

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

            // Voice & AI Smart Phrase Quick-Fill Controls
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
                                    AppStrings.text("مثال: حولت 1500 من البنك للمحفظة برسوم 10", "e.g., Transferred 1500 from Bank to Wallet", lang)
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
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(AppStrings.text("ملء ذكي AI", "AI Auto-Fill", lang)) }
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
                                        "مثال: «حولت 1500 من البنك للمحفظة برسوم 10» أو «قهوة 85 كاش»",
                                        "e.g. \"Transferred 1500 Bank to Wallet fee 10\" or \"85 coffee cash\"",
                                        lang
                                    )
                                )
                            },
                            minLines = 1,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("smart_phrase_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    aiConfidenceBadge = null
                                    onAiAutoFill(smartPhraseInput)
                                },
                                enabled = autoFillState !is QuickAutoFillState.Loading && smartPhraseInput.isNotBlank(),
                                modifier = Modifier.weight(1.2f).testTag("ai_autofill_btn")
                            ) {
                                if (autoFillState is QuickAutoFillState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = AppStrings.text("جاري التحليل...", "Analyzing...", lang),
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = AppStrings.text("تعبئة بـ Gemini", "Gemini Fill", lang),
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { applySmartPhrase(smartPhraseInput) },
                                enabled = smartPhraseInput.isNotBlank(),
                                modifier = Modifier.weight(1f).testTag("apply_smart_phrase_btn")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = AppStrings.text("تحليل محلي", "Local Fill", lang),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }

                        if (aiConfidenceBadge != null) {
                            val positiveColor = semanticPositiveColor()
                            Surface(
                                color = positiveColor.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().testTag("ai_autofill_success_badge")
                            ) {
                                Text(
                                    text = AppStrings.text(
                                        "✨ تمت تعبئة الحقول تلقائيًا بواسطة Gemini (ثقة ${aiConfidenceBadge}%)",
                                        "✨ Auto-filled by Gemini AI (${aiConfidenceBadge}% confidence)",
                                        lang
                                    ),
                                    color = positiveColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (autoFillState is QuickAutoFillState.Error) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().testTag("ai_autofill_error")
                            ) {
                                Text(
                                    text = autoFillState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (state.quickTemplates.isNotEmpty()) {
                    Text(
                        text = AppStrings.text("قوالب متكررة بلمسة واحدة", "One-Tap Frequent", lang),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("quick_templates_row")
                    ) {
                        itemsIndexed(state.quickTemplates) { index, tmpl ->
                            FilterChip(
                                selected = description.equals(tmpl.description, ignoreCase = true) && selectedType == tmpl.type,
                                onClick = {
                                    selectedType = tmpl.type
                                    selectedAccountId = tmpl.accountId
                                    selectedCategoryId = tmpl.categoryId
                                    description = tmpl.description
                                    amountInput = MoneyFormatter.piastersToDecimalString(tmpl.lastAmountPiasters)
                                    errorMsg = null
                                },
                                modifier = Modifier.testTag("quick_template_$index"),
                                label = {
                                    Text(
                                        "${tmpl.description} • ${MoneyFormatter.formatPiasters(tmpl.lastAmountPiasters, language = lang, hideAmounts = state.preferences.hideAmounts)}"
                                    )
                                }
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

            // One-tap Quick Amount Chips (additive when amount is entered, direct when empty)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().testTag("quick_amounts_row")
            ) {
                val presets = listOf(50L, 100L, 200L, 500L, 1000L)
                itemsIndexed(presets) { _, value ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val currentPiasters = MoneyFormatter.parseAllowZeroToPiasters(amountInput) ?: 0L
                            val nextPiasters = if (amountInput.isBlank() || currentPiasters == 0L) {
                                value * 100L
                            } else {
                                currentPiasters + (value * 100L)
                            }
                            amountInput = MoneyFormatter.piastersToDecimalString(nextPiasters)
                            errorMsg = null
                        },
                        modifier = Modifier.testTag("quick_amount_$value"),
                        label = { Text("+$value") }
                    )
                }
            }

            // Source Account Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedType == TransactionType.TRANSFER) {
                        AppStrings.text("من حساب", "From Account", lang)
                    } else {
                        AppStrings.text("الحساب", "Account", lang)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                if (selectedType == TransactionType.TRANSFER && targetAccountId != null && targetAccountId != selectedAccountId) {
                    IconButton(
                        onClick = {
                            val prevSource = selectedAccountId
                            val prevTarget = targetAccountId
                            if (prevTarget != null) {
                                selectedAccountId = prevTarget
                                targetAccountId = prevSource
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("swap_transfer_accounts_btn")
                    ) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = AppStrings.text("تبديل الحسابات", "Swap accounts", lang),
                            tint = SkyCyan
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.filter { !it.isArchived }.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            selectedAccountId = acc.id
                            if (targetAccountId == acc.id) {
                                targetAccountId = state.accounts.firstOrNull { !it.isArchived && it.id != acc.id }?.id
                            }
                        },
                        label = { Text("${acc.name} (${acc.currency})") }
                    )
                }
            }

            // Target Account & Optional Fee for Transfer
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

                if (editingTransaction == null) {
                    OutlinedTextField(
                        value = transferFeeInput,
                        onValueChange = { transferFeeInput = it },
                        label = {
                            Text(
                                AppStrings.text(
                                    "رسوم التحويل (اختياري • $selectedAccountCurrency)",
                                    "Transfer Fee (optional • $selectedAccountCurrency)",
                                    lang
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("tx_transfer_fee_input")
                    )
                }

                val enteredAmount = MoneyFormatter.parseAllowZeroToPiasters(amountInput) ?: 0L
                if (selectedAccount != null && targetAccount != null) {
                    Surface(
                        color = SkyCyan.copy(alpha = 0.10f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${selectedAccount.name} ➔ ${targetAccount.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyCyan
                            )
                            if (enteredAmount > 0L && selectedAccount.currency != targetAccount.currency) {
                                val converted = CurrencyConverter.convertPiasters(
                                    enteredAmount,
                                    selectedAccount.currency,
                                    targetAccount.currency
                                )
                                Text(
                                    text = AppStrings.text(
                                        "يعادل تقريبًا: ${MoneyFormatter.formatPiasters(converted, targetAccount.currency, lang)}",
                                        "Approx. target value: ${MoneyFormatter.formatPiasters(converted, targetAccount.currency, lang)}",
                                        lang
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Category chips for Income / Expense
            if (selectedType == TransactionType.INCOME || selectedType == TransactionType.EXPENSE) {
                val cats = state.categories.filter { it.type == selectedType }
                val liveSuggestion = remember(description, selectedType, state.categories, state.merchantRules) {
                    if (description.trim().length >= 2) {
                        VoiceTransactionParser.inferCategory(description, state.categories, state.merchantRules, selectedType)
                    } else null
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.text("الفئة", "Category", lang), style = MaterialTheme.typography.labelLarge)
                    if (description.trim().length >= 2) {
                        TextButton(
                            onClick = {
                                onSuggestCategory(description, selectedType) { suggestedId ->
                                    selectedCategoryId = suggestedId
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("quick_add_ai_categorize_btn")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = AppStrings.text("تصنيف بـ Gemini", "AI Categorize", lang),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = liveSuggestion != null && selectedCategoryId != liveSuggestion.id) {
                    liveSuggestion?.let { suggested ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedCategoryId = suggested.id },
                            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = {
                                Text(
                                    AppStrings.text(
                                        "اقتراح تلقائي: ${suggested.nameAr}",
                                        "Suggested: ${suggested.nameEn}",
                                        lang
                                    )
                                )
                            },
                            modifier = Modifier.testTag("quick_add_local_category_suggestion")
                        )
                    }
                }
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

            val saveInteraction = remember { MutableInteractionSource() }
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
                    val feePiasters = if (selectedType == TransactionType.TRANSFER && editingTransaction == null) {
                        MoneyFormatter.parseAllowZeroToPiasters(transferFeeInput) ?: 0L
                    } else 0L
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
                        learnMerchantRule,
                        feePiasters
                    )
                },
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .springPressEffect(saveInteraction, pressedScale = 0.97f)
                    .testTag("save_transaction_button")
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
