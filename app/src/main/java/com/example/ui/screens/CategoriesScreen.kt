package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import com.example.ui.components.AppStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinancialCard
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    state: MainUiState,
    onCreateCategory: (String, String, TransactionType, String) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onSaveMerchantRule: (String, Long?, Long?, TransactionType) -> Unit = { _, _, _, _ -> },
    onDeleteMerchantRule: (MerchantRuleEntity) -> Unit = {},
    onBack: () -> Unit
) {
    val lang = state.preferences.language
    var selectedTab by remember { mutableIntStateOf(0) }
    var filterType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    val filtered = state.categories.filter { it.type == filterType }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.text("الفئات وقواعد التصنيف", "Categories & Rules", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (selectedTab == 0) showAddCategoryDialog = true else showAddRuleDialog = true
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag(if (selectedTab == 0) "add_category_btn" else "add_merchant_rule_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            if (selectedTab == 0) {
                                AppStrings.text("فئة", "Category", lang)
                            } else {
                                AppStrings.text("قاعدة", "Rule", lang)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                listOf(
                    AppStrings.text("الفئات", "Categories", lang),
                    AppStrings.text("قواعد التجار (${state.merchantRules.size})", "Merchant Rules (${state.merchantRules.size})", lang)
                ).forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 2),
                        modifier = Modifier.testTag("categories_tab_$idx")
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == TransactionType.EXPENSE,
                        onClick = { filterType = TransactionType.EXPENSE },
                        label = { Text(AppStrings.text("فئات المصروفات", "Expense Categories", lang)) }
                    )
                    FilterChip(
                        selected = filterType == TransactionType.INCOME,
                        onClick = { filterType = TransactionType.INCOME },
                        label = { Text(AppStrings.text("فئات الدخل", "Income Categories", lang)) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { cat ->
                        val accent = parseHexColor(cat.colorHex)
                        FinancialCard(borderColor = accent.copy(alpha = 0.32f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Category, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (cat.isDefault) AppStrings.text("افتراضية", "Default", lang) else AppStrings.text("مخصصة", "Custom", lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!cat.isDefault) {
                                    IconButton(onClick = { onDeleteCategory(cat) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.merchantRules.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = AppStrings.text("لا توجد قواعد تجار مخصصة بعد", "No smart merchant rules yet", lang),
                                subtitle = AppStrings.text(
                                    "تُنشأ القواعد تلقائيًا عند اعتماد معاملات القارئ الذكي أو يدويًا هنا لتصنيف التجار فورًا.",
                                    "Rules are learned automatically when approving Smart Reader items or added manually here.",
                                    lang
                                ),
                                icon = Icons.Rounded.AutoFixHigh
                            )
                        }
                    } else {
                        items(state.merchantRules, key = { it.id }) { rule ->
                            val cat = rule.categoryId?.let { categoryMap[it] }
                            val acc = rule.accountId?.let { accountMap[it] }
                            FinancialCard(
                                elevated = true,
                                borderColor = SkyCyan.copy(alpha = 0.35f),
                                modifier = Modifier.testTag("merchant_rule_card_${rule.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(SkyCyan.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = SkyCyan, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = rule.keyword,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val subtitle = buildString {
                                                append(AppStrings.transactionTypeLabel(rule.preferredType, lang))
                                                if (cat != null) {
                                                    append(" • ")
                                                    append(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn)
                                                }
                                                if (acc != null) {
                                                    append(" • ")
                                                    append(acc.name)
                                                }
                                                append(" • ")
                                                append(AppStrings.text("تكرار: ${rule.hitCount}", "Hits: ${rule.hitCount}", lang))
                                            }
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteMerchantRule(rule) },
                                        modifier = Modifier.testTag("delete_rule_${rule.id}")
                                    ) {
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

    if (showAddCategoryDialog) {
        var nameAr by remember { mutableStateOf("") }
        var nameEn by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(filterType) }
        var colorHex by remember { mutableStateOf("#22D3EE") }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(AppStrings.text("إضافة فئة جديدة", "New Category", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, label = { Text(AppStrings.text("مصروف", "Expense", lang)) })
                        FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, label = { Text(AppStrings.text("دخل", "Income", lang)) })
                    }
                    OutlinedTextField(
                        value = nameAr,
                        onValueChange = { nameAr = it },
                        label = { Text(AppStrings.text("الاسم بالعربية", "Arabic Name", lang)) },
                        singleLine = true,
                        modifier = Modifier.testTag("new_category_name_ar")
                    )
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text(AppStrings.text("الاسم بالإنجليزية (اختياري)", "English Name", lang)) },
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("#22D3EE", "#4F8CFF", "#8B5CF6", "#F59E0B", "#EF4444", "#F472B6").forEach { hex ->
                            FilterChip(
                                selected = colorHex == hex,
                                onClick = { colorHex = hex },
                                label = { Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(hex))) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameAr.isNotBlank()) {
                        onCreateCategory(nameAr, nameEn, type, colorHex)
                        showAddCategoryDialog = false
                    }
                }) { Text(AppStrings.text("حفظ", "Save", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }

    if (showAddRuleDialog) {
        var keyword by remember { mutableStateOf("") }
        var preferredType by remember { mutableStateOf(TransactionType.EXPENSE) }
        val typedCats = state.categories.filter { it.type == preferredType }
        var selectedCatId by remember(preferredType) { mutableStateOf(typedCats.firstOrNull()?.id) }
        var selectedAccId by remember { mutableStateOf(state.accounts.firstOrNull { !it.isArchived }?.id) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(AppStrings.text("قاعدة تصنيف ذكية للتاجر", "Smart Merchant Rule", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(AppStrings.text("اسم التاجر أو الكلمة المفتاحية (مثل: Uber)", "Merchant Keyword", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("merchant_rule_keyword_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = preferredType == TransactionType.EXPENSE,
                            onClick = { preferredType = TransactionType.EXPENSE },
                            label = { Text(AppStrings.text("مصروف", "Expense", lang)) }
                        )
                        FilterChip(
                            selected = preferredType == TransactionType.INCOME,
                            onClick = { preferredType = TransactionType.INCOME },
                            label = { Text(AppStrings.text("دخل", "Income", lang)) }
                        )
                    }
                    Text(AppStrings.text("الفئة التلقائية:", "Auto Category:", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        typedCats.take(6).forEach { cat ->
                            FilterChip(
                                selected = selectedCatId == cat.id,
                                onClick = { selectedCatId = cat.id },
                                label = { Text(if (lang == AppLanguage.AR) cat.nameAr else cat.nameEn) }
                            )
                        }
                    }
                    Text(AppStrings.text("الحساب الافتراضي:", "Default Account:", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.accounts.filter { !it.isArchived }.forEach { acc ->
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
                Button(
                    onClick = {
                        if (keyword.isNotBlank()) {
                            onSaveMerchantRule(keyword.trim(), selectedCatId, selectedAccId, preferredType)
                            showAddRuleDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_merchant_rule_btn")
                ) {
                    Text(AppStrings.text("حفظ القاعدة", "Save Rule", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text(AppStrings.text("إلغاء", "Cancel", lang)) }
            }
        )
    }
}
