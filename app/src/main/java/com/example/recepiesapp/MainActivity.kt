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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recepiesapp.databinding.ActivityMainBinding
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recipeRepository: RecipeRepository
    private var recipes: MutableList<Recipe> = mutableListOf()
    private var currentCategoryFilter: DishType? = null
    private var currentCookingMethodFilter: CookingMethod? = null
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppLanguage(getCurrentLanguage())
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        recipeRepository = RecipeRepository(this)
        recipesAdapter = RecipesAdapter(
            onItemClick = { openRecipeDetails(it) },
            getDescriptionPreview = ::getDescriptionPreview,
            onEditClick = { editRecipe(it) },
            onDeleteClick = { confirmDeleteRecipe(it) },
            onFavoriteClick = { toggleFavorite(it) }
        )

        setupRecyclerView()
        setupSearch()
        setupAddButton()
        setupSettings()
        setupBottomPanel()

        // Загрузка рецептов из Room (c миграцией из JSON при первом запуске)
        lifecycleScope.launch {
            val loaded = recipeRepository.loadRecipes()
            recipes = loaded
            searchRecipes("")
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val loaded = recipeRepository.loadRecipes()
            recipes = loaded
            val currentQuery = binding.searchView.query?.toString() ?: ""
            searchRecipes(currentQuery)
        }
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
                runSearchWithDebounce(newText ?: "")
                return true
            }
        })
        binding.searchView.clearFocus()
    }

    private fun runSearchWithDebounce(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchRecipes(query)
        }
    }

    private fun searchRecipes(query: String) {
        val category = currentCategoryFilter
        val method = currentCookingMethodFilter
        val filteredRecipes = recipes.filter { recipe ->
            //Поиск по тексту
            val matchesQuery =
                query.isEmpty() ||
                        recipe.title.contains(query, ignoreCase = true) ||
                        recipe.ingredients.any { it.contains(query, ignoreCase = true) } ||
                        recipe.tags.any { it.contains(query, ignoreCase = true) } ||
                        recipe.cookingTimeMinutes.toString().contains(query) ||
                        recipe.caloriesPerServing.toInt().toString().contains(query)
                //фильтр по категории блюд
            val matchesCategory = category == null || run {
                val ids = recipe.dishType
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()
                ids.contains(category.id)
            }
                //фильтр по способу приготовления
            val matchesMethod = method == null || run {
                val ids = recipe.cookingMethod
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()
                ids.contains(method.id)
            }

            matchesQuery && matchesCategory && matchesMethod
        }
        recipesAdapter.submitList(filteredRecipes.sortedByTitle())
    }

    private fun setupBottomPanel() {
        binding.btnAllRecipes.setOnClickListener {
            currentCategoryFilter = null
            currentCookingMethodFilter = null
            binding.searchView.setQuery("", false)
            searchRecipes("")
        }

        binding.btnDishSections.setOnClickListener {
            showFiltersDialog()
        }

        binding.btnFavorites.setOnClickListener {
            openFavorites()
        }


    }

    private fun showFiltersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filters, null)
        val spSection = dialogView.findViewById<Spinner>(R.id.spDishSection)
        val spMethod = dialogView.findViewById<Spinner>(R.id.spCookingMethod)

        val sections = listOf<String?>(null) + DishType.values().map { it.id }
        val sectionLabels = listOf(getString(R.string.sections_all)) +
                DishType.values().map { getString(it.titleRes) }

        val methods = listOf<String?>(null) + CookingMethod.values().map { it.id }
        val methodLabels = listOf(getString(R.string.filter_all_methods)) +
                CookingMethod.values().map { getString(it.titleRes) }

        spSection.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sectionLabels
        )
        spMethod.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            methodLabels
        )

        val currentSectionIndex =
            sections.indexOf(currentCategoryFilter?.id).takeIf { it >= 0 } ?: 0
        val currentMethodIndex =
            methods.indexOf(currentCookingMethodFilter?.id).takeIf { it >= 0 } ?: 0
        spSection.setSelection(currentSectionIndex)
        spMethod.setSelection(currentMethodIndex)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filters_title))
            .setView(dialogView)
            .setPositiveButton(R.string.apply) { dialog, _ ->
                val selectedSectionId = sections[spSection.selectedItemPosition]
                val selectedMethodId = methods[spMethod.selectedItemPosition]

                currentCategoryFilter = selectedSectionId?.let { DishType.fromId(it) }
                currentCookingMethodFilter = selectedMethodId?.let { CookingMethod.fromId(it) }

                val currentQuery = binding.searchView.query?.toString() ?: ""
                searchRecipes(currentQuery)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
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
        recipes.sortByTitle()
        val currentQuery = binding.searchView.query?.toString() ?: ""
        searchRecipes(currentQuery)
        lifecycleScope.launch(Dispatchers.IO) {
            recipeRepository.upsertRecipe(newRecipe)
        }
        Log.d("RecipesApp Add", "Рецепт добавлен и сохранён: $newRecipe")
    }

    private fun updateRecipe(updatedRecipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == updatedRecipe.id }
        if (index >= 0) {
            recipes[index] = updatedRecipe
            recipes.sortByTitle()
            val currentQuery = binding.searchView.query?.toString() ?: ""
            searchRecipes(currentQuery)
            lifecycleScope.launch(Dispatchers.IO) {
                recipeRepository.upsertRecipe(updatedRecipe)
            }
            Log.d("RecipesApp Update", "Рецепт обновлён: $updatedRecipe")
        } else {
            addRecipe(updatedRecipe)
        }
    }

    fun getDescriptionPreview(description: String): String {
        return if (description.length > 50) {
            description.substring(0, 50) + "..."
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
            val currentQuery = binding.searchView.query?.toString() ?: ""
            searchRecipes(currentQuery)
            lifecycleScope.launch(Dispatchers.IO) {
                recipeRepository.upsertRecipe(incremented)
            }
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
            val currentQuery = binding.searchView.query?.toString() ?: ""
            searchRecipes(currentQuery)
            lifecycleScope.launch(Dispatchers.IO) {
                recipeRepository.deleteRecipe(recipe.id)
            }
            Log.d("RecipesApp Delete", "Рецепт удалён: $recipe")
        }
    }

    private fun toggleFavorite(recipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index < 0) return
        val newValue = !recipes[index].isFavorite
        recipes[index] = recipes[index].copy(isFavorite = newValue)

        val currentQuery = binding.searchView.query?.toString() ?: ""
        searchRecipes(currentQuery)

        lifecycleScope.launch(Dispatchers.IO) {
            recipeRepository.setFavorite(recipe.id, newValue)
        }
    }

    private fun openFavorites() {
        val intent = Intent(this, FavoritesActivity::class.java)
        startActivity(intent)
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
        private const val SEARCH_DEBOUNCE_MS = 180L
    }
}
