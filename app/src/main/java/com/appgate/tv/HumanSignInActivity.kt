package com.appgate.tv

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HumanSignInActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var targetHost: String = ""
    private var targetName: String = "Site"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Human Sign-in"
        val authUrl = intent.getStringExtra("authUrl").orEmpty()
        targetHost = intent.getStringExtra("targetHost").orEmpty()
        targetName = intent.getStringExtra("targetName").orEmpty().ifBlank { "Site" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 23))
            setPadding(12, 10, 12, 10)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = "Human sign-in for $targetName\nComplete Google/Facebook/Apple or the website login here. Site Brain is paused and will not type credentials for you."
            setPadding(4, 4, 4, 8)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "DONE — RETURN TO LEARNING"
            setOnClickListener {
                CookieManager.getInstance().flush()
                setResult(RESULT_OK)
                finish()
            }
        })

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    return decide(request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    return decide(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    val host = runCatching { Uri.parse(url.orEmpty()).host.orEmpty() }.getOrDefault("")
                    if (LearningNavigationPolicy.shouldAllow(targetHost, host, false)) {
                        status.text = "Sign-in returned to $targetName. If the site looks signed in, tap DONE — RETURN TO LEARNING."
                    }
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (authUrl.startsWith("https://")) webView.loadUrl(authUrl) else finish()
    }

    private fun decide(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        if (!rawUrl.startsWith("https://")) return true
        val host = uri.host.orEmpty()
        val allowed = LearningNavigationPolicy.shouldAllow(targetHost, host, true)
        if (!allowed) {
            status.text = "Blocked an unrelated website during sign-in: $host\nUse the website's normal Google/Facebook/Apple login or tap DONE to return."
        }
        return !allowed
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
