package com.zuko.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.Tmdb
import com.zuko.tv.data.TvEpisode
import com.zuko.tv.data.TvSeasonSummary
import com.zuko.tv.data.TvSummary
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import kotlinx.coroutines.launch

@Composable
fun TvShowsScreen(repository: MovieRepository, onBack: () -> Unit, onPlay: (Int, String, String, Int, Int, Int) -> Unit) {
    var shows by remember { mutableStateOf<List<TvSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<TvSummary?>(null) }
    var seasons by remember { mutableStateOf<List<TvSeasonSummary>>(emptyList()) }
    var seasonNumber by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<TvEpisode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { repository.popularTv() }.onSuccess { shows = it }.onFailure { error = it.message ?: "Couldn't load TV shows." }
        loading = false
    }
    LaunchedEffect(selected?.id) {
        val show = selected ?: return@LaunchedEffect
        loading = true
        runCatching { repository.tvDetails(show.id) }.onSuccess { detail ->
            seasons = detail.seasons.filter { it.seasonNumber > 0 }
            seasonNumber = seasons.firstOrNull()?.seasonNumber ?: 1
        }.onFailure { error = it.message ?: "Couldn't load show details." }
        loading = false
    }
    LaunchedEffect(selected?.id, seasonNumber) {
        val show = selected ?: return@LaunchedEffect
        loading = true
        runCatching { repository.tvSeason(show.id, seasonNumber) }.onSuccess { episodes = it.episodes }.onFailure { error = it.message ?: "Couldn't load episodes." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { if (selected != null) { selected = null; seasons = emptyList(); episodes = emptyList() } else onBack() }) { Text("‹ Back") }
            Spacer(Modifier.width(16.dp))
            Text(selected?.name ?: "TV SHOWS", color = TextHi, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = Color(0xFFFF8A80)); Spacer(Modifier.height(8.dp)) }
        if (selected == null) {
            androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search TV shows") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { loading = true; error = null; scope.launch { runCatching { if (query.isBlank()) repository.popularTv() else repository.searchTv(query) }.onSuccess { shows = it }.onFailure { error = it.message ?: "Search failed." }; loading = false } }) { Text("Search") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shows, key = { it.id }) { show ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { selected = show; error = null }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = show.posterPath?.let(Tmdb::poster), contentDescription = show.name, modifier = Modifier.size(width = 74.dp, height = 104.dp))
                        Spacer(Modifier.width(14.dp))
                        Column { Text(show.name, color = TextHi); Text(show.firstAirDate?.take(4).orEmpty(), color = TextLo); Text(show.overview, color = TextLo, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                seasons.forEach { season ->
                    OutlinedButton(onClick = { seasonNumber = season.seasonNumber }, modifier = Modifier.padding(end = 6.dp)) { Text(if (season.seasonNumber == seasonNumber) "✓ S${season.seasonNumber}" else "S${season.seasonNumber}") }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(episodes, key = { it.id }) { ep ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${ep.episodeNumber}. ${ep.name.ifBlank { "Episode ${ep.episodeNumber}" }}", color = TextHi)
                            Text(ep.overview, color = TextLo, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = { val count = seasons.firstOrNull { it.seasonNumber == seasonNumber }?.episodeCount ?: episodes.size; onPlay(selected!!.id, selected!!.name, selected!!.firstAirDate?.take(4).orEmpty(), seasonNumber, ep.episodeNumber, count) }) { Text("Play") }
                    }
                }
            }
        }
    }
}
