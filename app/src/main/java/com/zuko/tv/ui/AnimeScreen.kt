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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.zuko.tv.data.AnimeApi
import com.zuko.tv.data.AnimeItem
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun AnimeScreen(
    client: OkHttpClient,
    onBack: () -> Unit,
) {
    val api = remember { AnimeApi(client) }
    var tab by remember { mutableStateOf(0) } // 0 trending 1 popular 2 search
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<AnimeItem, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                if (!api.isConfigured) {
                    error = "Missing Runflix API key"
                    items = emptyList()
                } else {
                    items = when (tab) {
                        1 -> api.popular()
                        2 -> if (query.isBlank()) emptyList() else api.search(query.trim())
                        else -> api.trending()
                    }
                    if (items.isEmpty()) error = if (tab == 2 && query.isBlank()) "Type a search." else "No anime found."
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed"
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(tab) { if (tab != 2) reload() }

    if (playing != null) {
        val (item, url) = playing!!
        AnimePlayer(
            title = item.title,
            url = url,
            onBack = { playing = null },
            onError = { msg ->
                error = msg
                playing = null
            },
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
            Text("🎌 Anime", style = ZukoType.title, color = TextHi, modifier = Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("Refresh", color = TextHi) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Trending", tab == 0) { tab = 0 }
            Chip("Popular", tab == 1) { tab = 1 }
            Chip("Search", tab == 2) { tab = 2 }
        }
        if (tab == 2) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search anime…", color = TextLo) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextHi,
                    unfocusedTextColor = TextHi,
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                ),
            )
            TextButton(onClick = { reload() }) { Text("Search", color = TextHi) }
        }
        if (resolving) Text("Opening stream…", color = TextLo, style = ZukoType.meta)
        if (error != null) {
            Text(error!!, color = Color(0xFFFF8A80), style = ZukoType.meta)
            Spacer(Modifier.height(6.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            items.isEmpty() -> Text(error ?: "Empty", color = TextLo)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Panel)
                            .clickable(enabled = !resolving) {
                                scope.launch {
                                    resolving = true
                                    error = null
                                    try {
                                        val streams = api.streams(item.id)
                                        val url = streams.firstOrNull()?.url
                                            ?: throw IllegalStateException("No stream")
                                        playing = item to url
                                    } catch (e: Exception) {
                                        error = e.message ?: "Stream failed"
                                    } finally {
                                        resolving = false
                                    }
                                }
                            }
                            .padding(6.dp),
                    ) {
                        AsyncImage(
                            model = item.cover,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A22)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.title,
                            color = TextHi,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = ZukoType.meta,
                        )
                        val meta = listOfNotNull(
                            item.year?.toString(),
                            item.rating?.let { String.format("%.1f", it) },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, color = TextLo, style = ZukoType.meta)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextLo,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF6C4CFF) else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun AnimePlayer(
    title: String,
    url: String,
    onBack: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ZUKO-TV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        onError(error.localizedMessage ?: "Playback error")
                    }
                })
            }
    }
    DisposableEffect(url) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Close", color = Color.White) }
            Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
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
