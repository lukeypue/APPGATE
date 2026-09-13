package com.appgate.tv.sitebrain

object PrioritySiteSeeds {
    fun seedForHost(host: String): SiteBrainState? {
        val normalized = host.lowercase().removePrefix("www.")
        return when {
            normalized == "cars.ksl.com" || normalized.endsWith(".ksl.com") -> ksl(normalized)
            normalized == "facebook.com" || normalized.endsWith(".facebook.com") -> facebookMarketplace(normalized)
            normalized == "ebay.com" || normalized.endsWith(".ebay.com") -> ebay(normalized)
            else -> null
        }
    }

    private fun ksl(host: String) = seededState(
        host,
        listOf(
            seedEdge("ksl-search", ActionKind.SEARCH, "SEARCH_VEHICLES", "Vehicle search", PageType.RESULT_LIST, listOf("route:/search")),
            seedEdge("ksl-make", ActionKind.APPLY_FILTER, "FILTER_MAKE_MODEL", "Make and model", PageType.RESULT_LIST, listOf("concept:make", "concept:model")),
            seedEdge("ksl-price", ActionKind.APPLY_FILTER, "MAX_PRICE_FILTER", "Maximum price", PageType.RESULT_LIST, listOf("concept:max-price")),
            seedEdge("ksl-mileage", ActionKind.APPLY_FILTER, "MAX_MILEAGE_FILTER", "Maximum mileage", PageType.RESULT_LIST, listOf("concept:max-mileage")),
            seedEdge("ksl-detail", ActionKind.OPEN_DETAIL, "OPEN_VEHICLE_DETAIL", "Vehicle listing", PageType.DETAIL, listOf("concept:listing-detail"))
        )
    )

    private fun facebookMarketplace(host: String) = seededState(
        host,
        listOf(
            seedEdge("fb-marketplace", ActionKind.OPEN_CATEGORY, "OPEN_MARKETPLACE", "Marketplace", PageType.CATEGORY, listOf("route:/marketplace")),
            seedEdge("fb-search", ActionKind.SEARCH, "SEARCH_MARKETPLACE", "Marketplace search", PageType.RESULT_LIST, listOf("concept:marketplace-search")),
            seedEdge("fb-category", ActionKind.OPEN_CATEGORY, "OPEN_MARKETPLACE_CATEGORY", "Marketplace category", PageType.CATEGORY, listOf("concept:category")),
            seedEdge("fb-price", ActionKind.APPLY_FILTER, "MAX_PRICE_FILTER", "Maximum price", PageType.RESULT_LIST, listOf("concept:max-price")),
            seedEdge("fb-detail", ActionKind.OPEN_DETAIL, "OPEN_MARKETPLACE_LISTING", "Marketplace listing", PageType.DETAIL, listOf("concept:listing-detail"))
        )
    )

    private fun ebay(host: String) = seededState(
        host,
        listOf(
            seedEdge("ebay-search", ActionKind.SEARCH, "SEARCH_EBAY", "Search eBay", PageType.RESULT_LIST, listOf("route:/sch", "concept:search")),
            seedEdge("ebay-category", ActionKind.OPEN_CATEGORY, "OPEN_EBAY_CATEGORY", "Category", PageType.CATEGORY, listOf("concept:category")),
            seedEdge("ebay-price", ActionKind.APPLY_FILTER, "MAX_PRICE_FILTER", "Maximum price", PageType.RESULT_LIST, listOf("concept:max-price")),
            seedEdge("ebay-condition", ActionKind.APPLY_FILTER, "FILTER_CONDITION", "Condition", PageType.RESULT_LIST, listOf("concept:condition")),
            seedEdge("ebay-detail", ActionKind.OPEN_DETAIL, "OPEN_EBAY_ITEM", "Item listing", PageType.DETAIL, listOf("concept:listing-detail"))
        )
    )

    private fun seededState(host: String, edges: List<SiteEdge>) = SiteBrainState.empty(host).copy(
        readiness = ReadinessLevel.LEARNING,
        edges = edges,
        unresolvedBranches = edges.size,
        coverageScore = 0.05,
        revision = 1
    )

    private fun seedEdge(
        id: String,
        kind: ActionKind,
        intent: String,
        label: String,
        expected: PageType,
        hints: List<String>
    ) = SiteEdge(
        id = id,
        fromFingerprint = "seed",
        toFingerprint = null,
        actionKind = kind,
        semanticIntent = intent,
        label = label,
        safetyClass = SafetyClass.SAFE,
        locatorHints = hints,
        expectedPageType = expected,
        observedPostcondition = null,
        confidence = 0.15,
        successCount = 0,
        failureCount = 0,
        lastVerifiedAt = null
    )
}
