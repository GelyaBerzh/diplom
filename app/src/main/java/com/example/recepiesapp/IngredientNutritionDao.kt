package com.example.recepiesapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IngredientNutritionDao {
    @Query("SELECT * FROM ingredient_nutrition WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): IngredientNutritionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<IngredientNutritionEntity>): List<Long>
}

