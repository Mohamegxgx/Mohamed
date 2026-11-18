package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.CloudSyncSummary
import com.example.data.remote.CloudTransaction
import com.example.data.remote.UserProfile
import com.example.data.remote.unwrapFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

data class CloudSyncActionState(
    val isSyncing: Boolean = false,
    val lastSummary: CloudSyncSummary? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class CloudSyncViewModel(
    private val cloudRepository: CloudSyncRepository,
    private val localDb: AppDatabase,
    private val prefsRepo: UserPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    val profileState: StateFlow<CloudUiState<UserProfile?>> = cloudRepository.observeUserProfile()
        .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
        .catch { error ->
            val firestoreError = error.unwrapFirestoreException()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user profile for $currentUserId", rootError)
            emit(CloudUiState.Error(rootError.message ?: "Failed to load cloud profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CloudUiState.Loading
        )

    val cloudTransactionsState: StateFlow<CloudUiState<List<CloudTransaction>>> =
        cloudRepository.observeCloudTransactions()
            .map<List<CloudTransaction>, CloudUiState<List<CloudTransaction>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud transactions for $currentUserId", rootError)
                emit(CloudUiState.Error(rootError.message ?: "Failed to observe cloud transactions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    private val _actionState = MutableStateFlow(CloudSyncActionState())
    val actionState: StateFlow<CloudSyncActionState> = _actionState.asStateFlow()

    fun bootstrapProfileIfNeeded(displayName: String, email: String?) {
        viewModelScope.launch {
            val existing = cloudRepository.getUserProfileOnce().getOrNull()
            val prefs = prefsRepo.preferences.first()
            if (existing == null) {
                cloudRepository.upsertUserProfile(
                    displayName = displayName,
                    email = email,
                    currencyCode = prefs.currencyCode,
                    themePreset = prefs.themePreset.name
                )
            }
        }
    }

    fun syncToCloudNow(displayName: String, email: String?) {
        if (_actionState.value.isSyncing) return
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(
                isSyncing = true,
                errorMessage = null,
                statusMessage = null
            )
            val prefs = prefsRepo.preferences.first()
            val result = cloudRepository.pushRoomToCloud(
                localDb = localDb,
                displayName = displayName,
                email = email,
                currencyCode = prefs.currencyCode,
                themePreset = prefs.themePreset.name
            )
            _actionState.value = result.fold(
                onSuccess = { summary ->
                    CloudSyncActionState(
                        isSyncing = false,
                        lastSummary = summary,
                        statusMessage = "تمت المزامنة: ${summary.transactionsCount} معاملة • ${summary.accountsCount} حساب • ${summary.installmentsCount} قسط • ${summary.debtsCount} دين • ${summary.certificatesCount} أصل",
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    CloudSyncActionState(
                        isSyncing = false,
                        lastSummary = _actionState.value.lastSummary,
                        statusMessage = null,
                        errorMessage = err.localizedMessage ?: "تعذرت المزامنة السحابية"
                    )
                }
            )
        }
    }

    fun restoreFromCloudNow() {
        if (_actionState.value.isSyncing) return
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(
                isSyncing = true,
                errorMessage = null,
                statusMessage = null
            )
            val result = cloudRepository.pullCloudToRoom(localDb)
            _actionState.value = result.fold(
                onSuccess = { summary ->
                    val msg = if (summary.accountsCount == 0 && summary.transactionsCount == 0 && summary.installmentsCount == 0 && summary.certificatesCount == 0) {
                        "لا توجد بيانات محفوظة في السحابة لهذا الحساب بعد"
                    } else {
                        "تمت الاستعادة: ${summary.transactionsCount} معاملة • ${summary.accountsCount} حساب • ${summary.installmentsCount} قسط • ${summary.debtsCount} دين • ${summary.certificatesCount} أصل"
                    }
                    CloudSyncActionState(
                        isSyncing = false,
                        lastSummary = summary,
                        statusMessage = msg,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    CloudSyncActionState(
                        isSyncing = false,
                        lastSummary = _actionState.value.lastSummary,
                        statusMessage = null,
                        errorMessage = err.localizedMessage ?: "تعذرت الاستعادة من السحابة"
                    )
                }
            )
        }
    }

    fun clearMessages() {
        _actionState.value = _actionState.value.copy(statusMessage = null, errorMessage = null)
    }

    private companion object {
        const val TAG = "CloudSyncVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
