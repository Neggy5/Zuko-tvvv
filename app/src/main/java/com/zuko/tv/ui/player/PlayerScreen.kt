package com.zuko.tv.ui.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.zuko.tv.ZukoApplication

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(movieId: Int, title: String, imdbId: String?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as ZukoApplication
    val playerViewModel: PlayerViewModel = viewModel(
        key = "player-$movieId-${imdbId.orEmpty()}",
        factory = viewModelFactory {
            initializer { PlayerViewModel(app.streamRepository, movieId, imdbId, title) }
        },
    )
    val state by playerViewModel.state.collectAsState()
    val player = androidx.compose.runtime.remember(context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (val current = state) {
            PlayerState.Loading -> CircularProgressIndicator(color = Color.White)
            is PlayerState.Error -> Text(
                text = current.message,
                color = Color.Red,
            )
            is PlayerState.Ready -> {
                androidx.compose.runtime.LaunchedEffect(current.url) {
                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("ZUKO-TV/1.0")
                    // DefaultMediaSourceFactory selects HLS or progressive playback from the URL.
                    val source = DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(current.url))
                    player.setMediaSource(source)
                    player.prepare()
                    player.playWhenReady = true
                }
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            contentDescription = title
                            this.player = player
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
