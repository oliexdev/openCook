package com.food.opencook.ui.recipes

import com.food.opencook.data.local.entity.ImageEntity
import java.io.File

/**
 * Resolve a recipe image to a Coil model: a [File] for a local capture/import, or
 * a server crop URL string. Returns null when there's nothing to show.
 */
fun imageModelFor(image: ImageEntity?, serverBaseUrl: String?): Any? = when {
    image == null -> null
    image.localPath != null -> File(image.localPath)
    image.remoteName != null -> serverBaseUrl?.trimEnd('/')?.let { "$it/images/${image.remoteName}" }
    else -> null
}

/** Pick the primary photo deterministically across the (possibly multi-row) list —
 *  Room's @Relation doesn't ORDER BY, and AI extraction yields several crops. */
fun imageModelFor(images: List<ImageEntity>, serverBaseUrl: String?): Any? =
    imageModelFor(images.firstOrNull { it.isPrimary } ?: images.firstOrNull(), serverBaseUrl)
