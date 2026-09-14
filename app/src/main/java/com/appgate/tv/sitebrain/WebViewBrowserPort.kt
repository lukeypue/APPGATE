package com.appgate.tv.sitebrain

import android.net.Uri
import android.webkit.WebView
import com.appgate.sitebrain.core.*

/**
 * Android adapter for the portable Site Brain core.
 *
 * B1 deliberately keeps observation compact and synchronous. Existing production Site Brain
 * observation continues to run while later versions migrate richer snapshots behind BrowserPort.
 */
class WebViewBrowserPort(
    private val webView: WebView,
    private val authenticatedHostProvider: (String) -> Boolean = { false }
) : BrowserPort {

    override fun navigate(url: String): NavResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return NavResult(false, webView.url, "Only http/https navigation is allowed")
        }
        webView.loadUrl(url)
        return NavResult(true, url)
    }

    override fun observe(): Observation {
        val url = webView.url.orEmpty()
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        return Observation(
            url = url,
            host = parsed?.host.orEmpty(),
            title = webView.title.orEmpty(),
            pageType = "UNKNOWN",
            routeSignature = parsed?.path.orEmpty(),
            challengeDetected = false,
            loginDetected = false
        )
    }

    override fun act(action: Action): ActResult {
        return when (action) {
            is Action.Navigate -> {
                val result = navigate(action.url)
                ActResult(result.accepted, result.message)
            }
            is Action.Click -> runJsAction(
                "document.querySelector('[data-sitebrain-ref=\"${jsEscape(action.ref)}\"]')?.click();"
            )
            is Action.Type -> runJsAction(
                "var e=document.querySelector('[data-sitebrain-ref=\"${jsEscape(action.ref)}\"]');" +
                    "if(e){e.focus();e.value='${jsEscape(action.text)}';e.dispatchEvent(new Event('input',{bubbles:true}));}"
            )
            is Action.Select -> runJsAction(
                "var e=document.querySelector('[data-sitebrain-ref=\"${jsEscape(action.ref)}\"]');" +
                    "if(e){e.value='${jsEscape(action.value)}';e.dispatchEvent(new Event('change',{bubbles:true}));}"
            )
            is Action.Scroll -> {
                webView.scrollBy(action.dx, action.dy)
                ActResult(true)
            }
            Action.Back -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    ActResult(true)
                } else ActResult(false, "No browser history")
            }
            is Action.Wait -> ActResult(true, "Host wait is handled by the caller in B1")
        }
    }

    override fun screenshot(): ByteArray? = null

    override fun isAuthenticated(site: String): Boolean = authenticatedHostProvider(site)

    private fun runJsAction(script: String): ActResult {
        webView.evaluateJavascript("(function(){${script}return true;})();", null)
        return ActResult(true)
    }

    private fun jsEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
