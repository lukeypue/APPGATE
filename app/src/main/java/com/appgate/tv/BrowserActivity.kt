package com.appgate.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appgate.tv.sitebrain.ContinuousLearningSession
import com.appgate.tv.sitebrain.ExplorerBudget
import com.appgate.tv.sitebrain.LearningEvent
import com.appgate.tv.sitebrain.LearningReportWriter
import com.appgate.tv.sitebrain.SharedPreferencesSiteBrainStore
import com.appgate.tv.sitebrain.SiteBrainRepository
import com.appgate.tv.sitebrain.WebNavigationDecision
import com.appgate.tv.sitebrain.WebNavigationPolicy
import com.appgate.tv.sitebrain.WebViewSiteBrainController
import org.json.JSONArray
import org.json.JSONTokener

private data class FoundResult(val source: String, val title: String, val url: String, val deepVerified: Boolean)
private data class CandidateResult(val source: String, val sourceKey: String, val title: String, val url: String)
private enum class ScanMode { SOURCES, DEEP }

class BrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resumeButton: Button
    private lateinit var siteBrainController: WebViewSiteBrainController

    private val handler = Handler(Looper.getMainLooper())
    private var names = arrayListOf<String>()
    private var keys = arrayListOf<String>()
    private var urls = arrayListOf<String>()
    private var index = 0
    private var waitingForHuman = false
    private var stopped = false
    private var rememberSignIns = true
    private var mode = ScanMode.SOURCES
    private var explorationInProgress = false
    private var explorationVerificationScheduled = false
    private var explorationCoverageBefore = 0.0
    private var explorationActionLabel = ""
    private var learningSession: ContinuousLearningSession? = null
    private var learningStartedAtMs = 0L
    private var learningRootFingerprint: String? = null
    private var learningBacktracks = 0
    private lateinit var parsed: ParsedSearch

    private val candidates = LinkedHashMap<String, CandidateResult>()
    private val results = LinkedHashMap<String, FoundResult>()
    private val failures = mutableListOf<String>()
    private val sourceCounts = linkedMapOf<String, Int>()
    private val siteBrainStatuses = linkedMapOf<String, String>()
    private val learningEvents = mutableListOf<LearningEvent>()
    private var deepQueue = listOf<CandidateResult>()
    private var deepIndex = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI Browser Site Brain"
        names = intent.getStringArrayListExtra("sourceNames") ?: arrayListOf()
        keys = intent.getStringArrayListExtra("sourceKeys") ?: arrayListOf()
        urls = intent.getStringArrayListExtra("sourceUrls") ?: arrayListOf()
        rememberSignIns = intent.getBooleanExtra("rememberSignIns", true)
        parsed = SearchIntentParser.parse(intent.getStringExtra("query").orEmpty())
        val brainPrefs = getSharedPreferences("site_brain_knowledge", MODE_PRIVATE)
        siteBrainController = WebViewSiteBrainController(
            SiteBrainRepository(SharedPreferencesSiteBrainStore(brainPrefs))
        )

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
                siteBrainController.markHumanResume()
                isEnabled = false
                learnCurrentPage()
                inspectCurrentPage()
            }
        }
        controls.addView(resumeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "Skip"
            setOnClickListener {
                explorationInProgress = false
                explorationVerificationScheduled = false
                if (mode == ScanMode.SOURCES) {
                    failures.add("${currentName()}: skipped")
                    nextSource()
                } else {
                    nextDeepCandidate()
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "Show Results"
            setOnClickListener { showCombinedResults() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    return handleWebNavigation(view, request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    return handleWebNavigation(view, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (rememberSignIns) CookieManager.getInstance().flush()
                    if (stopped || waitingForHuman) return
                    if (explorationInProgress) {
                        scheduleExplorationVerification(650L)
                        return
                    }
                    handler.postDelayed({
                        learnCurrentPage()
                        inspectCurrentPage()
                    }, 1100L)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && mode == ScanMode.SOURCES) {
                        failures.add("${currentName()}: page error")
                    }
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (urls.isEmpty()) showCombinedResults() else loadCurrentSource()
    }

    private fun handleWebNavigation(view: WebView?, rawUrl: String): Boolean {
        val currentHost = runCatching { Uri.parse(view?.url.orEmpty()).host }.getOrNull()
        val decision = WebNavigationPolicy.decide(rawUrl, currentHost)
        return when (decision.kind) {
            WebNavigationDecision.KEEP_IN_WEBVIEW,
            WebNavigationDecision.LOAD_WEB_FALLBACK -> {
                val safeUrl = decision.url
                if (safeUrl.isNullOrBlank()) true else {
                    view?.loadUrl(safeUrl)
                    true
                }
            }
            else -> {
                if (!stopped && mode == ScanMode.SOURCES) {
                    failures.add("${currentName()}: blocked external app handoff")
                    learningEvents += LearningEvent(
                        timestamp = System.currentTimeMillis(),
                        source = currentName(),
                        host = currentHost.orEmpty(),
                        pageType = "NAVIGATION",
                        route = Uri.parse(view?.url.orEmpty()).path.orEmpty(),
                        action = "BLOCK_EXTERNAL_APP",
                        outcome = "BLOCKED",
                        coverageBefore = explorationCoverageBefore,
                        coverageAfter = explorationCoverageBefore,
                        note = "App-only navigation kept inside AI Browser"
                    )
                }
                true
            }
        }
    }

    private fun loadCurrentSource() {
        if (stopped) return
        if (index >= urls.size) {
            finishSourcePhase()
            return
        }
        mode = ScanMode.SOURCES
        waitingForHuman = false
        explorationInProgress = false
        explorationVerificationScheduled = false
        learningSession = ContinuousLearningSession.forSource(currentKey())
        learningStartedAtMs = SystemClock.elapsedRealtime()
        learningRootFingerprint = null
        learningBacktracks = 0
        resumeButton.isEnabled = false
        progress.max = names.size.coerceAtLeast(1)
        progress.progress = index
        status.text = "Searching ${index + 1} of ${urls.size}: ${currentName()}\nSite Brain is learning this website while it searches."
        webView.loadUrl(urls[index])
    }

    private fun finishSourcePhase() {
        if (parsed.requiredTerms.isEmpty() || candidates.isEmpty()) {
            showCombinedResults()
            return
        }
        mode = ScanMode.DEEP
        deepQueue = candidates.values.take(30)
        deepIndex = 0
        progress.max = deepQueue.size.coerceAtLeast(1)
        loadDeepCandidate()
    }

    private fun loadDeepCandidate() {
        if (stopped) return
        if (deepIndex >= deepQueue.size) {
            showCombinedResults()
            return
        }
        mode = ScanMode.DEEP
        waitingForHuman = false
        explorationInProgress = false
        explorationVerificationScheduled = false
        resumeButton.isEnabled = false
        progress.progress = deepIndex
        val candidate = deepQueue[deepIndex]
        status.text = "Deep checking ${deepIndex + 1} of ${deepQueue.size}: ${candidate.source}\nLooking for: ${parsed.requiredTerms.joinToString()}"
        webView.loadUrl(candidate.url)
    }

    private fun learnCurrentPage() {
        if (stopped || !::webView.isInitialized || explorationInProgress) return
        siteBrainController.observe(webView) { result ->
            result.onSuccess { observation ->
                val line = siteBrainController.statusLine(observation)
                siteBrainStatuses[observation.snapshot.host] = line
                if (!stopped) {
                    val first = status.text.toString().lineSequence().firstOrNull().orEmpty()
                    status.text = "$first\n$line"
                }
            }.onFailure {
                if (mode == ScanMode.SOURCES) failures.add("${currentName()}: Site Brain could not map this page")
            }
        }
    }

    private fun inspectCurrentPage() {
        if (stopped || waitingForHuman || explorationInProgress) return
        val challengeScript = """
            (function(){
              var t=((document.title||'')+' '+((document.body&&document.body.innerText)||'')).toLowerCase();
              return t.slice(0,12000);
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
                status.text = "${if (mode == ScanMode.SOURCES) currentName() else deepQueue.getOrNull(deepIndex)?.source ?: "Site"}: sign-in or human verification needed. Complete it in the page, then tap Resume.\n\nSite Brain pauses here and does not bypass security. If Remember Sign-ins is ON, the legitimate website session is kept on this device."
                return@evaluateJavascript
            }
            if (mode == ScanMode.SOURCES) extractSourceResults() else inspectDeepListing()
        }
    }

    private fun extractSourceResults() {
        val js = """
            (function(){
              var out=[]; var seen={}; var links=document.querySelectorAll('a[href]');
              function clean(s){return (s||'').replace(/\s+/g,' ').trim();}
              function cardText(a){
                var best=clean(a.innerText||a.getAttribute('aria-label')||'');
                var n=a;
                for(var d=0; d<5 && n; d++,n=n.parentElement){
                  var t=clean(n.innerText||'');
                  if(t.length>=best.length && t.length<=750) best=t;
                  if(n.matches && n.matches('article,li,[role="article"],[class*="listing"],[class*="result"],[class*="card"]')) break;
                }
                return best;
              }
              for(var i=0;i<links.length && out.length<40;i++){
                var a=links[i], href=a.href||'', txt=cardText(a);
                if(!href.startsWith('http') || txt.length<8 || txt.length>750) continue;
                var low=txt.toLowerCase();
                if(low==='sign in'||low==='log in'||low.includes('privacy policy')||low.includes('terms of use')) continue;
                if(seen[href]) continue; seen[href]=1;
                out.push({title:txt,url:href});
              }
              return JSON.stringify(out);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            var accepted = 0
            try {
                val decoded = decodeJsString(raw)
                val array = JSONArray(decoded)
                val sourceName = currentName()
                val sourceKey = currentKey()
                for (i in 0 until array.length()) {
                    if (parsed.requiredTerms.isNotEmpty() && accepted >= 6) break
                    val o = array.optJSONObject(i) ?: continue
                    val title = o.optString("title").trim()
                    val url = o.optString("url").trim()
                    if (title.length < 8 || !url.startsWith("http")) continue
                    if (!expectedDomainMatches(sourceKey, url)) continue
                    if (!SearchMatcher.summaryCouldMatch(title, parsed)) continue

                    if (parsed.requiredTerms.isEmpty()) {
                        if (!results.containsKey(url)) {
                            results[url] = FoundResult(sourceName, title, url, false)
                            accepted++
                        }
                    } else if (!candidates.containsKey(url)) {
                        candidates[url] = CandidateResult(sourceName, sourceKey, title, url)
                        accepted++
                    }
                }
                if (parsed.requiredTerms.isEmpty() && accepted > 0) {
                    sourceCounts[sourceName] = (sourceCounts[sourceName] ?: 0) + accepted
                }
                if (accepted == 0) failures.add("$sourceName: no matching cards read")
                status.text = "$sourceName: kept $accepted plausible listing${if (accepted == 1) "" else "s"} after hard filters\n${siteBrainStatuses.values.lastOrNull().orEmpty()}"
            } catch (_: Exception) {
                failures.add("${currentName()}: could not read result cards")
            }
            handler.postDelayed({ exploreCurrentSourceThenAdvance() }, 350L)
        }
    }

    private fun exploreCurrentSourceThenAdvance() {
        if (stopped || mode != ScanMode.SOURCES || index !in urls.indices) return
        val session = learningSession ?: ContinuousLearningSession.forSource(currentKey()).also {
            learningSession = it
            learningStartedAtMs = SystemClock.elapsedRealtime()
        }
        siteBrainController.observe(webView) { result ->
            result.onFailure {
                failures.add("${currentName()}: Site Brain exploration snapshot failed")
                nextSource()
            }.onSuccess { observation ->
                if (learningRootFingerprint == null) learningRootFingerprint = observation.snapshot.fingerprint
                val elapsed = SystemClock.elapsedRealtime() - learningStartedAtMs
                if (!session.shouldContinue(observation.brain.coverageScore, elapsed)) {
                    siteBrainStatuses[observation.snapshot.host] = siteBrainController.statusLine(observation)
                    nextSource()
                    return@onSuccess
                }

                val policy = session.policy
                val budget = ExplorerBudget(
                    maxPages = 120,
                    maxActions = policy.maxActions,
                    maxRevisitsPerFingerprint = 2,
                    maxElapsedMs = policy.maxElapsedMs,
                    pagesSeen = observation.brain.nodes.size,
                    actionsTaken = session.actionsTaken
                )
                val prepared = siteBrainController.prepareExploration(observation, budget, parsed.coreQuery)
                if (prepared == null) {
                    siteBrainStatuses[observation.snapshot.host] = siteBrainController.statusLine(observation)
                    val awayFromRoot = observation.snapshot.fingerprint != learningRootFingerprint
                    if (awayFromRoot && learningBacktracks < policy.maxBacktracks) {
                        learningBacktracks++
                        status.text = "Learning ${currentName()}: returning to the main search path to explore another safe branch"
                        handler.postDelayed({ webView.loadUrl(urls[index]) }, 700L)
                    } else {
                        nextSource()
                    }
                    return@onSuccess
                }

                explorationInProgress = true
                explorationVerificationScheduled = false
                explorationCoverageBefore = observation.brain.coverageScore
                explorationActionLabel = prepared.semanticIntent
                status.text = "Learning ${currentName()}: safe action ${session.actionsTaken + 1} of ${policy.maxActions}\n${prepared.semanticIntent}"
                siteBrainController.executePrepared(webView, prepared) { accepted ->
                    if (!accepted) {
                        explorationInProgress = false
                        failures.add("${currentName()}: safe path could not be activated")
                        learningEvents += LearningEvent(
                            timestamp = System.currentTimeMillis(),
                            source = currentName(),
                            host = observation.snapshot.host,
                            pageType = observation.snapshot.pageType.name,
                            route = observation.snapshot.routeSignature,
                            action = prepared.semanticIntent,
                            outcome = "NOT_ACTIVATED",
                            coverageBefore = observation.brain.coverageScore,
                            coverageAfter = observation.brain.coverageScore
                        )
                        handler.postDelayed({ exploreCurrentSourceThenAdvance() }, 750L)
                    } else {
                        session.recordAction()
                        handler.postDelayed({ scheduleExplorationVerification(0L) }, 4500L)
                    }
                }
            }
        }
    }

    private fun scheduleExplorationVerification(delayMs: Long) {
        if (!explorationInProgress || explorationVerificationScheduled || stopped) return
        explorationVerificationScheduled = true
        handler.postDelayed({
            if (!explorationInProgress || stopped) return@postDelayed
            siteBrainController.verifyPending(webView) { result ->
                explorationVerificationScheduled = false
                explorationInProgress = false
                var coverageAfter = explorationCoverageBefore
                var protectedBoundary = false
                result.onSuccess { verified ->
                    coverageAfter = verified.brain.coverageScore
                    val percent = (verified.brain.coverageScore * 100).toInt().coerceIn(0, 100)
                    val readiness = verified.brain.readiness.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                    siteBrainStatuses[verified.after.host] = "Site Brain: $readiness · $percent% mapped · ${verified.brain.edges.count { it.successCount > 0 }} verified paths"
                    learningEvents += LearningEvent(
                        timestamp = System.currentTimeMillis(),
                        source = currentName(),
                        host = verified.after.host,
                        pageType = verified.after.pageType.name,
                        route = verified.after.routeSignature,
                        action = explorationActionLabel,
                        outcome = if (verified.verification.success) "VERIFIED" else "NOT_VERIFIED",
                        coverageBefore = explorationCoverageBefore,
                        coverageAfter = verified.brain.coverageScore,
                        note = verified.verification.evidence.firstOrNull().orEmpty()
                    )
                    if (!verified.verification.success) {
                        val reason = verified.verification.evidence.firstOrNull().orEmpty()
                        failures.add("${currentName()}: explored path not verified${if (reason.isBlank()) "" else " ($reason)"}")
                    }
                    protectedBoundary = verified.after.challengeDetected || verified.after.loginDetected
                    if (protectedBoundary) {
                        waitingForHuman = true
                        resumeButton.isEnabled = true
                        status.text = "${currentName()}: sign-in or human verification boundary reached. Site Brain paused safely. Complete it yourself if you want this site included, then tap Resume."
                    }
                }.onFailure {
                    failures.add("${currentName()}: could not verify explored path")
                    learningEvents += LearningEvent(
                        timestamp = System.currentTimeMillis(),
                        source = currentName(),
                        host = Uri.parse(webView.url.orEmpty()).host.orEmpty(),
                        pageType = "UNKNOWN",
                        route = Uri.parse(webView.url.orEmpty()).path.orEmpty(),
                        action = explorationActionLabel,
                        outcome = "VERIFY_ERROR",
                        coverageBefore = explorationCoverageBefore,
                        coverageAfter = explorationCoverageBefore
                    )
                }

                if (protectedBoundary || waitingForHuman) return@verifyPending
                val session = learningSession
                val elapsed = SystemClock.elapsedRealtime() - learningStartedAtMs
                if (session != null && session.shouldContinue(coverageAfter, elapsed)) {
                    handler.postDelayed({ exploreCurrentSourceThenAdvance() }, 900L)
                } else {
                    nextSource()
                }
            }
        }, delayMs)
    }

    private fun inspectDeepListing() {
        val candidate = deepQueue.getOrNull(deepIndex) ?: run {
            showCombinedResults(); return
        }
        val js = """
            (function(){
              var title=(document.title||'');
              var body=((document.body&&document.body.innerText)||'');
              return (title+'\n'+body).slice(0,60000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            val text = decodeJsString(raw)
            if (SearchMatcher.deepTextMatches(text, parsed)) {
                results[candidate.url] = FoundResult(candidate.source, candidate.title, candidate.url, true)
                sourceCounts[candidate.source] = (sourceCounts[candidate.source] ?: 0) + 1
            }
            handler.postDelayed({ nextDeepCandidate() }, 300L)
        }
    }

    private fun nextSource() {
        if (stopped) return
        explorationInProgress = false
        explorationVerificationScheduled = false
        learningSession = null
        learningRootFingerprint = null
        learningBacktracks = 0
        index++
        loadCurrentSource()
    }

    private fun nextDeepCandidate() {
        if (stopped) return
        deepIndex++
        loadDeepCandidate()
    }

    private fun expectedDomainMatches(key: String, url: String): Boolean {
        val required = when (key) {
            "truecar" -> "truecar.com"
            "cargurus" -> "cargurus.com"
            "edmunds" -> "edmunds.com"
            "autolist" -> "autolist.com"
            "hemmings" -> "hemmings.com"
            "cars_bids" -> "carsandbids.com"
            "bringatrailer" -> "bringatrailer.com"
            else -> null
        }
        return required == null || url.contains(required, ignoreCase = true)
    }

    private fun showCombinedResults() {
        if (stopped) return
        stopped = true
        explorationInProgress = false
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        if (rememberSignIns) CookieManager.getInstance().flush()

        val verifiedCount = results.values.count { it.deepVerified }
        val possibleCount = results.size - verifiedCount
        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(11, 16, 24)) }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 40)
        }
        list.addView(label("Deep Search Results", 26f, Color.WHITE, true))
        list.addView(label("Site Brain is testing safe website paths, verifying what they do, and remembering successful paths for later searches.", 13f, Color.rgb(135, 190, 255), false).apply { setPadding(0, 4, 0, 8) })
        val constraintText = buildString {
            parsed.maxPrice?.let { append("Price ≤ $${"%,d".format(it)}  ") }
            parsed.maxMileage?.let { append("Mileage ≤ ${"%,d".format(it)}  ") }
            if (parsed.requiredTerms.isNotEmpty()) append("Deep match: ${parsed.requiredTerms.joinToString()}")
        }.ifBlank { "No hard constraints detected" }
        list.addView(label(constraintText, 14f, Color.rgb(175, 195, 220), false).apply { setPadding(0, 6, 0, 6) })
        list.addView(label("$verifiedCount verified · $possibleCount possible · searched ${index.coerceAtMost(names.size)} sources", 14f, Color.rgb(175, 195, 220), false).apply { setPadding(0, 0, 0, 14) })

        if (siteBrainStatuses.isNotEmpty()) {
            list.addView(label("Site Brain Learning", 17f, Color.WHITE, true))
            siteBrainStatuses.entries.take(20).forEach { (host, line) ->
                list.addView(label("• $host — $line", 12f, Color.rgb(150, 205, 160), false))
            }
        }

        if (sourceCounts.isNotEmpty()) {
            list.addView(label("Candidates by source", 17f, Color.WHITE, true).apply { setPadding(0, 12, 0, 0) })
            sourceCounts.forEach { (source, count) -> list.addView(label("• $source: $count", 13f, Color.rgb(150, 205, 160), false)) }
        }

        if (results.isEmpty()) {
            list.addView(label("No listing passed every requested condition yet. That is better than showing expensive or irrelevant listings as matches. Source notes below show which sites may need more Site Brain learning, a stronger verified path, or login.", 15f, Color.rgb(230, 210, 150), false).apply { setPadding(0, 14, 0, 14) })
        }

        results.values.take(100).forEach { result ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(Color.rgb(28, 37, 52))
            }
            card.addView(label(result.source, 13f, Color.rgb(135, 190, 255), true))
            if (result.deepVerified) {
                card.addView(label("✓ Verified match — detail requirement confirmed", 12f, Color.rgb(145, 220, 155), true))
            } else {
                card.addView(label("Possible match — card evidence passed; listing not deeply verified", 12f, Color.rgb(230, 210, 150), true))
            }
            card.addView(label(result.title, 16f, Color.WHITE, true).apply { setPadding(0, 4, 0, 6) })
            card.addView(Button(this).apply {
                text = "Open Original Listing"
                setOnClickListener {
                    startActivity(Intent(this@BrowserActivity, ListingActivity::class.java).apply {
                        putExtra("url", result.url)
                        putExtra("rememberSignIns", rememberSignIns)
                    })
                }
            })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            list.addView(card, lp)
        }

        if (failures.isNotEmpty()) {
            list.addView(label("Source Notes", 19f, Color.WHITE, true).apply { setPadding(0, 16, 0, 6) })
            failures.distinct().take(30).forEach { list.addView(label("• $it", 14f, Color.rgb(235, 175, 145), false)) }
        }

        if (learningEvents.isNotEmpty()) {
            list.addView(Button(this).apply {
                text = "Share Learning Data"
                setOnClickListener {
                    val report = LearningReportWriter.encode(learningEvents)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "AI Browser Site Brain Learning Data")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }, "Share Site Brain learning data"))
                }
            })
        }
        list.addView(Button(this).apply { text = "New Search"; setOnClickListener { finish() } })
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
    private fun currentKey(): String = if (index in keys.indices) keys[index] else ""

    private fun label(value: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.visibility == View.VISIBLE && webView.canGoBack() && !stopped) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        if (rememberSignIns) CookieManager.getInstance().flush()
        else CookieManager.getInstance().removeAllCookies(null)
        super.onDestroy()
    }
}
