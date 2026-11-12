package com.example.reader

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ElbagorryMoneyApp
import com.example.domain.model.MessageChannel
import com.example.notifications.NotificationHelper
import com.example.widget.ElbagorryWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FinancialNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val combinedBody = listOf(title, bigText.ifBlank { text }).filter { it.isNotBlank() }.joinToString(" ")
        if (combinedBody.isBlank()) return

        val app = applicationContext as? ElbagorryMoneyApp ?: return
        serviceScope.launch {
            val sources = app.repository.getAllFinancialSourcesOnce()
            val matchedByPkg = sources.firstOrNull { src ->
                src.isEnabled && !src.isIgnored && src.packageNames.split(",")
                    .any { it.trim().equals(pkg, ignoreCase = true) }
            }
            val senderLabel = matchedByPkg?.displayNameAr ?: title.ifBlank { pkg }
            val result = app.smartReaderProcessor.ingestMessages(
                messages = listOf(
                    IncomingRawMessage(
                        sender = senderLabel,
                        body = combinedBody,
                        timestampMillis = sbn.postTime
                    )
                ),
                channel = MessageChannel.NOTIFICATION_LISTENER
            )
            if (result.pendingCreated > 0) {
                val prefs = app.preferencesRepository.preferences.first()
                NotificationHelper.showSmartReaderPendingNotification(
                    context = app,
                    notificationId = 8501,
                    pendingCount = result.pendingCreated,
                    latestSummary = senderLabel,
                    lang = prefs.language
                )
                ElbagorryWidgetUpdater.updateAll(app)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val component = ComponentName(context, FinancialNotificationListenerService::class.java)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == component
            }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
