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
        val safeTitle = title.trim().ifBlank { "Unknown" }
        val errors = mutableListOf<String>()

        if (runflix.isConfigured) {
            try {
                val url = runflix.resolvePlayUrl(safeTitle, year)
                if (!url.isNullOrBlank()) return url
                errors += "Runflix: no stream for this title"
            } catch (e: Exception) {
                errors += "Runflix: ${e.message ?: e.javaClass.simpleName}"
            }
        } else {
            errors += "Runflix: not configured"
        }

        if (publicApi.isConfigured) {
            try {
                val response = publicApi.publicStream(safeTitle, year)
                val url = response.bestUrl?.takeIf {
                    it.startsWith("https://") || it.startsWith("http://")
                }
                if (url != null) return url
                errors += "Public archive: no stream"
            } catch (e: Exception) {
                errors += "Public archive: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        throw StreamException(
            buildString {
                append("No playable stream for \"$safeTitle\".")
                if (errors.isNotEmpty()) {
                    append('\n')
                    append(errors.joinToString("\n"))
                }
            }
        )
    }
}
