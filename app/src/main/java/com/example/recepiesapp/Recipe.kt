package com.example.recepiesapp

import java.io.Serializable

data class Recipe(
    val id: Int,
    val title: String,
    val ingredients: List<String>,
    val description: String,
    val instructions: String,
    val tags: List<String>,
    val dishType: String? = null,
    val cookingMethod: String? = null,
    val servings: Int = 1,
    val imageUri: String? = null,
    val caloriesPerServing: Double = 0.0,
    val proteinPerServing: Double = 0.0,
    val fatPerServing: Double = 0.0,
    val carbsPerServing: Double = 0.0,
    val viewCount: Int = 0,
    val isFavorite: Boolean = false
) : Serializable