package com.appgate.tv.sitebrain.trainer

object TrainerSiteCatalog {
    val starterSites: List<TrainerSite> = listOf(
        TrainerSite("ksl_cars", "KSL Cars", "https://cars.ksl.com/", setOf("cars.ksl.com", "www.ksl.com")),
        TrainerSite("facebook_marketplace", "Facebook Marketplace", "https://www.facebook.com/marketplace/", setOf("facebook.com", "www.facebook.com", "m.facebook.com")),
        TrainerSite("ebay", "eBay", "https://www.ebay.com/", setOf("ebay.com", "www.ebay.com")),
        TrainerSite("craigslist", "Craigslist", "https://www.craigslist.org/", setOf("craigslist.org", "www.craigslist.org")),
        TrainerSite("autotrader", "AutoTrader", "https://www.autotrader.com/", setOf("autotrader.com", "www.autotrader.com")),
        TrainerSite("cars_com", "Cars.com", "https://www.cars.com/", setOf("cars.com", "www.cars.com")),
        TrainerSite("cargurus", "CarGurus", "https://www.cargurus.com/", setOf("cargurus.com", "www.cargurus.com")),
        TrainerSite("edmunds", "Edmunds", "https://www.edmunds.com/", setOf("edmunds.com", "www.edmunds.com")),
        TrainerSite("truecar", "TrueCar", "https://www.truecar.com/", setOf("truecar.com", "www.truecar.com")),
        TrainerSite("carmax", "CarMax", "https://www.carmax.com/", setOf("carmax.com", "www.carmax.com")),
        TrainerSite("offerup", "OfferUp", "https://offerup.com/", setOf("offerup.com", "www.offerup.com"))
    )

    private val byId = starterSites.associateBy { it.id }

    fun find(siteId: String): TrainerSite? = byId[siteId]
}
