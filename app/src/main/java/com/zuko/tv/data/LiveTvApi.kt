package com.zuko.tv.data

import com.zuko.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LiveTvChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val category: String?,
    val isLive: Boolean = true,
    val streamUrl: String? = null,
)

/** Runflix Live TV catalog + stream resolve. */
class LiveTvApi(
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

    suspend fun channels(limit: Int = 80): List<LiveTvChannel> {
        val root = getJson("v1/livetv/channels", mapOf("limit" to "$limit"))
        return parseList(root)
    }

    suspend fun search(q: String): List<LiveTvChannel> {
        val root = getJson("v1/livetv/search", mapOf("q" to q))
        return parseList(root)
    }

    suspend fun sports(): List<LiveTvChannel> {
        val fromSearch = runCatching { search("sport") }.getOrDefault(emptyList())
        if (fromSearch.isNotEmpty()) return fromSearch
        return channels().filter {
            val n = (it.name + " " + (it.category ?: "")).lowercase()
            n.contains("sport") || n.contains("football") || n.contains("soccer")
        }
    }

    suspend fun resolveStream(channelId: String): String {
        val path = if (channelId.startsWith("live_")) {
            "v1/livetv/channel/$channelId"
        } else {
            "v1/livetv/channel/$channelId"
        }
        val root = getJson(path)
        val url = root.optString("streamUrl").ifBlank { root.optString("url") }
        if (!url.startsWith("http")) throw IllegalStateException("No stream for channel")
        return url
    }

    private fun parseList(root: JSONObject): List<LiveTvChannel> {
        val arr = root.optJSONArray("livetv")
            ?: root.optJSONArray("results")
            ?: root.optJSONArray("channels")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        val out = ArrayList<LiveTvChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out += LiveTvChannel(
                id = id,
                name = o.optString("name").ifBlank { id },
                logo = o.optString("logo").takeIf { it.startsWith("http") },
                category = o.optString("category").takeIf { it.isNotBlank() },
                isLive = o.optBoolean("isLive", true),
                streamUrl = o.optString("streamUrl").takeIf { it.startsWith("http") },
            )
        }
        return out
    }
}
