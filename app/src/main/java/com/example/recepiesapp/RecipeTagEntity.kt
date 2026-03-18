package com.example.recepiesapp

import androidx.room.Entity

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipeId", "tagId"]
)
data class RecipeTagEntity(
    val recipeId: Int,
    val tagId: Long
)

