package com.zuko.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.zuko.tv.ZukoApplication
import com.zuko.tv.data.StreamRepository

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(movieId: Int, title: String, imdbId: String?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val appContext = context.applicationContext

    val streamRepository: StreamRepository = remember(appContext) {
        val app = appContext as? ZukoApplication
            ?: error("ZukoApplication not registered — check AndroidManifest android:name")
        app.streamRepository
    }

    val playerViewModel: PlayerViewModel = viewModel(
        key = "player-$movieId-${imdbId.orEmpty()}-$title",
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(streamRepository, movieId, imdbId, title)
            }
        },
    )
    val state by playerViewModel.state.collectAsState()

    // Use application context so Activity recreation does not leak / crash the player
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ZUKO-TV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    DisposableEffect(player) {
        onDispose {
            runCatching {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            PlayerState.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            is PlayerState.Error -> {
                Text(
                    text = current.message,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            is PlayerState.Ready -> {
                LaunchedEffect(current.url) {
                    runCatching {
                        player.setMediaItem(MediaItem.fromUri(current.url))
                        player.prepare()
                        player.playWhenReady = true
                    }.onFailure { e ->
                        // Surface as UI error instead of crashing
                        playerViewModel.reportPlaybackError(
                            e.message ?: "Player failed to start"
                        )
                    }
                }

                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            playerViewModel.reportPlaybackError(
                                error.localizedMessage
                                    ?: error.cause?.message
                                    ?: "Playback error (${error.errorCode})"
                            )
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            keepScreenOn = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== player) view.player = player
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
