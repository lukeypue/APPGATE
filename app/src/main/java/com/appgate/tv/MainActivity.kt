package com.appgate.tv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * AppGate identity screen. The product opens directly into the supported
 * TikTok web experience instead of presenting a generic browser/launcher.
 */
class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openBrowser = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, BrowserActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(14, 17, 22))
        }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        stack.addView(label("AppGate", 34f, Color.WHITE))
        stack.addView(label("TikTok web experience", 19f, Color.rgb(205, 214, 224)).apply {
            setPadding(0, 16, 0, 0)
        })
        stack.addView(label("More web services coming later", 15f, Color.rgb(145, 157, 171)).apply {
            setPadding(0, 12, 0, 0)
        })

        root.addView(
            stack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        handler.postDelayed(openBrowser, 850L)
    }

    private fun label(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    override fun onDestroy() {
        handler.removeCallbacks(openBrowser)
        super.onDestroy()
    }
}
