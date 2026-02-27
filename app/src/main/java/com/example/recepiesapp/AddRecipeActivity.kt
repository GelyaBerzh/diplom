package com.example.recepiesapp

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var ingredientsLayout: GridLayout
    private lateinit var ingredientsList: List<String>
    private lateinit var units: List<String>
    private var ingredientsFields: MutableList<Triple<AutoCompleteTextView, TextInputEditText, Spinner>> = mutableListOf()

    private lateinit var chipGroupTags: ChipGroup
    private lateinit var etTagInput: TextInputEditText
    private lateinit var btnAddTag: MaterialButton
    private lateinit var ivRecipeImage: ShapeableImageView
    private lateinit var etServings: TextInputEditText
    private lateinit var ingredientAdapter: ArrayAdapter<String>

    private val tags = mutableListOf<String>()
    private lateinit var dishTypeCheckboxes: List<CheckBox>
    private lateinit var cookingMethodCheckboxes: List<CheckBox>
    private var focusedView: View? = null
    private val placeholderTag = "ingredient_placeholder"
    private var selectedImageUri: Uri? = null
    private var imagePlaceholderPadding: Int = 0
    private var recipeToEdit: Recipe? = null
    private var isEditMode: Boolean = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                releasePersistedUriPermission(selectedImageUri)
                persistUriPermission(it)
                selectedImageUri = it
                updateImagePreview(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_recipe)

        // Настройка Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        scrollView = findViewById(R.id.addRecipeRoot)
        scrollView.applySystemBarsPadding(onInsetsChanged = { maybeScrollFocusedView() })

        imagePlaceholderPadding = resources.getDimensionPixelSize(R.dimen.recipe_image_placeholder_padding)
        ivRecipeImage = findViewById(R.id.ivRecipeImage)
        val btnChangeImage = findViewById<FloatingActionButton>(R.id.btnChangeImage)
        val savedUri = savedInstanceState?.getString(KEY_IMAGE_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        selectedImageUri = savedUri
        updateImagePreview(selectedImageUri)

        val imageClickListener = View.OnClickListener { openImagePicker() }
        ivRecipeImage.setOnClickListener(imageClickListener)
        btnChangeImage.setOnClickListener(imageClickListener)

        chipGroupTags = findViewById(R.id.chipGroupTags)
        etTagInput = findViewById(R.id.etTagInput)
        btnAddTag = findViewById<MaterialButton>(R.id.btnAddTag)
        etServings = findViewById(R.id.etServings)

        dishTypeCheckboxes = listOf(
            findViewById(R.id.cbDishCold),
            findViewById(R.id.cbDishFirst),
            findViewById(R.id.cbDishHot),
            findViewById(R.id.cbDishPreserves),
            findViewById(R.id.cbDishDessert),
            findViewById(R.id.cbDishDrinks),
            findViewById(R.id.cbDishSide),
            findViewById(R.id.cbDishSauces),
            findViewById(R.id.cbDishBakery)
        )

        cookingMethodCheckboxes = listOf(
            findViewById(R.id.cbMethodPot),
            findViewById(R.id.cbMethodPan),
            findViewById(R.id.cbMethodmulticooker),
            findViewById(R.id.cbMethodOven),
            findViewById(R.id.cbMethodGrill),
            findViewById(R.id.cbMethodblender)
        )

        listOf<View>(
            findViewById<TextInputEditText>(R.id.etTitle),
            findViewById<TextInputEditText>(R.id.etDescription),
            findViewById<TextInputEditText>(R.id.etInstructions),
            etServings,
            etTagInput
        ).forEach { it.registerAutoScroll() }

        btnAddTag.setOnClickListener {
            addTag()
        }

        ingredientsLayout = findViewById(R.id.ingredientsLayout)
        ingredientsList = listOf(
            "мука", "яйца", "соль", "сахар", "масло", "молоко",
            "картофель", "лук", "помидоры", "чеснок", "рис", "капуста"
        )

        units = listOf(
            "г", "кг", "мл", "л", "ч.л.", "ст.л.", "щепотка", "шт"
        )

        ingredientAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ingredientsList)

        recipeToEdit = retrieveRecipeToEdit()
        isEditMode = recipeToEdit != null
        if (isEditMode) {
            toolbar.title = getString(R.string.edit_recipe_title)
            populateFieldsFromRecipe(recipeToEdit!!)
        } else {
            addIngredientField(ingredientAdapter)
        }

        // Кнопка для добавления нового поля ингредиента
        findViewById<MaterialButton>(R.id.btnAddIngredient).setOnClickListener {
            addIngredientField(ingredientAdapter)
        }

        // Кнопка сохранения рецепта
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveRecipe()
        }
    }

    private fun addIngredientField(
        adapter: ArrayAdapter<String>,
        initialData: IngredientInitialData? = null
    ) {
        val inflater = layoutInflater
        val ingredientView = inflater.inflate(R.layout.item_ingredient, ingredientsLayout, false)
        ingredientView.layoutParams = createGridChildLayoutParams()

        val autoCompleteTextView = ingredientView.findViewById<AutoCompleteTextView>(R.id.etIngredient)
        autoCompleteTextView.setAdapter(adapter)

        val quantityEditText = ingredientView.findViewById<TextInputEditText>(R.id.etQuantity)
        val unitSpinner = ingredientView.findViewById<Spinner>(R.id.spUnit)

        autoCompleteTextView.registerAutoScroll()
        quantityEditText.registerAutoScroll()

        // настройка выпадающего списка для единиц измерения
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = unitAdapter

        initialData?.let { data ->
            autoCompleteTextView.setText(data.name, false)
            quantityEditText.setText(data.quantity)
            val unitIndex = units.indexOf(data.unit).takeIf { it >= 0 } ?: 0
            unitSpinner.setSelection(unitIndex)
        } ?: unitSpinner.setSelection(0)

        val removeButton = ingredientView.findViewById<MaterialButton>(R.id.btnRemove)

        val removeAction = {
            ingredientsLayout.removeView(ingredientView)
            ingredientsFields.removeIf { it.first == autoCompleteTextView }
            syncIngredientPlaceholders()
        }

        removeButton?.setOnClickListener { removeAction() }
        ingredientView.enableSwipeToDelete { removeAction() }

        ingredientsLayout.addView(ingredientView)
        ingredientsFields.add(Triple(autoCompleteTextView, quantityEditText, unitSpinner))
        syncIngredientPlaceholders()
    }

    private fun saveRecipe() {
        val title = findViewById<TextInputEditText>(R.id.etTitle).text.toString().trim()
        val description = findViewById<TextInputEditText>(R.id.etDescription).text.toString().trim()
        val instructions = findViewById<TextInputEditText>(R.id.etInstructions).text.toString().trim()
        val servingsValue = etServings.text?.toString()?.trim()
        val servings = servingsValue?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_SERVINGS

        val recipeTags = tags.toList()

        val dishType = dishTypeCheckboxes
            .filter { it.isChecked }
            .joinToString(", ") { it.text.toString() }

        val cookingMethod = cookingMethodCheckboxes
            .filter { it.isChecked }
            .joinToString(", ") { it.text.toString() }

        // Строки ингредиентов для отображения и хранения
        val ingredientsStrings = ingredientsFields.mapNotNull { (ingredient, quantity, unit) ->
            val ingredientText = ingredient.text.toString().trim()
            val quantityText = quantity.text.toString().trim()
            val unitText = (unit as Spinner).selectedItem.toString()
            if (ingredientText.isNotEmpty() && quantityText.isNotEmpty()) {
                "$ingredientText $quantityText $unitText"
            } else {
                null
            }
        }

        // Структура для расчёта КБЖУ
        val ingredientAmounts = ingredientsFields.mapNotNull { (ingredient, quantity, unit) ->
            val ingredientText = ingredient.text.toString().trim()
            val quantityText = quantity.text.toString().trim()
            val unitText = (unit as Spinner).selectedItem.toString()
            val quantityValue = quantityText.toDoubleOrNull()

            if (ingredientText.isNotEmpty() && quantityValue != null) {
                IngredientAmount(
                    name = ingredientText,
                    quantity = quantityValue,
                    unit = unitText
                )
            } else {
                null
            }
        }

        if (title.isNotEmpty() && ingredientsStrings.isNotEmpty()) {
            val recipeId = recipeToEdit?.id ?: generateUniqueId()
            val existingViewCount = recipeToEdit?.viewCount ?: 0

            val nutritionSummary = calculateRecipeNutrition(ingredientAmounts, servings)

            val resultRecipe = Recipe(
                id = recipeId,
                title = title,
                ingredients = ingredientsStrings,
                description = description,
                instructions = instructions,
                tags = recipeTags,
                dishType = dishType.takeIf { it.isNotEmpty() },
                cookingMethod = cookingMethod.takeIf { it.isNotEmpty() },
                servings = servings,
                imageUri = selectedImageUri?.toString(),
                caloriesPerServing = nutritionSummary.calories,
                proteinPerServing = nutritionSummary.protein,
                fatPerServing = nutritionSummary.fat,
                carbsPerServing = nutritionSummary.carbs,
                viewCount = existingViewCount
            )

            val resultIntent = Intent().apply {
                putExtra(
                    if (isEditMode) EXTRA_UPDATED_RECIPE else EXTRA_NEW_RECIPE,
                    resultRecipe
                )
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            Toast.makeText(this, "Некорректные данные рецепта", Toast.LENGTH_SHORT).show()
            Log.e("AddRecipeActivity", "Некорректные данные рецепта")
        }
    }

    private fun generateUniqueId(): Int {
        // Генерация уникального ID
        return (System.currentTimeMillis() % 1000000).toInt()
    }

    private fun addTag() {
        val tagText = etTagInput.text.toString().trim()
        if (tagText.isNotEmpty()) {
            val tagWithHash = "#$tagText"
            tags.add(tagWithHash)
            createChip(tagWithHash)
            etTagInput.text?.clear()
        }
    }

    private fun createChip(tag: String) {
        val chip = Chip(this).apply {
            text = tag
            isCloseIconVisible = true
            setOnClickListener {
                chipGroupTags.removeView(this)
                tags.remove(tag)
            }
        }

        chipGroupTags.addView(chip)
    }

    private fun View.registerAutoScroll() {
        setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                focusedView = v
                scrollToView(v)
            } else if (focusedView == v) {
                focusedView = null
            }
        }
    }

    private fun scrollToView(target: View) {
        scrollView.post {
            val rect = Rect()
            target.getDrawingRect(rect)
            scrollView.offsetDescendantRectToMyCoords(target, rect)
            val offset = dpToPx(50)
            val y = (rect.top - offset).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, y)
        }
    }

    private fun maybeScrollFocusedView() {
        focusedView?.let { scrollToView(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedImageUri?.toString()?.let { outState.putString(KEY_IMAGE_URI, it) }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun createGridChildLayoutParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

    private fun syncIngredientPlaceholders() {
        val placeholders = mutableListOf<View>()
        for (i in 0 until ingredientsLayout.childCount) {
            val child = ingredientsLayout.getChildAt(i)
            if (child.tag == placeholderTag) {
                placeholders.add(child)
            }
        }
        placeholders.forEach { ingredientsLayout.removeView(it) }

        if (ingredientsFields.size % 2 != 0) {
            val placeholder = View(this).apply {
                tag = placeholderTag
                isEnabled = false
                isClickable = false
                layoutParams = createGridChildLayoutParams()
                alpha = 0f
            }
            ingredientsLayout.addView(placeholder)
        }
    }

    private fun View.enableSwipeToDelete(onDelete: () -> Unit) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var swiping = false

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    swiping = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    if (!swiping && deltaX < -touchSlop) {
                        swiping = true
                    }
                    if (swiping) {
                        val translation = deltaX.coerceAtMost(0f)
                        translationX = translation
                        val alphaFactor = 1f + (translation / width.coerceAtLeast(1))
                        alpha = alphaFactor.coerceIn(0.2f, 1f)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (swiping) {
                        val shouldDismiss = translationX <= -width * 0.35f
                        if (shouldDismiss) {
                            animate()
                                .translationX(-width.toFloat())
                                .alpha(0f)
                                .setDuration(200L)
                                .withEndAction {
                                    onDelete()
                                    translationX = 0f
                                    alpha = 1f
                                }
                                .start()
                        } else {
                            animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(200L)
                                .start()
                        }
                        swiping = false
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    private fun openImagePicker() {
        pickImageLauncher.launch(IMAGE_MIME_TYPES)
    }

    private fun updateImagePreview(uri: Uri?) {
        if (uri == null) {
            ivRecipeImage.setImageResource(R.drawable.ic_camera)
            ivRecipeImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
            ivRecipeImage.setPadding(
                imagePlaceholderPadding,
                imagePlaceholderPadding,
                imagePlaceholderPadding,
                imagePlaceholderPadding
            )
        } else {
            ivRecipeImage.setImageURI(uri)
            ivRecipeImage.scaleType = ImageView.ScaleType.CENTER_CROP
            ivRecipeImage.setPadding(0, 0, 0, 0)
        }
    }

    private fun retrieveRecipeToEdit(): Recipe? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_RECIPE_TO_EDIT, Recipe::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_RECIPE_TO_EDIT) as? Recipe
        }

    private fun populateFieldsFromRecipe(recipe: Recipe) {
        findViewById<TextInputEditText>(R.id.etTitle).setText(recipe.title)
        findViewById<TextInputEditText>(R.id.etDescription).setText(recipe.description)
        findViewById<TextInputEditText>(R.id.etInstructions).setText(recipe.instructions)
        etServings.setText(recipe.servings.toString())

        val selectedDishTypes = recipe.dishType
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()
        dishTypeCheckboxes.forEach { checkBox ->
            checkBox.isChecked = selectedDishTypes.contains(checkBox.text.toString())
        }

        val selectedCookingMethods = recipe.cookingMethod
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()
        cookingMethodCheckboxes.forEach { checkBox ->
            checkBox.isChecked = selectedCookingMethods.contains(checkBox.text.toString())
        }

        if (selectedImageUri == null && !recipe.imageUri.isNullOrBlank()) {
            selectedImageUri = Uri.parse(recipe.imageUri)
        }
        updateImagePreview(selectedImageUri)

        tags.clear()
        chipGroupTags.removeAllViews()
        recipe.tags.forEach {
            tags.add(it)
            createChip(it)
        }

        ingredientsLayout.removeAllViews()
        ingredientsFields.clear()
        recipe.ingredients.forEach { ingredient ->
            val parsed = parseIngredientForEdit(ingredient)
            addIngredientField(ingredientAdapter, parsed)
        }
        if (ingredientsFields.isEmpty()) {
            addIngredientField(ingredientAdapter)
        } else {
            syncIngredientPlaceholders()
        }
    }

    private fun parseIngredientForEdit(raw: String): IngredientInitialData {
        val parts = raw.trim().split("\\s+".toRegex())
        return if (parts.size >= 3) {
            val unitCandidate = parts.last()
            val quantityCandidate = parts[parts.size - 2]
            val name = parts.dropLast(2).joinToString(" ")
            val normalizedUnit = if (units.contains(unitCandidate)) unitCandidate else units.first()
            IngredientInitialData(name, quantityCandidate, normalizedUnit)
        } else {
            IngredientInitialData(raw, "", units.first())
        }
    }

    private fun persistUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Не удалось сохранить доступ к изображению", securityException)
        }
    }

    private fun releasePersistedUriPermission(uri: Uri?) {
        if (uri == null) return
        try {
            contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Не удалось освободить доступ к изображению", securityException)
        }
    }

    private data class IngredientInitialData(
        val name: String,
        val quantity: String,
        val unit: String
    )

    private data class Nutrition(
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double
    )

    private data class IngredientAmount(
        val name: String,
        val quantity: Double,
        val unit: String
    )

    // База КБЖУ на 100 г / мл / шт (примерные значения)
    private val ingredientNutrition: Map<String, Nutrition> = mapOf(
        "мука" to Nutrition(364.0, 10.3, 1.0, 76.0),
        "яйца" to Nutrition(157.0, 12.7, 11.5, 0.7),
        "соль" to Nutrition(0.0, 0.0, 0.0, 0.0),
        "сахар" to Nutrition(399.0, 0.0, 0.0, 99.8),
        "масло" to Nutrition(748.0, 0.5, 82.5, 0.8),
        "молоко" to Nutrition(60.0, 3.2, 3.5, 4.7),
        "картофель" to Nutrition(77.0, 2.0, 0.4, 17.0),
        "лук" to Nutrition(41.0, 1.4, 0.2, 9.3),
        "помидоры" to Nutrition(23.0, 1.1, 0.2, 3.8),
        "чеснок" to Nutrition(149.0, 6.4, 0.5, 33.1),
        "рис" to Nutrition(344.0, 7.0, 0.6, 76.0),
        "капуста" to Nutrition(27.0, 1.8, 0.1, 5.4)
    )

    private fun toGrams(quantity: Double, unit: String, ingredientName: String): Double {
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

    private data class NutritionSummary(
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double
    )

    private fun calculateRecipeNutrition(
        ingredients: List<IngredientAmount>,
        servings: Int
    ): NutritionSummary {
        var calories = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0

        for (item in ingredients) {
            val base = ingredientNutrition[item.name.lowercase()] ?: continue
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

    companion object {
        private const val TAG = "AddRecipeActivity"
        private const val KEY_IMAGE_URI = "key_image_uri"
        const val EXTRA_NEW_RECIPE = "NEW_RECIPE"
        const val EXTRA_UPDATED_RECIPE = "UPDATED_RECIPE"
        const val EXTRA_RECIPE_TO_EDIT = "EXTRA_RECIPE_TO_EDIT"
        private const val DEFAULT_SERVINGS = 1
        private val IMAGE_MIME_TYPES = arrayOf("image/*")
    }
}