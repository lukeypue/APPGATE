package com.appgate.sitebrainlab

data class SiteLearningConfig(
    val key: String,
    val entryUrl: String,
    val allowedHosts: Set<String>,
    val authenticatedCoverageDeviceOnly: Boolean = false
)

object SiteCatalog {
    val priority: List<SiteLearningConfig> = listOf(
        SiteLearningConfig("ksl_cars", "https://cars.ksl.com/", setOf("cars.ksl.com", "www.ksl.com")),
        SiteLearningConfig("ebay", "https://www.ebay.com/", setOf("ebay.com", "www.ebay.com")),
        SiteLearningConfig("craigslist", "https://www.craigslist.org/", setOf("craigslist.org", "www.craigslist.org")),
        SiteLearningConfig("autotrader", "https://www.autotrader.com/", setOf("autotrader.com", "www.autotrader.com")),
        SiteLearningConfig("cars_com", "https://www.cars.com/", setOf("cars.com", "www.cars.com")),
        SiteLearningConfig("cargurus", "https://www.cargurus.com/", setOf("cargurus.com", "www.cargurus.com")),
        SiteLearningConfig("edmunds", "https://www.edmunds.com/", setOf("edmunds.com", "www.edmunds.com")),
        SiteLearningConfig("truecar", "https://www.truecar.com/", setOf("truecar.com", "www.truecar.com")),
        SiteLearningConfig("carmax", "https://www.carmax.com/", setOf("carmax.com", "www.carmax.com")),
        SiteLearningConfig("offerup", "https://offerup.com/", setOf("offerup.com", "www.offerup.com")),
        SiteLearningConfig("facebook_marketplace", "https://www.facebook.com/marketplace/", setOf("facebook.com", "www.facebook.com"), authenticatedCoverageDeviceOnly = true)
    )

    fun byKey(key: String): SiteLearningConfig? = priority.firstOrNull { it.key == key }
}
