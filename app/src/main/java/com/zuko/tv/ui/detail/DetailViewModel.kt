package com.zuko.tv.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.MovieDetails
import com.zuko.tv.data.MovieDownload
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.StreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Ready(val movie: MovieDetails) : DetailUiState
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object Resolving : DownloadUiState
    data class Started(val message: String) : DownloadUiState
    data class Failed(val message: String) : DownloadUiState
}

class DetailViewModel(
    application: Application,
    private val repository: MovieRepository,
    private val streamRepository: StreamRepository,
    private val movieId: Int,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _download = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val download: StateFlow<DownloadUiState> = _download.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                DetailUiState.Ready(repository.details(movieId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailUiState.Error(e.message ?: "Something went wrong loading this title.")
            }
        }
    }

    fun downloadMovie() {
        val ready = _state.value as? DetailUiState.Ready ?: return
        val movie = ready.movie
        viewModelScope.launch {
            _download.value = DownloadUiState.Resolving
            try {
                val year = movie.releaseDate?.take(4)
                val url = streamRepository.resolveStream(
                    tmdbId = movie.id,
                    imdbId = movie.imdbId,
                    title = movie.title,
                    year = year,
                )
                if (url.contains(".m3u8", ignoreCase = true)) {
                    _download.value = DownloadUiState.Failed(
                        "This stream is HLS (m3u8) and can’t be saved as a single file. Use Play instead."
                    )
                    return@launch
                }
                val id = MovieDownload.enqueue(getApplication(), url, movie.title)
                _download.value = if (id != null) {
                    DownloadUiState.Started("Download started — check notifications / Movies folder.")
                } else {
                    DownloadUiState.Failed("DownloadManager is not available on this device.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _download.value = DownloadUiState.Failed(e.message ?: "Download failed.")
            }
        }
    }

    fun clearDownloadStatus() {
        _download.value = DownloadUiState.Idle
    }
}
