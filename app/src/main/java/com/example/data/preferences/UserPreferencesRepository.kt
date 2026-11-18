package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.SummaryFrequency
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "elbagorry_money_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themePreset: ThemePreset = ThemePreset.HYDROGEN_NEXUS,
    val language: AppLanguage = AppLanguage.AR,
    val hideAmounts: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val privacyScreenGuardEnabled: Boolean = false,
    val currencyCode: String = "EGP",
    val notifyInstallments: Boolean = true,
    val notifyBills: Boolean = true,
    val notifyBudgets: Boolean = true,
    val notifyDailyReminder: Boolean = true,
    val notifySalary: Boolean = true,
    val notifyGoals: Boolean = true,
    val notifyPeriodicSummary: Boolean = true,
    val summaryFrequency: SummaryFrequency = SummaryFrequency.DAILY,
    val lastSummaryTimestampMillis: Long = 0L,
    val smartReaderEnabled: Boolean = true,
    val smartReaderAutoApprove: Boolean = false,
    val readerIgnoreOtp: Boolean = true,
    val readerIgnoreBalanceInquiries: Boolean = true,
    val lastImportTimestampMillis: Long = 0L,
    val isGuestMode: Boolean = false,
    val customCurrencyRatesRaw: String = "",
    val autoCloudSyncEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val LANGUAGE = stringPreferencesKey("language")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val PRIVACY_SCREEN_GUARD = booleanPreferencesKey("privacy_screen_guard")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val NOTIFY_INSTALLMENTS = booleanPreferencesKey("notify_installments")
        val NOTIFY_BILLS = booleanPreferencesKey("notify_bills")
        val NOTIFY_BUDGETS = booleanPreferencesKey("notify_budgets")
        val NOTIFY_DAILY = booleanPreferencesKey("notify_daily")
        val NOTIFY_SALARY = booleanPreferencesKey("notify_salary")
        val NOTIFY_GOALS = booleanPreferencesKey("notify_goals")
        val NOTIFY_PERIODIC_SUMMARY = booleanPreferencesKey("notify_periodic_summary")
        val SUMMARY_FREQUENCY = stringPreferencesKey("summary_frequency")
        val LAST_SUMMARY_TS = longPreferencesKey("last_summary_ts")
        val SMART_READER_ENABLED = booleanPreferencesKey("smart_reader_enabled")
        val SMART_READER_AUTO_APPROVE = booleanPreferencesKey("smart_reader_auto_approve")
        val READER_IGNORE_OTP = booleanPreferencesKey("reader_ignore_otp")
        val READER_IGNORE_BALANCE = booleanPreferencesKey("reader_ignore_balance")
        val LAST_IMPORT_TS = longPreferencesKey("last_import_ts")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
        val CUSTOM_CURRENCY_RATES = stringPreferencesKey("custom_currency_rates")
        val AUTO_CLOUD_SYNC = booleanPreferencesKey("auto_cloud_sync")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            themePreset = prefs[Keys.THEME_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: ThemePreset.HYDROGEN_NEXUS,
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.AR,
            hideAmounts = prefs[Keys.HIDE_AMOUNTS] ?: false,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            privacyScreenGuardEnabled = prefs[Keys.PRIVACY_SCREEN_GUARD] ?: false,
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "EGP",
            notifyInstallments = prefs[Keys.NOTIFY_INSTALLMENTS] ?: true,
            notifyBills = prefs[Keys.NOTIFY_BILLS] ?: true,
            notifyBudgets = prefs[Keys.NOTIFY_BUDGETS] ?: true,
            notifyDailyReminder = prefs[Keys.NOTIFY_DAILY] ?: true,
            notifySalary = prefs[Keys.NOTIFY_SALARY] ?: true,
            notifyGoals = prefs[Keys.NOTIFY_GOALS] ?: true,
            notifyPeriodicSummary = prefs[Keys.NOTIFY_PERIODIC_SUMMARY] ?: true,
            summaryFrequency = prefs[Keys.SUMMARY_FREQUENCY]?.let { runCatching { SummaryFrequency.valueOf(it) }.getOrNull() } ?: SummaryFrequency.DAILY,
            lastSummaryTimestampMillis = prefs[Keys.LAST_SUMMARY_TS] ?: 0L,
            smartReaderEnabled = prefs[Keys.SMART_READER_ENABLED] ?: true,
            smartReaderAutoApprove = prefs[Keys.SMART_READER_AUTO_APPROVE] ?: false,
            readerIgnoreOtp = prefs[Keys.READER_IGNORE_OTP] ?: true,
            readerIgnoreBalanceInquiries = prefs[Keys.READER_IGNORE_BALANCE] ?: true,
            lastImportTimestampMillis = prefs[Keys.LAST_IMPORT_TS] ?: 0L,
            isGuestMode = prefs[Keys.IS_GUEST_MODE] ?: false,
            customCurrencyRatesRaw = prefs[Keys.CUSTOM_CURRENCY_RATES] ?: "",
            autoCloudSyncEnabled = prefs[Keys.AUTO_CLOUD_SYNC] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { it[Keys.THEME_PRESET] = preset.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun toggleHideAmounts(): Boolean {
        var newValue = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDE_AMOUNTS] ?: false
            newValue = !current
            prefs[Keys.HIDE_AMOUNTS] = newValue
        }
        return newValue
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setPrivacyScreenGuardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_SCREEN_GUARD] = enabled }
    }

    suspend fun setCurrencyCode(code: String) {
        context.dataStore.edit { it[Keys.CURRENCY_CODE] = code.uppercase() }
    }

    suspend fun setCustomCurrencyRates(raw: String) {
        context.dataStore.edit { it[Keys.CUSTOM_CURRENCY_RATES] = raw.trim() }
    }

    suspend fun setSmartReaderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_READER_ENABLED] = enabled }
    }

    suspend fun setSmartReaderAutoApprove(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_READER_AUTO_APPROVE] = enabled }
    }

    suspend fun setReaderIgnoreOtp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READER_IGNORE_OTP] = enabled }
    }

    suspend fun setReaderIgnoreBalance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READER_IGNORE_BALANCE] = enabled }
    }

    suspend fun setLastImportTimestamp(timestampMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_IMPORT_TS] = timestampMillis }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_GUEST_MODE] = enabled }
    }

    suspend fun setAutoCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLOUD_SYNC] = enabled }
    }

    suspend fun setSummaryFrequency(frequency: SummaryFrequency) {
        context.dataStore.edit { it[Keys.SUMMARY_FREQUENCY] = frequency.name }
    }

    suspend fun setLastSummaryTimestamp(timestampMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_SUMMARY_TS] = timestampMillis }
    }

    suspend fun setNotificationToggle(keyName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (keyName) {
                "installments" -> prefs[Keys.NOTIFY_INSTALLMENTS] = enabled
                "bills" -> prefs[Keys.NOTIFY_BILLS] = enabled
                "budgets" -> prefs[Keys.NOTIFY_BUDGETS] = enabled
                "daily" -> prefs[Keys.NOTIFY_DAILY] = enabled
                "salary" -> prefs[Keys.NOTIFY_SALARY] = enabled
                "goals" -> prefs[Keys.NOTIFY_GOALS] = enabled
                "summary" -> prefs[Keys.NOTIFY_PERIODIC_SUMMARY] = enabled
            }
        }
    }
}
