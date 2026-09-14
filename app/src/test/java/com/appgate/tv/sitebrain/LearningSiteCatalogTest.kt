package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSiteCatalogTest {
    @Test
    fun trainingCatalogUsesDirectSiteRootsInsteadOfSearchQueries() {
        val sites = LearningSiteCatalog.defaultSites()
        assertTrue(sites.size >= 20)
        assertEquals("facebook.com", sites.first { it.key == "facebook_marketplace" }.expectedHost)
        assertTrue(sites.first { it.key == "ksl_cars" }.startUrl.startsWith("https://cars.ksl.com"))
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
