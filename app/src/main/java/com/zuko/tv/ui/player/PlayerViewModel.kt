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
    titleEncoded: String,
) : ViewModel() {

    private val title: String = try {
        URLDecoder.decode(titleEncoded, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        titleEncoded
    }

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                PlayerState.Ready(repository.resolveStream(tmdbId, imdbId, title, year = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerState.Error(e.message ?: "Couldn't find a playable stream for this title.")
            }
        }
    }
}
