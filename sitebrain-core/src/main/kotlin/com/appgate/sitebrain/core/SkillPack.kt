package com.appgate.sitebrain.core

import org.json.JSONArray
import org.json.JSONObject

data class PageTypeRecord(
    val id: String,
    val name: String,
    val structuralHash: String,
    val landmarks: List<String>,
    val confidence: Double
)

data class ControlRecord(
    val id: String,
    val pageTypeId: String,
    val role: String,
    val label: String,
    val locators: List<String>,
    val safetyClass: String,
    val confidence: Double
)

data class SkillRecord(
    val id: String,
    val name: String,
    val paramsSchemaJson: String,
    val steps: List<String>,
    val verify: List<String>,
    val confidence: Double
)

data class RouteRecord(
    val id: String,
    val fromPageType: String,
    val toPageType: String,
    val steps: List<String>,
    val hops: Int,
    val costMsEstimate: Long,
    val confidence: Double
)

data class EvidenceRecord(
    val id: String,
    val url: String,
    val domHash: String,
    val screenshotHash: String,
    val timestamp: Long
)

data class SiteSkillPack(
    val schemaVersion: Int,
    val domain: String,
    val packVersion: String,
    val createdAt: Long,
    val lastVerifiedAt: Long,
    val pageTypes: List<PageTypeRecord> = emptyList(),
    val controls: List<ControlRecord> = emptyList(),
    val skills: List<SkillRecord> = emptyList(),
    val routes: List<RouteRecord> = emptyList(),
    val evidence: List<EvidenceRecord> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

object SkillPackCodec {
    fun encode(pack: SiteSkillPack): String {
        val root = JSONObject()
        root.put("schemaVersion", pack.schemaVersion)
        root.put("domain", pack.domain)
        root.put("packVersion", pack.packVersion)
        root.put("createdAt", pack.createdAt)
        root.put("lastVerifiedAt", pack.lastVerifiedAt)

        root.put("pageTypes", JSONArray().apply {
            pack.pageTypes.sortedBy { it.id }.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("structuralHash", p.structuralHash)
                    put("landmarks", JSONArray(p.landmarks.sorted()))
                    put("confidence", p.confidence)
                })
            }
        })

        root.put("controls", JSONArray().apply {
            pack.controls.sortedBy { it.id }.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("pageTypeId", c.pageTypeId)
                    put("role", c.role)
                    put("label", c.label)
                    put("locators", JSONArray(c.locators))
                    put("safetyClass", c.safetyClass)
                    put("confidence", c.confidence)
                })
            }
        })

        root.put("skills", JSONArray().apply {
            pack.skills.sortedBy { it.id }.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("paramsSchemaJson", s.paramsSchemaJson)
                    put("steps", JSONArray(s.steps))
                    put("verify", JSONArray(s.verify))
                    put("confidence", s.confidence)
                })
            }
        })

        root.put("routes", JSONArray().apply {
            pack.routes.sortedBy { it.id }.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("fromPageType", r.fromPageType)
                    put("toPageType", r.toPageType)
                    put("steps", JSONArray(r.steps))
                    put("hops", r.hops)
                    put("costMsEstimate", r.costMsEstimate)
                    put("confidence", r.confidence)
                })
            }
        })

        root.put("evidence", JSONArray().apply {
            pack.evidence.sortedBy { it.id }.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("url", e.url)
                    put("domHash", e.domHash)
                    put("screenshotHash", e.screenshotHash)
                    put("timestamp", e.timestamp)
                })
            }
        })

        root.put("metadata", JSONObject().apply {
            pack.metadata.toSortedMap().forEach { (k, v) -> put(k, v) }
        })

        return root.toString()
    }

    fun decode(json: String): SiteSkillPack {
        val root = JSONObject(json)
        return SiteSkillPack(
            schemaVersion = root.getInt("schemaVersion"),
            domain = root.getString("domain"),
            packVersion = root.getString("packVersion"),
            createdAt = root.getLong("createdAt"),
            lastVerifiedAt = root.getLong("lastVerifiedAt"),
            pageTypes = root.getJSONArray("pageTypes").toList { o ->
                PageTypeRecord(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    structuralHash = o.getString("structuralHash"),
                    landmarks = o.getJSONArray("landmarks").toStringList(),
                    confidence = o.getDouble("confidence")
                )
            },
            controls = root.getJSONArray("controls").toList { o ->
                ControlRecord(
                    id = o.getString("id"),
                    pageTypeId = o.getString("pageTypeId"),
                    role = o.getString("role"),
                    label = o.getString("label"),
                    locators = o.getJSONArray("locators").toStringList(),
                    safetyClass = o.getString("safetyClass"),
                    confidence = o.getDouble("confidence")
                )
            },
            skills = root.getJSONArray("skills").toList { o ->
                SkillRecord(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    paramsSchemaJson = o.getString("paramsSchemaJson"),
                    steps = o.getJSONArray("steps").toStringList(),
                    verify = o.getJSONArray("verify").toStringList(),
                    confidence = o.getDouble("confidence")
                )
            },
            routes = root.getJSONArray("routes").toList { o ->
                RouteRecord(
                    id = o.getString("id"),
                    fromPageType = o.getString("fromPageType"),
                    toPageType = o.getString("toPageType"),
                    steps = o.getJSONArray("steps").toStringList(),
                    hops = o.getInt("hops"),
                    costMsEstimate = o.getLong("costMsEstimate"),
                    confidence = o.getDouble("confidence")
                )
            },
            evidence = root.getJSONArray("evidence").toList { o ->
                EvidenceRecord(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    domHash = o.getString("domHash"),
                    screenshotHash = o.getString("screenshotHash"),
                    timestamp = o.getLong("timestamp")
                )
            },
            metadata = root.getJSONObject("metadata").let { obj ->
                obj.keys().asSequence().toList().sorted().associateWith { key -> obj.getString(key) }
            }
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    private inline fun <T> JSONArray.toList(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }
}
