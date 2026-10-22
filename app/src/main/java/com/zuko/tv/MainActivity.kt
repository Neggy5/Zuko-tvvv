package com.zuko.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            HomeScreen(
                viewModel = viewModel,
                onOpenMovie = { id: Int -> navController.navigate("detail/$id") },
            )
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
                    val titleEnc = URLEncoder.encode(
                        movie.title.ifBlank { "Movie" },
                        StandardCharsets.UTF_8.name(),
                    ).replace("+", "%20")
                    val imdbEnc = URLEncoder.encode(
                        movie.imdbId.orEmpty(),
                        StandardCharsets.UTF_8.name(),
                    ).replace("+", "%20")
                    // Path-based args avoid Navigation query-string parsing crashes
                    navController.navigate("player/${movie.id}/$titleEnc?imdb=$imdbEnc")
                },
            )
        }
        composable(
            route = "player/{movieId}/{title}?imdb={imdb}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.IntType },
                navArgument("title") { type = NavType.StringType },
                navArgument("imdb") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getInt("movieId") ?: return@composable
            val titleArg = entry.arguments?.getString("title").orEmpty()
            val title = try {
                URLDecoder.decode(titleArg, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                titleArg
            }
            val imdbRaw = entry.arguments?.getString("imdb")
            val imdb = imdbRaw?.takeIf { it.isNotBlank() }?.let {
                try {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    it
                }
            }
            PlayerScreen(
                movieId = id,
                title = title,
                imdbId = imdb,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
