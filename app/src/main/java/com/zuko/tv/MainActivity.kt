package com.zuko.tv

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.zuko.tv.ui.splash.SplashIntro
import com.zuko.tv.ui.theme.Ink
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zuko.tv.ui.detail.DetailScreen
import com.zuko.tv.ui.detail.DetailViewModel
import com.zuko.tv.ui.LiveFootballScreen
import com.zuko.tv.ui.home.HomeScreen
import com.zuko.tv.ui.home.HomeViewModel
import com.zuko.tv.ui.player.PlayerScreen
import com.zuko.tv.ui.theme.ZukoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ZukoApplication
        setContent {
            val dark by app.themeStore.dark.collectAsState()
            ZukoTheme(darkTheme = dark) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                val splashStartedAt = remember { SystemClock.uptimeMillis() }
                BackHandler(enabled = showSplash) { showSplash = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        // While the intro plays, any key press skips it (and is not passed on).
                        .onPreviewKeyEvent { event ->
                            if (showSplash) {
                                // Ignore held/repeating keys and the first moments, so the key
                                // press that launched the app can't skip the intro.
                                if (event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 &&
                                    SystemClock.uptimeMillis() - splashStartedAt > 800
                                ) {
                                    showSplash = false
                                }
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    // The app is composed underneath so movies load while the intro plays.
                    ZukoApp(dark = dark, onToggleTheme = { app.themeStore.toggle() }, onThemeMode = { app.themeStore.setMode(it) })
                    if (showSplash) {
                        SplashIntro(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun ZukoApp(dark: Boolean, onToggleTheme: () -> Unit, onThemeMode: (String) -> Unit) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ZukoApplication
    val repository = app.repository
    val streamRepository = app.streamRepository

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory { initializer { HomeViewModel(repository, app.watchHistory) } },
            )
            HomeScreen(
                viewModel = viewModel,
                darkTheme = dark,
                onToggleTheme = onToggleTheme,
                onOpenSettings = { navController.navigate("settings") },
                onOpenGenres = { navController.navigate("genres") },
                onOpenTvShows = { navController.navigate("tvshows") },
                onOpenFootball = { navController.navigate("football") },
                onOpenMovie = { id: Int ->
                    runCatching { navController.navigate("detail/$id") }
                },
            )
        }
        composable("genres") {
            com.zuko.tv.ui.GenreScreen(repository = repository, onBack = { navController.popBackStack() }, onOpenMovie = { id -> navController.navigate("detail/$id") })
        }
        composable("tvshows") {
            com.zuko.tv.ui.TvShowsScreen(repository = repository, onBack = { navController.popBackStack() }, onPlay = { showId, title, year, season, episode, count ->
                navController.navigate("tvplayer/$showId/${Uri.encode(title)}/${year.ifBlank { "0" }}/$season/$episode/$count")
            })
        }
        composable(
            route = "tvplayer/{showId}/{title}/{year}/{season}/{episode}/{count}",
            arguments = listOf(
                navArgument("showId") { type = NavType.IntType }, navArgument("title") { type = NavType.StringType },
                navArgument("year") { type = NavType.StringType }, navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }, navArgument("count") { type = NavType.IntType },
            ),
        ) { entry ->
            val showId = entry.arguments?.getInt("showId") ?: return@composable
            val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
            val year = entry.arguments?.getString("year").orEmpty().takeUnless { it == "0" }.orEmpty()
            val season = entry.arguments?.getInt("season") ?: 1
            val episode = entry.arguments?.getInt("episode") ?: 1
            val count = entry.arguments?.getInt("count") ?: episode
            com.zuko.tv.ui.TvPlayerScreen(showId, title, year, season, episode, count, streamRepository,
                onBack = { navController.popBackStack() },
                onNextEpisode = { navController.navigate("tvplayer/$showId/${Uri.encode(title)}/${year.ifBlank { "0" }}/$season/${episode + 1}/$count") { popUpTo("tvplayer/$showId/${Uri.encode(title)}/${year.ifBlank { "0" }}/$season/$episode/$count") { inclusive = true } } },
            )
        }
        composable("football") {
            LiveFootballScreen(
                client = app.httpClient,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            val mode by app.themeStore.mode.collectAsState()
            com.zuko.tv.ui.SettingsScreen(
                onBack = { navController.popBackStack() },
                watchHistory = app.watchHistory,
                currentTheme = mode,
                onTheme = onThemeMode,
            )
        }
        composable(
            route = "detail/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("movieId") ?: return@composable
            val viewModel: DetailViewModel = viewModel(
                key = "detail-$id",
                factory = viewModelFactory {
                    initializer {
                        DetailViewModel(app, repository, streamRepository, id)
                    }
                },
            )
            DetailScreen(
                viewModel = viewModel,
                onOpenMovie = { next: Int ->
                    runCatching { navController.navigate("detail/$next") }
                },
                onPlayMovie = { movie ->
                    runCatching { navController.navigate("player/${movie.id}") }
                },
            )
        }
        composable(
            route = "player/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("movieId") ?: return@composable
            PlayerScreen(
                movieId = id,
                movieRepository = repository,
                streamRepository = streamRepository,
                watchHistory = app.watchHistory,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
