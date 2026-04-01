package com.example.recepiesapp

import androidx.annotation.StringRes

enum class CookingMethod(
    val id: String,
    @StringRes val titleRes: Int
) {
    POT("pot", R.string.cbMethodPot),
    PAN("pan", R.string.cbMethodPan),
    BLENDER("blender", R.string.cbMethodblender),
    MULTICOOKER("multicooker", R.string.cbMethodmulticooker),
    OVEN("oven", R.string.cbMethodOven),
    GRILL("grill", R.string.cbMethodGrill);

    companion object {
        fun fromId(id: String): CookingMethod? =
            values().firstOrNull { it.id == id }
    }
}

