package com.appgate.sitebrainlab

interface BrowserAdapter {
    fun observe(): LabObservation
    fun executeSafe(action: FrontierAction): Boolean
}

abstract class GuardedBrowserAdapter : BrowserAdapter {
    final override fun executeSafe(action: FrontierAction): Boolean {
        require(action.control.safety == LabActionSafety.SAFE) { "Only SAFE actions may execute automatically" }
        return executeVerifiedSafe(action)
    }
    protected abstract fun executeVerifiedSafe(action: FrontierAction): Boolean
}
