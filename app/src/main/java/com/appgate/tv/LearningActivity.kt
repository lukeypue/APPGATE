package com.appgate.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appgate.tv.sitebrain.ExplorerBudget
import com.appgate.tv.sitebrain.LearningEvent
import com.appgate.tv.sitebrain.LearningProgressTracker
import com.appgate.tv.sitebrain.LearningReportWriter
import com.appgate.tv.sitebrain.LearningSite
import com.appgate.tv.sitebrain.LearningSiteCatalog
import com.appgate.tv.sitebrain.SharedPreferencesSiteBrainStore
import com.appgate.tv.sitebrain.SiteBrainControllerState
import com.appgate.tv.sitebrain.SiteBrainRepository
import com.appgate.tv.sitebrain.WebViewSiteBrainController
import java.io.File

class LearningActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var counters: TextView
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var skipButton: Button
    private lateinit var controller: WebViewSiteBrainController
    private lateinit var brainRepository: SiteBrainRepository

    private val handler = Handler(Looper.getMainLooper())
    private val sites = LearningSiteCatalog.defaultSites()
    private val guard = SourceSessionGuard()
    private val events = ArrayList<LearningEvent>()

    private var tracker: LearningProgressTracker? = null
    private var paused = false
    private var stopped = false
    private var waitingForHuman = false
    private var authScreenOpen = false
    private var actionInFlight = false
    private var actionsThisSite = 0
    private var pagesThisSite = 0
    private var verifiedThisSite = 0
    private var consecutivePlateaus = 0
    private var siteStartedAt = 0L
    private var activeSessionId = 0L
    private var activeSite: LearningSite? = null
    private var lastCoverage = 0.0

    private val maxActionsPerVisit = 150
    private val maxMinutesPerVisit = 25L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Site Brain — Start Learning"

        val brainPrefs = getSharedPreferences("site_brain_knowledge", MODE_PRIVATE)
        brainRepository = SiteBrainRepository(SharedPreferencesSiteBrainStore(brainPrefs))
        controller = WebViewSiteBrainController(brainRepository)

        val runPrefs = getSharedPreferences("site_brain_learning_run", MODE_PRIVATE)
        tracker = LearningProgressTracker(
            siteCount = sites.size,
            savedSiteIndex = runPrefs.getInt("site_index", 0),
            savedVerified = runPrefs.getInt("verified", 0),
            savedPasses = runPrefs.getInt("passes", 0)
        )
        loadExistingLog()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 23))
            setPadding(14, 12, 14, 12)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            text = "Preparing Site Brain learning…"
        }
        counters = TextView(this).apply {
            setTextColor(Color.rgb(145, 205, 165))
            textSize = 13f
            setPadding(0, 6, 0, 8)
        }
        root.addView(status)
        root.addView(counters)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener { pauseLearning() }
        }
        resumeButton = Button(this).apply {
            text = "Resume"
            isEnabled = false
            setOnClickListener { resumeLearning() }
        }
        skipButton = Button(this).apply {
            text = "Skip Site"
            setOnClickListener { skipCurrentSite() }
        }
        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopLearning() }
        }
        row1.addView(pauseButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(resumeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(skipButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = "UPDATE"
            setOnClickListener { startActivity(Intent(this@LearningActivity, UpdateActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(Button(this).apply {
            text = "SHARE LEARNING LOGS"
            setOnClickListener { shareLogs() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        root.addView(row2)

        root.addView(TextView(this).apply {
            setTextColor(Color.rgb(170, 185, 205))
            textSize = 12f
            text = "One site is trained at a time. Site Brain stays on that site through several shallow plateaus before moving on. If login/CAPTCHA appears, complete it yourself or tap Skip Site. Google/Facebook/Apple sign-in opens in a separate human-only screen. Learned knowledge, checkpoints and logs survive normal app updates."
            setPadding(0, 6, 0, 10)
        })

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
                    return allowNavigation(request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    return allowNavigation(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    if (stopped || paused || authScreenOpen) return
                    val actual = url.orEmpty()
                    val host = runCatching { Uri.parse(actual).host.orEmpty() }.getOrDefault("")
                    if (!guard.accept(activeSessionId, host)) {
                        record("STALE_OR_WRONG_HOST", "IGNORED", host, Uri.parse(actual).path.orEmpty(), "Ignored callback for another site/session")
                        return
                    }
                    pagesThisSite++
                    record("PAGE_LOADED", "OBSERVED", host, Uri.parse(actual).path.orEmpty(), actual)
                    if (actionInFlight) handler.postDelayed({ verifyAction() }, 900L)
                    else handler.postDelayed({ mapAndAct() }, 900L)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true || stopped) return
                    val host = request.url.host.orEmpty()
                    if (!guard.accept(activeSessionId, host)) return
                    record("PAGE_ERROR", "ERROR", host, request.url.path.orEmpty(), error?.description?.toString().orEmpty())
                    handler.postDelayed({ recoverOrMove("page error") }, 1200L)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        updateCounters()
        startCurrentSite()
    }

    private fun allowNavigation(rawUrl: String): Boolean {
        val site = activeSite ?: return true
        val host = runCatching { Uri.parse(rawUrl).host.orEmpty() }.getOrDefault("")
        if (site.acceptsHost(host)) return false

        if (LearningNavigationPolicy.isKnownAuthHost(host)) {
            openHumanSignIn(rawUrl)
            return true
        }

        record("CROSS_SITE_NAVIGATION", "BLOCKED", host, Uri.parse(rawUrl).path.orEmpty(), "Training kept inside ${site.expectedHost}")
        return true
    }

    private fun openHumanSignIn(authUrl: String) {
        if (authScreenOpen || stopped) return
        val site = activeSite ?: return
        authScreenOpen = true
        paused = true
        waitingForHuman = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = false
        record("HUMAN_AUTH", "OPENED", Uri.parse(authUrl).host.orEmpty(), Uri.parse(authUrl).path.orEmpty(), "Opened separate human-only sign-in screen")
        status.text = "${site.name} sign-in opened\nFinish Google/Facebook/Apple login in the sign-in screen, then return here."
        startActivityForResult(Intent(this, HumanSignInActivity::class.java).apply {
            putExtra("authUrl", authUrl)
            putExtra("targetHost", site.expectedHost)
            putExtra("targetName", site.name)
        }, AUTH_REQUEST)
    }

    private fun startCurrentSite() {
        if (stopped || paused || sites.isEmpty()) return
        val t = tracker ?: return
        val site = sites[t.siteIndex]
        activeSite = site
        controller = WebViewSiteBrainController(brainRepository)
        val session = guard.begin(site.key, site.expectedHost)
        activeSessionId = session.id
        actionsThisSite = 0
        pagesThisSite = 0
        verifiedThisSite = 0
        consecutivePlateaus = 0
        lastCoverage = 0.0
        siteStartedAt = System.currentTimeMillis()
        actionInFlight = false
        waitingForHuman = false
        authScreenOpen = false
        controller.markHumanResume()
        status.text = "Learning ${site.name}\nStaying on this site until it is well explored or you tap Skip Site."
        record("SITE_START", "STARTED", site.expectedHost, "", "root=${site.startUrl}")
        saveCheckpoint()
        webView.stopLoading()
        webView.loadUrl(site.startUrl)
    }

    private fun mapAndAct() {
        if (stopped || paused || waitingForHuman || actionInFlight || authScreenOpen) return
        val site = activeSite ?: return
        val elapsed = System.currentTimeMillis() - siteStartedAt
        if (actionsThisSite >= maxActionsPerVisit || elapsed >= maxMinutesPerVisit * 60_000L) {
            moveToNextSite("deep visit budget reached")
            return
        }

        controller.observe(webView) { result ->
            if (stopped || paused || authScreenOpen) return@observe
            result.onFailure {
                record("OBSERVE", "ERROR", site.expectedHost, "", it.message.orEmpty())
                recoverOrMove("observe failed")
            }.onSuccess { observation ->
                val actualHost = observation.snapshot.host
                if (!guard.accept(activeSessionId, actualHost)) {
                    record("OBSERVE", "STALE", actualHost, observation.snapshot.routeSignature, "Observation discarded by source-session guard")
                    return@onSuccess
                }
                lastCoverage = observation.brain.coverageScore
                val percent = (lastCoverage * 100).toInt().coerceIn(0, 100)
                status.text = "Learning ${site.name}\n$percent% mapped · ${observation.safeActionsFound} safe controls · $verifiedThisSite verified here · action ${actionsThisSite + 1}/$maxActionsPerVisit"

                if (observation.controllerState == SiteBrainControllerState.WAITING_FOR_HUMAN) {
                    waitForHuman(actualHost, observation.snapshot.routeSignature, "Login/CAPTCHA/security check requires you")
                    return@onSuccess
                }

                val budget = ExplorerBudget(
                    maxPages = 500,
                    maxActions = maxActionsPerVisit,
                    maxRevisitsPerFingerprint = 50,
                    maxElapsedMs = maxMinutesPerVisit * 60_000L,
                    pagesSeen = pagesThisSite,
                    actionsTaken = actionsThisSite,
                    startedAt = siteStartedAt
                )
                val prepared = controller.prepareExploration(observation, budget, "site brain training")
                if (prepared == null) {
                    handlePlateau(actualHost, observation.snapshot.routeSignature)
                    return@onSuccess
                }

                actionInFlight = true
                actionsThisSite++
                record(prepared.semanticIntent, "ATTEMPTED", actualHost, observation.snapshot.routeSignature, "safe action", lastCoverage, lastCoverage)
                controller.executePrepared(webView, prepared) { accepted ->
                    if (!accepted) {
                        actionInFlight = false
                        record(prepared.semanticIntent, "REJECTED", actualHost, observation.snapshot.routeSignature, "executor did not accept action")
                        handler.postDelayed({ mapAndAct() }, 650L)
                    } else {
                        handler.postDelayed({ verifyAction() }, 1100L)
                    }
                }
            }
        }
    }

    private fun handlePlateau(host: String, route: String) {
        consecutivePlateaus++
        record("SITE_PLATEAU", "CHECKPOINTED", host, route, "Plateau $consecutivePlateaus; trying recovery before leaving site")
        saveCheckpoint()
        val elapsed = System.currentTimeMillis() - siteStartedAt
        if (LearningPlateauPolicy.shouldMoveOn(consecutivePlateaus, actionsThisSite, verifiedThisSite, elapsed)) {
            moveToNextSite("repeated novelty plateau")
            return
        }

        controller.markHumanResume()
        status.text = "Still learning ${activeSite?.name ?: "site"}\nShallow plateau $consecutivePlateaus — trying another area before moving on."
        when {
            consecutivePlateaus % 3 == 0 -> {
                val root = activeSite?.startUrl.orEmpty()
                if (root.isNotBlank()) webView.loadUrl(root) else handler.postDelayed({ mapAndAct() }, 800L)
            }
            webView.canGoBack() && consecutivePlateaus % 2 == 0 -> webView.goBack()
            else -> webView.evaluateJavascript("(function(){window.scrollBy(0,Math.max(window.innerHeight*0.85,700));return 'SCROLLED';})();") {
                handler.postDelayed({ mapAndAct() }, 900L)
            }
        }
    }

    private fun recoverOrMove(reason: String) {
        consecutivePlateaus++
        val elapsed = System.currentTimeMillis() - siteStartedAt
        if (LearningPlateauPolicy.shouldMoveOn(consecutivePlateaus, actionsThisSite, verifiedThisSite, elapsed)) {
            moveToNextSite(reason)
        } else {
            controller.markHumanResume()
            handler.postDelayed({ webView.reload() }, 1200L)
        }
    }

    private fun verifyAction() {
        if (stopped || paused || authScreenOpen || !actionInFlight || !controller.hasPendingExploration()) return
        controller.verifyPending(webView) { result ->
            actionInFlight = false
            result.onFailure {
                record("VERIFY", "ERROR", activeSite?.expectedHost.orEmpty(), "", it.message.orEmpty())
                handler.postDelayed({ mapAndAct() }, 700L)
            }.onSuccess { verified ->
                val host = verified.after.host
                if (!guard.accept(activeSessionId, host)) {
                    record("VERIFY", "STALE", host, verified.after.routeSignature, "Verification discarded by source-session guard")
                    return@onSuccess
                }
                val before = lastCoverage
                lastCoverage = verified.brain.coverageScore
                if (verified.after.challengeDetected || verified.after.loginDetected) {
                    waitForHuman(host, verified.after.routeSignature, "Protected boundary reached after action")
                    return@onSuccess
                }
                val outcome = if (verified.verification.success) "VERIFIED" else "NOT_VERIFIED"
                if (verified.verification.success) {
                    tracker?.recordVerified()
                    verifiedThisSite++
                    consecutivePlateaus = 0
                }
                record("VERIFY", outcome, host, verified.after.routeSignature, verified.verification.evidence.joinToString("; "), before, lastCoverage)
                saveCheckpoint()
                updateCounters()
                handler.postDelayed({ mapAndAct() }, 700L)
            }
        }
    }

    private fun waitForHuman(host: String, route: String, reason: String) {
        waitingForHuman = true
        paused = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = true
        skipButton.isEnabled = true
        record("HUMAN_BOUNDARY", "PAUSED", host, route, reason)
        status.text = "${activeSite?.name ?: "Site"} needs you\nComplete login/CAPTCHA in the page. Google/Facebook/Apple login will open in a separate sign-in screen. Then tap Resume — or Skip Site."
        saveCheckpoint()
    }

    private fun moveToNextSite(reason: String, force: Boolean = false) {
        if (stopped || (!force && paused)) return
        val site = activeSite
        actionInFlight = false
        waitingForHuman = false
        authScreenOpen = false
        record("SITE_CHECKPOINT", "SAVED", site?.expectedHost.orEmpty(), "", reason)
        tracker?.nextSite()
        saveCheckpoint()
        updateCounters()
        handler.postDelayed({ startCurrentSite() }, 900L)
    }

    private fun skipCurrentSite() {
        if (stopped) return
        val site = activeSite
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        paused = false
        waitingForHuman = false
        authScreenOpen = false
        actionInFlight = false
        record("SITE_SKIP", "SKIPPED", site?.expectedHost.orEmpty(), "", "Skipped by user; learned knowledge retained")
        moveToNextSite("skipped by user", true)
    }

    private fun pauseLearning() {
        if (stopped) return
        paused = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = true
        record("LEARNING", "PAUSED", activeSite?.expectedHost.orEmpty(), "", "Paused by user")
        status.text = "Learning paused\nYour Site Brain checkpoint and logs are saved."
        saveCheckpoint()
    }

    private fun resumeLearning() {
        if (stopped || authScreenOpen) return
        paused = false
        waitingForHuman = false
        pauseButton.isEnabled = true
        resumeButton.isEnabled = false
        skipButton.isEnabled = true
        controller.markHumanResume()
        record("LEARNING", "RESUMED", activeSite?.expectedHost.orEmpty(), "", "Resumed by user")
        CookieManager.getInstance().flush()
        webView.reload()
    }

    private fun stopLearning() {
        stopped = true
        paused = false
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        CookieManager.getInstance().flush()
        record("LEARNING", "STOPPED", activeSite?.expectedHost.orEmpty(), "", "Stopped by user; checkpoint retained")
        saveCheckpoint()
        pauseButton.isEnabled = false
        resumeButton.isEnabled = false
        skipButton.isEnabled = false
        status.text = "Learning stopped\nEverything learned so far is still saved."
    }

    private fun updateCounters() {
        val t = tracker ?: return
        counters.text = "Site ${t.siteIndex + 1}/${sites.size} · verified discoveries ${t.verifiedDiscoveries} · completed passes ${t.completedPasses} · log events ${events.size}"
    }

    private fun record(
        action: String,
        outcome: String,
        host: String,
        route: String,
        note: String,
        coverageBefore: Double = lastCoverage,
        coverageAfter: Double = lastCoverage
    ) {
        val site = activeSite
        events += LearningEvent(
            timestamp = System.currentTimeMillis(),
            source = site?.name ?: "Learning Run",
            host = host,
            pageType = "MAP_SITE",
            route = route,
            action = action,
            outcome = outcome,
            coverageBefore = coverageBefore,
            coverageAfter = coverageAfter,
            note = note
        )
        while (events.size > 5000) events.removeAt(0)
        persistLog()
        updateCounters()
    }

    private fun loadExistingLog() {
        runCatching {
            val file = File(filesDir, LOG_FILE)
            if (file.exists()) events.addAll(LearningReportWriter.decode(file.readText()).takeLast(4500))
        }
    }

    private fun persistLog() {
        runCatching {
            File(filesDir, LOG_FILE).writeText(LearningReportWriter.encode(events, "site_brain_learning_run"))
        }
    }

    private fun saveCheckpoint() {
        val t = tracker ?: return
        getSharedPreferences("site_brain_learning_run", MODE_PRIVATE).edit()
            .putInt("site_index", t.siteIndex)
            .putInt("verified", t.verifiedDiscoveries)
            .putInt("passes", t.completedPasses)
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
        persistLog()
    }

    private fun shareLogs() {
        persistLog()
        val text = runCatching { File(filesDir, LOG_FILE).readText() }
            .getOrElse { LearningReportWriter.encode(events, "site_brain_learning_run") }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "AI Browser Site Brain Learning Logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share Learning Logs"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST) return
        authScreenOpen = false
        if (resultCode == RESULT_OK) {
            paused = false
            waitingForHuman = false
            pauseButton.isEnabled = true
            resumeButton.isEnabled = false
            controller.markHumanResume()
            record("HUMAN_AUTH", "RETURNED", activeSite?.expectedHost.orEmpty(), "", "Human sign-in completed; reloading target site")
            webView.reload()
        } else {
            paused = true
            waitingForHuman = true
            resumeButton.isEnabled = true
            status.text = "Sign-in screen closed\nTap Resume to retry this site, or Skip Site to keep training elsewhere."
        }
    }

    override fun onDestroy() {
        saveCheckpoint()
        handler.removeCallbacksAndMessages(null)
        runCatching { CookieManager.getInstance().flush() }
        super.onDestroy()
    }

    companion object {
        private const val AUTH_REQUEST = 4101
        private const val LOG_FILE = "site_brain_learning_log.json"
    }
}
