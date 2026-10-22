package com.zuko.tv.data

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves playable URLs for a title.
 * Order: Runflix Stremio addon (Railway) -> optional public STREAM_API_BASE backend.
 * Results are cached briefly so Play / Download feel instant after the first lookup.
 */
class StreamRepository(
    client: OkHttpClient,
    streamApiBase: String,
    runflixAddonBase: String,
) {
    private val publicApi = StreamApi(client, streamApiBase)
    private val runflix = RunflixAddonApi(client, runflixAddonBase)
    private val movieBox = MovieBoxApi(client)

    private class Cached(val urls: List<String>, val savedAt: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    /** Pings the addon so a sleeping Railway service is awake by the time someone hits Play. */
    suspend fun warmUp() = runflix.warmUp()

    suspend fun resolveStream(
        tmdbId: Int,
        imdbId: String?,
        title: String = "",
        year: String? = null,
    ): String = resolveStreams(tmdbId, imdbId, title, year).first()

    /** All candidate URLs, best first. Never empty (throws [StreamException] instead). */
    suspend fun resolveStreams(
        tmdbId: Int,
        imdbId: String?,
        title: String = "",
        year: String? = null,
        forceRefresh: Boolean = false,
    ): List<String> {
        val safeTitle = title.trim().ifBlank { "Unknown" }
        val key = "$tmdbId|${safeTitle.lowercase(Locale.US)}|${year.orEmpty()}"
        if (!forceRefresh) {
            cache[key]?.let {
                if (System.currentTimeMillis() - it.savedAt < CACHE_TTL_MS) return it.urls
            }
        }

        val errors = mutableListOf<String>()
        var urls: List<String> = emptyList()

        if (runflix.isConfigured) {
            try {
                urls = runflix.resolvePlayUrls(safeTitle, year)
                if (urls.isEmpty()) errors += "Runflix: no stream for this title"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors += "Runflix: ${e.message ?: e.javaClass.simpleName}"
            }
        } else {
            errors += "Runflix: not configured"
        }

        // MovieBox is an additional source; keep Runflix as the first choice.
        if (urls.isEmpty()) {
            try {
                urls = movieBox.resolve(safeTitle, year)
                if (urls.isEmpty()) errors += "MovieBox: no playable stream"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors += "MovieBox: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (urls.isEmpty() && publicApi.isConfigured) {
            try {
                val response = publicApi.publicStream(safeTitle, year)
                val url = response.bestUrl?.takeIf {
                    it.startsWith("https://") || it.startsWith("http://")
                }
                if (url != null) urls = listOf(url) else errors += "Public archive: no stream"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors += "Public archive: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (urls.isEmpty()) {
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
        cache[key] = Cached(urls, System.currentTimeMillis())
        return urls
    }

    private companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}
