/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.R
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
import com.food.opencook.sync.SyncClock
import com.food.opencook.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

enum class OnboardingStep { MODE, SERVER, PEERS, HOUSEHOLD, CREATE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.MODE,
    val serverUrl: String? = null,
    val households: List<HouseholdSummaryDto> = emptyList(),
    val loadingHouseholds: Boolean = false,
    /** Non-null shows the PIN dialog for that protected household. */
    val pinPromptFor: HouseholdSummaryDto? = null,
    /** Non-null while joining through a peer phone instead of the server: its endpoints
     *  answer the household list/join, and no server URL must be persisted. */
    val viaPeer: DiscoveredServer? = null,
    /** In the serverless flow (no server involved): PEERS lists phones to join through,
     *  and CREATE mints the household locally instead of on a server. */
    val serverless: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val serverDiscovery: ServerDiscovery,
    private val syncApi: SyncApi,
    @param:Named("peer") private val peerSyncApi: SyncApi,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    @param:Named("peer") private val peerUrlInterceptor: BaseUrlInterceptor,
    private val syncTrigger: SyncTrigger,
    private val pantryRepository: PantryRepository,
    private val syncClock: SyncClock,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Cold discovery flow — collected by the server step so it stops with the screen. */
    val discovered: Flow<List<DiscoveredServer>> = serverDiscovery.discover()

    /**
     * Called whenever the onboarding UI (re)appears. This ViewModel is host-scoped, so
     * after leaving a household it is reused — without resetting, stale `busy=true` (left
     * over from a successful join) would keep every button disabled until an app restart.
     * Reset transient flags and always start at the MODE picker, so the offline
     * ("just on this phone") choice is visible on a fresh install AND after leaving a
     * household. A known server URL is only cached here; it's used to skip discovery in
     * [chooseServerMode], not to skip the mode picker.
     */
    fun onEnter() {
        viewModelScope.launch {
            val url = settings.serverUrlOnce()
            _state.update {
                it.copy(
                    busy = false,
                    error = null,
                    pinPromptFor = null,
                    viaPeer = null,
                    serverless = false,
                    serverUrl = url,
                    step = OnboardingStep.MODE,
                )
            }
        }
    }

    /** "Just on this phone": skip the server entirely and use openCook offline-only. */
    fun useLocalOnly() {
        viewModelScope.launch { settings.setLocalOnly(true) }
    }

    /** From the mode picker: go to the server flow — but jump straight to the household
     *  list when a server is already known (e.g. after leaving a household). */
    fun chooseServerMode() {
        val url = _state.value.serverUrl
        if (!url.isNullOrBlank()) {
            baseUrlInterceptor.setBaseUrl(url)
            _state.update { it.copy(step = OnboardingStep.HOUSEHOLD, error = null) }
            loadHouseholds()
        } else {
            _state.update { it.copy(step = OnboardingStep.SERVER, error = null) }
        }
    }

    /** From the mode picker: a household without any server — phones only. Goes to the
     *  peer-discovery step: join through a phone that already has the household, or
     *  found a new one from there. */
    fun chooseServerlessMode() {
        _state.update { it.copy(step = OnboardingStep.PEERS, serverless = true, error = null) }
    }

    /**
     * A peer phone was picked on the PEERS step: run the household list/join against
     * its endpoints. Unlike [chooseServer] this must NOT persist a server URL —
     * a phone is a transient counterpart, not this household's server.
     */
    fun choosePeer(peer: DiscoveredServer) {
        peerUrlInterceptor.setBaseUrl(peer.baseUrl())
        _state.update { it.copy(viaPeer = peer, step = OnboardingStep.HOUSEHOLD, error = null) }
        loadHouseholds()
    }

    /** The endpoint set the current join flow talks to (a peer phone or the server). */
    private fun api(): SyncApi = if (_state.value.viaPeer != null) peerSyncApi else syncApi

    fun chooseServer(rawUrl: String) {
        val url = normalizeUrl(rawUrl) ?: run {
            _state.update { it.copy(error = context.getString(R.string.onboarding_error_invalid_address)) }
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
            runCatching { api().listHouseholds() }
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
            runCatching { api().joinHousehold(id, JoinHouseholdRequest(pin)) }
                .onSuccess { adopt(it) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = errorText(e)) } }
        }
    }

    fun goToCreate() = _state.update { it.copy(step = OnboardingStep.CREATE, error = null) }

    fun createHousehold(name: String, size: Int, pin: String?) {
        if (name.isBlank()) {
            _state.update { it.copy(error = context.getString(R.string.onboarding_error_name_required)) }
            return
        }
        if (_state.value.serverless) {
            createServerlessHousehold(name, size, pin)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val body = CreateHouseholdRequest(
                name = name.trim(),
                settings = HouseholdSettings(householdSize = size),
                pin = pin?.takeIf { it.isNotBlank() },
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

    /**
     * Mint a household right on this phone — no server involved. The id/invite code are
     * generated locally in the exact shapes the server uses (uuid / ~16 url-safe chars),
     * so a server can adopt this household later ("attach a server" in Settings) and
     * other phones join through the peer endpoints ([PeerSyncServer]) with the standard
     * flow. The meta HLC stamp makes this device's name/settings the newest copy.
     */
    private fun createServerlessHousehold(name: String, size: Int, pin: String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            settings.setHousehold(code = code, id = UUID.randomUUID().toString(), name = name.trim())
            settings.setHouseholdSize(size)
            settings.setHouseholdPin(pin?.takeIf { it.isNotBlank() })
            settings.setHouseholdMetaHlc(syncClock.stamp().pack())
            // P2P is a serverless household's only transport — switch it on explicitly
            // so it stays on even if a server is attached later.
            settings.setP2pEnabled(true)
            settings.setLocalOnly(false)
            // Pre-fill the pantry with curated staples — creator path only, like on the server.
            pantryRepository.seedDefaults()
            _state.update { it.copy(busy = false, error = null) }
        }
    }

    /** Persist membership + household-wide settings; AppViewModel then flips to Onboarded. */
    private suspend fun adopt(dto: HouseholdDto) {
        // Clear busy now so the (host-scoped) VM isn't left "busy" after the screen leaves.
        _state.update { it.copy(busy = false, error = null) }
        settings.setHousehold(code = dto.inviteCode, id = dto.householdId, name = dto.name)
        settings.setHouseholdSize(dto.settings.householdSize)
        // Joining through a peer phone = joining a serverless household: P2P is its
        // only transport, so switch it on explicitly (like the founding phone does).
        if (_state.value.viaPeer != null) settings.setP2pEnabled(true)
        // A real household supersedes local-only mode; clear the flag so it stays accurate.
        settings.setLocalOnly(false)
        // Pull existing household data right away — without this the first sync would
        // only happen on the next periodic tick, so a new member sees an empty app.
        syncTrigger.requestSync()
    }

    fun back() = _state.update {
        when (it.step) {
            OnboardingStep.CREATE ->
                // Founding serverless came from the peer step; joining via a peer keeps
                // its household list; everything else came from the server's list.
                if (it.serverless && it.viaPeer == null) it.copy(step = OnboardingStep.PEERS, error = null)
                else it.copy(step = OnboardingStep.HOUSEHOLD, error = null)
            OnboardingStep.HOUSEHOLD ->
                if (it.viaPeer != null) it.copy(step = OnboardingStep.PEERS, viaPeer = null, error = null)
                else it.copy(step = OnboardingStep.SERVER, error = null)
            OnboardingStep.PEERS -> it.copy(step = OnboardingStep.MODE, serverless = false, error = null)
            OnboardingStep.SERVER -> it.copy(step = OnboardingStep.MODE, error = null)
            OnboardingStep.MODE -> it
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val t = raw.trim().ifEmpty { return null }
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    private fun errorText(t: Throwable): String = when {
        t.message?.contains("403") == true -> context.getString(R.string.onboarding_error_pin_wrong)
        t.message?.contains("Server URL not configured") == true -> context.getString(R.string.onboarding_error_no_server)
        else -> context.getString(R.string.onboarding_error_unreachable)
    }
}
