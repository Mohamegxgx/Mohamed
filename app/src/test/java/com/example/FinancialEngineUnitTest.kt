package com.example

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.engine.AnalyticsEngine
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.DebtDirection
import com.example.domain.model.GoalPreset
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEngineUnitTest {

    private val fixedNow = 1762905600000L // fixed timestamp

    @Test
    fun `money formatter parses Arabic and Latin decimals accurately in piasters`() {
        assertEquals(15075L, MoneyFormatter.parseToPiasters("150.75"))
        assertEquals(15075L, MoneyFormatter.parseToPiasters("١٥٠.٧٥"))
        assertEquals(200000L, MoneyFormatter.parseToPiasters("2,000"))
        assertNull(MoneyFormatter.parseToPiasters("0"))
        assertNull(MoneyFormatter.parseToPiasters("-50"))
        assertNull(MoneyFormatter.parseToPiasters("abc"))
        assertEquals("•••••", MoneyFormatter.formatPiasters(15075L, hideAmounts = true))
        assertEquals("150.75 ج.م", MoneyFormatter.formatPiasters(15075L, language = AppLanguage.AR))
    }

    @Test
    fun `transfers savings contributions and debt payments never inflate income or expenses`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, openingBalancePiasters = 100_000L, currentBalancePiasters = 150_000L),
            AccountEntity(id = 2, name = "Bank", type = AccountType.BANK, openingBalancePiasters = 200_000L, currentBalancePiasters = 250_000L)
        )
        val categories = listOf(
            CategoryEntity(id = 1, nameAr = "طعام", nameEn = "Food", type = TransactionType.EXPENSE),
            CategoryEntity(id = 2, nameAr = "راتب", nameEn = "Salary", type = TransactionType.INCOME)
        )
        val txs = listOf(
            TransactionEntity(id = 1, amountPiasters = 100_000L, type = TransactionType.INCOME, accountId = 2, categoryId = 2, description = "Salary", timestampMillis = fixedNow),
            TransactionEntity(id = 2, amountPiasters = 25_000L, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1, description = "Groceries", timestampMillis = fixedNow),
            TransactionEntity(id = 3, amountPiasters = 30_000L, type = TransactionType.TRANSFER, accountId = 2, targetAccountId = 1, description = "ATM", timestampMillis = fixedNow),
            TransactionEntity(id = 4, amountPiasters = 20_000L, type = TransactionType.SAVINGS_CONTRIBUTION, accountId = 2, savingsGoalId = 1, description = "Emergency", timestampMillis = fixedNow),
            TransactionEntity(id = 5, amountPiasters = 10_000L, type = TransactionType.DEBT_PAYMENT, accountId = 1, debtId = 1, description = "Debt", timestampMillis = fixedNow)
        )
        val goals = listOf(
            SavingsGoalEntity(id = 1, name = "Emergency", presetType = GoalPreset.EMERGENCY, targetAmountPiasters = 100_000L, savedAmountPiasters = 20_000L)
        )
        val installments = listOf(
            InstallmentEntity(id = 1, name = "Phone", creditor = "Store", totalAmountPiasters = 120_000L, installmentAmountPiasters = 10_000L, totalCount = 12, paidCount = 2, nextDueDateMillis = fixedNow + 86_400_000L, frequency = InstallmentFrequency.MONTHLY)
        )
        val debts = listOf(
            DebtEntity(id = 1, personName = "Ali", direction = DebtDirection.OWED_BY_ME, totalAmountPiasters = 50_000L, paidAmountPiasters = 10_000L, dueDateMillis = fixedNow + 86_400_000L)
        )
        val currentYm = DateRangeResolver.currentYearMonth(fixedNow)
        val budgets = listOf(
            BudgetEntity(id = 1, name = "Monthly", categoryId = null, limitPiasters = 80_000L, yearMonth = currentYm)
        )

        val range = DateRangeResolver.resolve(TimeFilter.ALL_TIME, fixedNow)
        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts = accounts,
            transactions = txs,
            categories = categories,
            budgets = budgets,
            savingsGoals = goals,
            installments = installments,
            debts = debts,
            range = range,
            nowMillis = fixedNow
        )

        assertEquals(100_000L, metrics.totalIncomePiasters)
        assertEquals(25_000L, metrics.totalExpensesPiasters)
        assertEquals(75_000L, metrics.netCashFlowPiasters)
        assertEquals(400_000L, metrics.netBalancePiasters)
        assertEquals(25.0, metrics.expenseToIncomeRatioPercent, 0.01)
        assertEquals(75.0, metrics.savingsRatePercent, 0.01)
        // Obligations = 10 remaining installments * 10_000 + 40_000 debt remaining = 140_000
        assertEquals(140_000L, metrics.totalObligationsPiasters)
        // Due within 7 days = 10_000 installment + 40_000 debt = 50_000
        assertEquals(50_000L, metrics.amountsDuePiasters)
        assertEquals(350_000L, metrics.availableFundsPiasters)
        assertEquals(20_000L, metrics.allocatedSavingsPiasters)
    }

    @Test
    fun `budget evaluation detects 80 percent warning and exceeded thresholds`() {
        val currentYm = DateRangeResolver.currentYearMonth(fixedNow)
        val budgets = listOf(
            BudgetEntity(id = 1, name = "Food", categoryId = 1L, limitPiasters = 10_000L, yearMonth = currentYm),
            BudgetEntity(id = 2, name = "Transport", categoryId = 2L, limitPiasters = 10_000L, yearMonth = currentYm)
        )
        val txs = listOf(
            TransactionEntity(id = 1, amountPiasters = 8_500L, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1L, description = "Lunch", timestampMillis = fixedNow),
            TransactionEntity(id = 2, amountPiasters = 12_000L, type = TransactionType.EXPENSE, accountId = 1, categoryId = 2L, description = "Uber", timestampMillis = fixedNow)
        )

        val evals = FinancialEngine.evaluateBudgets(budgets, txs, currentYm, fixedNow)
        val food = evals.first { it.budgetId == 1L }
        val transport = evals.first { it.budgetId == 2L }

        assertTrue(food.isWarning80)
        assertFalse(food.isExceeded)
        assertEquals(1_500L, food.remainingPiasters)

        assertFalse(transport.isWarning80)
        assertTrue(transport.isExceeded)
        assertEquals(-2_000L, transport.remainingPiasters)
    }

    @Test
    fun `analytics engine handles empty data safely without throwing`() {
        val summary = AnalyticsEngine.analyze(
            accounts = emptyList(),
            transactions = emptyList(),
            categories = emptyList(),
            budgets = emptyList(),
            installments = emptyList(),
            nowMillis = fixedNow
        )
        assertEquals(0L, summary.dailySpendPiasters)
        assertEquals(0L, summary.monthlySpendPiasters)
        assertTrue(summary.insights.isNotEmpty())
    }
}
