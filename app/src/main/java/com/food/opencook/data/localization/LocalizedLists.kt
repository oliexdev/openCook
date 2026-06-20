package com.food.opencook.data.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.food.opencook.R
import com.food.opencook.data.recipeimport.IngredientLineParser
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory
import com.food.opencook.util.IngredientStaples
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the language-specific domain word-lists (grocery-aisle keywords, staples, units)
 * from the per-locale arrays.xml resources for the effective recipe content language and
 * pushes them into the domain singletons. Resources are read via a locale-overridden context so
 * lists follow the household's `contentLanguage` setting, not just the device UI language.
 */
@Singleton
class LocalizedLists @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    /** Reload for the current effective content language. Safe to call repeatedly. */
    suspend fun reload() {
        val lang = settings.effectiveContentLanguage(settings.contentLanguageOnce())
        apply(resourcesFor(lang))
    }

    private fun apply(res: Resources) {
        GroceryCategories.setRules(
            listOf(
                GroceryCategory.FROZEN to res.lower(R.array.grocery_kw_frozen),
                GroceryCategory.MEAT_FISH to res.lower(R.array.grocery_kw_meat_fish),
                GroceryCategory.PRODUCE to res.lower(R.array.grocery_kw_produce),
                GroceryCategory.BAKERY to res.lower(R.array.grocery_kw_bakery),
                GroceryCategory.DRINKS to res.lower(R.array.grocery_kw_drinks),
                GroceryCategory.SPICES to res.lower(R.array.grocery_kw_spices),
                GroceryCategory.PANTRY to res.lower(R.array.grocery_kw_pantry),
                GroceryCategory.DAIRY to res.lower(R.array.grocery_kw_dairy),
            ),
        )
        IngredientStaples.setData(
            all = res.lower(R.array.ingredient_staples).toSet(),
            // Pantry defaults keep their display capitalization (shown as-is in the pantry).
            pantry = res.getStringArray(R.array.pantry_defaults).toList(),
        )
        IngredientLineParser.setUnits(res.lower(R.array.ingredient_units).toSet())
    }

    private fun resourcesFor(lang: String): Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(lang))
        return context.createConfigurationContext(config).resources
    }

    private fun Resources.lower(id: Int): List<String> = getStringArray(id).map { it.lowercase() }
}
