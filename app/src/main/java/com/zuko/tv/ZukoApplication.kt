package com.zuko.tv

import android.app.Application
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository
import com.zuko.tv.data.TmdbApi
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

    val repository: MovieRepository by lazy {
        MovieRepository(TmdbApi(client, BuildConfig.TMDB_API_KEY))
    }

    val streamRepository: StreamRepository by lazy {
        StreamRepository(
            client = client,
            streamApiBase = BuildConfig.STREAM_API_BASE,
            runflixAddonBase = BuildConfig.RUNFLIX_ADDON_BASE,
        )
    }
}
