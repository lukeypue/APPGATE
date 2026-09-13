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
        title = "AI Browser v6.1 Deep Search"

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 18, 28)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
        }

        column.addView(text("AI Browser", 30f, Color.WHITE, true))
        column.addView(text("Site Brain v6.1 — search many websites, open promising listings, verify hard limits from full listing pages, and remember what each site taught us.", 16f, Color.rgb(190, 205, 225)).apply { setPadding(0, 8, 0, 20) })

        queryBox = EditText(this).apply {
            hint = "Try: Ford Expedition under 8k under 150k miles with a 3.73 axle"
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
        column.addView(text("Recommended: ON. AI Browser does not save your password; the website's normal WebView cookies keep you signed in. Security checks are still completed by you.", 12f, Color.rgb(150, 170, 195)).apply { setPadding(4, 0, 0, 10) })

        column.addView(Button(this).apply {
            text = "Connect Facebook Marketplace (1-time sign-in)"
            setOnClickListener { openFacebookMarketplace() }
        })
        column.addView(text("Do this once before your first Marketplace search. Sign in to Facebook in the page that opens, then press Back to return here. With Remember sign-ins ON, the normal Facebook session stays on this device for later searches.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 0, 0, 14) })

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

        column.addView(text("What is new in v6.1", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("• Price and mileage no longer have to be visible on a small result card. A plausible listing can be opened and verified from its full page.\n• Hard limits such as under $8,000 or under 150k miles now trigger full-listing verification even when you did not include a description phrase.\n• Facebook Marketplace has a dedicated one-time sign-in button so Facebook cannot silently disappear from a search just because its login wording changes.\n• Remember sign-ins keeps the website's normal session cookies on this device; AI Browser never stores your Facebook password itself.\n• CAPTCHA and account security checks remain human-only.\n• Site Brain still learns only read-only, safe website paths; buy, message, post, delete, checkout, payment and account-changing controls remain off-limits to automatic exploration.", 14f, Color.rgb(180, 195, 215)))

        column.addView(text("Vehicle sources in this test", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("KSL Cars • Facebook Marketplace • Craigslist • eBay • OfferUp • AutoTrader • Cars.com • CarMax • CARFAX • Carvana • Kelley Blue Book • CarsForSale.com • PrivateAuto • TrueCar • CarGurus • Edmunds • Autolist • Hemmings • Cars & Bids • Bring a Trailer • Google • Bing\n\nHard limits are applied to site filters when supported, rejected immediately when a card clearly violates them, and otherwise verified from the full listing page. Details after 'with' or 'must have' are also checked in the listing text.", 14f, Color.rgb(180, 195, 215)))

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

    private fun openFacebookMarketplace() {
        startActivity(Intent(this, ListingActivity::class.java).apply {
            putExtra("url", "https://www.facebook.com/marketplace/")
            putExtra("rememberSignIns", rememberSignIns.isChecked)
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
            .setMessage("1. Search once. AI Browser chooses useful marketplaces and specialty sites automatically.\n\n2. Before your first Marketplace search, use Connect Facebook Marketplace once and complete Facebook's normal login yourself.\n\n3. While each page is open, Site Brain records a privacy-safe semantic map: page type, headings, categories, search/filter controls, result links and navigation relationships.\n\n4. Hard limits such as price and mileage are sent to a site's filters when we know how. If a result card omits a hard field, AI Browser can open the full listing and verify it there instead of throwing the listing away.\n\n5. Details usually buried inside a listing — for example '3.73 axle', 'no rust', or a specific option — are also checked on the full listing page.\n\n6. Consequential controls such as Buy, Message, Post, Delete, Checkout, payment and account changes are never used during automatic exploration.\n\n7. With Remember Sign-ins ON, websites keep their normal WebView cookies on this device. Site Brain knowledge never stores your password or cookies.\n\n8. CAPTCHA/security checks stay human. Site Brain pauses at that boundary instead of trying to defeat it.")
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
