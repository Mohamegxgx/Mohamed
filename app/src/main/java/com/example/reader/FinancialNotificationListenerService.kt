package com.example.reader

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ElbagorryMoneyApp
import com.example.domain.model.AppLanguage
import com.example.domain.model.MessageChannel
import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.MessageDirection
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
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val combinedBody = extractFullNotificationText(extras, title)
        if (combinedBody.isBlank()) return

        val app = applicationContext as? ElbagorryMoneyApp ?: return
        serviceScope.launch {
            val sources = app.repository.getAllFinancialSourcesOnce()
            val matchedByPkg = sources.firstOrNull { src ->
                src.isEnabled && !src.isIgnored && src.packageNames.split(",")
                    .any { it.trim().equals(pkg, ignoreCase = true) }
            }
            val isSmsApp = pkg.lowercase() in SMS_APP_PACKAGES
            val isFinancialContent = matchedByPkg != null ||
                FinancialMessageParser.looksLikeFinancialNotification(title, combinedBody)
            if (!isSmsApp && !isFinancialContent) return@launch
            if (isSmsApp && !FinancialMessageParser.looksLikeFinancialNotification(title, combinedBody) && matchedByPkg == null) {
                return@launch
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
            if (result.pendingCreated > 0 || result.autoApprovedCount > 0) {
                val prefs = app.preferencesRepository.preferences.first()
                val dirLabel = when (result.latestDirection) {
                    MessageDirection.INCOMING -> if (prefs.language == AppLanguage.EN) "Incoming" else "استقبال (وارد)"
                    MessageDirection.OUTGOING -> if (prefs.language == AppLanguage.EN) "Outgoing" else "إرسال (صادر)"
                    MessageDirection.INTERNAL_TRANSFER -> if (prefs.language == AppLanguage.EN) "Internal Transfer" else "تحويل داخلي"
                    else -> ""
                }
                if (result.autoApprovedCount > 0) {
                    NotificationHelper.showSmartReaderAutoRecordedNotification(
                        context = app,
                        notificationId = 8502,
                        summaryText = result.latestSummaryText.ifBlank { senderLabel },
                        directionLabel = dirLabel.ifBlank { if (prefs.language == AppLanguage.EN) "Transaction" else "معاملة مالية" },
                        lang = prefs.language
                    )
                }
                if (result.pendingCreated > 0) {
                    NotificationHelper.showSmartReaderPendingNotification(
                        context = app,
                        notificationId = 8501,
                        pendingCount = result.pendingCreated,
                        latestSummary = result.latestSummaryText.ifBlank { senderLabel },
                        lang = prefs.language,
                        pendingId = result.latestPendingId,
                        accountId = result.latestSuggestedAccountId,
                        directionLabel = dirLabel
                    )
                }
                ElbagorryWidgetUpdater.updateAll(app)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractFullNotificationText(extras: Bundle, title: String): String {
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val messagingTexts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            parcelables?.mapNotNull { item ->
                (item as? Bundle)?.getCharSequence("text")?.toString()?.trim()
            }?.filter { it.isNotBlank() }.orEmpty()
        } else emptyList()

        val parts = linkedSetOf<String>()
        if (title.isNotBlank()) parts.add(title)
        if (bigText.isNotBlank()) parts.add(bigText) else if (text.isNotBlank()) parts.add(text)
        if (subText.isNotBlank()) parts.add(subText)
        parts.addAll(textLines)
        parts.addAll(messagingTexts)
        return parts.joinToString(" ").trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private val SMS_APP_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "org.thoughtcrime.securesms"
        )
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
