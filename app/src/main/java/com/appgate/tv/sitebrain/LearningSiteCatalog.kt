package com.appgate.tv.sitebrain

data class LearningSite(
    val key: String,
    val name: String,
    val startUrl: String,
    val expectedHost: String,
    val loginUrl: String? = null
) {
    fun acceptsHost(actualHost: String): Boolean {
        val host = actualHost.lowercase().removeSuffix(".").removePrefix("www.")
        val expected = expectedHost.lowercase().removePrefix("www.")
        return host == expected || host.endsWith(".$expected")
    }
}

object LearningSiteCatalog {
    /**
     * Dedicated-brain training catalog.
     *
     * Keep this small: these are sites where generic web search is not enough
     * for the deep-search behavior we need (dynamic/session UI, site-specific
     * filters, or listing-detail checks). Public retailer/search-engine sites
     * are intentionally NOT trained here; a general web/search layer can cover
     * those later without spending Site Brain time relearning them.
     *
     * Facebook Marketplace is also available to the Gecko Mini Brain, but it belongs in the
     * normal four-site learning rotation so it is never silently omitted from overnight runs.
     */
    fun defaultSites(): List<LearningSite> = listOf(
        LearningSite(
            "ksl_classifieds",
            "KSL Classifieds",
            "https://classifieds.ksl.com/",
            "ksl.com",
            "https://www.ksl.com/login"
        ),
        LearningSite(
            "ksl_cars",
            "KSL Cars",
            "https://cars.ksl.com/",
            "ksl.com",
            "https://www.ksl.com/login"
        ),
        LearningSite(
            "facebook_marketplace",
            "Facebook Marketplace",
            "https://www.facebook.com/marketplace/",
            "facebook.com",
            "https://www.facebook.com/login/"
        ),
        LearningSite(
            "offerup",
            "OfferUp",
            "https://offerup.com/",
            "offerup.com",
            "https://offerup.com/accounts/login/"
        ),
        LearningSite(
            "tiktok_shop",
            "TikTok Shop",
            "https://www.tiktok.com/shop",
            "tiktok.com",
            "https://www.tiktok.com/login"
        ),
        LearningSite(
            "instagram_shop",
            "Instagram Shop",
            "https://www.instagram.com/",
            "instagram.com",
            "https://www.instagram.com/accounts/login/"
        )
    )
}
