package com.zuko.tv.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zuko.tv.BuildConfig
import com.zuko.tv.data.FootballApi
import com.zuko.tv.data.FootballMatch
import com.zuko.tv.data.FootballStream
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun LiveFootballScreen(
    client: OkHttpClient,
    onBack: () -> Unit,
) {
    val api = remember { FootballApi(client) }
    var tab by remember { mutableStateOf(0) } // 0 live, 1 today
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<FootballMatch>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<FootballMatch, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                if (!api.isConfigured) {
                    error = "Add RUNFLIX_API_KEY (free at apimovie.runflix.name.ng/request-key) in GitHub secrets / local.properties"
                    matches = emptyList()
                } else {
                    matches = if (tab == 0) api.live() else api.today()
                    if (matches.isEmpty()) error = "No matches right now."
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load"
                matches = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(tab) { reload() }

    if (playing != null) {
        val (match, url) = playing!!
        LiveStreamPlayer(
            title = "${match.home} vs ${match.away}",
            url = url,
            onBack = { playing = null },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = TextHi) }
            Text("⚽ Live Football", style = ZukoType.title, color = TextHi, modifier = Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("Refresh", color = TextHi) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("Live", tab == 0) { tab = 0 }
            TabChip("Today", tab == 1) { tab = 1 }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Source: Runflix football API",
            color = TextLo,
            style = ZukoType.meta,
        )
        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            error != null && matches.isEmpty() -> Text(error!!, color = Color(0xFFFF8A80))
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(matches, key = { it.id }) { m ->
                    MatchRow(m) {
                        scope.launch {
                            try {
                                val streams = api.streams(m.id)
                                val best = streams.firstOrNull()?.url
                                    ?: throw IllegalStateException("No stream for this match")
                                playing = m to best
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextLo,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF6C4CFF) else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MatchRow(m: FootballMatch, onPlay: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable(onClick = onPlay)
            .padding(14.dp),
    ) {
        Text(m.league ?: "Football", color = TextLo, style = ZukoType.meta)
        Spacer(Modifier.height(4.dp))
        Text(
            "${m.home}  ${m.homeScore ?: ""} – ${m.awayScore ?: ""}  ${m.away}",
            color = TextHi,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (m.isLive) "LIVE · ${m.status}" else m.status,
            color = if (m.isLive) Color(0xFF4ADE80) else TextLo,
            style = ZukoType.meta,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveStreamPlayer(title: String, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Close", color = Color.White) }
            Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
