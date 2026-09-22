package com.appgate.tv.sitebrain.minibrain

data class MiniBrainControl(
    val id: String,
    val label: String,
    val tag: String,
    val role: String?,
    val href: String?
)

enum class MiniBrainIntent {
    SEARCH,
    FILTER,
    SORT,
    CATEGORY,
    RESULT,
    PAGINATE,
    BACK,
    IGNORE
}

data class MiniBrainDecision(
    val intent: MiniBrainIntent,
    val controlId: String?,
    val reason: String
)

interface SiteMiniBrain {
    val key: String
    val displayName: String
    val hostSuffixes: Set<String>

    fun acceptsHost(host: String): Boolean =
        hostSuffixes.any { suffix ->
            val h = host.lowercase().removePrefix("www.")
            val s = suffix.lowercase().removePrefix("www.")
            h == s || h.endsWith(".$s")
        }

    fun trainingQueries(): List<String>
    fun classify(control: MiniBrainControl): MiniBrainIntent
    fun chooseNext(url: String, controls: List<MiniBrainControl>, query: String): MiniBrainDecision?
}
