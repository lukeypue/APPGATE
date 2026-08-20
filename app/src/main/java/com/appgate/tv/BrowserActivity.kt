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
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts a single website with Fire TV remote controls.
 *
 * D-pad always moves the on-screen pointer and Select clicks it. Channel Down
 * advances a feed and Channel Up returns to the previous item. Media buttons
 * retain normal media meaning. Back dismisses the keyboard/fullscreen first,
 * then immediately returns to the AppGate launcher.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private lateinit var site: Site

    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorSpeed = 1f
    private var keyboardRequested = false
    private var feedMoveInProgress = false
    private var pageLooksBlank = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // TikTok now has an official desktop web experience. A desktop identity is
    // more stable on a landscape TV than pretending the Fire TV is a phone.
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra("site_id") ?: "tiktok"
        site = Prefs.siteById(this, id) ?: SiteCatalog.preloaded.first()
        cursorSpeed = Prefs.cursorSpeed(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        cursor = CursorView(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        setContentView(root)

        val restoreUrl = savedInstanceState?.getString(STATE_URL) ?: site.url
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

        root.postDelayed({ showControlHint() }, 650)
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
            userAgentString = if (site.mobileUa) mobileUserAgent else desktopUserAgent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = false
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                pageLooksBlank = false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url?.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            @Deprecated("Required for WebView navigation on API 22")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val scheme = android.net.Uri.parse(url).scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectTvHelpers()
                view.postDelayed({ injectTvHelpers() }, 900)
                view.postDelayed({ checkPageHealth() }, 2_500)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@BrowserActivity,
                        "The website did not load. Check the connection and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                val lastUrl = view.url ?: site.url
                root.removeView(view)
                view.destroy()
                buildWebView(lastUrl)
                Toast.makeText(
                    this@BrowserActivity,
                    "Reloading after low memory",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                android.util.Log.d(
                    "AppGateWeb",
                    "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}"
                )
                return true
            }

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
                // AppGate does not request camera or microphone permissions.
                // Browsing and commenting remain available without them.
                request.deny()
            }
        }

        webView.loadUrl(restoreUrl)
    }

    private fun injectTvHelpers() {
        val css = (SiteCatalog.COMMON_CSS + "\n" + site.cleanupCss)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
        val script = """
            (function(){
              var style = document.getElementById('appgate-tv-css');
              if (!style) {
                style = document.createElement('style');
                style.id = 'appgate-tv-css';
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = "$css";
              var viewport = document.querySelector('meta[name=viewport]');
              if (!viewport) {
                viewport = document.createElement('meta');
                viewport.name = 'viewport';
                (document.head || document.documentElement).appendChild(viewport);
              }
              viewport.content = 'width=device-width,initial-scale=1';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    // ---------------- Remote handling ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // Once an editor owns input, the Fire TV keyboard must receive normal
        // D-pad and Select events. Back is kept so we can dismiss it cleanly.
        if ((keyboardRequested || inputMethod.isAcceptingText) &&
            event.keyCode != KeyEvent.KEYCODE_BACK
        ) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (handledKeys.contains(event.keyCode)) true
            else super.dispatchKeyEvent(event)
        }

        val baseMovement = if (event.repeatCount > 3) 44f else 19f
        val movement = baseMovement * cursorSpeed

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursor(-movement, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursor(movement, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursor(0f, -movement)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(0f, movement)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                clickAt(cursorX, cursorY)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                moveFeed(next = true)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                moveFeed(next = false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                runJs(JS_TOGGLE_VIDEO)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                runJs(jsSeek(-10))
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                runJs(jsSeek(10))
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (pageLooksBlank) {
                    pageLooksBlank = false
                    webView.reload()
                    Toast.makeText(this, "Reloading website", Toast.LENGTH_SHORT).show()
                } else {
                    showControlHint()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showControlHint() {
        Toast.makeText(
            this,
            "Arrows: pointer  •  OK: click  •  CH ↓/↑: next/previous  •  Back: AppGate",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun checkPageHealth() {
        val script = """
            (function(){
              if (!document.body) return false;
              var text = (document.body.innerText || '').trim();
              return text.length > 20 || !!document.querySelector('video,img,canvas,iframe');
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            pageLooksBlank = result == "false"
            if (pageLooksBlank) {
                Toast.makeText(
                    this,
                    "The website returned a blank page. Press Menu to reload or Back to exit.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun moveFeed(next: Boolean) {
        if (!site.feedMode) {
            webView.scrollBy(0, if (next) root.height * 3 / 4 else -root.height * 3 / 4)
            return
        }
        if (feedMoveInProgress) return
        feedMoveInProgress = true

        webView.evaluateJavascript(FEED_FINGERPRINT) { before ->
            if (site.id == "tiktok" && !site.mobileUa) {
                dispatchWebArrow(next)
            } else {
                runJs(if (next) JS_FEED_NEXT else JS_FEED_PREVIOUS)
            }

            webView.postDelayed({
                webView.evaluateJavascript(FEED_FINGERPRINT) { after ->
                    if (before == after) {
                        runJs(if (next) JS_FEED_NEXT else JS_FEED_PREVIOUS)
                        webView.postDelayed({
                            webView.evaluateJavascript(FEED_FINGERPRINT) { finalValue ->
                                if (before == finalValue && site.mobileUa) {
                                    swipeFeed(next)
                                }
                            }
                        }, 420)
                    }
                    webView.postDelayed({ feedMoveInProgress = false }, 450)
                }
            }, 480)
        }
    }

    /** Sends a real key event to the web renderer so TikTok's web shortcut sees it. */
    private fun dispatchWebArrow(next: Boolean) {
        val keyCode = if (next) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        val downTime = SystemClock.uptimeMillis()
        webView.dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        webView.dispatchKeyEvent(KeyEvent(downTime, downTime + 20, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** Last-resort touch flick for mobile layouts that ignore scroll and key events. */
    private fun swipeFeed(next: Boolean) {
        val width = root.width.toFloat()
        val height = root.height.toFloat()
        val x = width / 2f
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
        val normalizedX = (x / root.width).coerceIn(0f, 1f)
        val normalizedY = (y / root.height).coerceIn(0f, 1f)
        val targetScript = targetKindScript(normalizedX, normalizedY)

        webView.evaluateJavascript(targetScript) { result ->
            val targetKind = when {
                result?.contains("comment") == true -> TargetKind.COMMENT
                result?.contains("editor") == true -> TargetKind.EDITOR
                else -> TargetKind.OTHER
            }
            dispatchClickMotion(x, y)
            cursor.pulse()

            when (targetKind) {
                TargetKind.EDITOR -> focusEditor(preferComment = false, delayMs = 100)
                TargetKind.COMMENT -> {
                    focusEditor(preferComment = true, delayMs = 320)
                    focusEditor(preferComment = true, delayMs = 900)
                }
                TargetKind.OTHER -> {
                    // Some sites put a transparent wrapper above the actual input.
                    focusActiveEditor(delayMs = 140)
                }
            }
        }
    }

    private fun dispatchClickMotion(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 65, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun focusEditor(preferComment: Boolean, delayMs: Long) {
        webView.postDelayed({
            val script = if (preferComment) JS_FOCUS_COMMENT_EDITOR else JS_FOCUS_ACTIVE_EDITOR
            webView.evaluateJavascript(script) { result ->
                if (result == "true") showKeyboard()
            }
        }, delayMs)
    }

    private fun focusActiveEditor(delayMs: Long) {
        webView.postDelayed({
            webView.evaluateJavascript(JS_FOCUS_ACTIVE_EDITOR) { result ->
                if (result == "true") showKeyboard()
            }
        }, delayMs)
    }

    private fun showKeyboard() {
        keyboardRequested = true
        webView.requestFocus()
        val inputMethod = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        webView.post {
            inputMethod.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            webView.postDelayed({
                inputMethod.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }, 180)
        }
    }

    private fun targetKindScript(normalizedX: Float, normalizedY: Float): String = """
        (function(){
          var x = window.innerWidth * $normalizedX;
          var y = window.innerHeight * $normalizedY;
          var element = document.elementFromPoint(x, y);
          function editable(node){
            if (!node || node.nodeType !== 1) return false;
            var tag = (node.tagName || '').toLowerCase();
            return tag === 'textarea' ||
              (tag === 'input' && !/button|submit|checkbox|radio|file/.test(node.type || '')) ||
              node.isContentEditable || node.getAttribute('role') === 'textbox';
          }
          for (var i = 0; element && i < 8; i++, element = element.parentElement) {
            if (editable(element)) return 'editor';
            var signature = [element.getAttribute('aria-label'), element.getAttribute('title'),
              element.getAttribute('data-e2e'), element.id, element.className].join(' ').toLowerCase();
            if (signature.indexOf('comment') >= 0 || signature.indexOf('reply') >= 0) return 'comment';
          }
          return 'other';
        })();
    """.trimIndent()

    private fun runJs(code: String) {
        if (::webView.isInitialized) webView.evaluateJavascript(code, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_URL, webView.url ?: site.url)
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

    private enum class TargetKind { EDITOR, COMMENT, OTHER }

    companion object {
        private const val STATE_URL = "browser_url"
        private const val STATE_CURSOR_X = "cursor_x"
        private const val STATE_CURSOR_Y = "cursor_y"

        private val handledKeys = setOf(
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
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
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

        private fun jsSeek(seconds: Int) = """
            (function(){
              var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
              if (!videos.length) return false;
              var middle = window.innerHeight / 2, best = videos[0], distance = 1e9;
              videos.forEach(function(video){
                var rect = video.getBoundingClientRect();
                var current = Math.abs((rect.top + rect.bottom) / 2 - middle);
                if (current < distance) { distance = current; best = video; }
              });
              best.currentTime = Math.max(0, Math.min(best.duration || 1e9, best.currentTime + ($seconds)));
              return true;
            })();
        """

        private const val JS_FOCUS_ACTIVE_EDITOR = """
            (function(){
              var element = document.activeElement;
              if (!element) return false;
              var tag = (element.tagName || '').toLowerCase();
              var editable = tag === 'textarea' ||
                (tag === 'input' && !/button|submit|checkbox|radio|file/.test(element.type || '')) ||
                element.isContentEditable || element.getAttribute('role') === 'textbox';
              if (!editable) return false;
              element.focus();
              if (element.click) element.click();
              return true;
            })();
        """

        private const val JS_FOCUS_COMMENT_EDITOR = """
            (function(){
              function visible(element){
                if (!element) return false;
                var rect = element.getBoundingClientRect();
                var style = getComputedStyle(element);
                return rect.width > 2 && rect.height > 2 &&
                  style.display !== 'none' && style.visibility !== 'hidden';
              }
              var selectors = [
                '[data-e2e="comment-input"] [contenteditable="true"]',
                '[data-e2e="comment-input"] textarea',
                '[data-e2e*="comment"] [contenteditable="true"]',
                '[data-e2e*="comment"] textarea',
                '[aria-label*="comment" i][contenteditable="true"]',
                'textarea[placeholder*="comment" i]',
                'textarea[placeholder*="reply" i]',
                '[role="dialog"] textarea',
                '[role="dialog"] [contenteditable="true"][role="textbox"]'
              ];
              for (var i = 0; i < selectors.length; i++) {
                var candidates = document.querySelectorAll(selectors[i]);
                for (var j = 0; j < candidates.length; j++) {
                  if (visible(candidates[j])) {
                    candidates[j].focus();
                    if (candidates[j].click) candidates[j].click();
                    return true;
                  }
                }
              }
              return false;
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

    /** A high-contrast pointer drawn above the website. */
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
            }, 130)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, ring)
        }
    }
}
