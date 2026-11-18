package com.example.data.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CloudAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser ?: return Result.success()
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.preferences.first()
        if (!prefs.autoCloudSyncEnabled || prefs.isGuestMode) {
            return Result.success()
        }
        val db = AppDatabase.getInstance(applicationContext)
        val databaseId = applicationContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val cloudRepo = CloudSyncRepository(firestore)

        val res = cloudRepo.pushRoomToCloud(
            localDb = db,
            displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "ELBAGORRY User",
            email = currentUser.email,
            currencyCode = prefs.currencyCode,
            themePreset = prefs.themePreset.name
        )
        return if (res.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "elbagorry_cloud_auto_sync"
        private const val ONETIME_WORK_NAME = "elbagorry_cloud_sync_now"

        private fun ensureWorkManager(context: Context) {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(
                    context.applicationContext,
                    androidx.work.Configuration.Builder().build()
                )
            }
        }

        fun schedulePeriodic(context: Context) {
            runCatching {
                ensureWorkManager(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val req = PeriodicWorkRequestBuilder<CloudAutoSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req
                )
            }
        }

        fun enqueueImmediateDebounced(context: Context) {
            runCatching {
                ensureWorkManager(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val req = OneTimeWorkRequestBuilder<CloudAutoSyncWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(10, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONETIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    req
                )
            }
        }

        fun cancelAll(context: Context) {
            runCatching {
                ensureWorkManager(context)
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
                WorkManager.getInstance(context).cancelUniqueWork(ONETIME_WORK_NAME)
            }
        }
    }
}
