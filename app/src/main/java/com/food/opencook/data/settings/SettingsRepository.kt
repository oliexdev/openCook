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

package com.food.opencook.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-configured settings backed by DataStore. The server is self-hosted on a
 * LAN/VPN and joined by an invite code — there is no account, so the only config
 * the app needs is the server base URL and the household code.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val serverUrl: Flow<String?> = dataStore.data.map { it[SERVER_URL] }
    val householdCode: Flow<String?> = dataStore.data.map { it[HOUSEHOLD_CODE] }
    val householdId: Flow<String?> = dataStore.data.map { it[HOUSEHOLD_ID] }
    /** Human-readable household name shown in Settings (cached from the server). */
    val householdName: Flow<String?> = dataStore.data.map { it[HOUSEHOLD_NAME] }

    /** Use Material You (wallpaper-based) colors instead of the brand palette. Default off. */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /**
     * The user explicitly chose to use openCook on this device only — no server, no
     * household. Lets the app gate past onboarding without a household (offline-first).
     * Cleared again when a household is joined, so it always means "currently local-only".
     */
    val localOnly: Flow<Boolean> = dataStore.data.map { it[LOCAL_ONLY] ?: false }

    suspend fun setLocalOnly(enabled: Boolean) {
        dataStore.edit { it[LOCAL_ONLY] = enabled }
    }

    /**
     * Language of recipe CONTENT (AI extraction, categories, grocery keywords, staples).
     * Household-wide (synced); null means "follow this device's system language".
     */
    val contentLanguage: Flow<String?> = dataStore.data.map { it[CONTENT_LANGUAGE] }

    suspend fun setContentLanguage(lang: String?) {
        dataStore.edit {
            if (lang.isNullOrBlank()) it.remove(CONTENT_LANGUAGE) else it[CONTENT_LANGUAGE] = lang
        }
    }

    suspend fun contentLanguageOnce(): String? = dataStore.data.first()[CONTENT_LANGUAGE]

    /** Resolve the effective content language: explicit setting, else the device language. */
    fun effectiveContentLanguage(stored: String?): String =
        stored?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language

    /**
     * People to cook for — a **household-wide** setting (set on the server, shared
     * across devices). Cached locally so the meal planner works offline; refreshed
     * from the server on join and on every sync.
     */
    val householdSize: Flow<Int> = dataStore.data.map { it[HOUSEHOLD_SIZE] ?: DEFAULT_HOUSEHOLD_SIZE }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[SERVER_URL] = url.trim() }
    }

    suspend fun setHouseholdSize(size: Int) {
        dataStore.edit { it[HOUSEHOLD_SIZE] = size.coerceIn(1, 20) }
    }

    suspend fun setHouseholdName(name: String) {
        dataStore.edit { it[HOUSEHOLD_NAME] = name }
    }

    suspend fun householdSizeOnce(): Int = dataStore.data.first()[HOUSEHOLD_SIZE] ?: DEFAULT_HOUSEHOLD_SIZE

    /** Joining/creating stores the shared code, the server id and the display name. */
    suspend fun setHousehold(code: String, id: String, name: String) {
        dataStore.edit {
            it[HOUSEHOLD_CODE] = code.trim()
            it[HOUSEHOLD_ID] = id
            it[HOUSEHOLD_NAME] = name
        }
    }

    /** Leave the household: clears membership so the app returns to onboarding. The
     *  sync node id and HLC are kept (this device's identity/clock are reusable). */
    suspend fun clearHousehold() {
        dataStore.edit {
            it.remove(HOUSEHOLD_CODE)
            it.remove(HOUSEHOLD_ID)
            it.remove(HOUSEHOLD_NAME)
        }
    }

    suspend fun householdIdOnce(): String? = dataStore.data.first()[HOUSEHOLD_ID]
    suspend fun householdCodeOnce(): String? = dataStore.data.first()[HOUSEHOLD_CODE]
    suspend fun serverUrlOnce(): String? = dataStore.data.first()[SERVER_URL]

    /** This device's stable sync node id, generated once on first use. */
    suspend fun ensureNodeId(): String {
        val existing = dataStore.data.first()[NODE_ID]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[NODE_ID] = generated }
        return generated
    }

    /** Persisted last HLC (packed), so the clock stays monotonic across restarts. */
    suspend fun lastHlc(): String? = dataStore.data.first()[LAST_HLC]
    suspend fun setLastHlc(packed: String) {
        dataStore.edit { it[LAST_HLC] = packed }
    }

    private companion object {
        const val DEFAULT_HOUSEHOLD_SIZE = 2
        val SERVER_URL = stringPreferencesKey("server_url")
        val HOUSEHOLD_CODE = stringPreferencesKey("household_code")
        val HOUSEHOLD_ID = stringPreferencesKey("household_id")
        val HOUSEHOLD_NAME = stringPreferencesKey("household_name")
        val HOUSEHOLD_SIZE = intPreferencesKey("household_size")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LOCAL_ONLY = booleanPreferencesKey("local_only")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val NODE_ID = stringPreferencesKey("node_id")
        val LAST_HLC = stringPreferencesKey("last_hlc")
    }
}

/**
 * Resolves the effective recipe content language ("de"/"en"). A tiny injectable seam so
 * callers (e.g. RecipeRepository) don't depend on the DataStore-backed [SettingsRepository]
 * directly and can be unit-tested with a trivial `ContentLanguageProvider { "de" }`.
 */
fun interface ContentLanguageProvider {
    suspend fun effective(): String
}
