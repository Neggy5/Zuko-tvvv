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
 * Minimal TMDB v3 client. [credential] may be the short v3 API key or the long
 * v4 "read access token" (sent as a Bearer header).
 */
class TmdbApi(
    private val client: OkHttpClient,
    private val credential: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val isConfigured: Boolean get() = credential.isNotBlank()

    private val isBearer: Boolean get() = credential.length > 40 && credential.contains('.')

    private suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().newBuilder().apply {
            addPathSegments(path)
            addQueryParameter("language", "en-US")
            query.forEach { (k, v) -> addQueryParameter(k, v) }
            if (!isBearer) addQueryParameter("api_key", credential)
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (isBearer) header("Authorization", "Bearer $credential") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "TMDB rejected the API key. Check TMDB_API_KEY."
                        404 -> "TMDB could not find that title."
                        429 -> "TMDB is rate limiting this app. Try again in a moment."
                        else -> "TMDB returned an error (${response.code})."
                    }
                    throw TmdbException(message)
                }
                val body = response.body?.string().orEmpty()
                json.decodeFromString(serializer, body)
            }
        } catch (e: IOException) {
            throw TmdbException("Can't reach TMDB. Check the TV's internet connection.", e)
        }
    }

    suspend fun trending() = get("trending/movie/week", MovieList.serializer())
    suspend fun popular() = get("movie/popular", MovieList.serializer())
    suspend fun topRated() = get("movie/top_rated", MovieList.serializer())
    suspend fun nowPlaying() = get("movie/now_playing", MovieList.serializer())
    suspend fun upcoming() = get("movie/upcoming", MovieList.serializer())

    suspend fun details(id: Int) = get(
        path = "movie/$id",
        serializer = MovieDetails.serializer(),
        query = mapOf("append_to_response" to "credits,similar"),
    )

    private companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
    }
}
