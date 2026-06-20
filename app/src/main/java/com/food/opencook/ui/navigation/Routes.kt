package com.food.opencook.ui.navigation

/**
 * Non-top-level routes layered into the same NavHost as the bottom-nav
 * [TopLevelDestination]s. The bottom bar is hidden while these are shown.
 */
object Routes {
    /** Add-a-recipe entry (take photo / pick gallery) — reached contextually, not a tab. */
    const val SCAN = "scan"
    const val CAMERA = "camera"

    /** Camera opened from the review screen; returns the captured path to it. */
    const val REVIEW_CAMERA = "review_camera"

    /** Password-gated server admin (backup/restore), opened from Settings. */
    const val ADMIN = "admin"

    /** Pick a recipe for a meal-plan day (full list + search). */
    const val ARG_DATE = "date"
    const val PLAN_PICK = "plan_pick/{$ARG_DATE}"
    fun planPick(date: String) = "plan_pick/$date"

    /** Result key set on the review back-stack entry by [REVIEW_CAMERA]. */
    const val RESULT_CAPTURED_PATH = "captured_path"

    const val ARG_JOB_ID = "jobId"
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BARCODE_TARGET = "target"

    /** Barcode scan that adds the product to [target] ("shopping" | "pantry"). */
    const val BARCODE_SCAN = "barcode/{$ARG_BARCODE_TARGET}"
    fun barcodeScan(target: String) = "barcode/$target"

    /** Sentinel jobId for the review screen meaning "all finished, unreviewed scans". */
    const val JOB_ID_ALL = "all"

    /** Sentinel jobId for "create a new recipe manually" — review screen opens a blank draft. */
    const val JOB_ID_NEW = "new"

    const val REVIEW = "review/{$ARG_JOB_ID}"
    fun review(jobId: String) = "review/$jobId"
    fun reviewAll() = "review/$JOB_ID_ALL"
    fun reviewNew() = "review/$JOB_ID_NEW"

    const val RECIPE_DETAIL = "recipe/{$ARG_RECIPE_ID}"
    fun recipeDetail(recipeId: String) = "recipe/$recipeId"

    /** Edit one existing recipe — reuses the review screen, loaded by recipe id. */
    const val EDIT = "edit/{$ARG_RECIPE_ID}"
    fun edit(recipeId: String) = "edit/$recipeId"

    /** Routes that should hide the bottom navigation bar (focused full-screen flow). */
    val fullScreenRoutes = setOf(SCAN, CAMERA, REVIEW_CAMERA, REVIEW, RECIPE_DETAIL, EDIT, BARCODE_SCAN, ADMIN, PLAN_PICK)
}
