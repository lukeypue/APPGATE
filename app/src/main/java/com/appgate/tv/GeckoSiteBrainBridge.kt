package com.appgate.tv

import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

class GeckoSiteBrainBridge(
    private val runtime: GeckoRuntime,
    private val session: GeckoSession
) {
    private var port: WebExtension.Port? = null
    var onMessage: ((JSONObject) -> Unit)? = null

    fun install(onReady: (Boolean) -> Unit = {}) {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                val builtIn = extension ?: run {
                    onReady(false)
                    return@accept
                }
                session.webExtensionController.setMessageDelegate(
                    builtIn,
                    object : WebExtension.MessageDelegate {
                        override fun onConnect(port: WebExtension.Port) {
                            this@GeckoSiteBrainBridge.port = port
                            port.setDelegate(object : WebExtension.PortDelegate {
                                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                    val json = message as? JSONObject ?: return
                                    onMessage?.invoke(json)
                                }

                                override fun onDisconnect(port: WebExtension.Port) {
                                    if (this@GeckoSiteBrainBridge.port === port) {
                                        this@GeckoSiteBrainBridge.port = null
                                    }
                                }
                            })
                        }
                    },
                    NATIVE_APP
                )
                onReady(true)
            }, {
                onReady(false)
            })
    }

    fun requestSnapshot(): Boolean =
        post(JSONObject().put("type", "SNAPSHOT_REQUEST"))

    fun ping(): Boolean =
        post(JSONObject().put("type", "PING"))

    fun click(elementId: String): Boolean =
        post(JSONObject()
            .put("type", "ACTION")
            .put("action", "CLICK")
            .put("elementId", elementId))

    fun fill(elementId: String, value: String): Boolean =
        post(JSONObject()
            .put("type", "ACTION")
            .put("action", "FILL")
            .put("elementId", elementId)
            .put("value", value))

    private fun post(message: JSONObject): Boolean {
        val activePort = port ?: return false
        activePort.postMessage(message)
        return true
    }

    companion object {
        const val EXTENSION_ID = "sitebrain@aibrowser.local"
        const val EXTENSION_LOCATION = "resource://android/assets/sitebrain/"
        const val NATIVE_APP = "aibrowser"
    }
}
