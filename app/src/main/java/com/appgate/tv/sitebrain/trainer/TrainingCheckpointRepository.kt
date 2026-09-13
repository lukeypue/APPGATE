package com.appgate.tv.sitebrain.trainer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

interface TrainingCheckpointStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun keys(): Set<String>
    fun remove(key: String)
}

class SharedPreferencesTrainingCheckpointStore(private val prefs: SharedPreferences) : TrainingCheckpointStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun keys(): Set<String> = prefs.all.keys
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

class TrainingCheckpointRepository(private val store: TrainingCheckpointStore) {
    fun load(siteId: String): TrainerSiteState? {
        val raw = store.get(key(siteId)) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    fun save(state: TrainerSiteState) {
        store.put(key(state.siteId), encode(state.copy(lastRoute = sanitizeRoute(state.lastRoute))).toString())
    }

    fun clear(siteId: String) = store.remove(key(siteId))

    fun loadAll(): List<TrainerSiteState> = store.keys()
        .filter { it.startsWith(PREFIX) }
        .mapNotNull { store.get(it)?.let { raw -> runCatching { decode(raw) }.getOrNull() } }
        .sortedBy { it.siteId }

    private fun encode(s: TrainerSiteState) = JSONObject().apply {
        put("siteId", s.siteId)
        put("status", s.status.name)
        put("verifiedCoverage", s.verifiedCoverage.coerceIn(0, 100))
        put("frontier", JSONArray(s.frontier.map { it.take(160) }))
        put("lastRoute", s.lastRoute)
        put("lastSuccessfulTrainingAt", s.lastSuccessfulTrainingAt)
        put("currentActivity", s.currentActivity.take(240))
        put("humanAttentionReason", s.humanAttentionReason?.take(120))
        put("actionsTakenThisCycle", s.actionsTakenThisCycle.coerceAtLeast(0))
        put("updatedAt", s.updatedAt)
    }

    private fun decode(raw: String): TrainerSiteState {
        val o = JSONObject(raw)
        val frontierArray = o.optJSONArray("frontier") ?: JSONArray()
        return TrainerSiteState(
            siteId = o.getString("siteId"),
            status = enumValueOf(o.optString("status", TrainingStatus.NOT_STARTED.name)),
            verifiedCoverage = o.optInt("verifiedCoverage", 0).coerceIn(0, 100),
            frontier = (0 until frontierArray.length()).map { frontierArray.optString(it) },
            lastRoute = o.optString("lastRoute").takeIf { it.isNotBlank() && it != "null" },
            lastSuccessfulTrainingAt = if (o.isNull("lastSuccessfulTrainingAt")) null else o.optLong("lastSuccessfulTrainingAt"),
            currentActivity = o.optString("currentActivity", "Not started"),
            humanAttentionReason = o.optString("humanAttentionReason").takeIf { it.isNotBlank() && it != "null" },
            actionsTakenThisCycle = o.optInt("actionsTakenThisCycle", 0),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    private fun sanitizeRoute(route: String?): String? {
        if (route.isNullOrBlank()) return route
        val hashless = route.substringBefore('#')
        val path = hashless.substringBefore('?')
        val query = hashless.substringAfter('?', "")
        if (query.isBlank()) return path.take(500)
        val blocked = setOf("token", "access_token", "auth", "authorization", "session", "sessionid", "sid", "password", "code", "state")
        val safe = query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').trim()
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key).lowercase()
            if (blocked.any { decodedKey == it || decodedKey.contains(it) }) null
            else {
                val value = pair.substringAfter('=', "")
                val cleanKey = URLEncoder.encode(runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key), "UTF-8")
                val cleanValue = URLEncoder.encode(runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value), "UTF-8")
                "$cleanKey=$cleanValue"
            }
        }
        return (path + if (safe.isEmpty()) "" else "?${safe.joinToString("&")}").take(500)
    }

    private fun key(siteId: String) = PREFIX + siteId.lowercase()

    companion object { private const val PREFIX = "site_brain_trainer_checkpoint_v1_" }
}
