package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiTeacherPreparationTest {
    private class MemoryStore : SiteBrainStore {
        private val data = mutableMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun put(key: String, value: String) { data[key] = value }
    }

    private fun observation(element: SemanticElement): SiteBrainObservation {
        val snapshot = PageSnapshot(
            url = "https://cars.ksl.com/search",
            host = "cars.ksl.com",
            routeSignature = "cars.ksl.com/search",
            title = "Cars",
            visibleTextSummary = "Cars",
            headings = listOf("Cars"),
            elements = listOf(element),
            pageType = PageType.FILTER_PANEL,
            loginDetected = false,
            challengeDetected = false,
            fingerprint = "fp"
        )
        return SiteBrainObservation(
            snapshot = snapshot,
            brain = SiteBrainState.empty("cars.ksl.com"),
            controllerState = SiteBrainControllerState.SEARCHING,
            safeActionsFound = 1,
            consequentialActionsBlocked = 0
        )
    }

    @Test
    fun teacherCanDirectOnlyExistingSafeControl() {
        val controller = WebViewSiteBrainController(SiteBrainRepository(MemoryStore()))
        val make = SemanticElement(
            id = "make", tag = "select", role = "combobox", label = "Make",
            href = null, inputType = null, selected = false, disabled = false,
            nearbyText = "Vehicle make", locatorHints = listOf("#make"),
            choices = listOf("Any", "Ford")
        )
        val prepared = controller.prepareTeacherExploration(
            observation(make),
            targetElementId = "make",
            actionKind = ActionKind.APPLY_FILTER,
            query = null
        )
        assertNotNull(prepared)
        assertEquals(ActionKind.APPLY_FILTER, prepared!!.edge.actionKind)
    }

    @Test
    fun teacherCannotInventMissingControlOrConsequentialAction() {
        val controller = WebViewSiteBrainController(SiteBrainRepository(MemoryStore()))
        val make = SemanticElement(
            id = "make", tag = "select", role = "combobox", label = "Make",
            href = null, inputType = null, selected = false, disabled = false,
            nearbyText = "Vehicle make", locatorHints = listOf("#make")
        )
        assertNull(controller.prepareTeacherExploration(observation(make), "missing", ActionKind.APPLY_FILTER, null))
        assertNull(controller.prepareTeacherExploration(observation(make), "make", ActionKind.PURCHASE, null))
    }
}
