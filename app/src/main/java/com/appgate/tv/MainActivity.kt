package com.appgate.tv

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var queryBox: EditText
    private lateinit var sourceSummary: TextView
    private lateinit var rememberSignIns: CheckBox
    private val sources = SearchCatalog.all()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI Browser v5.1 Live Explorer"

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 18, 28)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
        }

        column.addView(text("AI Browser", 30f, Color.WHITE, true))
        column.addView(text("Site Brain v5.1 — search once while AI Browser learns how websites are organized, safely tests useful paths, verifies what changed, and remembers what worked.", 16f, Color.rgb(190, 205, 225)).apply { setPadding(0, 8, 0, 20) })

        queryBox = EditText(this).apply {
            hint = "Try: expedition under 8k with a 3.73 axle"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 165))
            setSingleLine(false)
            minLines = 2
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(30, 39, 55))
        }
        column.addView(queryBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val searchButton = Button(this).apply {
            text = "Site Brain Deep Search"
            textSize = 17f
            setOnClickListener { startSearch() }
        }
        column.addView(searchButton)

        rememberSignIns = CheckBox(this).apply {
            text = "Remember site sign-ins on this device"
            setTextColor(Color.WHITE)
            isChecked = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("remember_signins", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("remember_signins", checked).apply()
            }
        }
        column.addView(rememberSignIns)
        column.addView(text("Recommended: ON. AI Browser does not save your password; the website's normal WebView cookies keep you signed in. Security checks are still completed by you.", 12f, Color.rgb(150, 170, 195)).apply { setPadding(4, 0, 0, 12) })

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

        column.addView(text("What is new in v5.1", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("• Site Brain now tests one bounded safe path after a source search instead of only observing the page.\n• It compares the page before and after the action and only promotes a path when a real state change is verified.\n• KSL Cars, Facebook Marketplace and eBay start with low-confidence semantic seed knowledge, then live exploration verifies what still works.\n• Buy, message, post, delete, checkout, payment and account-changing controls remain off-limits to automatic exploration.\n• CAPTCHA, login and security pages never count as successful learned paths.\n• Generic card scraping is labeled Possible Match; deep-read evidence is required before the app calls a result Verified.", 14f, Color.rgb(180, 195, 215)))

        column.addView(text("Vehicle sources in this test", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("KSL Cars • Facebook Marketplace • Craigslist • eBay • OfferUp • AutoTrader • Cars.com • CarMax • TrueCar • CarGurus • Edmunds • Autolist • Hemmings • Cars & Bids • Bring a Trailer • Google • Bing\n\nWhen you add a hard limit such as 'under 8k', AI Browser filters the site where possible AND rejects cards above that price. A phrase after 'with' or 'must have' becomes a deep-description requirement, so the browser can open promising listings and look for details such as axle ratio.", 14f, Color.rgb(180, 195, 215)))

        root.addView(column)
        setContentView(root)
    }

    private fun startSearch() {
        val raw = queryBox.text.toString().trim()
        if (raw.isBlank()) {
            queryBox.error = "Tell AI Browser what you want to find"
            return
        }
        val parsed = SearchIntentParser.parse(raw)
        val selected = route(raw)
        val names = ArrayList<String>()
        val keys = ArrayList<String>()
        val urls = ArrayList<String>()
        for (s in selected) {
            names.add(s.name)
            keys.add(s.key)
            urls.add(SearchUrlBuilder.build(s.key, s.template, parsed))
        }
        startActivity(Intent(this, BrowserActivity::class.java).apply {
            putExtra("query", raw)
            putExtra("rememberSignIns", rememberSignIns.isChecked)
            putStringArrayListExtra("sourceNames", names)
            putStringArrayListExtra("sourceKeys", keys)
            putStringArrayListExtra("sourceUrls", urls)
        })
    }

    private fun route(query: String): List<SearchSource> {
        val q = query.lowercase()
        val category = when {
            listOf("car", "truck", "suv", "vehicle", "ford", "toyota", "honda", "chevy", "expedition", "tacoma", "axle", "mileage").any { q.contains(it) } -> "vehicles"
            listOf("job", "hiring", "career", "work from home").any { q.contains(it) } -> "jobs"
            listOf("house", "apartment", "rent", "real estate", "home for sale", "vacation", "timeshare").any { q.contains(it) } -> "realestate"
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
            .setMessage("Turn on any site you want included in every search. This is useful for regional sites like KSL even when you are outside Utah.")
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
            .setTitle("How Site Brain Works")
            .setMessage("1. Search once. AI Browser chooses useful marketplaces and specialty sites automatically.\n\n2. While each page is open, Site Brain records a privacy-safe semantic map: page type, headings, categories, search/filter controls, result links and navigation relationships.\n\n3. After reading the search page, v5.1 may test a bounded safe action such as a category, filter, pagination or read-only navigation path. It compares the page before and after and remembers the path only when the result is verified.\n\n4. Consequential controls such as Buy, Message, Post, Delete, Checkout, payment and account changes are never used during automatic exploration.\n\n5. Hard limits such as price and mileage are sent to a site's filters when we know how, then checked again before a result is shown.\n\n6. Details usually buried inside a listing — for example '3.73 axle', 'no rust', or a specific option — trigger Deep Search.\n\n7. Some sites, especially Facebook Marketplace, need you to sign in. With Remember Sign-ins ON, the site's normal WebView cookies stay on this device. Site Brain knowledge never stores your password or cookies.\n\n8. CAPTCHA/security checks stay human. Site Brain pauses at that boundary instead of trying to defeat it.")
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
