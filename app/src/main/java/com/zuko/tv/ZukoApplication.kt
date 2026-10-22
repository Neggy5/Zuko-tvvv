package com.zuko.tv

import android.app.Application
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository
import com.zuko.tv.data.TmdbApi
import com.zuko.tv.data.WatchHistory
import com.zuko.tv.ui.theme.ThemeStore
import com.zuko.tv.ui.theme.ZukoPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ZukoApplication : Application() {
    private val client by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val watchHistory: WatchHistory by lazy { WatchHistory(this) }

    val themeStore: ThemeStore by lazy { ThemeStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Start with the saved mode already applied so there is no wrong-colour flash.
        ZukoPalette.darkness = if (themeStore.dark.value) 1f else 0f
        // Wake the Railway addon early so Play doesn't hit a cold start.
        appScope.launch { streamRepository.warmUp() }
    }

    val repository: MovieRepository by lazy {
        MovieRepository(TmdbApi(client, BuildConfig.TMDB_PROXY_BASE))
    }

    val streamRepository: StreamRepository by lazy {
        StreamRepository(
            client = client,
            streamApiBase = BuildConfig.STREAM_API_BASE,
            runflixAddonBase = BuildConfig.RUNFLIX_ADDON_BASE,
        )
    }
}
