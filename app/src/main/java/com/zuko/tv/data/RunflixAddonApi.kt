package com.zuko.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Client for the ZUKO Runflix Stremio addon hosted on Railway.
 * Base example: https://fuzzy-fortnight-production.up.railway.app
 */
class RunflixAddonApi(
    private val client: OkHttpClient,
    baseUrl: String,
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val isConfigured: Boolean get() = baseUrl.startsWith("http", ignoreCase = true)

    @Serializable
    data class MetaPreview(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val releaseInfo: String? = null,
    )

    @Serializable
    data class CatalogResponse(val metas: List<MetaPreview> = emptyList())

    @Serializable
    data class StreamItem(
        val name: String? = null,
        val title: String? = null,
        val url: String? = null,
    )

    @Serializable
    data class StreamResponse(val streams: List<StreamItem> = emptyList())

    suspend fun searchMovies(query: String): List<MetaPreview> = withContext(Dispatchers.IO) {
        if (!isConfigured || query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            .replace("+", "%20")
        // Stremio path: /catalog/movie/runflix-search/search=<q>.json
        val path = "/catalog/movie/runflix-search/search=$encoded.json"
        val body = get(path)
        json.decodeFromString<CatalogResponse>(body).metas
            .filter { it.id != null && (it.type == null || it.type == "movie") }
    }

    suspend fun streamsFor(metaId: String): List<StreamItem> = withContext(Dispatchers.IO) {
        if (!isConfigured || metaId.isBlank()) return@withContext emptyList()
        val id = metaId.removePrefix("runflix:").let { "runflix:$it" }
        val body = get("/stream/movie/$id.json")
        json.decodeFromString<StreamResponse>(body).streams
            .filter { !it.url.isNullOrBlank() }
    }

    /**
     * Search by title (+ optional year), pick best meta match, return best stream URL.
     */
    suspend fun resolvePlayUrl(title: String, year: String? = null): String? {
        val metas = searchMovies(title)
        if (metas.isEmpty()) return null

        val titleNorm = title.trim().lowercase(Locale.US)
        val yearNorm = year?.trim()?.take(4)

        val ranked = metas.sortedWith(
            compareByDescending<MetaPreview> {
                val n = it.name?.trim()?.lowercase(Locale.US).orEmpty()
                when {
                    n == titleNorm -> 3
                    n.contains(titleNorm) || titleNorm.contains(n) -> 2
                    else -> 1
                }
            }.thenByDescending {
                if (yearNorm != null && it.releaseInfo?.contains(yearNorm) == true) 1 else 0
            }
        )

        for (meta in ranked) {
            val id = meta.id ?: continue
            val streams = streamsFor(id)
            val best = streams.maxByOrNull { qualityScore(it.name + " " + it.title) }
            val url = best?.url?.trim()
            if (!url.isNullOrEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                return url
            }
        }
        return null
    }

    private fun qualityScore(label: String): Int {
        val s = label.lowercase(Locale.US)
        return when {
            "1080" in s -> 1080
            "720" in s -> 720
            "480" in s -> 480
            "360" in s -> 360
            else -> 1
        }
    }

    private fun get(path: String): String {
        val url = try {
            (baseUrl + path).toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw StreamException("Runflix addon URL is invalid. Check RUNFLIX_ADDON_BASE.", e)
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ZukoTV/0.2")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw StreamException(
                        when (response.code) {
                            404 -> "Runflix found no match for this title."
                            429 -> "Runflix is rate limiting. Try again shortly."
                            else -> "Runflix addon error (${response.code})."
                        }
                    )
                }
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) throw StreamException("Runflix addon returned an empty response.")
                return text
            }
        } catch (e: StreamException) {
            throw e
        } catch (e: IOException) {
            throw StreamException(
                "Can't reach the Runflix addon. Check the TV internet connection and RUNFLIX_ADDON_BASE.",
                e,
            )
        }
    }
}
