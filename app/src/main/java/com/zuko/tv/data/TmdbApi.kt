package com.zuko.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class TmdbException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * TMDB v3 client routed through the ZUKO-TV Railway proxy.
 * The TMDB API secret stays on the backend and is not sent by this client.
 */
class TmdbApi(
    private val client: OkHttpClient,
    private val proxyBaseUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val isConfigured: Boolean
        get() = proxyBaseUrl.startsWith("https://") &&
            proxyBaseUrl.contains("/api/tmdb/3/")

    private suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val url = try {
            proxyBaseUrl.toHttpUrl().newBuilder().apply {
                addPathSegments(path)
                addQueryParameter("language", "en-US")
                query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
        } catch (e: IllegalArgumentException) {
            throw TmdbException("The TMDB proxy URL is invalid.", e)
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        400 -> "TMDB rejected the request."
                        429 -> "TMDB is rate limiting this app. Try again in a moment."
                        502, 503 -> "The TMDB proxy is unavailable or not configured on Railway."
                        else -> "TMDB returned an error (${response.code})."
                    }
                    throw TmdbException(message)
                }

                val body = response.body?.string().orEmpty()
                try {
                    json.decodeFromString(serializer, body)
                } catch (e: Exception) {
                    throw TmdbException("TMDB returned an invalid response.", e)
                }
            }
        } catch (e: IOException) {
            throw TmdbException("Can't reach the TMDB proxy. Check the TV's internet connection.", e)
        }
    }

    suspend fun trending() = get("trending/movie/week", MovieList.serializer())
    suspend fun popular() = get("movie/popular", MovieList.serializer())
    suspend fun topRated() = get("movie/top_rated", MovieList.serializer())
    suspend fun nowPlaying() = get("movie/now_playing", MovieList.serializer())
    suspend fun upcoming() = get("movie/upcoming", MovieList.serializer())

    suspend fun genres() = get("genre/movie/list", GenreList.serializer())

    suspend fun popularTv() = get("tv/popular", TvList.serializer())

    suspend fun searchTv(query: String) = get(
        "search/tv",
        TvList.serializer(),
        mapOf("query" to query, "include_adult" to "false"),
    )

    suspend fun tvDetails(id: Int) = get("tv/$id", TvDetails.serializer())

    suspend fun tvSeason(id: Int, season: Int) =
        get("tv/$id/season/$season", TvSeasonDetails.serializer())

    suspend fun discoverGenre(genreId: Int, page: Int = 1) = get(
        path = "discover/movie",
        serializer = MovieList.serializer(),
        query = mapOf(
            "with_genres" to genreId.toString(),
            "page" to page.toString(),
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
        ),
    )

    suspend fun search(query: String, page: Int = 1) = get(
        path = "search/movie",
        serializer = MovieList.serializer(),
        query = mapOf(
            "query" to query,
            "page" to page.toString(),
            "include_adult" to "false",
        ),
    )

    suspend fun details(id: Int) = get(
        path = "movie/$id",
        serializer = MovieDetails.serializer(),
        query = mapOf("append_to_response" to "credits,similar"),
    )
}
