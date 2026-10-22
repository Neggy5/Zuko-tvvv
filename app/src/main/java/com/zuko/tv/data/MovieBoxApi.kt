package com.zuko.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MovieBox integration through the MovieBox-pro API.
 * Resolve signed stream URLs at playback time; do not persist them long-term.
 */
class MovieBoxApi(private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun resolve(title: String, year: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val query = title.trim()
            if (query.isBlank()) return@withContext emptyList()

            val searchUrl = endpoint()
                .newBuilder()
                .addQueryParameter("action", "search")
                .addQueryParameter("keyword", query)
                .build()

            val searchData = getJson(searchUrl)

            val normalizedQuery = normalize(query)

            val candidates = collectObjects(searchData)
                .filter { item ->
                    !item["subjectId"]?.text().isNullOrBlank() &&
                    !item["detailPath"]?.text().isNullOrBlank() &&
                    !item["title"]?.text().isNullOrBlank() &&
                    item["hasResource"]?.text().equals("true", ignoreCase = true)
                }
                .distinctBy { it["subjectId"]?.text() }
                .mapNotNull { item ->
                    val candidateTitle = item["title"]?.text().orEmpty()
                    val normalizedTitle = normalize(candidateTitle)

                    val titleScore = when {
                        normalizedTitle == normalizedQuery -> 100
                        normalizedTitle.contains(normalizedQuery) ||
                            normalizedQuery.contains(normalizedTitle) -> 50
                        else -> 0
                    }

                    if (titleScore == 0) {
                        null
                    } else {
                        val releaseDate = item["releaseDate"]?.text().orEmpty()
                        val yearScore =
                            if (!year.isNullOrBlank() &&
                                releaseDate.startsWith(year)) 10 else 0

                        val movieScore =
                            if (item["subjectType"]?.text() == "1") 5 else 0

                        Triple(item, titleScore + yearScore + movieScore, candidateTitle)
                    }
                }
                .sortedByDescending { it.second }

            val selected = candidates.firstOrNull()?.first
                ?: return@withContext emptyList()

            val subjectId = selected["subjectId"]?.text()
                ?: return@withContext emptyList()

            val detailPath = selected["detailPath"]?.text()
                ?: return@withContext emptyList()

            val downloadUrl = endpoint()
                .newBuilder()
                .addQueryParameter("action", "download")
                .addQueryParameter("subjectId", subjectId)
                .addQueryParameter("detailPath", detailPath)
                .addQueryParameter("se", "0")
                .addQueryParameter("ep", "0")
                .build()

            val downloadData = getJson(downloadUrl)

            collectObjects(downloadData)
                .filter { item ->
                    !item["url"]?.text().isNullOrBlank() &&
                    !item["quality"]?.text().isNullOrBlank()
                }
                .sortedByDescending { item ->
                    item["quality"]?.text()
                        ?.filter { it.isDigit() }
                        ?.toIntOrNull() ?: 0
                }
                .mapNotNull { item ->
                    item["url"]?.text()?.takeIf {
                        it.startsWith("https://") || it.startsWith("http://")
                    }
                }
                .distinct()
        }

    private fun endpoint(): HttpUrl =
        "https://api.omegatech.app/api/movie/MovieBox-pro".toHttpUrlChecked()

    private fun getJson(url: HttpUrl): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ZUKO-TV/1.0")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("MovieBox returned HTTP ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw IOException("MovieBox returned an empty response.")
                }

                return json.parseToJsonElement(body)
            }
        } catch (e: IOException) {
            throw IOException("MovieBox request failed: ${e.message}", e)
        }
    }

    private fun collectObjects(element: JsonElement): List<JsonObject> =
        when (element) {
            is JsonObject ->
                listOf(element) + element.values.flatMap(::collectObjects)

            is JsonArray ->
                element.flatMap(::collectObjects)

            else -> emptyList()
        }

    private fun JsonElement.text(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun String.toHttpUrlChecked(): HttpUrl =
        try {
            this.toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid MovieBox API URL.", e)
        }
}
