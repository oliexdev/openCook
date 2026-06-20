package com.food.opencook.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.LocalDataWiper
import com.food.opencook.data.remote.AdminApi
import com.food.opencook.data.remote.dto.AdminPasswordChangeDto
import com.food.opencook.data.remote.dto.BackupInfoDto
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.RestoreRequestDto
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.update.AppUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the "check for app update" flow shown in the admin area. */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val versionName: String, val url: String, val notes: String?) : UpdateUi
    data object Downloading : UpdateUi
    data object Error : UpdateUi
}

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
    val update: UpdateUi = UpdateUi.Idle,
)

/**
 * Backs the server admin area. The admin password lives only in memory for the
 * lifetime of this ViewModel (one unlock per visit) — it is never persisted.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminApi: AdminApi,
    private val settings: SettingsRepository,
    private val wiper: LocalDataWiper,
    private val appUpdater: AppUpdater,
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
                .onFailure { _state.update { it.copy(loading = false, error = "Server nicht erreichbar") } }
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
                _state.update { it.copy(busy = false, error = "Falsches Passwort") }
            }
        }
    }

    private fun loadBackups() {
        val pw = password ?: return
        viewModelScope.launch {
            runCatching { adminApi.listBackups(pw) }
                .onSuccess { list -> _state.update { it.copy(backups = list.backups) } }
                .onFailure { _state.update { it.copy(error = "Backups konnten nicht geladen werden") } }
        }
    }

    private fun loadHouseholds() {
        viewModelScope.launch {
            runCatching { adminApi.households() }
                .onSuccess { list -> _state.update { it.copy(households = list) } }
                .onFailure { _state.update { it.copy(error = "Haushalte konnten nicht geladen werden") } }
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
                _state.update { it.copy(busy = false, error = "Aktion fehlgeschlagen") }
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
                .onFailure { _state.update { it.copy(busy = false, error = "Aktion fehlgeschlagen") } }
        }
    }

    fun restore(backupId: String) {
        val pw = password ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { adminApi.restore(pw, RestoreRequestDto(backupId)) }
                .onSuccess { _state.update { it.copy(busy = false, info = "restore_done") } }
                .onFailure { _state.update { it.copy(busy = false, error = "Aktion fehlgeschlagen") } }
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
                _state.update { it.copy(busy = false, error = "Aktion fehlgeschlagen") }
                return@launch
            }
            wiper.wipeAndLeave() // clears DB, image files and household membership
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    /** Ask the configured server whether a newer APK is published (self-hosted update flow). */
    fun checkForUpdates() = viewModelScope.launch {
        _state.update { it.copy(update = UpdateUi.Checking) }
        val next = when (val r = appUpdater.check()) {
            AppUpdater.Check.UpToDate -> UpdateUi.UpToDate
            is AppUpdater.Check.Available -> UpdateUi.Available(r.versionName, r.url, r.notes)
            is AppUpdater.Check.Error -> UpdateUi.Error
        }
        _state.update { it.copy(update = next) }
    }

    /** Download the published APK and launch the system installer. */
    fun downloadAndInstall(url: String) = viewModelScope.launch {
        _state.update { it.copy(update = UpdateUi.Downloading) }
        if (!appUpdater.downloadAndInstall(url)) _state.update { it.copy(update = UpdateUi.Error) }
    }
}
