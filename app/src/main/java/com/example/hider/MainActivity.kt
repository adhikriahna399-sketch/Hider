package com.example.hider

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Launcher() } } }
    }
}

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Launcher() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("hider", Context.MODE_PRIVATE) }
    val apps = remember { loadApps(ctx) }
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden", emptySet())!!.toSet()) }
    var manage by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    val names = remember { mutableStateMapOf<String, String>().apply { apps.forEach { a -> prefs.getString("n_${a.pkg}", null)?.let { put(a.pkg, it) } } } }
    val symbols = remember { mutableStateMapOf<String, String>().apply { apps.forEach { a -> prefs.getString("s_${a.pkg}", null)?.let { put(a.pkg, it) } } } }
    var editing by remember { mutableStateOf<AppItem?>(null) }

    BackHandler(manage) { manage = false }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
        // Secret trigger: tap this title 5 times
        Text(
            if (manage) "Manage hidden apps (tap to hide/unhide)" else "Apps",
            fontSize = 22.sp,
            modifier = Modifier.padding(8.dp).clickable(enabled = !manage) {
                taps++
                if (taps >= 5) { taps = 0; askPin = true }
            }
        )
        val shown = if (manage) apps e
