package com.zuko.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.MovieDetails
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository
import com.zuko.tv.data.WatchHistory
import com.zuko.tv.data.WatchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Ready(
        val url: String,
        val title: String,
        val startMs: Long,
        val sourceIndex: Int,
        val sourceCount: Int,
    ) : PlayerState
    data class Error(val message: String) : PlayerState
}

class PlayerViewModel(
    private val movieRepository: MovieRepository,
    private val streamRepository: StreamRepository,
    private val watchHistory: WatchHistory,
    private val tmdbId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var details: MovieDetails? = null
    private var title: String = ""
    private var candidates: List<String> = emptyList()
    private var index = 0
    private var startMs = 0L

    init {
        resolve()
    }

    fun resolve(forceRefresh: Boolean = false) {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            _state.value = try {
                if (tmdbId == 0) {
                    PlayerState.Error("Invalid movie id.")
                } else {
                    val d = runCatching { movieRepository.details(tmdbId) }.getOrNull()
                    if (d != null) details = d
                    title = d?.title?.takeIf { it.isNotBlank() } ?: "Movie $tmdbId"
                    val urls = streamRepository.resolveStreams(
                        tmdbId = tmdbId,
                        imdbId = d?.imdbId,
                        title = title,
                        year = d?.releaseDate?.take(4),
                        forceRefresh = forceRefresh,
                    )
                    if (urls.isEmpty()) {
                        PlayerState.Error("Empty stream URL for \"$title\".")
                    } else {
                        candidates = urls
                        index = 0
                        startMs = resumePosition()
                        PlayerState.Ready(urls[0], title, startMs, 0, urls.size)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PlayerState.Error(e.message ?: "Couldn't find a playable stream.")
            }
        }
    }

    private fun resumePosition(): Long {
        val saved = watchHistory.get(tmdbId) ?: return 0L
        return if (saved.positionMs > 15_000L) saved.positionMs - 3_000L else 0L
    }

    /** Playback failed: move to the next candidate source (keeping position) or give up. */
    fun onPlaybackError(message: String, positionMs: Long) {
        if (index + 1 < candidates.size) {
            index += 1
            startMs = if (positionMs > 0L) positionMs else startMs
            _state.value = PlayerState.Ready(candidates[index], title, startMs, index, candidates.size)
        } else {
            val tried = candidates.size.coerceAtLeast(1)
            _state.value = PlayerState.Error("None of the $tried sources would play.\n$message")
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val d = details ?: return
        if (_state.value !is PlayerState.Ready) return
        watchHistory.record(
            WatchItem(
                id = d.id,
                title = d.title,
                posterPath = d.posterPath,
                backdropPath = d.backdropPath,
                releaseDate = d.releaseDate,
                voteAverage = d.voteAverage,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
