package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AnalyticsSummary
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.InsightSeverity
import com.example.domain.model.SmartInsight
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AnalyticsEngine {

    fun analyze(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        installments: List<InstallmentEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        targetCurrency: String = "EGP"
    ): AnalyticsSummary {
        val accountCurrencyMap = accounts.associate { it.id to it.currency }
        fun txAmt(tx: TransactionEntity): Long {
            val from = accountCurrencyMap[tx.accountId] ?: "EGP"
            return CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
        }

        val todayRange = DateRangeResolver.resolve(TimeFilter.TODAY, nowMillis)
        val currentMonthRange = DateRangeResolver.resolve(TimeFilter.CURRENT_MONTH, nowMillis)
        val prevMonthRange = DateRangeResolver.resolve(TimeFilter.PREVIOUS_MONTH, nowMillis)
        val currentYearRange = DateRangeResolver.resolve(TimeFilter.CURRENT_YEAR, nowMillis)

        val dailySpend = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in todayRange.startMillis..todayRange.endMillis }
            .sumOf { txAmt(it) }

        val monthTx = transactions.filter { it.timestampMillis in currentMonthRange.startMillis..currentMonthRange.endMillis }
        val prevMonthTx = transactions.filter { it.timestampMillis in prevMonthRange.startMillis..prevMonthRange.endMillis }

        val monthIncome = monthTx.filter { it.type == TransactionType.INCOME }.sumOf { txAmt(it) }
        val monthExpense = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { txAmt(it) }
        val prevMonthIncome = prevMonthTx.filter { it.type == TransactionType.INCOME }.sumOf { txAmt(it) }
        val prevMonthExpense = prevMonthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { txAmt(it) }

        val yearlySpend = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in currentYearRange.startMillis..currentYearRange.endMillis }
            .sumOf { txAmt(it) }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(dayOfMonth)
        val remainingDaysInMonth = (daysInMonth - dayOfMonth).coerceAtLeast(0)

        val avgDailySpend = BigDecimal(monthExpense)
            .divide(BigDecimal(dayOfMonth), 0, RoundingMode.HALF_UP)
            .toLong()

        val currentBalance = accounts.filter { !it.isArchived }.sumOf {
            CurrencyConverter.convertPiasters(it.currentBalancePiasters, it.currency, targetCurrency)
        }
        val projectedEndOfMonthBalance = currentBalance - (avgDailySpend * remainingDaysInMonth)

        val expenseDelta = computeDeltaPercent(monthExpense, prevMonthExpense)
        val incomeDelta = computeDeltaPercent(monthIncome, prevMonthIncome)

        val monthExpenseTx = monthTx.filter { it.type == TransactionType.EXPENSE }
        val breakdown = FinancialEngine.buildCategoryBreakdown(
            periodExpenses = monthExpenseTx,
            categories = categories,
            totalExpenses = monthExpense,
            accountCurrencyMap = accountCurrencyMap,
            targetCurrency = targetCurrency
        )
        val highestCategory = breakdown.firstOrNull()
        val lowestCategory = breakdown.lastOrNull()

        val byDay = monthExpenseTx.groupBy { DateRangeResolver.startOfDayMillis(it.timestampMillis) }
        val peakEntry = byDay.maxByOrNull { (_, list) -> list.sumOf { txAmt(it) } }
        val sdf = SimpleDateFormat("EEEE dd/MM", Locale.forLanguageTag("ar"))
        val peakLabel = peakEntry?.key?.let { sdf.format(Date(it)) }
        val peakAmount = peakEntry?.value?.sumOf { txAmt(it) } ?: 0L

        val savingsRate = if (monthIncome > 0L) {
            FinancialEngine.computePercent(monthIncome - monthExpense, monthIncome)
        } else 0.0

        val insights = buildLocalSmartInsights(
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            prevMonthIncome = prevMonthIncome,
            prevMonthExpense = prevMonthExpense,
            monthExpenseTx = monthExpenseTx,
            prevMonthExpenseTx = prevMonthTx.filter { it.type == TransactionType.EXPENSE },
            categories = categories,
            budgets = budgets,
            transactions = transactions,
            installments = installments,
            nowMillis = nowMillis
        )

        return AnalyticsSummary(
            dailySpendPiasters = dailySpend,
            monthlySpendPiasters = monthExpense,
            yearlySpendPiasters = yearlySpend,
            averageDailySpendPiasters = avgDailySpend,
            previousPeriodExpenseDeltaPercent = expenseDelta,
            previousPeriodIncomeDeltaPercent = incomeDelta,
            highestCategory = highestCategory,
            lowestCategory = lowestCategory,
            highestSpendDayLabel = peakLabel,
            highestSpendDayAmountPiasters = peakAmount,
            savingsRatePercent = savingsRate,
            projectedEndOfMonthBalancePiasters = projectedEndOfMonthBalance,
            insights = insights
        )
    }

    private fun buildLocalSmartInsights(
        monthIncome: Long,
        monthExpense: Long,
        prevMonthIncome: Long,
        prevMonthExpense: Long,
        monthExpenseTx: List<TransactionEntity>,
        prevMonthExpenseTx: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        installments: List<InstallmentEntity>,
        nowMillis: Long
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()
        val categoryMap = categories.associateBy { it.id }

        // 1. Category-level spike vs previous month
        val currByCat = monthExpenseTx.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amountPiasters } }
        val prevByCat = prevMonthExpenseTx.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amountPiasters } }

        currByCat.forEach { (catId, currAmount) ->
            val prevAmount = prevByCat[catId] ?: 0L
            if (prevAmount > 0L && currAmount > prevAmount) {
                val pct = FinancialEngine.computePercent(currAmount - prevAmount, prevAmount)
                if (pct >= 15.0) {
                    val cat = categoryMap[catId]
                    val nameAr = cat?.nameAr ?: "المصروفات"
                    val nameEn = cat?.nameEn ?: "Category"
                    val pctText = MoneyFormatter.formatPercent(pct)
                    insights += SmartInsight(
                        id = "cat_spike_${catId ?: 0}",
                        titleAr = "ارتفاع مصروفات $nameAr",
                        titleEn = "$nameEn spending increased",
                        detailAr = "مصروفات $nameAr ارتفعت بنسبة $pctText مقارنة بالشهر السابق.",
                        detailEn = "$nameEn expenses increased by $pctText compared to last month.",
                        severity = InsightSeverity.WARNING
                    )
                }
            }
        }

        // 2. Budget warnings (>= 80% or exceeded)
        val currentYm = DateRangeResolver.currentYearMonth(nowMillis)
        val budgetEvals = FinancialEngine.evaluateBudgets(budgets, transactions, currentYm, nowMillis)
        budgetEvals.forEach { eval ->
            if (eval.isExceeded) {
                insights += SmartInsight(
                    id = "budget_over_${eval.budgetId}",
                    titleAr = "تجاوز ميزانية ${eval.name}",
                    titleEn = "${eval.name} budget exceeded",
                    detailAr = "لقد تجاوزت حد ميزانية ${eval.name} بنسبة ${MoneyFormatter.formatPercent(eval.usagePercent)}.",
                    detailEn = "You exceeded ${eval.name} budget at ${MoneyFormatter.formatPercent(eval.usagePercent)}.",
                    severity = InsightSeverity.CRITICAL
                )
            } else if (eval.isWarning80) {
                insights += SmartInsight(
                    id = "budget_warn_${eval.budgetId}",
                    titleAr = "اقتربت من حد ${eval.name}",
                    titleEn = "Approaching ${eval.name} budget",
                    detailAr = "استهلكت ${MoneyFormatter.formatPercent(eval.usagePercent)} من ميزانية ${eval.name}.",
                    detailEn = "You used ${MoneyFormatter.formatPercent(eval.usagePercent)} of ${eval.name} budget.",
                    severity = InsightSeverity.WARNING
                )
            }
        }

        // 3. Savings rate comparison
        if (monthIncome > 0L && prevMonthIncome > 0L) {
            val currRate = FinancialEngine.computePercent(monthIncome - monthExpense, monthIncome)
            val prevRate = FinancialEngine.computePercent(prevMonthIncome - prevMonthExpense, prevMonthIncome)
            if (currRate > prevRate + 1.0) {
                insights += SmartInsight(
                    id = "savings_improved",
                    titleAr = "تحسن معدل الادخار",
                    titleEn = "Savings rate improved",
                    detailAr = "معدل ادخارك هذا الشهر (${MoneyFormatter.formatPercent(currRate)}) أفضل من الشهر الماضي (${MoneyFormatter.formatPercent(prevRate)}).",
                    detailEn = "Your savings rate (${MoneyFormatter.formatPercent(currRate)}) improved over last month (${MoneyFormatter.formatPercent(prevRate)}).",
                    severity = InsightSeverity.POSITIVE
                )
            }
        }

        // 4. Overdue installments
        val overdueCount = installments.count {
            it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount && it.nextDueDateMillis < nowMillis
        }
        if (overdueCount > 0) {
            insights += SmartInsight(
                id = "overdue_installments",
                titleAr = "أقساط مستحقة متأخرة",
                titleEn = "Overdue installments",
                detailAr = "لديك $overdueCount التزام/قسط متأخر عن موعد استحقاقه، يرجى المراجعة.",
                detailEn = "You have $overdueCount overdue installment(s) requiring attention.",
                severity = InsightSeverity.CRITICAL
            )
        }

        if (insights.isEmpty()) {
            insights += SmartInsight(
                id = "baseline_healthy",
                titleAr = "الوضع المالي مستقر",
                titleEn = "Financial status is steady",
                detailAr = "استمر في تسجيل معاملاتك اليومية ومراقبة الميزانيات للحصول على تنبيهات وتوقعات أدق.",
                detailEn = "Keep logging daily transactions and budgets for sharper local financial insights.",
                severity = InsightSeverity.INFO
            )
        }

        return insights
    }

    private fun computeDeltaPercent(current: Long, previous: Long): Double? {
        if (previous <= 0L) return null
        val diff = current - previous
        val pct = FinancialEngine.computePercent(abs(diff), previous)
        return if (diff >= 0) pct else -pct
    }
}
