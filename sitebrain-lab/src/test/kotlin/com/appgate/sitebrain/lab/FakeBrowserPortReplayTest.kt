package com.appgate.sitebrain.lab

import com.appgate.sitebrain.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBrowserPortReplayTest {
    @Test
    fun replayResultsToDetailAndBackWithoutAndroid() {
        val resultsUrl = "https://example.com/search?q=ford"
        val detailUrl = "https://example.com/listing/1"
        val results = Observation(
            url = resultsUrl,
            host = "example.com",
            title = "Results",
            pageType = "RESULTS",
            routeSignature = "/search",
            elements = listOf(InteractiveElement("result-1", "link", "2014 Ford Expedition", href = detailUrl))
        )
        val detail = Observation(
            url = detailUrl,
            host = "example.com",
            title = "2014 Ford Expedition",
            pageType = "DETAIL",
            routeSignature = "/listing/{id}"
        )
        val port = FakeBrowserPort(
            observationsByUrl = linkedMapOf(resultsUrl to results, detailUrl to detail),
            transitions = mapOf((resultsUrl to Action.Click("result-1")) to detailUrl)
        )

        assertTrue(port.navigate(resultsUrl).accepted)
        assertEquals("RESULTS", port.observe().pageType)
        assertTrue(port.act(Action.Click("result-1")).accepted)
        assertEquals("DETAIL", port.observe().pageType)
        assertTrue(port.act(Action.Back).accepted)
        assertEquals("RESULTS", port.observe().pageType)
    }
}
