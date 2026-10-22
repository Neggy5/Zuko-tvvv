package com.zuko.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuko.tv.data.HomeRow
import com.zuko.tv.data.MovieRepository
import com.zuko.tv.data.MovieSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Remembers focused poster across navigation. */
class FocusMemory {
    var row: Int = 0
    var col: Int = 0
}

class HomeViewModel(private val repository: MovieRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MovieSummary>>(emptyList())
    val searchResults: StateFlow<List<MovieSummary>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    val focusMemory = FocusMemory()
    private var searchJob: Job? = null

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

    fun setQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        val q = value.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _searching.value = true
            _searchError.value = null
            try {
                _searchResults.value = repository.search(q)
                if (_searchResults.value.isEmpty()) {
                    _searchError.value = "No movies found for \"$q\""
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _searchError.value = e.message ?: "Search failed"
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _query.value = ""
        _searchResults.value = emptyList()
        _searchError.value = null
        _searching.value = false
    }
}
