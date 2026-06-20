package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One ordered preparation step (schema.org `HowToStep`). */
@Entity(
    tableName = "instructions",
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
data class InstructionEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
)
