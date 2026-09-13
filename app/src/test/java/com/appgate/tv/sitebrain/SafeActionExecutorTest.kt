package com.appgate.tv.sitebrain

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeActionExecutorTest {
    @Test fun allowsSameSiteSafeNavigation() {
        val e = SemanticElement("1", "a", "link", "Vacation Rentals", "https://example.com/rentals", null, false, false, null, listOf("a"))
        val js = SafeActionExecutor.javascriptFor(e, ActionKind.OPEN_CATEGORY, null, "example.com")
        assertTrue(js!!.contains("example.com/rentals"))
    }

    @Test fun blocksCrossSiteNavigationDuringExploration() {
        val e = SemanticElement("1", "a", "link", "Elsewhere", "https://other.example/path", null, false, false, null, listOf("a"))
        assertNull(SafeActionExecutor.javascriptFor(e, ActionKind.NAVIGATE, null, "example.com"))
    }

    @Test fun blocksConsequentialActionsEvenIfElementLooksClickable() {
        val e = SemanticElement("1", "button", "button", "Send Message", null, null, false, false, null, listOf("button"))
        assertNull(SafeActionExecutor.javascriptFor(e, ActionKind.MESSAGE, null, "example.com"))
    }

    @Test fun searchInputUsesProvidedQueryWithoutSubmittingConsequentialForm() {
        val e = SemanticElement("1", "input", "searchbox", "Search", null, "search", false, false, null, listOf("input[name=\"q\"]"))
        val js = SafeActionExecutor.javascriptFor(e, ActionKind.SEARCH, "Ford Expedition", "example.com")
        assertTrue(js!!.contains("Ford Expedition"))
        assertTrue(js.contains("FILLED"))
    }
}
