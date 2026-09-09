package com.appgate.tv

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var store: KnowledgeStore
    private lateinit var eventLog: AppEventLog
    private val handler = Handler(Looper.getMainLooper())
    private var lastSkillHostShown: String? = null
    private var pendingExportJson: String? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importTextDocument(uri)
    }

    private val createTestLog = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeTestLog(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = KnowledgeStore(this)
        eventLog = AppEventLog(this)
        eventLog.add("app_launch", outcome = "success")
        setContentView(buildUi())
        configureWebView()
        showHome()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 20, 24))
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        address = EditText(this).apply {
            hint = "Search the web or what this browser learned"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(40, 43, 49))
            setPadding(dp(12), 0, dp(12), 0)
            setOnEditorActionListener { _, _, _ -> submitAddress(); true }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    submitAddress(); true
                } else false
            }
        }
        searchRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(button("Go") { submitAddress() }, LinearLayout.LayoutParams(dp(64), dp(48)).apply { marginStart = dp(6) })
        root.addView(searchRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(2), dp(6), dp(4))
        }
        actions.addView(button("←") { if (webView.canGoBack()) webView.goBack() })
        actions.addView(button("→") { if (webView.canGoForward()) webView.goForward() })
        actions.addView(button("Home") { showHome() })
        actions.addView(button("Refresh") { webView.reload() })
        actions.addView(button("Save Page") { saveCurrentPage() })
        actions.addView(button("Teach Site") { teachCurrentSite() })
        actions.addView(button("Upload Data") { openDocument.launch(arrayOf("text/*", "application/json", "text/csv", "text/markdown", "text/html")) })
        actions.addView(button("Export Test Log") { exportTestLog() })
        actions.addView(button("Check Update") { checkForUpdate() })
        actions.addView(button("Update Knowledge") { updateKnowledge() })
        actions.addView(button("Knowledge") { showKnowledgeSummary() })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            text = "Ready"
        }
        root.addView(status)

        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        minWidth = 0
        minimumWidth = 0
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = userAgentString + " AIBrowser/2.1"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "http" || uri.scheme == "https") return false
                return openExternal(uri)
            }

            override fun onPageFinished(view: WebView, url: String) {
                address.setText(url)
                status.text = view.title ?: url
                val host = KnowledgeSearch.normalizeHost(url)
                if (host.isNotBlank() && host != "local.ai-browser") {
                    eventLog.add("navigation", host, AppEventLog.sanitizeUrl(url), "loaded")
                }
                maybeShowSiteSkill(url)
            }
        }
    }

    private fun submitAddress() {
        val input = address.text.toString().trim()
        if (input.isBlank()) return
        if (looksLikeUrl(input)) {
            val url = normalizeUrl(input)
            eventLog.add("navigation_request", KnowledgeSearch.normalizeHost(url), AppEventLog.sanitizeUrl(url), "requested")
            webView.loadUrl(url)
            return
        }

        val local = KnowledgeSearch.search(input, store.getEntries(), store.getSkills(), 12)
        eventLog.add("search", detail = input.take(500), outcome = if (local.isEmpty()) "web" else "local_results:${local.size}")
        if (local.isEmpty()) {
            searchWeb(input)
        } else {
            val labels = local.map { "${it.title}\n${it.snippet}" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("What I already know")
                .setItems(labels) { _, which -> openKnowledgeResult(local[which]) }
                .setPositiveButton("Search web") { _, _ -> searchWeb(input) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openKnowledgeResult(result: KnowledgeResult) {
        when {
            result.source.startsWith("http://") || result.source.startsWith("https://") -> webView.loadUrl(result.source)
            else -> AlertDialog.Builder(this).setTitle(result.title).setMessage(result.snippet).setPositiveButton("OK", null).show()
        }
    }

    private fun searchWeb(query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        webView.loadUrl("https://www.google.com/search?q=$encoded")
    }

    private fun teachCurrentSite() {
        val current = webView.url ?: return toast("Open a website first")
        val host = KnowledgeSearch.normalizeHost(current)
        if (host.isBlank() || host == "local.ai-browser") return toast("Open a website first")
        val existing = store.skillForHost(host)?.instructions.orEmpty()
        val input = EditText(this).apply {
            setText(existing)
            hint = "Example: tap Search, choose People, do captcha manually if it appears, then continue"
            minLines = 5
            gravity = Gravity.TOP
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Teach me how to use $host")
            .setView(input)
            .setMessage("Save navigation notes that should be remembered next time. Captchas stay manual.")
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    store.upsertSkill(SiteSkill(host, text, System.currentTimeMillis()))
                    eventLog.add("teach_site", host, "instructions_updated", "success")
                    toast("Site instructions saved")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun maybeShowSiteSkill(url: String) {
        val host = KnowledgeSearch.normalizeHost(url)
        if (host.isBlank() || host == lastSkillHostShown) return
        val skill = store.skillForHost(host) ?: return
        lastSkillHostShown = host
        AlertDialog.Builder(this)
            .setTitle("Learned instructions for $host")
            .setMessage(skill.instructions)
            .setPositiveButton("Got it", null)
            .setNeutralButton("Edit") { _, _ -> teachCurrentSite() }
            .show()
    }

    private fun saveCurrentPage() {
        val source = webView.url ?: return toast("Open a website first")
        if (!source.startsWith("http")) return toast("Only web pages can be saved")
        val host = KnowledgeSearch.normalizeHost(source)
        status.text = "Reading this page…"
        webView.evaluateJavascript("(document.body && document.body.innerText) ? document.body.innerText : ''") { raw ->
            val text = decodeJsString(raw).take(120_000)
            if (text.isBlank()) {
                status.text = "Could not read this page"
                eventLog.add("save_page", host, AppEventLog.sanitizeUrl(source), "failed_no_text")
                toast("This site did not expose readable page text")
                return@evaluateJavascript
            }
            val entry = KnowledgeEntry(
                id = "web:" + source,
                title = webView.title ?: source,
                source = source,
                content = text,
                updatedAt = System.currentTimeMillis(),
                kind = "web"
            )
            store.upsertEntry(entry)
            eventLog.add("save_page", host, AppEventLog.sanitizeUrl(source), "success")
            status.text = "Saved page to knowledge"
            toast("Page learned")
        }
    }

    private fun importTextDocument(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8192)
                while (out.length < 500_000) {
                    val count = reader.read(buffer)
                    if (count <= 0) break
                    out.append(buffer, 0, minOf(count, 500_000 - out.length))
                }
                out.toString()
            }.orEmpty()
            if (text.isBlank()) {
                eventLog.add("upload_data", outcome = "failed_empty")
                return toast("That file did not contain readable text")
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Uploaded data"
            store.upsertEntry(
                KnowledgeEntry(
                    id = "upload:${UUID.randomUUID()}",
                    title = name,
                    source = "upload://$name",
                    content = text,
                    updatedAt = System.currentTimeMillis(),
                    kind = "upload"
                )
            )
            eventLog.add("upload_data", outcome = "success")
            toast("Imported into browser knowledge")
            showHome()
        } catch (e: Exception) {
            eventLog.add("upload_data", outcome = "failed")
            toast("Could not import file: ${e.message ?: "unknown error"}")
        }
    }

    private fun exportTestLog() {
        val notes = EditText(this).apply {
            hint = "What did you try? What failed? What should the next version do better?"
            minLines = 6
            gravity = Gravity.TOP
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Export Test Log")
            .setMessage("This report includes searches and app/site outcomes. It does not include passwords, cookies, form contents, page bodies, or URL query/fragment data.")
            .setView(notes)
            .setPositiveButton("Create Document") { _, _ ->
                pendingExportJson = AppEventLog.buildReport(
                    eventLog.events(),
                    notes.text.toString().trim(),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
                eventLog.add("export_test_log", outcome = "requested")
                createTestLog.launch("AI-Browser-Test-Log-${System.currentTimeMillis()}.json")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun writeTestLog(uri: Uri) {
        val json = pendingExportJson ?: return
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            eventLog.add("export_test_log", outcome = "saved")
            toast("Test log saved. Upload that JSON document to me with your next test report.")
        } catch (e: Exception) {
            eventLog.add("export_test_log", outcome = "failed")
            toast("Could not save test log: ${e.message ?: "unknown error"}")
        } finally {
            pendingExportJson = null
        }
    }

    private fun checkForUpdate() {
        status.text = "Checking for update…"
        eventLog.add("check_update", outcome = "started")
        UpdateChecker.check(UPDATE_MANIFEST_URL) { result ->
            result.onSuccess { info ->
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    status.text = "Update ${info.versionName} available"
                    eventLog.add("check_update", detail = info.versionName, outcome = "available")
                    AlertDialog.Builder(this)
                        .setTitle("Update available: ${info.versionName}")
                        .setMessage(info.notes.ifBlank { "A newer AI Browser build is available." })
                        .setPositiveButton("Open Download") { _, _ ->
                            openExternal(Uri.parse(info.downloadUrl))
                        }
                        .setNegativeButton("Later", null)
                        .show()
                } else {
                    status.text = "AI Browser is up to date"
                    eventLog.add("check_update", detail = BuildConfig.VERSION_NAME, outcome = "current")
                    AlertDialog.Builder(this)
                        .setTitle("You're up to date")
                        .setMessage("Installed: ${BuildConfig.VERSION_NAME}\nLatest: ${info.versionName}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.onFailure { error ->
                status.text = "Update check failed"
                eventLog.add("check_update", outcome = "failed")
                toast("Could not check for updates: ${error.message ?: "network error"}")
            }
        }
    }

    private fun updateKnowledge() {
        val sources = store.getEntries().filter { it.kind == "web" && it.source.startsWith("http") }
        if (sources.isEmpty()) return toast("Save some web pages first")
        status.text = "Updating 0/${sources.size} sources…"
        eventLog.add("update_knowledge", detail = "sources:${sources.size}", outcome = "started")
        val runner = WebView(this)
        runner.settings.javaScriptEnabled = true
        runner.settings.domStorageEnabled = true
        runner.settings.allowFileAccess = false
        runner.settings.allowContentAccess = false
        refreshSource(runner, sources, 0, 0, 0)
    }

    private fun refreshSource(runner: WebView, items: List<KnowledgeEntry>, index: Int, ok: Int, failed: Int) {
        if (index >= items.size) {
            runner.destroy()
            status.text = "Knowledge update finished: $ok updated, $failed failed"
            eventLog.add("update_knowledge", detail = "updated:$ok,failed:$failed", outcome = "finished")
            toast("Knowledge updated: $ok refreshed")
            return
        }
        val item = items[index]
        status.text = "Updating ${index + 1}/${items.size}: ${item.title}"
        var completed = false
        val timeout = Runnable {
            if (!completed) {
                completed = true
                runner.stopLoading()
                refreshSource(runner, items, index + 1, ok, failed + 1)
            }
        }
        handler.postDelayed(timeout, 15_000)
        runner.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (completed) return
                view.evaluateJavascript("(document.body && document.body.innerText) ? document.body.innerText : ''") { raw ->
                    if (completed) return@evaluateJavascript
                    completed = true
                    handler.removeCallbacks(timeout)
                    val text = decodeJsString(raw).take(120_000)
                    if (text.isNotBlank()) {
                        store.upsertEntry(item.copy(title = view.title ?: item.title, content = text, updatedAt = System.currentTimeMillis()))
                        refreshSource(runner, items, index + 1, ok + 1, failed)
                    } else {
                        refreshSource(runner, items, index + 1, ok, failed + 1)
                    }
                }
            }
        }
        runner.loadUrl(item.source)
    }

    private fun showKnowledgeSummary() {
        val entries = store.getEntries()
        val skills = store.getSkills()
        val message = buildString {
            append("Saved knowledge: ${entries.size} items\n")
            append("Learned sites: ${skills.size}\n")
            append("Test-log events: ${eventLog.events().size}\n\n")
            if (skills.isNotEmpty()) {
                append("Sites:\n")
                skills.take(15).forEach { append("• ${it.host}\n") }
            }
        }
        AlertDialog.Builder(this).setTitle("Browser Knowledge").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun showHome() {
        lastSkillHostShown = null
        val entries = store.getEntries().size
        val skills = store.getSkills().size
        val html = """
            <html><head><meta name='viewport' content='width=device-width,initial-scale=1'/>
            <style>body{font-family:sans-serif;background:#121418;color:#eee;padding:28px}h1{font-size:30px}p{line-height:1.5}.card{background:#22262d;border-radius:14px;padding:18px;margin:14px 0}b{color:#9ed0ff}</style></head>
            <body><h1>AI Browser</h1><p>Browse normally, then teach the browser what matters so you do not have to rediscover it every time.</p>
            <div class='card'><b>$entries</b> saved knowledge items<br/><b>$skills</b> learned websites</div>
            <div class='card'><b>Save Page</b> remembers readable page text.<br/><b>Teach Site</b> saves how you navigate a site.<br/><b>Upload Data</b> adds text files to knowledge.<br/><b>Export Test Log</b> creates the document you can send us after testing.<br/><b>Check Update</b> checks for the newest build.<br/><b>Update Knowledge</b> refreshes pages you previously saved.</div>
            <p>Captchas and login verification stay manual. Complete them yourself and continue browsing afterward.</p></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://local.ai-browser/", html, "text/html", "UTF-8", null)
        address.setText("")
        status.text = "Home"
    }

    private fun decodeJsString(raw: String): String = try {
        JSONObject("{\"v\":$raw}").optString("v")
    } catch (_: Exception) {
        raw.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
    }

    private fun looksLikeUrl(value: String): Boolean {
        val v = value.lowercase()
        return v.startsWith("http://") || v.startsWith("https://") ||
            (!v.contains(' ') && (v.contains('.') || v == "localhost"))
    }

    private fun normalizeUrl(value: String): String =
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"

    private fun openExternal(uri: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, uri)); true
    } catch (_: Exception) {
        toast("No app can open ${uri.scheme ?: "that link"}"); true
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/lukeypue/APPGATE/ai-browser-rebuild/update/latest.json"
    }
}
