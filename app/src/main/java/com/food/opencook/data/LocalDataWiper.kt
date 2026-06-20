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
