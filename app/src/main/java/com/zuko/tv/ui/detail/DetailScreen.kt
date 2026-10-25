package com.zuko.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zuko.tv.data.MovieDetails
import com.zuko.tv.data.MovieSummary
import com.zuko.tv.data.Tmdb
import com.zuko.tv.data.toRuntimeLabel
import com.zuko.tv.data.toYear
import com.zuko.tv.ui.components.CenterMessage
import com.zuko.tv.ui.components.PosterCard
import com.zuko.tv.ui.components.RatingChip
import com.zuko.tv.ui.components.ZukoButton
import com.zuko.tv.ui.theme.BodySoft
import com.zuko.tv.ui.theme.Err
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Ok
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onOpenMovie: (Int) -> Unit,
    onPlayMovie: (MovieDetails) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val download by viewModel.download.collectAsState()

    when (val s = state) {
        DetailUiState.Loading -> CenterMessage(title = "Loading")
        is DetailUiState.Error -> CenterMessage(
            title = "Couldn't load this title",
            body = s.message,
            actionLabel = "Try again",
            onAction = viewModel::load,
        )
        is DetailUiState.Ready -> DetailContent(
            movie = s.movie,
            download = download,
            onOpenMovie = onOpenMovie,
            onPlayMovie = onPlayMovie,
            onDownload = viewModel::downloadMovie,
        )
    }
}

@Composable
private fun DetailContent(
    movie: MovieDetails,
    download: DownloadUiState,
    onOpenMovie: (Int) -> Unit,
    onPlayMovie: (MovieDetails) -> Unit,
    onDownload: () -> Unit,
) {
    val similar = remember(movie.id) { movie.similar.results.filter { it.posterPath != null }.take(20) }
    val firstSimilarFocus = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        if (similar.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstSimilarFocus.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        val backdrop = movie.backdropPath ?: movie.posterPath
        if (backdrop != null) {
            AsyncImage(
                model = Tmdb.backdrop(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Ink, Ink.copy(alpha = 0.88f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.6f), Ink))),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .padding(start = 48.dp),
            ) {
                Text(
                    text = movie.title,
                    style = ZukoType.screenTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movie.tagline?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = ZukoType.body, modifier = Modifier.padding(top = 4.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    movie.releaseDate.toYear()?.let { Text(text = it, style = ZukoType.meta) }
                    movie.runtime.toRuntimeLabel()?.let { Text(text = it, style = ZukoType.meta) }
                    RatingChip(rating = movie.voteAverage)
                }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        text = movie.genres.joinToString(", ") { it.name },
                        style = ZukoType.body,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    ZukoButton(
                        text = "PLAY",
                        onClick = { onPlayMovie(movie) },
                    )
                    ZukoButton(
                        text = when (download) {
                            DownloadUiState.Resolving -> "GETTING LINK…"
                            is DownloadUiState.Started -> "DOWNLOADING"
                            else -> "DOWNLOAD"
                        },
                        onClick = {
                            if (download !is DownloadUiState.Resolving) onDownload()
                        },
                    )
                }
                when (download) {
                    is DownloadUiState.Started -> Text(
                        text = download.message,
                        style = ZukoType.small.copy(color = Ok),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is DownloadUiState.Failed -> Text(
                        text = download.message,
                        style = ZukoType.small.copy(color = Err),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    DownloadUiState.Resolving -> Text(
                        text = "Resolving stream for download…",
                        style = ZukoType.small.copy(color = TextLo),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> Unit
                }
                Text(
                    text = movie.overview,
                    style = ZukoType.body.copy(color = BodySoft),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val cast = movie.credits.cast.take(5).map { it.name }.filter { it.isNotBlank() }
                if (cast.isNotEmpty()) {
                    Text(
                        text = "Starring " + cast.joinToString(", "),
                        style = ZukoType.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            if (similar.isNotEmpty()) {
                Text(
                    text = "More like this",
                    style = ZukoType.rowTitle,
                    modifier = Modifier.padding(start = 48.dp, top = 16.dp),
                )
                SimilarRow(similar, firstSimilarFocus, onOpenMovie)
            }
        }
    }
}

@Composable
private fun SimilarRow(
    movies: List<MovieSummary>,
    firstFocus: FocusRequester,
    onOpenMovie: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
            PosterCard(
                movie = movie,
                onClick = { onOpenMovie(movie.id) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}
