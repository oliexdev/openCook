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

package com.food.opencook.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.R
import com.food.opencook.data.LocalDataWiper
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.dto.CreateHouseholdRequest
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.data.remote.dto.PatchHouseholdRequest
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.sync.SyncClock
import com.food.opencook.sync.SyncEngine
import com.food.opencook.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val syncApi: SyncApi,
    private val syncEngine: SyncEngine,
    private val wiper: LocalDataWiper,
    private val syncClock: SyncClock,
    private val syncTrigger: SyncTrigger,
    private val recipeDao: RecipeDao,
    private val baseUrlInterceptor: BaseUrlInterceptor,
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

    /** Phone-to-phone sync master switch (effective value incl. the serverless default). */
    val p2pEnabled: StateFlow<Boolean> =
        settings.p2pEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setP2pEnabled(enabled: Boolean) = viewModelScope.launch { settings.setP2pEnabled(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    /** Household-wide recipe content language (null = follow each device's system language). */
    val contentLanguage: StateFlow<String?> =
        settings.contentLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Persist locally + PATCH the server so the whole household converges (carries the
     *  current size too, since the server merges the settings object as a whole). In a
     *  serverless household there is nothing to PATCH — instead the edit stamps the
     *  household-meta HLC, so peers adopt this device's copy on their next exchange. */
    fun setContentLanguage(lang: String?) {
        viewModelScope.launch {
            settings.setContentLanguage(lang)
            if (settings.serverUrlOnce().isNullOrBlank()) {
                settings.setHouseholdMetaHlc(syncClock.stamp().pack())
                return@launch
            }
            val id = settings.householdIdOnce()
            val code = settings.householdCodeOnce()
            if (id != null && code != null) {
                val size = settings.householdSizeOnce()
                runCatching {
                    syncApi.patchHousehold(
                        id, code,
                        PatchHouseholdRequest(settings = HouseholdSettings(householdSize = size, contentLanguage = lang)),
                    )
                }.onFailure { _message.update { context.getString(R.string.settings_msg_size_update_failed) } }
            }
        }
    }

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
            if (settings.serverUrlOnce().isNullOrBlank()) {
                // Serverless: stamp the meta so peers adopt the new size (see SyncResponder).
                settings.setHouseholdMetaHlc(syncClock.stamp().pack())
                return@launch
            }
            val id = settings.householdIdOnce()
            val code = settings.householdCodeOnce()
            if (id != null && code != null) {
                runCatching {
                    syncApi.patchHousehold(id, code, PatchHouseholdRequest(settings = HouseholdSettings(clamped)))
                }.onFailure { _message.update { context.getString(R.string.settings_msg_size_update_failed) } }
            }
        }
    }

    /**
     * Attach a real server to a household that was founded serverless: create the
     * household there under its *existing* id + invite code (the server accepts
     * client-supplied identities for exactly this hand-over, idempotently), then make
     * the URL this device's server and re-upload every locally held photo — images
     * "uploaded" to peer phones never reached a server, and content-addressing makes
     * the re-upload land under the same names the imageRef messages already carry.
     * The next sync round pushes the whole message log up.
     */
    fun attachServer(rawUrl: String) {
        val url = rawUrl.trim().ifEmpty { return }
            .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it" }
        viewModelScope.launch {
            _busy.update { true }
            _message.update { null }
            val id = settings.householdIdOnce()
            val code = settings.householdCodeOnce()
            if (id == null || code == null) {
                _busy.update { false }
                return@launch
            }
            baseUrlInterceptor.setBaseUrl(url)
            runCatching {
                syncApi.createHousehold(
                    CreateHouseholdRequest(
                        name = settings.householdNameOnce().orEmpty(),
                        settings = HouseholdSettings(
                            householdSize = settings.householdSizeOnce(),
                            contentLanguage = settings.contentLanguageOnce(),
                        ),
                        pin = settings.householdPinOnce(),
                        id = id,
                        inviteCode = code,
                    ),
                )
            }.onSuccess {
                settings.setServerUrl(url)
                recipeDao.resetRemoteNamesForReupload()
                syncTrigger.requestSync()
                _message.update { context.getString(R.string.settings_msg_server_attached) }
            }.onFailure {
                // Restore the interceptor to the stored (empty) address so later requests
                // don't silently keep talking to a server the user never confirmed.
                baseUrlInterceptor.setBaseUrl(settings.serverUrlOnce())
                _message.update { context.getString(R.string.settings_msg_attach_failed) }
            }
            _busy.update { false }
        }
    }

    /** Leaving wipes the household's local data (recipes, sync log, photos) and clears
     *  the membership; the app returns to onboarding automatically. Keeping any of it
     *  would leak private content and — for the sync log — push old messages into a
     *  freshly-joined household on the next sync round. */
    fun leaveHousehold() = viewModelScope.launch { wiper.wipeAndLeave() }

    /** Leave local-only mode: with no household joined this sends the app back to
     *  onboarding (server → household), where the offline data syncs up after joining. */
    fun connectToServer() = viewModelScope.launch { settings.setLocalOnly(false) }

    fun synchronize() = run {
        _message.update { null }
        viewModelScope.launch {
            _busy.update { true }
            when (val result = syncEngine.sync()) {
                is SyncEngine.Result.Ok -> _message.update { context.getString(R.string.settings_msg_synced, result.pulled) }
                SyncEngine.Result.NoHousehold -> _message.update { context.getString(R.string.settings_msg_no_household) }
                SyncEngine.Result.UnknownHousehold -> _message.update {
                    context.getString(R.string.settings_msg_household_missing)
                }
                is SyncEngine.Result.Failed -> _message.update {
                    if (result.message.isBlank()) context.getString(R.string.settings_msg_sync_failed_generic)
                    else context.getString(R.string.settings_msg_sync_failed, result.message)
                }
            }
            _busy.update { false }
        }
    }
}
