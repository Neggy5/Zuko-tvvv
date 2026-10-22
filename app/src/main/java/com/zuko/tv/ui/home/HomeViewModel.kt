package com.zuko.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.HomeRow
import com.zuko.tv.data.MovieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object MissingKey : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(val rows: List<HomeRow>) : HomeUiState
}

/** Remembers which tile was focused so coming back from a detail page restores it. */
class FocusMemory(var row: Int = 0, var col: Int = 0)

class HomeViewModel(private val repository: MovieRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val focusMemory = FocusMemory()

    init {
        load()
    }

    fun load() {
        if (!repository.isConfigured) {
            _state.value = HomeUiState.MissingKey
            return
        }
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                HomeUiState.Ready(repository.homeRows())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeUiState.Error(e.message ?: "Something went wrong loading movies.")
            }
        }
    }
}
