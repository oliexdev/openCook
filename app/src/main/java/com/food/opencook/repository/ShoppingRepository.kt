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
     * Everything one [addFromRecipe] call changed, enough to reverse it exactly.
     * [created] are rows that did not exist before; [modified] holds the prior state of rows
     * the call merged into.
     */
    data class ShoppingAddUndo(
        val created: List<String>,
        val modified: List<ShoppingItemEntity>,
    )

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
        addItemTracked(text, quantity, unit, sourceRecipeId, sourceDate, manual)
    }

    /**
     * [addItem], reporting what it did: the id of the row that now carries the line, and the
     * row **as it was before** — null when the row is brand new. That pair is everything an
     * undo needs, and it has to come from in here: a caller cannot tell afterwards whether a
     * line became its own row or was summed into one that already existed.
     */
    private suspend fun addItemTracked(
        text: String,
        quantity: Double? = null,
        unit: String? = null,
        sourceRecipeId: String? = null,
        sourceDate: String? = null,
        manual: Boolean = false,
    ): Pair<String, ShoppingItemEntity?>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
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
        return item.id to existing
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

    /**
     * Take a dish's ingredients back off the list once that dish has left the plan for good —
     * but only the lines that are **solely** its doing and still open, returned so the
     * deletion can be undone as one piece.
     *
     * Scoped to the recipe rather than to a (recipe, day) pair, because the same dish planned
     * on two days shares one consolidated row that records only the first of them. Deciding
     * *whether* the dish is really gone is the caller's job — it is a question about the plan,
     * not about the list (see `MealPlanRepository.isPlannedFrom`).
     *
     * Two deliberate exclusions. A line several dishes contributed to stays: "Nudeln 500 g"
     * built from 400 g + 100 g cannot be unpicked here, since a row records *who* contributed
     * but not *how much* each did, and taking the whole line would rob the other dish. And a
     * checked line stays because it is already in the trolley — the plan changing does not
     * un-buy it.
     */
    suspend fun removeContributionOf(recipeId: String): List<ShoppingItemEntity> {
        val own = shoppingDao.getOpenByRecipe(recipeId).filter { item ->
            item.sourceRecipeIds?.split(',')?.filter { it.isNotBlank() } == listOf(recipeId)
        }
        own.forEach { item ->
            shoppingDao.deleteById(item.id)
            messageRecorder.record(ShoppingMessageEncoder.tombstone(item.id))
        }
        return own
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
     * Restore items from a backup. Ids and timestamps come from the archive, so this
     * upserts the same rows rather than duplicating them — running a restore twice is a
     * no-op. Recording the changes means a restored list syncs out to the household like
     * any other edit. Never deletes: a restore only ever adds back.
     */
    suspend fun importItems(items: List<ShoppingItemEntity>) {
        if (items.isEmpty()) return
        items.forEach { shoppingDao.upsert(it) }
        messageRecorder.record(items.flatMap { ShoppingMessageEncoder.encode(it) })
    }

    /**
     * Add a recipe's ingredients to the list (amount → quantity). [sourceDate] tags the
     * items with their planned day so the "not found" flow can find the dish to replace.
     *
     * All ingredients become rows — pantry-covered and staple items are **not** dropped
     * here. Hiding them is the view layer's job (`ShoppingListViewModel`), which keeps the
     * rows in the DB so they stay syncable and can resurface via the "brauch ich doch" chip
     * or when the pantry item is removed.
     */
    suspend fun addFromRecipe(
        recipe: RecipeWithDetails,
        sourceDate: String? = null,
        scale: Double = 1.0,
    ): ShoppingAddUndo? {
        // Idempotent per dish: if this recipe already put its ingredients on the list,
        // don't add them again (a second tap must not double the quantities). Scoped to
        // the planned day when there is one; otherwise (recipe-screen add) to the recipe
        // across all days. The replace-the-dish flow deletes the old lines first, so the
        // incoming dish is never blocked.
        val rid = recipe.recipe.id
        val alreadyOnList =
            if (sourceDate != null) shoppingDao.countBySource(rid, sourceDate) > 0
            else shoppingDao.getOpenByRecipe(rid).isNotEmpty()
        if (alreadyOnList) return null

        val created = mutableListOf<String>()
        val modified = mutableListOf<ShoppingItemEntity>()
        recipe.ingredients.sortedBy { it.position }.forEach { ingredient ->
            val (id, before) = addItemTracked(
                text = ingredient.name,
                quantity = Numbers.scaleQuantity(ingredient.quantity, scale),
                unit = ingredient.unit,
                sourceRecipeId = recipe.recipe.id,
                sourceDate = sourceDate,
            ) ?: return@forEach
            if (before == null) created += id else modified += before
        }
        return ShoppingAddUndo(created, modified).takeIf { created.isNotEmpty() || modified.isNotEmpty() }
    }

    /**
     * Put the list back exactly as it stood before one [addFromRecipe] call: rows the call
     * created are removed, rows it summed into are restored to their old quantity and source
     * set. Rows created *and then* added to again by a second line of the same recipe are
     * simply removed — their "before" state belongs to this same call and must not survive.
     *
     * Not a general "remove this recipe": that is [removeOpenForRecipe], which cannot restore
     * an amount another dish had contributed first.
     */
    suspend fun undoAddFromRecipe(undo: ShoppingAddUndo) {
        val createdIds = undo.created.toSet()
        undo.modified.filterNot { it.id in createdIds }.forEach { before ->
            shoppingDao.upsert(before)
            messageRecorder.record(ShoppingMessageEncoder.encode(before))
        }
        undo.created.forEach { id ->
            shoppingDao.deleteById(id)
            messageRecorder.record(ShoppingMessageEncoder.tombstone(id))
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
        scale: Double = 1.0,
    ) {
        shoppingDao.getBySource(oldRecipeId, date).forEach { item ->
            shoppingDao.deleteById(item.id)
            messageRecorder.record(ShoppingMessageEncoder.tombstone(item.id))
        }
        addFromRecipe(newRecipe, sourceDate = date, scale = scale)
    }
}
