package com.zuko.tv.ui

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zuko.tv.BuildConfig
import com.zuko.tv.data.WatchHistory
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType

private data class DownloadRow(val id: Long, val title: String, val status: String)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    watchHistory: WatchHistory,
    onTheme: (String) -> Unit,
    currentTheme: String,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zuko_settings", Context.MODE_PRIVATE) }
    var quality by remember { mutableStateOf(prefs.getString("quality", "Auto") ?: "Auto") }
    var update by remember { mutableStateOf("You're using ZUKO TV ${BuildConfig.VERSION_NAME}.") }
    var downloads by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { downloads = readDownloads(context) }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹  Back") }
            Text("Settings", style = ZukoType.screenTitle, color = TextHi)
        }
        item {
            SettingCard("Appearance", "Choose the app colour mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Dark", "Light").forEach { mode ->
                        FilterChip(selected = currentTheme == mode, onClick = { onTheme(mode) }, label = { Text(mode) })
                    }
                }
            }
        }
        item {
            SettingCard("Preferred quality", "Used as your playback preference") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "1080p", "720p", "480p").forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q; prefs.edit().putString("quality", q).apply() },
                            label = { Text(q) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Watch history", "${watchHistory.items.value.size} saved items") {
                Button(onClick = { watchHistory.clear(); message = "Watch history cleared" }) { Text("Clear watch history") }
            }
        }
        item {
            SettingCard("Downloads", "Android Download Manager items") {
                if (downloads.isEmpty()) Text("No downloads found", color = TextLo)
                downloads.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, color = TextHi)
                            Text(row.status, color = TextLo, style = ZukoType.small)
                        }
                        Button(onClick = {
                            cancelDownload(context, row.id)
                            downloads = readDownloads(context)
                        }) { Text("Remove") }
                    }
                }
                Button(onClick = { downloads = readDownloads(context) }) { Text("Refresh downloads") }
            }
        }
        item {
            SettingCard("App updates", "Keep ZUKO TV up to date") {
                Button(onClick = { update = "You're using ZUKO TV ${BuildConfig.VERSION_NAME}. Check the official release source for newer versions." }) {
                    Text("Check update information")
                }
                Text(update, color = TextLo, style = ZukoType.small)
            }
        }
        if (message.isNotBlank()) item { Text(message, color = TextHi) }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = TextHi, style = ZukoType.rowTitle)
            Text(subtitle, color = TextLo, style = ZukoType.small)
            content()
        }
    }
}

private fun readDownloads(context: Context): List<DownloadRow> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return emptyList()
    val cursor: Cursor = manager.query(DownloadManager.Query())
    return cursor.use { c ->
        val rows = mutableListOf<DownloadRow>()
        val idIndex = c.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleIndex = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
        while (c.moveToNext()) {
            val id = if (idIndex >= 0) c.getLong(idIndex) else -1L
            val title = if (titleIndex >= 0) c.getString(titleIndex).orEmpty() else "Download"
            val statusCode = if (statusIndex >= 0) c.getInt(statusIndex) else -1
            val status = when (statusCode) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                DownloadManager.STATUS_FAILED -> "Failed"
                else -> "Unknown"
            }
            rows += DownloadRow(id, title, status)
        }
        rows
    }
}

private fun cancelDownload(context: Context, id: Long) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    manager.remove(id)
}
