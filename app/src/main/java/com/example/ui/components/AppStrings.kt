package com.example.ui.components

import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.DebtDirection
import com.example.domain.model.GoalPreset
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.TimeFilter
import com.example.domain.model.TransactionSort
import com.example.domain.model.TransactionType

object AppStrings {
    fun text(ar: String, en: String, lang: AppLanguage): String = if (lang == AppLanguage.AR) ar else en

    fun timeFilterLabel(filter: TimeFilter, lang: AppLanguage): String = when (filter) {
        TimeFilter.TODAY -> text("اليوم", "Today", lang)
        TimeFilter.YESTERDAY -> text("أمس", "Yesterday", lang)
        TimeFilter.THIS_WEEK -> text("الأسبوع", "Week", lang)
        TimeFilter.CURRENT_MONTH -> text("الشهر الحالي", "This Month", lang)
        TimeFilter.PREVIOUS_MONTH -> text("الشهر السابق", "Last Month", lang)
        TimeFilter.CURRENT_YEAR -> text("السنة الحالية", "This Year", lang)
        TimeFilter.PREVIOUS_YEAR -> text("السنة السابقة", "Last Year", lang)
        TimeFilter.ALL_TIME -> text("كل الوقت", "All Time", lang)
        TimeFilter.CUSTOM -> text("فترة مخصصة", "Custom", lang)
    }

    fun transactionTypeLabel(type: TransactionType, lang: AppLanguage): String = when (type) {
        TransactionType.INCOME -> text("دخل", "Income", lang)
        TransactionType.EXPENSE -> text("مصروف", "Expense", lang)
        TransactionType.TRANSFER -> text("تحويل", "Transfer", lang)
        TransactionType.SAVINGS_CONTRIBUTION -> text("مساهمة ادخار", "Savings", lang)
        TransactionType.DEBT_PAYMENT -> text("سداد دين", "Debt Payment", lang)
    }

    fun accountTypeLabel(type: AccountType, lang: AppLanguage): String = when (type) {
        AccountType.CASH -> text("نقدية (كاش)", "Cash", lang)
        AccountType.BANK -> text("حساب بنكي", "Bank Account", lang)
        AccountType.E_WALLET -> text("محفظة إلكترونية", "E-Wallet", lang)
        AccountType.CUSTOM -> text("حساب مخصص", "Custom Account", lang)
    }

    fun goalPresetLabel(preset: GoalPreset, lang: AppLanguage): String = when (preset) {
        GoalPreset.PHONE -> text("شراء هاتف", "Buy Phone", lang)
        GoalPreset.CAR -> text("شراء سيارة", "Buy Car", lang)
        GoalPreset.TRAVEL -> text("السفر", "Travel", lang)
        GoalPreset.HOME -> text("تجهيز منزل", "Home Setup", lang)
        GoalPreset.EMERGENCY -> text("صندوق الطوارئ", "Emergency Fund", lang)
        GoalPreset.CUSTOM -> text("هدف مخصص", "Custom Goal", lang)
    }

    fun installmentFreqLabel(freq: InstallmentFrequency, lang: AppLanguage): String = when (freq) {
        InstallmentFrequency.WEEKLY -> text("أسبوعي", "Weekly", lang)
        InstallmentFrequency.MONTHLY -> text("شهري", "Monthly", lang)
        InstallmentFrequency.QUARTERLY -> text("ربع سنوي", "Quarterly", lang)
        InstallmentFrequency.YEARLY -> text("سنوي", "Yearly", lang)
    }

    fun installmentStatusLabel(status: InstallmentStatus, lang: AppLanguage): String = when (status) {
        InstallmentStatus.ACTIVE -> text("نشط", "Active", lang)
        InstallmentStatus.PAUSED -> text("موقوف", "Paused", lang)
        InstallmentStatus.COMPLETED -> text("مكتمل", "Completed", lang)
        InstallmentStatus.ARCHIVED -> text("مؤرشف", "Archived", lang)
    }

    fun recurringFreqLabel(freq: RecurringFrequency, lang: AppLanguage): String = when (freq) {
        RecurringFrequency.DAILY -> text("يومي", "Daily", lang)
        RecurringFrequency.WEEKLY -> text("أسبوعي", "Weekly", lang)
        RecurringFrequency.MONTHLY -> text("شهري", "Monthly", lang)
        RecurringFrequency.YEARLY -> text("سنوي", "Yearly", lang)
    }

    fun incomeSourceLabel(src: IncomeSourceType, lang: AppLanguage): String = when (src) {
        IncomeSourceType.MONTHLY_SALARY -> text("راتب شهري", "Monthly Salary", lang)
        IncomeSourceType.WEEKLY_INCOME -> text("دخل أسبوعي", "Weekly Income", lang)
        IncomeSourceType.DAILY_INCOME -> text("دخل يومي", "Daily Income", lang)
        IncomeSourceType.EXTRA_INCOME -> text("دخل إضافي", "Extra Income", lang)
        IncomeSourceType.FREELANCE -> text("عمل حر", "Freelance", lang)
        IncomeSourceType.BONUS -> text("مكافآت", "Bonus", lang)
        IncomeSourceType.INVESTMENT -> text("استثمارات", "Investments", lang)
        IncomeSourceType.CUSTOM -> text("مصدر مخصص", "Custom Source", lang)
    }

    fun debtDirectionLabel(dir: DebtDirection, lang: AppLanguage): String = when (dir) {
        DebtDirection.OWED_TO_ME -> text("مستحق لي عند الآخرين", "Owed to Me", lang)
        DebtDirection.OWED_BY_ME -> text("مستحق عليّ للآخرين", "Owed by Me", lang)
    }

    fun sortLabel(sort: TransactionSort, lang: AppLanguage): String = when (sort) {
        TransactionSort.DATE_DESC -> text("الأحدث أولًا", "Newest First", lang)
        TransactionSort.DATE_ASC -> text("الأقدم أولًا", "Oldest First", lang)
        TransactionSort.AMOUNT_DESC -> text("الأعلى مبلغًا", "Highest Amount", lang)
        TransactionSort.AMOUNT_ASC -> text("الأقل مبلغًا", "Lowest Amount", lang)
    }

    fun txSourceLabel(source: com.example.domain.model.TransactionSource, lang: AppLanguage): String = when (source) {
        com.example.domain.model.TransactionSource.MANUAL_ENTRY -> text("إدخال يدوي", "Manual", lang)
        com.example.domain.model.TransactionSource.SMS_IMPORT -> text("استيراد SMS", "SMS", lang)
        com.example.domain.model.TransactionSource.NOTIFICATION_IMPORT -> text("إشعار مالي", "Notification", lang)
        com.example.domain.model.TransactionSource.FILE_IMPORT -> text("استيراد ملف", "File", lang)
        com.example.domain.model.TransactionSource.SHARE_IMPORT -> text("مشاركة رسالة", "Shared", lang)
    }

    fun operationKindLabel(kind: com.example.domain.model.MessageOperationKind, lang: AppLanguage): String = when (kind) {
        com.example.domain.model.MessageOperationKind.INCOMING_TRANSFER -> text("تحويل وارد", "Incoming Transfer", lang)
        com.example.domain.model.MessageOperationKind.OUTGOING_TRANSFER -> text("تحويل صادر", "Outgoing Transfer", lang)
        com.example.domain.model.MessageOperationKind.DEPOSIT -> text("إيداع", "Deposit", lang)
        com.example.domain.model.MessageOperationKind.WITHDRAWAL -> text("سحب", "Withdrawal", lang)
        com.example.domain.model.MessageOperationKind.CARD_PURCHASE -> text("شراء بالبطاقة", "Card Purchase", lang)
        com.example.domain.model.MessageOperationKind.BILL_PAYMENT -> text("دفع فاتورة", "Bill Payment", lang)
        com.example.domain.model.MessageOperationKind.TRANSFER_FEE -> text("رسوم تحويل", "Transfer Fee", lang)
        com.example.domain.model.MessageOperationKind.REFUND -> text("استرداد مبلغ", "Refund", lang)
        com.example.domain.model.MessageOperationKind.SALARY -> text("استلام راتب", "Salary", lang)
        com.example.domain.model.MessageOperationKind.INTERNAL_TRANSFER -> text("تحويل بين حساباتك", "Internal Transfer", lang)
        com.example.domain.model.MessageOperationKind.BALANCE_INQUIRY -> text("استعلام رصيد", "Balance Inquiry", lang)
    }

    fun channelLabel(channel: com.example.domain.model.MessageChannel, lang: AppLanguage): String = when (channel) {
        com.example.domain.model.MessageChannel.SHARE_INTENT -> text("مشاركة للبرنامج", "Share Sheet", lang)
        com.example.domain.model.MessageChannel.FILE_IMPORT -> text("استيراد من ملف", "File Import", lang)
        com.example.domain.model.MessageChannel.CLIPBOARD_PASTE -> text("لصق نص الرسالة", "Direct Paste", lang)
        com.example.domain.model.MessageChannel.NOTIFICATION_LISTENER -> text("قارئ الإشعارات", "Notifications", lang)
        com.example.domain.model.MessageChannel.SMS_INBOX -> text("رسائل SMS", "SMS Inbox", lang)
    }
}
