package com.example.recepiesapp

import android.content.Intent
import android.graphics.Rect
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
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

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
    private lateinit var btnImageAction: FloatingActionButton
    private lateinit var etServings: TextInputEditText
    private lateinit var etCookingTime: TextInputEditText
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etInstructions: TextInputEditText
    private lateinit var ingredientAdapter: ArrayAdapter<String>

    private val tags = mutableListOf<String>()
    private lateinit var dishTypeCheckboxes: List<CheckBox>
    private lateinit var dishTypeMap: Map<Int, DishType>
    private lateinit var cookingMethodCheckboxes: List<CheckBox>
    private lateinit var cookingMethodMap: Map<Int, CookingMethod>
    private var focusedView: View? = null
    private val placeholderTag = "ingredient_placeholder"
    private var selectedImagePath: String? = null
    private var imagePlaceholderPadding: Int = 0
    private var recipeToEdit: Recipe? = null
    private var isEditMode: Boolean = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val localPath = RecipeImageUtils.copyImageFromUri(this, it)
                if (localPath == null) {
                    Toast.makeText(this, getString(R.string.image_load_error), Toast.LENGTH_SHORT).show()
                    return@let
                }
                if (selectedImagePath != recipeToEdit?.imageUri) {
                    RecipeImageUtils.deleteImageIfOwned(this, selectedImagePath)
                }
                selectedImagePath = localPath
                updateImagePreview(localPath)
                updateImageActionButton()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_recipe)

        // Фиксируем цвет status bar, чтобы при IME/Insets не "подмешивался" фон экрана.
        window.statusBarColor = ContextCompat.getColor(this, R.color.primaryAccent)

        // Настройка Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        // Только верхний системный inset для AppBar (без IME), чтобы toolbar не залезал под шторку.
        findViewById<AppBarLayout>(R.id.appBarAddRecipe).applySystemBarsPadding(
            applyLeft = false,
            applyRight = false,
            applyBottom = false,
            includeIme = false
        )
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        scrollView = findViewById(R.id.addRecipeRoot)
        // Верхний inset отдаём AppBarLayout, а ScrollView пусть реагирует на IME снизу.
        scrollView.applySystemBarsPadding(applyTop = false, includeIme = true, onInsetsChanged = { maybeScrollFocusedView() })

        imagePlaceholderPadding = resources.getDimensionPixelSize(R.dimen.recipe_image_placeholder_padding)
        ivRecipeImage = findViewById(R.id.ivRecipeImage)
        btnImageAction = findViewById(R.id.btnImageAction)
        selectedImagePath = savedInstanceState?.getString(KEY_IMAGE_PATH)?.takeIf { it.isNotBlank() }
        updateImagePreview(selectedImagePath)
        updateImageActionButton()

        ivRecipeImage.setOnClickListener { openImagePicker() }

        chipGroupTags = findViewById(R.id.chipGroupTags)
        etTagInput = findViewById(R.id.etTagInput)
        btnAddTag = findViewById<MaterialButton>(R.id.btnAddTag)
        etServings = findViewById(R.id.etServings)
        etCookingTime = findViewById(R.id.etCookingTime)
        etTitle = findViewById(R.id.etTitle)
        etDescription = findViewById(R.id.etDescription)
        etInstructions = findViewById(R.id.etInstructions)

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
        dishTypeMap = mapOf(
            R.id.cbDishCold to DishType.COLD,
            R.id.cbDishFirst to DishType.FIRST,
            R.id.cbDishHot to DishType.HOT,
            R.id.cbDishPreserves to DishType.PRESERVES,
            R.id.cbDishDessert to DishType.DESSERT,
            R.id.cbDishDrinks to DishType.DRINKS,
            R.id.cbDishSide to DishType.SIDE,
            R.id.cbDishSauces to DishType.SAUCES,
            R.id.cbDishBakery to DishType.BAKERY
        )

        cookingMethodCheckboxes = listOf(
            findViewById(R.id.cbMethodPot),
            findViewById(R.id.cbMethodPan),
            findViewById(R.id.cbMethodmulticooker),
            findViewById(R.id.cbMethodOven),
            findViewById(R.id.cbMethodGrill),
            findViewById(R.id.cbMethodblender)
        )
        cookingMethodMap = mapOf(
            R.id.cbMethodPot to CookingMethod.POT,
            R.id.cbMethodPan to CookingMethod.PAN,
            R.id.cbMethodmulticooker to CookingMethod.MULTICOOKER,
            R.id.cbMethodOven to CookingMethod.OVEN,
            R.id.cbMethodGrill to CookingMethod.GRILL,
            R.id.cbMethodblender to CookingMethod.BLENDER
        )

        listOf<View>(
            etTitle,
            etDescription,
            etInstructions,
            etServings,
            etCookingTime,
            etTagInput
        ).forEach { it.registerAutoScroll() }

        registerValidationAutoClear(etTitle)
        registerValidationAutoClear(etDescription)
        registerValidationAutoClear(etInstructions)
        registerValidationAutoClear(etServings)
        registerValidationAutoClear(etCookingTime)

        btnAddTag.setOnClickListener {
            addTag()
        }

        ingredientsLayout = findViewById(R.id.ingredientsLayout)
        ingredientsList = INGREDIENT_SUGGESTIONS

        units = resources.getStringArray(R.array.units_array).toList()

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
        registerValidationAutoClear(autoCompleteTextView)
        registerValidationAutoClear(quantityEditText)

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

    }

    private fun saveRecipe() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val instructions = etInstructions.text.toString().trim()
        val servingsValue = etServings.text?.toString()?.trim()
        val servings = servingsValue?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_SERVINGS
        val cookingTimeMinutes = etCookingTime.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        val recipeTags = tags.toList()

        val selectedDishTypeIds = dishTypeCheckboxes
            .filter { it.isChecked }
            .mapNotNull { dishTypeMap[it.id]?.id }
        val dishType = selectedDishTypeIds.joinToString(",")

        val selectedCookingMethodIds = cookingMethodCheckboxes
            .filter { it.isChecked }
            .mapNotNull { cookingMethodMap[it.id]?.id }
        val cookingMethod = selectedCookingMethodIds.joinToString(",")

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

        val validationResult = validateRequiredFields(
            title = title,
            description = description,
            instructions = instructions,
            servingsValue = servingsValue
        )
        if (!validationResult.isValid) {
            validationResult.firstInvalidView?.let { scrollToView(it) }
            val messageRes = if (validationResult.invalidFieldsCount > 1) {
                R.string.recipe_required_fields_many
            } else {
                R.string.recipe_required_field_single
            }
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
            Log.e("AddRecipeActivity", "Не заполнены обязательные поля: ${validationResult.invalidFieldsCount}")
            return
        }

        if (ingredientsStrings.isNotEmpty()) {
            lifecycleScope.launch {
                val repository = RecipeRepository(this@AddRecipeActivity)
                val recipeId = recipeToEdit?.id ?: generateUniqueId()
                val existingViewCount = recipeToEdit?.viewCount ?: 0
                val existingIsFavorite = recipeToEdit?.isFavorite ?: false

                val nutritionSummary =
                    repository.calculateRecipeNutritionFromDb(ingredientAmounts, servings)

                val imageUri = RecipeImageUtils.finalizeRecipeImage(
                    this@AddRecipeActivity,
                    selectedImagePath,
                    recipeId,
                    recipeToEdit?.imageUri
                )

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
                    imageUri = imageUri,
                    cookingTimeMinutes = cookingTimeMinutes,
                    caloriesPerServing = nutritionSummary.calories,
                    proteinPerServing = nutritionSummary.protein,
                    fatPerServing = nutritionSummary.fat,
                    carbsPerServing = nutritionSummary.carbs,
                    viewCount = existingViewCount,
                    isFavorite = existingIsFavorite
                )

                val resultIntent = Intent().apply {
                    putExtra(
                        if (isEditMode) EXTRA_UPDATED_RECIPE else EXTRA_NEW_RECIPE,
                        resultRecipe
                    )
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        } else {
            Toast.makeText(this, getString(R.string.recipe_required_field_single), Toast.LENGTH_SHORT).show()
            Log.e("AddRecipeActivity", "Некорректные данные рецепта")
        }
    }

    private fun validateRequiredFields(
        title: String,
        description: String,
        instructions: String,
        servingsValue: String?
    ): ValidationResult {
        var invalidCount = 0
        var firstInvalidView: View? = null

        fun markInvalid(view: View, messageRes: Int) {
            invalidCount += 1
            if (firstInvalidView == null) firstInvalidView = view
            view.findTextInputLayout()?.error = getString(messageRes)
        }

        fun clearError(view: View) {
            view.findTextInputLayout()?.error = null
        }

        if (title.isBlank()) {
            markInvalid(etTitle, R.string.error_required_field)
        } else {
            clearError(etTitle)
        }

        if (description.isBlank()) {
            markInvalid(etDescription, R.string.error_required_field)
        } else {
            clearError(etDescription)
        }

        if (instructions.isBlank()) {
            markInvalid(etInstructions, R.string.error_required_field)
        } else {
            clearError(etInstructions)
        }

        val servings = servingsValue?.toIntOrNull()
        if (servingsValue.isNullOrBlank() || servings == null || servings <= 0) {
            markInvalid(etServings, R.string.error_servings_positive)
        } else {
            clearError(etServings)
        }

        var hasAtLeastOneValidIngredient = false
        ingredientsFields.forEach { (ingredient, quantity, _) ->
            val ingredientText = ingredient.text?.toString()?.trim().orEmpty()
            val quantityText = quantity.text?.toString()?.trim().orEmpty()
            val quantityValue = quantityText.toDoubleOrNull()
            val ingredientLayout = ingredient.findTextInputLayout()
            val quantityLayout = quantity.findTextInputLayout()

            ingredientLayout?.error = null
            quantityLayout?.error = null

            val ingredientFilled = ingredientText.isNotEmpty()
            val quantityFilled = quantityText.isNotEmpty()
            val isRowEmpty = !ingredientFilled && !quantityFilled

            if (isRowEmpty) return@forEach

            val isIngredientValid = ingredientFilled
            val isQuantityValid = quantityFilled && quantityValue != null && quantityValue > 0.0
            if (isIngredientValid && isQuantityValid) {
                hasAtLeastOneValidIngredient = true
            } else {
                if (!isIngredientValid) {
                    invalidCount += 1
                    if (firstInvalidView == null) firstInvalidView = ingredient
                    ingredientLayout?.error = getString(R.string.error_required_field)
                }
                if (!isQuantityValid) {
                    invalidCount += 1
                    if (firstInvalidView == null) firstInvalidView = quantity
                    quantityLayout?.error = getString(R.string.error_quantity_positive)
                }
            }
        }

        if (!hasAtLeastOneValidIngredient) {
            invalidCount += 1
            val firstIngredient = ingredientsFields.firstOrNull()?.first ?: ingredientsLayout
            if (firstInvalidView == null) firstInvalidView = firstIngredient
            firstIngredient.findTextInputLayout()?.error = getString(R.string.error_add_ingredient)
        }

        return ValidationResult(
            isValid = invalidCount == 0,
            firstInvalidView = firstInvalidView,
            invalidFieldsCount = invalidCount
        )
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

    private fun registerValidationAutoClear(view: View) {
        when (view) {
            is TextInputEditText -> view.doAfterTextChanged {
                view.findTextInputLayout()?.error = null
            }
            is AutoCompleteTextView -> view.doAfterTextChanged {
                view.findTextInputLayout()?.error = null
            }
        }
    }

    private fun View.findTextInputLayout(): TextInputLayout? {
        var current: View? = this
        while (current != null) {
            if (current is TextInputLayout) return current
            current = current.parent as? View
        }
        return null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedImagePath?.let { outState.putString(KEY_IMAGE_PATH, it) }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun createGridChildLayoutParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.MATCH_PARENT
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(0, 1)
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

    private fun syncIngredientPlaceholders() {
        // Больше не нужно: ингредиенты отображаются в 1 колонку.
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

    private fun removeImage() {
        if (selectedImagePath != recipeToEdit?.imageUri) {
            RecipeImageUtils.deleteImageIfOwned(this, selectedImagePath)
        }
        selectedImagePath = null
        updateImagePreview(null)
        updateImageActionButton()
    }

    private fun updateImageActionButton() {
        val hasImage = selectedImagePath != null
        if (hasImage) {
            btnImageAction.setImageResource(android.R.drawable.ic_menu_delete)
            btnImageAction.contentDescription = getString(R.string.remove_image)
            btnImageAction.setOnClickListener { removeImage() }
        } else {
            btnImageAction.setImageResource(R.drawable.ic_camera)
            btnImageAction.contentDescription = getString(R.string.select_image)
            btnImageAction.setOnClickListener { openImagePicker() }
        }
    }

    private fun updateImagePreview(imagePath: String?) {
        RecipeImageUtils.setRecipeImage(ivRecipeImage, imagePath, imagePlaceholderPadding)
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
        etCookingTime.setText(recipe.cookingTimeMinutes.takeIf { it > 0 }?.toString().orEmpty())

        val selectedDishTypeIds = recipe.dishType
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()
        dishTypeCheckboxes.forEach { checkBox ->
            val type = dishTypeMap[checkBox.id]
            checkBox.isChecked = type != null && selectedDishTypeIds.contains(type.id)
        }

        val selectedCookingMethodIds = recipe.cookingMethod
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        cookingMethodCheckboxes.forEach { checkBox ->
            val method = cookingMethodMap[checkBox.id]
            checkBox.isChecked = method != null && selectedCookingMethodIds.contains(method.id)
        }

        if (selectedImagePath == null && !recipe.imageUri.isNullOrBlank()) {
            selectedImagePath = recipe.imageUri
        }
        updateImagePreview(selectedImagePath)
        updateImageActionButton()

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

    private data class IngredientInitialData(
        val name: String,
        val quantity: String,
        val unit: String
    )

    private data class ValidationResult(
        val isValid: Boolean,
        val firstInvalidView: View?,
        val invalidFieldsCount: Int
    )

    companion object {
        private const val TAG = "AddRecipeActivity"
        private const val KEY_IMAGE_PATH = "key_image_path"
        const val EXTRA_NEW_RECIPE = "NEW_RECIPE"
        const val EXTRA_UPDATED_RECIPE = "UPDATED_RECIPE"
        const val EXTRA_RECIPE_TO_EDIT = "EXTRA_RECIPE_TO_EDIT"
        private const val DEFAULT_SERVINGS = 1
        private val IMAGE_MIME_TYPES = arrayOf("image/*")
    }
}