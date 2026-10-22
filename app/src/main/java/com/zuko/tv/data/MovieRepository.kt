package com.zuko.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MovieRepository(private val api: TmdbApi) {
    val isConfigured: Boolean get() = api.isConfigured

    suspend fun homeRows(): List<HomeRow> = coroutineScope {
        val jobs = listOf(
            "Trending this week" to async { api.trending() },
            "Popular" to async { api.popular() },
            "Top rated" to async { api.topRated() },
            "Now playing" to async { api.nowPlaying() },
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

    suspend fun search(query: String): List<MovieSummary> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return api.search(q).results.filter { it.posterPath != null }
    }

    suspend fun details(id: Int): MovieDetails = api.details(id)
}
