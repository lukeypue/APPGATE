package com.appgate.sitebrain.lab

import com.appgate.sitebrain.core.*

class FakeBrowserPort(
    private val observationsByUrl: Map<String, Observation>,
    private val transitions: Map<Pair<String, Action>, String>
) : BrowserPort {
    private var currentUrl: String = observationsByUrl.keys.first()
    private val history = mutableListOf<String>()

    override fun navigate(url: String): NavResult {
        if (!observationsByUrl.containsKey(url)) return NavResult(false, currentUrl, "fixture not found")
        if (currentUrl != url) history += currentUrl
        currentUrl = url
        return NavResult(true, currentUrl)
    }

    override fun observe(): Observation = observationsByUrl.getValue(currentUrl)

    override fun act(action: Action): ActResult {
        if (action is Action.Back) {
            if (history.isEmpty()) return ActResult(false, "no history")
            currentUrl = history.removeAt(history.lastIndex)
            return ActResult(true)
        }
        val next = transitions[currentUrl to action] ?: return ActResult(false, "no fixture transition")
        history += currentUrl
        currentUrl = next
        return ActResult(true)
    }

    override fun screenshot(): ByteArray? = null

    override fun isAuthenticated(site: String): Boolean = false
}
