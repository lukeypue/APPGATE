package com.appgate.tv

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("appgate", Context.MODE_PRIVATE)

    // ---- Settings ----
    fun cursorSpeed(c: Context): Float = sp(c).getFloat("cursor_speed", 1.0f)
    fun setCursorSpeed(c: Context, v: Float) = sp(c).edit().putFloat("cursor_speed", v).apply()

    fun startupSiteId(c: Context): String? = sp(c).getString("startup_site", null)
    fun setStartupSiteId(c: Context, id: String?) =
        sp(c).edit().putString("startup_site", id).apply()

    // ---- Recents / favorites row (most recently opened first) ----
    fun recents(c: Context): List<String> =
        sp(c).getString("recents", "")!!.split(",").filter { it.isNotBlank() }

    fun touchRecent(c: Context, id: String) {
        val list = recents(c).toMutableList()
        list.remove(id); list.add(0, id)
        sp(c).edit().putString("recents", list.take(5).joinToString(",")).apply()
    }

    // ---- Custom tiles, stored as id|name|url entries ----
    fun customSites(c: Context): List<Site> =
        sp(c).getString("custom", "")!!.split(";;").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("||")
            if (p.size == 3) Site(id = p[0], name = p[1], url = p[2],
                color = 0xFF37474F, mobileUa = true) else null
        }

    fun addCustomSite(c: Context, name: String, url: String) {
        val id = "custom_" + System.currentTimeMillis()
        val fixed = if (url.startsWith("http")) url else "https://$url"
        val entry = "$id||$name||$fixed"
        val cur = sp(c).getString("custom", "")!!
        sp(c).edit().putString("custom", if (cur.isBlank()) entry else "$cur;;$entry").apply()
    }

    fun removeCustomSite(c: Context, id: String) {
        val kept = sp(c).getString("custom", "")!!.split(";;")
            .filter { it.isNotBlank() && !it.startsWith("$id||") }
        sp(c).edit().putString("custom", kept.joinToString(";;")).apply()
    }

    fun allSites(c: Context): List<Site> = SiteCatalog.preloaded + customSites(c)
    fun siteById(c: Context, id: String): Site? = allSites(c).find { it.id == id }
}
