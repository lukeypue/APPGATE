package com.appgate.tv

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class ListingActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Connected Site"
        val url = intent.getStringExtra("url").orEmpty()

        geckoView = GeckoView(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        session = GeckoSession().apply {
            contentDelegate = object : GeckoSession.ContentDelegate {}
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                    canGoBack = value
                }
            }
            open(GeckoRuntimeProvider.get(this@ListingActivity))
        }
        geckoView.setSession(session)

        val doneButton = Button(this).apply {
            text = "DONE — RETURN TO AI BROWSER"
            setOnClickListener { finish() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(doneButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(geckoView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        setContentView(root)

        if (url.startsWith("https://")) session.loadUri(url) else finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::session.isInitialized && canGoBack) session.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::geckoView.isInitialized) runCatching { geckoView.releaseSession() }
        if (::session.isInitialized) runCatching { session.close() }
        super.onDestroy()
    }
}
