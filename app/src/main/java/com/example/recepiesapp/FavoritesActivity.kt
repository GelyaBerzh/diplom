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
import com.example.recepiesapp.databinding.ActivityFavoritesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recipeRepository: RecipeRepository
    private var recipes: MutableList<Recipe> = mutableListOf()
    private var currentCategoryFilter: DishType? = null
    private var currentCookingMethodFilter: CookingMethod? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
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
        setupSettings()
        setupBottomPanel()
        setupBack()

        lifecycleScope.launch {
            reload()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            reload()
        }
    }

    private suspend fun reload() {
        val loaded = recipeRepository.loadFavoriteRecipes()
        recipes = loaded
        recipesAdapter.submitList(recipes.toList())
        val currentQuery = binding.searchView.query?.toString() ?: ""
        searchRecipes(currentQuery)
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

    private fun setupBottomPanel() {
        binding.btnAllFavorites.setOnClickListener {
            currentCategoryFilter = null
            currentCookingMethodFilter = null
            binding.searchView.setQuery("", false)
            searchRecipes("")
        }
        binding.btnDishSections.setOnClickListener {
            showFiltersDialog()
        }
    }

    private fun setupBack() {
        binding.btnBack.setOnClickListener { finish() }
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

    private fun searchRecipes(query: String) {
        val category = currentCategoryFilter
        val method = currentCookingMethodFilter
        val filteredRecipes = recipes.filter { recipe ->
            val matchesQuery =
                query.isEmpty() ||
                        recipe.title.contains(query, ignoreCase = true) ||
                        recipe.ingredients.any { it.contains(query, ignoreCase = true) } ||
                        recipe.tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = category == null || run {
                val ids = recipe.dishType
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()
                ids.contains(category.id)
            }

            val matchesMethod = method == null || run {
                val ids = recipe.cookingMethod
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()
                ids.contains(method.id)
            }

            matchesQuery && matchesCategory && matchesMethod
        }
        recipesAdapter.submitList(filteredRecipes)
    }

    private fun toggleFavorite(recipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index < 0) return
        val newValue = !recipes[index].isFavorite
        recipes[index] = recipes[index].copy(isFavorite = newValue)

        if (!newValue) {
            recipes.removeIf { it.id == recipe.id }
        }
        val currentQuery = binding.searchView.query?.toString() ?: ""
        searchRecipes(currentQuery)

        lifecycleScope.launch(Dispatchers.IO) {
            recipeRepository.setFavorite(recipe.id, newValue)
        }
    }

    private fun openRecipeDetails(recipe: Recipe) {
        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("RECIPE", recipe)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
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
            lifecycleScope.launch(Dispatchers.IO) {
                recipeRepository.deleteRecipe(recipe.id)
            }
            Log.d("Favorites Delete", "Рецепт удалён: $recipe")
        }
    }

    fun getDescriptionPreview(description: String): String {
        return if (description.length > 200) {
            description.substring(0, 200) + "..."
        } else {
            description
        }
    }

    @Suppress("DEPRECATION")
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

    private fun addRecipe(newRecipe: Recipe) {
        if (!newRecipe.isFavorite) return
        recipes.add(newRecipe)
        recipesAdapter.submitList(recipes.toList())
        lifecycleScope.launch(Dispatchers.IO) {
            recipeRepository.upsertRecipe(newRecipe)
        }
    }

    private fun updateRecipe(updatedRecipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == updatedRecipe.id }
        if (updatedRecipe.isFavorite) {
            if (index >= 0) {
                recipes[index] = updatedRecipe
            } else {
                recipes.add(updatedRecipe)
            }
        } else {
            if (index >= 0) recipes.removeAt(index)
        }
        recipesAdapter.submitList(recipes.toList())
        lifecycleScope.launch(Dispatchers.IO) {
            recipeRepository.upsertRecipe(updatedRecipe)
        }
        val currentQuery = binding.searchView.query?.toString() ?: ""
        searchRecipes(currentQuery)
    }

    private fun Intent.getRecipeFromResult(key: String): Recipe? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(key, Recipe::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializableExtra(key) as? Recipe
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

    companion object {
        private const val REQUEST_ADD_EDIT_RECIPE = 1
    }
}

