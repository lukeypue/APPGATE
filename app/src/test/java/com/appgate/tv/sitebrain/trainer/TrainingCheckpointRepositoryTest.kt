package com.appgate.tv.sitebrain.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCheckpointRepositoryTest {
    private class MemoryStore : TrainingCheckpointStore {
        val data = mutableMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun put(key: String, value: String) { data[key] = value }
        override fun keys(): Set<String> = data.keys
        override fun remove(key: String) { data.remove(key) }
    }

    @Test
    fun roundTripPreservesResumeFrontierAndSafeRoute() {
        val store = MemoryStore()
        val repo = TrainingCheckpointRepository(store)
        val state = TrainerSiteState(
            siteId = "ebay",
            status = TrainingStatus.PAUSED,
            verifiedCoverage = 73,
            frontier = listOf("SEARCH", "OPEN_DETAIL", "PAGINATE"),
            lastRoute = "/sch/i.html?_nkw=ford+expedition",
            lastSuccessfulTrainingAt = 1234L,
            currentActivity = "Verifying results",
            actionsTakenThisCycle = 14,
            updatedAt = 2345L
        )
        repo.save(state)

        assertEquals(state, repo.load("ebay"))
        val raw = store.data.values.single().lowercase()
        assertFalse(raw.contains("cookie"))
        assertFalse(raw.contains("password"))
        assertFalse(raw.contains("session"))
        assertFalse(raw.contains("token"))
    }

    @Test
    fun sitesAreIsolatedLoadAllWorksAndClearOnlyRemovesOne() {
        val repo = TrainingCheckpointRepository(MemoryStore())
        repo.save(TrainerSiteState("ebay", verifiedCoverage = 55))
        repo.save(TrainerSiteState("carmax", verifiedCoverage = 91))

        assertEquals(2, repo.loadAll().size)
        repo.clear("ebay")
        assertNull(repo.load("ebay"))
        assertEquals(91, repo.load("carmax")?.verifiedCoverage)
    }

    @Test
    fun unsafeLastRouteIsSanitizedBeforePersistence() {
        val repo = TrainingCheckpointRepository(MemoryStore())
        repo.save(TrainerSiteState("facebook_marketplace", lastRoute = "/marketplace/?token=secret123&category=vehicles"))
        val loaded = repo.load("facebook_marketplace")!!
        assertTrue(loaded.lastRoute!!.contains("category=vehicles"))
        assertFalse(loaded.lastRoute!!.contains("secret123"))
    }
}
