package com.appgate.tv

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * AppGate v1: one lightweight, remote-friendly TikTok web experience.
 *
 * There is no URL bar and no generic site launcher. D-pad moves AppGate's
 * pointer, OK clicks, Channel Down/Fast Forward move to the next feed item,
 * Channel Up/Rewind return to the previous item, and Menu opens AppGate help,
 * Home and verified hashtag/profile search.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView

    private var cursorX = 0f
    private var cursorY = 0f
    private var keyboardRequested = false
    private var feedMoveInProgress = false
    private var activeDialog: AlertDialog? = null

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var lastGoodUrl: String = TikTokNavigation.HOME_URL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        cursor = CursorView(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        setContentView(root)

        val restoreUrl = savedInstanceState?.getString(STATE_URL)
            ?.takeUnless { TikTokNavigation.shouldBlock(it) }
            ?: TikTokNavigation.HOME_URL
        lastGoodUrl = restoreUrl
        buildWebView(restoreUrl)

        root.addView(
            cursor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.post {
            cursorX = savedInstanceState?.getFloat(STATE_CURSOR_X)
                ?.takeIf { it > 0f } ?: root.width / 2f
            cursorY = savedInstanceState?.getFloat(STATE_CURSOR_Y)
                ?.takeIf { it > 0f } ?: root.height / 2f
            cursor.setPos(cursorX, cursorY)
        }

        root.postDelayed({
            Toast.makeText(
                this,
                "Menu: AppGate controls/search  •  CH ↓ or FF: next  •  CH ↑ or RW: previous",
                Toast.LENGTH_LONG
            ).show()
        }, 800L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(restoreUrl: String) {
        webView = WebView(this)
        root.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            // Deliberately keep Amazon WebView's real/default user-agent. Silk
            // was stable on the test Fire TV; spoofing a different Chrome build
            // can give modern TikTok contradictory UA/client-hint information.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) offscreenPreRaster = false
        }
        webView.setInitialScale(0)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (TikTokNavigation.shouldBlock(url)) {
                    if (request.isForMainFrame) showBlockedLinkMessage()
                    return true
                }
                return false
            }

            @Deprecated("Required for WebView navigation on API 22")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (TikTokNavigation.shouldBlock(url)) {
                    showBlockedLinkMessage()
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!TikTokNavigation.shouldBlock(url)) lastGoodUrl = url
                injectTvCleanup()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@BrowserActivity,
                        "TikTok did not load. Press Menu for Home or Search.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                val restore = view.url
                    ?.takeUnless { TikTokNavigation.shouldBlock(it) }
                    ?: lastGoodUrl
                root.removeView(view)
                view.destroy()
                buildWebView(restore)
                Toast.makeText(
                    this@BrowserActivity,
                    "AppGate reloaded TikTok after low memory",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                cursor.visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback = null
                webView.visibility = View.VISIBLE
                cursor.visibility = View.VISIBLE
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // v1 needs no camera/microphone/device permissions.
                request.deny()
            }
        }

        webView.loadUrl(restoreUrl)
    }

    /**
     * Keep cleanup intentionally narrow. Do not resize html/body, force 100vh,
     * or scale the whole document: those approaches moved hit targets and cut
     * off controls during earlier Fire TV testing.
     */
    private fun injectTvCleanup() {
        val script = """
            (function(){
              var style = document.getElementById('appgate-tv-css');
              if (!style) {
                style = document.createElement('style');
                style.id = 'appgate-tv-css';
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = `
                [class*="download-app"], [class*="app-banner"], [class*="AppBanner"],
                [class*="open-app"], [class*="OpenApp"], [class*="smart-banner"],
                [id*="smart-banner"], [data-e2e="download-app"],
                [data-e2e="download-guide"], [class*="DivDownload"],
                [class*="GuideContainer"], [class*="BottomBanner"] {
                  display: none !important;
                }
              `;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    // ---------------- Remote handling ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        activeDialog?.takeIf { it.isShowing }?.let {
            return super.dispatchKeyEvent(event)
        }

        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if ((keyboardRequested || inputMethod.isAcceptingText) &&
            event.keyCode != KeyEvent.KEYCODE_BACK
        ) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (HANDLED_KEYS.contains(event.keyCode)) true
            else super.dispatchKeyEvent(event)
        }

        val baseMovement = if (event.repeatCount > 3) 44f else 19f
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursor(-baseMovement, 0f); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursor(baseMovement, 0f); return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursor(0f, -baseMovement); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(0f, baseMovement); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                clickAt(cursorX, cursorY); return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                moveFeed(next = true); return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                moveFeed(next = false); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                runJs(JS_TOGGLE_VIDEO); return true
            }
            KeyEvent.KEYCODE_MENU -> {
                showAppGateMenu(); return true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBack(); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showAppGateMenu() {
        if (activeDialog?.isShowing == true) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("AppGate")
            .setMessage(
                "TikTok web experience — supported now\n" +
                    "More web services coming later.\n\n" +
                    "D-pad: move pointer\n" +
                    "OK: click\n" +
                    "Channel Down or Fast Forward: next video\n" +
                    "Channel Up or Rewind: previous video\n" +
                    "Play/Pause: play or pause\n" +
                    "Back: previous page / exit\n" +
                    "Menu: AppGate controls\n\n" +
                    "Search uses TV-friendly TikTok hashtag/profile pages. " +
                    "Comments can be read; posting is not guaranteed in this first version."
            )
            .setPositiveButton("Search") { _, _ -> root.post { showSearchDialog() } }
            .setNeutralButton("Home") { _, _ -> webView.loadUrl(TikTokNavigation.HOME_URL) }
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        activeDialog = dialog
        dialog.show()
    }

    private fun showSearchDialog() {
        if (activeDialog?.isShowing == true) return
        val input = EditText(this).apply {
            hint = "cats or @username"
            isSingleLine = true
            setPadding(32, 12, 32, 12)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Search TikTok")
            .setMessage("Enter a topic/hashtag, or type @username for a profile.")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val target = TikTokNavigation.destinationForSearch(input.text?.toString().orEmpty())
                if (target == null) {
                    Toast.makeText(this, "Enter a topic or @username", Toast.LENGTH_SHORT).show()
                } else {
                    webView.loadUrl(target)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 120L)
        }
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        activeDialog = dialog
        dialog.show()
    }

    private fun showBlockedLinkMessage() {
        Toast.makeText(
            this,
            "TikTok tried to open its phone app. AppGate kept you on TV. Use Menu → Search if needed.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun moveFeed(next: Boolean) {
        if (feedMoveInProgress) return
        feedMoveInProgress = true

        webView.evaluateJavascript(FEED_FINGERPRINT) { before ->
            dispatchWebArrow(next)
            webView.postDelayed({
                webView.evaluateJavascript(FEED_FINGERPRINT) { afterArrow ->
                    if (before != afterArrow) {
                        feedMoveInProgress = false
                        return@evaluateJavascript
                    }

                    runJs(if (next) JS_FEED_NEXT else JS_FEED_PREVIOUS)
                    webView.postDelayed({
                        webView.evaluateJavascript(FEED_FINGERPRINT) { afterJs ->
                            if (before == afterJs) swipeFeed(next)
                            webView.postDelayed({ feedMoveInProgress = false }, 380L)
                        }
                    }, 420L)
                }
            }, 460L)
        }
    }

    /** Sends a real key event to the web renderer before using fallbacks. */
    private fun dispatchWebArrow(next: Boolean) {
        val keyCode = if (next) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        val downTime = SystemClock.uptimeMillis()
        webView.dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        webView.dispatchKeyEvent(KeyEvent(downTime, downTime + 20, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** Last-resort in-process touch flick for a feed that ignores keys/scroll. */
    private fun swipeFeed(next: Boolean) {
        val x = root.width / 2f
        val height = root.height.toFloat()
        val startY = if (next) height * 0.78f else height * 0.22f
        val endY = if (next) height * 0.22f else height * 0.78f
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime

        fun send(action: Int, y: Float) {
            val motion = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(motion)
            motion.recycle()
        }

        send(MotionEvent.ACTION_DOWN, startY)
        for (step in 1..10) {
            eventTime += 10
            send(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 10f)
        }
        eventTime += 10
        send(MotionEvent.ACTION_UP, endY)
    }

    private fun handleBack() {
        when {
            customView != null -> customViewCallback?.onCustomViewHidden()
            keyboardRequested || isKeyboardVisible() -> hideKeyboard()
            webView.canGoBack() -> webView.goBack()
            else -> finish()
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val visible = Rect()
        root.getWindowVisibleDisplayFrame(visible)
        return root.height > 0 && root.height - visible.height() > root.height * 0.18f
    }

    private fun hideKeyboard() {
        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethod.hideSoftInputFromWindow(webView.windowToken, 0)
        keyboardRequested = false
        runJs(JS_BLUR_EDITOR)
        webView.clearFocus()
        webView.requestFocus()
    }

    private fun moveCursor(dx: Float, dy: Float) {
        val margin = 8f
        cursorX = (cursorX + dx).coerceIn(margin, (root.width - margin).coerceAtLeast(margin))
        cursorY = (cursorY + dy).coerceIn(margin, (root.height - margin).coerceAtLeast(margin))
        cursor.setPos(cursorX, cursorY)
    }

    private fun clickAt(x: Float, y: Float) {
        if (root.width <= 0 || root.height <= 0) return
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 65, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        cursor.pulse()

        // Login/search text fields should still get the Fire TV keyboard if the
        // page makes one active. Comment posting is deliberately not promised.
        webView.postDelayed({
            webView.evaluateJavascript(JS_ACTIVE_EDITOR) { result ->
                if (result == "true") showKeyboard()
            }
        }, 180L)
    }

    private fun showKeyboard() {
        keyboardRequested = true
        webView.requestFocus()
        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        webView.post {
            inputMethod.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            webView.postDelayed({
                inputMethod.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }, 180L)
        }
    }

    private fun runJs(code: String) {
        if (::webView.isInitialized) webView.evaluateJavascript(code, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) outState.putString(STATE_URL, webView.url ?: lastGoodUrl)
        outState.putFloat(STATE_CURSOR_X, cursorX)
        outState.putFloat(STATE_CURSOR_Y, cursorY)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            runJs(JS_PAUSE_ALL_VIDEOS)
            webView.onPause()
        }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::webView.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            webView.clearCache(false)
        }
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        if (::webView.isInitialized) {
            runJs(JS_PAUSE_ALL_VIDEOS)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val STATE_URL = "browser_url"
        private const val STATE_CURSOR_X = "cursor_x"
        private const val STATE_CURSOR_Y = "cursor_y"

        private val HANDLED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS
        )

        private const val FEED_FINGERPRINT = """
            (function(){
              var videos = document.querySelectorAll('video');
              if (!videos.length) return 'scroll|' + Math.round(window.scrollY || 0);
              var middle = window.innerHeight / 2, best = videos[0], distance = 1e9;
              for (var i = 0; i < videos.length; i++) {
                var rect = videos[i].getBoundingClientRect();
                var current = Math.abs((rect.top + rect.bottom) / 2 - middle);
                if (current < distance) { distance = current; best = videos[i]; }
              }
              var bestRect = best.getBoundingClientRect();
              return (best.currentSrc || best.src || 'video') + '|' + Math.round(bestRect.top);
            })();
        """

        private const val JS_FEED_NEXT = """
            (function(){
              var height = window.innerHeight;
              var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
              var middle = height / 2, active = null, activeIndex = -1, distance = 1e9;
              for (var i = 0; i < videos.length; i++) {
                var rect = videos[i].getBoundingClientRect();
                var current = Math.abs((rect.top + rect.bottom) / 2 - middle);
                if (current < distance) { distance = current; active = videos[i]; activeIndex = i; }
              }
              if (active && videos[activeIndex + 1]) {
                videos[activeIndex + 1].scrollIntoView({behavior:'smooth', block:'center'});
                return true;
              }
              var node = active ? active.parentElement : null;
              for (var depth = 0; node && depth < 8; depth++, node = node.parentElement) {
                var style = getComputedStyle(node);
                if (/auto|scroll/.test(style.overflowY) && node.scrollHeight > node.clientHeight + 40) {
                  node.scrollBy({top:node.clientHeight, behavior:'smooth'});
                  return true;
                }
              }
              window.scrollBy({top:height * 0.92, behavior:'smooth'});
              return true;
            })();
        """

        private const val JS_FEED_PREVIOUS = """
            (function(){
              var height = window.innerHeight;
              var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
              var middle = height / 2, active = null, activeIndex = -1, distance = 1e9;
              for (var i = 0; i < videos.length; i++) {
                var rect = videos[i].getBoundingClientRect();
                var current = Math.abs((rect.top + rect.bottom) / 2 - middle);
                if (current < distance) { distance = current; active = videos[i]; activeIndex = i; }
              }
              if (active && activeIndex > 0) {
                videos[activeIndex - 1].scrollIntoView({behavior:'smooth', block:'center'});
                return true;
              }
              var node = active ? active.parentElement : null;
              for (var depth = 0; node && depth < 8; depth++, node = node.parentElement) {
                var style = getComputedStyle(node);
                if (/auto|scroll/.test(style.overflowY) && node.scrollHeight > node.clientHeight + 40) {
                  node.scrollBy({top:-node.clientHeight, behavior:'smooth'});
                  return true;
                }
              }
              window.scrollBy({top:-height * 0.92, behavior:'smooth'});
              return true;
            })();
        """

        private const val JS_TOGGLE_VIDEO = """
            (function(){
              var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
              if (!videos.length) return false;
              var middle = window.innerHeight / 2, best = videos[0], distance = 1e9;
              videos.forEach(function(video){
                var rect = video.getBoundingClientRect();
                var current = Math.abs((rect.top + rect.bottom) / 2 - middle);
                if (current < distance) { distance = current; best = video; }
              });
              if (best.paused) best.play(); else best.pause();
              return true;
            })();
        """

        private const val JS_ACTIVE_EDITOR = """
            (function(){
              var element = document.activeElement;
              if (!element) return false;
              var tag = (element.tagName || '').toLowerCase();
              return tag === 'textarea' ||
                (tag === 'input' && !/button|submit|checkbox|radio|file/.test(element.type || '')) ||
                element.isContentEditable || element.getAttribute('role') === 'textbox';
            })();
        """

        private const val JS_PAUSE_ALL_VIDEOS = """
            (function(){
              document.querySelectorAll('video,audio').forEach(function(media){
                try { media.pause(); } catch (e) {}
              });
            })();
        """

        private const val JS_BLUR_EDITOR = """
            (function(){
              var active = document.activeElement;
              if (active && active.blur) active.blur();
            })();
        """
    }

    /** High-contrast pointer drawn above the website. */
    private class CursorView(context: Context) : View(context) {
        private var x = 0f
        private var y = 0f
        private var radius = 14f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 0, 190, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        fun setPos(newX: Float, newY: Float) {
            x = newX
            y = newY
            invalidate()
        }

        fun pulse() {
            radius = 22f
            invalidate()
            postDelayed({
                radius = 14f
                invalidate()
            }, 130L)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, ring)
        }
    }
}
