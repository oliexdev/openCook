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
        val NODE_ID = stringPreferencesKey("node_id")
        val LAST_HLC = stringPreferencesKey("last_hlc")
    }
}
