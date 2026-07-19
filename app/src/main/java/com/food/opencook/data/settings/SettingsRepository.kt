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
     * Global text size multiplier, on top of the system font scale — mainly for reading
     * ingredients/steps while cooking. Per-device only (not synced): unlike [contentLanguage],
     * how big someone wants their own screen's text has nothing to do with the household's
     * shared data, so it stays local, like [dynamicColor].
     */
    val textScale: Flow<TextScale> = dataStore.data.map { prefs ->
        TextScale.fromStored(prefs[TEXT_SCALE])
    }

    suspend fun setTextScale(scale: TextScale) {
        dataStore.edit { it[TEXT_SCALE] = scale.name }
    }

    /** Recipe list layout — album grid vs. the default photo-forward list. Per-device,
     *  like [textScale]: a display preference, not household data. */
    val recipeViewMode: Flow<RecipeViewMode> = dataStore.data.map { prefs ->
        RecipeViewMode.fromStored(prefs[RECIPE_VIEW_MODE])
    }

    suspend fun setRecipeViewMode(mode: RecipeViewMode) {
        dataStore.edit { it[RECIPE_VIEW_MODE] = mode.name }
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
            it.remove(HOUSEHOLD_META_HLC)
            it.remove(HOUSEHOLD_PIN)
        }
    }

    suspend fun householdIdOnce(): String? = dataStore.data.first()[HOUSEHOLD_ID]
    suspend fun householdCodeOnce(): String? = dataStore.data.first()[HOUSEHOLD_CODE]
    suspend fun serverUrlOnce(): String? = dataStore.data.first()[SERVER_URL]
    suspend fun householdNameOnce(): String? = dataStore.data.first()[HOUSEHOLD_NAME]

    /**
     * HLC stamp of the household meta (name/settings/PIN) this device holds. Serverless
     * households have no authoritative store, so peers exchange their meta with this
     * stamp and everyone adopts the newest copy (see SyncEngine/SyncResponder).
     */
    suspend fun householdMetaHlcOnce(): String? = dataStore.data.first()[HOUSEHOLD_META_HLC]
    suspend fun setHouseholdMetaHlc(packed: String) {
        dataStore.edit { it[HOUSEHOLD_META_HLC] = packed }
    }

    /** Join-PIN of a serverless household (empty/null = open). Server-backed households
     *  keep their PIN on the server; this is only consulted by the peer join endpoint. */
    suspend fun householdPinOnce(): String? = dataStore.data.first()[HOUSEHOLD_PIN]
    suspend fun setHouseholdPin(pin: String?) {
        dataStore.edit {
            if (pin.isNullOrBlank()) it.remove(HOUSEHOLD_PIN) else it[HOUSEHOLD_PIN] = pin
        }
    }

    /**
     * The phone-to-phone sync master switch: peer fallback in the engine, mDNS
     * visibility and the standby foreground service all hang on it. Unset means
     * "default by household kind": ON for serverless households (P2P is their only
     * transport), OFF when a server is configured (it covers the job invisibly).
     */
    val p2pEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[P2P_ENABLED] ?: prefs[SERVER_URL].isNullOrBlank()
    }

    suspend fun p2pEnabledOnce(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[P2P_ENABLED] ?: prefs[SERVER_URL].isNullOrBlank()
    }

    suspend fun setP2pEnabled(enabled: Boolean) {
        dataStore.edit { it[P2P_ENABLED] = enabled }
    }

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
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val RECIPE_VIEW_MODE = stringPreferencesKey("recipe_view_mode")
        val LOCAL_ONLY = booleanPreferencesKey("local_only")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val NODE_ID = stringPreferencesKey("node_id")
        val LAST_HLC = stringPreferencesKey("last_hlc")
        val HOUSEHOLD_META_HLC = stringPreferencesKey("household_meta_hlc")
        val HOUSEHOLD_PIN = stringPreferencesKey("household_pin")
        val P2P_ENABLED = booleanPreferencesKey("p2p_enabled")
    }
}

/**
 * Bundled recipe content languages (ISO 639-1), English first as the fallback. Single source of
 * truth: the Settings picker ([com.food.opencook.ui.settings.SettingsScreen]'s content-language
 * dialog) and the domain-list loader ([com.food.opencook.data.localization.LocalizedLists]) both
 * derive from this. Add a code here when you ship a new `values-<code>/arrays.xml` +
 * `server/app/i18n/<code>.json`.
 */
object ContentLanguages {
    val CODES = listOf("en", "de")
}

/**
 * Resolves the effective recipe content language ("de"/"en"). A tiny injectable seam so
 * callers (e.g. RecipeRepository) don't depend on the DataStore-backed [SettingsRepository]
 * directly and can be unit-tested with a trivial `ContentLanguageProvider { "de" }`.
 */
fun interface ContentLanguageProvider {
    suspend fun effective(): String
}

/**
 * A global text-size step applied on top of the system font scale (Settings > Appearance).
 * Stored by [name] so adding a step later doesn't renumber existing values. Applied via
 * [com.food.opencook.ui.theme.OpenCookTheme], which multiplies the ambient [LocalDensity]
 * font scale — every `sp` in the app grows with it, not just cook-mode text.
 */
enum class TextScale(val multiplier: Float) {
    NORMAL(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
    ;

    companion object {
        fun fromStored(name: String?): TextScale = entries.find { it.name == name } ?: NORMAL
    }
}

/**
 * The recipe list's layout (Settings has no entry for this — it's a toggle right on the
 * Recipes screen, since it's about that one screen rather than app-wide appearance).
 * [LIST] is the existing photo-forward card, one wide tile per row, adaptive columns on
 * larger screens. [GRID] is a denser "album" view: square image tiles, multiple columns
 * even on a phone. See [com.food.opencook.util.RecipesLayout] for the column math.
 */
enum class RecipeViewMode {
    LIST,
    GRID,
    ;

    companion object {
        fun fromStored(name: String?): RecipeViewMode = entries.find { it.name == name } ?: LIST
    }
}
