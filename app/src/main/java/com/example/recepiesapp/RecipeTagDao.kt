package com.example.recepiesapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecipeTagDao {
    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecipeTagEntity>)
}

