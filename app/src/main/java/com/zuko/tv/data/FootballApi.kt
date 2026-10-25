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
)

data class FootballStream(
    val url: String,
    val quality: String?,
    val type: String?,
)

/**
 * Runflix / DaraTech football API.
 * Docs: https://apimovie.runflix.name.ng/docs
 * Free key: https://apimovie.runflix.name.ng/request-key
 *
 * Endpoints used:
 *  GET /v1/football/live
 *  GET /v1/football/today
 *  GET /v1/football/match/{id}/stream
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

    suspend fun streams(matchId: String): List<FootballStream> {
        val json = getJson("v1/football/match/$matchId/stream")
        return extractStreams(json)
    }

    private fun parseMatches(root: JSONObject): List<FootballMatch> {
        val arr = findArray(root, listOf("data", "matches", "results", "items"))
            ?: JSONArray()
        val out = ArrayList<FootballMatch>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = firstString(o, "id", "matchId", "match_id") ?: continue
            val home = teamName(o, "home", "homeTeam", "home_team")
            val away = teamName(o, "away", "awayTeam", "away_team")
            val statusObj = o.optJSONObject("status")
            val status = statusObj?.optString("display")?.takeIf { it.isNotBlank() }
                ?: statusObj?.optString("status")
                ?: o.optString("status").ifBlank { "—" }
            val isLive = statusObj?.optBoolean("is_live") == true
                || status.contains("'", ignoreCase = true)
                || status.equals("live", true)
                || status.equals("inplay", true)
                || status.equals("1H", true)
                || status.equals("2H", true)
                || status.equals("HT", true)
            val league = o.optJSONObject("league")?.optString("name")
                ?: o.optString("league").takeIf { it.isNotBlank() }
                ?: o.optString("competition").takeIf { it.isNotBlank() }
            val kickoff = firstString(o, "kickoff", "start", "date", "kickoffUtc")
            out += FootballMatch(
                id = id,
                home = home,
                away = away,
                homeScore = score(o, "home"),
                awayScore = score(o, "away"),
                status = status,
                league = league,
                kickoff = kickoff,
                isLive = isLive,
            )
        }
        return out
    }

    private fun teamName(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            when (v) {
                is JSONObject -> {
                    val n = v.optString("name").ifBlank { v.optString("title") }
                    if (n.isNotBlank()) return n
                }
                is String -> if (v.isNotBlank()) return v
            }
        }
        return "TBD"
    }

    private fun score(o: JSONObject, side: String): String? {
        val nested = o.optJSONObject(side)
        if (nested != null) {
            val s = nested.opt("score") ?: nested.opt("goals")
            if (s != null && s.toString().isNotBlank() && s.toString() != "null") return s.toString()
        }
        val key = if (side == "home") listOf("homeScore", "home_score", "scoreHome")
        else listOf("awayScore", "away_score", "scoreAway")
        for (k in key) {
            val s = o.opt(k)
            if (s != null && s.toString().isNotBlank() && s.toString() != "null") return s.toString()
        }
        val scores = o.optJSONObject("score")
        if (scores != null) {
            val s = scores.opt(side) ?: scores.opt(if (side == "home") "home" else "away")
            if (s != null && s.toString() != "null") return s.toString()
        }
        return null
    }

    private fun extractStreams(root: JSONObject): List<FootballStream> {
        val out = mutableListOf<FootballStream>()
        fun add(url: String?, quality: String? = null, type: String? = null) {
            val u = url?.trim().orEmpty()
            if (u.startsWith("http")) out += FootballStream(u, quality, type)
        }
        // common shapes
        add(root.optString("url").takeIf { it.isNotBlank() })
        add(root.optString("stream").takeIf { it.isNotBlank() })
        add(root.optString("streamUrl").takeIf { it.isNotBlank() })
        val data = root.opt("data")
        when (data) {
            is JSONObject -> {
                add(data.optString("url").takeIf { it.isNotBlank() })
                add(data.optString("streamUrl").takeIf { it.isNotBlank() })
                val streams = data.opt("streams") ?: data.opt("sources")
                collectStreamArray(streams, out)
            }
            is JSONArray -> collectStreamArray(data, out)
            is String -> add(data)
        }
        collectStreamArray(root.opt("streams") ?: root.opt("sources"), out)
        return out.distinctBy { it.url }
    }

    private fun collectStreamArray(node: Any?, out: MutableList<FootballStream>) {
        val arr = node as? JSONArray ?: return
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            when (item) {
                is String -> if (item.startsWith("http")) out += FootballStream(item, null, null)
                is JSONObject -> {
                    val url = firstString(item, "url", "stream", "streamUrl", "src", "link")
                    if (url != null) {
                        out += FootballStream(
                            url = url,
                            quality = firstString(item, "quality", "label", "name"),
                            type = firstString(item, "type", "format"),
                        )
                    }
                }
            }
        }
    }

    private fun findArray(root: JSONObject, keys: List<String>): JSONArray? {
        for (k in keys) {
            when (val v = root.opt(k)) {
                is JSONArray -> return v
                is JSONObject -> {
                    for (inner in keys) {
                        val a = v.optJSONArray(inner)
                        if (a != null) return a
                    }
                }
            }
        }
        // sometimes top-level is already list under "response"
        root.optJSONArray("response")?.let { return it }
        return null
    }

    private fun firstString(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val s = o.optString(k)
            if (s.isNotBlank() && s != "null") return s
        }
        return null
    }
}
