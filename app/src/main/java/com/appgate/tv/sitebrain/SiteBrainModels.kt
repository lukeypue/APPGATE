package com.appgate.tv.sitebrain

enum class PageType {
    HOME,
    SEARCH,
    CATEGORY,
    RESULT_LIST,
    DETAIL,
    FILTER_PANEL,
    PROFILE,
    LOGIN,
    CHALLENGE,
    UNKNOWN
}

enum class ReadinessLevel {
    UNMAPPED,
    LEARNING,
    SEARCH_READY,
    DEEP_SEARCH_READY
}

enum class ActionKind {
    SEARCH,
    NAVIGATE,
    OPEN_CATEGORY,
    APPLY_FILTER,
    SORT,
    PAGINATE,
    EXPAND,
    OPEN_DETAIL,
    OPEN_TAB,
    BACK,
    LOGIN,
    MESSAGE,
    PURCHASE,
    POST,
    DELETE,
    FOLLOW,
    ACCOUNT_CHANGE,
    SUBMIT_FORM,
    UNKNOWN
}

enum class SafetyClass {
    SAFE,
    CONSEQUENTIAL,
    BLOCKED
}

// Semantic effect classes are assigned by perception/classification, never by an LLM.
// COMMIT_EXTERNAL is a hard executor boundary: training can learn that a control exists,
// but it may not dispatch the external side effect.
enum class EffectClass {
    READ,
    NAVIGATE,
    MUTATE_LOCAL,
    COMMIT_EXTERNAL,
    BLOCKED
}

enum class PostconditionKind {
    RESULTS_CHANGED,
    CONSTRAINT_APPLIED,
    PAGE_TYPE_IS,
    DETAIL_MATCHES,
    URL_QUERY_HAS,
    DIALOG_OPENED,
    TEXT_EXPANDED,
    END_OF_RESULTS
}

data class ActionExpectation(
    val kind: PostconditionKind,
    val key: String? = null,
    val value: String? = null,
    val pageType: PageType? = null
)


data class SemanticElement(
    val id: String,
    val tag: String,
    val role: String?,
    val label: String,
    val href: String?,
    val inputType: String?,
    val selected: Boolean,
    val disabled: Boolean,
    val nearbyText: String?,
    val locatorHints: List<String> = emptyList(),
    val currentValue: String? = null,
    val choices: List<String> = emptyList()
)

data class SemanticPageState(
    val host: String,
    val routeSignature: String,
    val pageType: PageType,
    val affordanceRoles: Set<ActionKind>,
    val activeConstraints: Map<String, String>,
    val resultItemKeys: Set<String>,
    val semanticHash: String
)

data class PageSnapshot(
    val url: String,
    val host: String,
    val routeSignature: String,
    val title: String,
    val visibleTextSummary: String,
    val headings: List<String>,
    val elements: List<SemanticElement>,
    val pageType: PageType,
    val loginDetected: Boolean,
    val challengeDetected: Boolean,
    val fingerprint: String,
    val gateReason: String? = null
)

data class SiteNode(
    val fingerprint: String,
    val routeSignature: String,
    val pageType: PageType,
    val title: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val visitCount: Int = 1
)

data class SiteEdge(
    val id: String,
    val fromFingerprint: String,
    val toFingerprint: String?,
    val actionKind: ActionKind,
    val semanticIntent: String,
    val label: String,
    val safetyClass: SafetyClass,
    val locatorHints: List<String>,
    val expectedPageType: PageType?,
    val observedPostcondition: String?,
    val confidence: Double,
    val successCount: Int,
    val failureCount: Int,
    val lastVerifiedAt: Long?
)

data class SiteBrainState(
    val host: String,
    val readiness: ReadinessLevel,
    val nodes: List<SiteNode>,
    val edges: List<SiteEdge>,
    val unresolvedBranches: Int,
    val coverageScore: Double,
    val revision: Int
) {
    companion object {
        fun empty(host: String): SiteBrainState = SiteBrainState(
            host = host,
            readiness = ReadinessLevel.UNMAPPED,
            nodes = emptyList(),
            edges = emptyList(),
            unresolvedBranches = 0,
            coverageScore = 0.0,
            revision = 1
        )
    }
}
