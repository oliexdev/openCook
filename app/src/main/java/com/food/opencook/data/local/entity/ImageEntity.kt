package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An image attached to a recipe. [remoteName] is the bare filename served by the
 * server's `GET /images/{name}`; [localPath] is set once the file is downloaded
 * or captured locally. Images are always optional and never block saving.
 */
@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class ImageEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val remoteName: String? = null,
    val localPath: String? = null,
    val isPrimary: Boolean = false,
)
