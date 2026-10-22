package com.zuko.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class MovieRepository(private val api: TmdbApi) {

    val isConfigured: Boolean get() = api.isConfigured

    /** Loads all home rows in parallel. A failing row is skipped; all failing is an error. */
    suspend fun homeRows(): List<HomeRow> = supervisorScope {
        val jobs = listOf(
            "Trending this week" to async { api.trending() },
            "Popular" to async { api.popular() },
            "Top rated" to async { api.topRated() },
            "In theaters" to async { api.nowPlaying() },
            "Coming soon" to async { api.upcoming() },
        )
        val results = jobs.map { (title, job) -> title to runCatching { job.await() } }

        val rows = results.mapNotNull { (title, result) ->
            val movies = result.getOrNull()?.results.orEmpty().filter { it.posterPath != null }
            if (movies.isEmpty()) null else HomeRow(title, movies)
        }
        if (rows.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
                ?: TmdbException("TMDB returned no movies.")
        }
        rows
    }

    suspend fun details(id: Int): MovieDetails = api.details(id)
}
