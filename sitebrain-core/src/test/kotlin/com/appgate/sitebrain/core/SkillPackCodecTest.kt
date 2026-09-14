package com.appgate.sitebrain.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillPackCodecTest {
    @Test
    fun canonicalPackRoundTripsByteIdentical() {
        val pack = SiteSkillPack(
            schemaVersion = 1,
            domain = "example.com",
            packVersion = "1.0.0",
            createdAt = 1789350366443,
            lastVerifiedAt = 1789350366443,
            pageTypes = listOf(PageTypeRecord("results", "RESULTS", "hash-results", listOf("search-results"), 0.9)),
            controls = listOf(ControlRecord("price-max", "results", "FILTER_RANGE", "Max price", listOf("aria:Max price"), "SAFE", 0.8)),
            skills = listOf(SkillRecord("set-price-max", "SET_FILTER_PRICE_MAX", "{\"price_max\":\"int\"}", listOf("price-max"), listOf("result_count_changed"), 0.8)),
            routes = listOf(RouteRecord("home-to-results", "HOME", "RESULTS", listOf("SEARCH"), 1, 900, 0.9)),
            evidence = listOf(EvidenceRecord("ev1", "https://example.com/search", "dom1", "shot1", 1789350366443)),
            metadata = linkedMapOf("author" to "sitebrain-core", "mode" to "B1")
        )

        val encoded = SkillPackCodec.encode(pack)
        val decoded = SkillPackCodec.decode(encoded)
        val encodedAgain = SkillPackCodec.encode(decoded)

        assertEquals(pack, decoded)
        assertEquals(encoded, encodedAgain)
    }
}
