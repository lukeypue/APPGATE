package com.appgate.tv

object LearningNavigationPolicy {
    private val trustedAuthHosts = setOf(
        "accounts.google.com",
        "accounts.googleusercontent.com",
        "oauth2.googleapis.com",
        "googleapis.com",
        "google.com",
        "oauth.facebook.com",
        "facebook.com",
        "m.facebook.com",
        "appleid.apple.com",
        "auth.offerup.com",
        "login.offerup.com",
        "offerup.com"
    )

    fun shouldAllow(targetHost: String, destinationHost: String, humanAuthWindow: Boolean): Boolean {
        val target = normalize(targetHost)
        val destination = normalize(destinationHost)
        if (destination.isBlank()) return false
        if (destination == target || destination.endsWith(".$target")) return true
        if (!humanAuthWindow) return false
        return trustedAuthHosts.any { trusted ->
            destination == trusted || destination.endsWith(".$trusted")
        }
    }

    fun isKnownAuthHost(destinationHost: String): Boolean {
        val destination = normalize(destinationHost)
        return trustedAuthHosts.any { trusted ->
            destination == trusted || destination.endsWith(".$trusted")
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .removeSuffix(".")
        .removePrefix("www.")
}
