package com.example.recepiesapp

import java.io.Serializable
import java.text.Collator
import java.util.Locale

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
    val cookingTimeMinutes: Int = 0,
    val caloriesPerServing: Double = 0.0,
    val proteinPerServing: Double = 0.0,
    val fatPerServing: Double = 0.0,
    val carbsPerServing: Double = 0.0,
    val viewCount: Int = 0,
    val isFavorite: Boolean = false
) : Serializable

private val recipeTitleCollator: Collator
    get() = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }

fun List<Recipe>.sortedByTitle(): List<Recipe> =
    sortedWith(compareBy(recipeTitleCollator) { it.title.trim() })

fun MutableList<Recipe>.sortByTitle() {
    sortWith(compareBy(recipeTitleCollator) { it.title.trim() })
}