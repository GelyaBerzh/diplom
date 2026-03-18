package com.example.recepiesapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.recepiesapp.databinding.ItemRecipeBinding

class RecipesAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val getDescriptionPreview: (String) -> String,
    private val onEditClick: (Recipe) -> Unit,
    private val onDeleteClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipesAdapter.RecipeViewHolder>(DiffCallback()) {

    class DiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem == newItem
    }

    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val getDescriptionPreview: (String) -> String,
        private val onEditClick: (Recipe) -> Unit,
        private val onDeleteClick: (Recipe) -> Unit,
        private val onFavoriteClick: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val placeholderPadding =
            binding.root.context.resources.getDimensionPixelSize(R.dimen.recipe_image_placeholder_padding)

        fun bind(recipe: Recipe) {
            binding.tvTitle.text = recipe.title
            binding.tvDescription.text = getDescriptionPreview(recipe.description)
            binding.tvServings.text =
                binding.root.context.getString(R.string.servings_text, recipe.servings)
            binding.tvViews.text =
                binding.root.context.getString(R.string.views_text, recipe.viewCount)
            binding.tvTags.text =
                recipe.tags.takeIf { it.isNotEmpty() }?.joinToString("  ") ?: ""
            binding.tvTags.isVisible = binding.tvTags.text.isNotBlank()

            binding.btnFavorite.apply {
                setImageResource(if (recipe.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
                setOnClickListener { onFavoriteClick(recipe) }
            }
            binding.btnEdit.setOnClickListener { onEditClick(recipe) }
            binding.btnDelete.setOnClickListener { onDeleteClick(recipe) }

            binding.ivRecipeImage.apply {
                if (recipe.imageUri.isNullOrBlank()) {
                    setImageResource(R.drawable.ic_camera)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(
                        placeholderPadding,
                        placeholderPadding,
                        placeholderPadding,
                        placeholderPadding
                    )
                } else {
                    setImageURI(Uri.parse(recipe.imageUri))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(0, 0, 0, 0)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding, getDescriptionPreview, onEditClick, onDeleteClick, onFavoriteClick)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = getItem(position)
        holder.itemView.setOnClickListener { onItemClick(recipe) }
        holder.bind(recipe)
    }
}