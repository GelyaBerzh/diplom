package com.example.recepiesapp

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var ingredientsLayout: LinearLayout
    private lateinit var ingredientsList: List<String>
    private lateinit var units: List<String>
    private var ingredientsFields: MutableList<Triple<AutoCompleteTextView, EditText, Spinner>> = mutableListOf()

    private lateinit var chipGroupTags: ChipGroup
    private lateinit var etTagInput: EditText
    private lateinit var btnAddTag: Button

    private val tags = mutableListOf<String>()
    private var focusedView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_recipe)
        scrollView = findViewById(R.id.addRecipeRoot)
        scrollView.applySystemBarsPadding(onInsetsChanged = { maybeScrollFocusedView() })

        chipGroupTags = findViewById(R.id.chipGroupTags)
        etTagInput = findViewById(R.id.etTagInput)
        btnAddTag = findViewById(R.id.btnAddTag)

        listOf<View>(
            findViewById(R.id.etTitle),
            findViewById(R.id.etDescription),
            findViewById(R.id.etInstructions),
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

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ingredientsList)

        // Инициализация первого поля ингредиента
        addIngredientField(adapter)

        // Кнопка для добавления нового поля ингредиента
        findViewById<Button>(R.id.btnAddIngredient).setOnClickListener {
            addIngredientField(adapter)
        }

        // Кнопка сохранения рецепта
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveRecipe()
        }
    }

    private fun addIngredientField(adapter: ArrayAdapter<String>) {
        val inflater = layoutInflater
        val ingredientView = inflater.inflate(R.layout.item_ingredient, ingredientsLayout, false)

        val autoCompleteTextView = ingredientView.findViewById<AutoCompleteTextView>(R.id.etIngredient)
        autoCompleteTextView.setAdapter(adapter)

        val quantityEditText = ingredientView.findViewById<EditText>(R.id.etQuantity)
        val unitSpinner = ingredientView.findViewById<Spinner>(R.id.spUnit)

        autoCompleteTextView.registerAutoScroll()
        quantityEditText.registerAutoScroll()

        // настройка выпадающего списка для единиц измерения
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = unitAdapter

        unitSpinner.setSelection(0)

        val removeButton = ingredientView.findViewById<ImageButton>(R.id.btnRemove)

        // Добавление обработчика для кнопки удаления
        removeButton?.setOnClickListener {
            ingredientsLayout.removeView(ingredientView) // удаляем вид
            ingredientsFields.removeIf { it.first == autoCompleteTextView } // удаляем из списка
        }

        ingredientsLayout.addView(ingredientView)
        ingredientsFields.add(Triple(autoCompleteTextView, quantityEditText, unitSpinner))
    }

    private fun saveRecipe() {
        val title = findViewById<EditText>(R.id.etTitle).text.toString().trim()
        val description = findViewById<EditText>(R.id.etDescription).text.toString().trim()
        val instructions = findViewById<EditText>(R.id.etInstructions).text.toString().trim()

//        val tags = findViewById<EditText>(R.id.etTags).text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val recipeTags = tags.toList()

        val ingredients = ingredientsFields.mapNotNull { (ingredient, quantity, unit) ->
            val ingredientText = ingredient.text.toString().trim()
            val quantityText = quantity.text.toString().trim()
            val unitText = (unit as Spinner).selectedItem.toString()
            if (ingredientText.isNotEmpty() && quantityText.isNotEmpty()) {
                "$ingredientText $quantityText $unitText"
            } else {
                null
            }
        }

        if (title.isNotEmpty() && ingredients.isNotEmpty()) {
            val newRecipe = Recipe(
                id = generateUniqueId(),
                title = title,
                ingredients = ingredients,
                description = description,
                instructions = instructions,
                tags = recipeTags
            )

            val resultIntent = Intent().apply {
                putExtra("NEW_RECIPE", newRecipe)
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
        val tagText = "#" + etTagInput.text.toString().trim()
        if (tagText.isNotEmpty()) {
            tags.add(tagText)
            createChip(tagText)
            etTagInput.text.clear()
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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}