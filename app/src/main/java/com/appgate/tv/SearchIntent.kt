package com.appgate.tv

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ParsedSearch(
    val raw: String,
    val coreQuery: String,
    val maxPrice: Int?,
    val maxMileage: Int?,
    val requiredTerms: List<String>,
    val conceptTerms: List<String> = emptyList(),
    val hardConstraints: Map<String, String> = emptyMap(),
    val optionalTerms: List<String> = emptyList(),
    val discoveredVocabulary: List<String> = emptyList()
)

object SearchIntentParser {
    const val HARD_LIMITS_DEEP_MARKER = "verify hard limits on full listing"

    private val mileageRegex = Regex("""(?i)\b(?:under|below|less\s+than|max(?:imum)?|up\s+to)\s*([\d,.]+)\s*([kK]?)\s*(?:miles?|mi)\b""")
    private val deepRegex = Regex("""(?i)\b(?:with|must\s+have|including)\s+(?:a\s+|an\s+)?(.+?)\s*$""")
    private val priceRegex = Regex("""(?i)\b(?:under|below|less\s+than|max(?:imum)?(?:\s+price)?|up\s+to)\s*\$?\s*([\d,.]+)\s*([kK]?)\s*(?:dollars?|bucks?)?\b""")
    private val optionalRegex = Regex("""(?i)\b(?:prefer|preferred|ideally|bonus if|nice to have)\s+(.+?)(?:,|;|$)""")
    private val conceptTokenRegex = Regex("""[a-z0-9]+(?:\.[0-9]+)?""")
    private val conceptStopWords = setOf("a", "an", "the", "find", "search", "show", "me", "for", "under", "below", "with", "and", "or", "used", "please")

    fun parse(input: String): ParsedSearch {
        val raw = input.trim()
        var working = raw

        val mileage = mileageRegex.find(working)?.let { parseAmount(it.groupValues[1], it.groupValues[2]) }
        working = mileageRegex.replace(working, " ")

        val required = deepRegex.find(working)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.replace(Regex("""(?i)\s+(?:and|or)\s*$"""), "")
            ?.trim()
            ?.trimEnd('.', ',', ';')
        working = deepRegex.replace(working, " ")

        val optional = optionalRegex.findAll(working).mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }.toList()
        working = optionalRegex.replace(working, " ")

        val price = priceRegex.find(working)?.let { parseAmount(it.groupValues[1], it.groupValues[2]) }
        working = priceRegex.replace(working, " ")

        val core = working
            .replace(Regex("""(?i)^\s*(?:please\s+)?(?:find|search\s+for|look\s+for|show\s+me)\s+(?:me\s+)?(?:a\s+|an\s+|the\s+)?"""), "")
            .replace(Regex("""(?i)\b(?:and|for)\s+(?:dollars?|bucks?)\b"""), " ")
            .replace(Regex("""(?i)\b(?:dollars?|bucks?)\b"""), " ")
            .replace(Regex("""(?i)\s+and\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', '-')
            .ifBlank { raw }

        val concepts = conceptTokenRegex.findAll(core.lowercase())
            .map { it.value }
            .filterNot { it in conceptStopWords || it.length < 2 }
            .distinct()
            .toList()

        val constraints = linkedMapOf<String, String>()
        price?.let { constraints["max_price"] = it.toString() }
        mileage?.let { constraints["max_mileage"] = it.toString() }
        required?.takeIf { it.isNotBlank() }?.let { constraints["detail_required"] = it }

        val requiredTerms = buildList {
            required?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (isEmpty() && (price != null || mileage != null)) add(HARD_LIMITS_DEEP_MARKER)
        }

        return ParsedSearch(
            raw = raw,
            coreQuery = core,
            maxPrice = price,
            maxMileage = mileage,
            requiredTerms = requiredTerms,
            conceptTerms = concepts,
            hardConstraints = constraints,
            optionalTerms = optional,
            discoveredVocabulary = emptyList()
        )
    }

    private fun parseAmount(number: String, suffix: String): Int {
        val base = number.replace(",", "").toDoubleOrNull() ?: 0.0
        return (base * if (suffix.equals("k", true)) 1000.0 else 1.0).toInt()
    }
}

object SearchMatcher {
    private val moneyRegex = Regex("""\$\s*([\d,]+(?:\.\d{1,2})?)""")
    private val milesRegex = Regex("""(?i)([\d,]+)\s*(?:miles?|mi)\b""")
    private val tokenRegex = Regex("""[a-z0-9]+(?:\.[0-9]+)?""")
    private val stopWords = setOf("a", "an", "the", "with", "and", "or", "for", "of", "to", "find", "search", "show", "me")

    fun summaryCouldMatch(summary: String, parsed: ParsedSearch): Boolean {
        val lower = summary.lowercase()
        val coreTokens = tokens(parsed.coreQuery).filterNot { it in stopWords || it.length < 2 }
        if (coreTokens.isNotEmpty() && !coreTokens.all { lower.contains(it) }) return false

        parsed.maxPrice?.let { ceiling ->
            val prices = pricesIn(summary)
            if (prices.isNotEmpty() && prices.minOrNull()!! > ceiling) return false
        }

        parsed.maxMileage?.let { ceiling ->
            val miles = mileageIn(summary)
            if (miles != null && miles > ceiling) return false
        }
        return true
    }

    fun deepTextMatches(text: String, parsed: ParsedSearch): Boolean {
        parsed.maxPrice?.let { ceiling ->
            val prices = pricesIn(text)
            if (prices.isEmpty() || prices.minOrNull()!! > ceiling) return false
        }
        parsed.maxMileage?.let { ceiling ->
            val miles = mileageIn(text) ?: return false
            if (miles > ceiling) return false
        }

        val semanticTerms = parsed.requiredTerms.filterNot { it == SearchIntentParser.HARD_LIMITS_DEEP_MARKER }
        if (semanticTerms.isEmpty()) return true

        val haystack = tokens(text).toSet()
        return semanticTerms.all { term ->
            val needed = tokens(term).filterNot { it in stopWords }
            needed.isNotEmpty() && needed.all { it in haystack }
        }
    }

    private fun pricesIn(text: String): List<Int> = moneyRegex.findAll(text).mapNotNull { match ->
        val after = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 16)).lowercase()
        if (after.contains("/mo") || after.contains("/month") || after.contains("per mo")) null
        else match.groupValues[1].replace(",", "").toDoubleOrNull()?.toInt()
    }.toList()

    private fun mileageIn(text: String): Int? = milesRegex.find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()

    private fun tokens(value: String): List<String> = tokenRegex.findAll(value.lowercase()).map { it.value }.toList()
}

object SearchUrlBuilder {
    private val vehicleMakes = listOf(
        "Acura", "Audi", "BMW", "Buick", "Cadillac", "Chevrolet", "Chevy", "Chrysler", "Dodge",
        "Ford", "GMC", "Honda", "Hyundai", "Infiniti", "Jeep", "Kia", "Land Rover", "Lexus",
        "Lincoln", "Mazda", "Mercedes-Benz", "Mitsubishi", "Nissan", "Porsche", "Ram", "Subaru",
        "Tesla", "Toyota", "Volkswagen", "Volvo"
    )

    fun build(sourceKey: String, template: String, parsed: ParsedSearch): String {
        if (sourceKey == "ksl_cars") return buildKslCars(parsed)

        val encoded = encode(parsed.coreQuery)
        var url = template.replace("{q}", encoded)
        when (sourceKey) {
            "ebay" -> parsed.maxPrice?.let { url += "&_udhi=$it" }
            "craigslist" -> parsed.maxPrice?.let { url += "&max_price=$it" }
            "facebook_marketplace" -> parsed.maxPrice?.let { url += "&maxPrice=$it" }
            "autotrader" -> parsed.maxPrice?.let { url += "&maxPrice=$it" }
            "cars_com" -> parsed.maxPrice?.let { url += "&list_price_max=$it" }
        }
        return url
    }

    private fun buildKslCars(parsed: ParsedSearch): String {
        val query = parsed.coreQuery.trim()
        val make = vehicleMakes.firstOrNull { query.startsWith(it, ignoreCase = true) }
        var url = if (make != null) {
            val model = query.substring(make.length).trim().trim(',', '-', ' ')
            if (model.isNotBlank()) {
                "https://cars.ksl.com/search/make/${encodePath(make)}/model/${encodePath(titleCaseWords(model))}"
            } else {
                "https://cars.ksl.com/search/make/${encodePath(make)}"
            }
        } else {
            "https://cars.ksl.com/search/keyword/${encodePath(query)}"
        }
        parsed.maxPrice?.let { url += "/priceFrom/0/priceTo/$it" }
        parsed.maxMileage?.let { url += "/mileageFrom/0/mileageTo/$it" }
        return url
    }

    private fun titleCaseWords(value: String): String = value.split(Regex("""\s+""")).joinToString(" ") { word ->
        if (word.isBlank()) word else word.lowercase().replaceFirstChar { c -> c.uppercase() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%2B")
}
