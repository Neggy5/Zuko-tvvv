package com.zuko.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.Genre
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.MovieSummary
import com.zuko.tv.ui.components.PosterCard
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GenreViewModel(private val repository: MovieRepository) : ViewModel() {
    private val _genres = MutableStateFlow<List<Genre>>(emptyList()); val genres = _genres.asStateFlow()
    private val _movies = MutableStateFlow<List<MovieSummary>>(emptyList()); val movies = _movies.asStateFlow()
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    init { viewModelScope.launch { runCatching { repository.genres() }.onSuccess { _genres.value = it }.onFailure { _error.value = it.message } } }
    fun select(genre: Genre) { viewModelScope.launch { _loading.value = true; _error.value = null; runCatching { repository.genreMovies(genre.id) }.onSuccess { _movies.value = it }.onFailure { _error.value = it.message }; _loading.value = false } }
}

@Composable
fun GenreScreen(repository: MovieRepository, onBack: () -> Unit, onOpenMovie: (Int) -> Unit) {
    val vm: GenreViewModel = viewModel(key = "genres", factory = viewModelFactory { initializer { GenreViewModel(repository) } })
    val genres by vm.genres.collectAsState(); val movies by vm.movies.collectAsState(); val loading by vm.loading.collectAsState(); val error by vm.error.collectAsState()
    var selected by remember { mutableStateOf<Genre?>(null) }
    Column(Modifier.fillMaxSize().background(Ink).padding(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Browse by genre", style = ZukoType.screenTitle, color = TextHi); TextButton(onClick = onBack) { Text("Back") } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(genres) { genre -> FilterChip(selected = selected?.id == genre.id, onClick = { selected = genre; vm.select(genre) }, label = { Text(genre.name) }) }
            items(listOf("Nollywood")) { name -> FilterChip(selected = selected?.name == name, onClick = { selected = Genre(-1, name); vm.select(Genre(10749, name)) }, label = { Text(name) }) }
        }
        if (selected == null) Text("Choose a genre to explore movies.", color = TextLo)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies) { movie -> PosterCard(movie = movie, onClick = { onOpenMovie(movie.id) }) }
        }
    }
}
