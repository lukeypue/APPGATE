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
    private lateinit var controller: WebViewSiteBrainController

    private val handler = Handler(Looper.getMainLooper())
    private val sites = LearningSiteCatalog.defaultSites()
    private val guard = SourceSessionGuard()
    private val events = ArrayList<LearningEvent>()

    private var tracker: LearningProgressTracker? = null
    private var paused = false
    private var stopped = false
    private var waitingForHuman = false
    private var actionInFlight = false
    private var actionsThisSite = 0
    private var pagesThisSite = 0
    private var siteStartedAt = 0L
    private var activeSessionId = 0L
    private var activeSite: LearningSite? = null
    private val maxActionsPerVisit = 40
    private val maxMinutesPerVisit = 8L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Site Brain — Start Learning"

        val brainPrefs = getSharedPreferences("site_brain_knowledge", MODE_PRIVATE)
        controller = WebViewSiteBrainController(SiteBrainRepository(SharedPreferencesSiteBrainStore(brainPrefs)))
        val runPrefs = getSharedPreferences("site_brain_learning_run", MODE_PRIVATE)
        tracker = LearningProgressTracker(
            siteCount = sites.size,
            savedSiteIndex = runPrefs.getInt("site_index", 0),
            savedVerified = runPrefs.getInt("verified", 0),
            savedPasses = runPrefs.getInt("passes", 0)
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 23))
            setPadding(16, 14, 16, 14)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            text = "Preparing Site Brain learning…"
        }
        counters = TextView(this).apply {
            setTextColor(Color.rgb(145, 205, 165))
            textSize = 13f
            setPadding(0, 6, 0, 10)
        }
        root.addView(status)
        root.addView(counters)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener { pauseLearning() }
        }
        resumeButton = Button(this).apply {
            text = "Resume"
            isEnabled = false
            setOnClickListener { resumeLearning() }
        }
        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopLearning() }
        }
        row.addView(pauseButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(resumeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        val shareButton = Button(this).apply {
            text = "Share Learning Logs"
            setOnClickListener { shareLogs() }
        }
        root.addView(shareButton)

        root.addView(TextView(this).apply {
            setTextColor(Color.rgb(170, 185, 205))
            textSize = 12f
            text = "Leave this running as long as you want. Site knowledge checkpoints are stored separately from the APK and survive normal app updates. Login, CAPTCHA, 2FA, payment, messaging, posting, deletion and account changes are never automated."
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
                    if (stopped || paused) return
                    val actual = url.orEmpty()
                    val host = runCatching { Uri.parse(actual).host.orEmpty() }.getOrDefault("")
                    if (!guard.accept(activeSessionId, host)) {
                        record("STALE_OR_WRONG_HOST", "IGNORED", host, Uri.parse(actual).path.orEmpty(), "Ignored callback for another site/session: $actual")
                        return
                    }
                    pagesThisSite++
                    record("PAGE_LOADED", "OBSERVED", host, Uri.parse(actual).path.orEmpty(), actual)
                    if (actionInFlight) {
                        handler.postDelayed({ verifyAction() }, 850L)
                    } else {
                        handler.postDelayed({ mapAndAct() }, 900L)
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true || stopped) return
                    val host = request.url.host.orEmpty()
                    if (!guard.accept(activeSessionId, host)) return
                    record("PAGE_ERROR", "ERROR", host, request.url.path.orEmpty(), error?.description?.toString().orEmpty())
                    handler.postDelayed({ moveToNextSite("page error") }, 1200L)
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
        record("CROSS_SITE_NAVIGATION", "BLOCKED", host, Uri.parse(rawUrl).path.orEmpty(), "Training kept inside ${site.expectedHost}")
        return true
    }

    private fun startCurrentSite() {
        if (stopped || paused || sites.isEmpty()) return
        val t = tracker ?: return
        val site = sites[t.siteIndex]
        activeSite = site
        val session = guard.begin(site.key, site.expectedHost)
        activeSessionId = session.id
        actionsThisSite = 0
        pagesThisSite = 0
        siteStartedAt = System.currentTimeMillis()
        actionInFlight = false
        waitingForHuman = false
        controller.markHumanResume()
        status.text = "Learning ${site.name}\nStarting from ${site.expectedHost}"
        record("SITE_START", "STARTED", site.expectedHost, "", "session=${session.id}; root=${site.startUrl}")
        saveCheckpoint()
        webView.stopLoading()
        webView.loadUrl(site.startUrl)
    }

    private fun mapAndAct() {
        if (stopped || paused || waitingForHuman || actionInFlight) return
        val site = activeSite ?: return
        val elapsed = System.currentTimeMillis() - siteStartedAt
        if (actionsThisSite >= maxActionsPerVisit || elapsed >= maxMinutesPerVisit * 60_000L) {
            moveToNextSite("visit budget reached")
            return
        }
        controller.observe(webView) { result ->
            if (stopped || paused) return@observe
            result.onFailure {
                record("OBSERVE", "ERROR", site.expectedHost, "", it.message.orEmpty())
                moveToNextSite("observe failed")
            }.onSuccess { observation ->
                val actualHost = observation.snapshot.host
                if (!guard.accept(activeSessionId, actualHost)) {
                    record("OBSERVE", "STALE", actualHost, observation.snapshot.routeSignature, "Observation discarded by source-session guard")
                    return@onSuccess
                }
                val percent = (observation.brain.coverageScore * 100).toInt().coerceIn(0, 100)
                status.text = "Learning ${site.name}\n$percent% mapped · ${observation.safeActionsFound} safe controls seen · action ${actionsThisSite + 1}/$maxActionsPerVisit"
                if (observation.controllerState == SiteBrainControllerState.WAITING_FOR_HUMAN) {
                    waitingForHuman = true
                    paused = true
                    pauseButton.isEnabled = false
                    resumeButton.isEnabled = true
                    record("HUMAN_BOUNDARY", "PAUSED", actualHost, observation.snapshot.routeSignature, "Login/CAPTCHA/security check requires you")
                    status.text = "${site.name} needs you\nComplete login/CAPTCHA in the page, then tap Resume."
                    return@onSuccess
                }
                val budget = ExplorerBudget(
                    maxPages = 120,
                    maxActions = maxActionsPerVisit,
                    maxRevisitsPerFingerprint = 2,
                    maxElapsedMs = maxMinutesPerVisit * 60_000L,
                    pagesSeen = pagesThisSite,
                    actionsTaken = actionsThisSite,
                    startedAt = siteStartedAt
                )
                val prepared = controller.prepareExploration(observation, budget, "site brain training")
                if (prepared == null) {
                    record("SITE_PLATEAU", "CHECKPOINTED", actualHost, observation.snapshot.routeSignature, "No new safe action available in this state")
                    handler.postDelayed({ moveToNextSite("novelty plateau") }, 800L)
                    return@onSuccess
                }
                actionInFlight = true
                actionsThisSite++
                record(prepared.semanticIntent, "ATTEMPTED", actualHost, observation.snapshot.routeSignature, "safe action")
                controller.executePrepared(webView, prepared) { accepted ->
                    if (!accepted) {
                        actionInFlight = false
                        record(prepared.semanticIntent, "REJECTED", actualHost, observation.snapshot.routeSignature, "executor did not accept action")
                        handler.postDelayed({ mapAndAct() }, 700L)
                    } else {
                        handler.postDelayed({ verifyAction() }, 1000L)
                    }
                }
            }
        }
    }

    private fun verifyAction() {
        if (stopped || paused || !actionInFlight || !controller.hasPendingExploration()) return
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
                if (verified.after.challengeDetected || verified.after.loginDetected) {
                    waitingForHuman = true
                    paused = true
                    pauseButton.isEnabled = false
                    resumeButton.isEnabled = true
                    record("HUMAN_BOUNDARY", "PAUSED", host, verified.after.routeSignature, "Protected boundary reached after action")
                    status.text = "${activeSite?.name ?: "Site"} needs you\nComplete the website check, then tap Resume."
                    return@onSuccess
                }
                val outcome = if (verified.verification.success) "VERIFIED" else "NOT_VERIFIED"
                if (verified.verification.success) tracker?.recordVerified()
                record("VERIFY", outcome, host, verified.after.routeSignature, verified.verification.evidence.joinToString("; "))
                saveCheckpoint()
                updateCounters()
                handler.postDelayed({ mapAndAct() }, 750L)
            }
        }
    }

    private fun moveToNextSite(reason: String) {
        if (stopped || paused) return
        val site = activeSite
        record("SITE_CHECKPOINT", "SAVED", site?.expectedHost.orEmpty(), "", reason)
        tracker?.nextSite()
        saveCheckpoint()
        updateCounters()
        handler.postDelayed({ startCurrentSite() }, 900L)
    }

    private fun pauseLearning() {
        if (stopped) return
        paused = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = true
        record("LEARNING", "PAUSED", activeSite?.expectedHost.orEmpty(), "", "Paused by user")
        status.text = "Learning paused\nYour Site Brain checkpoint is saved."
        saveCheckpoint()
    }

    private fun resumeLearning() {
        if (stopped) return
        paused = false
        waitingForHuman = false
        pauseButton.isEnabled = true
        resumeButton.isEnabled = false
        controller.markHumanResume()
        record("LEARNING", "RESUMED", activeSite?.expectedHost.orEmpty(), "", "Resumed by user")
        handler.postDelayed({ mapAndAct() }, 500L)
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
        status.text = "Learning stopped\nEverything learned so far is still saved."
    }

    private fun updateCounters() {
        val t = tracker ?: return
        counters.text = "Site ${t.siteIndex + 1}/${sites.size} · verified discoveries ${t.verifiedDiscoveries} · completed passes ${t.completedPasses} · log events ${events.size}"
    }

    private fun record(action: String, outcome: String, host: String, route: String, note: String) {
        val site = activeSite
        events += LearningEvent(
            timestamp = System.currentTimeMillis(),
            source = site?.name ?: "Learning Run",
            host = host,
            pageType = "MAP_SITE",
            route = route,
            action = action,
            outcome = outcome,
            coverageBefore = 0.0,
            coverageAfter = 0.0,
            note = note
        )
        if (events.size > 5000) events.removeAt(0)
        persistLog()
        updateCounters()
    }

    private fun persistLog() {
        runCatching {
            File(filesDir, "site_brain_learning_log.json").writeText(LearningReportWriter.encode(events))
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
        val text = runCatching { File(filesDir, "site_brain_learning_log.json").readText() }
            .getOrElse { LearningReportWriter.encode(events) }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "AI Browser Site Brain Learning Logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share Learning Logs"))
    }

    override fun onDestroy() {
        saveCheckpoint()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
