package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage

object NotificationHelper {

    const val CHANNEL_ID = "elbagorry_money_alerts"
    const val CHANNEL_SUMMARY_ID = "elbagorry_money_summary"

    const val EXTRA_NAV_DESTINATION = "extra_nav_destination"
    const val DEST_QUICK_ADD = "quick_add"
    const val DEST_QUICK_ADD_INCOME = "quick_add_income"
    const val DEST_PLANNING = "planning"
    const val DEST_SMART_READER = "smart_reader"
    const val DEST_INSIGHTS = "insights"
    const val DEST_NOTIFICATIONS = "notifications"

    fun ensureChannel(context: Context) {
        ensureChannels(context)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY_ID,
                context.getString(R.string.notification_summary_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_summary_channel_desc)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(alertsChannel)
            manager?.createNotificationChannel(summaryChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createDeepLinkPendingIntent(
        context: Context,
        requestCode: Int,
        destination: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_DESTINATION, destination)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        destination: String = DEST_NOTIFICATIONS,
        includeQuickAddAction: Boolean = true,
        lang: AppLanguage = AppLanguage.AR
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val contentPendingIntent = createDeepLinkPendingIntent(context, notificationId, destination)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        if (includeQuickAddAction) {
            val quickAddIntent = createDeepLinkPendingIntent(context, notificationId + 10_000, DEST_QUICK_ADD)
            builder.addAction(
                0,
                if (lang == AppLanguage.AR) "إضافة سريعة" else "Quick Add",
                quickAddIntent
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun showInstallmentDueNotification(
        context: Context,
        notificationId: Int,
        dbNotificationId: Long,
        installmentId: Long,
        accountId: Long,
        title: String,
        message: String,
        lang: AppLanguage = AppLanguage.AR
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openPlanningPi = createDeepLinkPendingIntent(context, notificationId, DEST_PLANNING)
        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_INSTALLMENT
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_DB_NOTIFICATION_ID, dbNotificationId)
            putExtra(NotificationActionReceiver.EXTRA_INSTALLMENT_ID, installmentId)
            putExtra(NotificationActionReceiver.EXTRA_ACCOUNT_ID, accountId)
        }
        val payPi = PendingIntent.getBroadcast(
            context,
            notificationId + 20_000,
            payIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPlanningPi)
            .addAction(
                0,
                if (lang == AppLanguage.AR) "تأكيد السداد" else "Mark Paid",
                payPi
            )
            .addAction(
                0,
                if (lang == AppLanguage.AR) "الالتزامات" else "View",
                openPlanningPi
            )
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showSmartReaderPendingNotification(
        context: Context,
        notificationId: Int,
        pendingCount: Int,
        latestSummary: String,
        lang: AppLanguage = AppLanguage.AR
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val reviewPi = createDeepLinkPendingIntent(context, notificationId, DEST_SMART_READER)
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            notificationId + 30_000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (lang == AppLanguage.AR) {
            "القارئ الذكي: $pendingCount عملية للمراجعة"
        } else {
            "Smart Reader: $pendingCount pending review"
        }
        val body = latestSummary.ifBlank {
            if (lang == AppLanguage.AR) "تم رصد معاملات مالية جديدة بانتظار اعتمادك." else "New financial alerts waiting for approval."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(reviewPi)
            .addAction(
                0,
                if (lang == AppLanguage.AR) "مراجعة الآن" else "Review Now",
                reviewPi
            )
            .addAction(
                0,
                if (lang == AppLanguage.AR) "تحديد كمقروء" else "Mark Read",
                dismissPi
            )
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showPeriodicSummaryNotification(
        context: Context,
        notificationId: Int,
        summary: FinancialSummarySnapshot,
        hideAmounts: Boolean,
        currency: String,
        isWeekly: Boolean,
        lang: AppLanguage = AppLanguage.AR
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = when {
            isWeekly && lang == AppLanguage.AR -> "ملخصك المالي الأسبوعي"
            isWeekly -> "Weekly Financial Summary"
            lang == AppLanguage.AR -> "ملخصك المالي اليومي"
            else -> "Daily Financial Summary"
        }

        val todayFmt = MoneyFormatter.formatPiasters(summary.todaySpendPiasters, currency, lang, hideAmounts)
        val periodSpend = if (isWeekly) summary.weekSpendPiasters else summary.monthSpendPiasters
        val periodFmt = MoneyFormatter.formatPiasters(periodSpend, currency, lang, hideAmounts)
        val balanceFmt = MoneyFormatter.formatPiasters(summary.netBalancePiasters, currency, lang, hideAmounts)
        val remBudgetFmt = MoneyFormatter.formatPiasters(summary.remainingBudgetPiasters, currency, lang, hideAmounts)

        val line1 = if (lang == AppLanguage.AR) "صافي الرصيد: $balanceFmt" else "Net Balance: $balanceFmt"
        val line2 = if (lang == AppLanguage.AR) "مصروف اليوم: $todayFmt" else "Today's Spend: $todayFmt"
        val line3 = if (isWeekly) {
            if (lang == AppLanguage.AR) "مصروف الأسبوع: $periodFmt" else "Week Spend: $periodFmt"
        } else {
            if (lang == AppLanguage.AR) "المتبقي من الميزانية: $remBudgetFmt" else "Remaining Budget: $remBudgetFmt"
        }
        val line4 = if (summary.pendingReaderCount > 0) {
            if (lang == AppLanguage.AR) "لديك ${summary.pendingReaderCount} عملية معلقة في القارئ الذكي" else "${summary.pendingReaderCount} Smart Reader items pending"
        } else null

        val compactMsg = "$line2 • $line1"

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .addLine(line1)
            .addLine(line2)
            .addLine(line3)
        if (line4 != null) inboxStyle.addLine(line4)

        val openInsightsPi = createDeepLinkPendingIntent(context, notificationId, DEST_INSIGHTS)
        val quickAddPi = createDeepLinkPendingIntent(context, notificationId + 40_000, DEST_QUICK_ADD)

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(compactMsg)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openInsightsPi)
            .addAction(
                0,
                if (lang == AppLanguage.AR) "إضافة سريعة +" else "Quick Add +",
                quickAddPi
            )
            .addAction(
                0,
                if (lang == AppLanguage.AR) "التحليلات" else "Insights",
                openInsightsPi
            )
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
