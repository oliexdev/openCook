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

package com.food.opencook.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How often the automatic backup runs. */
enum class BackupFrequency(val days: Long) {
    DAILY(1),
    WEEKLY(7),
}

/** Everything the automatic backup needs to remember between runs. */
data class AutoBackupConfig(
    /** Persisted SAF tree uri of the folder the user picked; null = automatic backup off. */
    val folderUri: String? = null,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val keep: Int = DEFAULT_KEEP,
    val lastRunAt: Long = 0,
    val lastError: String? = null,
    /** Fingerprint of the data at the last successful run — lets an unchanged library
     *  skip rewriting an identical multi-hundred-MB file every night. */
    val lastFingerprint: String? = null,
) {
    val enabled: Boolean get() = folderUri != null

    companion object {
        const val DEFAULT_KEEP = 5
    }
}

@Singleton
class BackupSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<AutoBackupConfig> = dataStore.data.map { it.toConfig() }

    suspend fun configOnce(): AutoBackupConfig = dataStore.data.first().toConfig()

    /** Enable automatic backup into [folderUri] (a persisted SAF tree). */
    suspend fun setFolder(folderUri: String?) {
        dataStore.edit { prefs ->
            if (folderUri == null) prefs.remove(FOLDER) else prefs[FOLDER] = folderUri
        }
    }

    suspend fun setFrequency(frequency: BackupFrequency) {
        dataStore.edit { it[FREQUENCY] = frequency.name.let(::indexOfFrequency) }
    }

    suspend fun setKeep(keep: Int) {
        dataStore.edit { it[KEEP] = keep.coerceIn(1, 50) }
    }

    suspend fun recordSuccess(at: Long, fingerprint: String) {
        dataStore.edit {
            it[LAST_RUN] = at
            it[LAST_FINGERPRINT] = fingerprint
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordFailure(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private fun Preferences.toConfig() = AutoBackupConfig(
        folderUri = this[FOLDER],
        frequency = BackupFrequency.entries.getOrElse(this[FREQUENCY] ?: 0) { BackupFrequency.DAILY },
        keep = this[KEEP] ?: AutoBackupConfig.DEFAULT_KEEP,
        lastRunAt = this[LAST_RUN] ?: 0,
        lastError = this[LAST_ERROR],
        lastFingerprint = this[LAST_FINGERPRINT],
    )

    private fun indexOfFrequency(name: String) =
        BackupFrequency.entries.indexOfFirst { it.name == name }.coerceAtLeast(0)

    private companion object {
        val FOLDER = stringPreferencesKey("backup_folder_uri")
        val FREQUENCY = intPreferencesKey("backup_frequency")
        val KEEP = intPreferencesKey("backup_keep")
        val LAST_RUN = longPreferencesKey("backup_last_run")
        val LAST_ERROR = stringPreferencesKey("backup_last_error")
        val LAST_FINGERPRINT = stringPreferencesKey("backup_last_fingerprint")
    }
}
