package com.zuko.tv.data

import com.zuko.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class AnimeItem(
    val id: String,
    val title: String,
    val cover: String?,
    val year: Int?,
    val rating: Double?,
    val description: String?,
    val category: String?,
)

data class AnimeStream(
    val url: String,
    val quality: String?,
    val type: String?,
)

/** Runflix anime catalog + stream/download. */
class AnimeApi(
    private val client: OkHttpClient,
    private val baseUrl: String = BuildConfig.RUNFLIX_API_BASE.trimEnd('/'),
    private val apiKey: String = BuildConfig.RUNFLIX_API_KEY.ifBlank { "zukoxmdapi" },
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private suspend fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val root = baseUrl.toHttpUrlOrNull()
                ?: throw IllegalStateException("Invalid RUNFLIX_API_BASE")
            val url = root.newBuilder()
                .addPathSegments(path.trimStart('/'))
                .apply {
                    query.forEach { (k, v) -> if (v.isNotBlank()) addQueryParameter(k, v) }
                    if (apiKey.isNotBlank()) addQueryParameter("apikey", apiKey)
                }
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ZUKO-TV/1.0")
                .apply {
                    if (apiKey.isNotBlank()) {
                        header("X-API-Key", apiKey)
                        header("Authorization", "ApiKey $apiKey")
                    }
                }
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
                    throw IllegalStateException(msg)
                }
                JSONObject(body)
            }
        }

    suspend fun trending(limit: Int = 30): List<AnimeItem> =
        parseItems(getJson("v1/anime/trending", mapOf("limit" to "$limit")))

    suspend fun popular(limit: Int = 30): List<AnimeItem> =
        parseItems(getJson("v1/anime/popular", mapOf("limit" to "$limit")))

    suspend fun search(q: String, limit: Int = 30): List<AnimeItem> =
        parseItems(getJson("v1/search/anime", mapOf("q" to q, "limit" to "$limit")))

    suspend fun detail(id: String): AnimeItem {
        val o = getJson("v1/anime/detail/$id")
        return parseOne(o) ?: AnimeItem(id, o.optString("title").ifBlank { id }, null, null, null, null, null)
    }

    suspend fun streams(id: String): List<AnimeStream> {
        val root = getJson("v1/stream/$id")
        val out = mutableListOf<AnimeStream>()
        fun add(url: String?, quality: String? = null, type: String? = null) {
            val u = url?.trim().orEmpty()
            if (u.startsWith("http")) out += AnimeStream(u, quality, type)
        }
        add(root.optString("streamUrl").takeIf { it.isNotBlank() }, type = root.optString("type"))
        add(root.optString("url").takeIf { it.isNotBlank() }, type = root.optString("type"))
        val qualities = root.optJSONArray("qualities")
        if (qualities != null) {
            for (i in 0 until qualities.length()) {
                val q = qualities.optJSONObject(i) ?: continue
                add(
                    q.optString("url").takeIf { it.isNotBlank() },
                    quality = q.optString("quality").ifBlank { q.optString("title") },
                    type = q.optString("format").ifBlank { q.optString("type") },
                )
            }
        }
        return out.distinctBy { it.url }
    }

    private fun parseItems(root: JSONObject): List<AnimeItem> {
        val arr = root.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("results")
            ?: JSONArray()
        val out = ArrayList<AnimeItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseOne(o)?.let { out += it }
        }
        return out
    }

    private fun parseOne(o: JSONObject): AnimeItem? {
        val id = o.optString("id").ifBlank { o.optString("subjectId") }
        if (id.isBlank()) return null
        return AnimeItem(
            id = id,
            title = o.optString("title").ifBlank { "Anime" },
            cover = o.optString("cover").takeIf { it.startsWith("http") },
            year = o.optInt("year").takeIf { it > 0 },
            rating = o.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
            description = o.optString("description").takeIf { it.isNotBlank() },
            category = o.optString("category").takeIf { it.isNotBlank() },
        )
    }
}
