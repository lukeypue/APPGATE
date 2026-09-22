package com.appgate.tv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class ListingActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Original Listing"
        val url = intent.getStringExtra("url").orEmpty()

        geckoView = GeckoView(this).apply {
            setViewBackend(GeckoView.BACKEND_SURFACE_VIEW)
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
        setContentView(geckoView)

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
