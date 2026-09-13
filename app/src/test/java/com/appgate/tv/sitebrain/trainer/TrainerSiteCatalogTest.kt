package com.appgate.tv.sitebrain.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerSiteCatalogTest {
    @Test
    fun starterCatalogContainsApprovedSitesWithSafeUniqueEntries() {
        val sites = TrainerSiteCatalog.starterSites
        assertEquals(11, sites.size)
        assertEquals(sites.size, sites.map { it.id }.toSet().size)
        assertTrue(sites.all { it.entryUrl.startsWith("https://") })
        assertTrue(sites.all { it.hostAliases.isNotEmpty() })
        assertEquals(
            setOf("ksl_cars", "facebook_marketplace", "ebay", "craigslist", "autotrader", "cars_com", "cargurus", "edmunds", "truecar", "carmax", "offerup"),
            sites.map { it.id }.toSet()
        )
    }

    @Test
    fun newSiteStartsUntrainedAndBelowTarget() {
        val site = TrainerSiteCatalog.starterSites.first()
        val state = TrainerSiteState(siteId = site.id)
        assertEquals(TrainingStatus.NOT_STARTED, state.status)
        assertTrue(state.verifiedCoverage < 95)
        assertTrue(state.frontier.isEmpty())
    }
}
