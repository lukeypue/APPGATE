package com.appgate.tv.sitebrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningReportTest {
    @Test fun reportKeepsDebugStructureButRedactsPrivateData() {
        val json = LearningReportWriter.encode(listOf(
            LearningEvent(
                timestamp = 1234,
                source = "eBay",
                host = "ebay.com",
                pageType = "RESULT_LIST",
                route = "/sch/i.html",
                action = "SEARCH",
                outcome = "VERIFIED",
                coverageBefore = 0.2,
                coverageAfter = 0.3,
                note = "user@example.com 801-555-1234 token=secret123"
            )
        ))
        assertTrue(json.contains("ebay.com"))
        assertTrue(json.contains("SEARCH"))
        assertTrue(json.contains("VERIFIED"))
        assertFalse(json.contains("user@example.com"))
        assertFalse(json.contains("801-555-1234"))
        assertFalse(json.contains("secret123"))
    }

    @Test fun reportStillIdentifiesSearchRunWhenNoLearningEventsWereRecorded() {
        val json = LearningReportWriter.encode(emptyList())
        assertTrue(json.contains("\"reportType\": \"site_brain_search_run\""))
        assertTrue(json.contains("\"events\": []"))
    }
}
