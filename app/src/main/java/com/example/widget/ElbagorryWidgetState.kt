package com.example.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.engine.CurrencyConverter
import com.example.domain.engine.DateRangeResolver
import com.example.domain.model.AppLanguage
import com.example.domain.model.PendingStatus
import com.example.domain.model.ThemePreset
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WidgetSnapshot(
    val netBalancePiasters: Long = 0L,
    val todayExpensePiasters: Long = 0L,
    val monthIncomePiasters: Long = 0L,
    val monthExpensePiasters: Long = 0L,
    val pendingCount: Int = 0,
    val currency: String = "EGP",
    val hideAmounts: Boolean = false,
    val language: AppLanguage = AppLanguage.AR,
    val themePreset: ThemePreset = ThemePreset.HYDROGEN_NEXUS,
    val hasData: Boolean = false
)

object WidgetStateKeys {
    val NET_BALANCE = longPreferencesKey("widget_net_balance")
    val TODAY_EXPENSE = longPreferencesKey("widget_today_expense")
    val MONTH_INCOME = longPreferencesKey("widget_month_income")
    val MONTH_EXPENSE = longPreferencesKey("widget_month_expense")
    val PENDING_COUNT = intPreferencesKey("widget_pending_count")
    val CURRENCY = stringPreferencesKey("widget_currency")
    val HIDE_AMOUNTS = booleanPreferencesKey("widget_hide_amounts")
    val LANGUAGE = stringPreferencesKey("widget_language")
    val THEME_PRESET = stringPreferencesKey("widget_theme_preset")
    val HAS_DATA = booleanPreferencesKey("widget_has_data")
    val REVISION = longPreferencesKey("widget_revision")
}

fun computeWidgetSnapshot(
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    pending: List<PendingTransactionEntity>,
    prefs: UserPreferences,
    nowMillis: Long = System.currentTimeMillis()
): WidgetSnapshot {
    val targetCurrency = prefs.currencyCode
    val accountCurrencyMap = accounts.associate { it.id to it.currency }
    val activeAccounts = accounts.filter { !it.isArchived }
    val netBalance = activeAccounts.sumOf {
        CurrencyConverter.convertPiasters(it.currentBalancePiasters, it.currency, targetCurrency)
    }

    val todayRange = DateRangeResolver.resolve(TimeFilter.TODAY, nowMillis)
    val monthRange = DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, nowMillis)

    fun txAmt(tx: TransactionEntity): Long {
        val from = accountCurrencyMap[tx.accountId] ?: "EGP"
        return CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
    }

    val todayExpense = transactions
        .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in todayRange.startMillis..todayRange.endMillis }
        .sumOf { txAmt(it) }

    val monthTx = transactions.filter { it.timestampMillis in monthRange.startMillis..monthRange.endMillis }
    val monthIncome = monthTx.filter { it.type == TransactionType.INCOME }.sumOf { txAmt(it) }
    val monthExpense = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { txAmt(it) }

    val pendingCount = pending.count { it.status == PendingStatus.PENDING }

    return WidgetSnapshot(
        netBalancePiasters = netBalance,
        todayExpensePiasters = todayExpense,
        monthIncomePiasters = monthIncome,
        monthExpensePiasters = monthExpense,
        pendingCount = pendingCount,
        currency = targetCurrency,
        hideAmounts = prefs.hideAmounts,
        language = prefs.language,
        themePreset = prefs.themePreset,
        hasData = true
    )
}

fun Preferences.toWidgetSnapshot(): WidgetSnapshot {
    val lang = this[WidgetStateKeys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.AR
    val preset = this[WidgetStateKeys.THEME_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: ThemePreset.HYDROGEN_NEXUS
    return WidgetSnapshot(
        netBalancePiasters = this[WidgetStateKeys.NET_BALANCE] ?: 0L,
        todayExpensePiasters = this[WidgetStateKeys.TODAY_EXPENSE] ?: 0L,
        monthIncomePiasters = this[WidgetStateKeys.MONTH_INCOME] ?: 0L,
        monthExpensePiasters = this[WidgetStateKeys.MONTH_EXPENSE] ?: 0L,
        pendingCount = this[WidgetStateKeys.PENDING_COUNT] ?: 0,
        currency = this[WidgetStateKeys.CURRENCY] ?: "EGP",
        hideAmounts = this[WidgetStateKeys.HIDE_AMOUNTS] ?: false,
        language = lang,
        themePreset = preset,
        hasData = this[WidgetStateKeys.HAS_DATA] ?: false
    )
}

object ElbagorryWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun loadCurrentSnapshot(context: Context): WidgetSnapshot {
        val db = AppDatabase.getInstance(context)
        val prefs = UserPreferencesRepository(context).preferences.first()
        val accounts = db.accountDao().getAllOnce()
        val transactions = db.transactionDao().getAllOnce()
        val pending = db.pendingTransactionDao().getAllOnce()
        return computeWidgetSnapshot(accounts, transactions, pending, prefs)
    }

    suspend fun updateAll(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(ElbagorrySummaryWidget::class.java)
            if (glanceIds.isEmpty()) return

            val snapshot = loadCurrentSnapshot(appContext)
            val widget = ElbagorrySummaryWidget()
            for (glanceId in glanceIds) {
                updateAppWidgetState(appContext, glanceId) { prefs ->
                    prefs[WidgetStateKeys.NET_BALANCE] = snapshot.netBalancePiasters
                    prefs[WidgetStateKeys.TODAY_EXPENSE] = snapshot.todayExpensePiasters
                    prefs[WidgetStateKeys.MONTH_INCOME] = snapshot.monthIncomePiasters
                    prefs[WidgetStateKeys.MONTH_EXPENSE] = snapshot.monthExpensePiasters
                    prefs[WidgetStateKeys.PENDING_COUNT] = snapshot.pendingCount
                    prefs[WidgetStateKeys.CURRENCY] = snapshot.currency
                    prefs[WidgetStateKeys.HIDE_AMOUNTS] = snapshot.hideAmounts
                    prefs[WidgetStateKeys.LANGUAGE] = snapshot.language.name
                    prefs[WidgetStateKeys.THEME_PRESET] = snapshot.themePreset.name
                    prefs[WidgetStateKeys.HAS_DATA] = true
                    prefs[WidgetStateKeys.REVISION] = (prefs[WidgetStateKeys.REVISION] ?: 0L) + 1L
                }
                widget.update(appContext, glanceId)
            }
        }
    }

    fun updateAllAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            updateAll(appContext)
        }
    }
}
