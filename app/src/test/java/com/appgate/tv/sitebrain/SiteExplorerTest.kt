package com.appgate.tv.sitebrain

import org.junit.Assert.assertTrue
import org.junit.Test

class SiteExplorerTest {
    @Test
    fun prefersSafeUnexploredCategoryAndNeverConsequential() {
        val snapshot = PageSnapshot(
            url = "https://example.com", host = "example.com", routeSignature = "example.com/", title = "Home",
            visibleTextSummary = "", headings = emptyList(),
            elements = listOf(
                element("Vacation Rentals", "https://example.com/category/vacation"),
                element("Buy now", "https://example.com/buy")
            ), pageType = PageType.HOME, loginDetected = false, challengeDetected = false, fingerprint = "home"
        )
        val decision = SiteExplorer.nextAction(SiteBrainState.empty("example.com"), snapshot, ExplorerBudget())
        assertTrue(decision is ExplorerDecision.Act && decision.element.label == "Vacation Rentals")
    }

    @Test
    fun stopsWhenBudgetIsExhausted() {
        val decision = SiteExplorer.nextAction(
            SiteBrainState.empty("example.com"),
            PageSnapshot("https://example.com", "example.com", "example.com/", "Home", "", emptyList(), emptyList(), PageType.HOME, false, false, "home"),
            ExplorerBudget(maxActions = 1, actionsTaken = 1)
        )
        assertTrue(decision is ExplorerDecision.Stop)
    }

    private fun element(label: String, href: String) = SemanticElement(
        id = label, tag = "a", role = "link", label = label, href = href,
        inputType = null, selected = false, disabled = false, nearbyText = label
    )
}
