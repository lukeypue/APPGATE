package com.appgate.tv.sitebrain

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object WebNavigationDecision {
    const val KEEP_IN_WEBVIEW = "KEEP_IN_WEBVIEW"
    const val LOAD_WEB_FALLBACK = "LOAD_WEB_FALLBACK"
    const val BLOCK_EXTERNAL_APP = "BLOCK_EXTERNAL_APP"
}

data class NavigationDecision(val kind: String, val url: String?)

object WebNavigationPolicy {
    fun decide(rawUrl: String, currentHost: String?): NavigationDecision {
        val url = rawUrl.trim()
        if (url.startsWith("https://", true) || url.startsWith("http://", true)) {
            return NavigationDecision(WebNavigationDecision.KEEP_IN_WEBVIEW, url)
        }
        if (url.startsWith("intent://", true)) {
            val marker = "S.browser_fallback_url="
            val start = url.indexOf(marker)
            if (start >= 0) {
                val encoded = url.substring(start + marker.length).substringBefore(';')
                val decoded = runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrNull()
                if (decoded != null && (decoded.startsWith("https://", true) || decoded.startsWith("http://", true))) {
                    return NavigationDecision(WebNavigationDecision.LOAD_WEB_FALLBACK, decoded)
                }
            }
        }
        return NavigationDecision(WebNavigationDecision.BLOCK_EXTERNAL_APP, null)
    }
}
