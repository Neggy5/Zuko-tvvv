package com.zuko.tv.ui

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zuko.tv.BuildConfig
import com.zuko.tv.data.WatchHistory
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(onBack: () -> Unit, watchHistory: WatchHistory, onTheme: (String) -> Unit, currentTheme: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zuko_settings", Context.MODE_PRIVATE) }
    var quality by remember { mutableStateOf(prefs.getString("quality", "Auto") ?: "Auto") }
    var addon by remember { mutableStateOf("Checking…") }
    var update by remember { mutableStateOf("Not checked") }
    var downloads by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        addon = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(BuildConfig.RUNFLIX_ADDON_BASE.trimEnd('/') + "/healthz").build()
                OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build().newCall(request).execute().use { if (it.isSuccessful) "Online" else "Responded: ${it.code}" }
            }.getOrElse { "Unavailable (${it.message ?: "network error"})" }
        }
        downloads = readDownloads(context)
    }
    LazyColumn(Modifier.fillMaxSize().background(Ink).padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Back") }; Text("Settings", style = ZukoType.screenTitle, color = TextHi) }
        item { SettingCard("Appearance", "Choose the app colour mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Dark", "Light").forEach { mode -> FilterChip(selected = currentTheme == mode, onClick = { onTheme(mode) }, label = { Text(mode) }) } }
        } }
        item { SettingCard("Preferred quality", "Used as your playback preference") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Auto", "1080p", "720p", "480p").forEach { q -> FilterChip(selected = quality == q, onClick = { quality = q; prefs.edit().putString("quality", q).apply() }, label = { Text(q) }) } }
        } }
        item { SettingCard("Runflix / Railway addon", "Status: $addon") { Text("Configured endpoint: ${BuildConfig.RUNFLIX_ADDON_BASE}", style = ZukoType.small, color = TextLo) } }
        item { SettingCard("Watch history", "${watchHistory.items.value.size} saved items") { Button(onClick = { watchHistory.clear(); message = "Watch history cleared" }) { Text("Clear watch history") } } }
        item { SettingCard("Downloads", "Android Download Manager items") {
            if (downloads.isEmpty()) Text("No downloads found", color = TextLo)
            downloads.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(row.title, color = TextHi); Text(row.status, color = TextLo, style = ZukoType.small) }; TextButton(onClick = { cancelDownload(context, row.id); downloads = readDownloads(context) }) { Text("Cancel") } } }
            Button(onClick = { downloads = readDownloads(context) }) { Text("Refresh downloads") }
        } }
        item { SettingCard("App updates", "Current version ${BuildConfig.VERSION_NAME}") {
            Button(onClick = { update = "Checking…" }) { Text("Check for update") }
            LaunchedEffect(update) { if (update == "Checking…") update = checkUpdate() }
            Text(update, color = TextLo, style = ZukoType.small)
        } }
        if (message.isNotBlank()) item { Text(message, color = TextHi) }
        item { Text("Release signing is configured through private Gradle/GitHub secrets. Never share your keystore or passwords.", color = TextLo, style = ZukoType.small) }
    }
}

private data class DownloadRow(val id: Long, val title: String, val status: String)
private fun readDownloads(context: Context): List<DownloadRow> {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return emptyList()
    val cursor = dm.query(DownloadManager.Query()) ?: return emptyList()
    return cursor.use { c -> buildList { while (c.moveToNext()) {
        val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Download"
        val statusCode = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val status = when (statusCode) { DownloadManager.STATUS_SUCCESSFUL -> "Complete"; DownloadManager.STATUS_FAILED -> "Failed"; DownloadManager.STATUS_PAUSED -> "Paused"; DownloadManager.STATUS_RUNNING -> "Downloading"; else -> "Queued" }
        add(DownloadRow(id, title, status))
    } } }
}
private fun cancelDownload(context: Context, id: Long) { (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)?.remove(id) }
private suspend fun checkUpdate(): String = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url("https://api.github.com/repos/Neggy5/Zuko-tvvv/releases/latest").header("Accept", "application/vnd.github+json").build()
        OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use "No published release found yet (HTTP ${response.code})."
            val release = JSONObject(response.body?.string().orEmpty())
            val tag = release.optString("tag_name", "").removePrefix("v")
            if (tag.isBlank()) "No release version found." else if (tag != BuildConfig.VERSION_NAME) "Latest release: $tag — ${release.optString("html_url")}" else "You're using the latest published release."
        }
    }.getOrElse { "Update check failed: ${it.message ?: "network error"}" }
}

@Composable private fun SettingCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = ZukoType.rowTitle, color = TextHi)
            Text(subtitle, style = ZukoType.small, color = TextLo)
            content()
        }
    }
}
