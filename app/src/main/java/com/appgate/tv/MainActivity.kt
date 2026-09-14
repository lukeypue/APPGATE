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
        title = "AI Browser v6.6 Persistent Learning"

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 18, 28)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
        }

        column.addView(text("AI Browser", 30f, Color.WHITE, true))
        column.addView(text("Site Brain v6.6 — one persistent app, a brain that keeps learning, and an Update button that installs newer engines without intentionally wiping what the brain already knows.", 16f, Color.rgb(190, 205, 225)).apply { setPadding(0, 8, 0, 16) })

        column.addView(Button(this).apply {
            text = "START LEARNING"
            textSize = 19f
            setOnClickListener { startActivity(Intent(this@MainActivity, LearningActivity::class.java)) }
        })
        column.addView(text("Training now stays on one site longer, tries recovery when it reaches a shallow plateau, preserves logs across restarts, and lets you Skip Site when a login or broken page blocks progress.", 13f, Color.rgb(155, 215, 175)).apply { setPadding(4, 4, 0, 8) })

        column.addView(Button(this).apply {
            text = "UPDATE AI BROWSER"
            textSize = 17f
            setOnClickListener { startActivity(Intent(this@MainActivity, UpdateActivity::class.java)) }
        })
        column.addView(text("Use this for future versions instead of uninstalling. Android installs the new APK over this app so the Site Brain store, learning checkpoint, cookies and learning logs remain in the app data area.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 2, 0, 14) })

        queryBox = EditText(this).apply {
            hint = "Deep Search: Ford Expedition under 8k under 150k miles with a 3.73 axle"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 165))
            setSingleLine(false)
            minLines = 2
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(30, 39, 55))
        }
        column.addView(queryBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(Button(this).apply {
            text = "Site Brain Deep Search"
            textSize = 17f
            setOnClickListener { startSearch() }
        })

        rememberSignIns = CheckBox(this).apply {
            text = "Remember site sign-ins on this device"
            setTextColor(Color.WHITE)
            isChecked = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("remember_signins", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("remember_signins", checked).apply()
            }
        }
        column.addView(rememberSignIns)
        column.addView(text("Recommended: ON. AI Browser does not save your password; each website's normal WebView cookies keep you signed in. CAPTCHA, 2FA and credentials remain human-only.", 12f, Color.rgb(150, 170, 195)).apply { setPadding(4, 0, 0, 10) })

        column.addView(Button(this).apply {
            text = "Connect Facebook Marketplace (1-time sign-in)"
            setOnClickListener { openFacebookMarketplace() }
        })
        column.addView(text("Do this once before training or searching Marketplace. Sign in to Facebook in the page that opens, then press Back to return here.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 0, 0, 14) })

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

        column.addView(text("What is new in v6.6", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("• Update AI Browser downloads the fixed latest project release and opens Android's installer.\n• Learning remains one site at a time; it no longer abandons a site after the first shallow plateau.\n• Skip Site lets a long training run continue when a site is blocked or needs credentials you do not want to enter.\n• Google/Facebook/Apple OAuth handoffs open in a separate human-only sign-in screen instead of being silently blocked.\n• False login detection is stricter, so a normal page with a Sign In link should not automatically stop training.\n• Site callbacks remain session/host guarded so stale eBay/KSL/etc. events cannot poison another site's map.\n• Learning logs survive normal app restarts and identify themselves as site_brain_learning_run.\n• Consequential actions remain blocked; login, CAPTCHA and 2FA are never automated.", 14f, Color.rgb(180, 195, 215)))

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
            .setMessage("Turn on any site you want included in every Deep Search. Start Learning has its own broad training catalog.")
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
        sourceSummary.text = if (always.isEmpty()) "Deep Search chooses sources automatically. Start Learning trains the broad site catalog." else "Always search: ${always.joinToString()}"
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle("How Site Brain Works")
            .setMessage("START LEARNING maps websites themselves rather than one query. It trains one site at a time, tests safe controls, verifies state changes, stores successful routes, tries recovery when it reaches a shallow plateau, then moves on only after repeated plateau/budget evidence or when you tap Skip Site.\n\nGoogle/Facebook/Apple login opens in a separate human-only sign-in screen. Site Brain never types your credentials and does not bypass CAPTCHA or 2FA.\n\nThe learning checkpoint, logs and Site Brain knowledge live separately from the APK. Use UPDATE AI BROWSER for future versions instead of uninstalling so that data stays in place.\n\nBuy, Message, Post, Delete, Checkout, payment and account changes are not automated.\n\nUse Share Learning Logs after a long run or if a site still gets stuck so the mapper can be improved from evidence.")
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
