package com.example.recepiesapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object RecipeImageUtils {

    private const val IMAGES_DIR = "recipe_images"
    private const val MAX_IMAGE_SIZE_PX = 1024
    private const val JPEG_QUALITY = 85

    fun getImagesDir(context: Context): File =
        File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }

    fun getRecipeImageFile(context: Context, recipeId: Int): File =
        File(getImagesDir(context), "recipe_$recipeId.jpg")

    fun copyImageFromUri(context: Context, sourceUri: Uri): String? {
        val destFile = File(getImagesDir(context), "pick_${System.currentTimeMillis()}.jpg")
        return if (compressUriToFile(context, sourceUri, destFile)) destFile.absolutePath else null
    }

    fun finalizeRecipeImage(
        context: Context,
        selectedPath: String?,
        recipeId: Int,
        previousPath: String?
    ): String? {
        if (selectedPath.isNullOrBlank()) {
            deleteImageIfOwned(context, previousPath)
            return null
        }

        val destFile = getRecipeImageFile(context, recipeId)
        val selectedFile = File(selectedPath)

        if (selectedFile.absolutePath == destFile.absolutePath && destFile.exists()) {
            if (!previousPath.isNullOrBlank() && previousPath != destFile.absolutePath) {
                deleteImageIfOwned(context, previousPath)
            }
            return destFile.absolutePath
        }

        val saved = when {
            selectedFile.absolutePath == destFile.absolutePath -> destFile.exists()
            else -> compressFileToFile(selectedFile, destFile)
        }
        if (!saved) return previousPath?.takeIf { File(it).exists() }

        if (!previousPath.isNullOrBlank() && previousPath != destFile.absolutePath) {
            deleteImageIfOwned(context, previousPath)
        }
        if (selectedFile.absolutePath != destFile.absolutePath) {
            deleteImageIfOwned(context, selectedPath)
        }
        return destFile.absolutePath
    }

    fun deleteImageIfOwned(context: Context, imagePath: String?) {
        if (imagePath.isNullOrBlank()) return
        val file = File(imagePath)
        if (!file.exists()) return
        val imagesDir = getImagesDir(context).canonicalFile
        if (file.canonicalFile.path.startsWith(imagesDir.path)) {
            file.delete()
        }
    }

    fun setRecipeImage(
        imageView: ImageView,
        imagePath: String?,
        placeholderPadding: Int
    ) {
        val file = imagePath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            imageView.setImageResource(R.drawable.ic_camera)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setPadding(
                placeholderPadding,
                placeholderPadding,
                placeholderPadding,
                placeholderPadding
            )
            return
        }

        imageView.setImageURI(Uri.fromFile(file))
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setPadding(0, 0, 0, 0)
    }

    private fun compressUriToFile(context: Context, sourceUri: Uri, destFile: File): Boolean {
        val original = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return false
        return compressBitmapToFile(original, destFile)
    }

    private fun compressFileToFile(sourceFile: File, destFile: File): Boolean {
        if (!sourceFile.exists()) return false
        val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false
        return compressBitmapToFile(original, destFile)
    }

    private fun compressBitmapToFile(original: Bitmap, destFile: File): Boolean {
        val scaled = scaleDown(original, MAX_IMAGE_SIZE_PX)
        if (scaled !== original) {
            original.recycle()
        }

        return try {
            FileOutputStream(destFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        } catch (_: Exception) {
            false
        } finally {
            scaled.recycle()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSizePx: Int): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= maxSizePx) return bitmap

        val scale = maxSizePx.toFloat() / largestSide
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
