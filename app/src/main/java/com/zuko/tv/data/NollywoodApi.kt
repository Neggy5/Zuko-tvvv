package com.zuko.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Nigerian / Nollywood catalog:
 * - FilmFlux public trending (legal YouTube-backed Nollywood)
 * - OmegaTech MovieBox-pro search (streamable titles)
 */
class NollywoodApi(private val client: OkHttpClient) {

    data class NollyItem(
        val id: Int,
        val title: String,
        val overview: String,
        val posterUrl: String?,
        val year: String?,
        val channel: String?,
        val youtubeId: String?,
        val source: String,
    )

    private val byId = ConcurrentHashMap<Int, NollyItem>()

    fun getCached(id: Int): NollyItem? = byId[id]

    suspend fun trending(limit: Int = 16): List<MovieSummary> = withContext(Dispatchers.IO) {
        val list = mutableListOf<NollyItem>()
        list += fetchFilmFlux(limit)
        if (list.size < 8) {
            list += fetchOmega("Nollywood", limit)
            list += fetchOmega("Nigerian movie", 8)
        }
        list.distinctBy { it.title.lowercase().trim() }
            .also { items -> items.forEach { byId[it.id] = it } }
            .map { it.toSummary() }
    }

    suspend fun search(query: String, limit: Int = 20): List<MovieSummary> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val list = mutableListOf<NollyItem>()
        list += fetchOmega(q, limit)
        if (q.length >= 3) {
            list += fetchOmega("$q Nollywood", 8)
            list += fetchOmega("$q Nigeria", 8)
        }
        list.distinctBy { it.title.lowercase().trim() }
            .take(limit)
            .also { items -> items.forEach { byId[it.id] = it } }
            .map { it.toSummary() }
    }

    fun detailsFromCache(id: Int): MovieDetails? {
        val item = byId[id] ?: return null
        return MovieDetails(
            id = item.id,
            title = item.title,
            imdbId = null,
            tagline = item.channel?.let { "Nollywood · $it" } ?: "Nollywood",
            overview = item.overview.ifBlank {
                "Nigerian / Nollywood title${item.channel?.let { " from $it" } ?: ""}."
            },
            posterPath = item.posterUrl,
            backdropPath = item.posterUrl,
            releaseDate = item.year?.let { "$it-01-01" },
            runtime = null,
            voteAverage = 0.0,
            genres = listOf(Genre(id = -1, name = "Nollywood")),
            credits = Credits(),
            similar = MovieList(),
        )
    }

    private fun fetchFilmFlux(limit: Int): List<NollyItem> {
        return try {
            val req = Request.Builder()
                .url("https://filmflux.app/api/public/trending?limit=$limit")
                .header("Accept", "application/json")
                .header("User-Agent", "ZUKO-TV/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return emptyList()
                val root = JSONObject(res.body?.string().orEmpty())
                val arr = root.optJSONArray("movies") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val title = o.optString("title")
                        if (title.isBlank()) continue
                        val yt = o.optString("youtube_id").takeIf { it.isNotBlank() }
                        val id = syntheticId("ff", yt ?: title)
                        add(
                            NollyItem(
                                id = id,
                                title = title,
                                overview = o.optString("description"),
                                posterUrl = o.optString("thumbnail").takeIf { it.startsWith("http") },
                                year = null,
                                channel = o.optString("channel").takeIf { it.isNotBlank() },
                                youtubeId = yt,
                                source = "FilmFlux",
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchOmega(keyword: String, limit: Int): List<NollyItem> {
        return try {
            val url =
                "https://api.omegatech.app/api/movie/MovieBox-pro?action=search&keyword=" +
                    java.net.URLEncoder.encode(keyword, Charsets.UTF_8.name())
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ZUKO-TV/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return emptyList()
                val root = JSONObject(res.body?.string().orEmpty())
                val data = root.optJSONObject("data") ?: root
                val arr = data.optJSONArray("results") ?: JSONArray()
                buildList {
                    for (i in 0 until minOf(arr.length(), limit)) {
                        val o = arr.optJSONObject(i) ?: continue
                        val title = o.optString("title")
                        if (title.isBlank()) continue
                        val country = o.optString("countryName")
                        val genre = o.optString("genre")
                        val subjectId = o.optString("subjectId").ifBlank { title }
                        val cover = o.optJSONObject("cover")?.optString("url")
                            ?: o.optString("cover")
                        val year = o.optString("releaseDate").take(4).takeIf { it.length == 4 }
                        val id = syntheticId("mb", subjectId)
                        add(
                            NollyItem(
                                id = id,
                                title = title,
                                overview = o.optString("description").ifBlank {
                                    listOf(genre, country).filter { it.isNotBlank() }.joinToString(" · ")
                                },
                                posterUrl = cover?.takeIf { it.startsWith("http") },
                                year = year,
                                channel = country.takeIf { it.isNotBlank() },
                                youtubeId = null,
                                source = "MovieBox",
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun syntheticId(prefix: String, key: String): Int {
        val h = abs("$prefix:$key".hashCode())
        val id = -(h % 1_000_000_000 + 1)
        return if (id == 0) -1 else id
    }

    private fun NollyItem.toSummary() = MovieSummary(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterUrl,
        backdropPath = posterUrl,
        releaseDate = year?.let { "$it-01-01" },
        voteAverage = 0.0,
    )
}
