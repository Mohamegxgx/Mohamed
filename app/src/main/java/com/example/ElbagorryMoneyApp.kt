package com.example

import android.app.Application
import com.example.data.backup.BackupAndExportManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MoneyRepository
import com.example.notifications.FinancialCheckWorker
import com.example.notifications.NotificationHelper
import com.example.reader.SmartReaderProcessor
import com.example.security.AppSecurityHelper
import com.example.widget.ElbagorryWidgetUpdater
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ElbagorryMoneyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: MoneyRepository by lazy { MoneyRepository(database) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val backupManager: BackupAndExportManager by lazy { BackupAndExportManager(database) }
    val smartReaderProcessor: SmartReaderProcessor by lazy { SmartReaderProcessor(database, preferencesRepository) }

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            runCatching { FirebaseApp.initializeApp(this) }
        }
        NotificationHelper.ensureChannels(this)
        AppSecurityHelper.ensureKeystoreKeyExists()
        FinancialCheckWorker.schedulePeriodic(this)
        appScope.launch {
            repository.ensureSeeded()
            repository.executeDueRecurringRules()
            ElbagorryWidgetUpdater.updateAll(this@ElbagorryMoneyApp)
        }
    }
}
