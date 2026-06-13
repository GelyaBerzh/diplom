package com.example.recepiesapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val ingredients: List<String>,
    val description: String,
    val instructions: String,
    val tags: List<String>,
    val dishType: String?,
    val cookingMethod: String?,
    val servings: Int,
    val imageUri: String?,
    val cookingTimeMinutes: Int,
    val caloriesPerServing: Double,
    val proteinPerServing: Double,
    val fatPerServing: Double,
    val carbsPerServing: Double,
    val viewCount: Int,
    val isFavorite: Boolean
)

fun RecipeEntity.toDomain(): Recipe =
    Recipe(
        id = id,
        title = title,
        ingredients = ingredients,
        description = description,
        instructions = instructions,
        tags = tags,
        dishType = dishType,
        cookingMethod = cookingMethod,
        servings = servings,
        imageUri = imageUri,
        cookingTimeMinutes = cookingTimeMinutes,
        caloriesPerServing = caloriesPerServing,
        proteinPerServing = proteinPerServing,
        fatPerServing = fatPerServing,
        carbsPerServing = carbsPerServing,
        viewCount = viewCount,
        isFavorite = isFavorite
    )

fun Recipe.toEntity(): RecipeEntity =
    RecipeEntity(
        id = id,
        title = title,
        ingredients = ingredients,
        description = description,
        instructions = instructions,
        tags = tags,
        dishType = dishType,
        cookingMethod = cookingMethod,
        servings = servings,
        imageUri = imageUri,
        cookingTimeMinutes = cookingTimeMinutes,
        caloriesPerServing = caloriesPerServing,
        proteinPerServing = proteinPerServing,
        fatPerServing = fatPerServing,
        carbsPerServing = carbsPerServing,
        viewCount = viewCount,
        isFavorite = isFavorite
    )

