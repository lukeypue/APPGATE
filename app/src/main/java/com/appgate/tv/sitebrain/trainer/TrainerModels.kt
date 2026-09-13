package com.appgate.tv.sitebrain.trainer

enum class TrainingStatus {
    NOT_STARTED,
    TRAINING,
    HUMAN_ATTENTION,
    PAUSED,
    TARGET_REACHED,
    ERROR
}

data class TrainerSite(
    val id: String,
    val displayName: String,
    val entryUrl: String,
    val hostAliases: Set<String>
)

data class TrainerSiteState(
    val siteId: String,
    val status: TrainingStatus = TrainingStatus.NOT_STARTED,
    val verifiedCoverage: Int = 0,
    val frontier: List<String> = emptyList(),
    val lastRoute: String? = null,
    val lastSuccessfulTrainingAt: Long? = null,
    val currentActivity: String = "Not started",
    val humanAttentionReason: String? = null,
    val actionsTakenThisCycle: Int = 0,
    val updatedAt: Long = 0L
)
