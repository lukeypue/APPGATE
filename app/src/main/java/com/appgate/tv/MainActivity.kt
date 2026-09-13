package com.appgate.tv

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SearchSource(val name: String, val key: String, val categories: Set<String>, val template: String, val loginCommon: Boolean = false)

class MainActivity : AppCompatActivity() {
    private lateinit var queryBox: EditText
    private lateinit var sourceSummary: TextView

    private val sources = listOf(
        SearchSource("KSL Classifieds", "ksl_classifieds", setOf("shopping", "local", "general"), "https://classifieds.ksl.com/search/keyword/{q}"),
        SearchSource("KSL Cars", "ksl_cars", setOf("vehicles"), "https://cars.ksl.com/search/keyword/{q}"),
        SearchSource("Facebook Marketplace", "facebook_marketplace", setOf("shopping", "vehicles", "local"), "https://www.facebook.com/marketplace/search/?query={q}", true),
        SearchSource("eBay", "ebay", setOf("shopping", "vehicles", "general"), "https://www.ebay.com/sch/i.html?_nkw={q}"),
        SearchSource("Craigslist", "craigslist", setOf("shopping", "vehicles", "local", "jobs", "realestate"), "https://www.craigslist.org/search/sss?query={q}"),
        SearchSource("Best Buy", "bestbuy", setOf("shopping"), "https://www.bestbuy.com/site/searchpage.jsp?id=pcat17071&st={q}"),
        SearchSource("Walmart", "walmart", setOf("shopping"), "https://www.walmart.com/search?q={q}"),
        SearchSource("Google", "google", setOf("web"), "https://www.google.com/search?q={q}"),
        SearchSource("Bing", "bing", setOf("web"), "https://www.bing.com/search?q={q}")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI Browser v4"

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 18, 28)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
        }

        column.addView(text("AI Browser", 30f, Color.WHITE, true))
        column.addView(text("Search once. AI Browser searches marketplaces, specialty sites and the web for you.", 16f, Color.rgb(190, 205, 225)).apply { setPadding(0, 8, 0, 20) })

        queryBox = EditText(this).apply {
            hint = "Try: gaming computer under $800"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 165))
            setSingleLine(false)
            minLines = 2
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(30, 39, 55))
        }
        column.addView(queryBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val searchButton = Button(this).apply {
            text = "Search All + Combine"
            textSize = 17f
            setOnClickListener { startSearch() }
        }
        column.addView(searchButton)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "My Sources"
            setOnClickListener { showSources() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "How It Works"
            setOnClickListener { showInfo() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(row)

        sourceSummary = text("", 14f, Color.rgb(160, 180, 205))
        sourceSummary.setPadding(0, 18, 0, 10)
        column.addView(sourceSummary)
        refreshSummary()

        column.addView(text("First AI-ready sources", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("KSL Classifieds • KSL Cars • Facebook Marketplace • eBay • Craigslist • Best Buy • Walmart • Google • Bing\n\nSites may require login or human verification. AI Browser pauses that source, lets you complete the check, then continues. Other sources keep working.", 14f, Color.rgb(180, 195, 215)))

        root.addView(column)
        setContentView(root)
    }

    private fun startSearch() {
        val raw = queryBox.text.toString().trim()
        if (raw.isBlank()) {
            queryBox.error = "Tell AI Browser what you want to find"
            return
        }
        val selected = route(raw)
        val names = ArrayList<String>()
        val urls = ArrayList<String>()
        for (s in selected) {
            names.add(s.name)
            val encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")
            urls.add(s.template.replace("{q}", encoded))
        }
        startActivity(Intent(this, BrowserActivity::class.java).apply {
            putExtra("query", raw)
            putStringArrayListExtra("sourceNames", names)
            putStringArrayListExtra("sourceUrls", urls)
        })
    }

    private fun route(query: String): List<SearchSource> {
        val q = query.lowercase()
        val category = when {
            listOf("car", "truck", "suv", "vehicle", "ford", "toyota", "honda", "chevy", "expedition", "tacoma").any { q.contains(it) } -> "vehicles"
            listOf("job", "hiring", "career", "work from home").any { q.contains(it) } -> "jobs"
            listOf("house", "apartment", "rent", "real estate", "home for sale").any { q.contains(it) } -> "realestate"
            else -> "shopping"
        }
        val prefs = getSharedPreferences("sources", MODE_PRIVATE)
        val result = LinkedHashSet<SearchSource>()
        sources.filter { prefs.getBoolean("always_${it.key}", false) }.forEach { result.add(it) }
        sources.filter { category in it.categories }.forEach { result.add(it) }
        sources.filter { "web" in it.categories }.forEach { result.add(it) }
        return result.toList()
    }

    private fun showSources() {
        val prefs = getSharedPreferences("sources", MODE_PRIVATE)
        val labels = sources.map { it.name }.toTypedArray()
        val checked = BooleanArray(sources.size) { i -> prefs.getBoolean("always_${sources[i].key}", false) }
        AlertDialog.Builder(this)
            .setTitle("Always Search These Sources")
            .setMessage("Turn on any site you want included in every search, no matter where you live or what category AI Browser detects.")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Save") { _, _ ->
                val e = prefs.edit()
                sources.forEachIndexed { i, s -> e.putBoolean("always_${s.key}", checked[i]) }
                e.apply()
                refreshSummary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshSummary() {
        val prefs = getSharedPreferences("sources", MODE_PRIVATE)
        val always = sources.filter { prefs.getBoolean("always_${it.key}", false) }.map { it.name }
        sourceSummary.text = if (always.isEmpty()) "AI chooses sources automatically. You can also pin favorite sources." else "Always search: ${always.joinToString()}"
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle("How AI Browser Works")
            .setMessage("1. Search once.\n\n2. AI Browser decides what kind of search it is and chooses useful sites automatically.\n\n3. It visits those sites, reads public result links, and combines what it can find.\n\n4. Some sites need you to sign in once. Cookies stay in the browser so the session can remain available.\n\n5. If a site shows CAPTCHA or another security check, AI Browser pauses for you. It does not defeat the security check. After you finish, tap Resume and it continues.\n\n6. Websites change. Failed sources are shown clearly so future versions can repair their site skill.\n\n7. Use My Sources to force a favorite site, such as KSL, into every search even outside its normal region/category.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
