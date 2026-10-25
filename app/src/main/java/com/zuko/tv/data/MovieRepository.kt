package com.zuko.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MovieRepository(
    private val api: TmdbApi,
    private val nollywood: NollywoodApi,
) {
    val isConfigured: Boolean get() = api.isConfigured

    suspend fun homeRows(): List<HomeRow> = coroutineScope {
        val nollyJob = async {
            runCatching { nollywood.trending(18) }.getOrDefault(emptyList())
        }
        val jobs = listOf(
            "Trending this week" to async { api.trending() },
            "Popular" to async { api.popular() },
            "Top rated" to async { api.topRated() },
            "Now playing" to async { api.nowPlaying() },
            "Coming soon" to async { api.upcoming() },
        )
        val results = jobs.map { (title, job) -> title to runCatching { job.await() } }

        val rows = mutableListOf<HomeRow>()
        val nolly = nollyJob.await().filter { !it.posterPath.isNullOrBlank() }
        if (nolly.isNotEmpty()) {
            rows += HomeRow("🇳🇬 Nollywood · Nigerian movies", nolly)
        }
        results.forEach { (title, result) ->
            val movies = result.getOrNull()?.results.orEmpty().filter { it.posterPath != null }
            if (movies.isNotEmpty()) rows += HomeRow(title, movies)
        }
        if (rows.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
                ?: TmdbException("No movies available.")
        }
        rows
    }

    suspend fun search(query: String): List<MovieSummary> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val tmdbJob = async {
            runCatching { api.search(q).results.filter { it.posterPath != null } }
                .getOrDefault(emptyList())
        }
        val nollyJob = async {
            runCatching { nollywood.search(q) }.getOrDefault(emptyList())
        }
        val nollyFirst = q.contains("nolly", true) ||
            q.contains("naija", true) ||
            q.contains("nigeria", true) ||
            q.contains("yoruba", true) ||
            q.contains("nollywood", true)
        val tmdb = tmdbJob.await()
        val nolly = nollyJob.await()
        if (nollyFirst) (nolly + tmdb).distinctBy { it.title.lowercase() }
        else (tmdb + nolly).distinctBy { it.title.lowercase() }
    }

    suspend fun details(id: Int): MovieDetails {
        if (id < 0) {
            return nollywood.detailsFromCache(id)
                ?: throw TmdbException("Nollywood title expired from cache — search again.")
        }
        return api.details(id)
    }

    suspend fun popularTv() = api.popularTv().results.filter { it.posterPath != null }
    suspend fun searchTv(query: String) = api.searchTv(query).results.filter { it.posterPath != null }
    suspend fun tvDetails(id: Int) = api.tvDetails(id)
    suspend fun tvSeason(id: Int, season: Int) = api.tvSeason(id, season)
    suspend fun genres() = api.genres().genres
    suspend fun genreMovies(genreId: Int) = api.discoverGenre(genreId).results.filter { it.posterPath != null }
}
