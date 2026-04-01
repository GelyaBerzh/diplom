package com.example.recepiesapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecipeCookingMethodDao {
    @Query("DELETE FROM recipe_cooking_methods WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecipeCookingMethodEntity>)
}

