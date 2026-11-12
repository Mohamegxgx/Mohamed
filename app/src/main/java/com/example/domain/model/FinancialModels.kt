package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    CASH,
    BANK,
    E_WALLET,
    CUSTOM
}

@Serializable
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    SAVINGS_CONTRIBUTION,
    DEBT_PAYMENT
}

@Serializable
enum class TransactionSource {
    MANUAL_ENTRY,
    SMS_IMPORT,
    NOTIFICATION_IMPORT,
    FILE_IMPORT,
    SHARE_IMPORT
}

@Serializable
enum class MessageChannel {
    SHARE_INTENT,
    FILE_IMPORT,
    CLIPBOARD_PASTE,
    NOTIFICATION_LISTENER,
    SMS_INBOX
}

@Serializable
enum class MessageOperationKind {
    INCOMING_TRANSFER,
    OUTGOING_TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    CARD_PURCHASE,
    BILL_PAYMENT,
    TRANSFER_FEE,
    REFUND,
    SALARY,
    INTERNAL_TRANSFER,
    BALANCE_INQUIRY
}

@Serializable
enum class PendingStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@Serializable
enum class ImportedMessageStatus {
    PENDING,
    APPROVED,
    REJECTED,
    IGNORED,
    NON_FINANCIAL,
    DUPLICATE
}

@Serializable
enum class GoalPreset {
    PHONE,
    CAR,
    TRAVEL,
    HOME,
    EMERGENCY,
    CUSTOM
}

@Serializable
enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    PAUSED
}

@Serializable
enum class InstallmentFrequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

@Serializable
enum class InstallmentStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED
}

@Serializable
enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

@Serializable
enum class IncomeSourceType {
    MONTHLY_SALARY,
    WEEKLY_INCOME,
    DAILY_INCOME,
    EXTRA_INCOME,
    FREELANCE,
    BONUS,
    INVESTMENT,
    CUSTOM
}

@Serializable
enum class DebtDirection {
    OWED_TO_ME,
    OWED_BY_ME
}

@Serializable
enum class DebtStatus {
    OPEN,
    PARTIAL,
    SETTLED
}

@Serializable
enum class NotificationType {
    INSTALLMENT_DUE,
    BILL_DUE,
    BUDGET_ALERT,
    EXPENSE_REMINDER,
    SALARY_DUE,
    GOAL_NEAR_COMPLETE,
    FINANCIAL_SUMMARY,
    SMART_READER_ALERT
}

enum class TimeFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    CURRENT_MONTH,
    PREVIOUS_MONTH,
    CURRENT_YEAR,
    PREVIOUS_YEAR,
    ALL_TIME,
    CUSTOM
}

enum class TransactionSort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class ThemePreset {
    EMERALD_OBSIDIAN,
    ROYAL_SAPPHIRE,
    AMETHYST_ONYX,
    DESERT_AMBER
}

enum class SummaryFrequency {
    DAILY,
    WEEKLY
}

enum class AppLanguage(val tag: String) {
    AR("ar"),
    EN("en")
}

data class TimeRange(
    val startMillis: Long,
    val endMillis: Long
)

data class CategoryBreakdownItem(
    val categoryId: Long,
    val nameAr: String,
    val nameEn: String,
    val colorHex: String,
    val amountPiasters: Long,
    val percentage: Double
)

data class TrendPoint(
    val label: String,
    val incomePiasters: Long,
    val expensePiasters: Long,
    val netPiasters: Long
)

data class DashboardMetrics(
    val totalIncomePiasters: Long,
    val totalExpensesPiasters: Long,
    val netBalancePiasters: Long,
    val netCashFlowPiasters: Long,
    val expenseToIncomeRatioPercent: Double,
    val savingsRatePercent: Double,
    val totalObligationsPiasters: Long,
    val amountsDuePiasters: Long,
    val remainingBudgetPiasters: Long,
    val availableFundsPiasters: Long,
    val allocatedSavingsPiasters: Long,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val trendSeries: List<TrendPoint>
)

data class SmartInsight(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val detailAr: String,
    val detailEn: String,
    val severity: InsightSeverity
)

enum class InsightSeverity {
    POSITIVE,
    INFO,
    WARNING,
    CRITICAL
}

data class AnalyticsSummary(
    val dailySpendPiasters: Long,
    val monthlySpendPiasters: Long,
    val yearlySpendPiasters: Long,
    val averageDailySpendPiasters: Long,
    val previousPeriodExpenseDeltaPercent: Double?,
    val previousPeriodIncomeDeltaPercent: Double?,
    val highestCategory: CategoryBreakdownItem?,
    val lowestCategory: CategoryBreakdownItem?,
    val highestSpendDayLabel: String?,
    val highestSpendDayAmountPiasters: Long,
    val savingsRatePercent: Double,
    val projectedEndOfMonthBalancePiasters: Long,
    val insights: List<SmartInsight>
)

data class BudgetEvaluation(
    val budgetId: Long,
    val name: String,
    val categoryId: Long?,
    val limitPiasters: Long,
    val spentPiasters: Long,
    val remainingPiasters: Long,
    val usagePercent: Double,
    val isWarning80: Boolean,
    val isExceeded: Boolean
)
