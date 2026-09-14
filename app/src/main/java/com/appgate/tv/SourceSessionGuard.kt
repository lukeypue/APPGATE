package com.appgate.tv

import java.util.concurrent.atomic.AtomicLong

data class SourceSession(
    val id: Long,
    val sourceKey: String,
    val expectedHostSuffix: String
)

class SourceSessionGuard {
    private val sequence = AtomicLong(0)
    @Volatile private var active: SourceSession? = null

    fun begin(sourceKey: String, expectedHostSuffix: String): SourceSession {
        val session = SourceSession(sequence.incrementAndGet(), sourceKey, expectedHostSuffix.lowercase().removePrefix("www."))
        active = session
        return session
    }

    fun current(): SourceSession? = active

    fun accept(sessionId: Long, actualHost: String): Boolean {
        val session = active ?: return false
        if (session.id != sessionId) return false
        val host = actualHost.lowercase().removeSuffix(".").removePrefix("www.")
        val expected = session.expectedHostSuffix
        return host == expected || host.endsWith(".$expected")
    }
}
