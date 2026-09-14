package com.appgate.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSessionGuardTest {
    @Test
    fun acceptsOnlyCallbacksFromActiveSourceSessionAndExpectedHost() {
        val guard = SourceSessionGuard()
        val ebay = guard.begin("ebay", "ebay.com")
        assertTrue(guard.accept(ebay.id, "www.ebay.com"))

        val craigslist = guard.begin("craigslist", "craigslist.org")
        assertFalse(guard.accept(ebay.id, "www.ebay.com"))
        assertTrue(guard.accept(craigslist.id, "www.craigslist.org"))
        assertFalse(guard.accept(craigslist.id, "www.ebay.com"))
    }

    @Test
    fun subdomainsCountAsExpectedHostButLookalikesDoNot() {
        val guard = SourceSessionGuard()
        val facebook = guard.begin("facebook_marketplace", "facebook.com")
        assertTrue(guard.accept(facebook.id, "www.facebook.com"))
        assertTrue(guard.accept(facebook.id, "m.facebook.com"))
        assertFalse(guard.accept(facebook.id, "facebook.com.example.org"))
    }
}
