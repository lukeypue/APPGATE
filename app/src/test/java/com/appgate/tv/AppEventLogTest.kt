package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEventLogTest {
    @Test
    fun sanitizeUrlRemovesQueryAndFragment() {
        val sanitized = AppEventLog.sanitizeUrl("https://example.com/path?q=secret#part")
        assertEquals("https://example.com/path", sanitized)
    }

    @Test
    fun reportContainsEventsAndNotesWithoutSensitiveUrlParts() {
        val events = listOf(
            BrowserEvent(
                type = "navigation",
                timestamp = 1234L,
                host = "example.com",
                detail = AppEventLog.sanitizeUrl("https://example.com/page?token=abc#x"),
                outcome = "success"
            )
        )
        val json = AppEventLog.buildReport(events, "Search results need better grouping", "2.1", 5)
        assertTrue(json.contains("Search results need better grouping"))
        assertTrue(json.contains("example.com/page"))
        assertFalse(json.contains("token=abc"))
        assertFalse(json.contains("#x"))
    }
}
