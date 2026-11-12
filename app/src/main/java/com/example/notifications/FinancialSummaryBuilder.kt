package com.example.notifications

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.PendingStatus
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType

data class FinancialSummarySnapshot(
    val netBalancePiasters: Long,
    val todaySpendPiasters: Long,
    val weekSpendPiasters: Long,
    val monthSpendPiasters: Long,
    val remainingBudgetPiasters: Long,
    val dueInstallmentsCount: Int,
    val pendingReaderCount: Int
)

object FinancialSummaryBuilder {

    fun isSummaryDue(
        lastSummaryTimestampMillis: Long,
        frequency: SummaryFrequency,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastSummaryTimestampMillis <= 0L) return true
        val elapsed = nowMillis - lastSummaryTimestampMillis
        val thresholdMillis = when (frequency) {
            SummaryFrequency.DAILY -> 20L * 60L * 60L * 1000L
            SummaryFrequency.WEEKLY -> 6L * 24L * 60L * 60L * 1000L
        }
        return elapsed >= thresholdMillis
    }

    fun build(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        installments: List<InstallmentEntity>,
        pending: List<PendingTransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): FinancialSummarySnapshot {
        val netBalance = accounts.filter { !it.isArchived }.sumOf { it.currentBalancePiasters }
        val todayRange = DateRangeResolver.resolve(TimeFilter.TODAY, nowMillis)
        val weekRange = DateRangeResolver.resolve(TimeFilter.THIS_WEEK, nowMillis)
        val monthRange = DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, nowMillis)

        val todaySpend = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in todayRange.startMillis..todayRange.endMillis }
            .sumOf { it.amountPiasters }

        val weekSpend = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in weekRange.startMillis..weekRange.endMillis }
            .sumOf { it.amountPiasters }

        val monthSpend = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in monthRange.startMillis..monthRange.endMillis }
            .sumOf { it.amountPiasters }

        val evals = FinancialEngine.evaluateBudgets(
            budgets = budgets,
            transactions = transactions,
            currentYearMonth = DateRangeResolver.currentYearMonth(nowMillis),
            nowMillis = nowMillis
        )
        val remainingBudget = evals.sumOf { it.remainingPiasters }

        val dueWindow = nowMillis + 3L * 24L * 60L * 60L * 1000L
        val dueInstallments = installments.count {
            it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount && it.nextDueDateMillis <= dueWindow
        }
        val pendingCount = pending.count { it.status == PendingStatus.PENDING }

        return FinancialSummarySnapshot(
            netBalancePiasters = netBalance,
            todaySpendPiasters = todaySpend,
            weekSpendPiasters = weekSpend,
            monthSpendPiasters = monthSpend,
            remainingBudgetPiasters = remainingBudget,
            dueInstallmentsCount = dueInstallments,
            pendingReaderCount = pendingCount
        )
    }

    fun formatMessageText(
        snapshot: FinancialSummarySnapshot,
        isWeekly: Boolean,
        currency: String,
        hideAmounts: Boolean,
        lang: AppLanguage
    ): String {
        val bal = MoneyFormatter.formatPiasters(snapshot.netBalancePiasters, currency, lang, hideAmounts)
        val today = MoneyFormatter.formatPiasters(snapshot.todaySpendPiasters, currency, lang, hideAmounts)
        val period = MoneyFormatter.formatPiasters(
            if (isWeekly) snapshot.weekSpendPiasters else snapshot.monthSpendPiasters,
            currency,
            lang,
            hideAmounts
        )
        return if (lang == AppLanguage.AR) {
            val periodLabel = if (isWeekly) "الأسبوع" else "الشهر"
            "صافي الرصيد: $bal • مصروف اليوم: $today • مصروف $periodLabel: $period"
        } else {
            val periodLabel = if (isWeekly) "Week" else "Month"
            "Net: $bal • Today: $today • $periodLabel: $period"
        }
    }
}
