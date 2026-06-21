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
