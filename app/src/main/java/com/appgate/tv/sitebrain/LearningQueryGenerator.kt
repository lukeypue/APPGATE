package com.appgate.tv.sitebrain

import kotlin.math.abs

/**
 * Generates varied, harmless training searches so the Site Brain learns how a site's
 * search UI behaves across different terms instead of memorizing one canned phrase.
 */
object LearningQueryGenerator {
    private val vehicleQueries = listOf(
        "Ford Expedition",
        "Toyota Tacoma",
        "Honda CR-V",
        "Chevrolet Tahoe",
        "hybrid SUV",
        "electric pickup",
        "3.73 axle",
        "SUV under 10000",
        "truck under 150000 miles",
        "used AWD crossover"
    )

    private val shoppingQueries = listOf(
        "cordless drill",
        "laptop",
        "patio furniture",
        "coffee maker",
        "mountain bike",
        "winter jacket",
        "55 inch TV",
        "robot vacuum",
        "camping tent",
        "wireless headphones"
    )

    private val jobQueries = listOf(
        "electrician",
        "warehouse",
        "customer service",
        "software engineer",
        "delivery driver",
        "medical assistant",
        "part time",
        "remote work"
    )

    private val housingQueries = listOf(
        "3 bedroom house",
        "2 bedroom apartment",
        "condo",
        "townhouse",
        "cabin",
        "pet friendly rental",
        "garage",
        "acreage"
    )

    private val generalQueries = listOf(
        "weather tomorrow",
        "chicken recipes",
        "home improvement",
        "local events",
        "camera reviews",
        "gardening tips",
        "travel ideas",
        "movie reviews",
        "running shoes",
        "computer monitor",
        "fishing gear",
        "used books"
    )

    fun nextQuery(
        siteKey: String,
        siteName: String,
        host: String,
        actionOrdinal: Int,
        timeBucket: Long = System.currentTimeMillis() / 30_000L
    ): String {
        val identity = "$siteKey $siteName $host".lowercase()
        val pool = when {
            listOf("car", "auto", "vehicle", "kbb", "cargurus", "cars.com", "carfax", "autotrader")
                .any(identity::contains) -> vehicleQueries
            listOf("job", "indeed", "linkedin").any(identity::contains) -> jobQueries
            listOf("realty", "realtor", "zillow", "apartment", "rent", "redfin")
                .any(identity::contains) -> housingQueries
            listOf("market", "offerup", "ebay", "amazon", "walmart", "target", "craigslist", "shopping")
                .any(identity::contains) -> shoppingQueries
            else -> generalQueries
        }

        val hash = "$identity|$actionOrdinal|$timeBucket".hashCode()
        val index = abs(hash.toLong()).rem(pool.size.toLong()).toInt()
        return pool[index]
    }
}
