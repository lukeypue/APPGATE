package com.appgate.tv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UpdateActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button
    private var downloadId: Long = -1L
    private var downloadedUri: Uri? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadedUri = dm.getUriForDownloadedFile(downloadId)
            progress.visibility = View.GONE
            if (downloadedUri == null) {
                status.text = "Update download failed. Your learned Site Brain data was not changed."
                downloadButton.isEnabled = true
                return
            }
            status.text = "Update downloaded. Opening Android's installer…\nYour Site Brain knowledge and checkpoints stay in the app's data area."
            installDownloadedUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Update AI Browser"
        val currentVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }.getOrDefault("unknown")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(12, 18, 28))
        }
        root.addView(TextView(this).apply {
            text = "AI Browser Updater"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            text = "Current version: $currentVersion\n\nTap Download Latest Update. The update installs over this app so the Site Brain database, checkpoints, cookies and logs are intended to remain in place. Android may ask once for permission to install updates from AI Browser."
            textSize = 15f
            setTextColor(Color.rgb(185, 205, 225))
            setPadding(0, 12, 0, 18)
        }
        root.addView(status)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        downloadButton = Button(this).apply {
            text = "DOWNLOAD LATEST UPDATE"
            textSize = 17f
            setOnClickListener { downloadLatest() }
        }
        root.addView(downloadButton)
        root.addView(Button(this).apply {
            text = "BACK WITHOUT UPDATING"
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "Updates come from the project's fixed GitHub 'latest' release. A failed update does not delete the learned brain. Do not uninstall the app unless we explicitly decide to reset it."
            textSize = 12f
            setTextColor(Color.rgb(150, 175, 200))
            setPadding(0, 18, 0, 0)
        })
        setContentView(root)

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
    }

    private fun downloadLatest() {
        val request = DownloadManager.Request(Uri.parse(LATEST_APK_URL))
            .setTitle("AI Browser update")
            .setDescription("Downloading latest Site Brain engine")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "AI-Browser-latest.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        downloadButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Downloading the latest approved AI Browser build…\nSite Brain knowledge remains untouched while the engine downloads."
    }

    private fun installDownloadedUpdate() {
        val uri = downloadedUri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            status.text = "Android needs one-time permission for AI Browser to install its own updates. Enable 'Allow from this source', then return here."
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onResume() {
        super.onResume()
        if (downloadedUri != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())) {
            installDownloadedUpdate()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        const val LATEST_APK_URL = "https://github.com/lukeypue/APPGATE/releases/download/ai-browser-latest/AI-Browser-latest.apk"
    }
}
