package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebNavigationPolicyTest {
    @Test
    fun httpsLinksStayInsideWebView() {
        val decision = WebNavigationPolicy.decide("https://www.ebay.com/itm/123", "www.ebay.com")
        assertEquals(WebNavigationDecision.KEEP_IN_WEBVIEW, decision.kind)
        assertEquals("https://www.ebay.com/itm/123", decision.url)
    }

    @Test
    fun appIntentUsesSafeBrowserFallbackInsteadOfOpeningInstalledApp() {
        val intent = "intent://itm/123#Intent;scheme=ebay;S.browser_fallback_url=https%3A%2F%2Fwww.ebay.com%2Fitm%2F123;end"
        val decision = WebNavigationPolicy.decide(intent, "www.ebay.com")
        assertEquals(WebNavigationDecision.LOAD_WEB_FALLBACK, decision.kind)
        assertEquals("https://www.ebay.com/itm/123", decision.url)
    }

    @Test
    fun customAppSchemeWithoutWebFallbackIsBlocked() {
        val decision = WebNavigationPolicy.decide("ebay://itm/123", "www.ebay.com")
        assertEquals(WebNavigationDecision.BLOCK_EXTERNAL_APP, decision.kind)
        assertNull(decision.url)
    }
}
