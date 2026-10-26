package com.zuko.tv.data

import com.zuko.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MangaItem(
    val id: String,
    val title: String,
    val cover: String?,
    val description: String?,
    val source: String, // "manga" | "mangadex"
)

data class MangaChapter(
    val id: String,
    val title: String,
    val chapter: String?,
    val volume: String?,
    val language: String?,
)

data class MangaPage(
    val index: Int,
    val url: String,
)

/** Runflix manga + MangaDex proxy. */
class MangaApi(
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

    suspend fun mangadexTrending(limit: Int = 30): List<MangaItem> =
        parseMangadex(getJson("v1/mangadex/trending", mapOf("limit" to "$limit")))

    suspend fun mangadexSearch(q: String, limit: Int = 30): List<MangaItem> =
        parseMangadex(getJson("v1/mangadex/search", mapOf("q" to q, "limit" to "$limit")))

    suspend fun mangadexChapters(mangaId: String, limit: Int = 50): List<MangaChapter> {
        val root = getJson(
            "v1/mangadex/chapter",
            mapOf("manga" to mangaId, "limit" to "$limit", "translatedLanguage[]" to "en"),
        )
        val arr = root.optJSONArray("chapters")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("results")
            ?: JSONArray()
        val out = ArrayList<MangaChapter>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val ch = o.optString("chapter").takeIf { it.isNotBlank() }
            val title = o.optString("title").ifBlank {
                listOfNotNull(ch?.let { "Ch. $it" }, o.optString("volume").takeIf { it.isNotBlank() }?.let { "Vol $it" })
                    .joinToString(" · ")
                    .ifBlank { "Chapter" }
            }
            out += MangaChapter(
                id = id,
                title = title,
                chapter = ch,
                volume = o.optString("volume").takeIf { it.isNotBlank() },
                language = o.optString("language").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /** Best-effort page list for a chapter. */
    suspend fun mangadexPages(chapterId: String): List<MangaPage> {
        val root = getJson("v1/mangadex/chapter/$chapterId")
        val out = mutableListOf<MangaPage>()
        val pages = root.optJSONArray("pages")
            ?: root.optJSONObject("data")?.optJSONArray("pages")
            ?: root.optJSONArray("images")
            ?: JSONArray()
        for (i in 0 until pages.length()) {
            val el = pages.opt(i)
            when (el) {
                is String -> if (el.startsWith("http")) out += MangaPage(i, el)
                is JSONObject -> {
                    val u = el.optString("url").ifBlank { el.optString("src") }
                    if (u.startsWith("http")) out += MangaPage(i, u)
                }
            }
        }
        // Some responses use baseUrl + hash + data[]
        if (out.isEmpty()) {
            val base = root.optString("baseUrl").ifBlank {
                root.optJSONObject("data")?.optString("baseUrl").orEmpty()
            }
            val hash = root.optString("hash").ifBlank {
                root.optJSONObject("data")?.optString("hash").orEmpty()
            }
            val data = root.optJSONArray("data")
                ?: root.optJSONObject("chapter")?.optJSONArray("data")
                ?: root.optJSONObject("data")?.optJSONArray("data")
            if (base.isNotBlank() && data != null) {
                for (i in 0 until data.length()) {
                    val file = data.optString(i)
                    if (file.isNotBlank()) {
                        val u = if (hash.isNotBlank()) "$base/data/$hash/$file" else "$base/$file"
                        out += MangaPage(i, u)
                    }
                }
            }
        }
        return out
    }

    private fun parseMangadex(root: JSONObject): List<MangaItem> {
        val arr = root.optJSONArray("results")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("items")
            ?: JSONArray()
        val out = ArrayList<MangaItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            var title = o.optString("title")
            if (title.isBlank()) {
                val alt = o.optJSONArray("altTitles")
                if (alt != null && alt.length() > 0) {
                    title = alt.optJSONObject(0)?.optString("title").orEmpty()
                }
            }
            val cover = o.optString("cover").takeIf { it.startsWith("http") }
                ?: o.optString("coverUrl").takeIf { it.startsWith("http") }
                ?: o.optJSONObject("coverArt")?.optString("url")?.takeIf { it.startsWith("http") }
            out += MangaItem(
                id = id,
                title = title.ifBlank { "Manga" },
                cover = cover,
                description = o.optString("description").takeIf { it.isNotBlank() },
                source = "mangadex",
            )
        }
        return out
    }
}
