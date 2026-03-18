package com.example.recepiesapp

import androidx.annotation.StringRes

enum class DishType(
    val id: String,
    @StringRes val titleRes: Int
    ) {
    COLD("cold", R.string.cbDishCold),
    FIRST("first", R.string.cbDishFirst),
    HOT("hot", R.string.cbDishHot),
    PRESERVES("preserves", R.string.cbDishPreserves),
    DESSERT("dessert", R.string.cbDishDessert),
    DRINKS("drinks", R.string.cbDishDrinks),
    SIDE("side", R.string.cbDishSide),
    SAUCES("sauces", R.string.cbDishSauces),
    BAKERY("bakery", R.string.cbDishBakery);

    companion object {
        fun fromId(id: String): DishType? =
            values().firstOrNull { it.id == id }
    }
}

