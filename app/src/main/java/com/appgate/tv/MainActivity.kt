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
import android.text.InputType
import android.widget.Toast
import com.appgate.tv.sitebrain.AiTeacherKeyStore
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var queryBox: EditText
    private lateinit var sourceSummary: TextView
    private lateinit var rememberSignIns: CheckBox
    private val sources = SearchCatalog.all()
    private var automaticUpdateCheckStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }.getOrDefault("unknown")
        title = "AI Browser $versionName"

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 18, 28)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
        }

        column.addView(text("AI Browser", 30f, Color.WHITE, true))
        column.addView(text("Site Brain $versionName — persistent website learning, AI Teacher support, overnight screen-off training, watchdog auto-skip, attached JSON logs, and in-app updates that keep the brain's stored knowledge.", 16f, Color.rgb(190, 205, 225)).apply { setPadding(0, 8, 0, 16) })

        column.addView(Button(this).apply {
            text = "START OVERNIGHT LEARNING"
            textSize = 19f
            setOnClickListener { startActivity(Intent(this@MainActivity, OvernightLearningActivity::class.java)) }
        })
        column.addView(text("Training stays on one site at a time. A foreground learning service and wake lock help it continue with the screen off. If there is no useful progress for 30 seconds, the current site is checkpointed and skipped so the run can keep going.", 13f, Color.rgb(155, 215, 175)).apply { setPadding(4, 4, 0, 8) })

        column.addView(text("Facebook Mini Brain is paused for now while we stabilize and improve the main Site Brain. Facebook sign-in can remain saved; no separate Facebook learner needs to be running.", 12f, Color.rgb(155, 215, 175)).apply { setPadding(4, 2, 0, 12) })

        column.addView(Button(this).apply {
            text = "UPDATE AI BROWSER"
            textSize = 17f
            setOnClickListener { startActivity(Intent(this@MainActivity, UpdateActivity::class.java)) }
        })
        column.addView(text("Use this for future versions instead of uninstalling. Android installs the new APK over this app so the Site Brain store, learning checkpoint, cookies and learning logs remain in the app data area.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 2, 0, 14) })

        column.addView(Button(this).apply {
            text = "SAVE LEARNING LOG TO DOWNLOADS"
            textSize = 17f
            setOnClickListener {
                LearningLogExporter.saveToDownloads(this@MainActivity)
                    .onSuccess { name -> Toast.makeText(this@MainActivity, "Saved $name to Downloads.", Toast.LENGTH_LONG).show() }
                    .onFailure { error -> Toast.makeText(this@MainActivity, error.message ?: "Could not save the learning log.", Toast.LENGTH_LONG).show() }
            }
        })
        column.addView(text("After saving, attach the JSON from Downloads directly to the AI Browser project chat. Mapping and verification are separate: a site is only considered trained when learned routes also verify and repeat reliably.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 2, 0, 14) })

        column.addView(CheckBox(this).apply {
            text = "Automatically download verified AI Browser updates"
            setTextColor(Color.WHITE)
            isChecked = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_updates", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("auto_updates", checked).apply()
                if (checked) checkForAutomaticUpdate()
            }
        })
        column.addView(text("When a newer permanently signed build is published, AI Browser can download it automatically. Android still requires its normal Install confirmation for a sideloaded app.", 12f, Color.rgb(150, 175, 200)).apply { setPadding(4, 0, 0, 14) })

        column.addView(Button(this).apply {
            text = if (AiTeacherKeyStore.isConfigured(this@MainActivity)) "AI TEACHER KEY: SET" else "SET AI TEACHER KEY"
            textSize = 17f
            setOnClickListener { showAiTeacherKeyDialog(this) }
        })
        column.addView(text("AI Teacher is only used when the normal Site Brain gets stuck. The key is encrypted on this phone with Android Keystore.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 2, 0, 14) })

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

        column.addView(text("CONNECTED SITES", 19f, Color.WHITE, true).apply { setPadding(0, 12, 0, 6) })
        column.addView(text("You control which private/account sites AI Browser can use. Sign in here yourself; Site Brain never types your password or completes CAPTCHA/2FA.", 12f, Color.rgb(170, 195, 220)).apply { setPadding(4, 0, 0, 8) })
        addSiteConnectButton(column, "Facebook Marketplace", "https://www.facebook.com/marketplace/")
        addSiteConnectButton(column, "OfferUp", "https://offerup.com/")
        addSiteConnectButton(column, "KSL", "https://www.ksl.com/login")
        addSiteConnectButton(column, "TikTok Shop", "https://www.tiktok.com/shop")
        addSiteConnectButton(column, "Instagram", "https://www.instagram.com/")

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

        column.addView(text("What is new in $versionName", 19f, Color.WHITE, true).apply { setPadding(0, 20, 0, 8) })
        column.addView(text("• AI Teacher can be configured directly from the main screen.\n• TEACH ME mode lets you demonstrate difficult safe controls.\n• Safe popup dismissal helps clear blocking ads and overlays.\n• Dropdown/filter learning was expanded for make/model/year-style controls.\n• Learning logs retain the newest 8,000 events and continue rolling forward, plus a separate AI capability-gap log.\n• The updater now checks version numbers and says when you are already up to date.\n• Login, CAPTCHA, 2FA, payment and destructive actions remain human-only.", 14f, Color.rgb(180, 195, 215)))

        root.addView(column)
        setContentView(root)

        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_updates", true)) {
            checkForAutomaticUpdate()
        }
    }

    private fun currentVersionCode(): Long {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull() ?: return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun checkForAutomaticUpdate() {
        if (automaticUpdateCheckStarted) return
        automaticUpdateCheckStarted = true
        val currentCode = currentVersionCode()
        Thread {
            val latestCode = runCatching {
                val connection = URL(UpdateActivity.LATEST_VERSION_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.useCaches = false
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getLong("versionCode")
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (latestCode != null && UpdateVersionPolicy.isUpdateAvailable(currentCode, latestCode)) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        startActivity(Intent(this, UpdateActivity::class.java).apply {
                            putExtra(UpdateActivity.EXTRA_AUTO_DOWNLOAD, true)
                        })
                    }
                }
            }
        }.start()
    }

    private fun showAiTeacherKeyDialog(button: Button) {
        val input = EditText(this).apply {
            hint = "OpenAI API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("AI Teacher")
            .setMessage("Paste your OpenAI API key here. It is encrypted with Android Keystore and stored only on this phone.")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val key = input.text?.toString().orEmpty().trim()
                if (key.isBlank()) return@setPositiveButton
                runCatching { AiTeacherKeyStore.save(this, key) }
                    .onSuccess {
                        button.text = "AI TEACHER KEY: SET"
                        Toast.makeText(this, "AI Teacher key saved.", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(this, "Could not save the key.", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("CANCEL", null)
        if (AiTeacherKeyStore.isConfigured(this)) {
            builder.setNeutralButton("CLEAR KEY") { _, _ ->
                AiTeacherKeyStore.clear(this)
                button.text = "SET AI TEACHER KEY"
                Toast.makeText(this, "AI Teacher key cleared.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
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

    private fun addSiteConnectButton(column: LinearLayout, name: String, url: String) {
        column.addView(Button(this).apply {
            text = "Connect $name"
            setOnClickListener { openSiteConnection(url) }
        })
    }

    private fun openSiteConnection(url: String) {
        startActivity(Intent(this, ListingActivity::class.java).apply {
            putExtra("url", url)
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
            .setMessage("Turn on any dedicated hard-site source you want included in every Deep Search. Normal retailer/web results will come from the later general web aggregation layer.")
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
        sourceSummary.text = if (always.isEmpty()) "Deep Search currently uses the dedicated hard-site sources. General web/retailer results will be added later through the Google/web aggregation layer." else "Always search: ${always.joinToString()}"
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle("How Site Brain Works")
            .setMessage("START OVERNIGHT LEARNING maps only the hard-to-search websites that need dedicated site knowledge rather than wasting training time on normal public web retailers. It trains one site at a time, tests safe controls, verifies state changes, stores successful routes and checkpoints constantly. A 30-second no-progress watchdog skips blocked/stalled sites so unattended runs keep moving.\n\nA foreground service and partial wake lock help learning continue when the screen is off. Android can still impose background limits or kill an app under extreme memory/battery pressure, so the checkpoint is always saved for the next resume.\n\nGoogle/Facebook/Apple login opens in a separate human-only sign-in screen. Site Brain never types your credentials and does not bypass CAPTCHA or 2FA.\n\nThe learning checkpoint, logs and Site Brain knowledge live separately from the APK. Use UPDATE AI BROWSER for future versions instead of uninstalling so that data stays in place.\n\nBuy, Message, Post, Delete, Checkout, payment and account changes are not automated.\n\nShare / Save Logs now attaches the real JSON file so you can send it directly for analysis.")
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
