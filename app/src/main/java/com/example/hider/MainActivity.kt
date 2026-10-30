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
        val shown = if (manage) apps else apps.filter { it.pkg !in hidden }
        LazyVerticalGrid(GridCells.Adaptive(80.dp), Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                val isHidden = app.pkg in hidden
                Column(
                    Modifier.padding(6.dp).alpha(if (manage && isHidden) 0.35f else 1f).combinedClickable(
                        onLongClick = { editing = app },
                        onClick = {
                            if (manage) {
                                hidden = if (isHidden) hidden - app.pkg else hidden + app.pkg
                                prefs.edit().putStringSet("hidden", hidden).apply()
                            } else {
                                ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let { ctx.startActivity(it) }
                            }
                        }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sym = symbols[app.pkg]
                    if (sym != null) {
                        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), Alignment.Center) {
                            Text(sym, fontSize = 28.sp)
                        }
                    } else Image(app.icon, app.label, Modifier.size(52.dp))
                    Text(names[app.pkg] ?: app.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    editing?.let { app ->
        var name by remember(app) { mutableStateOf(names[app.pkg] ?: app.label) }
        var sym by remember(app) { mutableStateOf(symbols[app.pkg]) }
        val choices = listOf("🧮", "📝", "📅", "⏰", "🌤️", "🎵", "📷", "🔧", "📚", "💬", "🛒", "📞", "🗺️", "💡")
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit app") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                    Text("Symbol", Modifier.padding(top = 12.dp, bottom = 4.dp), fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { sym = null }) { Text(if (sym == null) "[Original]" else "Original") }
                        choices.forEach { e ->
                            TextButton(onClick = { sym = e }) { Text(if (sym == e) "[$e]" else e, fontSize = 20.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val e = prefs.edit()
                    if (name.isBlank() || name == app.label) { names.remove(app.pkg); e.remove("n_${app.pkg}") }
                    else { names[app.pkg] = name.trim(); e.putString("n_${app.pkg}", name.trim()) }
                    val chosen = sym
                    if (chosen == null) { symbols.remove(app.pkg); e.remove("s_${app.pkg}") }
                    else { symbols[app.pkg] = chosen; e.putString("s_${app.pkg}", chosen) }
                    e.apply(); editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (askPin) {
        val saved = prefs.getString("pin", null)
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { askPin = false },
            title = { Text(if (saved == null) "Set a PIN" else "Enter PIN") },
            text = {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) },
                    singleLine = true, isError = error, label = { Text("PIN") })
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        saved == null && pin.length >= 4 -> { prefs.edit().putString("pin", sha(pin)).apply(); askPin = false; manage = true }
                        saved != null && sha(pin) == saved -> { askPin = false; manage = true }
                        else -> error = true
                    }
                }) { Text("OK") }
            }
        )
    }
}
