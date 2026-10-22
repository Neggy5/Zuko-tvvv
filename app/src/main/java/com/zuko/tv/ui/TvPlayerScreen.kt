package com.zuko.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.zuko.tv.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(showId: Int, showTitle: String, year: String, season: Int, episode: Int, episodeCount: Int, streams: StreamRepository, onBack: () -> Unit, onNextEpisode: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val player = remember {
        val http = DefaultHttpDataSource.Factory().setUserAgent("ZUKO-TV/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(20000).setReadTimeoutMs(30000)
        ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var readyUrl by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val episodeLabel = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !ended) {
                    ended = true
                    if (episode < episodeCount) onNextEpisode() else onBack()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); runCatching { player.release() } }
    }
    LaunchedEffect(showId, season, episode) {
        loading = true; error = null; readyUrl = null; ended = false
        val titleQuery = "$showTitle $episodeLabel"
        runCatching { withContext(Dispatchers.IO) { streams.resolveStreams(showId, null, titleQuery, year, forceRefresh = true).firstOrNull() } }
            .onSuccess { url -> if (url.isNullOrBlank()) error = "No stream found for $titleQuery." else readyUrl = url }
            .onFailure { error = it.message ?: "Couldn't resolve this episode." }
        loading = false
    }
    LaunchedEffect(readyUrl) {
        readyUrl?.let { url -> runCatching { player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.playWhenReady = true }.onFailure { error = it.message ?: "Playback failed." } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
        if (loading) CircularProgressIndicator(color = Color.White)
        error?.let { message ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color(0xDD111111)).padding(24.dp)) {
                Text("$showTitle · $episodeLabel", color = Color.White)
                Spacer(Modifier.height(8.dp)); Text(message, color = Color.White)
                Row { Button(onClick = { scope.launch { readyUrl = null; loading = true; error = null; runCatching { streams.resolveStreams(showId, null, "$showTitle $episodeLabel", year, true).firstOrNull() }.onSuccess { if (it == null) error = "No stream found." else readyUrl = it }.onFailure { error = it.message ?: "Retry failed." }; loading = false } }) { Text("Retry") }; Spacer(Modifier.width(8.dp)); Button(onClick = onBack) { Text("Back") } }
            }
        }
    }
}
