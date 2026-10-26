package com.zuko.tv.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zuko.tv.data.LiveTvApi
import com.zuko.tv.data.LiveTvChannel
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Official free Ballon d'Or broadcaster (account required, no paid sub in most markets). */
const val BALLON_DOR_DAZN = "https://www.dazn.com/"

@Composable
fun LiveTvScreen(
    client: OkHttpClient,
    onBack: () -> Unit,
    initialTab: Int = 0, // 0 all, 1 sports, 2 ballon
) {
    val api = remember { LiveTvApi(client) }
    val context = LocalContext.current
    var tab by remember { mutableStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<LiveTvChannel>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                channels = when {
                    tab == 2 -> emptyList() // event card only
                    tab == 1 -> api.sports()
                    query.isNotBlank() -> api.search(query.trim())
                    else -> api.channels()
                }
                if (tab != 2 && channels.isEmpty()) error = "No channels found."
            } catch (e: Exception) {
                error = e.message ?: "Failed"
                channels = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(tab) { reload() }

    if (playing != null) {
        val (title, url) = playing!!
        LivePlayer(
            title = title,
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
            Text("📺 Live TV", style = ZukoType.title, color = TextHi, modifier = Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("Refresh", color = TextHi) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("All", tab == 0) { tab = 0 }
            TabChip("Sports", tab == 1) { tab = 1 }
            TabChip("Ballon d'Or", tab == 2) { tab = 2 }
        }

        Spacer(Modifier.height(8.dp))

        if (tab == 2) {
            BallonDorCard(
                onOpenDazn = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(BALLON_DOR_DAZN)),
                        )
                    }
                },
                onOpenSports = { tab = 1 },
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search channels…", color = TextLo) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
            ),
        )
        TextButton(onClick = { reload() }) { Text("Search", color = TextHi) }

        if (resolving) Text("Opening stream…", color = TextLo, style = ZukoType.meta)
        if (error != null) {
            Text(error!!, color = Color(0xFFFF8A80), style = ZukoType.meta)
            Spacer(Modifier.height(6.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            channels.isEmpty() -> Text(error ?: "Empty", color = TextLo)
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels, key = { it.id }) { ch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel)
                            .clickable(enabled = !resolving) {
                                scope.launch {
                                    resolving = true
                                    error = null
                                    try {
                                        val url = ch.streamUrl?.takeIf { it.startsWith("http") }
                                            ?: api.resolveStream(ch.id)
                                        playing = ch.name to url
                                    } catch (e: Exception) {
                                        error = e.message ?: "Stream failed"
                                    } finally {
                                        resolving = false
                                    }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = ch.logo,
                            contentDescription = ch.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A22)),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, color = TextHi, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(ch.category, if (ch.isLive) "LIVE" else null)
                                    .joinToString(" · "),
                                color = if (ch.isLive) Color(0xFF4ADE80) else TextLo,
                                style = ZukoType.meta,
                            )
                        }
                        Text("Play", color = TextHi)
                    }
                }
            }
        }
    }
}

@Composable
private fun BallonDorCard(
    onOpenDazn: () -> Unit,
    onOpenSports: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(16.dp),
    ) {
        Text("🏆 Ballon d'Or 2026", color = TextHi, fontWeight = FontWeight.Bold, style = ZukoType.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Official free live stream is on DAZN (free account, most countries).\n" +
                "Ceremony: London Palladium · Mon 26 Oct 2026 · from ~20:00 GMT.\n" +
                "France: L'Équipe TV · Spain: Telefónica / Movistar.",
            color = TextLo,
            style = ZukoType.meta,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenDazn) {
            Text("Open DAZN (official free)", color = Color.White)
        }
        TextButton(onClick = onOpenSports) {
            Text("Browse sports Live TV", color = TextHi)
        }
        Text(
            "Runflix has no dedicated Ballon d'Or feed — use DAZN for the official stream.",
            color = TextLo,
            style = ZukoType.meta,
        )
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun LivePlayer(
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
