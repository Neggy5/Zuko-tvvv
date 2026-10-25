package com.zuko.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class StreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class MediaInfoResponse(
    val file: String? = null,
    val key: String? = null,
)

@Serializable
data class StreamResponse(
    val stream: String? = null,
    val m3u8: String? = null,
    val url: String? = null,
) {
    val bestUrl: String? get() = sequenceOf(stream, m3u8, url)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
}

class StreamApi(
    private val client: OkHttpClient,
    baseUrl: String,
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val isConfigured: Boolean get() = baseUrl.startsWith("http", ignoreCase = true)

    suspend fun mediaInfo(imdbId: String): MediaInfoResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) throw StreamException("Streaming is not configured. Set STREAM_API_BASE in local.properties or your build environment.")
        val url = try {
            "$baseUrl/api/v1/mediaInfo".toHttpUrl().newBuilder()
                .addQueryParameter("id", imdbId).build()
        } catch (e: IllegalArgumentException) {
            throw StreamException("The streaming API base URL is invalid. Check STREAM_API_BASE.", e)
        }
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        execute(request) { body -> json.decodeFromString<MediaInfoResponse>(body) }
    }


    /** Resolve a publicly available movie from the Internet Archive-backed service. */
    suspend fun publicStream(title: String, year: String? = null): StreamResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) throw StreamException("Public streaming is not configured. Set STREAM_API_BASE to your deployed ZUKO streaming backend.")
        val url = try {
            val builder = "$baseUrl/api/v1/public/stream".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
            year?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("year", it) }
            builder.build()
        } catch (e: IllegalArgumentException) {
            throw StreamException("The streaming backend URL is invalid. Check STREAM_API_BASE.", e)
        }
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        execute(request) { body -> json.decodeFromString<StreamResponse>(body) }
    }

    suspend fun getStream(file: String, key: String): StreamResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) throw StreamException("Streaming is not configured. Set STREAM_API_BASE in local.properties or your build environment.")
        val url = try { "$baseUrl/api/v1/getStream".toHttpUrl() }
        catch (e: IllegalArgumentException) { throw StreamException("The streaming API base URL is invalid. Check STREAM_API_BASE.", e) }
        val body = json.encodeToString(buildJsonObject {
            put("file", file); put("key", key)
        }).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("Accept", "application/json").post(body).build()
        execute(request) { json.decodeFromString<StreamResponse>(it) }
    }

    private inline fun <T> execute(request: Request, decode: (String) -> T): T {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        400, 422 -> "The streaming service rejected this title. Try another movie."
                        401, 403 -> "The streaming service denied the request. Check the API configuration."
                        404 -> "No stream was found for this title."
                        429 -> "The streaming service is busy. Try again shortly."
                        else -> "The streaming service returned an error (${response.code})."
                    }
                    throw StreamException(message)
                }
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) throw StreamException("The streaming service returned an empty response.")
                return decode(text)
            }
        } catch (e: StreamException) {
            throw e
        } catch (e: IOException) {
            throw StreamException("Can't reach the streaming service. Check the TV's internet connection and STREAM_API_BASE.", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw StreamException("The streaming service returned an unreadable response. Check that the API is compatible.", e)
        }
    }
}
