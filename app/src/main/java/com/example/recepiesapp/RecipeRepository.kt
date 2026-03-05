package com.example.recepiesapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class RecipeRepository(private val context: Context) {

    private val fileName = "recipes.json"
    private val gson = Gson()
    private val database = RecipeDatabase.getInstance(context)
    private val recipeDao = database.recipeDao()

    private val file: File
        get() = File(context.filesDir, fileName)

    /**
     * Загрузка рецептов из Room.
     * При первом запуске выполняется миграция из legacy-файла recipes.json.
     */
    suspend fun loadRecipes(): MutableList<Recipe> = withContext(Dispatchers.IO) {
        return@withContext try {
            val dbRecipes = recipeDao.getAll()
            if (dbRecipes.isNotEmpty()) {
                dbRecipes.map { it.toDomain() }.toMutableList()
            } else {
                // Миграция из файла, если БД пустая
                val legacyRecipes = loadRecipesFromFile()
                if (legacyRecipes.isNotEmpty()) {
                    recipeDao.insertAll(legacyRecipes.map { it.toEntity() })
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
}