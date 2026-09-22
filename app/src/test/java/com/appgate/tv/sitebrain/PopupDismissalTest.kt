package com.appgate.tv.sitebrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupDismissalTest {
    @Test fun closeAndNoThanksAreSafeDismissals() {
        assertTrue(PopupDismissal.isSafeDismissLabel("Close"))
        assertTrue(PopupDismissal.isSafeDismissLabel("No thanks"))
        assertTrue(PopupDismissal.isSafeDismissLabel("×"))
    }

    @Test fun consentAndConsequentialButtonsAreNeverAutoDismissed() {
        assertFalse(PopupDismissal.isSafeDismissLabel("Accept all"))
        assertFalse(PopupDismissal.isSafeDismissLabel("Continue"))
        assertFalse(PopupDismissal.isSafeDismissLabel("Subscribe now"))
        assertFalse(PopupDismissal.isSafeDismissLabel("Skip to main content"))
        assertFalse(PopupDismissal.isSafeDismissLabel("Skip navigation"))
    }
}
