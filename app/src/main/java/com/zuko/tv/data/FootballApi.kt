package com.zuko.tv.data

import com.zuko.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class FootballMatch(
    val id: String,
    val home: String,
    val away: String,
    val homeScore: String?,
    val awayScore: String?,
    val status: String,
    val league: String?,
    val kickoff: String?,
    val isLive: Boolean,
    /** Direct HLS URL from the list response when present. */
    val streamUrl: String? = null,
    val streamAvailable: Boolean = false,
)

data class FootballStream(
    val url: String,
    val quality: String?,
    val type: String?,
)

/**
 * Runflix / DaraTech football API.
 * Live list often includes [streamUrl] already; otherwise:
 * GET /v1/football/match/{id}/stream
 */
class FootballApi(
    private val client: OkHttpClient,
    private val baseUrl: String = BuildConfig.RUNFLIX_API_BASE.trimEnd('/'),
    private val apiKey: String = BuildConfig.RUNFLIX_API_KEY.ifBlank { "zukoxmdapi" },
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

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
                        ?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${resp.code}"
                    throw IllegalStateException(msg)
                }
                JSONObject(body)
            }
        }

    suspend fun live(timezone: String = "Africa/Lagos"): List<FootballMatch> =
        parseMatches(getJson("v1/football/live", mapOf("timezone" to timezone, "limit" to "100")))

    suspend fun today(timezone: String = "Africa/Lagos"): List<FootballMatch> =
        parseMatches(getJson("v1/football/today", mapOf("timezone" to timezone, "limit" to "100")))

    /** Prefer list streamUrl; fall back to /match/{id}/stream. */
    suspend fun resolvePlayUrl(match: FootballMatch): String {
        match.streamUrl?.takeIf { it.startsWith("http") }?.let { return it }
        val streams = streams(match.id)
        return streams.firstOrNull()?.url
            ?: throw IllegalStateException("No stream for ${match.home} vs ${match.away}")
    }

    suspend fun streams(matchId: String): List<FootballStream> {
        val json = getJson("v1/football/match/$matchId/stream")
        return extractStreams(json)
    }

    private fun parseMatches(root: JSONObject): List<FootballMatch> {
        val arr = root.optJSONArray("matches")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("results")
            ?: JSONArray()
        val out = ArrayList<FootballMatch>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { o.optString("matchId") }
            if (id.isBlank()) continue
            val home = o.optString("homeTeam").ifBlank {
                o.optJSONObject("home")?.optString("name").orEmpty()
            }.ifBlank { "TBD" }
            val away = o.optString("awayTeam").ifBlank {
                o.optJSONObject("away")?.optString("name").orEmpty()
            }.ifBlank { "TBD" }
            val statusRaw = o.opt("status")
            val status = when (statusRaw) {
                is JSONObject -> statusRaw.optString("display").ifBlank {
                    statusRaw.optString("status")
                }
                else -> o.optString("status")
            }.ifBlank { o.optString("time").ifBlank { "—" } }
            val isLive = o.optBoolean("streamAvailable") ||
                status.equals("live", true) ||
                status.contains("'", ignoreCase = false) ||
                status.equals("1H", true) ||
                status.equals("2H", true) ||
                status.equals("HT", true) ||
                status.equals("inplay", true)
            val league = when (val l = o.opt("league")) {
                is JSONObject -> l.optString("name")
                is String -> l
                else -> o.optString("competition")
            }.takeIf { it.isNotBlank() }
            val streamUrl = o.optString("streamUrl").takeIf { it.startsWith("http") }
                ?: o.optString("url").takeIf { it.startsWith("http") }
            out += FootballMatch(
                id = id,
                home = home,
                away = away,
                homeScore = o.optString("homeScore").takeIf { it.isNotBlank() && it != "null" },
                awayScore = o.optString("awayScore").takeIf { it.isNotBlank() && it != "null" },
                status = status,
                league = league,
                kickoff = o.optString("kickoff").takeIf { it.isNotBlank() }
                    ?: o.optString("time").takeIf { it.isNotBlank() },
                isLive = isLive,
                streamUrl = streamUrl,
                streamAvailable = o.optBoolean("streamAvailable") || streamUrl != null,
            )
        }
        return out
    }

    private fun extractStreams(root: JSONObject): List<FootballStream> {
        val out = mutableListOf<FootballStream>()
        fun add(url: String?, quality: String? = null, type: String? = null) {
            val u = url?.trim().orEmpty()
            if (u.startsWith("http")) out += FootballStream(u, quality, type)
        }
        add(root.optString("url").takeIf { it.isNotBlank() }, type = root.optString("type"))
        add(root.optString("streamUrl").takeIf { it.isNotBlank() }, type = root.optString("type"))
        val qualities = root.optJSONArray("qualities")
        if (qualities != null) {
            for (i in 0 until qualities.length()) {
                val q = qualities.optJSONObject(i) ?: continue
                add(
                    q.optString("url").takeIf { it.isNotBlank() },
                    quality = q.optString("quality").ifBlank { q.optString("title") },
                    type = q.optString("format").ifBlank { q.optString("kind") },
                )
            }
        }
        val data = root.optJSONObject("data")
        if (data != null) {
            add(data.optString("url").takeIf { it.isNotBlank() })
            add(data.optString("streamUrl").takeIf { it.isNotBlank() })
        }
        return out.distinctBy { it.url }
    }
}
