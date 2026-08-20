package com.appgate.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Startup app: jump straight into a site if configured
        Prefs.startupSiteId(this)?.let { id ->
            Prefs.siteById(this, id)?.let { openSite(it); }
        }

        setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherScreen() } }
    }

    private fun openSite(site: Site) {
        Prefs.touchRecent(this, site.id)
        startActivity(Intent(this, BrowserActivity::class.java).putExtra("site_id", site.id))
    }

    @Composable
    fun LauncherScreen() {
        var refresh by remember { mutableStateOf(0) }
        val sites = remember(refresh) { Prefs.allSites(this) }
        val recents = remember(refresh) {
            Prefs.recents(this).mapNotNull { id -> sites.find { it.id == id } }
        }
        var showAdd by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var longPressSite by remember { mutableStateOf<Site?>(null) }

        Column(
            Modifier.fillMaxSize().background(Color(0xFF0E1116))
                .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 40.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AppGate", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
                Clock()
            }
            Spacer(Modifier.height(18.dp))

            if (recents.isNotEmpty()) {
                Text("Recent", color = Color(0xFF9AA4B2), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recents.size) { i ->
                        Tile(recents[i], small = true,
                            onOpen = { openSite(recents[i]) },
                            onLong = { longPressSite = recents[i] })
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Text("Apps", color = Color(0xFF9AA4B2), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sites, key = { it.id }) { site ->
                    Tile(site, onOpen = { openSite(site) }, onLong = { longPressSite = site })
                }
                item { ActionTile("+ Add Site") { showAdd = true } }
                item { ActionTile("Settings") { showSettings = true } }
            }
        }

        if (showAdd) AddSiteDialog(
            onDone = { name, url ->
                if (name.isNotBlank() && url.isNotBlank()) Prefs.addCustomSite(this, name, url)
                showAdd = false; refresh++
            })
        if (showSettings) SettingsDialog(onClose = { showSettings = false; refresh++ })
        longPressSite?.let { site ->
            TileOptionsDialog(site,
                onClose = { longPressSite = null; refresh++ })
        }
    }

    @Composable
    fun Clock() {
        var now by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                delay(10_000)
            }
        }
        Text(now, color = Color.White, fontSize = 22.sp)
    }

    @Composable
    fun Tile(site: Site, small: Boolean = false, onOpen: () -> Unit, onLong: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        var downTime by remember { mutableStateOf(0L) }
        val h = if (small) 70.dp else 96.dp

        Box(
            Modifier
                .height(h)
                .then(if (small) Modifier.width(130.dp) else Modifier.fillMaxWidth())
                .scale(if (focused) 1.06f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(site.color))
                .border(
                    if (focused) 3.dp else 0.dp,
                    if (focused) Color.White else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.DirectionCenter) -> {
                            if (downTime == 0L) downTime = System.currentTimeMillis(); true
                        }
                        e.type == KeyEventType.KeyUp &&
                            (e.key == Key.Enter || e.key == Key.DirectionCenter) -> {
                            val held = System.currentTimeMillis() - downTime
                            downTime = 0L
                            if (held > 600) onLong() else onOpen()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val dark = listOf("snapchat", "lemon8").contains(site.id)
            Text(
                site.name,
                color = if (dark) Color.Black else Color.White,
                fontSize = if (small) 15.sp else 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    @Composable
    fun ActionTile(label: String, onOpen: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .height(96.dp).fillMaxWidth()
                .scale(if (focused) 1.06f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1C2430))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color.White else Color(0xFF33404F),
                    RoundedCornerShape(14.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp &&
                        (e.key == Key.Enter || e.key == Key.DirectionCenter)
                    ) { onOpen(); true } else false
                },
            contentAlignment = Alignment.Center
        ) { Text(label, color = Color(0xFFB9C4D0), fontSize = 17.sp) }
    }

    @Composable
    fun AddSiteDialog(onDone: (String, String) -> Unit) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onDone("", "") },
            title = { Text("Add a site") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = url, onValueChange = { url = it },
                        label = { Text("Web address (e.g. example.com)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onDone(name, url) }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { onDone("", "") }) { Text("Cancel") } }
        )
    }

    @Composable
    fun TileOptionsDialog(site: Site, onClose: () -> Unit) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(site.name) },
            text = { Text("Tile options") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        Prefs.setStartupSiteId(this@MainActivity, site.id); onClose()
                    }) { Text("Open this app at startup") }
                    if (site.id.startsWith("custom_")) TextButton(onClick = {
                        Prefs.removeCustomSite(this@MainActivity, site.id); onClose()
                    }) { Text("Remove tile") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        )
    }

    @Composable
    fun SettingsDialog(onClose: () -> Unit) {
        var speed by remember { mutableStateOf(Prefs.cursorSpeed(this)) }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Cursor speed: ${"%.1f".format(speed)}x")
                    Slider(value = speed, onValueChange = {
                        speed = it; Prefs.setCursorSpeed(this@MainActivity, it)
                    }, valueRange = 0.5f..2.5f)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        Prefs.setStartupSiteId(this@MainActivity, null)
                    }) { Text("Clear startup app (open to home screen)") }
                    Text(
                        "Inside a site: Arrows move the pointer, OK clicks, " +
                        "Channel Down/Up changes videos, and Back returns here.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
        )
    }
}
