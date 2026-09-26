package com.appgate.tv

import org.mozilla.geckoview.GeckoSession

object AuthenticatedGeckoSessionStore {
    @Volatile private var session: GeckoSession? = null
    @Volatile private var host: String = ""

    @Synchronized
    fun retain(targetHost: String, authenticatedSession: GeckoSession) {
        if (session !== authenticatedSession) runCatching { session?.close() }
        host = targetHost.lowercase()
        session = authenticatedSession
    }

    @Synchronized
    fun takeFor(targetHost: String): GeckoSession? {
        if (host != targetHost.lowercase()) return null
        return session.also {
            session = null
            host = ""
        }
    }

    @Synchronized
    fun hasSessionFor(targetHost: String): Boolean =
        session != null && host == targetHost.lowercase()

    @Synchronized
    fun clear() {
        runCatching { session?.close() }
        session = null
        host = ""
    }
}
