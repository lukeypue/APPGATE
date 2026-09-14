package com.appgate.tv.sitebrain

class LearningProgressTracker(
    private val siteCount: Int,
    savedSiteIndex: Int = 0,
    savedVerified: Int = 0,
    savedPasses: Int = 0
) {
    var siteIndex: Int = if (siteCount <= 0) 0 else savedSiteIndex.coerceIn(0, siteCount - 1)
        private set
    var verifiedDiscoveries: Int = savedVerified.coerceAtLeast(0)
        private set
    var completedPasses: Int = savedPasses.coerceAtLeast(0)
        private set

    fun recordVerified() { verifiedDiscoveries++ }

    fun nextSite() {
        if (siteCount <= 0) return
        siteIndex++
        if (siteIndex >= siteCount) {
            siteIndex = 0
            completedPasses++
        }
    }

    fun snapshot(): String = "{\"siteIndex\":$siteIndex,\"verifiedDiscoveries\":$verifiedDiscoveries,\"completedPasses\":$completedPasses}"
}
