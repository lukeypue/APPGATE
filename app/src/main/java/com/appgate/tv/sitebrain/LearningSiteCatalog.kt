package com.appgate.tv.sitebrain

data class LearningSite(
    val key: String,
    val name: String,
    val startUrl: String,
    val expectedHost: String
) {
    fun acceptsHost(actualHost: String): Boolean {
        val host = actualHost.lowercase().removeSuffix(".").removePrefix("www.")
        val expected = expectedHost.lowercase().removePrefix("www.")
        return host == expected || host.endsWith(".$expected")
    }
}

object LearningSiteCatalog {
    fun defaultSites(): List<LearningSite> = listOf(
        LearningSite("facebook_marketplace", "Facebook Marketplace", "https://www.facebook.com/marketplace/", "facebook.com"),
        LearningSite("ksl_classifieds", "KSL Classifieds", "https://classifieds.ksl.com/", "classifieds.ksl.com"),
        LearningSite("ksl_cars", "KSL Cars", "https://cars.ksl.com/", "cars.ksl.com"),
        LearningSite("ebay", "eBay", "https://www.ebay.com/", "ebay.com"),
        LearningSite("craigslist", "Craigslist", "https://www.craigslist.org/", "craigslist.org"),
        LearningSite("offerup", "OfferUp", "https://offerup.com/", "offerup.com"),
        LearningSite("autotrader", "AutoTrader", "https://www.autotrader.com/", "autotrader.com"),
        LearningSite("cars_com", "Cars.com", "https://www.cars.com/", "cars.com"),
        LearningSite("carmax", "CarMax", "https://www.carmax.com/", "carmax.com"),
        LearningSite("carfax", "CARFAX", "https://www.carfax.com/", "carfax.com"),
        LearningSite("carvana", "Carvana", "https://www.carvana.com/", "carvana.com"),
        LearningSite("kbb", "Kelley Blue Book", "https://www.kbb.com/", "kbb.com"),
        LearningSite("carsforsale", "CarsForSale.com", "https://www.carsforsale.com/", "carsforsale.com"),
        LearningSite("privateauto", "PrivateAuto", "https://privateauto.com/", "privateauto.com"),
        LearningSite("truecar", "TrueCar", "https://www.truecar.com/", "truecar.com"),
        LearningSite("cargurus", "CarGurus", "https://www.cargurus.com/", "cargurus.com"),
        LearningSite("edmunds", "Edmunds", "https://www.edmunds.com/", "edmunds.com"),
        LearningSite("autolist", "Autolist", "https://www.autolist.com/", "autolist.com"),
        LearningSite("hemmings", "Hemmings", "https://www.hemmings.com/", "hemmings.com"),
        LearningSite("cars_bids", "Cars & Bids", "https://carsandbids.com/", "carsandbids.com"),
        LearningSite("bringatrailer", "Bring a Trailer", "https://bringatrailer.com/", "bringatrailer.com"),
        LearningSite("bestbuy", "Best Buy", "https://www.bestbuy.com/", "bestbuy.com"),
        LearningSite("walmart", "Walmart", "https://www.walmart.com/", "walmart.com"),
        LearningSite("target", "Target", "https://www.target.com/", "target.com"),
        LearningSite("amazon", "Amazon", "https://www.amazon.com/", "amazon.com"),
        LearningSite("etsy", "Etsy", "https://www.etsy.com/", "etsy.com"),
        LearningSite("homedepot", "Home Depot", "https://www.homedepot.com/", "homedepot.com"),
        LearningSite("lowes", "Lowe's", "https://www.lowes.com/", "lowes.com"),
        LearningSite("newegg", "Newegg", "https://www.newegg.com/", "newegg.com"),
        LearningSite("google", "Google", "https://www.google.com/", "google.com"),
        LearningSite("bing", "Bing", "https://www.bing.com/", "bing.com")
    )
}
