package com.food.opencook.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.discovery.DiscoveredServer
import com.food.opencook.data.discovery.ServerDiscovery
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.dto.CreateHouseholdRequest
import com.food.opencook.data.remote.dto.HouseholdDto
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.JoinHouseholdRequest
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { SERVER, HOUSEHOLD, CREATE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SERVER,
    val serverUrl: String? = null,
    val households: List<HouseholdSummaryDto> = emptyList(),
    val loadingHouseholds: Boolean = false,
    /** Non-null shows the PIN dialog for that protected household. */
    val pinPromptFor: HouseholdSummaryDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val serverDiscovery: ServerDiscovery,
    private val syncApi: SyncApi,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val syncTrigger: SyncTrigger,
    private val pantryRepository: PantryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Cold discovery flow — collected by the server step so it stops with the screen. */
    val discovered: Flow<List<DiscoveredServer>> = serverDiscovery.discover()

    /**
     * Called whenever the onboarding UI (re)appears. This ViewModel is host-scoped, so
     * after leaving a household it is reused — without resetting, stale `busy=true` (left
     * over from a successful join) would keep every button disabled until an app restart.
     * Reset transient flags; if a server is already known, jump straight to a fresh
     * household list instead of re-asking for the server.
     */
    fun onEnter() {
        viewModelScope.launch {
            val url = settings.serverUrlOnce()
            _state.update {
                it.copy(
                    busy = false,
                    error = null,
                    pinPromptFor = null,
                    serverUrl = url,
                    step = if (!url.isNullOrBlank()) OnboardingStep.HOUSEHOLD else OnboardingStep.SERVER,
                )
            }
            if (!url.isNullOrBlank()) {
                baseUrlInterceptor.setBaseUrl(url)
                loadHouseholds()
            }
        }
    }

    fun chooseServer(rawUrl: String) {
        val url = normalizeUrl(rawUrl) ?: run {
            _state.update { it.copy(error = "Ungültige Adresse") }
            return
        }
        viewModelScope.launch {
            settings.setServerUrl(url)
            // Set synchronously too so the next request hits this server without a race.
            baseUrlInterceptor.setBaseUrl(url)
            _state.update { it.copy(serverUrl = url, step = OnboardingStep.HOUSEHOLD, error = null) }
            loadHouseholds()
        }
    }

    fun loadHouseholds() {
        viewModelScope.launch {
            _state.update { it.copy(loadingHouseholds = true, error = null) }
            runCatching { syncApi.listHouseholds() }
                .onSuccess { list -> _state.update { it.copy(households = list, loadingHouseholds = false) } }
                .onFailure { e -> _state.update { it.copy(loadingHouseholds = false, error = errorText(e)) } }
        }
    }

    fun selectHousehold(summary: HouseholdSummaryDto) {
        if (summary.protected) {
            _state.update { it.copy(pinPromptFor = summary, error = null) }
        } else {
            join(summary.id, pin = null)
        }
    }

    fun submitPin(pin: String) {
        val target = _state.value.pinPromptFor ?: return
        join(target.id, pin)
    }

    fun dismissPin() = _state.update { it.copy(pinPromptFor = null) }

    private fun join(id: String, pin: String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { syncApi.joinHousehold(id, JoinHouseholdRequest(pin)) }
                .onSuccess { adopt(it) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = errorText(e)) } }
        }
    }

    fun goToCreate() = _state.update { it.copy(step = OnboardingStep.CREATE, error = null) }

    fun createHousehold(name: String, size: Int, pin: String?, adminPassword: String?) {
        if (name.isBlank()) {
            _state.update { it.copy(error = "Bitte einen Namen eingeben") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val body = CreateHouseholdRequest(
                name = name.trim(),
                settings = HouseholdSettings(householdSize = size),
                pin = pin?.takeIf { it.isNotBlank() },
                adminPassword = adminPassword?.takeIf { it.isNotBlank() },
            )
            runCatching { syncApi.createHousehold(body) }
                .onSuccess {
                    adopt(it)
                    // Pre-fill the pantry with curated staples — only on the creator path,
                    // never on join (the joining device pulls the pantry via sync and would
                    // otherwise insert duplicates).
                    pantryRepository.seedDefaults()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = errorText(e)) } }
        }
    }

    /** Persist membership + household-wide settings; AppViewModel then flips to Onboarded. */
    private suspend fun adopt(dto: HouseholdDto) {
        // Clear busy now so the (host-scoped) VM isn't left "busy" after the screen leaves.
        _state.update { it.copy(busy = false, error = null) }
        settings.setHousehold(code = dto.inviteCode, id = dto.householdId, name = dto.name)
        settings.setHouseholdSize(dto.settings.householdSize)
        // Pull existing household data right away — without this the first sync would
        // only happen on the next periodic tick, so a new member sees an empty app.
        syncTrigger.requestSync()
    }

    fun back() = _state.update {
        when (it.step) {
            OnboardingStep.CREATE -> it.copy(step = OnboardingStep.HOUSEHOLD, error = null)
            OnboardingStep.HOUSEHOLD -> it.copy(step = OnboardingStep.SERVER, error = null)
            OnboardingStep.SERVER -> it
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val t = raw.trim().ifEmpty { return null }
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    private fun errorText(t: Throwable): String = when {
        t.message?.contains("403") == true -> "PIN falsch."
        t.message?.contains("Server URL not configured") == true -> "Kein Server gewählt."
        else -> "Server nicht erreichbar."
    }
}
