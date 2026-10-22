package com.zuko.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.MovieDetails
import com.zuko.tv.data.MovieRepository
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

class DetailViewModel(
    private val repository: MovieRepository,
    private val movieId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

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
}
