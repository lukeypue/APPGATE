package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSiteCatalogTest {
    @Test
    fun trainingCatalogContainsAllPriorityMarketplaces() {
        val sites = LearningSiteCatalog.defaultSites()
        val keys = sites.map { it.key }.toSet()
        assertEquals(setOf("ksl_classifieds", "ksl_cars", "facebook_marketplace", "offerup", "tiktok_shop", "instagram_shop"), keys)
        assertTrue(sites.first { it.key == "ksl_cars" }.startUrl.startsWith("https://cars.ksl.com"))
        assertTrue(sites.first { it.key == "facebook_marketplace" }.startUrl.contains("facebook.com/marketplace"))\n        assertTrue(sites.first { it.key == "tiktok_shop" }.startUrl.contains("tiktok.com/shop"))\n        assertTrue(sites.first { it.key == "instagram_shop" }.startUrl.contains("instagram.com"))
        assertFalse(sites.any { it.startUrl.contains("{q}") })
        assertFalse(sites.any { it.startUrl.contains("google.com/search?q=site") })
    }

    @Test
    fun trainingSiteAcceptsExpectedHostAndSubdomainsOnly() {
        val site = LearningSite("fb", "Facebook", "https://www.facebook.com/marketplace/", "facebook.com")
        assertTrue(site.acceptsHost("www.facebook.com"))
        assertTrue(site.acceptsHost("m.facebook.com"))
        assertFalse(site.acceptsHost("facebook.com.example.org"))
    }
}
