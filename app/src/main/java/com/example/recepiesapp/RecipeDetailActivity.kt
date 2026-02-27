package com.example.recepiesapp

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView

class RecipeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recipe_detail)

        findViewById<MaterialToolbar>(R.id.toolbarRecipeDetail).apply {
            setNavigationOnClickListener { finish() }
        }

        findViewById<View>(R.id.recipeDetailRoot).applySystemBarsPadding()

        val recipe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("RECIPE", Recipe::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("RECIPE") as? Recipe
        } ?: return

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
        findViewById<TextView>(R.id.tvDescription).text =
            "${getString(R.string.recipe_description)}:\n${recipe.description}"
        findViewById<TextView>(R.id.tvInstructions).text =
            "${getString(R.string.instructions_label)}:\n${recipe.instructions}"

        val cardMeta = findViewById<View>(R.id.cardMeta)
        val dishTypeLabelView = findViewById<TextView>(R.id.labelDishType)
        val dishTypeView = findViewById<TextView>(R.id.tvDishType)
        val cookingMethodLabelView = findViewById<TextView>(R.id.labelCookingMethod)
        val cookingMethodView = findViewById<TextView>(R.id.tvCookingMethod)
        val viewsView = findViewById<TextView>(R.id.tvViews)

        val hasDishType = !recipe.dishType.isNullOrBlank()
        val hasCookingMethod = !recipe.cookingMethod.isNullOrBlank()

        if (!hasDishType && !hasCookingMethod && recipe.viewCount == 0) {
            cardMeta.visibility = View.GONE
        } else {
            cardMeta.visibility = View.VISIBLE

            if (hasDishType) {
                dishTypeLabelView.visibility = View.VISIBLE
                dishTypeView.visibility = View.VISIBLE
                dishTypeView.text = recipe.dishType
            } else {
                dishTypeLabelView.visibility = View.GONE
                dishTypeView.visibility = View.GONE
            }

            if (hasCookingMethod) {
                cookingMethodLabelView.visibility = View.VISIBLE
                cookingMethodView.visibility = View.VISIBLE
                cookingMethodView.text = recipe.cookingMethod
            } else {
                cookingMethodLabelView.visibility = View.GONE
                cookingMethodView.visibility = View.GONE
            }

            viewsView.text = getString(R.string.views_text, recipe.viewCount)
        }
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
            val amount = parts.takeLast(2).joinToString(" ")
            val name = parts.dropLast(2).joinToString(" ")
            name to amount
        } else {
            raw to ""
        }
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
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondaryAccent))
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
