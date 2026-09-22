package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCatalogTest {
    @Test
    fun dedicatedBrainSearchUsesOnlyHardSiteSources() {
        val all = SearchCatalog.all()
        val keys = all.map { it.key }.toSet()
        assertEquals(setOf("ksl_classifieds", "ksl_cars", "offerup"), keys)
        assertTrue(all.any { it.key == "ksl_cars" && "vehicles" in it.categories })
        assertTrue(all.any { it.key == "offerup" && "vehicles" in it.categories })
    }

    @Test
    fun facebookMarketplaceIsIsolatedFromLegacySearchRunner() {
        assertFalse(SearchCatalog.all().any { it.key == "facebook_marketplace" })
    }

    @Test
    fun publicRetailersAndSearchEnginesAreNotDedicatedBrainSources() {
        val keys = SearchCatalog.all().map { it.key }.toSet()
        assertFalse("homedepot" in keys)
        assertFalse("walmart" in keys)
        assertFalse("google" in keys)
        assertFalse("bing" in keys)
    }
}
