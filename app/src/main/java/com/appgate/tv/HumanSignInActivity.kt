package com.appgate.tv

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class HumanSignInActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var status: TextView
    private var targetHost: String = ""
    private var targetName: String = "Site"
    private var canGoBack = false
    private var lastUrl: String = ""
    private var returnedToTarget = false

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
            text = "Human sign-in for $targetName\nComplete the website login or verification here. This secure sign-in window will return you to Site Brain when the site finishes."
            setPadding(4, 4, 4, 8)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "DONE — RETURN TO LEARNING"
            setOnClickListener {
                if (returnedToTarget) {
                    geckoView.releaseSession()
                    AuthenticatedGeckoSessionStore.retain(targetHost, session)
                    setResult(RESULT_OK, Intent().putExtra("geckoSessionRetained", true))
                    finish()
                } else {
                    status.text = "Sign-in has not returned to $targetName yet. Finish Google/2-step verification first."
                }
            }
        })

        geckoView = GeckoView(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build()
        ).apply {
            contentDelegate = object : GeckoSession.ContentDelegate {}
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                    canGoBack = value
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    lastUrl = url.orEmpty()
                    val host = runCatching { Uri.parse(lastUrl).host.orEmpty() }.getOrDefault("")
                    if (LearningNavigationPolicy.shouldAllow(targetHost, host, false)) {
                        returnedToTarget = true
                        status.text = "Signed in to $targetName. You can keep using this authenticated browser session."
                    }
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny>? {
                    val rawUrl = request.uri
                    lastUrl = rawUrl
                    val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull()
                    val scheme = parsed?.scheme.orEmpty().lowercase()
                    if (scheme != "https" && scheme != "http") {
                        // OAuth providers can finish with an app/deep-link callback. A hard deny
                        // leaves Gecko on a blank document. Hand the callback back to Android
                        // instead, while still refusing arbitrary navigation inside the auth view.
                        val handled = runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, parsed))
                            true
                        }.getOrDefault(false)
                        status.text = if (handled) {
                            "Finishing $targetName sign-in…"
                        } else {
                            "Sign-in reached a callback this browser cannot open ($scheme). Tap DONE to return, or try email sign-in."
                        }
                        return GeckoResult.deny()
                    }
                    val host = parsed?.host.orEmpty()
                    val allowed = LearningNavigationPolicy.shouldAllow(targetHost, host, true) || isGoogleAuthSupportHost(host)
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

        // Reuse the shared Gecko runtime so authenticated state survives between human sign-in
        // and Gecko-powered Site Brain sessions. Do not automatically finish on target return;
        // keeping this session alive avoids throwing away the very login state we just created.
        if (authUrl.startsWith("https://")) session.loadUri(authUrl) else finish()
    }

    private fun isGoogleAuthSupportHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "gstatic.com" || normalized.endsWith(".gstatic.com") ||
            normalized == "googleusercontent.com" || normalized.endsWith(".googleusercontent.com") ||
            normalized == "googleapis.com" || normalized.endsWith(".googleapis.com")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::session.isInitialized && canGoBack) session.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::geckoView.isInitialized) runCatching { geckoView.releaseSession() }
        // Keep authenticated Gecko state in the shared runtime. The session itself is closed
        // because the learning screen currently uses a different renderer.
        if (::session.isInitialized && !AuthenticatedGeckoSessionStore.hasSessionFor(targetHost)) runCatching { session.close() }
        super.onDestroy()
    }
}
