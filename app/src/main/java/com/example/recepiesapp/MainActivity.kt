package com.example.recepiesapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recepiesapp.databinding.ActivityMainBinding
import java.util.Locale



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recipeRepository: RecipeRepository
    private var recipes: MutableList<Recipe> = mutableListOf()
    private var currentCategoryFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)

        setAppLanguage(getCurrentLanguage())
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        recipeRepository = RecipeRepository(this)
        recipes = recipeRepository.loadRecipes()
        recipesAdapter = RecipesAdapter(
            onItemClick = { openRecipeDetails(it) },
            getDescriptionPreview = ::getDescriptionPreview,
            onEditClick = { editRecipe(it) },
            onDeleteClick = { confirmDeleteRecipe(it) }
        )

        setupRecyclerView()
        setupSearch()
        setupAddButton()
        setupSettings()
        setupBottomPanel()
    }

    @Suppress("DEPRECATION")
    private fun setAppLanguage(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewRecipes.adapter = recipesAdapter
        recipesAdapter.submitList(recipes.toList())
    }

    private fun setupSearch() {
        binding.searchView.apply {
            setIconifiedByDefault(false)
            isIconified = false
            setOnClickListener {
                isIconified = false
                requestFocus()
            }
        }
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchRecipes(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchRecipes(newText ?: "")
                return true
            }
        })
        binding.searchView.clearFocus()
    }

    private fun searchRecipes(query: String) {
        val category = currentCategoryFilter
        val filteredRecipes = recipes.filter { recipe ->
            val matchesQuery =
                query.isEmpty() ||
                        recipe.title.contains(query, ignoreCase = true) ||
                        recipe.ingredients.any { it.contains(query, ignoreCase = true) } ||
                        recipe.tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = category == null ||
                    (recipe.dishType?.contains(category, ignoreCase = true) == true)

            matchesQuery && matchesCategory
        }
        recipesAdapter.submitList(filteredRecipes)
    }

    private fun setupBottomPanel() {
        binding.btnAllRecipes.setOnClickListener {
            currentCategoryFilter = null
            binding.searchView.setQuery("", false)
            searchRecipes("")
        }

        binding.btnDishSections.setOnClickListener {
            showCategoryFilterDialog()
        }


    }

    private fun showCategoryFilterDialog() {
        val categories = arrayOf(
            "Холодные закуски",
            "Первые блюда",
            "Горячие блюда",
            "Домашние заготовки",
            "Десерты",
            "Напитки",
            "Гарниры",
            "Соусы",
            "Выпечка"
        )

        val items = arrayOf("Все разделы") + categories

        AlertDialog.Builder(this)
            .setTitle("Разделы")
            .setItems(items) { dialog, which ->
                currentCategoryFilter = if (which == 0) null else categories[which - 1]
                val currentQuery = binding.searchView.query?.toString() ?: ""
                searchRecipes(currentQuery)
                dialog.dismiss()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun setupAddButton() {
        binding.btnAddRecipe.setOnClickListener {
            val intent = Intent(this, AddRecipeActivity::class.java)
            startActivityForResult(intent, REQUEST_ADD_EDIT_RECIPE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ADD_EDIT_RECIPE || resultCode != RESULT_OK || data == null) return

        val updatedRecipe = data.getRecipeFromResult(AddRecipeActivity.EXTRA_UPDATED_RECIPE)
        val newRecipe = data.getRecipeFromResult(AddRecipeActivity.EXTRA_NEW_RECIPE)

        when {
            updatedRecipe != null -> updateRecipe(updatedRecipe)
            newRecipe != null -> addRecipe(newRecipe)
        }
    }

    fun addRecipe(newRecipe: Recipe) {
        recipes.add(newRecipe)
        recipesAdapter.submitList(recipes.toList())
        recipeRepository.saveRecipes(recipes)
        Log.d("RecipesApp Add", "Рецепт добавлен и сохранён: $newRecipe")
    }

    private fun updateRecipe(updatedRecipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == updatedRecipe.id }
        if (index >= 0) {
            recipes[index] = updatedRecipe
            recipesAdapter.submitList(recipes.toList())
            recipeRepository.saveRecipes(recipes)
            Log.d("RecipesApp Update", "Рецепт обновлён: $updatedRecipe")
        } else {
            addRecipe(updatedRecipe)
        }
    }

    fun getDescriptionPreview(description: String): String {
        return if (description.length > 200) {
            description.substring(0, 200) + "..."
        }
        else {
            description
        }
    }

    private fun setupSettings() {
        binding.ivSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spLanguage = dialogView.findViewById<Spinner>(R.id.spLanguage)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClose)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnTermsOfUse = dialogView.findViewById<Button>(R.id.btnTermsOfUse)

        val currentLanguage = getCurrentLanguage()
        spLanguage.setSelection(if (currentLanguage == "en") 1 else 0)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val selectedLanguage = if (spLanguage.selectedItemPosition == 1) "en" else "ru"
            saveLanguage(selectedLanguage)
            recreate()
            dialog.dismiss()
        }

        btnTermsOfUse.setOnClickListener {
            openTermsOfUse()
        }

        dialog.show()
    }

    private fun openTermsOfUse() {
        val intent = Intent(this, TermsOfUseActivity::class.java)
        startActivity(intent)
    }

    private fun saveLanguage(language: String) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        sharedPreferences.edit().putString("language", language).apply()
    }

    private fun getCurrentLanguage(): String {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return sharedPreferences.getString("language", "ru") ?: "ru"
    }

    private fun openRecipeDetails(recipe: Recipe) {
        // Увеличиваем счётчик просмотров и сохраняем
        val index = recipes.indexOfFirst { it.id == recipe.id }
        val updatedRecipe = if (index >= 0) {
            val incremented = recipes[index].copy(viewCount = recipes[index].viewCount + 1)
            recipes[index] = incremented
            recipeRepository.saveRecipes(recipes)
            recipesAdapter.submitList(recipes.toList())
            incremented
        } else {
            recipe.copy(viewCount = recipe.viewCount + 1)
        }

        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("RECIPE", updatedRecipe)
        }
        startActivity(intent)
    }

    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(this, AddRecipeActivity::class.java).apply {
            putExtra(AddRecipeActivity.EXTRA_RECIPE_TO_EDIT, recipe)
        }
        startActivityForResult(intent, REQUEST_ADD_EDIT_RECIPE)
    }

    private fun confirmDeleteRecipe(recipe: Recipe) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_recipe_title))
            .setMessage(getString(R.string.delete_recipe_message, recipe.title))
            .setPositiveButton(R.string.delete) { _, _ -> deleteRecipe(recipe) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRecipe(recipe: Recipe) {
        val removed = recipes.removeIf { it.id == recipe.id }
        if (removed) {
            recipesAdapter.submitList(recipes.toList())
            recipeRepository.saveRecipes(recipes)
            Log.d("RecipesApp Delete", "Рецепт удалён: $recipe")
        }
    }

    private fun Intent.getRecipeFromResult(key: String): Recipe? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(key, Recipe::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializableExtra(key) as? Recipe
        }

    companion object {
        private const val REQUEST_ADD_EDIT_RECIPE = 1
    }
}
