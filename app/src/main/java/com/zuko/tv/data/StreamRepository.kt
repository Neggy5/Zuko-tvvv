package com.zuko.tv.data

import okhttp3.OkHttpClient

/**
 * Resolves a playable URL for a title.
 * Order: Runflix Stremio addon (Railway) → optional public STREAM_API_BASE backend.
 */
class StreamRepository(
    client: OkHttpClient,
    streamApiBase: String,
    runflixAddonBase: String,
) {
    private val publicApi = StreamApi(client, streamApiBase)
    private val runflix = RunflixAddonApi(client, runflixAddonBase)

    suspend fun resolveStream(
        tmdbId: Int,
        imdbId: String?,
        title: String = "",
        year: String? = null,
    ): String {
        if (tmdbId <= 0) {
            throw StreamException("This movie has an invalid TMDB ID. Return to the title page and try again.")
        }
        if (title.isBlank()) {
            throw StreamException("The movie title is missing. Return to the title page and try again.")
        }

        val errors = mutableListOf<String>()

        // 1) ZUKO Runflix addon (deployed on Railway)
        if (runflix.isConfigured) {
            try {
                val url = runflix.resolvePlayUrl(title, year)
                if (!url.isNullOrBlank()) return url
                errors += "Runflix: no stream for this title"
            } catch (e: Exception) {
                errors += "Runflix: ${e.message ?: "failed"}"
            }
        }

        // 2) Optional public-domain backend
        if (publicApi.isConfigured) {
            try {
                val response = publicApi.publicStream(title, year)
                val url = response.bestUrl?.takeIf {
                    it.startsWith("https://") || it.startsWith("http://")
                }
                if (url != null) return url
                errors += "Public archive: no stream"
            } catch (e: Exception) {
                errors += "Public archive: ${e.message ?: "failed"}"
            }
        }

        if (errors.isEmpty()) {
            throw StreamException(
                "No stream source is configured. Set RUNFLIX_ADDON_BASE (default Railway) or STREAM_API_BASE."
            )
        }
        throw StreamException(
            "No playable stream for '$title'.\n" + errors.joinToString("\n")
        )
    }
}
