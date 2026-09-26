package com.appgate.tv.sitebrain.minibrain

class FacebookMarketplaceMiniBrain : SiteMiniBrain {
    override val key = "facebook_marketplace"
    override val displayName = "Facebook Marketplace"
    override val hostSuffixes = setOf("facebook.com")

    override fun trainingQueries(): List<String> = listOf(
        "Ford Expedition",
        "Toyota Tacoma",
        "SUV under 8000",
        "truck under 150000 miles",
        "patio furniture",
        "cordless drill",
        "mountain bike"
    )

    override fun classify(control: MiniBrainControl): MiniBrainIntent {
        val label = control.label.lowercase()
        val href = control.href.orEmpty().lowercase()
        return when {
            label.contains("search marketplace") || label == "search" -> MiniBrainIntent.SEARCH
            label.contains("filter") || label.contains("price") || label.contains("mileage") ||
                label.contains("year") || label.contains("make") || label.contains("model") ||
                label.contains("location") -> MiniBrainIntent.FILTER
            label.contains("sort") -> MiniBrainIntent.SORT
            href.contains("/marketplace/item/") -> MiniBrainIntent.RESULT
            href.contains("/marketplace/category") || href.contains("/marketplace/vehicles") ->
                MiniBrainIntent.CATEGORY
            label.contains("next") || label.contains("see more") -> MiniBrainIntent.PAGINATE
            label.contains("log in") || label.contains("sign up") || label.contains("message") ||
                label.contains("buy") || label.contains("checkout") || label.contains("contact seller") ->
                MiniBrainIntent.IGNORE
            else -> MiniBrainIntent.IGNORE
        }
    }

    override fun chooseNext(
        url: String,
        controls: List<MiniBrainControl>,
        query: String
    ): MiniBrainDecision? {
        val scored = controls.map { it to classify(it) }

        scored.firstOrNull { it.second == MiniBrainIntent.SEARCH }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.SEARCH,
                c.id,
                "Marketplace search is the highest-value reusable control for '$query'"
            )
        }

        scored.firstOrNull { it.second == MiniBrainIntent.FILTER }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.FILTER,
                c.id,
                "Learn Marketplace filters before exploring individual listings"
            )
        }

        scored.firstOrNull { it.second == MiniBrainIntent.CATEGORY }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.CATEGORY,
                c.id,
                "Explore a Marketplace category branch"
            )
        }

        scored.firstOrNull { it.second == MiniBrainIntent.RESULT }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.RESULT,
                c.id,
                "Open one safe listing to learn the detail-page structure"
            )
        }

        scored.firstOrNull { it.second == MiniBrainIntent.SORT }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.SORT,
                c.id,
                "Learn Marketplace sort controls after search, filters, categories, and results"
            )
        }

        scored.firstOrNull { it.second == MiniBrainIntent.PAGINATE }?.let { (c, _) ->
            return MiniBrainDecision(
                MiniBrainIntent.PAGINATE,
                c.id,
                "Learn safe result pagination after higher-value Marketplace controls"
            )
        }

        return null
    }
}
