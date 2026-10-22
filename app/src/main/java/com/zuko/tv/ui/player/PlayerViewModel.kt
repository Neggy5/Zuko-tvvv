package com.zuko.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Ready(val url: String, val title: String) : PlayerState
    data class Error(val message: String) : PlayerState
}

class PlayerViewModel(
    private val movieRepository: MovieRepository,
    private val streamRepository: StreamRepository,
    private val tmdbId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            _state.value = try {
                if (tmdbId <= 0) {
                    PlayerState.Error("Invalid movie id.")
                } else {
                    val details = runCatching { movieRepository.details(tmdbId) }.getOrNull()
                    val title = details?.title?.takeIf { it.isNotBlank() } ?: "Movie $tmdbId"
                    val year = details?.releaseDate?.take(4)
                    val imdb = details?.imdbId
                    val url = streamRepository.resolveStream(tmdbId, imdb, title, year)
                    if (url.isBlank()) {
                        PlayerState.Error("Empty stream URL for \"$title\".")
                    } else {
                        PlayerState.Ready(url = url, title = title)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PlayerState.Error(e.message ?: "Couldn't find a playable stream.")
            }
        }
    }

    fun reportPlaybackError(message: String) {
        _state.value = PlayerState.Error(message)
    }
}
