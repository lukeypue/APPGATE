package com.appgate.sitebrain.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPortModelTest {
    @Test
    fun portableObservationAndActionsAreValueObjects() {
        val element = InteractiveElement(
            ref = "price-max",
            role = "combobox",
            label = "Max price",
            value = "8000",
            href = null,
            bounds = Rect(10, 20, 100, 40)
        )
        val observation = Observation(
            url = "https://example.com/search?priceMax=8000",
            host = "example.com",
            title = "Results",
            pageType = "RESULTS",
            routeSignature = "/search",
            elements = listOf(element),
            headings = listOf("Search results"),
            challengeDetected = false,
            loginDetected = false,
            structuralHash = "abc123"
        )

        assertEquals("RESULTS", observation.pageType)
        assertEquals(element, observation.elements.single())
        assertEquals(Action.Click("price-max"), Action.Click("price-max"))
        assertEquals(Action.Type("q", "ford expedition"), Action.Type("q", "ford expedition"))
        assertTrue(Action.Back is Action)
    }
}
