package com.appgate.sitebrainlab

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageTest {
    @Test fun reachesHundredOnlyWhenEveryDiscoveredFunctionIsClassified() {
        val functions = listOf(
            StructuralFunction("search", FunctionStatus.VERIFIED),
            StructuralFunction("category", FunctionStatus.VERIFIED),
            StructuralFunction("filter", FunctionStatus.VERIFIED),
            StructuralFunction("results", FunctionStatus.VERIFIED),
            StructuralFunction("detail", FunctionStatus.DISCOVERED),
            StructuralFunction("pagination", FunctionStatus.PROTECTED)
        )
        assertEquals(83, CoverageEngine.calculate(functions).percent)
        val finished = functions.map { if (it.key == "detail") it.copy(status = FunctionStatus.UNRESOLVED) else it }
        assertEquals(100, CoverageEngine.calculate(finished).percent)
    }

    @Test fun checkpointRoundTripPreservesFrontier() {
        val cp = LabCheckpoint("ebay.com", listOf("home"), listOf("filter-price", "next-page"), listOf("search"), listOf(PackBoundary("login", "HUMAN_OR_AUTH_REQUIRED")))
        assertEquals(cp, CheckpointCodec.decode(CheckpointCodec.encode(cp)))
    }
}
