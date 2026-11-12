package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.entity.NotificationEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.DateRangeResolver
import com.example.domain.engine.FinancialEngine
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.GoalStatus
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.TransactionType
import com.example.widget.ElbagorryWidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class FinancialCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = MoneyRepository(db)
            val prefsRepo = UserPreferencesRepository(applicationContext)
            val prefs = prefsRepo.preferences.first()
            val lang = prefs.language
            val now = System.currentTimeMillis()

            repo.ensureSeeded()
            // 1. Safely execute due recurring rules with unique cycle idempotency
            repo.executeDueRecurringRules(now)

            val existingNotifications = repo.getAllNotificationsOnce()
            val oneDayAgo = now - 24L * 60L * 60L * 1000L

            fun recentlyNotified(type: NotificationType, relatedId: Long?): Boolean {
                return existingNotifications.any {
                    it.type == type && it.relatedEntityId == relatedId && it.timestampMillis >= oneDayAgo
                }
            }

            val accounts = repo.getAllAccountsOnce()
            val defaultAccountId = accounts.firstOrNull { !it.isArchived }?.id ?: 1L
            val installments = repo.getAllInstallmentsOnce()

            // 2. Interactive Installment Due Notifications (with Mark Paid & View actions)
            if (prefs.notifyInstallments) {
                val window = now + 3L * 24L * 60L * 60L * 1000L
                installments
                    .filter { it.status == InstallmentStatus.ACTIVE && it.paidCount < it.totalCount && it.nextDueDateMillis <= window }
                    .forEach { inst ->
                        if (!recentlyNotified(NotificationType.INSTALLMENT_DUE, inst.id)) {
                            val title = if (inst.nextDueDateMillis < now) {
                                if (lang == AppLanguage.AR) "قسط متأخر: ${inst.name}" else "Overdue Installment: ${inst.name}"
                            } else {
                                if (lang == AppLanguage.AR) "موعد قسط قريب: ${inst.name}" else "Upcoming Installment: ${inst.name}"
                            }
                            val amountFormatted = MoneyFormatter.formatPiasters(inst.installmentAmountPiasters, prefs.currencyCode, lang, prefs.hideAmounts)
                            val msg = if (lang == AppLanguage.AR) "المبلغ المستحق: $amountFormatted" else "Due amount: $amountFormatted"
                            val dbNotifId = repo.addNotification(
                                NotificationEntity(
                                    type = NotificationType.INSTALLMENT_DUE,
                                    title = title,
                                    message = msg,
                                    relatedEntityId = inst.id,
                                    timestampMillis = now
                                )
                            )
                            NotificationHelper.showInstallmentDueNotification(
                                context = applicationContext,
                                notificationId = (1000 + inst.id).toInt(),
                                dbNotificationId = dbNotifId,
                                installmentId = inst.id,
                                accountId = inst.accountId ?: defaultAccountId,
                                title = title,
                                message = msg,
                                lang = lang
                            )
                        }
                    }
            }

            // 3. Upcoming recurring bills & salaries
            val rules = repo.getAllRecurringRulesOnce().filter { it.isActive }
            rules.forEach { rule ->
                val warnWindow = now + rule.notifyDaysBefore.toLong() * 24L * 60L * 60L * 1000L
                if (rule.nextRunDateMillis in now..warnWindow) {
                    val notifType = if (rule.type == TransactionType.INCOME) NotificationType.SALARY_DUE else NotificationType.BILL_DUE
                    val enabled = if (notifType == NotificationType.SALARY_DUE) prefs.notifySalary else prefs.notifyBills
                    if (enabled && !recentlyNotified(notifType, rule.id)) {
                        val title = if (notifType == NotificationType.SALARY_DUE) {
                            if (lang == AppLanguage.AR) "موعد دخل متوقع: ${rule.title}" else "Expected Income: ${rule.title}"
                        } else {
                            if (lang == AppLanguage.AR) "فاتورة مستحقة قريبًا: ${rule.title}" else "Upcoming Bill: ${rule.title}"
                        }
                        val amountFmt = MoneyFormatter.formatPiasters(rule.amountPiasters, prefs.currencyCode, lang, prefs.hideAmounts)
                        val msg = if (lang == AppLanguage.AR) "القيمة: $amountFmt" else "Amount: $amountFmt"
                        repo.addNotification(
                            NotificationEntity(
                                type = notifType,
                                title = title,
                                message = msg,
                                relatedEntityId = rule.id,
                                timestampMillis = now
                            )
                        )
                        NotificationHelper.showNotification(
                            context = applicationContext,
                            notificationId = (2000 + rule.id).toInt(),
                            title = title,
                            message = msg,
                            destination = NotificationHelper.DEST_PLANNING,
                            includeQuickAddAction = true,
                            lang = lang
                        )
                    }
                }
            }

            val budgets = repo.getAllBudgetsOnce()
            val txs = repo.getAllTransactionsOnce()

            // 4. Budget alerts (>= 80% / exceeded)
            if (prefs.notifyBudgets) {
                val evaluations = FinancialEngine.evaluateBudgets(
                    budgets = budgets,
                    transactions = txs,
                    currentYearMonth = DateRangeResolver.currentYearMonth(now),
                    nowMillis = now
                )
                evaluations.filter { it.isWarning80 || it.isExceeded }.forEach { eval ->
                    if (!recentlyNotified(NotificationType.BUDGET_ALERT, eval.budgetId)) {
                        val title = if (eval.isExceeded) "تجاوز ميزانية ${eval.name}" else "تنبيه ميزانية ${eval.name}"
                        val msg = "نسبة الاستهلاك الحالية ${MoneyFormatter.formatPercent(eval.usagePercent)}"
                        repo.addNotification(
                            NotificationEntity(
                                type = NotificationType.BUDGET_ALERT,
                                title = title,
                                message = msg,
                                relatedEntityId = eval.budgetId,
                                timestampMillis = now
                            )
                        )
                        NotificationHelper.showNotification(
                            context = applicationContext,
                            notificationId = (3000 + eval.budgetId).toInt(),
                            title = title,
                            message = msg,
                            destination = NotificationHelper.DEST_PLANNING,
                            includeQuickAddAction = true,
                            lang = lang
                        )
                    }
                }
            }

            // 5. Savings goals near completion (>= 90%)
            if (prefs.notifyGoals) {
                repo.getAllGoalsOnce()
                    .filter { it.status == GoalStatus.ACTIVE && it.targetAmountPiasters > 0L }
                    .forEach { goal ->
                        val pct = FinancialEngine.computePercent(goal.savedAmountPiasters, goal.targetAmountPiasters)
                        if (pct >= 90.0 && pct < 100.0 && !recentlyNotified(NotificationType.GOAL_NEAR_COMPLETE, goal.id)) {
                            val title = "اقتربت من هدف ${goal.name}!"
                            val msg = "حققت ${MoneyFormatter.formatPercent(pct)} من هدف الادخار."
                            repo.addNotification(
                                NotificationEntity(
                                    type = NotificationType.GOAL_NEAR_COMPLETE,
                                    title = title,
                                    message = msg,
                                    relatedEntityId = goal.id,
                                    timestampMillis = now
                                )
                            )
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                notificationId = (4000 + goal.id).toInt(),
                                title = title,
                                message = msg,
                                destination = NotificationHelper.DEST_NOTIFICATIONS,
                                includeQuickAddAction = false,
                                lang = lang
                            )
                        }
                    }
            }

            // 6. Periodic Financial Summary (Daily or Weekly)
            if (prefs.notifyPeriodicSummary && FinancialSummaryBuilder.isSummaryDue(prefs.lastSummaryTimestampMillis, prefs.summaryFrequency, now)) {
                val pending = repo.getAllPendingOnce()
                val summary = FinancialSummaryBuilder.build(
                    accounts = accounts,
                    transactions = txs,
                    budgets = budgets,
                    installments = installments,
                    pending = pending,
                    nowMillis = now
                )
                val isWeekly = prefs.summaryFrequency == SummaryFrequency.WEEKLY
                val title = when {
                    isWeekly && lang == AppLanguage.AR -> "ملخصك المالي الأسبوعي"
                    isWeekly -> "Weekly Financial Summary"
                    lang == AppLanguage.AR -> "ملخصك المالي اليومي"
                    else -> "Daily Financial Summary"
                }
                val text = FinancialSummaryBuilder.formatMessageText(
                    snapshot = summary,
                    isWeekly = isWeekly,
                    currency = prefs.currencyCode,
                    hideAmounts = prefs.hideAmounts,
                    lang = lang
                )
                repo.addNotification(
                    NotificationEntity(
                        type = NotificationType.FINANCIAL_SUMMARY,
                        title = title,
                        message = text,
                        timestampMillis = now
                    )
                )
                NotificationHelper.showPeriodicSummaryNotification(
                    context = applicationContext,
                    notificationId = SUMMARY_NOTIFICATION_ID,
                    summary = summary,
                    hideAmounts = prefs.hideAmounts,
                    currency = prefs.currencyCode,
                    isWeekly = isWeekly,
                    lang = lang
                )
                prefsRepo.setLastSummaryTimestamp(now)
            }

            // 7. Refresh Glance Home Screen Widget
            ElbagorryWidgetUpdater.updateAll(applicationContext)

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "elbagorry_financial_daily_check"
        const val SUMMARY_NOTIFICATION_ID = 9001

        fun schedulePeriodic(context: Context) {
            runCatching {
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(
                        context.applicationContext,
                        androidx.work.Configuration.Builder().build()
                    )
                }
                val request = PeriodicWorkRequestBuilder<FinancialCheckWorker>(12, TimeUnit.HOURS).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
