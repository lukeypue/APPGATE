package com.appgate.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_browser_knowledge", Context.MODE_PRIVATE)

    fun getEntries(): List<KnowledgeEntry> = try {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    KnowledgeEntry(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        source = o.optString("source"),
                        content = o.optString("content").take(MAX_CONTENT),
                        updatedAt = o.optLong("updatedAt"),
                        kind = o.optString("kind", "note")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun upsertEntry(entry: KnowledgeEntry) {
        val items = getEntries().toMutableList()
        val safe = entry.copy(content = entry.content.take(MAX_CONTENT))
        val index = items.indexOfFirst { it.id == safe.id }
        if (index >= 0) items[index] = safe else items.add(0, safe)
        prefs.edit().putString(KEY_ENTRIES, entriesToJson(items).toString()).apply()
    }

    fun getSkills(): List<SiteSkill> = try {
        val array = JSONArray(prefs.getString(KEY_SKILLS, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val host = KnowledgeSearch.normalizeHost(o.optString("host"))
                if (host.isNotBlank()) {
                    add(SiteSkill(host, o.optString("instructions"), o.optLong("updatedAt")))
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun upsertSkill(skill: SiteSkill) {
        val host = KnowledgeSearch.normalizeHost(skill.host)
        if (host.isBlank()) return
        val items = getSkills().toMutableList()
        val safe = skill.copy(host = host, instructions = skill.instructions.take(MAX_SKILL))
        val index = items.indexOfFirst { it.host == host }
        if (index >= 0) items[index] = safe else items.add(0, safe)
        prefs.edit().putString(KEY_SKILLS, skillsToJson(items).toString()).apply()
    }

    fun skillForHost(host: String): SiteSkill? {
        val normalized = KnowledgeSearch.normalizeHost(host)
        return getSkills().firstOrNull { it.host == normalized }
    }

    private fun entriesToJson(items: List<KnowledgeEntry>) = JSONArray().apply {
        items.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("source", e.source)
                put("content", e.content.take(MAX_CONTENT))
                put("updatedAt", e.updatedAt)
                put("kind", e.kind)
            })
        }
    }

    private fun skillsToJson(items: List<SiteSkill>) = JSONArray().apply {
        items.forEach { s ->
            put(JSONObject().apply {
                put("host", s.host)
                put("instructions", s.instructions.take(MAX_SKILL))
                put("updatedAt", s.updatedAt)
            })
        }
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val KEY_SKILLS = "skills"
        private const val MAX_CONTENT = 120_000
        private const val MAX_SKILL = 20_000
    }
}
