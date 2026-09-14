package com.appgate.tv

data class SearchSource(
    val name: String,
    val key: String,
    val categories: Set<String>,
    val template: String,
    val loginCommon: Boolean = false,
    val direct: Boolean = true,
    val expectedHostSuffix: String? = null
)

object SearchCatalog {
    fun all(): List<SearchSource> = listOf(
        SearchSource("Facebook Marketplace", "facebook_marketplace", setOf("shopping", "vehicles", "local"), "https://www.facebook.com/marketplace/search/?query={q}", true, expectedHostSuffix = "facebook.com"),
        SearchSource("KSL Classifieds", "ksl_classifieds", setOf("shopping", "local", "general"), "https://classifieds.ksl.com/search/keyword/{q}", expectedHostSuffix = "classifieds.ksl.com"),
        SearchSource("KSL Cars", "ksl_cars", setOf("vehicles"), "https://cars.ksl.com/search/keyword/{q}", expectedHostSuffix = "cars.ksl.com"),
        SearchSource("eBay", "ebay", setOf("shopping", "vehicles", "general"), "https://www.ebay.com/sch/i.html?_nkw={q}", expectedHostSuffix = "ebay.com"),
        SearchSource("Craigslist", "craigslist", setOf("shopping", "vehicles", "local", "jobs", "realestate"), "https://www.craigslist.org/search/sss?query={q}", expectedHostSuffix = "craigslist.org"),
        SearchSource("OfferUp", "offerup", setOf("shopping", "vehicles", "local"), "https://offerup.com/search?q={q}", expectedHostSuffix = "offerup.com"),
        SearchSource("AutoTrader", "autotrader", setOf("vehicles"), "https://www.autotrader.com/cars-for-sale/all-cars?keywordPhrases={q}", expectedHostSuffix = "autotrader.com"),
        SearchSource("Cars.com", "cars_com", setOf("vehicles"), "https://www.cars.com/shopping/results/?keyword={q}", expectedHostSuffix = "cars.com"),
        SearchSource("CarMax", "carmax", setOf("vehicles"), "https://www.carmax.com/cars?search={q}", expectedHostSuffix = "carmax.com"),
        SearchSource("CARFAX", "carfax", setOf("vehicles"), "https://www.google.com/search?q=site%3Acarfax.com%20{q}", direct = false, expectedHostSuffix = "carfax.com"),
        SearchSource("Carvana", "carvana", setOf("vehicles"), "https://www.google.com/search?q=site%3Acarvana.com%20{q}", direct = false, expectedHostSuffix = "carvana.com"),
        SearchSource("Kelley Blue Book", "kbb", setOf("vehicles"), "https://www.google.com/search?q=site%3Akbb.com%20{q}", direct = false, expectedHostSuffix = "kbb.com"),
        SearchSource("CarsForSale.com", "carsforsale", setOf("vehicles"), "https://www.google.com/search?q=site%3Acarsforsale.com%20{q}", direct = false, expectedHostSuffix = "carsforsale.com"),
        SearchSource("PrivateAuto", "privateauto", setOf("vehicles"), "https://www.google.com/search?q=site%3Aprivateauto.com%20{q}", direct = false, expectedHostSuffix = "privateauto.com"),
        SearchSource("TrueCar", "truecar", setOf("vehicles"), "https://www.google.com/search?q=site%3Atruecar.com%20{q}", direct = false, expectedHostSuffix = "truecar.com"),
        SearchSource("CarGurus", "cargurus", setOf("vehicles"), "https://www.google.com/search?q=site%3Acargurus.com%20{q}", direct = false, expectedHostSuffix = "cargurus.com"),
        SearchSource("Edmunds", "edmunds", setOf("vehicles"), "https://www.google.com/search?q=site%3Aedmunds.com%20{q}", direct = false, expectedHostSuffix = "edmunds.com"),
        SearchSource("Autolist", "autolist", setOf("vehicles"), "https://www.google.com/search?q=site%3Aautolist.com%20{q}", direct = false, expectedHostSuffix = "autolist.com"),
        SearchSource("Hemmings", "hemmings", setOf("vehicles"), "https://www.google.com/search?q=site%3Ahemmings.com%20{q}", direct = false, expectedHostSuffix = "hemmings.com"),
        SearchSource("Cars & Bids", "cars_bids", setOf("vehicles"), "https://www.google.com/search?q=site%3Acarsandbids.com%20{q}", direct = false, expectedHostSuffix = "carsandbids.com"),
        SearchSource("Bring a Trailer", "bringatrailer", setOf("vehicles"), "https://www.google.com/search?q=site%3Abringatrailer.com%20{q}", direct = false, expectedHostSuffix = "bringatrailer.com"),
        SearchSource("Best Buy", "bestbuy", setOf("shopping"), "https://www.bestbuy.com/site/searchpage.jsp?id=pcat17071&st={q}", expectedHostSuffix = "bestbuy.com"),
        SearchSource("Walmart", "walmart", setOf("shopping"), "https://www.walmart.com/search?q={q}", expectedHostSuffix = "walmart.com"),
        SearchSource("Target", "target", setOf("shopping"), "https://www.target.com/s?searchTerm={q}", expectedHostSuffix = "target.com"),
        SearchSource("Amazon", "amazon", setOf("shopping"), "https://www.amazon.com/s?k={q}", expectedHostSuffix = "amazon.com"),
        SearchSource("Etsy", "etsy", setOf("shopping"), "https://www.etsy.com/search?q={q}", expectedHostSuffix = "etsy.com"),
        SearchSource("Home Depot", "homedepot", setOf("shopping"), "https://www.homedepot.com/s/{q}", expectedHostSuffix = "homedepot.com"),
        SearchSource("Lowe's", "lowes", setOf("shopping"), "https://www.lowes.com/search?searchTerm={q}", expectedHostSuffix = "lowes.com"),
        SearchSource("Newegg", "newegg", setOf("shopping"), "https://www.newegg.com/p/pl?d={q}", expectedHostSuffix = "newegg.com"),
        SearchSource("Google", "google", setOf("web"), "https://www.google.com/search?q={q}", expectedHostSuffix = "google.com"),
        SearchSource("Bing", "bing", setOf("web"), "https://www.bing.com/search?q={q}", expectedHostSuffix = "bing.com")
    )

    fun expectedHostSuffix(key: String): String? = all().firstOrNull { it.key == key }?.expectedHostSuffix
}
