package com.appgate.sitebrain.core

data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class InteractiveElement(
    val ref: String,
    val role: String,
    val label: String,
    val value: String? = null,
    val href: String? = null,
    val bounds: Rect? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class Observation(
    val url: String,
    val host: String,
    val title: String,
    val pageType: String,
    val routeSignature: String,
    val elements: List<InteractiveElement> = emptyList(),
    val headings: List<String> = emptyList(),
    val challengeDetected: Boolean = false,
    val loginDetected: Boolean = false,
    val structuralHash: String = "",
    val metadata: Map<String, String> = emptyMap()
)

sealed class Action {
    data class Navigate(val url: String) : Action()
    data class Click(val ref: String) : Action()
    data class Type(val ref: String, val text: String) : Action()
    data class Select(val ref: String, val value: String) : Action()
    data class Scroll(val dx: Int, val dy: Int) : Action()
    data object Back : Action()
    data class Wait(val condition: String, val maxMs: Long) : Action()
}

data class NavResult(
    val accepted: Boolean,
    val finalUrl: String? = null,
    val message: String = ""
)

data class ActResult(
    val accepted: Boolean,
    val message: String = ""
)

enum class BoundaryKind {
    LOGIN,
    CAPTCHA,
    TWO_FACTOR,
    PAYWALL,
    PROTECTED_ACTION,
    UNKNOWN
}

data class Boundary(
    val kind: BoundaryKind,
    val site: String,
    val message: String = ""
)

interface BrainHost {
    fun humanNeeded(boundary: Boundary)
    fun telemetry(event: String, detail: Map<String, String> = emptyMap())
}

interface BrowserPort {
    fun navigate(url: String): NavResult
    fun observe(): Observation
    fun act(action: Action): ActResult
    fun screenshot(): ByteArray?
    fun isAuthenticated(site: String): Boolean
}
