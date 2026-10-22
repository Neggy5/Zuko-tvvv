package com.zuko.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.StreamRepository
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Ready(val url: String) : PlayerState
    data class Error(val message: String) : PlayerState
}

class PlayerViewModel(
    private val repository: StreamRepository,
    private val tmdbId: Int,
    private val imdbId: String?,
    titleRaw: String,
) : ViewModel() {

    private val title: String = decode(titleRaw)

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val url = repository.resolveStream(tmdbId, imdbId, title, year = null)
                if (url.isBlank()) {
                    PlayerState.Error("Empty stream URL for \"$title\".")
                } else {
                    PlayerState.Ready(url)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerState.Error(e.message ?: "Couldn't find a playable stream for this title.")
            }
        }
    }

    /** Called from PlayerScreen when ExoPlayer fails — must not crash the Activity. */
    fun reportPlaybackError(message: String) {
        _state.value = PlayerState.Error(message)
    }

    private fun decode(value: String): String {
        if (value.isBlank()) return value
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
