package com.food.opencook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.LocalDataWiper
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.data.remote.dto.PatchHouseholdRequest
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val householdId: String? = null,
    val householdName: String = "",
    val householdSize: Int = 2,
    /** The invite code — shown so it can be copied into the browser extension. */
    val householdCode: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val syncApi: SyncApi,
    private val syncEngine: SyncEngine,
    private val wiper: LocalDataWiper,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.serverUrl,
            settings.householdId,
            settings.householdName,
            settings.householdSize,
            settings.householdCode,
        ) { url, id, name, size, code ->
            SettingsUiState(
                serverUrl = url.orEmpty(),
                householdId = id,
                householdName = name.orEmpty(),
                householdSize = size,
                householdCode = code.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Whether Material You (wallpaper) colors are used instead of the brand palette. */
    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun saveServerUrl(url: String) = viewModelScope.launch { settings.setServerUrl(url) }

    /**
     * Person count is household-wide: cache it locally for instant UI/offline use,
     * then PATCH the server so every device converges (on its next sync).
     */
    fun setHouseholdSize(size: Int) {
        val clamped = size.coerceIn(1, 20)
        viewModelScope.launch {
            settings.setHouseholdSize(clamped) // optimistic local cache
            val id = settings.householdIdOnce()
            val code = settings.householdCodeOnce()
            if (id != null && code != null) {
                runCatching {
                    syncApi.patchHousehold(id, code, PatchHouseholdRequest(settings = HouseholdSettings(clamped)))
                }.onFailure { _message.update { "Konnte Server nicht aktualisieren — wird beim nächsten Sync nachgeholt." } }
            }
        }
    }

    /** Leaving wipes the household's local data (recipes, sync log, photos) and clears
     *  the membership; the app returns to onboarding automatically. Keeping any of it
     *  would leak private content and — for the sync log — push old messages into a
     *  freshly-joined household on the next sync round. */
    fun leaveHousehold() = viewModelScope.launch { wiper.wipeAndLeave() }

    fun synchronize() = run {
        _message.update { null }
        viewModelScope.launch {
            _busy.update { true }
            when (val result = syncEngine.sync()) {
                is SyncEngine.Result.Ok -> _message.update { "Synchronisiert (${result.pulled} empfangen)" }
                SyncEngine.Result.NoHousehold -> _message.update { "Kein Haushalt — erst beitreten." }
                SyncEngine.Result.UnknownHousehold -> _message.update {
                    "Haushalt auf dem Server nicht gefunden — evtl. neu aufgesetzt. Bitte neu beitreten."
                }
                is SyncEngine.Result.Failed -> _message.update { "Sync fehlgeschlagen: ${result.message}" }
            }
            _busy.update { false }
        }
    }
}
