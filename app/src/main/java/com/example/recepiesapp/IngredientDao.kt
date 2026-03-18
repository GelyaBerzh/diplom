package com.example.recepiesapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE name = :name AND quantity = :quantity AND unit = :unit LIMIT 1")
    suspend fun findByKey(name: String, quantity: Double, unit: String): IngredientEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: IngredientEntity): Long
}

