package com.zuko.tv

import android.app.Application
import android.os.Process
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.NollywoodApi
import com.zuko.tv.data.StreamRepository
import com.zuko.tv.data.TmdbApi
import com.zuko.tv.data.WatchHistory
import com.zuko.tv.security.IntegrityGuard
import com.zuko.tv.ui.theme.ThemeStore
import com.zuko.tv.ui.theme.ZukoPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ZukoApplication : Application() {
    val httpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(req)
            })
            .build()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val watchHistory: WatchHistory by lazy { WatchHistory(this) }

    val themeStore: ThemeStore by lazy { ThemeStore(this) }

    override fun onCreate() {
        super.onCreate()

        // Release-only environment checks (debuggable APK, debugger, common RE tools)
        if (!BuildConfig.DEBUG && !IntegrityGuard.check(this)) {
            // Fail closed without a detailed message (avoids helping attackers)
            Process.killProcess(Process.myPid())
            return
        }

        ZukoPalette.darkness = if (themeStore.dark.value) 1f else 0f

        // Coil3 must use OkHttp network fetcher or many CDNs fail silently in release.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(ctx, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(ctx.cacheDir.resolve("coil_images"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        }

        appScope.launch { streamRepository.warmUp() }
    }

    val repository: MovieRepository by lazy {
        MovieRepository(
            api = TmdbApi(httpClient, BuildConfig.TMDB_PROXY_BASE),
            nollywood = NollywoodApi(httpClient),
        )
    }

    val streamRepository: StreamRepository by lazy {
        StreamRepository(
            client = httpClient,
            streamApiBase = BuildConfig.STREAM_API_BASE,
            runflixAddonBase = BuildConfig.RUNFLIX_ADDON_BASE,
        )
    }
}
