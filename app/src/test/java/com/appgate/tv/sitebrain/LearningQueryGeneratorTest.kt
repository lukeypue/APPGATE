package com.appgate.tv.sitebrain

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningQueryGeneratorTest {
    @Test
    fun vehicleSitesUseVehicleLikeQueries() {
        val q = LearningQueryGenerator.nextQuery(
            siteKey = "ksl_cars",
            siteName = "KSL Cars",
            host = "cars.ksl.com",
            actionOrdinal = 3,
            timeBucket = 42
        )
        val vehicleTerms = listOf("ford", "toyota", "honda", "chevrolet", "suv", "pickup", "axle", "truck", "crossover")
        assertTrue(vehicleTerms.any { q.lowercase().contains(it) })
    }

    @Test
    fun searchesVaryAcrossAttempts() {
        val a = LearningQueryGenerator.nextQuery("general", "Bing", "bing.com", 1, 100)
        val b = LearningQueryGenerator.nextQuery("general", "Bing", "bing.com", 2, 101)
        assertNotEquals(a, b)
    }
}
