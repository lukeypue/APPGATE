package com.appgate.tv.sitebrain.minibrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookMarketplaceMiniBrainTest {
    private val brain = FacebookMarketplaceMiniBrain()

    @Test
    fun acceptsFacebookMarketplaceHosts() {
        assertTrue(brain.acceptsHost("www.facebook.com"))
        assertTrue(brain.acceptsHost("facebook.com"))
    }

    @Test
    fun prioritizesSearchBeforeListings() {
        val decision = brain.chooseNext(
            "https://www.facebook.com/marketplace/",
            listOf(
                MiniBrainControl("g1", "2020 Ford Expedition", "a", "link", "https://www.facebook.com/marketplace/item/123"),
                MiniBrainControl("g2", "Search Marketplace", "input", "searchbox", null)
            ),
            "Ford Expedition"
        )
        assertNotNull(decision)
        assertEquals(MiniBrainIntent.SEARCH, decision!!.intent)
        assertEquals("g2", decision.controlId)
    }

    @Test
    fun ignoresConsequentialControls() {
        assertEquals(
            MiniBrainIntent.IGNORE,
            brain.classify(MiniBrainControl("g1", "Message seller", "button", "button", null))
        )
    }
}
