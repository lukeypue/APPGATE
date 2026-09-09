package com.searchai.mobile

import org.junit.Assert.*
import org.junit.Test

class SearchAiCoreTest {
 @Test fun registryHasSixteenSources() { assertEquals(16, SourceRegistry.all().size) }
 @Test fun plannerKeepsGeneralAndNamedSource() { val p=SearchPlanner.plan("find a truck on KSL"); assertTrue(p.any{it.id=="general"}); assertTrue(p.any{it.id=="ksl"}) }
 @Test fun sanitizerRemovesSecretsAndQuery() { val r=LearningSanitizer.sanitize("https://x.test/a?token=secret", "cookie=abc token=xyz changed layout"); assertEquals("https://x.test/a",r.url); assertFalse(r.details.contains("abc")); assertFalse(r.details.contains("xyz")) }
 @Test fun updatePoliciesExist() { assertEquals(listOf("Every launch","Daily","Weekly","Ask me first"), UpdatePolicy.entries.map{it.label}) }
 @Test fun verificationDetectorFindsHumanCheck() { assertTrue(VerificationDetector.requiresHuman("Please verify you are human")); assertFalse(VerificationDetector.requiresHuman("Cars for sale")) }
}
