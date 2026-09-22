package com.appgate.tv.sitebrain.minibrain

object MiniBrainRegistry {
    private val brains: List<SiteMiniBrain> = listOf(
        FacebookMarketplaceMiniBrain()
    )

    fun forKey(key: String): SiteMiniBrain? =
        brains.firstOrNull { it.key.equals(key, ignoreCase = true) }

    fun forHost(host: String): SiteMiniBrain? =
        brains.firstOrNull { it.acceptsHost(host) }

    fun all(): List<SiteMiniBrain> = brains
}
