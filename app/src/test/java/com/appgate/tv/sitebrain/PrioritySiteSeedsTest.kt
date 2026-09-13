package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritySiteSeedsTest {
    @Test fun kslSeedContainsVehicleSearchAndHardFilters() {
        val state = PrioritySiteSeeds.seedForHost("cars.ksl.com")!!
        assertEquals(ReadinessLevel.LEARNING, state.readiness)
        assertTrue(state.edges.any { it.semanticIntent == "MAX_PRICE_FILTER" })
        assertTrue(state.edges.any { it.semanticIntent == "MAX_MILEAGE_FILTER" })
        assertTrue(state.edges.any { it.actionKind == ActionKind.OPEN_DETAIL })
        assertTrue(state.edges.all { it.successCount == 0 && it.confidence < 0.5 })
    }

    @Test fun marketplaceAndEbaySeedsStayLowConfidenceUntilLiveVerification() {
        val facebook = PrioritySiteSeeds.seedForHost("www.facebook.com")
        val ebay = PrioritySiteSeeds.seedForHost("www.ebay.com")
        assertNotNull(facebook)
        assertNotNull(ebay)
        assertTrue(facebook!!.edges.any { it.semanticIntent == "SEARCH_MARKETPLACE" })
        assertTrue(ebay!!.edges.any { it.semanticIntent == "SEARCH_EBAY" })
        assertTrue((facebook.edges + ebay.edges).all { it.successCount == 0 && it.lastVerifiedAt == null })
    }

    @Test fun unrelatedHostsAreNotSeeded() {
        assertEquals(null, PrioritySiteSeeds.seedForHost("example.com"))
    }
}
