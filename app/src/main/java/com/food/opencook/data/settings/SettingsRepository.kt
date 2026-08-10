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
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.ui.mealplan.MealPlanSlots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /**
     * One setting, read reactively. [DataStore.data] re-emits the whole preference map on
     * **every** write, no matter which key changed — and the app writes constantly (the HLC
     * is persisted per tick, see SyncClock). Without the dedupe every collector would be woken
     * for values that did not change; the one place that genuinely wants repeats (PeerAdvertiser's
     * standby retry) keys on its own foreground flow, not on these.
     */
    private fun <T> pref(read: (Preferences) -> T): Flow<T> =
        dataStore.data.map(read).distinctUntilChanged()

    val serverUrl: Flow<String?> = pref { it[SERVER_URL] }
    val householdCode: Flow<String?> = pref { it[HOUSEHOLD_CODE] }
    val householdId: Flow<String?> = pref { it[HOUSEHOLD_ID] }
    /** Human-readable household name shown in Settings (cached from the server). */
    val householdName: Flow<String?> = pref { it[HOUSEHOLD_NAME] }

    /** Use Material You (wallpaper-based) colors instead of the brand palette. Default off. */
    val dynamicColor: Flow<Boolean> = pref { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /**
     * Text size factor applied on top of the device's own font size — recipes are read
     * from arm's length at the stove. Clamped on read so shortening [FontScales.STEPS]
     * later can't leave a device stuck at an unreachable size.
     */
    val fontScale: Flow<Float> = pref {
        (it[FONT_SCALE] ?: FontScales.DEFAULT).coerceIn(FontScales.MIN, FontScales.MAX)
    }

    suspend fun setFontScale(scale: Float) {
        dataStore.edit { it[FONT_SCALE] = scale.coerceIn(FontScales.MIN, FontScales.MAX) }
    }

    /**
     * The user explicitly chose to use openCook on this device only — no server, no
     * household. Lets the app gate past onboarding without a household (offline-first).
     * Cleared again when a household is joined, so it always means "currently local-only".
     */
    val localOnly: Flow<Boolean> = pref { it[LOCAL_ONLY] ?: false }

    /** Whether the one-time swipe peek-hint has been shown — tracked per list, so both the
     *  shopping list and the pantry each demonstrate the gesture once. */
    val swipeHintSeenShopping: Flow<Boolean> = pref { it[SWIPE_HINT_SEEN_SHOPPING] ?: false }
    val swipeHintSeenPantry: Flow<Boolean> = pref { it[SWIPE_HINT_SEEN_PANTRY] ?: false }

    suspend fun setSwipeHintSeenShopping() {
        dataStore.edit { it[SWIPE_HINT_SEEN_SHOPPING] = true }
    }

    suspend fun setSwipeHintSeenPantry() {
        dataStore.edit { it[SWIPE_HINT_SEEN_PANTRY] = true }
    }

    suspend fun setLocalOnly(enabled: Boolean) {
        dataStore.edit { it[LOCAL_ONLY] = enabled }
    }

    /**
     * Language of recipe CONTENT (AI extraction, categories, grocery keywords, staples).
     * Household-wide (synced); null means "follow this device's system language".
     */
    val contentLanguage: Flow<String?> = pref { it[CONTENT_LANGUAGE] }

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
     * Which meals of the day the household plans at all, as newline-joined
     * [com.food.opencook.util.MealTypes] keys. Household-wide (synced). Null/absent means
     * [MealPlanSlots.DEFAULT_PLANNED] — a single lunch slot, i.e. exactly how the planner
     * behaved before slots existed, so an update never silently multiplies anyone's week.
     */
    val plannedMeals: Flow<List<String>> =
        pref { MealPlanSlots.plannedFromStored(it[PLANNED_MEALS]) }

    suspend fun plannedMealsOnce(): List<String> =
        MealPlanSlots.plannedFromStored(dataStore.data.first()[PLANNED_MEALS])

    suspend fun setPlannedMeals(keys: List<String>) {
        val stored = MealPlanSlots.toStored(keys)
        dataStore.edit { if (stored == null) it.remove(PLANNED_MEALS) else it[PLANNED_MEALS] = stored }
    }

    /**
     * The complete household-wide settings object as this device currently knows it.
     *
     * Every caller that PATCHes or serves household settings must go through this: the
     * object is merged as a whole, so building it field-by-field at a call site means the
     * next added setting gets silently wiped by whichever site forgot it.
     */
    suspend fun currentHouseholdSettings(): HouseholdSettings = HouseholdSettings(
        householdSize = householdSizeOnce(),
        contentLanguage = contentLanguageOnce(),
        plannedMeals = MealPlanSlots.toStored(plannedMealsOnce()),
    )

    /** Adopt a settings object received from the server or a peer. Absent fields keep the
     *  local value, so an older peer can't erase a setting it doesn't know about. */
    suspend fun applyHouseholdSettings(remote: HouseholdSettings) {
        setHouseholdSize(remote.householdSize)
        setContentLanguage(remote.contentLanguage)
        remote.plannedMeals?.let { setPlannedMeals(MealPlanSlots.plannedFromStored(it)) }
    }

    /**
     * People to cook for — a **household-wide** setting (set on the server, shared
     * across devices). Cached locally so the meal planner works offline; refreshed
     * from the server on join and on every sync.
     */
    val householdSize: Flow<Int> = pref { it[HOUSEHOLD_SIZE] ?: DEFAULT_HOUSEHOLD_SIZE }

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
    val p2pEnabled: Flow<Boolean> = pref { prefs ->
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
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LOCAL_ONLY = booleanPreferencesKey("local_only")
        val SWIPE_HINT_SEEN_SHOPPING = booleanPreferencesKey("swipe_hint_seen_shopping")
        val SWIPE_HINT_SEEN_PANTRY = booleanPreferencesKey("swipe_hint_seen_pantry")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val NODE_ID = stringPreferencesKey("node_id")
        val LAST_HLC = stringPreferencesKey("last_hlc")
        val HOUSEHOLD_META_HLC = stringPreferencesKey("household_meta_hlc")
        val HOUSEHOLD_PIN = stringPreferencesKey("household_pin")
        val P2P_ENABLED = booleanPreferencesKey("p2p_enabled")
        val PLANNED_MEALS = stringPreferencesKey("planned_meals")
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
 * The selectable text sizes, smallest first — single source of truth for the settings
 * slider, its labels and the stored value. Ordered and evenly perceived rather than
 * mathematically even; 1f is the canonical scale the type ramp was designed at.
 */
object FontScales {
    val STEPS = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
    const val DEFAULT = 1f
    val MIN = STEPS.first()
    val MAX = STEPS.last()

    /** Slider position for a stored factor — nearest step, so old values always land somewhere. */
    fun indexOf(scale: Float): Int =
        STEPS.indices.minByOrNull { kotlin.math.abs(STEPS[it] - scale) } ?: STEPS.indexOf(DEFAULT)
}

/**
 * Resolves the effective recipe content language ("de"/"en"). A tiny injectable seam so
 * callers (e.g. RecipeRepository) don't depend on the DataStore-backed [SettingsRepository]
 * directly and can be unit-tested with a trivial `ContentLanguageProvider { "de" }`.
 */
fun interface ContentLanguageProvider {
    suspend fun effective(): String
}
