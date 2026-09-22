package com.appgate.tv.sitebrain

object SafeActionClassifier {
    private val consequentialSignals = listOf(
        "buy", "bid", "checkout", "pay", "payment", "purchase", "order",
        "message", "contact seller", "send", "post", "publish", "upload",
        "delete", "remove listing", "follow", "subscribe", "account", "profile",
        "security", "accept terms", "agree", "confirm purchase"
    )

    fun classify(element: SemanticElement): SafetyClass {
        if (element.disabled) return SafetyClass.BLOCKED
        val text = normalizedText(element)
        if (consequentialSignals.any { text.contains(it) }) return SafetyClass.CONSEQUENTIAL

        val type = element.inputType?.lowercase().orEmpty()
        if (type in setOf("password", "file")) return SafetyClass.BLOCKED
        if (element.tag.equals("form", true) && text.isBlank()) return SafetyClass.CONSEQUENTIAL
        return SafetyClass.SAFE
    }

    fun inferActionKind(element: SemanticElement): ActionKind {
        val text = normalizedText(element)
        return when {
            element.tag.equals("select", true) -> ActionKind.APPLY_FILTER
            element.role.equals("combobox", true) || element.role.equals("option", true) || element.role.equals("listbox", true) -> ActionKind.APPLY_FILTER
            text.contains("search") || element.inputType?.equals("search", true) == true -> ActionKind.SEARCH
            text.contains("filter") || text.contains("price") || text.contains("mileage") || text.contains("distance") -> ActionKind.APPLY_FILTER
            text.contains("sort") -> ActionKind.SORT
            text == "next" || text.contains("next page") || text.contains("more results") || text.contains("load more") -> ActionKind.PAGINATE
            text.contains("back") -> ActionKind.BACK
            text.contains("login") || text.contains("log in") || text.contains("sign in") -> ActionKind.LOGIN
            text.contains("message") || text.contains("contact") -> ActionKind.MESSAGE
            text.contains("buy") || text.contains("bid") || text.contains("checkout") || text.contains("purchase") -> ActionKind.PURCHASE
            text.contains("delete") || text.contains("remove listing") -> ActionKind.DELETE
            text.contains("post") || text.contains("publish") || text.contains("upload") -> ActionKind.POST
            text.contains("follow") || text.contains("subscribe") -> ActionKind.FOLLOW
            element.role.equals("tab", true) -> ActionKind.OPEN_TAB
            element.href != null && looksLikeDetail(text, element.href) -> ActionKind.OPEN_DETAIL
            element.href != null && looksLikeCategory(text, element.href) -> ActionKind.OPEN_CATEGORY
            element.href != null -> ActionKind.NAVIGATE
            text.contains("details") || text.contains("more") || text.contains("expand") -> ActionKind.EXPAND
            else -> ActionKind.UNKNOWN
        }
    }

    private fun normalizedText(element: SemanticElement): String = listOf(
        element.label,
        element.role.orEmpty(),
        element.nearbyText.orEmpty(),
        element.href.orEmpty()
    ).joinToString(" ").lowercase().replace(Regex("\\s+"), " ").trim()

    private fun looksLikeCategory(text: String, href: String): Boolean {
        val h = href.lowercase()
        return listOf("category", "classified", "rent", "sale", "cars", "vehicles", "vacation", "property").any {
            text.contains(it) || h.contains(it)
        }
    }

    private fun looksLikeDetail(text: String, href: String): Boolean {
        val h = href.lowercase()
        return listOf("listing", "item", "detail", "product", "vehicle").any {
            text.contains(it) || h.contains("/$it/") || h.contains("-$it-")
        }
    }
}
