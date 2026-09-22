package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeActionClassifierTest {
    private fun element(label: String, href: String? = "https://example.com/$label") = SemanticElement(
        id = label,
        tag = "a",
        role = "link",
        label = label,
        href = href,
        inputType = null,
        selected = false,
        disabled = false,
        nearbyText = null
    )

    @Test
    fun searchAndCategoryControlsAreSafe() {
        assertEquals(SafetyClass.SAFE, SafeActionClassifier.classify(element("Search")))
        assertEquals(SafetyClass.SAFE, SafeActionClassifier.classify(element("Vacation Rentals")))
    }

    @Test
    fun purchaseAndMessagingControlsAreConsequential() {
        listOf("Buy now", "Checkout", "Send Message", "Delete", "Post Listing", "Follow")
            .forEach { label ->
                assertEquals(label, SafetyClass.CONSEQUENTIAL, SafeActionClassifier.classify(element(label)))
            }
    }

    @Test
    fun disabledAndSensitiveInputsAreBlocked() {
        val disabled = element("Vacation Rentals").copy(disabled = true)
        val password = element("Password", null).copy(tag = "input", inputType = "password")
        assertEquals(SafetyClass.BLOCKED, SafeActionClassifier.classify(disabled))
        assertEquals(SafetyClass.BLOCKED, SafeActionClassifier.classify(password))
    }
    @Test
    fun dropdownsAndOptionsAreFilterActions() {
        val select = SemanticElement("s", "select", "combobox", "Make", null, null, false, false, null, listOf("#make"))
        val option = SemanticElement("o", "div", "option", "Ford", null, null, false, false, "Make Ford", listOf("[role=option]"))
        assertEquals(ActionKind.APPLY_FILTER, SafeActionClassifier.inferActionKind(select))
        assertEquals(ActionKind.APPLY_FILTER, SafeActionClassifier.inferActionKind(option))
    }
}
