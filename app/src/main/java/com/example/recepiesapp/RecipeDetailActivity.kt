package com.example.recepiesapp

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recipe_detail)

        // На экране просмотра статус-бар пусть совпадает с общим фоном экрана.
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)

        findViewById<MaterialToolbar>(R.id.toolbarRecipeDetail).apply {
            // Только верхний inset для toolbar, чтобы не залезать под статус-бар/шторку.
            applySystemBarsPadding(
                applyLeft = false,
                applyRight = false,
                applyBottom = false,
                includeIme = false
            )
            setNavigationOnClickListener { finish() }
        }

        var recipe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("RECIPE", Recipe::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("RECIPE") as? Recipe
        } ?: return

        val repository = RecipeRepository(this)
        val favoriteButton = findViewById<ImageButton>(R.id.btnFavoriteDetail)
        fun renderFavorite() {
            favoriteButton.setImageResource(
                if (recipe.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
            )
        }
        renderFavorite()
        favoriteButton.setOnClickListener {
            recipe = recipe.copy(isFavorite = !recipe.isFavorite)
            renderFavorite()
            lifecycleScope.launch(Dispatchers.IO) {
                repository.setFavorite(recipe.id, recipe.isFavorite)
            }
        }

        setupImage(recipe)
        setupTextBlocks(recipe)
        setupIngredients(recipe.ingredients)
        setupTags(recipe.tags)
    }

    private fun setupImage(recipe: Recipe) {
        val imagePadding = resources.getDimensionPixelSize(R.dimen.recipe_image_placeholder_padding)
        val recipeImageView = findViewById<ShapeableImageView>(R.id.ivRecipeImage)
        if (recipe.imageUri.isNullOrBlank()) {
            recipeImageView.setImageResource(R.drawable.ic_camera)
            recipeImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            recipeImageView.setPadding(imagePadding, imagePadding, imagePadding, imagePadding)
        } else {
            recipeImageView.setImageURI(Uri.parse(recipe.imageUri))
            recipeImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            recipeImageView.setPadding(0, 0, 0, 0)
        }
    }

    private fun setupTextBlocks(recipe: Recipe) {
        findViewById<TextView>(R.id.tvTitle).text = recipe.title
        findViewById<TextView>(R.id.tvServings).text =
            getString(R.string.servings_text, recipe.servings)

        findViewById<TextView>(R.id.tvCalories).text =
            "Калории: ${"%.0f".format(recipe.caloriesPerServing)} ккал"

        findViewById<TextView>(R.id.tvProtein).text =
            "Белки: ${"%.1f".format(recipe.proteinPerServing)} г"

        findViewById<TextView>(R.id.tvFat).text =
            "Жиры: ${"%.1f".format(recipe.fatPerServing)} г"

        findViewById<TextView>(R.id.tvCarbs).text =
            "Углеводы: ${"%.1f".format(recipe.carbsPerServing)} г"
        findViewById<TextView>(R.id.tvDescription).text = recipe.description
        findViewById<TextView>(R.id.tvInstructions).text = recipe.instructions

        val cardMeta = findViewById<View>(R.id.cardMeta)
        val dishTypeLabelView = findViewById<TextView>(R.id.labelDishType)
        val dishTypeView = findViewById<TextView>(R.id.tvDishType)
        val cookingMethodLabelView = findViewById<TextView>(R.id.labelCookingMethod)
        val cookingMethodView = findViewById<TextView>(R.id.tvCookingMethod)
        val viewsView = findViewById<TextView>(R.id.tvViews)

        val dishTypeText = formatDishTypes(recipe.dishType)
        val hasDishType = !dishTypeText.isNullOrBlank()
        val cookingMethodText = formatCookingMethods(recipe.cookingMethod)
        val hasCookingMethod = !cookingMethodText.isNullOrBlank()

        if (!hasDishType && !hasCookingMethod && recipe.viewCount == 0) {
            cardMeta.visibility = View.GONE
        } else {
            cardMeta.visibility = View.VISIBLE

            if (hasDishType) {
                dishTypeLabelView.visibility = View.VISIBLE
                dishTypeView.visibility = View.VISIBLE
                dishTypeView.text = dishTypeText
            } else {
                dishTypeLabelView.visibility = View.GONE
                dishTypeView.visibility = View.GONE
            }

            if (hasCookingMethod) {
                cookingMethodLabelView.visibility = View.VISIBLE
                cookingMethodView.visibility = View.VISIBLE
                cookingMethodView.text = cookingMethodText
            } else {
                cookingMethodLabelView.visibility = View.GONE
                cookingMethodView.visibility = View.GONE
            }

            viewsView.text = getString(R.string.views_text, recipe.viewCount)
        }
    }

    private fun formatDishTypes(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val ids = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (ids.isEmpty()) return null

        val localized = ids.mapNotNull { id ->
            DishType.fromId(id)?.let { getString(it.titleRes) }
        }

        if (localized.isEmpty()) return null
        return localized.joinToString(", ")
    }

    private fun formatCookingMethods(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val ids = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (ids.isEmpty()) return null

        val localized = ids.mapNotNull { id ->
            CookingMethod.fromId(id)?.let { getString(it.titleRes) }
        }

        if (localized.isEmpty()) return null
        return localized.joinToString(", ")
    }

    private fun setupIngredients(ingredients: List<String>) {
        val container = findViewById<LinearLayout>(R.id.ingredientsContainer)
        container.removeAllViews()
        ingredients.forEach { ingredient ->
            val (name, amount) = parseIngredient(ingredient)
            val row = layoutInflater.inflate(
                R.layout.item_detail_ingredient_row,
                container,
                false
            )
            row.findViewById<TextView>(R.id.tvIngredientName).text = name
            row.findViewById<TextView>(R.id.tvIngredientAmount).text = amount
            container.addView(row)
        }
    }

    private fun parseIngredient(raw: String): Pair<String, String> {
        val parts = raw.trim().split("\\s+".toRegex())
        return if (parts.size >= 3) {
            val quantity = parts[parts.size - 2]
            val unit = localizeUnit(parts.last())
            val amount = "$quantity $unit"
            val name = parts.dropLast(2).joinToString(" ")
            name to amount
        } else {
            raw to ""
        }
    }

    private fun localizeUnit(rawUnit: String): String {
        val unitIndexByAlias = mapOf(
            "г" to 0, "g" to 0,
            "кг" to 1, "kg" to 1,
            "мл" to 2, "ml" to 2,
            "л" to 3, "l" to 3,
            "ч.л." to 4, "tsp" to 4,
            "ст.л." to 5, "tbsp" to 5,
            "щепотка" to 6, "pinch" to 6,
            "шт" to 7, "pcs" to 7, "pc" to 7
        )

        val idx = unitIndexByAlias[rawUnit.trim().lowercase()] ?: return rawUnit
        val localizedUnits = resources.getStringArray(R.array.units_array)
        return localizedUnits.getOrNull(idx) ?: rawUnit
    }

    private fun setupTags(tags: List<String>) {
        val label = findViewById<TextView>(R.id.labelTags)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupTags)
        chipGroup.removeAllViews()

        if (tags.isEmpty()) {
            label.visibility = View.GONE
            chipGroup.visibility = View.GONE
            return
        }

        label.visibility = View.VISIBLE
        chipGroup.visibility = View.VISIBLE

        val chipColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primaryAccent))
        val textColor = ContextCompat.getColor(this, android.R.color.white)
        val cornerRadius = resources.getDimension(R.dimen.chip_pill_radius)

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isClickable = false
                isCheckable = false
                isCloseIconVisible = false
                chipBackgroundColor = chipColor
                setTextColor(textColor)
                chipCornerRadius = cornerRadius
            }
            chipGroup.addView(chip)
        }
    }
}
