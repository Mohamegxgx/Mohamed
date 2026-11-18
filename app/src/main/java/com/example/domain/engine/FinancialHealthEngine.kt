package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AnalyticsSummary
import com.example.domain.model.BudgetEvaluation
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionType
import java.util.Calendar

enum class HealthGrade { EXCELLENT, BALANCED, NEEDS_ATTENTION, CRITICAL }

data class HealthPillar(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val score: Int,
    val maxScore: Int,
    val detailAr: String,
    val detailEn: String
)

data class FinancialHealthReport(
    val score: Int,
    val grade: HealthGrade,
    val safeToSpendTodayPiasters: Long,
    val runwayDays: Int,
    val daysRemainingInMonth: Int,
    val upcoming30DaysCommitmentsPiasters: Long,
    val pillars: List<HealthPillar>,
    val adviceAr: String,
    val adviceEn: String
)

data class QuickMerchantTemplate(
    val description: String,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val lastAmountPiasters: Long,
    val usageCount: Int
)

object FinancialHealthEngine {

    private const val DAY_MS = 86_400_000L

    fun analyzeHealth(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        budgetEvaluations: List<BudgetEvaluation>,
        savingsGoals: List<SavingsGoalEntity>,
        installments: List<InstallmentEntity>,
        recurringRules: List<RecurringRuleEntity>,
        debts: List<DebtEntity>,
        monthMetrics: DashboardMetrics,
        analyticsSummary: AnalyticsSummary,
        nowMillis: Long = System.currentTimeMillis(),
        targetCurrency: String = "EGP"
    ): FinancialHealthReport {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDay - currentDay + 1).coerceAtLeast(1)

        val accountCurrencyMap = accounts.associate { it.id to it.currency }
        val horizon30d = nowMillis + 30L * DAY_MS

        val activeInstallments = installments.filter {
            it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount
        }
        val overdueInstallments = activeInstallments.count { it.nextDueDateMillis < nowMillis }
        val upcomingInstallmentsSum = activeInstallments
            .filter { it.nextDueDateMillis <= horizon30d }
            .sumOf { inst ->
                val cur = inst.accountId?.let { accountCurrencyMap[it] } ?: "EGP"
                CurrencyConverter.convertPiasters(inst.installmentAmountPiasters, cur, targetCurrency)
            }

        val upcomingRecurringExpenses = recurringRules
            .filter { it.isActive && it.type == TransactionType.EXPENSE && it.nextRunDateMillis <= horizon30d }
            .sumOf { rule ->
                val cur = accountCurrencyMap[rule.accountId] ?: "EGP"
                CurrencyConverter.convertPiasters(rule.amountPiasters, cur, targetCurrency)
            }

        val upcomingCommitments = upcomingInstallmentsSum + upcomingRecurringExpenses
        val availableFunds = monthMetrics.availableFundsPiasters.coerceAtLeast(0L)

        // Safe-to-Spend Today: prefers active budgets margin; falls back to liquid funds minus 30-day commitments
        val safeBudgetMargin = if (budgetEvaluations.isNotEmpty()) {
            val generalBudget = budgets.firstOrNull { it.categoryId == null }
                ?.let { gb -> budgetEvaluations.firstOrNull { it.budgetId == gb.id } }
            if (generalBudget != null) {
                generalBudget.remainingPiasters.coerceAtLeast(0L)
            } else {
                budgetEvaluations.sumOf { it.remainingPiasters.coerceAtLeast(0L) }
            }
        } else {
            (availableFunds - upcomingCommitments).coerceAtLeast(0L)
        }
        val safeToSpendToday = (safeBudgetMargin / daysRemaining.toLong()).coerceAtLeast(0L)

        // Cash Runway Days
        val avgDailySpend = analyticsSummary.averageDailySpendPiasters
        val runwayDays = when {
            availableFunds <= 0L -> 0
            avgDailySpend <= 0L -> 999
            else -> (availableFunds / avgDailySpend).toInt().coerceIn(0, 999)
        }

        // Pillar 1: Savings Rate (30 pts)
        val hasMonthActivity = monthMetrics.totalIncomePiasters > 0L || monthMetrics.totalExpensesPiasters > 0L
        val savingsScore = when {
            !hasMonthActivity -> 15
            monthMetrics.savingsRatePercent >= 20.0 -> 30
            monthMetrics.savingsRatePercent >= 10.0 -> 24
            monthMetrics.savingsRatePercent >= 1.0 -> 17
            monthMetrics.totalExpensesPiasters <= monthMetrics.totalIncomePiasters -> 12
            else -> 5
        }
        val activeGoalsBonus = if (savingsGoals.any { it.savedAmountPiasters > 0L } && savingsScore < 30) 2 else 0
        val finalSavingsScore = (savingsScore + activeGoalsBonus).coerceAtMost(30)
        val savingsDetailAr = if (!hasMonthActivity) {
            "بانتظار تسجيل معاملات الشهر"
        } else {
            "معدل الادخار ${MoneyFormatter.formatPercent(monthMetrics.savingsRatePercent)}"
        }
        val savingsDetailEn = if (!hasMonthActivity) {
            "Awaiting monthly activity"
        } else {
            "Savings rate ${MoneyFormatter.formatPercent(monthMetrics.savingsRatePercent)}"
        }

        // Pillar 2: Budget Discipline (25 pts)
        val exceededCount = budgetEvaluations.count { it.isExceeded }
        val warningCount = budgetEvaluations.count { it.isWarning80 && !it.isExceeded }
        val budgetScore = if (budgetEvaluations.isEmpty()) {
            16
        } else {
            (25 - (exceededCount * 10) - (warningCount * 5)).coerceIn(4, 25)
        }
        val budgetDetailAr = when {
            budgetEvaluations.isEmpty() -> "لا توجد ميزانيات نشطة بعد"
            exceededCount > 0 -> "$exceededCount ميزانية متجاوزة للحد"
            warningCount > 0 -> "$warningCount ميزانية فوق 80%"
            else -> "جميع الميزانيات ضمن الحد الآمن"
        }
        val budgetDetailEn = when {
            budgetEvaluations.isEmpty() -> "No active budgets set yet"
            exceededCount > 0 -> "$exceededCount budget(s) exceeded"
            warningCount > 0 -> "$warningCount budget(s) above 80%"
            else -> "All budgets within safe bounds"
        }

        // Pillar 3: Obligations & Debt Load (25 pts)
        val totalObligations = monthMetrics.totalObligationsPiasters
        val commitmentRatio = if (availableFunds > 0L) {
            upcomingCommitments.toDouble() / availableFunds.toDouble()
        } else if (upcomingCommitments > 0L) 1.5 else 0.0
        val obligationsScore = when {
            overdueInstallments > 0 -> (12 - overdueInstallments * 3).coerceAtLeast(4)
            upcomingCommitments == 0L && totalObligations == 0L -> 25
            commitmentRatio <= 0.30 -> 24
            commitmentRatio <= 0.60 -> 18
            commitmentRatio <= 0.90 -> 12
            else -> 6
        }
        val obligationsDetailAr = when {
            overdueInstallments > 0 -> "$overdueInstallments أقساط متأخرة السداد"
            upcomingCommitments == 0L -> "لا توجد التزامات مستحقة قريباً"
            else -> "تغطية جيدة لالتزامات 30 يوم"
        }
        val obligationsDetailEn = when {
            overdueInstallments > 0 -> "$overdueInstallments overdue installment(s)"
            upcomingCommitments == 0L -> "No commitments due within 30d"
            else -> "Covered for 30-day commitments"
        }

        // Pillar 4: Liquid Cash Runway (20 pts)
        val runwayScore = when {
            runwayDays >= 90 -> 20
            runwayDays >= 30 -> 16
            runwayDays >= 14 -> 11
            runwayDays >= 7 -> 7
            else -> 3
        }
        val runwayDetailAr = if (runwayDays >= 999) {
            "سيولة مريحة جداً"
        } else {
            "تكفي حوالي $runwayDays يوم من الصرف"
        }
        val runwayDetailEn = if (runwayDays >= 999) {
            "Strong liquid cushion"
        } else {
            "Covers ~$runwayDays days of spending"
        }

        val totalScore = (finalSavingsScore + budgetScore + obligationsScore + runwayScore).coerceIn(0, 100)
        val grade = when {
            totalScore >= 80 -> HealthGrade.EXCELLENT
            totalScore >= 60 -> HealthGrade.BALANCED
            totalScore >= 40 -> HealthGrade.NEEDS_ATTENTION
            else -> HealthGrade.CRITICAL
        }

        val pillars = listOf(
            HealthPillar("savings", "قوة الادخار", "Savings Power", finalSavingsScore, 30, savingsDetailAr, savingsDetailEn),
            HealthPillar("budget", "انضباط الميزانية", "Budget Discipline", budgetScore, 25, budgetDetailAr, budgetDetailEn),
            HealthPillar("obligations", "توازن الالتزامات", "Obligation Load", obligationsScore, 25, obligationsDetailAr, obligationsDetailEn),
            HealthPillar("runway", "أمان السيولة", "Liquidity Runway", runwayScore, 20, runwayDetailAr, runwayDetailEn)
        )

        val weakest = pillars.minByOrNull { it.score.toFloat() / it.maxScore.toFloat() }
        val (adviceAr, adviceEn) = when (weakest?.id) {
            "budget" -> if (budgetEvaluations.isEmpty()) {
                "أضف ميزانية شهرية بسيطة لمراقبة حد الصرف اليومي تلقائياً." to
                    "Add a monthly budget to unlock precision daily safe-to-spend limits."
            } else {
                "راجع الفئات التي اقتربت من الحد الأقصى وحافظ على الحد اليومي الآمن." to
                    "Review categories nearing their limit and stick to today's safe spend."
            }
            "obligations" -> if (overdueInstallments > 0) {
                "لديك أقساط مستحقة؛ سدادها يرفع مؤشر صحتك المالية فوراً." to
                    "Settling overdue installments will immediately lift your financial health score."
            } else {
                "خصص جزءاً من السيولة لتغطية التزامات الشهر القادم قبل الصرف الحر." to
                    "Ring-fence upcoming 30-day commitments before discretionary spending."
            }
            "runway" -> "رفع الرصيد الاحتياطي المتاح ليغطي 30 يوماً يمنحك استقراراً أعلى." to
                "Building a 30-day liquid cash buffer noticeably boosts financial resilience."
            else -> if (totalScore >= 80) {
                "وضعك المالي متوازن وممتاز — استمر بتوجيه الفائض نحو أهداف الادخار." to
                    "Great financial balance — channel surplus cash toward your savings goals."
            } else {
                "توجيه 10–20% من الدخل الشهري للادخار يرفع نقاط صحتك المالية سريعاً." to
                    "Directing 10–20% of monthly income into savings builds momentum quickly."
            }
        }

        return FinancialHealthReport(
            score = totalScore,
            grade = grade,
            safeToSpendTodayPiasters = safeToSpendToday,
            runwayDays = runwayDays,
            daysRemainingInMonth = daysRemaining,
            upcoming30DaysCommitmentsPiasters = upcomingCommitments,
            pillars = pillars,
            adviceAr = adviceAr,
            adviceEn = adviceEn
        )
    }

    fun extractQuickTemplates(
        transactions: List<TransactionEntity>,
        limit: Int = 6
    ): List<QuickMerchantTemplate> {
        if (transactions.isEmpty()) return emptyList()
        val recentFirst = transactions
            .filter {
                (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) &&
                    it.description.trim().length >= 2
            }
            .sortedByDescending { it.timestampMillis }

        val grouped = linkedMapOf<String, MutableList<TransactionEntity>>()
        for (tx in recentFirst) {
            val key = tx.description.trim().lowercase()
            grouped.getOrPut(key) { mutableListOf() }.add(tx)
        }

        return grouped.values
            .sortedWith(
                compareByDescending<List<TransactionEntity>> { it.size }
                    .thenByDescending { it.first().timestampMillis }
            )
            .take(limit)
            .map { group ->
                val latest = group.first()
                QuickMerchantTemplate(
                    description = latest.description.trim(),
                    type = latest.type,
                    accountId = latest.accountId,
                    categoryId = latest.categoryId,
                    lastAmountPiasters = latest.amountPiasters,
                    usageCount = group.size
                )
            }
    }
}
