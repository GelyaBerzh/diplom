package com.example.recepiesapp

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

data class InitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

fun View.applySystemBarsPadding(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    includeIme: Boolean = true,
    onInsetsChanged: ((WindowInsetsCompat) -> Unit)? = null
) {
    val initialPadding = InitialPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottomInset = when {
            applyBottom && includeIme -> max(systemBars.bottom, imeInsets.bottom)
            includeIme -> imeInsets.bottom
            else -> systemBars.bottom
        }
        view.setPadding(
            initialPadding.left + if (applyLeft) systemBars.left else 0,
            initialPadding.top + if (applyTop) systemBars.top else 0,
            initialPadding.right + if (applyRight) systemBars.right else 0,
            initialPadding.bottom + if (applyBottom) bottomInset else 0
        )
        onInsetsChanged?.invoke(insets)
        insets
    }

    ViewCompat.requestApplyInsets(this)
}

