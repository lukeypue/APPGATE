package com.appgate.tv

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.view.View
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import com.appgate.tv.sitebrain.PopupDismissal
import com.appgate.tv.sitebrain.PageSnapshot
import com.appgate.tv.sitebrain.SemanticElement
import com.appgate.tv.sitebrain.SiteEdge
import com.appgate.tv.sitebrain.SafeActionClassifier
import com.appgate.tv.sitebrain.ActionKind
import com.appgate.tv.sitebrain.SafetyClass
import com.appgate.tv.sitebrain.CapabilityGapLogger
import com.appgate.tv.sitebrain.AiTeacherKeyStore
import com.appgate.tv.sitebrain.AiTeacherClient
import com.appgate.tv.sitebrain.SiteBrainObservation
import com.appgate.tv.sitebrain.LearningQueryGenerator
import com.appgate.tv.sitebrain.GitHubLogRequest
import com.appgate.tv.sitebrain.GitHubLogRequestClient
import org.json.JSONObject
import java.io.File

class OvernightLearningActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var counters: TextView
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var skipButton: Button
    private lateinit var teachButton: Button
    private lateinit var aiTeacherButton: Button
    private lateinit var requestedLogsButton: Button
    private lateinit var controller: WebViewSiteBrainController
    private lateinit var brainRepository: SiteBrainRepository
    private lateinit var gapLogger: CapabilityGapLogger

    private val handler = Handler(Looper.getMainLooper())
    private val sites = LearningSiteCatalog.defaultSites()
    private val guard = SourceSessionGuard()
    private val events = ArrayList<LearningEvent>()

    private var tracker: LearningProgressTracker? = null
    private var userPaused = false
    private var stopped = false
    private var waitingForHuman = false
    private var authScreenOpen = false
    private var actionInFlight = false
    private var actionsThisSite = 0
    private var pagesThisSite = 0
    private var verifiedThisSite = 0
    private var consecutivePlateaus = 0
    private var siteStartedAt = 0L
    private var lastProgressAt = 0L
    private var activeSessionId = 0L
    private var activeSite: LearningSite? = null
    private var lastCoverage = 0.0
    private var awaitingInitialPage = false
    private val routeActionAttempts = HashMap<String, Int>()
    private val seenRoutesThisVisit = HashSet<String>()
    private var lastLogPersistAt = 0L
    private var eventsAtLastPersist = 0
    private var teachingMode = false
    private var lastHumanTouchAt = 0L
    private var lastObservedSnapshot: PageSnapshot? = null
    private var teacherCallInFlight = false
    private var lastTeacherCallAt = 0L
    private var pendingGitHubLogRequest: GitHubLogRequest? = null
    private var logRequestCheckInFlight = false

    private val githubLogRequestPoller = object : Runnable {
        override fun run() {
            if (!stopped) checkGitHubLogRequest()
            if (!stopped) handler.postDelayed(this, GITHUB_LOG_REQUEST_POLL_MS)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!stopped && !userPaused && !authScreenOpen && !teachingMode) {
                val stalled = LearningRuntimePolicy.stalledForMs(System.currentTimeMillis(), lastProgressAt)
                if (LearningRuntimePolicy.shouldAutoSkip(stalled, waitingForHuman)) {
                    val site = activeSite
                    gapLogger.record("STALLED", site?.name.orEmpty(), site?.expectedHost.orEmpty(), "", "No useful progress for ${stalled / 1000}s before auto-skip")
                    record(
                        "WATCHDOG_AUTO_SKIP",
                        "SKIPPED",
                        site?.expectedHost.orEmpty(),
                        "",
                        "No useful progress for ${stalled / 1000}s; checkpointed and moving on"
                    )
                    skipCurrentSite(auto = true)
                }
            }
            if (!stopped) handler.postDelayed(this, WATCHDOG_POLL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Site Brain — Overnight Learning"

        startKeepAliveService()

        val brainPrefs = getSharedPreferences("site_brain_knowledge", MODE_PRIVATE)
        brainRepository = SiteBrainRepository(SharedPreferencesSiteBrainStore(brainPrefs))
        controller = WebViewSiteBrainController(brainRepository)
        gapLogger = CapabilityGapLogger(this)

        val runPrefs = getSharedPreferences("site_brain_learning_run", MODE_PRIVATE)
        tracker = LearningProgressTracker(
            siteCount = sites.size,
            savedSiteIndex = runPrefs.getInt("site_index", 0),
            savedVerified = runPrefs.getInt("verified", 0),
            savedPasses = runPrefs.getInt("passes", 0)
        )
        loadExistingLog()
        val installedInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val installedVersionName = installedInfo?.versionName.orEmpty().ifBlank { "unknown" }
        val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installedInfo?.longVersionCode ?: 0L else @Suppress("DEPRECATION") (installedInfo?.versionCode?.toLong() ?: 0L)
        record("APP_VERSION", "ACTIVE", "", "", "$installedVersionName ($installedVersionCode)")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 23))
            setPadding(14, 12, 14, 12)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            text = "Preparing overnight Site Brain learning…"
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
            setOnClickListener { skipCurrentSite(auto = false) }
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

        teachButton = Button(this).apply {
            text = "TEACH ME"
            setOnClickListener { toggleTeachMode() }
        }
        root.addView(teachButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        aiTeacherButton = Button(this).apply {
            setOnClickListener { showAiTeacherKeyDialog() }
        }
        root.addView(aiTeacherButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        updateAiTeacherButton()

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = "UPDATE"
            setOnClickListener { startActivity(Intent(this@OvernightLearningActivity, UpdateActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(Button(this).apply {
            text = "SHARE / SAVE LOGS"
            setOnClickListener { shareLogs() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        root.addView(row2)
        root.addView(Button(this).apply {
            text = "SHARE AI GAP LOG"
            setOnClickListener { shareGapLog() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        requestedLogsButton = Button(this).apply {
            text = "CHECK GITHUB LOG REQUEST"
            setOnClickListener {
                val request = pendingGitHubLogRequest
                if (request != null) shareRequestedLogs(request) else checkGitHubLogRequest(forceToast = true)
            }
        }
        root.addView(requestedLogsButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            setTextColor(Color.rgb(170, 185, 205))
            textSize = 12f
            text = "Overnight mode keeps a foreground learning service and CPU wake lock active so training can continue with the screen off. It trains one site at a time, keeps up to 50,000 learning events, checkpoints constantly, and auto-skips a site/state after 30 seconds without useful progress. Login/CAPTCHA/2FA still remain human-only. Tap TEACH ME to demonstrate a hard safe button, filter, dropdown, or popup-close action and the Site Brain will save that interaction."
            setPadding(0, 6, 0, 10)
        })

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            addJavascriptInterface(TeachBridge(), "SiteBrainTeachBridge")
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                    lastHumanTouchAt = System.currentTimeMillis()
                }
                false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (awaitingInitialPage) webView.visibility = View.INVISIBLE
                }

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
                    if (stopped || userPaused || authScreenOpen) return
                    val actual = url.orEmpty()
                    val parsed = Uri.parse(actual)
                    val host = parsed.host.orEmpty()
                    if (!guard.accept(activeSessionId, host)) {
                        record("STALE_OR_WRONG_HOST", "IGNORED", host, parsed.path.orEmpty(), "Ignored callback for another site/session")
                        return
                    }
                    if (awaitingInitialPage) {
                        webView.clearHistory()
                        awaitingInitialPage = false
                    }
                    webView.visibility = View.VISIBLE
                    pagesThisSite++
                    val routeKey = "$host${parsed.path.orEmpty()}"
                    if (seenRoutesThisVisit.add(routeKey)) touchProgress()
                    record("PAGE_LOADED", "OBSERVED", host, parsed.path.orEmpty(), actual)
                    installHumanTeachListener()
                    if (teachingMode) {
                        refreshTeachingSnapshot()
                        return
                    }
                    if (actionInFlight) {
                        handler.postDelayed({ verifyAction() }, 900L)
                    } else {
                        tryDismissBlockingPopup { dismissed ->
                            if (!dismissed) handler.postDelayed({ mapAndAct() }, 650L)
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true || stopped) return
                    val host = request.url.host.orEmpty()
                    if (!guard.accept(activeSessionId, host)) return
                    webView.visibility = View.INVISIBLE
                    gapLogger.record("SITE_LOAD_ERROR", activeSite?.name.orEmpty(), host, request.url.path.orEmpty(), error?.description?.toString().orEmpty())
                    record("PAGE_ERROR", "ERROR", host, request.url.path.orEmpty(), error?.description?.toString().orEmpty())
                    status.text = "Learning ${activeSite?.name ?: "site"}\nSite page did not load; retrying without showing stale content."
                    handler.postDelayed({ recoverOrMove("page error") }, 1200L)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        updateCounters()
        handler.postDelayed(watchdog, WATCHDOG_POLL_MS)
        handler.postDelayed(githubLogRequestPoller, 8_000L)
        startCurrentSite()
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, LearningKeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun allowNavigation(rawUrl: String): Boolean {
        val site = activeSite ?: return false
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
        waitingForHuman = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = false
        record("HUMAN_AUTH", "OPENED", Uri.parse(authUrl).host.orEmpty(), Uri.parse(authUrl).path.orEmpty(), "Opened separate human-only sign-in screen")
        status.text = "${site.name} sign-in opened\nFinish login in the sign-in screen, then return here."
        startActivityForResult(Intent(this, HumanSignInActivity::class.java).apply {
            putExtra("authUrl", authUrl)
            putExtra("targetHost", site.expectedHost)
            putExtra("targetName", site.name)
        }, AUTH_REQUEST)
    }

    private fun startCurrentSite() {
        if (stopped || userPaused || sites.isEmpty()) return
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
        routeActionAttempts.clear()
        seenRoutesThisVisit.clear()
        lastObservedSnapshot = null
        siteStartedAt = System.currentTimeMillis()
        lastProgressAt = siteStartedAt
        actionInFlight = false
        waitingForHuman = false
        authScreenOpen = false
        controller.markHumanResume()
        awaitingInitialPage = true
        webView.visibility = View.INVISIBLE
        status.text = "Learning ${site.name}\nLoading this site in a fresh visual session…"
        record("SITE_START", "STARTED", site.expectedHost, "", "root=${site.startUrl}; overnight=true")
        saveCheckpoint()
        webView.stopLoading()
        webView.loadUrl(site.startUrl)
    }

    private fun mapAndAct() {
        if (stopped || userPaused || waitingForHuman || actionInFlight || authScreenOpen || teachingMode) return
        val site = activeSite ?: return
        val elapsed = System.currentTimeMillis() - siteStartedAt
        if (actionsThisSite >= LearningRuntimePolicy.maxActionsPerVisit || elapsed >= LearningRuntimePolicy.maxMinutesPerVisit * 60_000L) {
            moveToNextSite("deep overnight visit budget reached")
            return
        }

        controller.observe(webView) { result ->
            if (stopped || userPaused || authScreenOpen) return@observe
            result.onFailure {
                record("OBSERVE", "ERROR", site.expectedHost, "", it.message.orEmpty())
                recoverOrMove("observe failed")
            }.onSuccess { observation ->
                val actualHost = observation.snapshot.host
                if (!guard.accept(activeSessionId, actualHost)) {
                    record("OBSERVE", "STALE", actualHost, observation.snapshot.routeSignature, "Observation discarded by source-session guard")
                    return@onSuccess
                }
                lastObservedSnapshot = observation.snapshot
                lastCoverage = observation.brain.coverageScore
                val percent = (lastCoverage * 100).toInt().coerceIn(0, 100)
                status.text = "Learning ${site.name}\n$percent% mapped · ${observation.safeActionsFound} safe controls · $verifiedThisSite verified here · action ${actionsThisSite + 1}/${LearningRuntimePolicy.maxActionsPerVisit}"

                if (observation.controllerState == SiteBrainControllerState.WAITING_FOR_HUMAN) {
                    waitForHuman(actualHost, observation.snapshot.routeSignature, "Login/CAPTCHA/security check requires you")
                    return@onSuccess
                }

                val budget = ExplorerBudget(
                    maxPages = 5000,
                    maxActions = LearningRuntimePolicy.maxActionsPerVisit,
                    maxRevisitsPerFingerprint = 100,
                    maxElapsedMs = LearningRuntimePolicy.maxMinutesPerVisit * 60_000L,
                    pagesSeen = pagesThisSite,
                    actionsTaken = actionsThisSite,
                    startedAt = siteStartedAt
                )
                val trainingQuery = LearningQueryGenerator.nextQuery(site.key, site.name, site.expectedHost, actionsThisSite)
                val prepared = controller.prepareExploration(observation, budget, trainingQuery)
                if (prepared == null) {
                    if (!requestAiTeacher(observation, "No safe unexplored action was available")) {
                        handlePlateau(actualHost, observation.snapshot.routeSignature)
                    }
                    return@onSuccess
                }

                val attemptKey = "$actualHost|${observation.snapshot.routeSignature}|${prepared.semanticIntent}"
                val previousAttempts = routeActionAttempts[attemptKey] ?: 0
                if (!LearningRuntimePolicy.shouldTrySameRouteAction(previousAttempts)) {
                    record(
                        "DUPLICATE_BRANCH",
                        "SUPPRESSED",
                        actualHost,
                        observation.snapshot.routeSignature,
                        "Already tried ${prepared.semanticIntent} $previousAttempts times on this route; forcing a different branch"
                    )
                    handlePlateau(actualHost, observation.snapshot.routeSignature)
                    return@onSuccess
                }
                routeActionAttempts[attemptKey] = previousAttempts + 1

                actionInFlight = true
                actionsThisSite++
                record(prepared.semanticIntent, "ATTEMPTED", actualHost, observation.snapshot.routeSignature, "safe action", lastCoverage, lastCoverage)
                controller.executePrepared(webView, prepared) { accepted ->
                    if (!accepted) {
                        actionInFlight = false
                        gapLogger.record("ACTION_REJECTED", site.name, actualHost, observation.snapshot.routeSignature, prepared.semanticIntent)
                        record(prepared.semanticIntent, "REJECTED", actualHost, observation.snapshot.routeSignature, "executor did not accept action")
                        if (!requestAiTeacher(observation, "Executor rejected ${prepared.semanticIntent}")) {
                            handler.postDelayed({ mapAndAct() }, 650L)
                        }
                    } else {
                        handler.postDelayed({ verifyAction() }, 1100L)
                    }
                }
            }
        }
    }

    private fun updateAiTeacherButton() {
        if (!::aiTeacherButton.isInitialized) return
        aiTeacherButton.text = if (AiTeacherKeyStore.isConfigured(this)) "AI TEACHER: ON" else "SET AI TEACHER KEY"
    }

    private fun showAiTeacherKeyDialog() {
        val input = EditText(this).apply {
            hint = "OpenAI API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("AI Teacher")
            .setMessage("For this test build the key is encrypted with Android Keystore and stays on this phone. The teacher sends only generic site/control structure, not cookies, passwords, or full browsing text.")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val key = input.text?.toString().orEmpty().trim()
                if (key.isNotBlank()) {
                    runCatching { AiTeacherKeyStore.save(this, key) }
                        .onSuccess { Toast.makeText(this, "AI Teacher enabled.", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this, "Could not save the AI Teacher key.", Toast.LENGTH_LONG).show() }
                    updateAiTeacherButton()
                }
            }
            .setNegativeButton("CANCEL", null)
        if (AiTeacherKeyStore.isConfigured(this)) {
            dialog.setNeutralButton("CLEAR KEY") { _, _ ->
                AiTeacherKeyStore.clear(this)
                updateAiTeacherButton()
                Toast.makeText(this, "AI Teacher disabled.", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun requestAiTeacher(observation: SiteBrainObservation, reason: String): Boolean {
        val key = AiTeacherKeyStore.load(this) ?: return false
        val site = activeSite
        val trainingIntent = LearningQueryGenerator.nextQuery(site?.key.orEmpty(), site?.name.orEmpty(), observation.snapshot.host, actionsThisSite)
        val teacherReason = "Autonomous training goal: $trainingIntent. $reason"
        val now = System.currentTimeMillis()
        if (teacherCallInFlight || now - lastTeacherCallAt < AI_TEACHER_COOLDOWN_MS) return false
        teacherCallInFlight = true
        lastTeacherCallAt = now
        val source = activeSite?.name.orEmpty()
        val host = observation.snapshot.host
        val route = observation.snapshot.routeSignature
        status.text = "Learning ${activeSite?.name ?: "site"}\nAI Teacher is studying a hard control…"
        record("AI_TEACHER", "REQUESTED", host, route, teacherReason)
        AiTeacherClient.suggest(key, observation.snapshot, teacherReason) { result ->
            runOnUiThread {
                teacherCallInFlight = false
                if (stopped || userPaused || teachingMode || authScreenOpen) return@runOnUiThread
                result.onFailure { error ->
                    gapLogger.record("AI_TEACHER_ERROR", source, host, route, error.message.orEmpty())
                    record("AI_TEACHER", "ERROR", host, route, error.message.orEmpty())
                    handlePlateau(host, route)
                }.onSuccess teacherSuccess@ { suggestion ->
                    val detail = buildString {
                        append(suggestion.diagnosis)
                        suggestion.capabilityGap?.let { append(" | gap=").append(it) }
                    }
                    record("AI_TEACHER", "ANSWERED", host, route, detail)
                    if (suggestion.needsEngineCode || suggestion.actionKind == null || suggestion.targetElementId == null) {
                        gapLogger.record("AI_ENGINEER_NEEDED", source, host, route, detail)
                        handlePlateau(host, route)
                        return@teacherSuccess
                    }
                    controller.observe(webView) { currentResult ->
                        currentResult.onFailure {
                            gapLogger.record("AI_TEACHER_RECHECK_FAILED", source, host, route, it.message.orEmpty())
                            handler.postDelayed({ mapAndAct() }, 600L)
                        }.onSuccess currentSuccess@ { current ->
                            if (!guard.accept(activeSessionId, current.snapshot.host)) return@currentSuccess
                            val prepared = controller.prepareTeacherExploration(
                                current,
                                suggestion.targetElementId,
                                suggestion.actionKind,
                                LearningQueryGenerator.nextQuery(activeSite?.key.orEmpty(), activeSite?.name.orEmpty(), current.snapshot.host, actionsThisSite)
                            )
                            if (prepared == null) {
                                gapLogger.record("AI_TEACHER_UNUSABLE", source, host, route, detail)
                                handlePlateau(host, route)
                                return@currentSuccess
                            }
                            actionInFlight = true
                            actionsThisSite++
                            record(prepared.semanticIntent, "AI_TEACHER_ATTEMPTED", current.snapshot.host, current.snapshot.routeSignature, detail)
                            controller.executePrepared(webView, prepared) { accepted ->
                                if (!accepted) {
                                    actionInFlight = false
                                    gapLogger.record("AI_TEACHER_ACTION_REJECTED", source, host, route, prepared.semanticIntent)
                                    handler.postDelayed({ mapAndAct() }, 650L)
                                } else {
                                    handler.postDelayed({ verifyAction() }, 1100L)
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    private fun tryDismissBlockingPopup(done: (Boolean) -> Unit) {
        webView.evaluateJavascript(PopupDismissal.javascript()) { raw ->
            val decoded = raw.orEmpty().trim().trim('"')
            if (decoded.startsWith("DISMISSED:")) {
                gapLogger.record("POPUP_DISMISSED", activeSite?.name.orEmpty(), activeSite?.expectedHost.orEmpty(), "", decoded.removePrefix("DISMISSED:"))
                record("POPUP_DISMISS", "VERIFIED", activeSite?.expectedHost.orEmpty(), "", decoded.removePrefix("DISMISSED:"))
                touchProgress()
                handler.postDelayed({ mapAndAct() }, 450L)
                done(true)
            } else {
                done(false)
            }
        }
    }

    private fun toggleTeachMode() {
        if (stopped) return
        teachingMode = !teachingMode
        if (teachingMode) {
            actionInFlight = false
            controller = WebViewSiteBrainController(brainRepository)
            teachButton.text = "DONE TEACHING"
            pauseButton.isEnabled = false
            resumeButton.isEnabled = false
            status.text = "Teach mode\nTap the hard safe button, dropdown, filter, or popup-close action yourself. The brain will record what you clicked."
            installHumanTeachListener()
            refreshTeachingSnapshot()
            record("TEACH_MODE", "STARTED", activeSite?.expectedHost.orEmpty(), "", "Human demonstration mode started")
        } else {
            teachButton.text = "TEACH ME"
            pauseButton.isEnabled = true
            resumeButton.isEnabled = false
            controller = WebViewSiteBrainController(brainRepository)
            touchProgress()
            record("TEACH_MODE", "ENDED", activeSite?.expectedHost.orEmpty(), "", "Human demonstration mode ended")
            handler.postDelayed({ mapAndAct() }, 500L)
        }
    }

    private fun refreshTeachingSnapshot() {
        controller.observe(webView) { result ->
            result.onSuccess { observation ->
                lastObservedSnapshot = observation.snapshot
                lastCoverage = observation.brain.coverageScore
            }
        }
    }

    private fun installHumanTeachListener() {
        val script = """
            (function(){
              if(window.__siteBrainTeachInstalled) return 'READY';
              window.__siteBrainTeachInstalled=true;
              function clean(v){return (v||'').replace(/\s+/g,' ').trim();}
              function locator(el){
                if(el.id) return '#'+CSS.escape(el.id);
                var n=el.getAttribute('name');
                if(n) return el.tagName.toLowerCase()+'[name="'+n.replace(/"/g,'')+'"]';
                var a=el.getAttribute('aria-label');
                if(a) return '[aria-label="'+a.replace(/"/g,'')+'"]';
                return el.tagName.toLowerCase();
              }
              function capture(e){
                if(!e.isTrusted || !window.SiteBrainTeachBridge) return;
                var el=e.target && e.target.closest ? e.target.closest('button,a,input,select,[role="button"],[role="option"],[role="combobox"],[aria-label]') : e.target;
                if(!el) return;
                var p=el.parentElement;
                var payload={
                  host:location.host.toLowerCase(),
                  tag:(el.tagName||'').toLowerCase(),
                  role:el.getAttribute('role')||'',
                  label:clean(el.getAttribute('aria-label')||el.getAttribute('title')||el.innerText||el.textContent||el.value||''),
                  href:el.href||'',
                  inputType:(el.getAttribute('type')||'').toLowerCase(),
                  nearbyText:p?clean(p.innerText||p.textContent||'').slice(0,220):'',
                  locator:locator(el)
                };
                SiteBrainTeachBridge.onHumanClick(JSON.stringify(payload));
              }
              document.addEventListener('click',capture,true);
              document.addEventListener('change',capture,true);
              return 'READY';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    inner class TeachBridge {
        @JavascriptInterface
        fun onHumanClick(json: String) {
            runOnUiThread { handleHumanClick(json) }
        }
    }

    private fun handleHumanClick(json: String) {
        if (!teachingMode || stopped || System.currentTimeMillis() - lastHumanTouchAt > 2_000L) return
        val before = lastObservedSnapshot ?: return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val host = obj.optString("host")
        if (!guard.accept(activeSessionId, host)) return
        val element = SemanticElement(
            id = "human",
            tag = obj.optString("tag"),
            role = obj.optString("role").takeIf { it.isNotBlank() },
            label = obj.optString("label").take(180),
            href = obj.optString("href").takeIf { it.isNotBlank() },
            inputType = obj.optString("inputType").takeIf { it.isNotBlank() },
            selected = false,
            disabled = false,
            nearbyText = obj.optString("nearbyText").take(220),
            locatorHints = listOfNotNull(obj.optString("locator").takeIf { it.isNotBlank() })
        )
        if (SafeActionClassifier.classify(element) != SafetyClass.SAFE) {
            record("HUMAN_DEMO", "IGNORED_UNSAFE", host, before.routeSignature, element.label)
            return
        }
        var kind = SafeActionClassifier.inferActionKind(element)
        if (kind == ActionKind.UNKNOWN && PopupDismissal.isSafeDismissLabel(element.label)) kind = ActionKind.EXPAND
        if (kind == ActionKind.UNKNOWN) kind = ActionKind.NAVIGATE
        val edgeId = ("human|${before.fingerprint}|${kind.name}|${element.label.lowercase()}|${element.href.orEmpty()}").hashCode().toUInt().toString(16)
        val edge = SiteEdge(
            id = edgeId,
            fromFingerprint = before.fingerprint,
            toFingerprint = null,
            actionKind = kind,
            semanticIntent = "HUMAN_DEMO:${kind.name}:${element.label.take(100)}",
            label = element.label,
            safetyClass = SafetyClass.SAFE,
            locatorHints = element.locatorHints,
            expectedPageType = null,
            observedPostcondition = "demonstrated by user",
            confidence = 0.70,
            successCount = 1,
            failureCount = 0,
            lastVerifiedAt = System.currentTimeMillis()
        )
        brainRepository.recordTransition(host, before.fingerprint, edge, null)
        tracker?.recordVerified()
        verifiedThisSite++
        touchProgress()
        gapLogger.record("RESOLVED_BY_HUMAN", activeSite?.name.orEmpty(), host, before.routeSignature, "${kind.name}:${element.label}", resolvedByHuman = true)
        record("HUMAN_DEMO", "LEARNED", host, before.routeSignature, "${kind.name}:${element.label}")
        handler.postDelayed({ refreshTeachingSnapshot() }, 700L)
    }

    private fun handlePlateau(host: String, route: String) {
        consecutivePlateaus++
        if (consecutivePlateaus >= 3) gapLogger.record("PLATEAU", activeSite?.name.orEmpty(), host, route, "Repeated plateau $consecutivePlateaus")
        record("SITE_PLATEAU", "CHECKPOINTED", host, route, "Plateau $consecutivePlateaus; recovery continues until watchdog/budget decides to move on")
        saveCheckpoint()
        controller.markHumanResume()
        when {
            consecutivePlateaus % 3 == 0 -> activeSite?.startUrl?.takeIf { it.isNotBlank() }?.let { webView.loadUrl(it) }
            webView.canGoBack() && consecutivePlateaus % 2 == 0 -> webView.goBack()
            else -> webView.evaluateJavascript("(function(){window.scrollBy(0,Math.max(window.innerHeight*0.85,700));return 'SCROLLED';})();") {
                handler.postDelayed({ mapAndAct() }, 900L)
            }
        }
    }

    private fun recoverOrMove(reason: String) {
        consecutivePlateaus++
        record("RECOVERY", "RETRYING", activeSite?.expectedHost.orEmpty(), "", reason)
        controller.markHumanResume()
        handler.postDelayed({ webView.reload() }, 1200L)
    }

    private fun verifyAction() {
        if (stopped || userPaused || authScreenOpen || !actionInFlight || !controller.hasPendingExploration()) return
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
                    touchProgress()
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
        pauseButton.isEnabled = false
        resumeButton.isEnabled = true
        skipButton.isEnabled = true
        record("HUMAN_BOUNDARY", "WAITING_30_SECONDS", host, route, reason)
        status.text = "${activeSite?.name ?: "Site"} needs you\nComplete login/CAPTCHA and tap Resume. If nothing happens, the watchdog will checkpoint and auto-skip after 30 seconds."
        saveCheckpoint()
    }

    private fun moveToNextSite(reason: String, force: Boolean = false) {
        if (stopped || (!force && userPaused)) return
        val site = activeSite
        actionInFlight = false
        waitingForHuman = false
        authScreenOpen = false
        record("SITE_CHECKPOINT", "SAVED", site?.expectedHost.orEmpty(), "", reason)
        val beforePasses = tracker?.completedPasses ?: 0
        tracker?.nextSite()
        val afterPasses = tracker?.completedPasses ?: beforePasses
        if (afterPasses > beforePasses) {
            record(
                "SITE_PASS",
                "COMPLETED",
                site?.expectedHost.orEmpty(),
                "",
                "Completed full ${sites.size}-site sweep #$afterPasses; starting next deepening pass"
            )
        }
        saveCheckpoint()
        updateCounters()
        handler.postDelayed({ startCurrentSite() }, 900L)
    }

    private fun skipCurrentSite(auto: Boolean) {
        if (stopped) return
        val site = activeSite
        webView.stopLoading()
        awaitingInitialPage = true
        webView.visibility = View.INVISIBLE
        waitingForHuman = false
        authScreenOpen = false
        actionInFlight = false
        record(
            if (auto) "SITE_AUTO_SKIP" else "SITE_SKIP",
            "SKIPPED",
            site?.expectedHost.orEmpty(),
            "",
            if (auto) "Watchdog skip; learned knowledge retained" else "Skipped by user; learned knowledge retained"
        )
        moveToNextSite(if (auto) "30-second watchdog" else "skipped by user", true)
    }

    private fun pauseLearning() {
        if (stopped) return
        userPaused = true
        pauseButton.isEnabled = false
        resumeButton.isEnabled = true
        record("LEARNING", "PAUSED", activeSite?.expectedHost.orEmpty(), "", "Paused by user")
        status.text = "Learning paused\nCheckpoint and logs are saved."
        saveCheckpoint()
    }

    private fun resumeLearning() {
        if (stopped || authScreenOpen) return
        userPaused = false
        waitingForHuman = false
        pauseButton.isEnabled = true
        resumeButton.isEnabled = false
        skipButton.isEnabled = true
        controller.markHumanResume()
        touchProgress()
        record("LEARNING", "RESUMED", activeSite?.expectedHost.orEmpty(), "", "Resumed by user")
        CookieManager.getInstance().flush()
        webView.reload()
    }

    private fun stopLearning() {
        stopped = true
        userPaused = false
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        CookieManager.getInstance().flush()
        record("LEARNING", "STOPPED", activeSite?.expectedHost.orEmpty(), "", "Stopped by user; checkpoint retained")
        saveCheckpoint()
        persistLog(force = true)
        stopService(Intent(this, LearningKeepAliveService::class.java))
        pauseButton.isEnabled = false
        resumeButton.isEnabled = false
        skipButton.isEnabled = false
        status.text = "Learning stopped\nEverything learned so far is still saved."
    }

    private fun touchProgress() {
        lastProgressAt = System.currentTimeMillis()
    }

    private fun updateCounters() {
        if (!::counters.isInitialized) return
        val t = tracker ?: return
        val stalled = LearningRuntimePolicy.stalledForMs(System.currentTimeMillis(), lastProgressAt) / 1000
        counters.text = "Site ${t.siteIndex + 1}/${sites.size} · verified ${t.verifiedDiscoveries} · passes ${t.completedPasses} · logs ${events.size} · no-progress ${stalled}s"
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
        while (events.size > LearningRuntimePolicy.maxLogEvents) events.removeAt(0)
        persistLog()
        updateCounters()
    }

    private fun loadExistingLog() {
        runCatching {
            val file = File(filesDir, LOG_FILE)
            if (file.exists()) {
                events.addAll(LearningReportWriter.decode(file.readText()).takeLast(LearningRuntimePolicy.maxLogEvents))
                eventsAtLastPersist = events.size
                lastLogPersistAt = System.currentTimeMillis()
            }
        }
    }

    private fun persistLog(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val eventsSince = (events.size - eventsAtLastPersist).coerceAtLeast(0)
        val elapsed = (now - lastLogPersistAt).coerceAtLeast(0L)
        if (!force && !LearningRuntimePolicy.shouldPersistLog(eventsSince, elapsed)) return
        runCatching {
            File(filesDir, LOG_FILE).writeText(LearningReportWriter.encode(events, "site_brain_learning_run"))
            eventsAtLastPersist = events.size
            lastLogPersistAt = now
        }
    }

    private fun saveCheckpoint() {
        val t = tracker ?: return
        getSharedPreferences("site_brain_learning_run", MODE_PRIVATE).edit()
            .putInt("site_index", t.siteIndex)
            .putInt("verified", t.verifiedDiscoveries)
            .putInt("passes", t.completedPasses)
            .putLong("saved_at", System.currentTimeMillis())
            .putLong("last_progress_at", lastProgressAt)
            .apply()
        persistLog()
    }

    private fun checkGitHubLogRequest(forceToast: Boolean = false) {
        if (logRequestCheckInFlight) return
        logRequestCheckInFlight = true
        Thread {
            val result = GitHubLogRequestClient.fetch()
            runOnUiThread {
                logRequestCheckInFlight = false
                result.onFailure {
                    if (forceToast) Toast.makeText(this, "Could not check GitHub log request right now.", Toast.LENGTH_LONG).show()
                }.onSuccess { request ->
                    if (request == null || !GitHubLogRequestClient.isNew(this, request) || pendingGitHubLogRequest?.requestId == request.requestId) {
                        if (forceToast) Toast.makeText(this, "No new GitHub log request.", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    pendingGitHubLogRequest = request
                    requestedLogsButton.text = "SEND BOTH REQUESTED LOGS"
                    record("GITHUB_LOG_REQUEST", "RECEIVED", activeSite?.expectedHost.orEmpty(), "", request.requestId)
                    Toast.makeText(this, "GitHub requested both Site Brain logs. Tap SEND BOTH REQUESTED LOGS.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareRequestedLogs(request: GitHubLogRequest) {
        persistLog(force = true)
        val learning = File(filesDir, LOG_FILE)
        if (!learning.exists()) {
            Toast.makeText(this, "The learning log is not ready yet.", Toast.LENGTH_LONG).show()
            return
        }
        val bundle = runCatching {
            GitHubLogRequestClient.buildBundle(this, request, learning, gapLogger.fileOrNull())
        }.getOrElse {
            Toast.makeText(this, "Could not package the requested logs.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", bundle)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, "AI Browser Requested Logs ${request.requestId}")
            putExtra(Intent.EXTRA_TEXT, "GitHub request ${request.requestId}. Bundle contains the Site Brain learning log and AI capability-gap log when available.")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("AI Browser requested logs", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        GitHubLogRequestClient.markHandled(this, request)
        pendingGitHubLogRequest = null
        requestedLogsButton.text = "CHECK GITHUB LOG REQUEST"
        record("GITHUB_LOG_REQUEST", "BUNDLED", activeSite?.expectedHost.orEmpty(), "", request.requestId)
        startActivity(Intent.createChooser(intent, "Send both requested logs"))
    }

    private fun shareLogs() {
        persistLog(force = true)
        val file = File(filesDir, LOG_FILE)
        if (!file.exists()) {
            Toast.makeText(this, "No learning log file exists yet.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "AI Browser Site Brain Learning Logs")
            putExtra(Intent.EXTRA_TEXT, "Attached Site Brain learning log (${events.size} events).")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Site Brain learning log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Toast.makeText(this, "Learning log attached as a JSON file.", Toast.LENGTH_SHORT).show()
        startActivity(Intent.createChooser(intent, "Share / Save Learning Logs"))
    }

    private fun shareGapLog() {
        val file = gapLogger.fileOrNull()
        if (file == null) {
            Toast.makeText(this, "No AI capability gaps have been recorded yet.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_SUBJECT, "AI Browser Capability Gap Log")
            putExtra(Intent.EXTRA_TEXT, "Separate Site Brain capability-gap log for the AI Teacher / Engineer.")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("AI capability gap log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share AI Capability Gap Log"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST) return
        authScreenOpen = false
        if (resultCode == RESULT_OK) {
            waitingForHuman = false
            pauseButton.isEnabled = true
            resumeButton.isEnabled = false
            controller.markHumanResume()
            touchProgress()
            record("HUMAN_AUTH", "RETURNED", activeSite?.expectedHost.orEmpty(), "", "Human sign-in completed; reloading target site")
            webView.reload()
        } else {
            waitingForHuman = true
            resumeButton.isEnabled = true
            status.text = "Sign-in screen closed\nTap Resume to retry, Skip Site, or let the watchdog move on."
        }
    }

    override fun onDestroy() {
        saveCheckpoint()
        persistLog(force = true)
        if (stopped) handler.removeCallbacksAndMessages(null)
        runCatching { CookieManager.getInstance().flush() }
        super.onDestroy()
    }

    companion object {
        private const val AUTH_REQUEST = 4201
        private const val LOG_FILE = "site_brain_learning_log.json"
        private const val WATCHDOG_POLL_MS = 5_000L
        private const val AI_TEACHER_COOLDOWN_MS = 45_000L
        private const val GITHUB_LOG_REQUEST_POLL_MS = 60_000L
    }
}
