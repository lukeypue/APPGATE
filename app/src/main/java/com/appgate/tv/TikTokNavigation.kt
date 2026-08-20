package com.appgate.tv

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/**
 * Small, testable policy for the TikTok-only AppGate build.
 *
 * The Fire TV test device proved that normal TikTok HTTPS pages such as
 * /tag/<topic> and /@<profile> work, while TikTok's native-app handoffs
 * (snssdk / intent / OneLink) strand a TV browser on a blank page.
 */
object TikTokNavigation {
    const val HOME_URL = "https://www.tiktok.com/"

    fun destinationForSearch(raw: String): String? {
        val cleaned = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
        if (cleaned.isBlank()) return null

        return if (cleaned.startsWith("@")) {
            val username = cleaned.drop(1)
                .filter { it.isLetterOrDigit() || it == '_' || it == '.' }
            username.takeIf { it.isNotBlank() }?.let { "https://www.tiktok.com/@$it" }
        } else {
            val tag = cleaned
                .removePrefix("#")
                .lowercase(Locale.ROOT)
                .filter { it.isLetterOrDigit() || it == '_' }
            tag.takeIf { it.isNotBlank() }?.let { "https://www.tiktok.com/tag/$it" }
        }
    }

    fun shouldBlock(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase() ?: return true

        // AppGate is a web experience only. Never hand control to a native app,
        // app store, Android intent, or other custom scheme.
        if (scheme != "https") return true

        val host = uri.host?.lowercase() ?: return true
        if (host == "snssdk1233.onelink.me" || host.endsWith(".onelink.me")) return true

        return false
    }
}
