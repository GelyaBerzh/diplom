package com.example.recepiesapp

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class TermsOfUseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_terms_of_use)
        findViewById<View>(R.id.termsRoot).applySystemBarsPadding()

        val webView = findViewById<WebView>(R.id.webViewTerms)
        webView.settings.javaScriptEnabled = true

        // Загрузка HTML-файла из ресурсов
        val htmlFile = resources.openRawResource(R.raw.terms_of_use).bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(null, htmlFile, "text/html", "UTF-8", null)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}