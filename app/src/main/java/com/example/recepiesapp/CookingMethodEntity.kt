package com.example.recepiesapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cooking_methods")
data class CookingMethodEntity(
    @PrimaryKey
    val id: String
)

