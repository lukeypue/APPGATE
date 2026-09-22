package com.appgate.tv

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class HumanSignInActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var status: TextView
    private var targetHost: String = ""
    private var targetName: String = "Site"
    private var canGoBack = false

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
            text = "Human sign-in for $targetName\nComplete the website login or verification here. Site Brain will reuse this Gecko browser session."
            setPadding(4, 4, 4, 8)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "DONE — RETURN TO LEARNING"
            setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        })

        geckoView = GeckoView(this).apply {
            setViewBackend(GeckoView.BACKEND_SURFACE_VIEW)
        }
        session = GeckoSession().apply {
            contentDelegate = object : GeckoSession.ContentDelegate {}
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                    canGoBack = value
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>?,
                    hasUserGesture: Boolean
                ) {
                    val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                    if (LearningNavigationPolicy.shouldAllow(targetHost, host, false)) {
                        status.text = "Sign-in returned to $targetName. If the site looks signed in, tap DONE — RETURN TO LEARNING."
                    }
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny>? {
                    val rawUrl = request.uri
                    if (!rawUrl.startsWith("https://")) return GeckoResult.deny()
                    val host = runCatching { Uri.parse(rawUrl).host.orEmpty() }.getOrDefault("")
                    val allowed = LearningNavigationPolicy.shouldAllow(targetHost, host, true)
                    if (!allowed) {
                        status.text = "Blocked an unrelated website during sign-in: $host\nUse the website's normal login or tap DONE to return."
                    }
                    return if (allowed) GeckoResult.allow() else GeckoResult.deny()
                }
            }
            open(GeckoRuntimeProvider.get(this@HumanSignInActivity))
        }
        geckoView.setSession(session)
        root.addView(geckoView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (authUrl.startsWith("https://")) session.loadUri(authUrl) else finish()
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
