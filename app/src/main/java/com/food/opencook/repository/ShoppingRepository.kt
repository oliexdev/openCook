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

package com.food.opencook.repository

import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.Numbers
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.ShoppingMessageEncoder
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Shopping list: offline-first, syncs over the same engine as recipes. */
@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val messageRecorder: MessageRecorder,
    private val pantryRepository: PantryRepository,
) {
    fun observeItems(): Flow<List<ShoppingItemEntity>> = shoppingDao.observeAll()

    /**
     * "Were this dish's ingredients procured?" for the self-healing roll-forward:
     * a shopping list was generated for (recipe, day) and nothing is left unchecked.
     * False when no list was ever generated — we can't assume the food is on hand.
     */
    suspend fun isProcured(recipeId: String, date: String): Boolean =
        shoppingDao.countBySource(recipeId, date) > 0 && shoppingDao.countOpenBySource(recipeId, date) == 0

    /** Are this dish's ingredients on the shopping list for [date] at all (bought or still open)? */
    suspend fun hasItemsFor(recipeId: String, date: String): Boolean =
        shoppingDao.countBySource(recipeId, date) > 0

    /** Re-tag a dish's shopping items from [fromDate] to [toDate] when the dish is rescheduled,
     *  so provenance ("which day is this for?") follows the move. */
    suspend fun moveSource(recipeId: String, fromDate: String, toDate: String) {
        val now = System.currentTimeMillis()
        shoppingDao.getAllBySource(recipeId, fromDate).forEach { item ->
            val updated = item.copy(sourceDate = toDate, updatedAt = now)
            shoppingDao.upsert(updated)
            messageRecorder.record(ShoppingMessageEncoder.encode(updated))
        }
    }

    suspend fun addItem(
        text: String,
        quantity: Double? = null,
        unit: String? = null,
        sourceRecipeId: String? = null,
        sourceDate: String? = null,
        manual: Boolean = false,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val cleanUnit = unit?.trim()?.takeIf { it.isNotEmpty() }

        // Consolidate into an existing open item of the same name. Same unit (or
        // both unit-less) → sum the numbers (400 g + 100 g = 500 g). Different
        // units can't be summed → keep the existing entry unchanged. The single
        // [sourceRecipeId] provenance stays with whichever dish contributed first,
        // but every contributing dish accumulates in [sourceRecipeIds] for the label,
        // and a manual touch latches [manual] on so the line beats the pantry cover.
        val existing = shoppingDao.findOpenByText(trimmed)
        val item = when {
            existing == null -> ShoppingItemEntity(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                quantity = quantity,
                unit = cleanUnit,
                checked = false,
                position = (now % Int.MAX_VALUE).toInt(),
                sourceRecipeId = sourceRecipeId,
                sourceDate = sourceDate,
                manual = manual,
                sourceRecipeIds = sourceRecipeId,
                createdAt = now,
                updatedAt = now,
            )
            existing.unit == cleanUnit -> existing.copy(
                quantity = sumOrNull(existing.quantity, quantity),
                manual = existing.manual || manual,
                sourceRecipeIds = mergeSources(existing.sourceRecipeIds, sourceRecipeId),
                updatedAt = now,
            )
            else -> existing // different units — leave as is
        }
        shoppingDao.upsert(item)
        messageRecorder.record(ShoppingMessageEncoder.encode(item))
    }

    private fun sumOrNull(a: Double?, b: Double?): Double? =
        if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

    /** Append [add] to the comma-separated id set, preserving order and dropping dups. */
    private fun mergeSources(existing: String?, add: String?): String? {
        if (add.isNullOrBlank()) return existing
        val ids = existing?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (add in ids) return existing
        return (ids + add).joinToString(",")
    }

    /**
     * Finish the shop: checked items were bought, so move them into the pantry (stock)
     * and drop them from the list (tombstoned, so both sides sync). This is the "in" half
     * of the pantry cycle — "cooked" is the "out" half (see [PantryRepository.consume]).
     */
    suspend fun checkoutChecked() {
        val checked = shoppingDao.getChecked()
        checked.forEach { pantryRepository.addItem(it.text) } // dedupe-safe, records its own sync msg
        checked.forEach { shoppingDao.deleteById(it.id) }
        messageRecorder.record(checked.flatMap { ShoppingMessageEncoder.tombstone(it.id) })
    }

    /** Remove still-open items that came from a recipe (across all days); checked items stay. */
    suspend fun removeOpenForRecipe(recipeId: String) {
        val open = shoppingDao.getOpenByRecipe(recipeId)
        open.forEach { shoppingDao.deleteById(it.id) }
        messageRecorder.record(open.flatMap { ShoppingMessageEncoder.tombstone(it.id) })
    }

    /** Wipe the entire shopping list (tombstoned, so the clear syncs across devices). */
    suspend fun clearAll() {
        val all = shoppingDao.getAll()
        all.forEach { shoppingDao.deleteById(it.id) }
        messageRecorder.record(all.flatMap { ShoppingMessageEncoder.tombstone(it.id) })
    }

    suspend fun setChecked(id: String, checked: Boolean) {
        val item = shoppingDao.getById(id) ?: return
        val updated = item.copy(checked = checked, updatedAt = System.currentTimeMillis())
        shoppingDao.upsert(updated)
        messageRecorder.record(ShoppingMessageEncoder.encode(updated))
    }

    suspend fun deleteItem(id: String) {
        shoppingDao.deleteById(id)
        messageRecorder.record(ShoppingMessageEncoder.tombstone(id))
    }

    /**
     * Add a recipe's ingredients to the list (amount → quantity), skipping any
     * whose name is already in the pantry ([skipNames], lower-cased). [sourceDate]
     * tags the items with their planned day so the "not found" flow can find the
     * dish to replace.
     */
    suspend fun addFromRecipe(
        recipe: RecipeWithDetails,
        skipNames: Set<String> = emptySet(),
        sourceDate: String? = null,
        scale: Double = 1.0,
    ) {
        // Idempotent per dish: if this recipe already put its ingredients on the list,
        // don't add them again (a second tap must not double the quantities). Scoped to
        // the planned day when there is one; otherwise (recipe-screen add) to the recipe
        // across all days. The replace-the-dish flow deletes the old lines first, so the
        // incoming dish is never blocked.
        val rid = recipe.recipe.id
        val alreadyOnList =
            if (sourceDate != null) shoppingDao.countBySource(rid, sourceDate) > 0
            else shoppingDao.getOpenByRecipe(rid).isNotEmpty()
        if (alreadyOnList) return

        recipe.ingredients.sortedBy { it.position }.forEach { ingredient ->
            if (!IngredientMatch.containsLike(skipNames, ingredient.name)) {
                addItem(
                    text = ingredient.name,
                    quantity = Numbers.scaleQuantity(ingredient.quantity, scale),
                    unit = ingredient.unit,
                    sourceRecipeId = recipe.recipe.id,
                    sourceDate = sourceDate,
                )
            }
        }
    }

    /**
     * "Ingredient not found → replace the dish": drop the open items uniquely from
     * the old dish on [date] and add the alternative recipe's ingredients instead.
     * Consolidated (shared) lines keep their first-dish provenance and are left be.
     */
    suspend fun replaceMealContribution(
        oldRecipeId: String,
        date: String,
        newRecipe: RecipeWithDetails,
        skipNames: Set<String> = emptySet(),
        scale: Double = 1.0,
    ) {
        shoppingDao.getBySource(oldRecipeId, date).forEach { item ->
            shoppingDao.deleteById(item.id)
            messageRecorder.record(ShoppingMessageEncoder.tombstone(item.id))
        }
        addFromRecipe(newRecipe, skipNames, sourceDate = date, scale = scale)
    }
}
