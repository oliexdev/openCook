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

package com.food.opencook

import com.food.opencook.data.local.Transactor
import com.food.opencook.data.local.dao.JobDao
import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.Stamper
import com.food.opencook.sync.SyncTrigger
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.JobEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.JobsApi
import com.food.opencook.data.remote.dto.CreateJobResponseDto
import com.food.opencook.data.remote.dto.JobResponseDto
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.repository.DrainOutcome
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeJobDao : JobDao {
    val jobs = mutableMapOf<String, JobEntity>()
    override suspend fun upsert(job: JobEntity) { jobs[job.jobId] = job }
    override suspend fun getById(jobId: String): JobEntity? = jobs[jobId]
    override fun observeById(jobId: String): Flow<JobEntity?> = throw NotImplementedError()
    override fun observeActive(): Flow<List<JobEntity>> = throw NotImplementedError()
    override suspend fun getActive(): List<JobEntity> = jobs.values.filter { it.status in setOf("pending", "processing") }
    override fun observeFinishedUnacknowledged(): Flow<List<JobEntity>> = throw NotImplementedError()
    override fun observeFailedUnacknowledged(): Flow<List<JobEntity>> = throw NotImplementedError()
    override suspend fun getFailedUnacknowledged(): List<JobEntity> = jobs.values.filter { it.status == "error" }
    override suspend fun acknowledgeAllFinished(ts: Long) {}
    override suspend fun deleteById(jobId: String) { jobs.remove(jobId) }
    override suspend fun updateStatus(jobId: String, status: String, error: String?, updatedAt: Long) {
        jobs[jobId]?.let { jobs[jobId] = it.copy(status = status, error = error, updatedAt = updatedAt) }
    }
    override suspend fun updateProgress(jobId: String, status: String, stage: String?, updatedAt: Long) {
        jobs[jobId]?.let { jobs[jobId] = it.copy(status = status, stage = stage, updatedAt = updatedAt) }
    }
    override suspend fun setServerJob(jobId: String, serverJobId: String, status: String, updatedAt: Long) {
        jobs[jobId]?.let { jobs[jobId] = it.copy(serverJobId = serverJobId, status = status, updatedAt = updatedAt) }
    }
    override suspend fun markDrained(jobId: String, ts: Long) {
        jobs[jobId]?.let { jobs[jobId] = it.copy(resultDrainedAt = ts, updatedAt = ts) }
    }
}

private class FakeRecipeDao : RecipeDao {
    val recipes = mutableListOf<RecipeEntity>()
    val ingredients = mutableListOf<IngredientEntity>()
    val instructions = mutableListOf<InstructionEntity>()
    val images = mutableListOf<ImageEntity>()
    override suspend fun insertRecipe(recipe: RecipeEntity) { recipes += recipe }
    override suspend fun insertIngredients(ingredients: List<IngredientEntity>) { this.ingredients += ingredients }
    override suspend fun insertInstructions(instructions: List<InstructionEntity>) { this.instructions += instructions }
    override suspend fun insertImages(images: List<ImageEntity>) { this.images += images }
    override suspend fun localOnlyImages(): List<ImageEntity> = images.filter { it.remoteName == null && it.localPath != null }
    override suspend fun remoteOnlyImages(): List<ImageEntity> = images.filter { it.remoteName != null && it.localPath == null }
    override suspend fun setImageRemoteName(id: String, name: String) {
        images.replaceAll { if (it.id == id) it.copy(remoteName = name) else it }
    }
    override suspend fun localPathForRemoteName(name: String): String? =
        images.firstOrNull { it.remoteName == name && it.localPath != null }?.localPath
    override suspend fun resetRemoteNamesForReupload() {
        images.replaceAll { if (it.localPath != null) it.copy(remoteName = null) else it }
    }
    override suspend fun setImageLocalPath(id: String, path: String) {
        images.replaceAll { if (it.id == id) it.copy(localPath = path) else it }
    }
    override suspend fun imageIdsFor(recipeId: String): List<String> = images.filter { it.recipeId == recipeId }.map { it.id }
    override suspend fun imagesForRecipe(recipeId: String): List<ImageEntity> = images.filter { it.recipeId == recipeId }
    override suspend fun getImageById(id: String): ImageEntity? = images.firstOrNull { it.id == id }
    override suspend fun deleteImageById(id: String) { images.removeAll { it.id == id } }
    override suspend fun deleteImagesForRecipe(recipeId: String) { images.removeAll { it.recipeId == recipeId } }
    override suspend fun insertNutrition(nutrition: NutritionEntity) {}
    override suspend fun deleteIngredientsFor(recipeId: String) {}
    override suspend fun deleteInstructionsFor(recipeId: String) {}
    override suspend fun deleteNutritionFor(recipeId: String) {}
    override suspend fun deleteRecipe(recipeId: String) {}
    override fun observeAll(): Flow<List<RecipeWithDetails>> = throw NotImplementedError()
    override fun observeById(id: String): Flow<RecipeWithDetails?> = throw NotImplementedError()
    override suspend fun getByIdOnce(id: String): RecipeWithDetails? = null
    override suspend fun getAllOnce(): List<RecipeWithDetails> = emptyList()
    override suspend fun ingredientIdsFor(recipeId: String): List<String> = ingredients.filter { it.recipeId == recipeId }.map { it.id }
    override suspend fun instructionIdsFor(recipeId: String): List<String> = instructions.filter { it.recipeId == recipeId }.map { it.id }
    override suspend fun deleteIngredientById(id: String) { ingredients.removeAll { it.id == id } }
    override suspend fun deleteInstructionById(id: String) { instructions.removeAll { it.id == id } }
    override fun observeBySourcePhoto(sourcePhotoId: String): Flow<List<RecipeWithDetails>> = throw NotImplementedError()
    override suspend fun getBySourcePhoto(sourcePhotoId: String): List<RecipeWithDetails> = throw NotImplementedError()
    override fun observeUnreviewed(): Flow<List<RecipeWithDetails>> = throw NotImplementedError()
    override suspend fun getUnreviewed(): List<RecipeWithDetails> = throw NotImplementedError()
    override suspend fun recipeExists(id: String): Boolean = recipes.any { it.id == id }
    override suspend fun upsertRecipeEntity(recipe: RecipeEntity) { recipes.removeAll { it.id == recipe.id }; recipes += recipe }
    override suspend fun upsertIngredientRow(ingredient: IngredientEntity) { ingredients.removeAll { it.id == ingredient.id }; ingredients += ingredient }
    override suspend fun upsertInstructionRow(instruction: InstructionEntity) { instructions.removeAll { it.id == instruction.id }; instructions += instruction }
    override suspend fun upsertNutritionRow(nutrition: NutritionEntity) {}
    override suspend fun upsertImageRow(image: ImageEntity) { images.removeAll { it.id == image.id }; images += image }
    override suspend fun setLastCookedAt(recipeId: String, dateIso: String?, now: Long) {
        recipes.replaceAll { if (it.id == recipeId) it.copy(lastCookedAt = dateIso) else it }
    }
    override suspend fun distinctIngredientNames(): List<String> = ingredients.map { it.name }.distinct()
    override suspend fun allIdAndNames(): List<com.food.opencook.data.local.dao.RecipeIdName> =
        recipes.map { com.food.opencook.data.local.dao.RecipeIdName(it.id, it.name) }
}

private class FakeRecipeLikeDao : com.food.opencook.data.local.dao.RecipeLikeDao {
    val likes = mutableListOf<com.food.opencook.data.local.entity.RecipeLikeEntity>()
    override suspend fun upsert(like: com.food.opencook.data.local.entity.RecipeLikeEntity) {
        likes.removeAll { it.recipeId == like.recipeId && it.nodeId == like.nodeId }
        likes += like
    }
    override fun observe(recipeId: String, nodeId: String): Flow<com.food.opencook.data.local.entity.RecipeLikeEntity?> =
        throw NotImplementedError()
    override suspend fun likedRecipeIds(): List<String> = likes.filter { it.liked }.map { it.recipeId }.distinct()
}

private class FakeMessageDao : MessageDao {
    val messages = mutableListOf<MessageEntity>()
    override suspend fun insert(message: MessageEntity) {
        if (messages.none { it.timestamp == message.timestamp }) messages += message
    }
    override suspend fun insertAll(messages: List<MessageEntity>) = messages.forEach { insert(it) }
    override suspend fun maxTimestamp(dataset: String, rowId: String, column: String): String? =
        messages.filter { it.dataset == dataset && it.rowId == rowId && it.column == column }.maxOfOrNull { it.timestamp }
    override suspend fun all(): List<MessageEntity> = messages.sortedBy { it.timestamp }
    override suspend fun since(cursor: String): List<MessageEntity> = messages.filter { it.timestamp > cursor }.sortedBy { it.timestamp }
    override suspend fun forRow(dataset: String, rowId: String): List<MessageEntity> = messages.filter { it.dataset == dataset && it.rowId == rowId }
    override suspend fun existingTimestamps(timestamps: List<String>): List<String> =
        messages.map { it.timestamp }.filter { it in timestamps.toSet() }
    override suspend fun count(): Int = messages.size
}

/** Deterministic stamper: strictly increasing timestamps. */
private class FakeStamper : Stamper {
    private var n = 0L
    override suspend fun stamp(): Hlc = Hlc(1_000 + n++, 0, "T")
}

/** Pass-through transactor: tests don't need real DB atomicity. */
private val passThrough = object : Transactor {
    override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
}

/** No-op DAOs: the drain path never touches shopping/pantry, so these stay empty. */
private val noopPantryDao = object : PantryDao {
    override fun observeAll(): Flow<List<PantryItemEntity>> = throw NotImplementedError()
    override suspend fun getById(id: String): PantryItemEntity? = null
    override suspend fun findByName(name: String): PantryItemEntity? = null
    override suspend fun allNames(): List<String> = emptyList()
    override suspend fun getAll(): List<PantryItemEntity> = emptyList()
    override suspend fun upsert(item: PantryItemEntity) {}
    override suspend fun deleteById(id: String) {}
}
private val noopShoppingDao = object : ShoppingDao {
    override fun observeAll(): Flow<List<ShoppingItemEntity>> = throw NotImplementedError()
    override suspend fun getById(id: String): ShoppingItemEntity? = null
    override suspend fun findOpenByText(text: String): ShoppingItemEntity? = null
    override suspend fun getChecked(): List<ShoppingItemEntity> = emptyList()
    override suspend fun getAll(): List<ShoppingItemEntity> = emptyList()
    override suspend fun getBySource(recipeId: String, date: String): List<ShoppingItemEntity> = emptyList()
    override suspend fun getAllBySource(recipeId: String, date: String): List<ShoppingItemEntity> = emptyList()
    override suspend fun countBySource(recipeId: String, date: String): Int = 0
    override suspend fun countOpenBySource(recipeId: String, date: String): Int = 0
    override suspend fun getOpenByRecipe(recipeId: String): List<ShoppingItemEntity> = emptyList()
    override suspend fun distinctTexts(): List<String> = emptyList()
    override suspend fun upsert(item: ShoppingItemEntity) {}
    override suspend fun deleteById(id: String) {}
}

private class FakeApi(private val response: JobResponseDto) : JobsApi {
    override suspend fun createJob(
        image: MultipartBody.Part,
        language: okhttp3.RequestBody?,
    ): CreateJobResponseDto = CreateJobResponseDto("job-1", "pending")
    override suspend fun getJob(id: String): JobResponseDto = response
}

class RecipeRepositoryDrainTest {

    private fun repo(jobDao: FakeJobDao, recipeDao: FakeRecipeDao): RecipeRepository {
        val recorder = MessageRecorder(
            FakeMessageDao(),
            FakeStamper(),
            object : SyncTrigger { override fun requestSync() {} },
        )
        val pantryRepo = PantryRepository(noopPantryDao, recorder)
        return RecipeRepository(
            api = FakeApi(JobResponseDto(jobId = "job-1", status = "done")),
            transactor = passThrough,
            recipeDao = recipeDao,
            recipeLikeDao = FakeRecipeLikeDao(),
            jobDao = jobDao,
            messageRecorder = recorder,
            importCorrector = { it },
            shoppingRepository = ShoppingRepository(noopShoppingDao, recorder, pantryRepo),
            pantryRepository = pantryRepo,
            contentLanguage = { "de" },
        )
    }

    private val sampleRecipes = listOf(
        RecipeDto(name = "A", recipeIngredient = listOf("1 Ei")),
        RecipeDto(name = "B", recipeInstructions = listOf()),
    )

    @Test
    fun drainWritesRecipesAndMarksDrained() = runTest {
        val jobDao = FakeJobDao().apply {
            jobs["job-1"] = JobEntity(jobId = "job-1", localImagePath = "/tmp/x.jpg", status = "done", createdAt = 0, updatedAt = 0)
        }
        val recipeDao = FakeRecipeDao()
        val outcome = repo(jobDao, recipeDao).drainJobResults("job-1", sampleRecipes)

        assertTrue(outcome is DrainOutcome.Drained)
        assertEquals(2, (outcome as DrainOutcome.Drained).recipeCount)
        assertEquals(2, recipeDao.recipes.size)
        // All drafts linked to the job for the review screen.
        assertTrue(recipeDao.recipes.all { it.sourcePhotoId == "job-1" })
        assertTrue(jobDao.jobs["job-1"]!!.resultDrainedAt != null)
    }

    @Test
    fun secondDrainIsNoOp() = runTest {
        val jobDao = FakeJobDao().apply {
            jobs["job-1"] = JobEntity(jobId = "job-1", localImagePath = "/tmp/x.jpg", status = "done", createdAt = 0, updatedAt = 0)
        }
        val recipeDao = FakeRecipeDao()
        val repository = repo(jobDao, recipeDao)

        repository.drainJobResults("job-1", sampleRecipes)
        val second = repository.drainJobResults("job-1", sampleRecipes)

        assertTrue(second is DrainOutcome.AlreadyDrained)
        assertEquals(2, recipeDao.recipes.size) // not doubled
    }

    @Test
    fun drainMissingJobIsNoOp() = runTest {
        val outcome = repo(FakeJobDao(), FakeRecipeDao()).drainJobResults("nope", sampleRecipes)
        assertTrue(outcome is DrainOutcome.AlreadyDrained)
    }
}
