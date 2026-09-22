package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticPageSnapshotTest {
    @Test
    fun semanticallyEquivalentCategoryPagesHaveStableFingerprint() {
        val a = SemanticPageSnapshot.parse(categoryJson("Vacation Property", "12 listings"))
        val b = SemanticPageSnapshot.parse(categoryJson("Vacation Property", "18 listings"))

        assertEquals("example.com/category/vacation", a.routeSignature)
        assertEquals(a.routeSignature, b.routeSignature)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun resultPageHasDifferentFingerprint() {
        val category = SemanticPageSnapshot.parse(categoryJson("Vacation Property", "12 listings"))
        val results = SemanticPageSnapshot.parse(
            """{
              "url":"https://example.com/search/timeshare",
              "host":"example.com",
              "title":"Timeshare results",
              "visibleTextSummary":"Timeshare results 24 listings",
              "headings":["Timeshare results"],
              "elements":[{"id":"e1","tag":"a","role":"link","label":"WorldMark Bear Lake","href":"https://example.com/listing/12345","inputType":null,"selected":false,"disabled":false,"nearbyText":"WorldMark Bear Lake","locatorHints":["a"]}],
              "pageType":"RESULT_LIST",
              "loginDetected":false,
              "challengeDetected":false
            }""".trimIndent()
        )
        assertNotEquals(category.fingerprint, results.fingerprint)
        assertEquals(PageType.RESULT_LIST, results.pageType)
    }

    @Test
    fun passwordValuesAreNeverRequestedBySnapshotScript() {
        val script = SemanticPageSnapshot.javascript().lowercase()
        assertTrue(script.contains("type==='password'"))
        assertTrue(!script.contains("document.cookie"))
        assertTrue(!script.contains("localstorage"))
        assertTrue(!script.contains("sessionstorage"))
    }

    @Test
    fun challengeDetectionRequiresStrongPageEvidence() {
        val script = SemanticPageSnapshot.javascript()
        assertTrue(script.contains("visiblePasswordFields"))
        assertTrue(script.contains("challengePath"))
        assertTrue(script.contains("challengeTitle"))
        assertTrue(script.contains("challengeWidget"))
        assertTrue(script.contains("!richInteractivePage && (challengePath || challengeTitle || challengePhrase)"))
        assertTrue(!script.contains("var challenge=/(captcha|verify you are human|security check|checkpoint|unusual traffic|confirm your identity)/"))
    }

    @Test
    fun richStorefrontDoesNotBecomeChallengeFromGenericSecurityText() {
        val script = SemanticPageSnapshot.javascript()
        assertTrue(script.contains("richInteractivePage"))
        assertTrue(script.contains("challengeWidget"))
        assertTrue(script.contains("!richInteractivePage"))
    }

    @Test
    fun snapshotScriptCapturesComboboxesAndNativeChoices() {
        val script = SemanticPageSnapshot.javascript()
        assertTrue(script.contains("[role=\"combobox\"]"))
        assertTrue(script.contains("[role=\"option\"]"))
        assertTrue(script.contains("choices:"))
        assertTrue(script.contains("el.options"))
    }

    private fun categoryJson(heading: String, countText: String): String =
        """{
          "url":"https://example.com/category/vacation?sort=newest",
          "host":"example.com",
          "title":"$heading",
          "visibleTextSummary":"$heading $countText",
          "headings":["$heading"],
          "elements":[
            {"id":"e1","tag":"a","role":"link","label":"Timeshares","href":"https://example.com/category/timeshares","inputType":null,"selected":false,"disabled":false,"nearbyText":"Timeshares","locatorHints":["a"]},
            {"id":"e2","tag":"button","role":"button","label":"Price","href":null,"inputType":null,"selected":false,"disabled":false,"nearbyText":"Price","locatorHints":["button"]}
          ],
          "pageType":"CATEGORY",
          "loginDetected":false,
          "challengeDetected":false
        }""".trimIndent()
}
