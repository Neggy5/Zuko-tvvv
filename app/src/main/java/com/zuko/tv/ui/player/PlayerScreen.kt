package com.zuko.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movieId: Int,
    movieRepository: MovieRepository,
    streamRepository: StreamRepository,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val appContext = context.applicationContext

    val playerViewModel: PlayerViewModel = viewModel(
        key = "player-$movieId",
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(movieRepository, streamRepository, movieId)
            }
        },
    )
    val state by playerViewModel.state.collectAsState()
    var inflateError by remember { mutableStateOf<String?>(null) }

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
        val crashMsg = inflateError
        when {
            crashMsg != null -> {
                ErrorPane(message = crashMsg, onRetry = {
                    inflateError = null
                    playerViewModel.resolve()
                }, onBack = onBack)
            }
            state is PlayerState.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            state is PlayerState.Error -> {
                ErrorPane(
                    message = (state as PlayerState.Error).message,
                    onRetry = { playerViewModel.resolve() },
                    onBack = onBack,
                )
            }
            state is PlayerState.Ready -> {
                val ready = state as PlayerState.Ready
                LaunchedEffect(ready.url) {
                    runCatching {
                        player.setMediaItem(MediaItem.fromUri(ready.url))
                        player.prepare()
                        player.playWhenReady = true
                    }.onFailure { e ->
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
                        try {
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                keepScreenOn = true
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        } catch (e: Throwable) {
                            inflateError =
                                "Player UI failed: ${e.javaClass.simpleName}: ${e.message}"
                            // Return empty view so Compose does not hard-crash
                            android.view.View(ctx)
                        }
                    },
                    update = { view ->
                        if (view is PlayerView && view.player !== player) {
                            view.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = message,
            color = Color(0xFFFF8A80),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Retry")
        }
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
