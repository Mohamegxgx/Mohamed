package com.example.domain.engine

import com.example.data.local.entity.SavingsGoalEntity
import com.example.domain.model.DashboardMetrics
import kotlin.math.ceil
import kotlin.math.roundToLong

enum class WhatIfMode {
    INSTALLMENT_PURCHASE,
    SAVINGS_BOOST
}

data class WhatIfPurchaseInput(
    val cashPricePiasters: Long,
    val months: Int,
    val annualInterestRatePercent: Double,
    val bnplProgramId: Long? = null
)

data class WhatIfPurchaseResult(
    val monthlyInstallmentPiasters: Long,
    val totalPayablePiasters: Long,
    val totalInterestPiasters: Long,
    val currentMonthlyObligationsPiasters: Long,
    val projectedMonthlyObligationsPiasters: Long,
    val projectedInstallmentsToIncomePercent: Double,
    val projectedMonthlySurplusPiasters: Long,
    val projectedSafeToSpendDailyPiasters: Long,
    val selectedProgramUtilizationBeforePercent: Double?,
    val selectedProgramUtilizationAfterPercent: Double?,
    val exceedsBnplLimit: Boolean,
    val burdenAfter: BurdenLevel,
    val feasible: Boolean,
    val verdictAr: String,
    val verdictEn: String,
    val adviceAr: String,
    val adviceEn: String
)

data class WhatIfSavingsInput(
    val monthlyBoostPiasters: Long,
    val targetGoalId: Long? = null
)

data class WhatIfSavingsResult(
    val monthlyBoostPiasters: Long,
    val annualExtraSavedPiasters: Long,
    val baselineMonthlySurplusPiasters: Long,
    val newMonthlySurplusPiasters: Long,
    val baselineSavingsRatePercent: Double,
    val newSavingsRatePercent: Double,
    val targetGoalName: String?,
    val remainingToGoalPiasters: Long,
    val monthsToGoalBefore: Int?,
    val monthsToGoalAfter: Int?,
    val monthsSaved: Int?,
    val adviceAr: String,
    val adviceEn: String
)

object WhatIfSimulatorEngine {

    fun simulatePurchase(
        input: WhatIfPurchaseInput,
        bnplReport: BnplPortfolioReport,
        metrics: DashboardMetrics,
        healthReport: FinancialHealthReport,
        currencyCode: String = "EGP"
    ): WhatIfPurchaseResult {
        val months = input.months.coerceIn(1, 60)
        val principal = input.cashPricePiasters.coerceAtLeast(0L)
        val apr = input.annualInterestRatePercent.coerceIn(0.0, 100.0)
        val interestMultiplier = (apr / 100.0) * (months.toDouble() / 12.0)
        val totalInterest = (principal.toDouble() * interestMultiplier).roundToLong().coerceAtLeast(0L)
        val totalPayable = principal + totalInterest
        val monthlyInst = if (totalPayable <= 0L) 0L else {
            (totalPayable.toDouble() / months.toDouble()).roundToLong().coerceAtLeast(1L)
        }

        val deficit = bnplReport.deficitAnalysis
        val estIncome = deficit.estimatedMonthlyIncomePiasters
        val currentObligations = deficit.monthlyInstallmentsLoadPiasters
        val projectedObligations = currentObligations + monthlyInst
        val projectedIncomeRatio = if (estIncome > 0L) {
            FinancialEngine.computePercent(projectedObligations, estIncome)
        } else if (projectedObligations > 0L) 100.0 else 0.0

        val projectedSurplus = deficit.netMonthlySurplusOrDeficitPiasters - monthlyInst
        val remDays = healthReport.daysRemainingInMonth.coerceAtLeast(1)
        val dailyReduction = monthlyInst / remDays.toLong()
        val projectedSafeDaily = (healthReport.safeToSpendTodayPiasters - dailyReduction).coerceAtLeast(0L)

        val programAnalytics = input.bnplProgramId?.let { id ->
            bnplReport.programAnalytics.firstOrNull { it.program.id == id }
        }
        val utilBefore = programAnalytics?.utilizationPercent
        val utilAfter = programAnalytics?.let { prog ->
            if (prog.program.creditLimitPiasters > 0L) {
                FinancialEngine.computePercent(prog.utilizedLimitPiasters + totalPayable, prog.program.creditLimitPiasters)
            } else null
        }
        val exceedsLimit = programAnalytics != null &&
            programAnalytics.program.creditLimitPiasters > 0L &&
            (programAnalytics.utilizedLimitPiasters + totalPayable) > programAnalytics.program.creditLimitPiasters

        val burdenAfter = when {
            projectedObligations == 0L -> BurdenLevel.NONE
            projectedSurplus < 0L || projectedIncomeRatio >= 40.0 || exceedsLimit -> BurdenLevel.CRITICAL
            projectedIncomeRatio >= 28.0 -> BurdenLevel.HIGH
            projectedIncomeRatio >= 15.0 -> BurdenLevel.MODERATE
            else -> BurdenLevel.HEALTHY
        }
        val feasible = burdenAfter == BurdenLevel.NONE || burdenAfter == BurdenLevel.HEALTHY || burdenAfter == BurdenLevel.MODERATE

        val verdictAr: String
        val verdictEn: String
        val adviceAr: String
        val adviceEn: String

        val instFmtAr = MoneyFormatter.formatPiasters(monthlyInst, currencyCode)
        val surFmtAr = MoneyFormatter.formatPiasters(projectedSurplus, currencyCode, showSign = true)

        when {
            exceedsLimit -> {
                verdictAr = "يتجاوز حد برنامج التقسيط"
                verdictEn = "Exceeds BNPL Credit Limit"
                adviceAr = "إجمالي العملية يتخطى المتاح في البرنامج المختار. قلل المبلغ أو سدد جزءًا كاش مقدمًا."
                adviceEn = "Total commitment exceeds your available credit limit on the selected BNPL program."
            }
            projectedSurplus < 0L -> {
                verdictAr = "تحذير: يسبب عجزًا شهريًا"
                verdictEn = "Warning: Causes Monthly Deficit"
                adviceAr = "إضافة قسط $instFmtAr ستجعل الصافي الشهري $surFmtAr. ننصح بزيادة مدة التقسيط أو التأجيل."
                adviceEn = "Adding $instFmtAr/month shifts monthly net cash flow to $surFmtAr. Consider delaying or increasing tenure."
            }
            burdenAfter == BurdenLevel.CRITICAL || burdenAfter == BurdenLevel.HIGH -> {
                verdictAr = "ضغط التزامات مرتفع (${projectedIncomeRatio.toInt()}%)"
                verdictEn = "High Obligation Burden (${projectedIncomeRatio.toInt()}%)"
                adviceAr = "سترتفع نسبة الأقساط إلى ${MoneyFormatter.formatPercent(projectedIncomeRatio)} من الدخل. احرص على وجود احتياطي طوارئ."
                adviceEn = "Installments rise to ${MoneyFormatter.formatPercent(projectedIncomeRatio)} of income. Keep a cash buffer."
            }
            else -> {
                verdictAr = "خطة آمنة ومستقرة"
                verdictEn = "Safe & Affordable Plan"
                adviceAr = "القسط الشهري ($instFmtAr) متوازن ويترك فائضًا شهريًا متوقعًا $surFmtAr دون إرهاق الميزانية."
                adviceEn = "Monthly payment ($instFmtAr) fits safely with projected surplus $surFmtAr."
            }
        }

        return WhatIfPurchaseResult(
            monthlyInstallmentPiasters = monthlyInst,
            totalPayablePiasters = totalPayable,
            totalInterestPiasters = totalInterest,
            currentMonthlyObligationsPiasters = currentObligations,
            projectedMonthlyObligationsPiasters = projectedObligations,
            projectedInstallmentsToIncomePercent = projectedIncomeRatio,
            projectedMonthlySurplusPiasters = projectedSurplus,
            projectedSafeToSpendDailyPiasters = projectedSafeDaily,
            selectedProgramUtilizationBeforePercent = utilBefore,
            selectedProgramUtilizationAfterPercent = utilAfter,
            exceedsBnplLimit = exceedsLimit,
            burdenAfter = burdenAfter,
            feasible = feasible,
            verdictAr = verdictAr,
            verdictEn = verdictEn,
            adviceAr = adviceAr,
            adviceEn = adviceEn
        )
    }

    fun simulateSavingsBoost(
        input: WhatIfSavingsInput,
        metrics: DashboardMetrics,
        bnplReport: BnplPortfolioReport,
        goals: List<SavingsGoalEntity>,
        currencyCode: String = "EGP"
    ): WhatIfSavingsResult {
        val boost = input.monthlyBoostPiasters.coerceAtLeast(0L)
        val annualExtra = boost * 12L
        val estIncome = bnplReport.deficitAnalysis.estimatedMonthlyIncomePiasters
            .takeIf { it > 0L } ?: metrics.totalIncomePiasters
        val baselineSurplus = bnplReport.deficitAnalysis.netMonthlySurplusOrDeficitPiasters
            .takeIf { estIncome > 0L } ?: metrics.netCashFlowPiasters
        val newSurplus = baselineSurplus + boost

        val baselineRate = if (estIncome > 0L) FinancialEngine.computePercent(baselineSurplus, estIncome) else 0.0
        val newRate = if (estIncome > 0L) FinancialEngine.computePercent(newSurplus, estIncome) else 0.0

        val goal = input.targetGoalId?.let { id -> goals.firstOrNull { it.id == id } } ?: goals.firstOrNull()
        val remaining = goal?.let { (it.targetAmountPiasters - it.savedAmountPiasters).coerceAtLeast(0L) } ?: 0L

        val monthsBefore = if (goal != null && remaining > 0L && baselineSurplus > 0L) {
            ceil(remaining.toDouble() / baselineSurplus.toDouble()).toInt()
        } else null

        val effectiveAfterMonthly = newSurplus.coerceAtLeast(boost)
        val monthsAfter = if (goal != null && remaining > 0L && effectiveAfterMonthly > 0L) {
            ceil(remaining.toDouble() / effectiveAfterMonthly.toDouble()).toInt()
        } else null

        val monthsSaved = if (monthsBefore != null && monthsAfter != null && monthsBefore > monthsAfter) {
            monthsBefore - monthsAfter
        } else null

        val boostFmt = MoneyFormatter.formatPiasters(boost, currencyCode)
        val annualFmt = MoneyFormatter.formatPiasters(annualExtra, currencyCode)
        val adviceAr = if (goal != null && monthsAfter != null) {
            if (monthsSaved != null && monthsSaved > 0) {
                "توفير $boostFmt إضافي شهريًا يختصر الوصول لهدف «${goal.name}» بمقدار $monthsSaved شهر (خلال $monthsAfter شهرًا فقط) ويضيف $annualFmt سنويًا."
            } else {
                "بتخصيص $boostFmt شهريًا، ستصل لهدف «${goal.name}» خلال $monthsAfter شهرًا تقريبًا وتدخر $annualFmt خلال سنة."
            }
        } else {
            "تقليل المصروف أو زيادة الادخار بمقدار $boostFmt شهريًا يحرر لك $annualFmt سنويًا ويرفع معدل ادخارك إلى ${MoneyFormatter.formatPercent(newRate)}."
        }

        val adviceEn = if (goal != null && monthsAfter != null) {
            if (monthsSaved != null && monthsSaved > 0) {
                "Saving an extra $boostFmt/mo cuts '${goal.name}' timeline by $monthsSaved month(s) (down to $monthsAfter months) and adds $annualFmt/year."
            } else {
                "Allocating $boostFmt/mo reaches '${goal.name}' in ~$monthsAfter month(s) and builds $annualFmt in a year."
            }
        } else {
            "Boosting savings by $boostFmt/mo adds $annualFmt per year and lifts your savings rate to ${MoneyFormatter.formatPercent(newRate)}."
        }

        return WhatIfSavingsResult(
            monthlyBoostPiasters = boost,
            annualExtraSavedPiasters = annualExtra,
            baselineMonthlySurplusPiasters = baselineSurplus,
            newMonthlySurplusPiasters = newSurplus,
            baselineSavingsRatePercent = baselineRate,
            newSavingsRatePercent = newRate,
            targetGoalName = goal?.name,
            remainingToGoalPiasters = remaining,
            monthsToGoalBefore = monthsBefore,
            monthsToGoalAfter = monthsAfter,
            monthsSaved = monthsSaved,
            adviceAr = adviceAr,
            adviceEn = adviceEn
        )
    }
}
