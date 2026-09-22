package com.appgate.tv

import android.graphics.Color
import android.os.Bundle
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
    private var lastSnapshot: JSONObject? = null

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
            setViewBackend(GeckoView.BACKEND_SURFACE_VIEW)
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
                status.text = "Facebook Mini Brain connected. Waiting for Marketplace to settle…"
                bridge.requestSnapshot()
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
                val query = miniBrain.trainingQueries().first()
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
        if (::geckoView.isInitialized) runCatching { geckoView.releaseSession() }
        if (::session.isInitialized) runCatching { session.close() }
        super.onDestroy()
    }
}
