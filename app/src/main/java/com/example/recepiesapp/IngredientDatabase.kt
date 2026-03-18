package com.example.recepiesapp

// Список ингредиентов для автодополнения
val INGREDIENT_SUGGESTIONS: List<String> = listOf(
    "мука", "яйца", "соль", "сахар", "масло", "молоко",
    "картофель", "лук", "помидоры", "чеснок", "рис", "капуста", "яблоко", "груша", "банан"
)

data class Nutrition(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

data class IngredientAmount(
    val name: String,
    val quantity: Double,
    val unit: String
)

data class NutritionSummary(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

// Fallback-база КБЖУ на 100 г (если справочник в БД ещё не заполнен).
private val ingredientNutritionFallback: Map<String, Nutrition> = mapOf(
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

fun toGrams(quantity: Double, unit: String, ingredientName: String): Double {
    return when (unit) {
        "г" -> quantity
        "кг" -> quantity * 1000.0
        "мл" -> quantity // условно 1 мл = 1 г для жидких
        "л" -> quantity * 1000.0
        "шт" -> {
            // пример: для яиц считаем 1 шт ≈ 50 г
            if (ingredientName.lowercase().contains("яйц")) quantity * 50.0 else quantity * 50.0
        }
        "ч.л." -> quantity * 5.0
        "ст.л." -> quantity * 15.0
        else -> quantity
    }
}

fun calculateRecipeNutrition(
    ingredients: List<IngredientAmount>,
    servings: Int
): NutritionSummary {
    var calories = 0.0
    var protein = 0.0
    var fat = 0.0
    var carbs = 0.0

    for (item in ingredients) {
        val base = ingredientNutritionFallback[item.name.lowercase()] ?: continue
        val grams = toGrams(item.quantity, item.unit, item.name)
        val factor = grams / 100.0

        calories += base.calories * factor
        protein += base.protein * factor
        fat += base.fat * factor
        carbs += base.carbs * factor
    }

    val perServing = servings.coerceAtLeast(1)
    return NutritionSummary(
        calories / perServing,
        protein / perServing,
        fat / perServing,
        carbs / perServing
    )
}

