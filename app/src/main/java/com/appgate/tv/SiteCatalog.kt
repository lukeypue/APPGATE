package com.appgate.tv

/**
 * One entry per tile. Per-site tuning lives here so fixes are one-line changes:
 *  - mobileUa: request a narrow mobile layout instead of the landscape desktop site
 *  - feedMode: Channel Up/Down snaps between videos/posts instead of page scrolling
 *  - cleanupCss: injected CSS that hides "get the app" nags, banners, sidebars
 */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val color: Long,          // tile color (ARGB)
    val mobileUa: Boolean = true,
    val feedMode: Boolean = false,
    val cleanupCss: String = ""
)

object SiteCatalog {

    // Generic store-install nags only. Keep selectors narrow so normal site
    // controls do not disappear when a website changes its class names.
    const val COMMON_CSS = """
        [class*="download-app"], [class*="app-banner"], [class*="AppBanner"],
        [class*="open-app"], [class*="OpenApp"], [class*="smart-banner"],
        [id*="smart-banner"], [data-e2e="download-app"], [data-e2e="download-guide"]
        { display: none !important; }
        html { overscroll-behavior: none !important; }
    """

    val preloaded: List<Site> = listOf(
        Site(
            id = "tiktok", name = "TikTok", url = "https://www.tiktok.com",
            color = 0xFF010101, mobileUa = false, feedMode = true,
            cleanupCss = """
                [data-e2e="download-guide"], [class*="DivDownload"],
                [class*="GuideContainer"],
                [class*="BottomBanner"] { display:none !important; }
            """
        ),
        Site(
            id = "instagram", name = "Instagram", url = "https://www.instagram.com",
            color = 0xFFE1306C, mobileUa = false, feedMode = true
        ),
        Site(
            id = "reddit", name = "Reddit", url = "https://www.reddit.com",
            color = 0xFFFF4500, mobileUa = true, feedMode = true,
            cleanupCss = "shreddit-async-loader[bundlename*=\"app_selector\"] { display:none !important; }"
        ),
        Site(id = "youtube", name = "YouTube", url = "https://www.youtube.com/tv",
            color = 0xFFFF0000, mobileUa = false),
        Site(id = "snapchat", name = "Snapchat", url = "https://web.snapchat.com",
            color = 0xFFFFFC00, mobileUa = false),
        Site(id = "pinterest", name = "Pinterest", url = "https://www.pinterest.com",
            color = 0xFFBD081C, mobileUa = false, feedMode = true),
        Site(id = "x", name = "X", url = "https://x.com",
            color = 0xFF14171A, mobileUa = false, feedMode = true),
        Site(id = "facebook", name = "Facebook", url = "https://m.facebook.com",
            color = 0xFF1877F2, mobileUa = true, feedMode = true),
        Site(id = "discord", name = "Discord", url = "https://discord.com/app",
            color = 0xFF5865F2, mobileUa = false),
        Site(id = "twitch", name = "Twitch", url = "https://www.twitch.tv",
            color = 0xFF9146FF, mobileUa = true),
        Site(id = "geforce", name = "GeForce NOW", url = "https://play.geforcenow.com",
            color = 0xFF76B900, mobileUa = false),
        Site(id = "lemon8", name = "Lemon8", url = "https://www.lemon8-app.com",
            color = 0xFFFFD400, mobileUa = false, feedMode = true),
        Site(id = "threads", name = "Threads", url = "https://www.threads.net",
            color = 0xFF101010, mobileUa = false, feedMode = true),
        Site(id = "bluesky", name = "Bluesky", url = "https://bsky.app",
            color = 0xFF1683FE, mobileUa = false, feedMode = true)
    )

    fun byId(id: String): Site? = preloaded.find { it.id == id }
}
