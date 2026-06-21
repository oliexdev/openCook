package com.food.opencook.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.R
import com.food.opencook.data.LocalDataWiper
import com.food.opencook.data.remote.AdminApi
import com.food.opencook.data.remote.dto.AdminPasswordChangeDto
import com.food.opencook.data.remote.dto.BackupInfoDto
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.RestoreRequestDto
import com.food.opencook.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val loading: Boolean = true,
    val noServer: Boolean = false,
    val configured: Boolean = false,
    val unlocked: Boolean = false,
    val backups: List<BackupInfoDto> = emptyList(),
    val households: List<HouseholdSummaryDto> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/**
 * Backs the server admin area. The admin password lives only in memory for the
 * lifetime of this ViewModel (one unlock per visit) — it is never persisted.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminApi: AdminApi,
    private val settings: SettingsRepository,
    private val wiper: LocalDataWiper,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    /** Held only while this screen is open. */
    private var password: String? = null

    init {
        viewModelScope.launch {
            if (settings.serverUrlOnce().isNullOrBlank()) {
                _state.update { it.copy(loading = false, noServer = true) }
                return@launch
            }
            runCatching { adminApi.status() }
                .onSuccess { s -> _state.update { it.copy(loading = false, configured = s.configured) } }
                .onFailure { _state.update { it.copy(loading = false, error = context.getString(R.string.admin_error_unreachable)) } }
        }
    }

    /** Set the password (when unconfigured) or verify it, then unlock + load backups. */
    fun unlock(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val configured = _state.value.configured
            val ok = if (!configured) {
                runCatching { adminApi.setPassword(AdminPasswordChangeDto(newPassword = input)) }
                    .map { it.isSuccessful }.getOrDefault(false)
            } else {
                runCatching { adminApi.verify(input) }.map { it.isSuccessful }.getOrDefault(false)
            }
            if (ok) {
                password = input
                _state.update { it.copy(busy = false, configured = true, unlocked = true) }
                loadBackups()
                loadHouseholds()
            } else {
                _state.update { it.copy(busy = false, error = context.getString(R.string.admin_wrong_password)) }
            }
        }
    }

    private fun loadBackups() {
        val pw = password ?: return
        viewModelScope.launch {
            runCatching { adminApi.listBackups(pw) }
                .onSuccess { list -> _state.update { it.copy(backups = list.backups) } }
                .onFailure { _state.update { it.copy(error = context.getString(R.string.admin_error_load_backups)) } }
        }
    }

    private fun loadHouseholds() {
        viewModelScope.launch {
            runCatching { adminApi.households() }
                .onSuccess { list -> _state.update { it.copy(households = list) } }
                .onFailure { _state.update { it.copy(error = context.getString(R.string.admin_error_load_households)) } }
        }
    }

    fun deleteHousehold(id: String) {
        val pw = password ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            val ok = runCatching { adminApi.deleteHousehold(id, pw) }.map { it.isSuccessful }.getOrDefault(false)
            if (ok) {
                _state.update { it.copy(busy = false, info = "household_deleted") }
                loadHouseholds()
            } else {
                _state.update { it.copy(busy = false, error = context.getString(R.string.admin_error)) }
            }
        }
    }

    fun createBackup() {
        val pw = password ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { adminApi.createBackup(pw) }
                .onSuccess {
                    _state.update { it.copy(busy = false, info = "backup_created") }
                    loadBackups()
                }
                .onFailure { _state.update { it.copy(busy = false, error = context.getString(R.string.admin_error)) } }
        }
    }

    fun restore(backupId: String) {
        val pw = password ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { adminApi.restore(pw, RestoreRequestDto(backupId)) }
                .onSuccess { _state.update { it.copy(busy = false, info = "restore_done") } }
                .onFailure { _state.update { it.copy(busy = false, error = context.getString(R.string.admin_error)) } }
        }
    }

    /**
     * Full testing reset: wipe the server (log/jobs/households/images; keeps backups +
     * admin password), then clear this device's local Room DB and leave the (now-deleted)
     * household — the app returns to onboarding, fully fresh. Other devices can't re-push
     * because their household no longer exists.
     */
    fun resetDatabase() {
        val pw = password ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            val ok = runCatching { adminApi.reset(pw) }.map { it.isSuccessful }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(busy = false, error = context.getString(R.string.admin_error)) }
                return@launch
            }
            wiper.wipeAndLeave() // clears DB, image files and household membership
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }
}
