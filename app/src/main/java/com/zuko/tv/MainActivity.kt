package com.zuko.tv

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zuko.tv.ui.detail.DetailScreen
import com.zuko.tv.ui.detail.DetailViewModel
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
                BackHandler(enabled = showSplash) { showSplash = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        // While the intro plays, any key press skips it (and is not passed on).
                        .onPreviewKeyEvent { event ->
                            if (showSplash) {
                                if (event.type == KeyEventType.KeyDown) showSplash = false
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    // The app is composed underneath so movies load while the intro plays.
                    ZukoApp(dark = dark, onToggleTheme = { app.themeStore.toggle() })
                    if (showSplash) {
                        SplashIntro(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun ZukoApp(dark: Boolean, onToggleTheme: () -> Unit) {
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
                onOpenMovie = { id: Int ->
                    runCatching { navController.navigate("detail/$id") }
                },
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
