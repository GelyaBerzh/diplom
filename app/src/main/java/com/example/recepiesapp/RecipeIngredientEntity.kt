package com.example.recepiesapp

import androidx.room.Entity

@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "ingredientId"]
)
data class RecipeIngredientEntity(
    val recipeId: Int,
    val ingredientId: Long
)

