package com.example.recepiesapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecipeRepository(private val context: Context) {

    private val fileName = "recipes.json"
    private val gson = Gson()
    private val database = RecipeDatabase.getInstance(context)
    private val recipeDao = database.recipeDao()
    private val ingredientDao = database.ingredientDao()
    private val tagDao = database.tagDao()
    private val categoryDao = database.categoryDao()
    private val recipeIngredientDao = database.recipeIngredientDao()
    private val recipeTagDao = database.recipeTagDao()
    private val ingredientNutritionDao = database.ingredientNutritionDao()

    private val file: File
        get() = File(context.filesDir, fileName)

    /**
     * Загрузка рецептов из Room.
     * При первом запуске выполняется миграция из legacy-файла recipes.json.
     */
    suspend fun loadRecipes(): MutableList<Recipe> = withContext(Dispatchers.IO) {
        return@withContext try {
            ensureDefaultCategories()
            ensureDefaultNutritionSeeded()
            val dbRecipes = recipeDao.getAll()
            if (dbRecipes.isNotEmpty()) {
                dbRecipes
                    .map { it.toDomain().withNormalizedDishType() }
                    .toMutableList()
            } else {
                // Миграция из файла, если БД пустая
                val legacyRecipes = loadRecipesFromFile()
                    .map { it.withNormalizedDishType() }
                    .toMutableList()
                if (legacyRecipes.isNotEmpty()) {
                    recipeDao.insertAll(legacyRecipes.map { it.toEntity() })
                    syncNormalizedTables(legacyRecipes)
                    // После успешной миграции удаляем файл, чтобы не мигрировать повторно
                    if (file.exists()) {
                        runCatching { file.delete() }
                    }
                }
                legacyRecipes
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    /**
     * Полная перезапись списка рецептов в Room.
     * Используется текущей логикой MainActivity, где изменяется общий список.
     */
    suspend fun saveRecipes(recipes: MutableList<Recipe>) = withContext(Dispatchers.IO) {
        try {
            recipeDao.clearAll()
            if (recipes.isNotEmpty()) {
                recipeDao.insertAll(recipes.map { it.toEntity() })
            }
            syncNormalizedTables(recipes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun upsertRecipe(recipe: Recipe) = withContext(Dispatchers.IO) {
        try {
            recipeDao.upsert(recipe.toEntity())
            syncNormalizedTables(listOf(recipe))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteRecipe(recipeId: Int) = withContext(Dispatchers.IO) {
        try {
            recipeDao.deleteById(recipeId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadFavoriteRecipes(): MutableList<Recipe> = withContext(Dispatchers.IO) {
        return@withContext try {
            ensureDefaultNutritionSeeded()
            recipeDao.getFavorites()
                .map { it.toDomain().withNormalizedDishType() }
                .toMutableList()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    suspend fun setFavorite(recipeId: Int, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        try {
            recipeDao.setFavorite(recipeId, isFavorite)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun ensureDefaultCategories() {
        // Храним «разделы блюд» в таблице categories (по id DishType), чтобы таблица была наполнена.
        DishType.values().forEach { type ->
            val name = type.id
            val existing = categoryDao.findByName(name)
            if (existing == null) {
                categoryDao.insert(CategoryEntity(name = name))
            }
        }
    }

    private suspend fun syncNormalizedTables(recipes: List<Recipe>) {
        ensureDefaultCategories()
        recipes.forEach { recipe ->
            // Рецепт — источник связей. Пересобираем связи для актуального состояния.
            recipeIngredientDao.deleteForRecipe(recipe.id)
            recipeTagDao.deleteForRecipe(recipe.id)

            val ingredientLinks = recipe.ingredients.mapNotNull { raw ->
                val parsed = parseIngredientParts(raw)
                val name = parsed.name.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val ingredientId = getOrCreateIngredientId(name, parsed.quantity, parsed.unit)
                RecipeIngredientEntity(recipeId = recipe.id, ingredientId = ingredientId)
            }
            if (ingredientLinks.isNotEmpty()) {
                recipeIngredientDao.insertAll(ingredientLinks)
            }

            val tagLinks = recipe.tags.mapNotNull { raw ->
                val name = raw.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val tagId = getOrCreateTagId(name)
                RecipeTagEntity(recipeId = recipe.id, tagId = tagId)
            }
            if (tagLinks.isNotEmpty()) {
                recipeTagDao.insertAll(tagLinks)
            }
        }
    }

    private suspend fun getOrCreateIngredientId(name: String, quantity: Double, unit: String): Long {
        val existing = ingredientDao.findByKey(name, quantity, unit)
        if (existing != null) return existing.id
        val insertedId = ingredientDao.insert(IngredientEntity(name = name, quantity = quantity, unit = unit))
        if (insertedId > 0) return insertedId
        // Если insert вернул -1 (уже есть), дочитаем.
        return ingredientDao.findByKey(name, quantity, unit)?.id ?: 0
    }

    private suspend fun getOrCreateTagId(name: String): Long {
        val existing = tagDao.findByName(name)
        if (existing != null) return existing.id
        val insertedId = tagDao.insert(TagEntity(name = name))
        if (insertedId > 0) return insertedId
        return tagDao.findByName(name)?.id ?: 0
    }

    private data class IngredientParts(
        val name: String,
        val quantity: Double,
        val unit: String
    )

    private fun parseIngredientParts(raw: String): IngredientParts {
        val parts = raw.trim().split("\\s+".toRegex())
        return if (parts.size >= 3) {
            val unit = parts.last()
            val quantity = parts[parts.size - 2].toDoubleOrNull() ?: 0.0
            val name = parts.dropLast(2).joinToString(" ")
            IngredientParts(name = name, quantity = quantity, unit = unit)
        } else {
            IngredientParts(name = raw, quantity = 0.0, unit = "")
        }
    }

    private suspend fun ensureDefaultNutritionSeeded() {
        // Сидим справочник КБЖУ на 100г в БД (при необходимости).
        val defaults = defaultNutrition()
        val existingCount = defaults.count { (name, _) ->
            ingredientNutritionDao.findByName(name) != null
        }
        if (existingCount == defaults.size) return

        val items = defaults.map { (name, n) ->
            IngredientNutritionEntity(
                name = name,
                caloriesPer100 = n.calories,
                proteinPer100 = n.protein,
                fatPer100 = n.fat,
                carbsPer100 = n.carbs
            )
        }
        ingredientNutritionDao.insertAll(items)
    }

    private fun defaultNutrition(): Map<String, Nutrition> = mapOf(
        "яблоко" to Nutrition(52.0, 0.3, 0.2, 13.8),
        "груша" to Nutrition(57.0, 0.4, 0.1, 15.2),
        "банан" to Nutrition(89.0, 1.1, 0.3, 22.8),
        "апельсин" to Nutrition(47.0, 0.9, 0.1, 11.8),
        "мандарин" to Nutrition(53.0, 0.8, 0.2, 13.3),
        "грейпфрут" to Nutrition(42.0, 0.7, 0.1, 10.7),
        "лимон" to Nutrition(29.0, 1.1, 0.3, 9.3),
        "лайм" to Nutrition(30.0, 0.7, 0.2, 10.5),
        "киви" to Nutrition(61.0, 1.1, 0.5, 14.6),
        "ананас" to Nutrition(50.0, 0.5, 0.1, 13.1),
        "манго" to Nutrition(60.0, 0.8, 0.4, 15.0),
        "папайя" to Nutrition(43.0, 0.5, 0.3, 10.8),
    )

    suspend fun calculateRecipeNutritionFromDb(
        ingredients: List<IngredientAmount>,
        servings: Int
    ): NutritionSummary = withContext(Dispatchers.IO) {
        ensureDefaultNutritionSeeded()

        var calories = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0

        for (item in ingredients) {
            val nameKey = item.name.lowercase()
            val baseEntity = ingredientNutritionDao.findByName(nameKey)
            val base = baseEntity?.let {
                Nutrition(it.caloriesPer100, it.proteinPer100, it.fatPer100, it.carbsPer100)
            } ?: defaultNutrition()[nameKey] ?: continue

            val grams = toGrams(item.quantity, item.unit, item.name)
            val factor = grams / 100.0
            calories += base.calories * factor
            protein += base.protein * factor
            fat += base.fat * factor
            carbs += base.carbs * factor
        }

        val perServing = servings.coerceAtLeast(1)
        return@withContext NutritionSummary(
            calories / perServing,
            protein / perServing,
            fat / perServing,
            carbs / perServing
        )
    }

    /**
     * Legacy-загрузка рецептов из файла JSON.
     */
    private fun loadRecipesFromFile(): MutableList<Recipe> {
        return try {
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<MutableList<Recipe>>() {}.type
                gson.fromJson<MutableList<Recipe>>(json, type) ?: mutableListOf()
            } else {
                mutableListOf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    private fun Recipe.withNormalizedDishType(): Recipe {
        val normalized = normalizeDishType(dishType)
        return copy(dishType = normalized)
    }

    private fun normalizeDishType(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        // Если это уже нормализованные id (cold, hot, ...), просто вернём их
        val asIds = parts.mapNotNull { part ->
            DishType.fromId(part)?.id
        }
        if (asIds.size == parts.size) {
            return asIds.distinct().joinToString(",")
        }

        // Иначе считаем, что это локализованные названия и маппим их в id
        val ids = parts.mapNotNull { name ->
            val lower = name.lowercase()
            when (lower) {
                "холодные закуски", "cold dishes" -> DishType.COLD.id
                "первые блюда", "first courses" -> DishType.FIRST.id
                "горячие блюда", "hot dishes" -> DishType.HOT.id
                "домашние заготовки", "homemade preparations" -> DishType.PRESERVES.id
                "десерты", "dessert" -> DishType.DESSERT.id
                "напитки", "drinks" -> DishType.DRINKS.id
                "гарниры", "side dishes" -> DishType.SIDE.id
                "соусы", "sauces" -> DishType.SAUCES.id
                "выпечка", "bakery" -> DishType.BAKERY.id
                else -> null
            }
        }.distinct()

        if (ids.isEmpty()) return null
        return ids.joinToString(",")
    }
}