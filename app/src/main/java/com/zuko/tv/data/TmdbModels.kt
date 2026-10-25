package com.zuko.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieSummary(
    val id: Int,
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class MovieList(
    val results: List<MovieSummary> = emptyList(),
)

@Serializable
data class Genre(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class CastMember(
    val name: String = "",
    val character: String? = null,
)

@Serializable
data class Credits(
    val cast: List<CastMember> = emptyList(),
)

@Serializable
data class MovieDetails(
    val id: Int,
    val title: String = "",
    @SerialName("imdb_id") val imdbId: String? = null,
    val tagline: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val genres: List<Genre> = emptyList(),
    val credits: Credits = Credits(),
    val similar: MovieList = MovieList(),
)

data class HomeRow(
    val title: String,
    val movies: List<MovieSummary>,
)

object Tmdb {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else "w342"
    fun backdrop(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else "w1280"
}

fun String?.toYear(): String? =
    this?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

fun Int?.toRuntimeLabel(): String? {
    val minutes = this ?: return null
    if (minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Serializable
data class GenreList(val genres: List<Genre> = emptyList())


@Serializable
data class TvSummary(
    val id: Int,
    @SerialName("name") val name: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class TvList(val results: List<TvSummary> = emptyList())

@Serializable
data class TvSeasonSummary(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class TvDetails(
    val id: Int,
    @SerialName("name") val name: String = "",
    val overview: String = "",
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val seasons: List<TvSeasonSummary> = emptyList(),
)

@Serializable
data class TvEpisode(
    val id: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("still_path") val stillPath: String? = null,
)

@Serializable
data class TvSeasonDetails(val episodes: List<TvEpisode> = emptyList())
