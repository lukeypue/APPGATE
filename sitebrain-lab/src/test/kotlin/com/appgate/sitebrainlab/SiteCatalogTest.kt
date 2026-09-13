package com.appgate.sitebrainlab

import org.junit.Assert.*
import org.junit.Test

class SiteCatalogTest {
    @Test fun prioritySitesUsePublicHttpsEntryPointsWithoutCredentials() {
        assertTrue(SiteCatalog.priority.size >= 10)
        SiteCatalog.priority.forEach {
            assertTrue(it.entryUrl.startsWith("https://"))
            assertFalse(it.entryUrl.contains("@"))
            assertTrue(it.allowedHosts.isNotEmpty())
        }
        assertTrue(SiteCatalog.byKey("facebook_marketplace")!!.authenticatedCoverageDeviceOnly)
    }

    @Test(expected = IllegalArgumentException::class)
    fun guardedAdapterRefusesConsequentialAction() {
        val adapter = object : GuardedBrowserAdapter() {
            override fun observe() = LabObservation("x", "HOME", emptyList())
            override fun executeVerifiedSafe(action: FrontierAction) = true
        }
        adapter.executeSafe(FrontierAction("x", LabControl("buy", "Buy now", LabActionSafety.CONSEQUENTIAL)))
    }
}
