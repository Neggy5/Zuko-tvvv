package com.zuko.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zuko.tv.R
import com.zuko.tv.data.HomeRow
import com.zuko.tv.data.MovieSummary
import com.zuko.tv.data.Tmdb
import com.zuko.tv.data.toYear
import com.zuko.tv.ui.components.CenterMessage
import com.zuko.tv.ui.components.PosterCard
import com.zuko.tv.ui.components.RatingChip
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.ZukoType

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenMovie: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        HomeUiState.Loading -> CenterMessage(title = "Loading movies")
        HomeUiState.MissingKey -> CenterMessage(
            title = "Add your TMDB key",
            body = "ZUKO TV gets posters and details from TMDB. Create a free key at themoviedb.org/settings/api, " +
                "then add it as TMDB_API_KEY in local.properties or as a GitHub secret and rebuild.",
        )
        is HomeUiState.Error -> CenterMessage(
            title = "Couldn't load movies",
            body = s.message,
            actionLabel = "Try again",
            onAction = viewModel::load,
        )
        is HomeUiState.Ready -> HomeContent(s.rows, viewModel.focusMemory, onOpenMovie)
    }
}

@Composable
private fun HomeContent(
    rows: List<HomeRow>,
    memory: FocusMemory,
    onOpenMovie: (Int) -> Unit,
) {
    val startRow = memory.row.coerceIn(0, rows.lastIndex)
    val startCol = memory.col.coerceIn(0, rows[startRow].movies.lastIndex)

    var featured by remember { mutableStateOf(rows[startRow].movies[startCol]) }
    val restoreFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startRow)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { restoreFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Hero(movie = featured, modifier = Modifier.fillMaxWidth().height(270.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.title }) { rowIndex, row ->
                MovieRow(
                    title = row.title,
                    movies = row.movies,
                    initialIndex = if (rowIndex == startRow) startCol else 0,
                    restoreIndex = if (rowIndex == startRow) startCol else -1,
                    restoreRequester = restoreFocus,
                    onFocused = { col, movie ->
                        memory.row = rowIndex
                        memory.col = col
                        featured = movie
                    },
                    onOpen = onOpenMovie,
                )
            }
            item(key = "credits") {
                Text(
                    text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = ZukoType.small,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MovieRow(
    title: String,
    movies: List<MovieSummary>,
    initialIndex: Int,
    restoreIndex: Int,
    restoreRequester: FocusRequester,
    onFocused: (Int, MovieSummary) -> Unit,
    onOpen: (Int) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = ZukoType.rowTitle,
            modifier = Modifier.padding(start = 48.dp, top = 14.dp, bottom = 2.dp),
        )
        val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                PosterCard(
                    movie = movie,
                    onClick = { onOpen(movie.id) },
                    onFocus = { onFocused(index, movie) },
                    modifier = if (index == restoreIndex) Modifier.focusRequester(restoreRequester) else Modifier,
                )
            }
        }
    }
}

/** Backdrop banner that follows whichever poster has focus. */
@Composable
private fun Hero(movie: MovieSummary, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Ink)) {
        Crossfade(
            targetState = movie.backdropPath ?: movie.posterPath,
            animationSpec = tween(350),
            label = "heroBackdrop",
        ) { path ->
            if (path != null) {
                AsyncImage(
                    model = Tmdb.backdrop(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Ink, Ink.copy(alpha = 0.82f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.zuko_mark),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "ZUKO TV", style = ZukoType.brand)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.55f)
                .padding(start = 48.dp, bottom = 16.dp),
        ) {
            Text(
                text = movie.title,
                style = ZukoType.heroTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                movie.releaseDate.toYear()?.let { Text(text = it, style = ZukoType.meta) }
                RatingChip(rating = movie.voteAverage)
            }
            Text(
                text = movie.overview,
                style = ZukoType.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
