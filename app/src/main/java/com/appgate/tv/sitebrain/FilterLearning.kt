package com.appgate.tv.sitebrain

data class LearnedFilter(
    val name: String,
    val value: String?,
    val locatorHints: List<String>,
    val selected: Boolean,
    val evidence: String
)

object FilterLearning {
    private val filterWords = setOf(
        "price", "mileage", "miles", "make", "model", "year", "condition",
        "distance", "radius", "trim", "body", "transmission", "drive", "fuel",
        "sort", "minimum", "maximum", "min", "max"
    )

    fun discover(snapshot: PageSnapshot): List<LearnedFilter> {
        return snapshot.elements.mapNotNull { element ->
            val evidence = listOf(element.label, element.nearbyText.orEmpty(), element.currentValue.orEmpty())
                .joinToString(" ")
                .lowercase()
            val isFilterControl = element.tag == "select" ||
                element.inputType in setOf("range", "number", "checkbox", "radio") ||
                filterWords.any { word -> evidence.split(Regex("[^a-z0-9.]+")).contains(word) }
            if (!isFilterControl) return@mapNotNull null
            val name = element.label.ifBlank {
                element.nearbyText.orEmpty().take(80).ifBlank { "filter" }
            }
            LearnedFilter(
                name = name,
                value = element.currentValue?.takeIf { it.isNotBlank() },
                locatorHints = element.locatorHints,
                selected = element.selected,
                evidence = evidence.take(220)
            )
        }.distinctBy { it.name.lowercase() + "|" + it.locatorHints.firstOrNull().orEmpty() }
    }

    fun appliedValues(snapshot: PageSnapshot): Map<String, String> =
        discover(snapshot)
            .mapNotNull { f -> f.value?.let { f.name to it } }
            .toMap()
}
