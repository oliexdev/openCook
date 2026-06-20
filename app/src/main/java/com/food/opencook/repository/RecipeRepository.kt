package com.food.opencook.repository

import com.food.opencook.data.local.Transactor
import com.food.opencook.sync.FieldChange
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.RecipeMessageEncoder
import com.food.opencook.sync.SyncDatasets
import com.food.opencook.data.local.dao.JobDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.RecipeLikeDao
import com.food.opencook.data.local.entity.RecipeLikeEntity
import com.food.opencook.sync.RecipeLikeMessageEncoder
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.JobEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.JobsApi
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.data.remote.mapper.MappedRecipe
import com.food.opencook.data.remote.mapper.toMappedRecipe
import com.food.opencook.util.ImportCorrector
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Job lifecycle as the repository reports it to callers. */
sealed interface JobOutcome {
    /** Queued locally; not yet uploaded (e.g. server unreachable) or accepted but not started. */
    data object Pending : JobOutcome
    data object Processing : JobOutcome
    /** [newlyDrained] is true only for the caller that actually wrote the recipes. */
    data class Done(val newlyDrained: Boolean) : JobOutcome
    data class Failed(val error: String?) : JobOutcome
}

/** Result of [RecipeRepository.drainJobResults], used to fire exactly one notification. */
sealed interface DrainOutcome {
    data class Drained(val recipeCount: Int, val skippedDuplicates: Int = 0) : DrainOutcome
    data object AlreadyDrained : DrainOutcome
}

/** Outcome of saving a recipe — a same-named recipe already exists ⇒ [Duplicate], not saved. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data object Duplicate : SaveResult
}

/** Normalize a recipe name for duplicate comparison: trim, lowercase, collapse whitespace. */
internal fun normalizeRecipeName(name: String?): String? =
    name?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

@Singleton
class RecipeRepository @Inject constructor(
    private val api: JobsApi,
    private val transactor: Transactor,
    private val recipeDao: RecipeDao,
    private val recipeLikeDao: RecipeLikeDao,
    private val jobDao: JobDao,
    private val messageRecorder: MessageRecorder,
    private val importCorrector: ImportCorrector,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository,
) {
    fun observeRecipes(): Flow<List<RecipeWithDetails>> = recipeDao.observeAll()
    fun observeRecipe(id: String): Flow<RecipeWithDetails?> = recipeDao.observeById(id)
    fun observeRecipesForJob(jobId: String): Flow<List<RecipeWithDetails>> =
        recipeDao.observeBySourcePhoto(jobId)
    suspend fun getRecipesForJob(jobId: String): List<RecipeWithDetails> =
        recipeDao.getBySourcePhoto(jobId)
    suspend fun getRecipeOnce(recipeId: String): RecipeWithDetails? = recipeDao.getByIdOnce(recipeId)
    suspend fun getAllRecipesOnce(): List<RecipeWithDetails> = recipeDao.getAllOnce()

    /**
     * The id of an existing recipe whose name matches [name] (normalized), or null.
     * Used for name-based duplicate detection across all creation paths.
     */
    suspend fun existingRecipeNameId(name: String?): String? {
        val key = normalizeRecipeName(name) ?: return null
        return recipeDao.allIdAndNames().firstOrNull { normalizeRecipeName(it.name) == key }?.id
    }
    fun observeJob(jobId: String): Flow<JobEntity?> = jobDao.observeById(jobId)

    /** Jobs still uploading or being processed by the server. */
    fun observeActiveJobs(): Flow<List<JobEntity>> = jobDao.observeActive()
    suspend fun getActiveJobs(): List<JobEntity> = jobDao.getActive()

    /** Finished scans whose recipes the user hasn't acknowledged yet (drives the strip). */
    fun observeFinishedUnacknowledged(): Flow<List<JobEntity>> =
        jobDao.observeFinishedUnacknowledged()

    /** Scans that ended in error and haven't been acknowledged. */
    fun observeFailedJobs(): Flow<List<JobEntity>> = jobDao.observeFailedUnacknowledged()
    suspend fun getFailedJobs(): List<JobEntity> = jobDao.getFailedUnacknowledged()

    /** Mark all terminal (done/failed) scans as seen. */
    suspend fun acknowledgeFinishedJobs() = jobDao.acknowledgeAllFinished(System.currentTimeMillis())

    /** Drop a local job entirely (user cancelled it). */
    suspend fun deleteJob(jobId: String) = jobDao.deleteById(jobId)

    /** Recipes from finished, not-yet-acknowledged scans (for the strip count + multi-job review). */
    fun observeUnreviewedRecipes(): Flow<List<RecipeWithDetails>> = recipeDao.observeUnreviewed()
    suspend fun getUnreviewedRecipes(): List<RecipeWithDetails> = recipeDao.getUnreviewed()

    /**
     * Register a scan locally (status "pending") and return its client-generated
     * id. Done synchronously the moment a photo is captured so the scan exists and
     * is visible even with no network; the upload worker fills the server id later.
     */
    suspend fun createLocalJob(localImagePath: String): String {
        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()
        jobDao.upsert(
            JobEntity(
                jobId = jobId,
                localImagePath = localImagePath,
                status = "pending",
                createdAt = now,
                updatedAt = now,
            ),
        )
        return jobId
    }

    /**
     * Upload the photo for a local job to the server. Idempotent: if the job
     * already carries a [JobEntity.serverJobId] (a retry after a successful upload)
     * it is a no-op, so we never create duplicate server jobs. Throws on network
     * failure — the [com.food.opencook.work.UploadJobWorker] retries with backoff.
     */
    suspend fun uploadJob(localJobId: String) {
        val job = jobDao.getById(localJobId) ?: return
        if (job.serverJobId != null) return

        val file = File(job.localImagePath)
        val part = MultipartBody.Part.createFormData(
            name = "image",
            filename = file.name,
            body = file.asRequestBody("image/jpeg".toMediaType()),
        )
        val created = api.createJob(part)
        jobDao.setServerJob(localJobId, created.jobId, created.status, System.currentTimeMillis())
    }

    /**
     * Ask the server for a job's current state. On "done" the recipes are drained
     * into Room (idempotently). Returns [JobOutcome.Pending] while the upload hasn't
     * happened yet. Throws on network failure — callers treat that as "still queued".
     */
    suspend fun refreshJob(localJobId: String): JobOutcome {
        val job = jobDao.getById(localJobId) ?: return JobOutcome.Failed(null)
        val serverJobId = job.serverJobId ?: return JobOutcome.Pending

        val dto = api.getJob(serverJobId)
        val now = System.currentTimeMillis()
        return when (dto.status) {
            "done" -> {
                val drain = drainJobResults(localJobId, dto.result.orEmpty())
                jobDao.updateProgress(localJobId, "done", null, now)
                JobOutcome.Done(newlyDrained = drain is DrainOutcome.Drained)
            }
            "error" -> {
                jobDao.updateStatus(localJobId, "error", dto.error, now)
                JobOutcome.Failed(dto.error)
            }
            "processing" -> {
                jobDao.updateProgress(localJobId, "processing", dto.stage, now)
                JobOutcome.Processing
            }
            else -> {
                jobDao.updateProgress(localJobId, "pending", dto.stage, now)
                JobOutcome.Pending
            }
        }
    }

    /**
     * Write a finished job's recipes into Room exactly once. The active poll and
     * the fallback PollJobWorker can both observe "done"; whichever reaches the
     * transaction first wins, the other is a no-op. Recipes are linked to the job
     * via [RecipeEntity.sourcePhotoId] == jobId so the review screen can find them.
     */
    suspend fun drainJobResults(jobId: String, recipes: List<RecipeDto>): DrainOutcome {
        val drained = mutableListOf<MappedRecipe>()
        val outcome = transactor.withTransaction {
            val job = jobDao.getById(jobId)
            if (job == null || job.resultDrainedAt != null) {
                return@withTransaction DrainOutcome.AlreadyDrained
            }
            val now = System.currentTimeMillis()
            // Skip extracted recipes whose name already exists (or repeats within this batch),
            // so re-photographing a recipe doesn't create a duplicate.
            val seenNames = recipeDao.allIdAndNames().mapNotNull { normalizeRecipeName(it.name) }.toMutableSet()
            var skipped = 0
            recipes.forEach { dto ->
                val mapped = dto.toMappedRecipe(sourcePhotoId = jobId, now = now).let { m ->
                    // Silently apply only the clear (1-edit) ingredient-name fixes at import;
                    // the review screen still suggests the borderline ones.
                    m.copy(ingredients = m.ingredients.map { ing ->
                        importCorrector.correct(ing.name).let { fixed ->
                            if (fixed != ing.name) ing.copy(name = fixed) else ing
                        }
                    })
                }
                val key = normalizeRecipeName(mapped.recipe.name)
                if (key != null && !seenNames.add(key)) {
                    skipped++
                    return@forEach
                }
                recipeDao.insertRecipe(mapped.recipe)
                recipeDao.insertIngredients(mapped.ingredients)
                recipeDao.insertInstructions(mapped.instructions)
                mapped.nutrition?.let { recipeDao.insertNutrition(it) }
                recipeDao.insertImages(mapped.images)
                drained += mapped
            }
            jobDao.markDrained(jobId, now)
            DrainOutcome.Drained(drained.size, skipped)
        }
        // Append sync messages outside the DB transaction (the clock touches DataStore).
        if (outcome is DrainOutcome.Drained) {
            drained.forEach {
                recordChanges(RecipeMessageEncoder.encode(it.recipe, it.ingredients, it.instructions, it.nutrition, it.images))
            }
        }
        return outcome
    }

    /**
     * Persist user edits from the review screen (replaces child rows transactionally).
     * Returns [SaveResult.Duplicate] without writing if a *different* recipe with the same
     * (normalized) name already exists — so manual creation, imports and review-saves can't
     * pile up duplicates. Editing a recipe in place (same id) is always allowed.
     */
    suspend fun saveRecipe(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
        nutrition: NutritionEntity?,
        images: List<ImageEntity>,
    ): SaveResult {
        val clashId = existingRecipeNameId(recipe.name)
        if (clashId != null && clashId != recipe.id) return SaveResult.Duplicate

        // Child rows that existed before this save but are gone now → tombstone them
        // so the removal also syncs (stable ids mean edits update in place).
        val removedIngredients = recipeDao.ingredientIdsFor(recipe.id).toSet() - ingredients.map { it.id }.toSet()
        val removedInstructions = recipeDao.instructionIdsFor(recipe.id).toSet() - instructions.map { it.id }.toSet()
        // Image edits use fresh UUIDs (attachLocalImage); the prior row (e.g. the synced
        // sync-<id> one) would otherwise linger as an orphan and the UI's firstOrNull()
        // could keep showing it. No sync tombstone needed — images travel by imageRef on
        // the recipe, which the upload step refreshes once the new file is on the server.
        val removedImages = recipeDao.imageIdsFor(recipe.id).toSet() - images.map { it.id }.toSet()

        transactor.withTransaction {
            recipeDao.upsertRecipe(recipe, ingredients, instructions, nutrition)
            removedImages.forEach { recipeDao.deleteImageById(it) }
            recipeDao.insertImages(images)
        }
        recordChanges(RecipeMessageEncoder.encode(recipe, ingredients, instructions, nutrition, images))
        val tombstones =
            removedIngredients.map { FieldChange(SyncDatasets.INGREDIENTS, it, SyncDatasets.COLUMN_DELETED, "true") } +
                removedInstructions.map { FieldChange(SyncDatasets.INSTRUCTIONS, it, SyncDatasets.COLUMN_DELETED, "true") }
        recordChanges(tombstones)
        return SaveResult.Saved
    }

    // --- Feedback (planner signals) ---------------------------------------

    /** This device's own like state for a recipe (drives the heart toggle). */
    fun observeLike(recipeId: String, nodeId: String): Flow<RecipeLikeEntity?> =
        recipeLikeDao.observe(recipeId, nodeId)

    /** Recipes liked by at least one household member — boosts them in the planner. */
    suspend fun likedRecipeIds(): Set<String> = recipeLikeDao.likedRecipeIds().toSet()

    /** Set/clear a member's like ([nodeId] = the member). Un-liking is kept as liked=false. */
    suspend fun setLiked(recipeId: String, nodeId: String, liked: Boolean) {
        val now = System.currentTimeMillis()
        val like = RecipeLikeEntity(recipeId, nodeId, liked, createdAt = now, updatedAt = now)
        recipeLikeDao.upsert(like)
        recordChanges(RecipeLikeMessageEncoder.encode(like))
    }

    /**
     * Toggle the household-wide "has been cooked" marker. We store the date it was marked
     * (so the planner can still mildly favour variety), but the UX is just an on/off
     * "has been cooked" flag — the user doesn't re-mark it every time they cook it.
     */
    suspend fun setCooked(recipeId: String, cooked: Boolean) {
        val date = if (cooked) java.time.LocalDate.now().toString() else null
        val now = System.currentTimeMillis()
        recipeDao.setLastCookedAt(recipeId, date, now)
        val value = if (date == null) "null" else "\"$date\""
        recordChanges(listOf(FieldChange(SyncDatasets.RECIPES, recipeId, "lastCookedAt", value)))
    }

    /**
     * Record that a recipe was cooked on [dateIso], but only advance [lastCookedAt] if
     * [dateIso] is newer than what's stored. Used by the meal-plan "cooked" tap, where the
     * same recipe may be planned on several days: un-marking one day must not erase a more
     * recent cook, so this never clears — it only moves the marker forward.
     */
    suspend fun markCookedOn(recipeId: String, dateIso: String) {
        val current = recipeDao.getByIdOnce(recipeId)?.recipe?.lastCookedAt
        if (current != null && current >= dateIso) return // ISO dates sort lexicographically
        val now = System.currentTimeMillis()
        recipeDao.setLastCookedAt(recipeId, dateIso, now)
        recordChanges(listOf(FieldChange(SyncDatasets.RECIPES, recipeId, "lastCookedAt", "\"$dateIso\"")))
        // "Out" half of the pantry cycle: cooking consumes this dish's perishables (staples stay).
        recipeDao.getByIdOnce(recipeId)?.let { pantryRepository.consume(it.ingredients.map { ing -> ing.name }) }
    }

    /** Set [lastCookedAt] to an exact value (or null) — used to undo a "cooked today" mark. */
    suspend fun restoreLastCookedAt(recipeId: String, dateIso: String?) {
        recipeDao.setLastCookedAt(recipeId, dateIso, System.currentTimeMillis())
        val value = if (dateIso == null) "null" else "\"$dateIso\""
        recordChanges(listOf(FieldChange(SyncDatasets.RECIPES, recipeId, "lastCookedAt", value)))
    }

    /** Delete a recipe locally and emit a tombstone so the deletion syncs. Also clears
     *  this recipe's still-open shopping items (checked = already bought → kept). */
    suspend fun deleteRecipe(recipeId: String) {
        transactor.withTransaction { recipeDao.deleteRecipe(recipeId) }
        recordChanges(listOf(FieldChange(SyncDatasets.RECIPES, recipeId, SyncDatasets.COLUMN_DELETED, "true")))
        shoppingRepository.removeOpenForRecipe(recipeId)
    }

    private suspend fun recordChanges(changes: List<FieldChange>) = messageRecorder.record(changes)
}
