package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAutoFillResult
import com.example.data.ai.FinancialAiGateway
import com.example.data.ai.FinancialContextFormatter
import com.example.data.ai.FirebaseGeminiFinancialGateway
import com.example.data.backup.BackupAndExportManager
import com.example.data.mock.MockSmsRepository
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CloudAutoSyncWorker
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.AnalyticsEngine
import com.example.domain.engine.BnplAnalysisEngine
import com.example.domain.engine.BnplPortfolioReport
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.FinancialHealthEngine
import com.example.domain.engine.FinancialHealthReport
import com.example.domain.engine.MoneyFormatter
import com.example.domain.engine.QuickMerchantTemplate
import com.example.domain.engine.VoiceTransactionParser
import com.example.domain.model.AccountType
import com.example.domain.model.AnalyticsSummary
import com.example.domain.model.AppLanguage
import com.example.domain.model.BalanceDiscrepancy
import com.example.domain.model.BudgetEvaluation
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.DebtDirection
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.PayoutCycle
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.SavingsAssetKind
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThemePreset
import com.example.domain.model.TimeFilter
import com.example.domain.model.TimeRange
import com.example.domain.model.TransactionSort
import com.example.domain.model.TransactionType
import com.example.domain.reader.BankStatementImporter
import com.example.domain.reader.StatementColumnMapping
import com.example.domain.reader.StatementPreview
import com.example.notifications.FinancialCheckWorker
import com.example.notifications.FinancialSummaryBuilder
import com.example.notifications.NotificationHelper
import com.example.reader.IncomingRawMessage
import com.example.reader.SmartReaderProcessor
import com.example.widget.ElbagorryWidgetUpdater
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val VM_AI_TAG = "MainViewModelAI"
private const val VM_AI_TIMEOUT_MS = 60_000L

sealed interface QuickAutoFillState {
    data object Idle : QuickAutoFillState
    data object Loading : QuickAutoFillState
    data class Ready(val result: AiAutoFillResult) : QuickAutoFillState
    data class Error(val message: String) : QuickAutoFillState
}

sealed interface SmartReaderAiState {
    data object Idle : SmartReaderAiState
    data object Loading : SmartReaderAiState
    data class Done(val summary: String) : SmartReaderAiState
    data class Error(val message: String) : SmartReaderAiState
}

sealed interface AutoCategorizeState {
    data object Idle : AutoCategorizeState
    data object Loading : AutoCategorizeState
    data class Done(val updatedCount: Int) : AutoCategorizeState
    data class Error(val message: String) : AutoCategorizeState
}

data class MainUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val timeFilter: TimeFilter = TimeFilter.CURRENT_MONTH,
    val customRange: TimeRange? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetEvaluations: List<BudgetEvaluation> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList(),
    val installments: List<InstallmentEntity> = emptyList(),
    val installmentPayments: List<InstallmentPaymentEntity> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val financialSources: List<FinancialSourceEntity> = emptyList(),
    val importedMessages: List<ImportedMessageEntity> = emptyList(),
    val pendingTransactions: List<PendingTransactionEntity> = emptyList(),
    val importHistory: List<ImportHistoryEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList(),
    val bnplPrograms: List<BnplProgramEntity> = emptyList(),
    val savingsCertificates: List<SavingsCertificateEntity> = emptyList(),
    val balanceDiscrepancies: List<BalanceDiscrepancy> = emptyList(),
    val dashboardMetrics: DashboardMetrics = FinancialEngine.calculateDashboardMetrics(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH)
    ),
    val analyticsSummary: AnalyticsSummary = AnalyticsEngine.analyze(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    ),
    val searchQuery: String = "",
    val selectedTypeFilter: TransactionType? = null,
    val selectedAccountFilterId: Long? = null,
    val selectedSort: TransactionSort = TransactionSort.DATE_DESC,
    val todayExpensePiasters: Long = 0L,
    val healthReport: FinancialHealthReport = FinancialHealthEngine.analyzeHealth(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        FinancialEngine.calculateDashboardMetrics(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH)
        ),
        AnalyticsEngine.analyze(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    ),
    val bnplReport: BnplPortfolioReport = BnplAnalysisEngine.analyze(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        FinancialEngine.calculateDashboardMetrics(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH)
        )
    ),
    val quickTemplates: List<QuickMerchantTemplate> = emptyList()
)

private data class FilterState(
    val timeFilter: TimeFilter = TimeFilter.CURRENT_MONTH,
    val customRange: TimeRange? = null,
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val accountFilterId: Long? = null,
    val sort: TransactionSort = TransactionSort.DATE_DESC
)

private data class CoreBundle(
    val prefs: UserPreferences,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>
)

private data class SecondaryBundle(
    val goals: List<SavingsGoalEntity>,
    val installments: List<InstallmentEntity>,
    val installmentPayments: List<InstallmentPaymentEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val debts: List<DebtEntity>,
    val notifications: List<NotificationEntity>,
    val savingsCertificates: List<SavingsCertificateEntity> = emptyList()
)

private data class ReaderBundle(
    val sources: List<FinancialSourceEntity>,
    val importedMessages: List<ImportedMessageEntity>,
    val pendingTransactions: List<PendingTransactionEntity>,
    val importHistory: List<ImportHistoryEntity>,
    val merchantRules: List<MerchantRuleEntity>,
    val bnplPrograms: List<BnplProgramEntity> = emptyList(),
    val balanceDiscrepancies: List<BalanceDiscrepancy> = emptyList()
)

class MainViewModel(
    private val repository: MoneyRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val backupManager: BackupAndExportManager,
    private val smartReaderProcessor: SmartReaderProcessor,
    private val appContext: Context? = null,
    private val aiGateway: FinancialAiGateway = FirebaseGeminiFinancialGateway()
) : ViewModel() {

    private val filterState = MutableStateFlow(FilterState())
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _quickAutoFillState = MutableStateFlow<QuickAutoFillState>(QuickAutoFillState.Idle)
    val quickAutoFillState: StateFlow<QuickAutoFillState> = _quickAutoFillState.asStateFlow()

    private val _smartReaderAiState = MutableStateFlow<SmartReaderAiState>(SmartReaderAiState.Idle)
    val smartReaderAiState: StateFlow<SmartReaderAiState> = _smartReaderAiState.asStateFlow()

    private val _autoCategorizeState = MutableStateFlow<AutoCategorizeState>(AutoCategorizeState.Idle)
    val autoCategorizeState: StateFlow<AutoCategorizeState> = _autoCategorizeState.asStateFlow()

    private val _balanceDiscrepancies = MutableStateFlow<List<BalanceDiscrepancy>>(emptyList())

    private val coreFlow = combine(
        prefsRepo.preferences,
        repository.accounts,
        repository.categories,
        repository.transactions,
        repository.budgets
    ) { prefs, accounts, categories, transactions, budgets ->
        CoreBundle(prefs, accounts, categories, transactions, budgets)
    }

    private val secondaryFlow = combine(
        repository.savingsGoals,
        repository.installments,
        repository.installmentPayments,
        repository.recurringRules,
        repository.debts
    ) { goals, installments, payments, rules, debts ->
        SecondaryBundle(goals, installments, payments, rules, debts, emptyList())
    }.combine(repository.notifications) { bundle, notifications ->
        bundle.copy(notifications = notifications)
    }.combine(repository.savingsCertificates) { bundle, certs ->
        bundle.copy(savingsCertificates = certs)
    }

    private val readerFlow = combine(
        repository.financialSources,
        repository.importedMessages,
        repository.pendingTransactions,
        repository.importHistory,
        repository.merchantRules
    ) { sources, messages, pending, history, rules ->
        ReaderBundle(sources, messages, pending, history, rules)
    }.combine(repository.bnplPrograms) { bundle, programs ->
        bundle.copy(bnplPrograms = programs)
    }.combine(_balanceDiscrepancies) { bundle, discrepancies ->
        bundle.copy(balanceDiscrepancies = discrepancies)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreFlow,
        secondaryFlow,
        readerFlow,
        filterState
    ) { core, sec, reader, filter ->
        CurrencyConverter.applyCustomRatesFromRaw(core.prefs.customCurrencyRatesRaw)
        val now = System.currentTimeMillis()
        val targetCurrency = core.prefs.currencyCode
        val resolvedRange = DateRangeResolver.resolve(filter.timeFilter, now, filter.customRange)
        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts = core.accounts,
            transactions = core.transactions,
            categories = core.categories,
            budgets = core.budgets,
            savingsGoals = sec.goals,
            installments = sec.installments,
            debts = sec.debts,
            range = resolvedRange,
            nowMillis = now,
            targetCurrency = targetCurrency
        )
        val analytics = AnalyticsEngine.analyze(
            accounts = core.accounts,
            transactions = core.transactions,
            categories = core.categories,
            budgets = core.budgets,
            installments = sec.installments,
            nowMillis = now,
            targetCurrency = targetCurrency
        )
        val budgetEvals = FinancialEngine.evaluateBudgets(
            budgets = core.budgets,
            transactions = core.transactions,
            currentYearMonth = DateRangeResolver.currentYearMonth(now),
            nowMillis = now,
            accounts = core.accounts,
            targetCurrency = targetCurrency
        )

        val filteredTx = applyTransactionFilters(core.transactions, filter)
        val accountCurrencyMap = core.accounts.associate { it.id to it.currency }
        val todayRange = DateRangeResolver.resolve(TimeFilter.TODAY, now)
        val todayExpense = core.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in todayRange.startMillis..todayRange.endMillis }
            .sumOf { tx ->
                CurrencyConverter.convertPiasters(
                    tx.amountPiasters,
                    accountCurrencyMap[tx.accountId] ?: "EGP",
                    targetCurrency
                )
            }

        val currentMonthMetrics = if (filter.timeFilter == TimeFilter.CURRENT_MONTH) {
            metrics
        } else {
            FinancialEngine.calculateDashboardMetrics(
                accounts = core.accounts,
                transactions = core.transactions,
                categories = core.categories,
                budgets = core.budgets,
                savingsGoals = sec.goals,
                installments = sec.installments,
                debts = sec.debts,
                range = DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, now),
                nowMillis = now,
                targetCurrency = targetCurrency
            )
        }
        val health = FinancialHealthEngine.analyzeHealth(
            accounts = core.accounts,
            transactions = core.transactions,
            budgets = core.budgets,
            budgetEvaluations = budgetEvals,
            savingsGoals = sec.goals,
            installments = sec.installments,
            recurringRules = sec.recurringRules,
            debts = sec.debts,
            monthMetrics = currentMonthMetrics,
            analyticsSummary = analytics,
            nowMillis = now,
            targetCurrency = targetCurrency
        )
        val bnplReport = BnplAnalysisEngine.analyze(
            programs = reader.bnplPrograms,
            installments = sec.installments,
            recurringRules = sec.recurringRules,
            transactions = core.transactions,
            accounts = core.accounts,
            monthMetrics = currentMonthMetrics,
            nowMillis = now,
            targetCurrency = targetCurrency
        )
        val templates = FinancialHealthEngine.extractQuickTemplates(core.transactions)

        MainUiState(
            isLoading = false,
            preferences = core.prefs,
            timeFilter = filter.timeFilter,
            customRange = filter.customRange,
            accounts = core.accounts,
            categories = core.categories,
            transactions = core.transactions,
            filteredTransactions = filteredTx,
            budgets = core.budgets,
            budgetEvaluations = budgetEvals,
            savingsGoals = sec.goals,
            installments = sec.installments,
            installmentPayments = sec.installmentPayments,
            recurringRules = sec.recurringRules,
            debts = sec.debts,
            notifications = core.let { sec.notifications },
            financialSources = reader.sources,
            importedMessages = reader.importedMessages,
            pendingTransactions = reader.pendingTransactions,
            importHistory = reader.importHistory,
            merchantRules = reader.merchantRules,
            bnplPrograms = reader.bnplPrograms,
            savingsCertificates = sec.savingsCertificates,
            balanceDiscrepancies = reader.balanceDiscrepancies,
            dashboardMetrics = metrics,
            analyticsSummary = analytics,
            searchQuery = filter.searchQuery,
            selectedTypeFilter = filter.typeFilter,
            selectedAccountFilterId = filter.accountFilterId,
            selectedSort = filter.sort,
            todayExpensePiasters = todayExpense,
            healthReport = health,
            bnplReport = bnplReport,
            quickTemplates = templates
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState()
        )

    private fun applyTransactionFilters(
        all: List<TransactionEntity>,
        filter: FilterState
    ): List<TransactionEntity> {
        val q = filter.searchQuery.trim().lowercase()
        val filtered = all.filter { tx ->
            val matchQuery = q.isEmpty() ||
                tx.description.lowercase().contains(q) ||
                tx.notes.lowercase().contains(q) ||
                (tx.referenceNumber?.lowercase()?.contains(q) == true)
            val matchType = filter.typeFilter == null || tx.type == filter.typeFilter
            val matchAccount = filter.accountFilterId == null ||
                tx.accountId == filter.accountFilterId ||
                tx.targetAccountId == filter.accountFilterId
            matchQuery && matchType && matchAccount
        }
        return when (filter.sort) {
            TransactionSort.DATE_DESC -> filtered.sortedByDescending { it.timestampMillis }
            TransactionSort.DATE_ASC -> filtered.sortedBy { it.timestampMillis }
            TransactionSort.AMOUNT_DESC -> filtered.sortedByDescending { it.amountPiasters }
            TransactionSort.AMOUNT_ASC -> filtered.sortedBy { it.amountPiasters }
        }
    }

    // --- Filters ---

    fun setTimeFilter(filter: TimeFilter, customRange: TimeRange? = null) {
        filterState.value = filterState.value.copy(
            timeFilter = filter,
            customRange = customRange ?: filterState.value.customRange
        )
    }

    fun setSearchQuery(query: String) {
        filterState.value = filterState.value.copy(searchQuery = query)
    }

    fun setTypeFilter(type: TransactionType?) {
        filterState.value = filterState.value.copy(typeFilter = type)
    }

    fun setAccountFilter(accountId: Long?) {
        filterState.value = filterState.value.copy(accountFilterId = accountId)
    }

    fun setSort(sort: TransactionSort) {
        filterState.value = filterState.value.copy(sort = sort)
    }

    // --- Preferences ---

    fun togglePrivacyAmounts() = viewModelScope.launch {
        prefsRepo.toggleHideAmounts()
        refreshWidgets()
    }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setThemePreset(preset: ThemePreset) = viewModelScope.launch {
        prefsRepo.setThemePreset(preset)
        refreshWidgets()
    }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        prefsRepo.setLanguage(language)
        refreshWidgets()
    }
    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { prefsRepo.setBiometricLockEnabled(enabled) }
    fun setPrivacyScreenGuard(enabled: Boolean) = viewModelScope.launch { prefsRepo.setPrivacyScreenGuardEnabled(enabled) }
    fun setPrimaryCurrency(currencyCode: String) = viewModelScope.launch {
        prefsRepo.setCurrencyCode(currencyCode)
        refreshWidgets()
    }
    fun setCustomCurrencyRates(rawRates: String) = viewModelScope.launch {
        CurrencyConverter.applyCustomRatesFromRaw(rawRates)
        prefsRepo.setCustomCurrencyRates(rawRates)
        refreshWidgets()
    }
    fun resetCustomCurrencyRates() = viewModelScope.launch {
        CurrencyConverter.resetCustomRates()
        prefsRepo.setCustomCurrencyRates("")
        refreshWidgets()
    }
    fun setNotificationToggle(key: String, enabled: Boolean) = viewModelScope.launch { prefsRepo.setNotificationToggle(key, enabled) }
    fun setSummaryFrequency(frequency: SummaryFrequency) = viewModelScope.launch { prefsRepo.setSummaryFrequency(frequency) }
    fun setSmartReaderEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.setSmartReaderEnabled(enabled) }
    fun setSmartReaderAutoApprove(enabled: Boolean) = viewModelScope.launch { prefsRepo.setSmartReaderAutoApprove(enabled) }
    fun setReaderIgnoreOtp(enabled: Boolean) = viewModelScope.launch { prefsRepo.setReaderIgnoreOtp(enabled) }
    fun setReaderIgnoreBalance(enabled: Boolean) = viewModelScope.launch { prefsRepo.setReaderIgnoreBalance(enabled) }
    fun setAutoCloudSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAutoCloudSyncEnabled(enabled)
        appContext?.let { ctx ->
            if (enabled && !uiState.value.preferences.isGuestMode) {
                CloudAutoSyncWorker.schedulePeriodic(ctx)
                CloudAutoSyncWorker.enqueueImmediateDebounced(ctx)
            } else {
                CloudAutoSyncWorker.cancelAll(ctx)
            }
        }
    }

    fun sendSummaryNotificationNow(context: Context) = viewModelScope.launch {
        val st = uiState.value
        val prefs = st.preferences
        val lang = prefs.language
        val now = System.currentTimeMillis()
        val summary = FinancialSummaryBuilder.build(
            accounts = st.accounts,
            transactions = st.transactions,
            budgets = st.budgets,
            installments = st.installments,
            pending = st.pendingTransactions,
            nowMillis = now
        )
        val isWeekly = prefs.summaryFrequency == SummaryFrequency.WEEKLY
        val title = when {
            isWeekly && lang == AppLanguage.AR -> "ملخصك المالي الأسبوعي"
            isWeekly -> "Weekly Financial Summary"
            lang == AppLanguage.AR -> "ملخصك المالي اليومي"
            else -> "Daily Financial Summary"
        }
        val text = FinancialSummaryBuilder.formatMessageText(
            snapshot = summary,
            isWeekly = isWeekly,
            currency = prefs.currencyCode,
            hideAmounts = prefs.hideAmounts,
            lang = lang
        )
        repository.addNotification(
            NotificationEntity(
                type = com.example.domain.model.NotificationType.FINANCIAL_SUMMARY,
                title = title,
                message = text,
                timestampMillis = now
            )
        )
        NotificationHelper.showPeriodicSummaryNotification(
            context = context.applicationContext,
            notificationId = FinancialCheckWorker.SUMMARY_NOTIFICATION_ID,
            summary = summary,
            hideAmounts = prefs.hideAmounts,
            currency = prefs.currencyCode,
            isWeekly = isWeekly,
            lang = lang
        )
        prefsRepo.setLastSummaryTimestamp(now)
        emitMsg(if (lang == AppLanguage.AR) "تم إرسال الملخص المالي إلى الإشعارات" else "Financial summary sent")
    }

    // --- Categories ---

    fun createCategory(
        nameAr: String,
        nameEn: String,
        type: TransactionType,
        colorHex: String
    ) = viewModelScope.launch {
        repository.createCategory(
            CategoryEntity(
                nameAr = nameAr.trim(),
                nameEn = nameEn.trim().ifBlank { nameAr.trim() },
                type = type,
                colorHex = colorHex,
                isDefault = false
            )
        )
        emitMsg("تمت إضافة الفئة")
    }

    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        repository.deleteCategory(category)
        emitMsg("تم حذف الفئة")
    }

    // --- Smart Merchant Rules ---

    fun saveMerchantRule(
        keyword: String,
        categoryId: Long?,
        accountId: Long?,
        preferredType: TransactionType
    ) = viewModelScope.launch {
        if (keyword.isBlank()) return@launch
        repository.learnOrUpdateMerchantRule(
            rawKeyword = keyword,
            categoryId = categoryId,
            accountId = accountId,
            preferredType = preferredType
        )
        emitMsg("تم حفظ قاعدة التصنيف التلقائي للتاجر")
    }

    fun deleteMerchantRule(rule: MerchantRuleEntity) = viewModelScope.launch {
        repository.deleteMerchantRule(rule)
        emitMsg("تم حذف قاعدة التاجر")
    }

    // --- Smart Money Reader Actions ---

    fun ingestSharedOrPastedText(
        sender: String,
        rawText: String,
        channel: MessageChannel
    ) = viewModelScope.launch {
        val lines = rawText.split(Regex("""\n{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
        val messages = lines.map { IncomingRawMessage(sender = sender, body = it) }
        val result = withContext(Dispatchers.IO) {
            smartReaderProcessor.ingestMessages(messages, channel)
        }
        recordDiscrepancyIfAny(result.latestBalanceDiscrepancy)
        val msg = when {
            result.pendingCreated > 0 -> "تم اكتشاف ${result.pendingCreated} عملية مالية في قائمة المراجعة"
            result.duplicatesSkipped > 0 -> "الرسالة مستوردة مسبقًا (تم منع التكرار)"
            else -> "تمت تصفية الرسالة (غير مالية أو رمز تحقق OTP)"
        }
        emitMsg(msg)
    }

    fun ingestTextWithAi(
        sender: String,
        rawText: String,
        channel: MessageChannel = MessageChannel.CLIPBOARD_PASTE
    ) = viewModelScope.launch {
        val trimmed = rawText.trim()
        val lang = uiState.value.preferences.language
        if (trimmed.isBlank()) {
            _smartReaderAiState.value = SmartReaderAiState.Error(
                if (lang == AppLanguage.EN) "Paste a message first." else "الصق نص الرسالة أولًا."
            )
            return@launch
        }
        _smartReaderAiState.value = SmartReaderAiState.Loading
        try {
            val result = withTimeout(VM_AI_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    smartReaderProcessor.ingestMessagesWithAi(
                        sender = sender,
                        rawText = trimmed,
                        channel = channel,
                        aiGateway = aiGateway,
                        language = lang
                    )
                }
            }
            recordDiscrepancyIfAny(result.latestBalanceDiscrepancy)
            val msg = when {
                result.pendingCreated > 0 ->
                    if (lang == AppLanguage.EN) {
                        "Gemini AI extracted ${result.pendingCreated} pending transaction(s) for review"
                    } else {
                        "استخرج Gemini ${result.pendingCreated} عملية مالية في قائمة المراجعة"
                    }
                result.duplicatesSkipped > 0 ->
                    if (lang == AppLanguage.EN) "Message already imported (duplicate blocked)" else "الرسالة مستوردة مسبقًا (تم منع التكرار)"
                else ->
                    if (lang == AppLanguage.EN) "Filtered (OTP, security alert, or non-financial)" else "تمت التصفية (رمز تحقق OTP أو رسالة غير مالية)"
            }
            _smartReaderAiState.value = SmartReaderAiState.Done(msg)
            emitMsg(msg)
        } catch (e: TimeoutCancellationException) {
            Log.e(VM_AI_TAG, "AI message reading timed out", e)
            val msg = if (lang == AppLanguage.EN) {
                "AI analysis timed out after 60 seconds."
            } else {
                "انتهت مهلة تحليل الرسالة بالذكاء الاصطناعي بعد 60 ثانية."
            }
            _smartReaderAiState.value = SmartReaderAiState.Error(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(VM_AI_TAG, "AI message reading failed", e)
            _smartReaderAiState.value = SmartReaderAiState.Error(
                e.localizedMessage ?: if (lang == AppLanguage.EN) {
                    "Gemini failed to analyze messages."
                } else {
                    "تعذر تحليل الرسائل عبر Gemini."
                }
            )
        }
    }

    fun resetSmartReaderAiState() {
        _smartReaderAiState.value = SmartReaderAiState.Idle
    }

    // --- AI Smart Auto-Fill ---

    fun runAiAutoFill(rawPhrase: String) = viewModelScope.launch {
        val phrase = rawPhrase.trim()
        val state = uiState.value
        val lang = state.preferences.language
        if (phrase.isBlank()) {
            _quickAutoFillState.value = QuickAutoFillState.Error(
                if (lang == AppLanguage.EN) "Enter a phrase first." else "اكتب جملة لوصف المعاملة أولًا."
            )
            return@launch
        }
        val activeAccounts = state.accounts.filter { !it.isArchived }
        val validAccountIds = activeAccounts.map { it.id }.toSet()
        val validCategoryIds = state.categories.map { it.id }.toSet()
        _quickAutoFillState.value = QuickAutoFillState.Loading
        try {
            val result = withTimeout(VM_AI_TIMEOUT_MS) {
                aiGateway.smartAutoFillTransaction(
                    phrase = phrase,
                    accountsContext = FinancialContextFormatter.buildAccountsContext(activeAccounts),
                    categoriesContext = FinancialContextFormatter.buildCategoriesContext(state.categories),
                    validAccountIds = validAccountIds,
                    validCategoryIds = validCategoryIds,
                    primaryCurrency = state.preferences.currencyCode,
                    language = lang
                )
            }
            _quickAutoFillState.value = QuickAutoFillState.Ready(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(VM_AI_TAG, "AI auto-fill timed out", e)
            _quickAutoFillState.value = QuickAutoFillState.Error(
                if (lang == AppLanguage.EN) "AI Auto-Fill timed out after 60s." else "انتهت مهلة الملء التلقائي بعد 60 ثانية."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(VM_AI_TAG, "AI auto-fill failed", e)
            _quickAutoFillState.value = QuickAutoFillState.Error(
                e.localizedMessage ?: if (lang == AppLanguage.EN) {
                    "AI Auto-Fill failed. Check connection and try again."
                } else {
                    "تعذر إتمام الملء التلقائي بالذكاء الاصطناعي."
                }
            )
        }
    }

    fun resetAiAutoFillState() {
        _quickAutoFillState.value = QuickAutoFillState.Idle
    }

    fun resetAutoCategorizeState() {
        _autoCategorizeState.value = AutoCategorizeState.Idle
    }

    fun autoCategorizeUncategorizedWithAi() = viewModelScope.launch {
        val state = uiState.value
        val lang = state.preferences.language
        val targets = state.transactions
            .filter { (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) && it.categoryId == null }
            .take(25)

        if (targets.isEmpty()) {
            _autoCategorizeState.value = AutoCategorizeState.Done(0)
            emitMsg(
                if (lang == AppLanguage.EN) "All expenses & income are already categorized."
                else "جميع المصروفات والدخل مصنفة بالفعل."
            )
            return@launch
        }

        val validTxIds = targets.map { it.id }.toSet()
        val validCatIds = state.categories.map { it.id }.toSet()
        val txSummary = targets.joinToString("\n") { tx ->
            "- transactionId=${tx.id} | type=${tx.type.name} | amount=${MoneyFormatter.piastersToDecimalString(tx.amountPiasters)} | description=\"${tx.description}\" | notes=\"${tx.notes}\""
        }
        val categoriesContext = FinancialContextFormatter.buildCategoriesContext(state.categories)

        _autoCategorizeState.value = AutoCategorizeState.Loading
        try {
            val aiAssignments = withTimeout(VM_AI_TIMEOUT_MS) {
                aiGateway.autoCategorizeTransactions(
                    transactionsSummary = txSummary,
                    categoriesContext = categoriesContext,
                    validTransactionIds = validTxIds,
                    validCategoryIds = validCatIds,
                    language = lang
                )
            }
            val merged = mutableMapOf<Long, Long>()
            aiAssignments.forEach { a -> merged[a.transactionId] = a.categoryId }

            // Local rule/keyword fallback for any transactions AI didn't assign
            targets.forEach { tx ->
                if (!merged.containsKey(tx.id)) {
                    VoiceTransactionParser.inferCategory(
                        description = "${tx.description} ${tx.notes}",
                        categories = state.categories,
                        merchantRules = state.merchantRules,
                        type = tx.type
                    )?.let { cat -> merged[tx.id] = cat.id }
                }
            }

            val updatedCount = withContext(Dispatchers.IO) {
                repository.batchAssignCategories(merged)
            }
            if (updatedCount > 0) {
                refreshWidgets()
            }
            _autoCategorizeState.value = AutoCategorizeState.Done(updatedCount)
            emitMsg(
                if (lang == AppLanguage.EN) {
                    "Gemini organized $updatedCount transaction(s) into categories"
                } else {
                    "قام Gemini بتصنيف وتنظيم $updatedCount معاملة تلقائيًا"
                }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(VM_AI_TAG, "AI auto-categorize timed out", e)
            val msg = if (lang == AppLanguage.EN) {
                "Auto-categorization timed out after 60s."
            } else {
                "انتهت مهلة التصنيف التلقائي بعد 60 ثانية."
            }
            _autoCategorizeState.value = AutoCategorizeState.Error(msg)
            emitMsg(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(VM_AI_TAG, "AI auto-categorize failed", e)
            val msg = e.localizedMessage ?: if (lang == AppLanguage.EN) {
                "Auto-categorization failed."
            } else {
                "تعذر التصنيف التلقائي عبر Gemini."
            }
            _autoCategorizeState.value = AutoCategorizeState.Error(msg)
            emitMsg(msg)
        }
    }

    fun suggestCategoryWithAi(
        description: String,
        type: TransactionType,
        onSuggested: (Long) -> Unit
    ) = viewModelScope.launch {
        val trimmed = description.trim()
        val state = uiState.value
        val lang = state.preferences.language
        if (trimmed.isBlank() || (type != TransactionType.EXPENSE && type != TransactionType.INCOME)) {
            return@launch
        }
        val categoriesContext = FinancialContextFormatter.buildCategoriesContext(state.categories)
        val validCatIds = state.categories.filter { it.type == type }.map { it.id }.toSet()
        try {
            val aiResult = withTimeout(VM_AI_TIMEOUT_MS) {
                aiGateway.autoCategorizeTransactions(
                    transactionsSummary = "- transactionId=1 | type=${type.name} | description=\"$trimmed\"",
                    categoriesContext = categoriesContext,
                    validTransactionIds = setOf(1L),
                    validCategoryIds = validCatIds,
                    language = lang
                )
            }
            val matchedCatId = aiResult.firstOrNull { it.transactionId == 1L }?.categoryId
                ?: VoiceTransactionParser.inferCategory(trimmed, state.categories, state.merchantRules, type)?.id
            if (matchedCatId != null) {
                onSuggested(matchedCatId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(VM_AI_TAG, "Single AI category suggestion fell back to local parser", e)
            VoiceTransactionParser.inferCategory(trimmed, state.categories, state.merchantRules, type)
                ?.let { onSuggested(it.id) }
        }
    }

    fun importMessagesFromFile(context: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            val content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("تعذر قراءة الملف")
            }
            val blocks = content
                .split(Regex("""\n{2,}|\r\n\r\n"""))
                .flatMap { block ->
                    if (block.lines().size > 4) block.lines() else listOf(block)
                }
                .map { it.trim() }
                .filter { it.length >= 8 }

            val messages = blocks.map { IncomingRawMessage(sender = "File", body = it) }
            val result = withContext(Dispatchers.IO) {
                smartReaderProcessor.ingestMessages(messages, MessageChannel.FILE_IMPORT)
            }
            recordDiscrepancyIfAny(result.latestBalanceDiscrepancy)
            emitMsg(
                "فحص ${result.totalScanned} سطر: ${result.pendingCreated} معلقة، ${result.duplicatesSkipped} مكررة، ${result.ignoredFiltered} مستبعدة"
            )
        }.onFailure {
            emitMsg("تعذر استيراد الملف المحدد")
        }
    }

    suspend fun readAndInspectBankStatement(context: Context, uri: Uri): Pair<String, StatementPreview>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@runCatching null
                val preview = BankStatementImporter.inspectStatement(raw) ?: return@runCatching null
                raw to preview
            }.getOrNull()
        }
    }

    fun importBankStatement(
        rawContent: String,
        bankName: String,
        mapping: StatementColumnMapping?
    ) = viewModelScope.launch {
        runCatching {
            val result = withContext(Dispatchers.IO) {
                smartReaderProcessor.ingestBankStatement(
                    rawContent = rawContent,
                    bankOrSourceLabel = bankName,
                    mappingOverride = mapping
                )
            }
            recordDiscrepancyIfAny(result.latestBalanceDiscrepancy)
            emitMsg(
                "كشف الحساب (${result.totalScanned} عملية): ${result.pendingCreated} معلقة للمراجعة، ${result.duplicatesSkipped} مكررة"
            )
        }.onFailure {
            emitMsg("تعذر تحليل كشف الحساب")
        }
    }

    fun runMockSmsBatch() = viewModelScope.launch {
        val mockRepo = MockSmsRepository()
        val batch = mockRepo.getRawSampleBatch()
        val result = withContext(Dispatchers.IO) {
            smartReaderProcessor.ingestMessages(batch, MessageChannel.CLIPBOARD_PASTE)
        }
        recordDiscrepancyIfAny(result.latestBalanceDiscrepancy)
        emitMsg(
            "محاكاة ${result.totalScanned} رسالة تجريبية: ${result.pendingCreated} معلقة، ${result.duplicatesSkipped} مكررة، ${result.ignoredFiltered} مستبعدة"
        )
    }

    fun reconcileBalance(discrepancy: BalanceDiscrepancy) = viewModelScope.launch {
        val txId = withContext(Dispatchers.IO) {
            repository.reconcileAccountBalance(
                accountId = discrepancy.accountId,
                targetBankBalancePiasters = discrepancy.bankBalancePiasters,
                sourceName = discrepancy.sourceName
            )
        }
        dismissDiscrepancy(discrepancy.accountId)
        if (txId != null) {
            refreshWidgets()
            triggerBackgroundCloudSyncIfEnabled()
            emitMsg("تمت مطابقة رصيد ${discrepancy.accountName} مع ${discrepancy.sourceName}")
        }
    }

    fun dismissDiscrepancy(accountId: Long) {
        _balanceDiscrepancies.value = _balanceDiscrepancies.value.filterNot { it.accountId == accountId }
    }

    private fun recordDiscrepancyIfAny(discrepancy: BalanceDiscrepancy?) {
        if (discrepancy == null || abs(discrepancy.diffPiasters) < 100L) return
        _balanceDiscrepancies.value = _balanceDiscrepancies.value
            .filterNot { it.accountId == discrepancy.accountId } + discrepancy
    }

    fun approvePending(
        pendingId: Long,
        accountId: Long,
        targetAccountId: Long? = null,
        categoryId: Long? = null,
        overrideAmountPiasters: Long? = null,
        overrideType: TransactionType? = null,
        overrideDescription: String? = null
    ) = viewModelScope.launch {
        val pendingSnapshot = uiState.value.pendingTransactions.firstOrNull { it.id == pendingId }
        val res = repository.approvePendingTransaction(
            pendingId = pendingId,
            accountId = accountId,
            targetAccountId = targetAccountId,
            categoryId = categoryId,
            overrideAmountPiasters = overrideAmountPiasters,
            overrideType = overrideType,
            overrideDescription = overrideDescription
        )
        res.onSuccess {
            val bankBal = pendingSnapshot?.balanceAfterPiasters
            if (bankBal != null) {
                val updatedAcc = withContext(Dispatchers.IO) {
                    repository.getAllAccountsOnce().firstOrNull { it.id == accountId }
                }
                if (updatedAcc != null) {
                    val diff = bankBal - updatedAcc.currentBalancePiasters
                    if (abs(diff) >= 100L) {
                        recordDiscrepancyIfAny(
                            BalanceDiscrepancy(
                                accountId = updatedAcc.id,
                                accountName = updatedAcc.name,
                                appBalancePiasters = updatedAcc.currentBalancePiasters,
                                bankBalancePiasters = bankBal,
                                diffPiasters = diff,
                                sourceName = pendingSnapshot.sourceName,
                                detectedAtMillis = pendingSnapshot.transactionTimestampMillis
                            )
                        )
                    } else {
                        dismissDiscrepancy(accountId)
                    }
                }
            }
            refreshWidgets()
            triggerBackgroundCloudSyncIfEnabled()
            emitMsg("تم قبول المعاملة وإضافتها وتحديث الرصيد")
        }.onFailure { err ->
            emitMsg(err.message ?: "تعذر اعتماد المعاملة")
        }
    }

    fun rejectPending(pendingId: Long) = viewModelScope.launch {
        repository.rejectPendingTransaction(pendingId)
        refreshWidgets()
        emitMsg("تم رفض العملية")
    }

    fun ignoreFinancialSource(sourceId: Long) = viewModelScope.launch {
        repository.ignoreFinancialSource(sourceId)
        emitMsg("تم تجاهل المصدر المالي")
    }

    fun toggleFinancialSource(source: FinancialSourceEntity) = viewModelScope.launch {
        repository.updateFinancialSource(
            source.copy(isEnabled = !source.isEnabled, isIgnored = false)
        )
    }

    fun addCustomFinancialSource(
        nameAr: String,
        nameEn: String,
        keywords: String,
        defaultAccountId: Long?
    ) = viewModelScope.launch {
        val code = "CUSTOM_${System.currentTimeMillis()}"
        repository.saveFinancialSource(
            FinancialSourceEntity(
                code = code,
                displayNameAr = nameAr.trim(),
                displayNameEn = nameEn.trim().ifBlank { nameAr.trim() },
                senderKeywords = keywords.trim(),
                defaultAccountId = defaultAccountId
            )
        )
        emitMsg("تمت إضافة المصدر المالي")
    }

    fun clearImportData() = viewModelScope.launch {
        repository.clearImportData()
        emitMsg("تم مسح بيانات وسجل الاستيراد")
    }

    // --- Transactions ---

    fun saveTransaction(
        existingId: Long? = null,
        amountPiasters: Long,
        type: TransactionType,
        accountId: Long,
        targetAccountId: Long? = null,
        categoryId: Long? = null,
        savingsGoalId: Long? = null,
        debtId: Long? = null,
        description: String,
        notes: String = "",
        attachmentUri: String? = null,
        timestampMillis: Long = System.currentTimeMillis(),
        learnMerchantRule: Boolean = false,
        transferFeePiasters: Long = 0L
    ) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            val cleanDesc = description.trim().ifEmpty {
                when (type) {
                    TransactionType.INCOME -> "دخل"
                    TransactionType.EXPENSE -> "مصروف"
                    TransactionType.TRANSFER -> "تحويل بين الحسابات"
                    TransactionType.SAVINGS_CONTRIBUTION -> "مساهمة ادخار"
                    TransactionType.DEBT_PAYMENT -> "سداد دين"
                }
            }
            val resolvedCategoryId = if (type == TransactionType.EXPENSE || type == TransactionType.INCOME) {
                categoryId ?: VoiceTransactionParser.inferCategory(
                    description = cleanDesc,
                    categories = uiState.value.categories,
                    merchantRules = uiState.value.merchantRules,
                    type = type
                )?.id
            } else {
                categoryId
            }
            val tx = TransactionEntity(
                id = existingId ?: 0L,
                amountPiasters = amountPiasters,
                type = type,
                accountId = accountId,
                targetAccountId = targetAccountId,
                categoryId = resolvedCategoryId,
                savingsGoalId = savingsGoalId,
                debtId = debtId,
                description = cleanDesc,
                notes = notes.trim(),
                attachmentUri = attachmentUri,
                timestampMillis = timestampMillis,
                createdAt = now,
                updatedAt = now
            )
            if (existingId != null && existingId > 0L) {
                repository.updateTransaction(tx)
                refreshWidgets()
                triggerBackgroundCloudSyncIfEnabled()
                emitMsg("تم تحديث المعاملة بنجاح")
            } else if (type == TransactionType.TRANSFER && targetAccountId != null && transferFeePiasters > 0L) {
                repository.addTransferWithFee(
                    fromAccountId = accountId,
                    toAccountId = targetAccountId,
                    amountPiasters = amountPiasters,
                    feePiasters = transferFeePiasters,
                    description = cleanDesc,
                    notes = notes.trim(),
                    attachmentUri = attachmentUri,
                    timestampMillis = timestampMillis
                )
                refreshWidgets()
                triggerBackgroundCloudSyncIfEnabled()
                emitMsg("تم تسجيل التحويل ورسوم التحويل بنجاح")
            } else {
                repository.addTransaction(tx)
                if (learnMerchantRule && description.trim().length >= 2 &&
                    (type == TransactionType.EXPENSE || type == TransactionType.INCOME) && resolvedCategoryId != null
                ) {
                    repository.learnOrUpdateMerchantRule(
                        rawKeyword = description.trim(),
                        categoryId = resolvedCategoryId,
                        accountId = accountId,
                        preferredType = type
                    )
                }
                refreshWidgets()
                triggerBackgroundCloudSyncIfEnabled()
                emitMsg("تم تسجيل المعاملة بنجاح")
            }
        }.onFailure {
            emitMsg(it.message ?: "تعذر حفظ المعاملة")
        }
    }

    fun deleteTransaction(tx: TransactionEntity) = viewModelScope.launch {
        runCatching {
            repository.deleteTransaction(tx)
            refreshWidgets()
            triggerBackgroundCloudSyncIfEnabled()
            emitMsg("تم حذف المعاملة وتحديث الرصيد")
        }.onFailure {
            emitMsg("تعذر حذف المعاملة")
        }
    }

    fun duplicateTransaction(tx: TransactionEntity) = viewModelScope.launch {
        runCatching {
            repository.duplicateTransaction(tx)
            refreshWidgets()
            emitMsg("تم تكرار المعاملة")
        }.onFailure {
            emitMsg("تعذر تكرار المعاملة")
        }
    }

    // --- Accounts ---

    fun createAccount(
        name: String,
        type: AccountType,
        openingBalancePiasters: Long,
        colorHex: String,
        currency: String = "EGP"
    ) = viewModelScope.launch {
        runCatching {
            repository.createAccount(
                AccountEntity(
                    name = name.trim(),
                    type = type,
                    openingBalancePiasters = openingBalancePiasters,
                    currentBalancePiasters = openingBalancePiasters,
                    currency = currency,
                    colorHex = colorHex
                )
            )
            refreshWidgets()
            emitMsg("تم إنشاء الحساب")
        }
    }

    fun createTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountPiasters: Long,
        notes: String,
        feePiasters: Long = 0L
    ) = saveTransaction(
        amountPiasters = amountPiasters,
        type = TransactionType.TRANSFER,
        accountId = fromAccountId,
        targetAccountId = toAccountId,
        description = "تحويل بين حسابات",
        notes = notes,
        transferFeePiasters = feePiasters
    )

    // --- Budgets ---

    fun saveBudget(
        name: String,
        categoryId: Long?,
        limitPiasters: Long,
        yearMonth: String = DateRangeResolver.currentYearMonth(),
        isCustomPeriod: Boolean = false,
        startMillis: Long? = null,
        endMillis: Long? = null
    ) = viewModelScope.launch {
        runCatching {
            repository.saveBudget(
                BudgetEntity(
                    name = name.trim(),
                    categoryId = categoryId,
                    limitPiasters = limitPiasters,
                    yearMonth = yearMonth,
                    isCustomPeriod = isCustomPeriod,
                    startDateMillis = startMillis,
                    endDateMillis = endMillis
                )
            )
            emitMsg("تم حفظ الميزانية")
        }
    }

    fun deleteBudget(budget: BudgetEntity) = viewModelScope.launch {
        repository.deleteBudget(budget)
        emitMsg("تم حذف الميزانية")
    }

    fun copyPreviousMonthBudgets() = viewModelScope.launch {
        val count = repository.copyPreviousMonthBudgets(DateRangeResolver.currentYearMonth())
        if (count > 0) emitMsg("تم نسخ $count ميزانية من الشهر السابق")
        else emitMsg("لا توجد ميزانيات جديدة للنسخ من الشهر السابق")
    }

    // --- Savings Goals ---

    fun saveSavingsGoal(
        name: String,
        preset: GoalPreset,
        targetAmountPiasters: Long,
        targetDateMillis: Long?,
        colorHex: String
    ) = viewModelScope.launch {
        repository.saveGoal(
            SavingsGoalEntity(
                name = name.trim(),
                presetType = preset,
                targetAmountPiasters = targetAmountPiasters,
                targetDateMillis = targetDateMillis,
                colorHex = colorHex
            )
        )
        emitMsg("تم إنشاء هدف الادخار")
    }

    fun contributeToGoal(
        goal: SavingsGoalEntity,
        accountId: Long,
        amountPiasters: Long
    ) = saveTransaction(
        amountPiasters = amountPiasters,
        type = TransactionType.SAVINGS_CONTRIBUTION,
        accountId = accountId,
        savingsGoalId = goal.id,
        description = "مساهمة في هدف: ${goal.name}"
    )

    fun toggleGoalPause(goal: SavingsGoalEntity) = viewModelScope.launch {
        val next = if (goal.status == GoalStatus.PAUSED) GoalStatus.ACTIVE else GoalStatus.PAUSED
        repository.updateGoal(goal.copy(status = next))
    }

    fun deleteGoal(goal: SavingsGoalEntity) = viewModelScope.launch {
        repository.deleteGoal(goal)
        triggerBackgroundCloudSyncIfEnabled()
    }

    // --- Bank Savings Certificates & Gold Reserves ---

    fun saveCertificate(
        existingId: Long? = null,
        title: String,
        assetKind: SavingsAssetKind,
        institutionName: String,
        principalPiasters: Long,
        annualYieldPercent: Double,
        payoutCycle: PayoutCycle,
        linkedAccountId: Long?,
        nextPayoutMillis: Long = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L,
        maturityMillis: Long? = null,
        autoCreditInterest: Boolean = true,
        grams: Double = 0.0,
        karat: Int = 21,
        pricePerGramPiasters: Long = 0L,
        notes: String = ""
    ) = viewModelScope.launch {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@launch
        val entity = SavingsCertificateEntity(
            id = existingId ?: 0L,
            title = cleanTitle,
            assetKind = assetKind,
            institutionName = institutionName.trim(),
            principalPiasters = principalPiasters.coerceAtLeast(0L),
            annualYieldPercent = annualYieldPercent.coerceAtLeast(0.0),
            payoutCycle = payoutCycle,
            linkedAccountId = linkedAccountId,
            nextPayoutMillis = nextPayoutMillis,
            maturityMillis = maturityMillis,
            autoCreditInterest = autoCreditInterest,
            grams = grams.coerceAtLeast(0.0),
            karat = karat,
            pricePerGramPiasters = pricePerGramPiasters.coerceAtLeast(0L),
            notes = notes.trim(),
            isActive = true
        )
        if (existingId != null && existingId > 0L) {
            repository.updateCertificate(entity)
        } else {
            repository.saveCertificate(entity)
        }
        triggerBackgroundCloudSyncIfEnabled()
        emitMsg(
            if (assetKind == SavingsAssetKind.GOLD_RESERVE) "تم حفظ مدخرات الذهب"
            else "تم حفظ الشهادة / الوديعة البنكية"
        )
    }

    fun deleteCertificate(cert: SavingsCertificateEntity) = viewModelScope.launch {
        repository.deleteCertificate(cert)
        triggerBackgroundCloudSyncIfEnabled()
        emitMsg("تم حذف الأصل الادخاري")
    }

    fun updateGoldPrice(karat: Int, pricePerGramPiasters: Long) = viewModelScope.launch {
        if (pricePerGramPiasters <= 0L) return@launch
        repository.updateGoldPriceForKarat(karat, pricePerGramPiasters)
        triggerBackgroundCloudSyncIfEnabled()
        emitMsg("تم تحديث سعر جرام عيار $karat لجميع أصول الذهب")
    }

    fun creditCertificateInterest(certId: Long) = viewModelScope.launch {
        val txId = withContext(Dispatchers.IO) {
            repository.creditCertificateInterest(certId)
        }
        if (txId != null) {
            refreshWidgets()
            triggerBackgroundCloudSyncIfEnabled()
            emitMsg("تم إضافة عائد الشهادة إلى رصيد الحساب المرتبط")
        } else {
            emitMsg("تعذر صرف العائد (تأكد من قيمة الشهادة ونسبة الفائدة)")
        }
    }

    // --- Installments & BNPL Programs ---

    fun saveInstallment(
        name: String,
        creditor: String,
        accountId: Long?,
        installmentAmountPiasters: Long,
        totalCount: Int,
        firstDueDateMillis: Long,
        frequency: InstallmentFrequency,
        bnplProgramId: Long? = null,
        principalAmountPiasters: Long = 0L
    ) = viewModelScope.launch {
        val totalRepayable = installmentAmountPiasters * totalCount
        repository.saveInstallment(
            InstallmentEntity(
                name = name.trim(),
                creditor = creditor.trim(),
                accountId = accountId,
                totalAmountPiasters = totalRepayable,
                installmentAmountPiasters = installmentAmountPiasters,
                totalCount = totalCount,
                nextDueDateMillis = firstDueDateMillis,
                frequency = frequency,
                bnplProgramId = bnplProgramId,
                principalAmountPiasters = principalAmountPiasters.coerceAtLeast(0L)
            )
        )
        emitMsg("تم تسجيل القسط بنجاح")
    }

    fun saveBnplProgram(
        existingId: Long? = null,
        nameAr: String,
        nameEn: String,
        creditLimitPiasters: Long,
        manualUsedPiasters: Long,
        defaultAnnualInterestPercent: Double,
        colorHex: String = "#38BDF8",
        notes: String = ""
    ) = viewModelScope.launch {
        val existing = existingId?.let { id -> uiState.value.bnplPrograms.firstOrNull { it.id == id } }
        val program = if (existing != null) {
            existing.copy(
                nameAr = nameAr.trim().ifBlank { existing.nameAr },
                nameEn = nameEn.trim().ifBlank { existing.nameEn },
                creditLimitPiasters = creditLimitPiasters.coerceAtLeast(0L),
                manualUsedPiasters = manualUsedPiasters.coerceAtLeast(0L),
                defaultAnnualInterestPercent = defaultAnnualInterestPercent.coerceAtLeast(0.0),
                colorHex = colorHex,
                notes = notes.trim()
            )
        } else {
            BnplProgramEntity(
                providerCode = "CUSTOM_${System.currentTimeMillis()}",
                nameAr = nameAr.trim(),
                nameEn = nameEn.trim().ifBlank { nameAr.trim() },
                creditLimitPiasters = creditLimitPiasters.coerceAtLeast(0L),
                manualUsedPiasters = manualUsedPiasters.coerceAtLeast(0L),
                defaultAnnualInterestPercent = defaultAnnualInterestPercent.coerceAtLeast(0.0),
                colorHex = colorHex,
                isEnabled = true,
                notes = notes.trim()
            )
        }
        repository.saveBnplProgram(program)
        emitMsg("تم حفظ بيانات برنامج التقسيط")
    }

    fun deleteBnplProgram(program: BnplProgramEntity) = viewModelScope.launch {
        repository.deleteBnplProgram(program)
        emitMsg("تم حذف برنامج التقسيط")
    }

    fun payInstallment(installment: InstallmentEntity, accountId: Long) = viewModelScope.launch {
        runCatching {
            repository.payInstallment(installment.id, accountId)
            refreshWidgets()
            emitMsg("تم سداد القسط وتسجيل مصروف واحد مرتبط")
        }.onFailure {
            emitMsg("تعذر سداد القسط")
        }
    }

    fun setInstallmentStatus(installment: InstallmentEntity, status: InstallmentStatus) = viewModelScope.launch {
        repository.updateInstallment(installment.copy(status = status))
    }

    // --- Recurring Income & Expenses ---

    fun saveRecurringRule(
        title: String,
        type: TransactionType,
        incomeSourceType: IncomeSourceType,
        amountPiasters: Long,
        accountId: Long,
        categoryId: Long?,
        frequency: RecurringFrequency,
        nextRunDateMillis: Long,
        notifyDaysBefore: Int
    ) = viewModelScope.launch {
        repository.saveRecurringRule(
            RecurringRuleEntity(
                title = title.trim(),
                type = type,
                incomeSourceType = incomeSourceType,
                amountPiasters = amountPiasters,
                accountId = accountId,
                categoryId = categoryId,
                frequency = frequency,
                nextRunDateMillis = nextRunDateMillis,
                notifyDaysBefore = notifyDaysBefore
            )
        )
        repository.executeDueRecurringRules()
        emitMsg("تم حفظ القاعدة المتكررة")
    }

    fun toggleRecurringRule(rule: RecurringRuleEntity) = viewModelScope.launch {
        repository.updateRecurringRule(rule.copy(isActive = !rule.isActive))
    }

    fun deleteRecurringRule(rule: RecurringRuleEntity) = viewModelScope.launch {
        repository.deleteRecurringRule(rule)
    }

    // --- Debts ---

    fun saveDebt(
        personName: String,
        direction: DebtDirection,
        totalAmountPiasters: Long,
        dueDateMillis: Long?,
        notes: String
    ) = viewModelScope.launch {
        repository.saveDebt(
            DebtEntity(
                personName = personName.trim(),
                direction = direction,
                totalAmountPiasters = totalAmountPiasters,
                dueDateMillis = dueDateMillis,
                notes = notes.trim()
            )
        )
        emitMsg("تم تسجيل الدين")
    }

    fun settleDebt(debt: DebtEntity, accountId: Long, amountPiasters: Long) = viewModelScope.launch {
        repository.recordDebtSettlement(debt.id, accountId, amountPiasters)
        emitMsg("تم تسجيل دفعة سداد الدين")
    }

    fun deleteDebt(debt: DebtEntity) = viewModelScope.launch {
        repository.deleteDebt(debt)
    }

    // --- Notifications ---

    fun markAllNotificationsRead() = viewModelScope.launch {
        repository.markAllNotificationsRead()
    }

    // --- Exports & Backup ---

    fun exportAndShareCsv(context: Context) = viewModelScope.launch {
        val state = uiState.value
        val file = withContext(Dispatchers.IO) {
            backupManager.exportCsvToFile(
                context = context,
                title = "ELBAGORRY Money",
                transactions = state.filteredTransactions,
                accounts = state.accounts.associateBy { it.id },
                categories = state.categories.associateBy { it.id }
            )
        }
        backupManager.shareFile(context, file, "text/csv", "مشاركة تقرير CSV")
    }

    fun exportAndSharePdf(context: Context) = viewModelScope.launch {
        val state = uiState.value
        val m = state.dashboardMetrics
        val file = withContext(Dispatchers.IO) {
            backupManager.exportPdfToFile(
                context = context,
                reportTitle = "Financial Statement",
                periodLabel = state.timeFilter.name,
                totalIncome = m.totalIncomePiasters,
                totalExpense = m.totalExpensesPiasters,
                netBalance = m.netBalancePiasters,
                transactions = state.filteredTransactions,
                language = state.preferences.language
            )
        }
        backupManager.shareFile(context, file, "application/pdf", "مشاركة تقرير PDF")
    }

    fun exportAndShareJsonBackup(context: Context) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val raw = backupManager.createJsonBackup()
            backupManager.exportJsonToFile(context, raw)
        }
        backupManager.shareFile(context, file, "application/json", "حفظ النسخة الاحتياطية JSON")
        emitMsg("تم تجهيز النسخة الاحتياطية JSON")
    }

    fun writeBackupToUri(context: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            val raw = withContext(Dispatchers.IO) { backupManager.createJsonBackup() }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(raw.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open output stream")
            }
            emitMsg("تم حفظ النسخة الاحتياطية بنجاح")
        }.onFailure {
            emitMsg("فشل حفظ النسخة الاحتياطية")
        }
    }

    fun writeEncryptedBackupToUri(context: Context, uri: Uri, password: String) = viewModelScope.launch {
        runCatching {
            val encrypted = withContext(Dispatchers.IO) {
                backupManager.createEncryptedJsonBackup(password)
            }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(encrypted.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open output stream")
            }
            emitMsg("تم حفظ النسخة الاحتياطية المشفرة (AES-256) بنجاح")
        }.onFailure {
            emitMsg(it.message ?: "فشل تشفير أو حفظ النسخة الاحتياطية")
        }
    }

    suspend fun readBackupFileState(context: Context, uri: Uri): Pair<String, Boolean>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@runCatching null
                raw to backupManager.isEncryptedBackup(raw)
            }.getOrNull()
        }
    }

    fun restoreBackupContent(rawText: String, password: String? = null) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            backupManager.restoreAuto(rawText, password)
        }
        result.onSuccess { count ->
            refreshWidgets()
            triggerBackgroundCloudSyncIfEnabled()
            emitMsg("تمت استعادة البيانات بنجاح ($count معاملة)")
        }.onFailure { err ->
            emitMsg(err.message ?: "ملف النسخة الاحتياطية أو كلمة المرور غير صالحة")
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            val raw = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot read file")
            }
            val result = withContext(Dispatchers.IO) { backupManager.restoreAuto(raw, null) }
            result.onSuccess { count ->
                refreshWidgets()
                triggerBackgroundCloudSyncIfEnabled()
                emitMsg("تمت استعادة البيانات بنجاح ($count معاملة)")
            }.onFailure { err ->
                emitMsg(err.message ?: "ملف النسخة الاحتياطية غير صالح")
            }
        }.onFailure {
            emitMsg("تعذر قراءة الملف المحدد")
        }
    }

    private fun triggerBackgroundCloudSyncIfEnabled() {
        val ctx = appContext ?: return
        val prefs = uiState.value.preferences
        if (prefs.autoCloudSyncEnabled && !prefs.isGuestMode) {
            CloudAutoSyncWorker.enqueueImmediateDebounced(ctx)
        }
    }

    private fun refreshWidgets() {
        appContext?.let { ElbagorryWidgetUpdater.updateAllAsync(it) }
    }

    private fun emitMsg(msg: String) {
        viewModelScope.launch { _events.emit(msg) }
    }

    class Factory(
        private val repository: MoneyRepository,
        private val prefsRepo: UserPreferencesRepository,
        private val backupManager: BackupAndExportManager,
        private val smartReaderProcessor: SmartReaderProcessor,
        private val appContext: Context? = null,
        private val aiGateway: FinancialAiGateway = FirebaseGeminiFinancialGateway()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, prefsRepo, backupManager, smartReaderProcessor, appContext, aiGateway) as T
        }
    }
}
