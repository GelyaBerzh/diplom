package com.example.recepiesapp

import androidx.room.Entity

@Entity(
    tableName = "recipe_cooking_methods",
    primaryKeys = ["recipeId", "methodId"]
)
data class RecipeCookingMethodEntity(
    val recipeId: Int,
    val methodId: String
)

