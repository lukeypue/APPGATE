package com.appgate.tv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appgate.tv.sitebrain.minibrain.FacebookMarketplaceMiniBrain
import com.appgate.tv.sitebrain.minibrain.MiniBrainControl
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class FacebookGeckoPilotActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var bridge: GeckoSiteBrainBridge
    private lateinit var status: TextView
    private val miniBrain = FacebookMarketplaceMiniBrain()
    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot: JSONObject? = null
    private var firstSnapshotScheduled = false
    private var autoSteps = 0
    private var trainingQueryIndex = 0
    private val attemptedActions = linkedSetOf<String>()
    private val maxAutoSteps = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Facebook Gecko Mini Brain"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 23))
            setPadding(10, 8, 10, 8)
        }

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "Starting Facebook Marketplace in Gecko…"
            setPadding(4, 4, 4, 8)
        }
        root.addView(status)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "SNAPSHOT"
            setOnClickListener {
                if (!bridge.requestSnapshot()) {
                    status.text = "Gecko bridge is not connected yet."
                }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "RELOAD"
            setOnClickListener { session.reload() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "BACK"
            setOnClickListener { session.goBack() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls)

        geckoView = GeckoView(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        session = GeckoSession().apply {
            contentDelegate = object : GeckoSession.ContentDelegate {}
            open(GeckoRuntimeProvider.get(this@FacebookGeckoPilotActivity))
        }
        geckoView.setSession(session)
        root.addView(geckoView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        bridge = GeckoSiteBrainBridge(GeckoRuntimeProvider.get(this), session)
        bridge.onMessage = { message ->
            runOnUiThread { handleBridgeMessage(message) }
        }
        bridge.install { ok ->
            runOnUiThread {
                status.text = if (ok) {
                    "Facebook Gecko bridge ready. Loading Marketplace…"
                } else {
                    "Gecko loaded, but the Site Brain bridge did not install."
                }
            }
        }

        session.loadUri("https://www.facebook.com/marketplace/")
    }

    private fun handleBridgeMessage(message: JSONObject) {
        when (message.optString("type")) {
            "BRIDGE_READY" -> {
                status.text = "Facebook Mini Brain connected. Letting Marketplace finish rendering…"
                if (!firstSnapshotScheduled) {
                    firstSnapshotScheduled = true
                    handler.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            status.text = "Facebook Mini Brain connected. Taking a lightweight snapshot…"
                            bridge.requestSnapshot()
                        }
                    }, 2_500L)
                }
            }
            "SNAPSHOT" -> {
                lastSnapshot = message
                val controls = message.optJSONArray("controls")
                val list = ArrayList<MiniBrainControl>()
                if (controls != null) {
                    for (i in 0 until controls.length()) {
                        val o = controls.optJSONObject(i) ?: continue
                        list += MiniBrainControl(
                            id = o.optString("id"),
                            label = o.optString("label"),
                            tag = o.optString("tag"),
                            role = o.optString("role").takeIf { it.isNotBlank() },
                            href = o.optString("href").takeIf { it.isNotBlank() }
                        )
                    }
                }
                val trainingQueries = miniBrain.trainingQueries()
                val query = trainingQueries[trainingQueryIndex % trainingQueries.size]
                val decision = miniBrain.chooseNext(message.optString("url"), list, query)
                status.text = buildString {
                    append("Facebook Gecko Mini Brain\n")
                    append("Page: ").append(message.optString("title").take(80)).append("\n")
                    append("Controls seen: ").append(list.size).append("\n")
                    if (decision != null) {
                        append("Next learned intent: ").append(decision.intent.name)
                        append("\n").append(decision.reason)
                    } else {
                        append("No reusable Facebook action chosen yet.")
                    }
                }

                // Facebook Marketplace continuously renders in the background, so advance
                // through safe reusable controls instead of waiting for a page-finished state.
                if (decision != null && autoSteps < maxAutoSteps) {
                    val chosen = list.firstOrNull { it.id == decision.controlId }
                    val signature = decision.intent.name + ":" +
                        chosen?.label.orEmpty().lowercase().take(80) +
                        if (decision.intent.name == "SEARCH") ":$query" else ""
                    if (attemptedActions.add(signature)) {
                        autoSteps += 1
                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                status.text = "Facebook Mini Brain learning " +
                                    decision.intent.name.lowercase() +
                                    " controls… step $autoSteps/$maxAutoSteps"
                                decision.controlId?.let { controlId ->
                                    if (decision.intent.name == "SEARCH") {
                                        // Search is a reusable parameterized skill, not just a button click.
                                        // Fill + submit the visible Marketplace search control with varied
                                        // training queries so the brain learns result-page transitions.
                                        bridge.action("FILL", controlId, query)
                                        trainingQueryIndex = (trainingQueryIndex + 1) % trainingQueries.size
                                    } else {
                                        bridge.click(controlId)
                                    }
                                }
                                handler.postDelayed({
                                    if (!isFinishing && !isDestroyed) bridge.requestSnapshot()
                                }, 1_800L)
                            }
                        }, 650L)
                    } else {
                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) bridge.requestSnapshot()
                        }, 2_000L)
                    }
                }
            }
            "ACTION_RESULT" -> {
                status.text = "Facebook action result: " + message.optString("result", message.optString("reason"))
            }
            "BRIDGE_ERROR" -> {
                status.text = "Facebook bridge error: " + message.optString("message")
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::geckoView.isInitialized) runCatching { geckoView.releaseSession() }
        if (::session.isInitialized) runCatching { session.close() }
        super.onDestroy()
    }
}
