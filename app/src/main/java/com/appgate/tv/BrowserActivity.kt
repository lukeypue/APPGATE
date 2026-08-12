package com.appgate.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts one site with TV-native controls.
 *
 * Modes:
 *  - NAV mode (default): D-pad Up/Down = previous/next post (feed sites) or page scroll.
 *    Center = play/pause the visible video. Menu = switch to cursor mode.
 *  - CURSOR mode: D-pad moves a pointer, Center clicks (soft keyboard pops on inputs).
 *
 * Hardware media keys always control the visible HTML5 video.
 * Back priority: exit fullscreen video -> web history back -> return to launcher.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private lateinit var site: Site

    private var cx = 0f
    private var cy = 0f
    private var speedMult = 1f

    // Fullscreen video handoff
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; SM-S911U) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra("site_id") ?: "tiktok"
        site = Prefs.siteById(this, id) ?: SiteCatalog.preloaded[0]
        speedMult = Prefs.cursorSpeed(this)

        root = FrameLayout(this)
        cursor = CursorView(this)
        setContentView(root)
        buildWebView(restoreUrl = site.url)
        root.addView(cursor, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        cursor.visibility = View.VISIBLE   // cursor visible the moment you enter

        root.post { cx = root.width / 2f; cy = root.height / 2f; cursor.setPos(cx, cy) }

        // Brief on-screen hint so the controls are never a mystery
        root.postDelayed({
            Toast.makeText(this,
                "Arrows = move pointer  •  OK = click  •  ◀◀ ▶▶ = prev/next video  •  Back = home",
                Toast.LENGTH_LONG).show()
        }, 700)
    }

    /** Build (or rebuild after a renderer crash) the WebView. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(restoreUrl: String) {
        webView = WebView(this)
        root.addView(webView, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            setSupportZoom(false)
            userAgentString = if (site.mobileUa) MOBILE_UA else DESKTOP_UA
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                injectCleanup()
            }

            // 1GB sticks: Chromium renderer can get killed under memory pressure.
            // Rebuild instead of crashing the whole app.
            override fun onRenderProcessGone(
                view: WebView, detail: RenderProcessGoneDetail
            ): Boolean {
                val lastUrl = view.url ?: site.url
                root.removeView(view)
                view.destroy()
                buildWebView(restoreUrl = lastUrl)
                Toast.makeText(this@BrowserActivity,
                    "Reloading (low memory)", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }
        }

        webView.loadUrl(restoreUrl)
    }

    private fun injectCleanup() {
        // Force the page (and its full-height containers) to match the TV screen,
        // so nothing spills off the bottom edge.
        val heightLock = """
            html, body { height: 100vh !important; max-height: 100vh !important;
                         overflow: hidden !important; }
        """
        val css = (SiteCatalog.COMMON_CSS + "\n" + heightLock + "\n" + site.cleanupCss)
            .replace("\n", " ").replace("\"", "\\\"")
        val js = """
            (function(){
              var s = document.getElementById('appgate-css');
              if (!s) { s = document.createElement('style'); s.id='appgate-css';
                        document.head.appendChild(s); }
              s.textContent = "$css";
              // Ensure a proper mobile viewport meta so layout matches screen width
              var m = document.querySelector('meta[name=viewport]');
              if (!m) { m = document.createElement('meta'); m.name='viewport';
                        document.head.appendChild(m); }
              m.content = 'width=device-width, initial-scale=1, maximum-scale=1';
            })();
        """
        webView.evaluateJavascript(js, null)
    }

    // ---------------- Key handling ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While the soft keyboard is open, let it handle everything except Back
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isAcceptingText && event.keyCode != KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (handledKeys.contains(event.keyCode)) true
                   else super.dispatchKeyEvent(event)
        }

        val base = if (event.repeatCount > 3) 42f else 18f
        val move = base * speedMult

        when (event.keyCode) {
            // Arrows always drive the pointer — consistent in every app
            KeyEvent.KEYCODE_DPAD_LEFT  -> { moveCursor(-move, 0f); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(move, 0f);  return true }
            KeyEvent.KEYCODE_DPAD_UP    -> { moveCursor(0f, -move); return true }
            KeyEvent.KEYCODE_DPAD_DOWN  -> { moveCursor(0f, move);  return true }

            // OK clicks whatever the pointer is on
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> { clickAt(cx, cy); return true }

            // Dedicated video controls, never conflict with the pointer
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { feedNext(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND       -> { feedPrev(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { js(JS_TOGGLE_VIDEO); return true }

            // Menu = jump to next video too (many remotes lack the ◀◀ ▶▶ keys)
            KeyEvent.KEYCODE_MENU -> { feedNext(); return true }

            KeyEvent.KEYCODE_BACK -> { handleBack(); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun feedNext() {
        if (site.feedMode) js(JS_FEED_NEXT) else webView.scrollBy(0, 500)
    }
    private fun feedPrev() {
        if (site.feedMode) js(JS_FEED_PREV) else webView.scrollBy(0, -500)
    }

    private val handledKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    )

    private fun handleBack() {
        when {
            customView != null -> customViewCallback?.onCustomViewHidden()
            webView.canGoBack() -> webView.goBack()
            else -> finish()   // back to AppGate launcher
        }
    }

    private fun moveCursor(dx: Float, dy: Float) {
        cx = (cx + dx).coerceIn(0f, root.width.toFloat())
        cy += dy
        if (cy < 0f) { webView.scrollBy(0, -220); cy = 0f }
        if (cy > root.height) { webView.scrollBy(0, 220); cy = root.height.toFloat() }
        cursor.setPos(cx, cy)
    }

    private fun clickAt(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t, t + 70, MotionEvent.ACTION_UP, x, y, 0)
        webView.dispatchTouchEvent(down); webView.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
        cursor.pulse()
    }

    private fun js(code: String) = webView.evaluateJavascript(code, null)

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()   // keep logins across launches
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // ---------------- Injected JS ----------------

    companion object {
        /** Move to the next/previous item. Strategy, in order:
         *  1) Find the scrolling feed container (the element that actually scrolls,
         *     which on mobile TikTok is usually NOT window) and scroll it by one
         *     screen height. This is what makes the feed advance.
         *  2) Fall back to scrolling the nearest video into view.
         *  3) Fall back to window scroll + arrow key for other sites. */
        private const val JS_FEED_NEXT = """
            (function(){
              var vh = window.innerHeight;
              function scroller(){
                var els = document.querySelectorAll('*'); 
                for (var i=0;i<els.length;i++){
                  var e = els[i], s = getComputedStyle(e);
                  if ((s.overflowY==='scroll'||s.overflowY==='auto') &&
                      e.scrollHeight > e.clientHeight + 50 &&
                      e.clientHeight > vh*0.5) return e;
                }
                return null;
              }
              var c = scroller();
              if (c) { c.scrollBy({top: c.clientHeight, behavior:'smooth'}); return; }
              var vids = document.querySelectorAll('video');
              if (vids.length){
                var mid = vh/2, idx = 0, bd = 1e9;
                for (var j=0;j<vids.length;j++){
                  var r = vids[j].getBoundingClientRect();
                  var d = Math.abs((r.top+r.bottom)/2 - mid);
                  if (d<bd){bd=d; idx=j;}
                }
                if (vids[idx+1]) { vids[idx+1].scrollIntoView({behavior:'smooth'}); return; }
              }
              try { document.dispatchEvent(new KeyboardEvent('keydown',
                {key:'ArrowDown',keyCode:40,which:40,bubbles:true})); } catch(e){}
              window.scrollBy({top: vh, behavior:'smooth'});
            })();
        """
        private const val JS_FEED_PREV = """
            (function(){
              var vh = window.innerHeight;
              function scroller(){
                var els = document.querySelectorAll('*');
                for (var i=0;i<els.length;i++){
                  var e = els[i], s = getComputedStyle(e);
                  if ((s.overflowY==='scroll'||s.overflowY==='auto') &&
                      e.scrollHeight > e.clientHeight + 50 &&
                      e.clientHeight > vh*0.5) return e;
                }
                return null;
              }
              var c = scroller();
              if (c) { c.scrollBy({top: -c.clientHeight, behavior:'smooth'}); return; }
              try { document.dispatchEvent(new KeyboardEvent('keydown',
                {key:'ArrowUp',keyCode:38,which:38,bubbles:true})); } catch(e){}
              window.scrollBy({top: -vh, behavior:'smooth'});
            })();
        """

        /** Play/pause whichever video is most on-screen. */
        private const val JS_TOGGLE_VIDEO = """
            (function(){
              var vids = Array.from(document.querySelectorAll('video'));
              if (!vids.length) return;
              var mid = window.innerHeight/2, best = vids[0], bd = 1e9;
              vids.forEach(function(v){
                var r = v.getBoundingClientRect();
                var d = Math.abs((r.top+r.bottom)/2 - mid);
                if (d < bd) { bd = d; best = v; }
              });
              if (best.paused) best.play(); else best.pause();
            })();
        """
    }

    /** Pointer drawn above the WebView in cursor mode. */
    private class CursorView(context: Context) : View(context) {
        private var x = 0f; private var y = 0f; private var r = 14f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255); style = Paint.Style.FILL }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 0, 200, 255); style = Paint.Style.STROKE
            strokeWidth = 5f }

        fun setPos(nx: Float, ny: Float) { x = nx; y = ny; invalidate() }
        fun pulse() { r = 22f; invalidate(); postDelayed({ r = 14f; invalidate() }, 120) }
        override fun onDraw(c: Canvas) {
            c.drawCircle(x, y, r, fill); c.drawCircle(x, y, r, ring)
        }
    }
}
