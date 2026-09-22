package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterLearningTest {
    @Test
    fun learnsVehicleFilterNamesAndCurrentValues() {
        val snapshot = PageSnapshot(
            url = "https://cars.ksl.com/search",
            host = "cars.ksl.com",
            routeSignature = "cars.ksl.com/search",
            title = "Cars",
            visibleTextSummary = "Make Model Price Mileage",
            headings = listOf("Cars"),
            elements = listOf(
                SemanticElement("e1", "select", null, "Make", null, null, true, false, "Make Ford", listOf("#make"), "Ford"),
                SemanticElement("e2", "select", null, "Model", null, null, true, false, "Model Expedition", listOf("#model"), "Expedition"),
                SemanticElement("e3", "input", null, "Maximum Price", null, "number", false, false, "Price", listOf("#priceTo"), "8000"),
                SemanticElement("e4", "input", null, "Maximum Mileage", null, "number", false, false, "Mileage", listOf("#mileageTo"), "150000")
            ),
            pageType = PageType.FILTER_PANEL,
            loginDetected = false,
            challengeDetected = false,
            fingerprint = "test"
        )

        val filters = FilterLearning.discover(snapshot)
        assertEquals(4, filters.size)
        assertEquals("Ford", FilterLearning.appliedValues(snapshot)["Make"])
        assertEquals("Expedition", FilterLearning.appliedValues(snapshot)["Model"])
        assertEquals("8000", FilterLearning.appliedValues(snapshot)["Maximum Price"])
        assertEquals("150000", FilterLearning.appliedValues(snapshot)["Maximum Mileage"])
    }

    @Test
    fun ignoresOrdinaryNonFilterLinks() {
        val snapshot = PageSnapshot(
            url = "https://example.com",
            host = "example.com",
            routeSignature = "example.com/",
            title = "Home",
            visibleTextSummary = "Home",
            headings = emptyList(),
            elements = listOf(
                SemanticElement("e1", "a", "link", "About us", "https://example.com/about", null, false, false, null, listOf("a"))
            ),
            pageType = PageType.HOME,
            loginDetected = false,
            challengeDetected = false,
            fingerprint = "test"
        )
        assertTrue(FilterLearning.discover(snapshot).isEmpty())
    }
}
