package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.AppDatabase
import com.example.data.local.entity.NotificationEntity
import com.example.data.repository.MoneyRepository
import com.example.domain.model.NotificationType
import com.example.widget.ElbagorryWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        when (intent.action) {
            ACTION_PAY_INSTALLMENT -> {
                val installmentId = intent.getLongExtra(EXTRA_INSTALLMENT_ID, -1L)
                val accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
                val dbNotifId = intent.getLongExtra(EXTRA_DB_NOTIFICATION_ID, -1L)
                if (installmentId <= 0L) {
                    cancelSystemNotification(context, notifId)
                    return
                }
                val pendingResult: PendingResult? = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context.applicationContext)
                        val repo = MoneyRepository(db)
                        val resolvedAccountId = if (accountId > 0L) {
                            accountId
                        } else {
                            repo.getAllAccountsOnce().firstOrNull { !it.isArchived }?.id ?: 1L
                        }
                        runCatching {
                            repo.payInstallment(installmentId, resolvedAccountId)
                            if (dbNotifId > 0L) {
                                repo.markAllNotificationsRead()
                            } else {
                                repo.addNotification(
                                    NotificationEntity(
                                        type = NotificationType.INSTALLMENT_DUE,
                                        title = "تم تأكيد سداد القسط",
                                        message = "تم تسجيل دفعة القسط بنجاح وتحديث الرصيد.",
                                        relatedEntityId = installmentId,
                                        isRead = true
                                    )
                                )
                            }
                            ElbagorryWidgetUpdater.updateAll(context.applicationContext)
                        }
                    } finally {
                        cancelSystemNotification(context, notifId)
                        pendingResult?.finish()
                    }
                }
            }
            ACTION_APPROVE_PENDING -> {
                val pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
                val accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
                if (pendingId <= 0L) {
                    cancelSystemNotification(context, notifId)
                    return
                }
                val pendingResult: PendingResult? = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context.applicationContext)
                        val repo = MoneyRepository(db)
                        val resolvedAccountId = if (accountId > 0L) {
                            accountId
                        } else {
                            repo.getAllAccountsOnce().firstOrNull { !it.isArchived }?.id ?: 1L
                        }
                        runCatching {
                            repo.approvePendingTransaction(
                                pendingId = pendingId,
                                accountId = resolvedAccountId
                            )
                            ElbagorryWidgetUpdater.updateAll(context.applicationContext)
                        }
                    } finally {
                        cancelSystemNotification(context, notifId)
                        pendingResult?.finish()
                    }
                }
            }
            ACTION_DISMISS -> {
                val pendingResult: PendingResult? = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context.applicationContext)
                        MoneyRepository(db).markAllNotificationsRead()
                    } finally {
                        cancelSystemNotification(context, notifId)
                        pendingResult?.finish()
                    }
                }
            }
        }
    }

    private fun cancelSystemNotification(context: Context, notifId: Int) {
        if (notifId != -1) {
            runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
        }
    }

    companion object {
        const val ACTION_PAY_INSTALLMENT = "com.example.notifications.ACTION_PAY_INSTALLMENT"
        const val ACTION_APPROVE_PENDING = "com.example.notifications.ACTION_APPROVE_PENDING"
        const val ACTION_DISMISS = "com.example.notifications.ACTION_DISMISS"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_DB_NOTIFICATION_ID = "extra_db_notification_id"
        const val EXTRA_INSTALLMENT_ID = "extra_installment_id"
        const val EXTRA_PENDING_ID = "extra_pending_id"
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
    }
}
