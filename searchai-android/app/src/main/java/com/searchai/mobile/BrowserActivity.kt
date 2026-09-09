package com.searchai.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class BrowserActivity:ComponentActivity(){
 private lateinit var web:WebView
 @SuppressLint("SetJavaScriptEnabled") override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val url=intent.getStringExtra("url")?:run{finish();return}; if(!url.startsWith("https://")){finish();return}
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val help=TextView(this).apply{text="If this site asks for human verification, complete it here. SearchAI will never bypass it.";setPadding(20,16,20,16)}
  web=WebView(this); web.settings.javaScriptEnabled=true; web.settings.domStorageEnabled=true; web.webViewClient=WebViewClient(); web.webChromeClient=WebChromeClient()
  root.addView(help);root.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);web.loadUrl(url)
 }
 @Deprecated("Deprecated in Java") override fun onBackPressed(){if(::web.isInitialized&&web.canGoBack())web.goBack() else super.onBackPressed()}
 override fun onDestroy(){if(::web.isInitialized){web.stopLoading();web.destroy()};super.onDestroy()}
}
