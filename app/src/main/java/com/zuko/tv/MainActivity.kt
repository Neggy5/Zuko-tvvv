package com.zuko.tv

import android.os.Bundle
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
import androidx.core.net.toUri
import com.zuko.tv.ui.detail.DetailScreen
import com.zuko.tv.ui.detail.DetailViewModel
import com.zuko.tv.ui.home.HomeScreen
import com.zuko.tv.ui.home.HomeViewModel
import com.zuko.tv.ui.player.PlayerScreen
import com.zuko.tv.ui.theme.ZukoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            ZukoTheme {
                ZukoApp()
            }
        }
    }
}

@Composable
private fun ZukoApp() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ZukoApplication
    val repository = app.repository

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
                factory = viewModelFactory { initializer { HomeViewModel(repository) } },
            )
            HomeScreen(viewModel = viewModel, onOpenMovie = { id: Int -> navController.navigate("detail/$id") })
        }
        composable(
            route = "detail/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("movieId") ?: return@composable
            val viewModel: DetailViewModel = viewModel(
                key = "detail-$id",
                factory = viewModelFactory { initializer { DetailViewModel(repository, id) } },
            )
            DetailScreen(
                viewModel = viewModel,
                onOpenMovie = { next: Int -> navController.navigate("detail/$next") },
                onPlayMovie = { movie ->
                    val imdb = movie.imdbId.orEmpty().let { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()) }
                    val title = java.net.URLEncoder.encode(movie.title, Charsets.UTF_8.name())
                    navController.navigate("player/${movie.id}?imdb=$imdb&title=$title")
                },
            )
        }
        composable(
            route = "player/{movieId}?imdb={imdb}&title={title}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.IntType },
                navArgument("imdb") { type = NavType.StringType; nullable = false; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; nullable = false; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getInt("movieId") ?: return@composable
            val imdb = entry.arguments?.getString("imdb")?.takeIf { it.isNotBlank() }
            val title = entry.arguments?.getString("title").orEmpty()
            PlayerScreen(movieId = id, title = title, imdbId = imdb, onBack = { navController.popBackStack() })
        }
    }
}
