package com.appgate.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningNavigationPolicyTest {
    @Test
    fun normalTrainingBlocksUnrelatedCrossSiteNavigation() {
        assertFalse(
            LearningNavigationPolicy.shouldAllow(
                targetHost = "offerup.com",
                destinationHost = "example.com",
                humanAuthWindow = false
            )
        )
    }

    @Test
    fun humanAuthWindowAllowsKnownOAuthProviders() {
        assertTrue(LearningNavigationPolicy.shouldAllow("offerup.com", "accounts.google.com", true))
        assertTrue(LearningNavigationPolicy.shouldAllow("offerup.com", "oauth.facebook.com", true))
        assertTrue(LearningNavigationPolicy.shouldAllow("offerup.com", "www.facebook.com", true))
    }

    @Test
    fun humanAuthWindowStillBlocksUnknownCrossSiteHosts() {
        assertFalse(LearningNavigationPolicy.shouldAllow("offerup.com", "random.example", true))
    }

    @Test
    fun targetHostAndSubdomainsAlwaysAllowed() {
        assertTrue(LearningNavigationPolicy.shouldAllow("facebook.com", "m.facebook.com", false))
        assertTrue(LearningNavigationPolicy.shouldAllow("offerup.com", "offerup.com", false))
    }
}
