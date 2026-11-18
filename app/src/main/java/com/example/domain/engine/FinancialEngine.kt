package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.BudgetEvaluation
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.GoalStatus
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TimeRange
import com.example.domain.model.TransactionType
import com.example.domain.model.TrendPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FinancialEngine {

    private const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L

    fun calculateDashboardMetrics(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        savingsGoals: List<SavingsGoalEntity>,
        installments: List<InstallmentEntity>,
        debts: List<DebtEntity>,
        range: TimeRange,
        nowMillis: Long = System.currentTimeMillis(),
        targetCurrency: String = "EGP"
    ): DashboardMetrics {
        val accountCurrencyMap = accounts.associate { it.id to it.currency }
        fun txAmountInTarget(tx: TransactionEntity): Long {
            val from = accountCurrencyMap[tx.accountId] ?: "EGP"
            return CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
        }

        val periodTransactions = transactions.filter {
            it.timestampMillis in range.startMillis..range.endMillis
        }

        // Only INCOME and EXPENSE affect cash-flow income/expense metrics.
        // TRANSFER, SAVINGS_CONTRIBUTION, and DEBT_PAYMENT are strictly excluded to avoid double counting.
        val totalIncome = periodTransactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { txAmountInTarget(it) }

        val totalExpenses = periodTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { txAmountInTarget(it) }

        val netCashFlow = totalIncome - totalExpenses

        val activeAccounts = accounts.filter { !it.isArchived }
        val netBalance = activeAccounts.sumOf { acc ->
            CurrencyConverter.convertPiasters(acc.currentBalancePiasters, acc.currency, targetCurrency)
        }

        val expenseToIncomeRatio = computePercent(totalExpenses, totalIncome)
        val savingsRate = if (totalIncome > 0L) {
            computePercent(totalIncome - totalExpenses, totalIncome)
        } else {
            0.0
        }

        val activeInstallments = installments.filter {
            it.status == InstallmentStatus.ACTIVE || it.status == InstallmentStatus.PAUSED
        }
        val installmentRemainingTotal = activeInstallments.sumOf { inst ->
            val remainingCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0)
            val rawRemaining = remainingCount.toLong() * inst.installmentAmountPiasters
            CurrencyConverter.convertPiasters(rawRemaining, "EGP", targetCurrency)
        }

        val owedByMeRemaining = debts
            .filter { it.direction == DebtDirection.OWED_BY_ME && it.status != DebtStatus.SETTLED }
            .sumOf {
                val rem = (it.totalAmountPiasters - it.paidAmountPiasters).coerceAtLeast(0L)
                CurrencyConverter.convertPiasters(rem, "EGP", targetCurrency)
            }

        val totalObligations = installmentRemainingTotal + owedByMeRemaining

        val dueWindowEnd = nowMillis + SEVEN_DAYS_MILLIS
        val dueInstallments = installments
            .filter { it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount }
            .filter { it.nextDueDateMillis <= dueWindowEnd }
            .sumOf { CurrencyConverter.convertPiasters(it.installmentAmountPiasters, "EGP", targetCurrency) }

        val dueDebts = debts
            .filter {
                it.direction == DebtDirection.OWED_BY_ME &&
                    it.status != DebtStatus.SETTLED &&
                    it.dueDateMillis != null &&
                    it.dueDateMillis <= dueWindowEnd
            }
            .sumOf {
                val rem = (it.totalAmountPiasters - it.paidAmountPiasters).coerceAtLeast(0L)
                CurrencyConverter.convertPiasters(rem, "EGP", targetCurrency)
            }

        val amountsDue = dueInstallments + dueDebts

        val currentYm = DateRangeResolver.currentYearMonth(nowMillis)
        val budgetEvaluations = evaluateBudgets(
            budgets = budgets,
            transactions = transactions,
            currentYearMonth = currentYm,
            nowMillis = nowMillis,
            accounts = accounts,
            targetCurrency = targetCurrency
        )
        val generalBudget = budgetEvaluations.firstOrNull { it.categoryId == null }
        val remainingBudget = when {
            generalBudget != null -> generalBudget.remainingPiasters
            budgetEvaluations.isNotEmpty() -> budgetEvaluations.sumOf { it.remainingPiasters }
            else -> 0L
        }

        val allocatedSavings = savingsGoals
            .filter { it.status != GoalStatus.PAUSED }
            .sumOf { CurrencyConverter.convertPiasters(it.savedAmountPiasters, "EGP", targetCurrency) }

        // Available funds = Current liquid account balances minus immediate due obligations
        val availableFunds = netBalance - amountsDue

        val categoryBreakdown = buildCategoryBreakdown(
            periodExpenses = periodTransactions.filter { it.type == TransactionType.EXPENSE },
            categories = categories,
            totalExpenses = totalExpenses,
            accountCurrencyMap = accountCurrencyMap,
            targetCurrency = targetCurrency
        )

        val trendSeries = buildTrendSeries(
            transactions = periodTransactions,
            range = range,
            nowMillis = nowMillis,
            accountCurrencyMap = accountCurrencyMap,
            targetCurrency = targetCurrency
        )

        return DashboardMetrics(
            totalIncomePiasters = totalIncome,
            totalExpensesPiasters = totalExpenses,
            netBalancePiasters = netBalance,
            netCashFlowPiasters = netCashFlow,
            expenseToIncomeRatioPercent = expenseToIncomeRatio,
            savingsRatePercent = savingsRate,
            totalObligationsPiasters = totalObligations,
            amountsDuePiasters = amountsDue,
            remainingBudgetPiasters = remainingBudget,
            availableFundsPiasters = availableFunds,
            allocatedSavingsPiasters = allocatedSavings,
            categoryBreakdown = categoryBreakdown,
            trendSeries = trendSeries
        )
    }

    fun evaluateBudgets(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        currentYearMonth: String,
        nowMillis: Long = System.currentTimeMillis(),
        accounts: List<AccountEntity> = emptyList(),
        targetCurrency: String = "EGP"
    ): List<BudgetEvaluation> {
        val accountCurrencyMap = accounts.associate { it.id to it.currency }
        val activeBudgets = budgets.filter { budget ->
            if (budget.isCustomPeriod && budget.startDateMillis != null && budget.endDateMillis != null) {
                nowMillis in budget.startDateMillis..budget.endDateMillis
            } else {
                budget.yearMonth == currentYearMonth
            }
        }

        return activeBudgets.map { budget ->
            val range = if (budget.isCustomPeriod && budget.startDateMillis != null && budget.endDateMillis != null) {
                TimeRange(budget.startDateMillis, budget.endDateMillis)
            } else {
                DateRangeResolver.monthRange(budget.yearMonth)
            }
            val spent = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .filter { it.timestampMillis in range.startMillis..range.endMillis }
                .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
                .sumOf { tx ->
                    val from = accountCurrencyMap[tx.accountId] ?: "EGP"
                    CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
                }

            val limit = CurrencyConverter.convertPiasters(budget.limitPiasters, "EGP", targetCurrency)
            val remaining = limit - spent
            val usagePercent = computePercent(spent, limit)

            val (elapsedDays, totalDays, remainingDays) = computePeriodDayMetrics(
                range = range,
                nowMillis = nowMillis,
                isCustom = budget.isCustomPeriod
            )
            val dailyPace = if (spent > 0L) {
                BigDecimal(spent).divide(BigDecimal(elapsedDays), 0, RoundingMode.HALF_UP).toLong()
            } else 0L
            val projectedEnd = dailyPace * totalDays.toLong()
            val depletionDay = if (dailyPace > 0L && limit > 0L && projectedEnd > limit) {
                val estDay = BigDecimal(limit).divide(BigDecimal(dailyPace), 0, RoundingMode.CEILING).toInt()
                estDay.coerceIn(1, totalDays)
            } else null
            val recommendedCap = if (remaining > 0L && remainingDays > 0) {
                BigDecimal(remaining).divide(BigDecimal(remainingDays), 0, RoundingMode.DOWN).toLong()
            } else 0L
            val isExceeded = spent > limit
            val burnRateWarning = !isExceeded && limit > 0L && spent > 0L && projectedEnd > limit

            BudgetEvaluation(
                budgetId = budget.id,
                name = budget.name,
                categoryId = budget.categoryId,
                limitPiasters = limit,
                spentPiasters = spent,
                remainingPiasters = remaining,
                usagePercent = usagePercent,
                isWarning80 = usagePercent >= 80.0 && usagePercent < 100.0,
                isExceeded = isExceeded,
                dailySpendPacePiasters = dailyPace,
                projectedMonthEndPiasters = projectedEnd,
                estimatedDepletionDay = depletionDay,
                recommendedDailyCapPiasters = recommendedCap,
                burnRateWarning = burnRateWarning
            )
        }
    }

    private fun computePeriodDayMetrics(
        range: TimeRange,
        nowMillis: Long,
        isCustom: Boolean
    ): Triple<Int, Int, Int> {
        return if (!isCustom) {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis.coerceIn(range.startMillis, range.endMillis) }
            val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
            val elapsed = cal.get(Calendar.DAY_OF_MONTH).coerceIn(1, totalDays)
            val remaining = (totalDays - elapsed + 1).coerceAtLeast(1)
            Triple(elapsed, totalDays, remaining)
        } else {
            val dayMs = 86_400_000L
            val totalDays = (((range.endMillis - range.startMillis).coerceAtLeast(dayMs)) / dayMs).toInt().coerceAtLeast(1)
            val clampedNow = nowMillis.coerceIn(range.startMillis, range.endMillis)
            val elapsed = ((((clampedNow - range.startMillis) / dayMs) + 1L).toInt()).coerceIn(1, totalDays)
            val remaining = (totalDays - elapsed + 1).coerceAtLeast(1)
            Triple(elapsed, totalDays, remaining)
        }
    }

    fun buildCategoryBreakdown(
        periodExpenses: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        totalExpenses: Long,
        accountCurrencyMap: Map<Long, String> = emptyMap(),
        targetCurrency: String = "EGP"
    ): List<CategoryBreakdownItem> {
        if (periodExpenses.isEmpty()) return emptyList()
        val categoryMap = categories.associateBy { it.id }
        val grouped = periodExpenses.groupBy { it.categoryId ?: -1L }
        return grouped.map { (catId, txList) ->
            val sum = txList.sumOf { tx ->
                val from = accountCurrencyMap[tx.accountId] ?: "EGP"
                CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
            }
            val cat = categoryMap[catId]
            CategoryBreakdownItem(
                categoryId = catId,
                nameAr = cat?.nameAr ?: "غير مصنف",
                nameEn = cat?.nameEn ?: "Uncategorized",
                colorHex = cat?.colorHex ?: "#94A3B8",
                amountPiasters = sum,
                percentage = computePercent(sum, totalExpenses)
            )
        }.sortedByDescending { it.amountPiasters }
    }

    fun buildTrendSeries(
        transactions: List<TransactionEntity>,
        range: TimeRange,
        nowMillis: Long = System.currentTimeMillis(),
        accountCurrencyMap: Map<Long, String> = emptyMap(),
        targetCurrency: String = "EGP"
    ): List<TrendPoint> {
        fun txAmt(tx: TransactionEntity): Long {
            val from = accountCurrencyMap[tx.accountId] ?: "EGP"
            return CurrencyConverter.convertPiasters(tx.amountPiasters, from, targetCurrency)
        }
        val effectiveEnd = minOf(range.endMillis, nowMillis + 86_400_000L)
        val spanDays = ((effectiveEnd - range.startMillis).coerceAtLeast(1L)) / 86_400_000L
        val useMonthlyBuckets = spanDays > 45 || range.startMillis == 0L

        return if (useMonthlyBuckets) {
            val sdf = SimpleDateFormat("MM/yy", Locale.US)
            val grouped = transactions.groupBy { tx ->
                val cal = Calendar.getInstance().apply { timeInMillis = tx.timestampMillis }
                cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            }
            if (grouped.isEmpty()) {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                listOf(TrendPoint(sdf.format(cal.time), 0L, 0L, 0L))
            } else {
                grouped.toSortedMap().map { (_, list) ->
                    val inc = list.filter { it.type == TransactionType.INCOME }.sumOf { txAmt(it) }
                    val exp = list.filter { it.type == TransactionType.EXPENSE }.sumOf { txAmt(it) }
                    val sampleDate = Date(list.first().timestampMillis)
                    TrendPoint(sdf.format(sampleDate), inc, exp, inc - exp)
                }
            }
        } else {
            val sdf = SimpleDateFormat("dd/MM", Locale.US)
            val grouped = transactions.groupBy { tx ->
                DateRangeResolver.startOfDayMillis(tx.timestampMillis)
            }
            if (grouped.isEmpty()) {
                listOf(TrendPoint(sdf.format(Date(nowMillis)), 0L, 0L, 0L))
            } else {
                grouped.toSortedMap().map { (dayStart, list) ->
                    val inc = list.filter { it.type == TransactionType.INCOME }.sumOf { txAmt(it) }
                    val exp = list.filter { it.type == TransactionType.EXPENSE }.sumOf { txAmt(it) }
                    TrendPoint(sdf.format(Date(dayStart)), inc, exp, inc - exp)
                }
            }
        }
    }

    fun computePercent(numerator: Long, denominator: Long): Double {
        if (denominator <= 0L) return 0.0
        return BigDecimal(numerator)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(denominator), 2, RoundingMode.HALF_UP)
            .toDouble()
    }
}
