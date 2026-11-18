package com.example.domain.engine

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.InsightSeverity
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TransactionType
import kotlin.math.roundToLong

enum class BurdenLevel {
    NONE,
    HEALTHY,
    MODERATE,
    HIGH,
    CRITICAL
}

data class BnplProgramAnalytics(
    val program: BnplProgramEntity,
    val linkedInstallments: List<InstallmentEntity>,
    val activeCount: Int,
    val completedCount: Int,
    val paidPartsCount: Int,
    val remainingPartsCount: Int,
    val monthlyInstallmentLoadPiasters: Long,
    val remainingInstallmentsTotalPiasters: Long,
    val utilizedLimitPiasters: Long,
    val availableLimitPiasters: Long,
    val overLimitPiasters: Long,
    val utilizationPercent: Double,
    val totalPrincipalPiasters: Long,
    val totalRepayablePiasters: Long,
    val totalInterestPiasters: Long,
    val remainingInterestPiasters: Long,
    val effectiveInterestPercent: Double,
    val incomeSharePercent: Double
)

data class DeficitCauseItem(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val detailAr: String,
    val detailEn: String,
    val amountPiasters: Long,
    val shareOfIncomePercent: Double,
    val severity: InsightSeverity
)

data class DeficitCauseAnalysis(
    val estimatedMonthlyIncomePiasters: Long,
    val monthlyInstallmentsLoadPiasters: Long,
    val monthlyBnplInterestCostPiasters: Long,
    val monthlyRecurringExpensesPiasters: Long,
    val monthlyDiscretionarySpendPiasters: Long,
    val totalMonthlyOutflowPiasters: Long,
    val netMonthlySurplusOrDeficitPiasters: Long,
    val hasDeficit: Boolean,
    val installmentsToIncomePercent: Double,
    val burdenLevel: BurdenLevel,
    val causes: List<DeficitCauseItem>,
    val headlineAr: String,
    val headlineEn: String
)

data class BnplPortfolioReport(
    val programAnalytics: List<BnplProgramAnalytics>,
    val unlinkedInstallmentsCount: Int,
    val totalCreditLimitPiasters: Long,
    val totalUtilizedPiasters: Long,
    val totalAvailableLimitPiasters: Long,
    val overallUtilizationPercent: Double,
    val activeInstallmentsCount: Int,
    val totalPaidInstallmentParts: Int,
    val totalRemainingInstallmentParts: Int,
    val totalRemainingObligationPiasters: Long,
    val totalPrincipalPiasters: Long,
    val totalInterestPiasters: Long,
    val remainingInterestPiasters: Long,
    val averageInterestPercent: Double,
    val deficitAnalysis: DeficitCauseAnalysis
)

object BnplAnalysisEngine {

    fun analyze(
        programs: List<BnplProgramEntity>,
        installments: List<InstallmentEntity>,
        recurringRules: List<RecurringRuleEntity>,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        monthMetrics: DashboardMetrics,
        nowMillis: Long = System.currentTimeMillis(),
        targetCurrency: String = "EGP"
    ): BnplPortfolioReport {
        val accountCurrencyMap = accounts.associate { it.id to it.currency }

        fun toTarget(amountPiasters: Long, accountId: Long?): Long {
            val cur = accountId?.let { accountCurrencyMap[it] } ?: "EGP"
            return CurrencyConverter.convertPiasters(amountPiasters, cur, targetCurrency)
        }

        // 1. Estimated Monthly Income: greater of actual current month income or normalized active recurring income rules
        val recurringMonthlyIncome = recurringRules
            .filter { it.isActive && it.type == TransactionType.INCOME }
            .sumOf { normalizeRecurringToMonthly(toTarget(it.amountPiasters, it.accountId), it.frequency) }
        val estimatedMonthlyIncome = maxOf(monthMetrics.totalIncomePiasters, recurringMonthlyIncome)

        // 2. Analyze each BNPL program
        val activeNonArchivedInstallments = installments.filter { it.status != InstallmentStatus.ARCHIVED }
        val programAnalyticsList = programs.map { program ->
            val linked = activeNonArchivedInstallments.filter { it.bnplProgramId == program.id }
            val activeLinked = linked.filter { it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount }
            val completedLinked = linked.count { it.status == InstallmentStatus.COMPLETED || it.paidCount >= it.totalCount }

            val paidParts = linked.sumOf { it.paidCount.coerceAtLeast(0) }
            val remainingParts = activeLinked.sumOf { (it.totalCount - it.paidCount).coerceAtLeast(0) }

            val monthlyLoad = activeLinked.sumOf { inst ->
                normalizeInstallmentToMonthly(toTarget(inst.installmentAmountPiasters, inst.accountId), inst.frequency)
            }

            val remainingInstallmentsTotal = activeLinked.sumOf { inst ->
                val remCount = (inst.totalCount - inst.paidCount).coerceAtLeast(0).toLong()
                toTarget(remCount * inst.installmentAmountPiasters, inst.accountId)
            }

            val manualUsed = CurrencyConverter.convertPiasters(
                program.manualUsedPiasters.coerceAtLeast(0L),
                "EGP",
                targetCurrency
            )
            val utilized = remainingInstallmentsTotal + manualUsed
            val creditLimit = CurrencyConverter.convertPiasters(
                program.creditLimitPiasters.coerceAtLeast(0L),
                "EGP",
                targetCurrency
            )
            val available = (creditLimit - utilized).coerceAtLeast(0L)
            val overLimit = (utilized - creditLimit).coerceAtLeast(0L)
            val utilPct = if (creditLimit > 0L) (utilized.toDouble() * 100.0 / creditLimit.toDouble()) else 0.0

            val totalRepayable = linked.sumOf { toTarget(it.totalAmountPiasters, it.accountId) }
            val totalPrincipal = linked.sumOf { inst ->
                val effectivePrincipal = if (inst.principalAmountPiasters > 0L) inst.principalAmountPiasters else inst.totalAmountPiasters
                toTarget(effectivePrincipal, inst.accountId)
            }
            val totalInterest = (totalRepayable - totalPrincipal).coerceAtLeast(0L)
            val remainingInterest = activeLinked.sumOf { inst ->
                val totalAmt = toTarget(inst.totalAmountPiasters, inst.accountId)
                val principal = toTarget(
                    if (inst.principalAmountPiasters > 0L) inst.principalAmountPiasters else inst.totalAmountPiasters,
                    inst.accountId
                )
                val instInterest = (totalAmt - principal).coerceAtLeast(0L)
                val totalCnt = inst.totalCount.coerceAtLeast(1)
                val remCnt = (inst.totalCount - inst.paidCount).coerceIn(0, totalCnt)
                ((instInterest.toDouble() * remCnt.toDouble()) / totalCnt.toDouble()).roundToLong()
            }
            val effectiveInterestPct = if (totalPrincipal > 0L) {
                (totalInterest.toDouble() * 100.0 / totalPrincipal.toDouble())
            } else 0.0
            val incomeSharePct = if (estimatedMonthlyIncome > 0L) {
                (monthlyLoad.toDouble() * 100.0 / estimatedMonthlyIncome.toDouble())
            } else 0.0

            BnplProgramAnalytics(
                program = program,
                linkedInstallments = linked,
                activeCount = activeLinked.size,
                completedCount = completedLinked,
                paidPartsCount = paidParts,
                remainingPartsCount = remainingParts,
                monthlyInstallmentLoadPiasters = monthlyLoad,
                remainingInstallmentsTotalPiasters = remainingInstallmentsTotal,
                utilizedLimitPiasters = utilized,
                availableLimitPiasters = available,
                overLimitPiasters = overLimit,
                utilizationPercent = utilPct,
                totalPrincipalPiasters = totalPrincipal,
                totalRepayablePiasters = totalRepayable,
                totalInterestPiasters = totalInterest,
                remainingInterestPiasters = remainingInterest,
                effectiveInterestPercent = effectiveInterestPct,
                incomeSharePercent = incomeSharePct
            )
        }

        // 3. Portfolio-wide installment + BNPL totals (includes unlinked installments for income-burden & deficit accuracy)
        val allActiveInstallments = activeNonArchivedInstallments.filter {
            it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount
        }
        val unlinkedActiveCount = allActiveInstallments.count { inst ->
            inst.bnplProgramId == null || programs.none { it.id == inst.bnplProgramId }
        }
        val monthlyInstallmentsLoadAll = allActiveInstallments.sumOf { inst ->
            normalizeInstallmentToMonthly(toTarget(inst.installmentAmountPiasters, inst.accountId), inst.frequency)
        }
        val monthlyInterestCostAll = allActiveInstallments.sumOf { inst ->
            val totalAmt = toTarget(inst.totalAmountPiasters, inst.accountId)
            val principal = toTarget(
                if (inst.principalAmountPiasters > 0L) inst.principalAmountPiasters else inst.totalAmountPiasters,
                inst.accountId
            )
            val instInterest = (totalAmt - principal).coerceAtLeast(0L)
            val perInstallmentInterest = instInterest.toDouble() / inst.totalCount.coerceAtLeast(1).toDouble()
            normalizeInstallmentToMonthly(perInstallmentInterest.roundToLong(), inst.frequency)
        }

        val totalCreditLimit = programAnalyticsList.sumOf {
            CurrencyConverter.convertPiasters(it.program.creditLimitPiasters.coerceAtLeast(0L), "EGP", targetCurrency)
        }
        val totalUtilized = programAnalyticsList.sumOf { it.utilizedLimitPiasters }
        val totalAvailable = (totalCreditLimit - totalUtilized).coerceAtLeast(0L)
        val overallUtilPct = if (totalCreditLimit > 0L) {
            (totalUtilized.toDouble() * 100.0 / totalCreditLimit.toDouble())
        } else 0.0

        val totalPaidPartsAll = activeNonArchivedInstallments.sumOf { it.paidCount.coerceAtLeast(0) }
        val totalRemainingPartsAll = allActiveInstallments.sumOf { (it.totalCount - it.paidCount).coerceAtLeast(0) }
        val totalRemainingObligationAll = allActiveInstallments.sumOf { inst ->
            val rem = (inst.totalCount - itRemainingCountZeroGuard(inst)).coerceAtLeast(0).toLong()
            toTarget(rem * inst.installmentAmountPiasters, inst.accountId)
        } + programAnalyticsList.sumOf {
            CurrencyConverter.convertPiasters(it.program.manualUsedPiasters.coerceAtLeast(0L), "EGP", targetCurrency)
        }

        val totalPrincipalAll = activeNonArchivedInstallments.sumOf { inst ->
            val effectivePrincipal = if (inst.principalAmountPiasters > 0L) inst.principalAmountPiasters else inst.totalAmountPiasters
            toTarget(effectivePrincipal, inst.accountId)
        }
        val totalRepayableAll = activeNonArchivedInstallments.sumOf { toTarget(it.totalAmountPiasters, it.accountId) }
        val totalInterestAll = (totalRepayableAll - totalPrincipalAll).coerceAtLeast(0L)
        val remainingInterestAll = allActiveInstallments.sumOf { inst ->
            val totalAmt = toTarget(inst.totalAmountPiasters, inst.accountId)
            val principal = toTarget(
                if (inst.principalAmountPiasters > 0L) inst.principalAmountPiasters else inst.totalAmountPiasters,
                inst.accountId
            )
            val instInterest = (totalAmt - principal).coerceAtLeast(0L)
            val totalCnt = inst.totalCount.coerceAtLeast(1)
            val remCnt = (inst.totalCount - inst.paidCount).coerceIn(0, totalCnt)
            ((instInterest.toDouble() * remCnt.toDouble()) / totalCnt.toDouble()).roundToLong()
        }
        val avgInterestPct = if (totalPrincipalAll > 0L) {
            (totalInterestAll.toDouble() * 100.0 / totalPrincipalAll.toDouble())
        } else 0.0

        // 4. Monthly Expense Decomposition & Financial Deficit Root-Cause Analysis
        val monthRange = DateRangeResolver.resolve(com.example.domain.model.TimeFilter.CURRENT_MONTH, nowMillis)
        val monthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE &&
                it.timestampMillis in monthRange.startMillis..monthRange.endMillis
        }
        val monthRecurringTxSum = monthExpenses
            .filter { it.recurringRuleId != null && it.installmentId == null }
            .sumOf { toTarget(it.amountPiasters, it.accountId) }
        val recurringRulesMonthlySum = recurringRules
            .filter { it.isActive && it.type == TransactionType.EXPENSE }
            .sumOf { normalizeRecurringToMonthly(toTarget(it.amountPiasters, it.accountId), it.frequency) }
        val monthlyRecurringExpenses = maxOf(monthRecurringTxSum, recurringRulesMonthlySum)

        val monthlyDiscretionarySpend = monthExpenses
            .filter { it.installmentId == null && it.recurringRuleId == null }
            .sumOf { toTarget(it.amountPiasters, it.accountId) }

        val totalMonthlyOutflow = monthlyInstallmentsLoadAll + monthlyRecurringExpenses + monthlyDiscretionarySpend
        val netMonthlySurplusOrDeficit = estimatedMonthlyIncome - totalMonthlyOutflow
        val installmentsToIncomePct = if (estimatedMonthlyIncome > 0L) {
            (monthlyInstallmentsLoadAll.toDouble() * 100.0 / estimatedMonthlyIncome.toDouble())
        } else if (monthlyInstallmentsLoadAll > 0L) 100.0 else 0.0

        val burdenLevel = when {
            monthlyInstallmentsLoadAll <= 0L -> BurdenLevel.NONE
            installmentsToIncomePct < 20.0 -> BurdenLevel.HEALTHY
            installmentsToIncomePct < 35.0 -> BurdenLevel.MODERATE
            installmentsToIncomePct < 50.0 -> BurdenLevel.HIGH
            else -> BurdenLevel.CRITICAL
        }

        val hasDeficit = (estimatedMonthlyIncome > 0L && netMonthlySurplusOrDeficit < 0L) ||
            (estimatedMonthlyIncome == 0L && totalMonthlyOutflow > 0L) ||
            burdenLevel == BurdenLevel.HIGH ||
            burdenLevel == BurdenLevel.CRITICAL

        val causes = buildDeficitCauses(
            estimatedMonthlyIncome = estimatedMonthlyIncome,
            monthlyInstallmentsLoad = monthlyInstallmentsLoadAll,
            monthlyInterestCost = monthlyInterestCostAll,
            monthlyRecurringExpenses = monthlyRecurringExpenses,
            monthlyDiscretionarySpend = monthlyDiscretionarySpend,
            installmentsToIncomePct = installmentsToIncomePct,
            burdenLevel = burdenLevel,
            programAnalytics = programAnalyticsList,
            netSurplusOrDeficit = netMonthlySurplusOrDeficit,
            currency = targetCurrency
        )

        val (headlineAr, headlineEn) = when {
            estimatedMonthlyIncome <= 0L && totalMonthlyOutflow > 0L ->
                "لم يُسجّل دخل شهري بعد لتغطية التزامات بقيمة ${MoneyFormatter.formatPiasters(totalMonthlyOutflow, targetCurrency, com.example.domain.model.AppLanguage.AR)}" to
                    "No monthly income logged yet against ${MoneyFormatter.formatPiasters(totalMonthlyOutflow, targetCurrency, com.example.domain.model.AppLanguage.EN)} in obligations"
            netMonthlySurplusOrDeficit < 0L -> {
                val deficitStrAr = MoneyFormatter.formatPiasters(-netMonthlySurplusOrDeficit, targetCurrency, com.example.domain.model.AppLanguage.AR)
                val deficitStrEn = MoneyFormatter.formatPiasters(-netMonthlySurplusOrDeficit, targetCurrency, com.example.domain.model.AppLanguage.EN)
                "يوجد عجز شهري متوقع قدره $deficitStrAr — راجع الأسباب أدناه" to
                    "Projected monthly deficit of $deficitStrEn — see primary drivers below"
            }
            burdenLevel == BurdenLevel.CRITICAL || burdenLevel == BurdenLevel.HIGH ->
                "ضغط أقساط مرتفع (${MoneyFormatter.formatPercent(installmentsToIncomePct)} من الدخل) يقلص هامش الأمان" to
                    "High installment burden (${MoneyFormatter.formatPercent(installmentsToIncomePct)} of income) squeezes cashflow"
            totalMonthlyOutflow == 0L ->
                "أضف ليمت برامج التقسيط وأقساطك ودخلك لتفعيل التحليل الكامل" to
                    "Add BNPL limits, installments, and income for full deficit analysis"
            else ->
                "توازن جيد — التزاماتك تحت السيطرة مع فائض شهري متوقع" to
                    "Balanced cashflow — commitments are under control with a monthly surplus"
        }

        val deficitAnalysis = DeficitCauseAnalysis(
            estimatedMonthlyIncomePiasters = estimatedMonthlyIncome,
            monthlyInstallmentsLoadPiasters = monthlyInstallmentsLoadAll,
            monthlyBnplInterestCostPiasters = monthlyInterestCostAll,
            monthlyRecurringExpensesPiasters = monthlyRecurringExpenses,
            monthlyDiscretionarySpendPiasters = monthlyDiscretionarySpend,
            totalMonthlyOutflowPiasters = totalMonthlyOutflow,
            netMonthlySurplusOrDeficitPiasters = netMonthlySurplusOrDeficit,
            hasDeficit = hasDeficit,
            installmentsToIncomePercent = installmentsToIncomePct,
            burdenLevel = burdenLevel,
            causes = causes,
            headlineAr = headlineAr,
            headlineEn = headlineEn
        )

        return BnplPortfolioReport(
            programAnalytics = programAnalyticsList,
            unlinkedInstallmentsCount = unlinkedActiveCount,
            totalCreditLimitPiasters = totalCreditLimit,
            totalUtilizedPiasters = totalUtilized,
            totalAvailableLimitPiasters = totalAvailable,
            overallUtilizationPercent = overallUtilPct,
            activeInstallmentsCount = allActiveInstallments.size,
            totalPaidInstallmentParts = totalPaidPartsAll,
            totalRemainingInstallmentParts = totalRemainingPartsAll,
            totalRemainingObligationPiasters = totalRemainingObligationAll,
            totalPrincipalPiasters = totalPrincipalAll,
            totalInterestPiasters = totalInterestAll,
            remainingInterestPiasters = remainingInterestAll,
            averageInterestPercent = avgInterestPct,
            deficitAnalysis = deficitAnalysis
        )
    }

    private fun itRemainingCountZeroGuard(inst: InstallmentEntity): Int {
        return inst.paidCount.coerceIn(0, inst.totalCount)
    }

    private fun buildDeficitCauses(
        estimatedMonthlyIncome: Long,
        monthlyInstallmentsLoad: Long,
        monthlyInterestCost: Long,
        monthlyRecurringExpenses: Long,
        monthlyDiscretionarySpend: Long,
        installmentsToIncomePct: Double,
        burdenLevel: BurdenLevel,
        programAnalytics: List<BnplProgramAnalytics>,
        netSurplusOrDeficit: Long,
        currency: String
    ): List<DeficitCauseItem> {
        val items = mutableListOf<DeficitCauseItem>()

        fun share(amount: Long): Double = if (estimatedMonthlyIncome > 0L) {
            (amount.toDouble() * 100.0 / estimatedMonthlyIncome.toDouble())
        } else 0.0

        // 1. Installments Burden
        if (monthlyInstallmentsLoad > 0L) {
            val severity = when (burdenLevel) {
                BurdenLevel.CRITICAL -> InsightSeverity.CRITICAL
                BurdenLevel.HIGH -> InsightSeverity.WARNING
                BurdenLevel.MODERATE -> InsightSeverity.INFO
                else -> InsightSeverity.POSITIVE
            }
            val topProgram = programAnalytics.maxByOrNull { it.monthlyInstallmentLoadPiasters }
            val topProgNoteAr = topProgram?.takeIf { it.monthlyInstallmentLoadPiasters > 0L }
                ?.let { " (أعلاها ${it.program.nameAr})" } ?: ""
            val topProgNoteEn = topProgram?.takeIf { it.monthlyInstallmentLoadPiasters > 0L }
                ?.let { " (highest: ${it.program.nameEn})" } ?: ""
            val detailAr = when (burdenLevel) {
                BurdenLevel.CRITICAL, BurdenLevel.HIGH ->
                    "الأقساط تلتهم ${MoneyFormatter.formatPercent(installmentsToIncomePct)} من الدخل$topProgNoteAr — يوصى بتجميد أي تقسيط جديد حتى تنخفض تحت 25%."
                BurdenLevel.MODERATE ->
                    "الأقساط تمثل ${MoneyFormatter.formatPercent(installmentsToIncomePct)} من الدخل$topProgNoteAr — اقتربت من الحد التحذيري 35%."
                else ->
                    "الأقساط تمثل ${MoneyFormatter.formatPercent(installmentsToIncomePct)} من الدخل$topProgNoteAr وهي ضمن النطاق الآمن (<20%)."
            }
            val detailEn = when (burdenLevel) {
                BurdenLevel.CRITICAL, BurdenLevel.HIGH ->
                    "Installments consume ${MoneyFormatter.formatPercent(installmentsToIncomePct)} of income$topProgNoteEn — pause new BNPL purchases until under 25%."
                BurdenLevel.MODERATE ->
                    "Installments take ${MoneyFormatter.formatPercent(installmentsToIncomePct)} of income$topProgNoteEn — nearing the 35% caution threshold."
                else ->
                    "Installments take ${MoneyFormatter.formatPercent(installmentsToIncomePct)} of income$topProgNoteEn — within healthy limits."
            }
            items += DeficitCauseItem(
                id = "installments_burden",
                titleAr = "التزامات الأقساط الشهرية",
                titleEn = "Monthly Installments Load",
                detailAr = detailAr,
                detailEn = detailEn,
                amountPiasters = monthlyInstallmentsLoad,
                shareOfIncomePercent = installmentsToIncomePct,
                severity = severity
            )
        }

        // 2. Hidden BNPL Interest / Financing Cost
        if (monthlyInterestCost > 0L) {
            val interestShare = share(monthlyInterestCost)
            val interestSeverity = if (interestShare >= 8.0) InsightSeverity.CRITICAL else InsightSeverity.WARNING
            val formattedAr = MoneyFormatter.formatPiasters(monthlyInterestCost, currency, com.example.domain.model.AppLanguage.AR)
            val formattedEn = MoneyFormatter.formatPiasters(monthlyInterestCost, currency, com.example.domain.model.AppLanguage.EN)
            items += DeficitCauseItem(
                id = "bnpl_interest",
                titleAr = "تكلفة فوائد وعوائد التقسيط",
                titleEn = "Installment Interest & Financing Cost",
                detailAr = "تدفع نحو $formattedAr شهرياً كفوائد فوق السعر الكاش؛ السداد المعجل للأقساط الأعلى فائدة يوفر سيولة مباشرة.",
                detailEn = "You pay ~$formattedEn/mo in financing markup above cash price; paying off highest-APR plans saves immediate cash.",
                amountPiasters = monthlyInterestCost,
                shareOfIncomePercent = interestShare,
                severity = interestSeverity
            )
        }

        // 3. High Utilization BNPL Credit Limits
        val maxedPrograms = programAnalytics.filter { it.program.creditLimitPiasters > 0L && it.utilizationPercent >= 75.0 }
        if (maxedPrograms.isNotEmpty()) {
            val namesAr = maxedPrograms.joinToString("، ") { "${it.program.nameAr} (${it.utilizationPercent.toInt()}%)" }
            val namesEn = maxedPrograms.joinToString(", ") { "${it.program.nameEn} (${it.utilizationPercent.toInt()}%)" }
            items += DeficitCauseItem(
                id = "high_limit_utilization",
                titleAr = "تشبع الليمت الائتماني للبرامج",
                titleEn = "High BNPL Limit Utilization",
                detailAr = "استهلكت أكثر من 75% من الليمت في: $namesAr — قلة المساحة المتاحة ترفع مخاطر العجز عند الطوارئ.",
                detailEn = "Over 75% credit limit used on: $namesEn — low headroom increases cashflow vulnerability.",
                amountPiasters = maxedPrograms.sumOf { it.utilizedLimitPiasters },
                shareOfIncomePercent = maxedPrograms.maxOf { it.utilizationPercent },
                severity = if (maxedPrograms.any { it.utilizationPercent >= 90.0 }) InsightSeverity.CRITICAL else InsightSeverity.WARNING
            )
        }

        // 4. Recurring Fixed Bills
        if (monthlyRecurringExpenses > 0L) {
            val recShare = share(monthlyRecurringExpenses)
            items += DeficitCauseItem(
                id = "recurring_commitments",
                titleAr = "المصروفات الثابتة والفواتير المتكررة",
                titleEn = "Fixed Recurring Bills",
                detailAr = "الفواتير والالتزامات الثابتة تستهلك ${MoneyFormatter.formatPercent(recShare)} من الدخل الشهري.",
                detailEn = "Recurring fixed bills take ${MoneyFormatter.formatPercent(recShare)} of monthly income.",
                amountPiasters = monthlyRecurringExpenses,
                shareOfIncomePercent = recShare,
                severity = if (recShare >= 40.0) InsightSeverity.WARNING else InsightSeverity.INFO
            )
        }

        // 5. Discretionary / Variable Overspending
        if (monthlyDiscretionarySpend > 0L) {
            val discShare = share(monthlyDiscretionarySpend)
            val discSeverity = when {
                netSurplusOrDeficit < 0L && monthlyDiscretionarySpend >= monthlyInstallmentsLoad -> InsightSeverity.CRITICAL
                discShare >= 45.0 -> InsightSeverity.WARNING
                else -> InsightSeverity.INFO
            }
            items += DeficitCauseItem(
                id = "discretionary_spend",
                titleAr = "الإنفاق اليومي المتغير",
                titleEn = "Variable Daily Spending",
                detailAr = if (discSeverity == InsightSeverity.CRITICAL || discSeverity == InsightSeverity.WARNING) {
                    "الصرف اليومي وصل إلى ${MoneyFormatter.formatPercent(discShare)} من الدخل وهو مساهم رئيسي في الضغط المالي الحالي."
                } else {
                    "الصرف اليومي يمثل ${MoneyFormatter.formatPercent(discShare)} من الدخل الشهري."
                },
                detailEn = if (discSeverity == InsightSeverity.CRITICAL || discSeverity == InsightSeverity.WARNING) {
                    "Variable spend reached ${MoneyFormatter.formatPercent(discShare)} of income and is a primary deficit driver."
                } else {
                    "Variable daily spending represents ${MoneyFormatter.formatPercent(discShare)} of monthly income."
                },
                amountPiasters = monthlyDiscretionarySpend,
                shareOfIncomePercent = discShare,
                severity = discSeverity
            )
        }

        // Sort critical & highest financial impact causes first
        return items.sortedWith(
            compareByDescending<DeficitCauseItem> {
                when (it.severity) {
                    InsightSeverity.CRITICAL -> 4
                    InsightSeverity.WARNING -> 3
                    InsightSeverity.INFO -> 2
                    InsightSeverity.POSITIVE -> 1
                }
            }.thenByDescending { it.amountPiasters }
        )
    }

    private fun normalizeInstallmentToMonthly(amountPiasters: Long, frequency: InstallmentFrequency): Long = when (frequency) {
        InstallmentFrequency.WEEKLY -> (amountPiasters.toDouble() * 4.333).roundToLong()
        InstallmentFrequency.MONTHLY -> amountPiasters
        InstallmentFrequency.QUARTERLY -> (amountPiasters.toDouble() / 3.0).roundToLong()
        InstallmentFrequency.YEARLY -> (amountPiasters.toDouble() / 12.0).roundToLong()
    }

    private fun normalizeRecurringToMonthly(amountPiasters: Long, frequency: RecurringFrequency): Long = when (frequency) {
        RecurringFrequency.DAILY -> amountPiasters * 30L
        RecurringFrequency.WEEKLY -> (amountPiasters.toDouble() * 4.333).roundToLong()
        RecurringFrequency.MONTHLY -> amountPiasters
        RecurringFrequency.YEARLY -> (amountPiasters.toDouble() / 12.0).roundToLong()
    }
}
