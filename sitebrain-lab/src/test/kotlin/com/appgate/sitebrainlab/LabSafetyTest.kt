package com.appgate.sitebrainlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LabSafetyTest {
    @Test fun classifiesSafeAndConsequentialActions() {
        assertEquals(LabActionSafety.SAFE, LabSafety.classify("Search", "searchbox", null))
        assertEquals(LabActionSafety.SAFE, LabSafety.classify("Price filter", "button", null))
        assertEquals(LabActionSafety.CONSEQUENTIAL, LabSafety.classify("Buy now", "button", null))
        assertEquals(LabActionSafety.CONSEQUENTIAL, LabSafety.classify("Message seller", "button", null))
        assertEquals(LabActionSafety.PROTECTED, LabSafety.classify("Sign in", "button", "/login"))
    }

    @Test fun sanitizerRemovesPrivateLookingText() {
        val pack = SiteKnowledgePack(1, "example.com", emptyList(), 1, 20, "LEARNING",
            listOf(PackNode("1", "DETAIL", "/listing/1", "email me at user@example.com token=abc123")),
            emptyList(), emptyList())
        val clean = PackSanitizer.sanitize(pack)
        assertFalse(clean.nodes.single().summary.contains("@"))
        assertFalse(clean.nodes.single().summary.contains("token", ignoreCase = true))
    }
}
