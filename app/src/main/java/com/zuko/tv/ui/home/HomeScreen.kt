package com.zuko.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zuko.tv.R
import com.zuko.tv.data.HomeRow
import com.zuko.tv.data.MovieSummary
import com.zuko.tv.data.Tmdb
import com.zuko.tv.data.WatchItem
import com.zuko.tv.data.toYear
import com.zuko.tv.ui.components.CenterMessage
import com.zuko.tv.ui.components.PosterCard
import com.zuko.tv.ui.components.RatingChip
import com.zuko.tv.ui.theme.Azure
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoBrush
import com.zuko.tv.ui.theme.ZukoType

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenMovie: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

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
        is HomeUiState.Ready -> {
            val isSearching = query.isNotBlank()
            val continueItems by viewModel.continueWatching.collectAsState()
            val allRows = remember(s.rows, continueItems) { buildRows(s.rows, continueItems) }
            val progress = remember(continueItems) { continueItems.associate { it.id to it.progress } }
            Column(modifier = Modifier.fillMaxSize().background(Ink)) {
                SearchBar(
                    query = query,
                    onQuery = viewModel::setQuery,
                    onClear = viewModel::clearQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 12.dp),
                )
                if (isSearching) {
                    SearchResultsPane(
                        query = query,
                        results = searchResults,
                        searching = searching,
                        error = searchError,
                        onOpenMovie = onOpenMovie,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HomeContent(allRows, viewModel.focusMemory, onOpenMovie, progress)
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .height(48.dp)
            .background(Panel, shape)
            .then(
                if (focused) Modifier.border(2.dp, ZukoBrush, shape)
                else Modifier.border(1.dp, Color(0x33FFFFFF), shape)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "⌕", style = ZukoType.meta, color = if (focused) Azure else TextLo)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = ZukoType.meta.copy(color = TextHi),
            cursorBrush = SolidColor(Azure),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search movies…", style = ZukoType.meta.copy(color = TextLo))
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text("Clear", style = ZukoType.small.copy(color = Azure))
            }
        }
    }
}

@Composable
private fun SearchResultsPane(
    query: String,
    results: List<MovieSummary>,
    searching: Boolean,
    error: String?,
    onOpenMovie: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        searching && results.isEmpty() -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Azure)
            }
        }
        error != null && results.isEmpty() -> {
            CenterMessage(title = "No results", body = error)
        }
        results.isEmpty() -> {
            CenterMessage(title = "Search", body = "Type a movie name to search TMDB.")
        }
        else -> {
            Column(modifier = modifier) {
                Text(
                    text = "Results for \"$query\" (${results.size})",
                    style = ZukoType.rowTitle,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(results, key = { _, m -> m.id }) { _, movie ->
                        PosterCard(movie = movie, onClick = { onOpenMovie(movie.id) })
                    }
                }
            }
        }
    }
}

private const val CONTINUE_TITLE = "Continue watching"

private fun buildRows(rows: List<HomeRow>, continueItems: List<WatchItem>): List<HomeRow> {
    if (continueItems.isEmpty()) return rows
    return listOf(HomeRow(CONTINUE_TITLE, continueItems.map { it.toSummary() })) +
        rows.filterNot { it.title == CONTINUE_TITLE }
}

@Composable
private fun HomeContent(
    rows: List<HomeRow>,
    memory: FocusMemory,
    onOpenMovie: (Int) -> Unit,
    progress: Map<Int, Float>,
) {
    if (rows.isEmpty()) {
        CenterMessage(title = "No movies")
        return
    }
    val startRow = memory.row.coerceIn(0, rows.lastIndex)
    val startCol = memory.col.coerceIn(0, (rows[startRow].movies.lastIndex).coerceAtLeast(0))

    var featured by remember(rows) {
        mutableStateOf(
            rows.getOrNull(startRow)?.movies?.getOrNull(startCol)
                ?: rows.first().movies.first()
        )
    }
    val restoreFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startRow)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { restoreFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Hero(movie = featured, modifier = Modifier.fillMaxWidth().height(240.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.title }) { rowIndex, row ->
                MovieRow(
                    title = row.title,
                    movies = row.movies,
                    progress = if (row.title == CONTINUE_TITLE) progress else emptyMap(),
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
    progress: Map<Int, Float>,
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
        val rowState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (movies.size - 1).coerceAtLeast(0))
        )
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
                    progress = progress[movie.id],
                    modifier = if (index == restoreIndex) Modifier.focusRequester(restoreRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun Hero(movie: MovieSummary, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val path = movie.backdropPath ?: movie.posterPath
        if (path != null) {
            AsyncImage(
                model = Tmdb.backdrop(path),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Ink.copy(alpha = 0.55f), Ink)
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(R.drawable.zuko_mark),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = movie.title,
                    style = ZukoType.heroTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    movie.releaseDate.toYear()?.let { Text(text = it, style = ZukoType.meta) }
                    RatingChip(rating = movie.voteAverage)
                }
            }
        }
    }
}
