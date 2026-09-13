package com.appgate.tv.sitebrain

import org.junit.Assert.*
import org.junit.Test

class SafeActionExecutorTest {
    @Test fun searchActionSubmitsOrPressesEnterAfterFilling() {
        val element = SemanticElement(
            id = "q", tag = "input", role = "searchbox", label = "Search",
            href = null, inputType = "search", selected = false, disabled = false,
            nearbyText = null, locatorHints = listOf("#q")
        )
        val js = SafeActionExecutor.javascriptFor(element, ActionKind.SEARCH, "Ford Expedition", "ebay.com")
        assertNotNull(js)
        assertTrue(js!!.contains("requestSubmit") || js.contains("KeyboardEvent"))
        assertTrue(js.contains("Ford Expedition"))
    }

    @Test fun externalHostHrefIsRejected() {
        val element = SemanticElement(
            id = "x", tag = "a", role = "link", label = "View details",
            href = "https://evil.example/item", inputType = null, selected = false, disabled = false,
            nearbyText = null, locatorHints = listOf("#x")
        )
        assertNull(SafeActionExecutor.javascriptFor(element, ActionKind.OPEN_DETAIL, null, "ebay.com"))
    }
}
