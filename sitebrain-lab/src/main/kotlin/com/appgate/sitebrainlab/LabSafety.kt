package com.appgate.sitebrainlab

enum class LabActionSafety { SAFE, CONSEQUENTIAL, PROTECTED, UNKNOWN }

object LabSafety {
    private val protectedWords = Regex("(?i)\\b(login|log in|sign in|captcha|verify identity|two.factor|2fa|mfa|security)\\b")
    private val consequentialWords = Regex("(?i)\\b(buy|bid|checkout|pay|purchase|message|contact|send|post|publish|upload|delete|remove|follow|subscribe|account|profile)\\b")
    private val safeWords = Regex("(?i)\\b(search|filter|sort|category|results?|details?|next|previous|more|expand|view|cars?|vehicles?)\\b")

    fun classify(label: String, role: String?, href: String?): LabActionSafety {
        val text = listOf(label, role.orEmpty(), href.orEmpty()).joinToString(" ")
        if (protectedWords.containsMatchIn(text)) return LabActionSafety.PROTECTED
        if (consequentialWords.containsMatchIn(text)) return LabActionSafety.CONSEQUENTIAL
        if (safeWords.containsMatchIn(text)) return LabActionSafety.SAFE
        return LabActionSafety.UNKNOWN
    }
}

object PackSanitizer {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("(?<!\\d)(?:\\+?1[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}(?!\\d)")
    private val secrets = Regex("(?i)\\b(cookie|session|token|password|authorization|bearer)\\b[^\\s]*")
    private fun clean(value: String): String = value.replace(email, "[redacted]").replace(phone, "[redacted]").replace(secrets, "[redacted]")

    fun sanitize(pack: SiteKnowledgePack): SiteKnowledgePack = pack.copy(
        nodes = pack.nodes.map { it.copy(route = clean(it.route), summary = clean(it.summary)) },
        transitions = pack.transitions.map { it.copy(label = clean(it.label)) },
        boundaries = pack.boundaries.map { it.copy(route = clean(it.route)) }
    )
}
