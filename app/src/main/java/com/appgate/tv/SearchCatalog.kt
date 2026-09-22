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
    /**
     * Sources handled by the dedicated Site Brain path today.
     * Facebook Marketplace is temporarily isolated in the Gecko mini-brain
     * pilot instead of being duplicated in the legacy search runner.
     */
    fun all(): List<SearchSource> = listOf(
        SearchSource(
            "KSL Classifieds",
            "ksl_classifieds",
            setOf("shopping", "local", "general"),
            "https://classifieds.ksl.com/search/keyword/{q}",
            expectedHostSuffix = "classifieds.ksl.com"
        ),
        SearchSource(
            "KSL Cars",
            "ksl_cars",
            setOf("vehicles"),
            "https://cars.ksl.com/search/keyword/{q}",
            expectedHostSuffix = "cars.ksl.com"
        ),
        SearchSource(
            "OfferUp",
            "offerup",
            setOf("shopping", "vehicles", "local"),
            "https://offerup.com/search?q={q}",
            expectedHostSuffix = "offerup.com"
        )
    )

    fun expectedHostSuffix(key: String): String? =
        all().firstOrNull { it.key == key }?.expectedHostSuffix
}
