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

package com.food.opencook.data

import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.OpenCookDatabase
import com.food.opencook.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erases every trace of the current household from this device. Used when leaving
 * a household (so private recipes/photos don't linger and so the message log can't
 * leak into a freshly-joined household on the next sync) and by the admin
 * reset-database flow.
 *
 * Wipes:
 *  - every Room table (recipes, ingredients, images rows, sync message log, jobs,
 *    meal plans, shopping list, pantry, likes, product cache)
 *  - every image file on disk (captures and downloaded server crops)
 *  - the household credential in DataStore (flips the app back to onboarding)
 *
 * Keeps:
 *  - the device's stable sync node id and HLC (reusable in any future household)
 *  - app preferences (theme, dynamic color, etc.)
 */
@Singleton
class LocalDataWiper @Inject constructor(
    private val database: OpenCookDatabase,
    private val imageStore: ImageStore,
    private val settings: SettingsRepository,
) {
    suspend fun wipeAndLeave() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        imageStore.wipeAll()
        settings.clearHousehold()
    }
}
