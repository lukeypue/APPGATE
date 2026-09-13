package com.appgate.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private data class FoundResult(val source: String, val title: String, val url: String)

class BrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resumeButton: Button
    private lateinit var skipButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var names = arrayListOf<String>()
    private var urls = arrayListOf<String>()
    private var index = 0
    private var waitingForHuman = false
    private var stopped = false
    private val results = LinkedHashMap<String, FoundResult>()
    private val failures = mutableListOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI Browser Search"
        names = intent.getStringArrayListExtra("sourceNames") ?: arrayListOf()
        urls = intent.getStringArrayListExtra("sourceUrls") ?: arrayListOf()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 16, 24))
        }

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(18, 14, 18, 10)
        }
        root.addView(status)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (names.isEmpty()) 1 else names.size
            progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resumeButton = Button(this).apply {
            text = "Resume"
            isEnabled = false
            setOnClickListener {
                waitingForHuman = false
                isEnabled = false
                inspectCurrentPage()
            }
        }
        skipButton = Button(this).apply {
            text = "Skip Source"
            setOnClickListener {
                failures.add("${currentName()}: skipped")
                nextSource()
            }
        }
        stopButton = Button(this).apply {
            text = "Show Results"
            setOnClickListener {
                stopped = true
                showCombinedResults()
            }
        }
        controls.addView(resumeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(skipButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(stopButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls)

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!stopped && !waitingForHuman) {
                        handler.postDelayed({ inspectCurrentPage() }, 1200L)
                    }
                }

                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        failures.add("${currentName()}: page error")
                    }
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (urls.isEmpty()) {
            status.text = "No sources were selected."
            showCombinedResults()
        } else {
            loadCurrentSource()
        }
    }

    private fun loadCurrentSource() {
        if (stopped) return
        if (index >= urls.size) {
            showCombinedResults()
            return
        }
        waitingForHuman = false
        resumeButton.isEnabled = false
        progress.progress = index
        status.text = "Searching ${index + 1} of ${urls.size}: ${currentName()}"
        webView.loadUrl(urls[index])
    }

    private fun inspectCurrentPage() {
        if (stopped || waitingForHuman || index >= urls.size) return
        val challengeScript = """
            (function(){
              var t=((document.title||'')+' '+((document.body&&document.body.innerText)||'')).toLowerCase();
              return t.slice(0,9000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(challengeScript) { raw ->
            val text = decodeJsString(raw).lowercase()
            val human = listOf(
                "captcha", "verify you are human", "security check", "checkpoint",
                "unusual traffic", "confirm your identity", "log in to facebook", "login to facebook"
            ).any { text.contains(it) }
            if (human) {
                waitingForHuman = true
                resumeButton.isEnabled = true
                status.text = "${currentName()}: Human action needed. Complete the login/security check in the page, then tap Resume. Other sites are not blocked."
                return@evaluateJavascript
            }
            extractResults()
        }
    }

    private fun extractResults() {
        val js = """
            (function(){
              var out=[]; var seen={};
              var links=document.querySelectorAll('a[href]');
              for(var i=0;i<links.length && out.length<18;i++){
                var a=links[i];
                var href=a.href||'';
                var txt=(a.innerText||a.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim();
                if(!href.startsWith('http') || txt.length<10 || txt.length>220) continue;
                var low=txt.toLowerCase();
                if(low==='sign in'||low==='log in'||low.includes('privacy')||low.includes('terms of use')||low==='help') continue;
                if(seen[href]) continue;
                seen[href]=1;
                out.push({title:txt,url:href});
              }
              return JSON.stringify(out);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            try {
                val decoded = decodeJsString(raw)
                val array = JSONArray(decoded)
                var added = 0
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val title = o.optString("title").trim()
                    val url = o.optString("url").trim()
                    if (title.length < 10 || !url.startsWith("http")) continue
                    if (!results.containsKey(url)) {
                        results[url] = FoundResult(currentName(), title, url)
                        added++
                    }
                }
                status.text = "${currentName()}: read $added candidate result links"
            } catch (_: Exception) {
                failures.add("${currentName()}: could not read result links")
            }
            handler.postDelayed({ nextSource() }, 700L)
        }
    }

    private fun nextSource() {
        if (stopped) return
        index++
        progress.progress = index.coerceAtMost(progress.max)
        loadCurrentSource()
    }

    private fun showCombinedResults() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(11, 16, 24)) }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 40)
        }
        list.addView(label("Combined Results", 26f, Color.WHITE, true))
        list.addView(label("${results.size} candidate links found across ${index.coerceAtMost(names.size)} of ${names.size} sources", 14f, Color.rgb(175, 195, 220), false).apply { setPadding(0, 6, 0, 16) })

        if (results.isEmpty()) {
            list.addView(label("No generic result links were extracted yet. This does not necessarily mean the site has no results — it may need a stronger site-specific skill. Use the browser screen to verify the source visually, then we can improve that site's adapter.", 15f, Color.rgb(230, 210, 150), false))
        }

        results.values.take(100).forEach { result ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(Color.rgb(28, 37, 52))
            }
            card.addView(label(result.source, 13f, Color.rgb(135, 190, 255), true))
            card.addView(label(result.title, 16f, Color.WHITE, true).apply { setPadding(0, 4, 0, 6) })
            card.addView(Button(this).apply {
                text = "Open Original Listing"
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) }
            })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            list.addView(card, lp)
        }

        if (failures.isNotEmpty()) {
            list.addView(label("Source Notes", 19f, Color.WHITE, true).apply { setPadding(0, 16, 0, 6) })
            failures.distinct().forEach { list.addView(label("• $it", 14f, Color.rgb(235, 175, 145), false)) }
        }

        list.addView(Button(this).apply {
            text = "New Search"
            setOnClickListener { finish() }
        })
        root.addView(list)
        setContentView(root)
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            val value = JSONTokener(raw).nextValue()
            if (value is String) value else value.toString()
        } catch (_: Exception) {
            raw.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
        }
    }

    private fun currentName(): String = if (index in names.indices) names[index] else "Source"

    private fun label(value: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.visibility == View.VISIBLE && webView.canGoBack() && !stopped) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
