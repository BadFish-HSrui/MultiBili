package tv.hsrui.bolo.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.search.SearchTrendingItemData
import tv.hsrui.network.feature.search.SearchTrendingResponse
import tv.hsrui.network.feature.search.fetchSearchTrending

sealed interface SearchTrendingUiState {
    data object Loading : SearchTrendingUiState
    data class Success(val items: List<SearchTrendingItemData>) : SearchTrendingUiState
    data class Error(val message: String) : SearchTrendingUiState
}

class SearchTrendingViewModel(
    private val fetch: suspend () -> SearchTrendingResponse = ::fetchSearchTrending,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchTrendingUiState>(SearchTrendingUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null

    init {
        loadTrending()
    }

    fun loadTrending() {
        if (requestJob?.isActive == true) return
        _uiState.value = SearchTrendingUiState.Loading
        requestJob = viewModelScope.launch {
            try {
                val response = fetch()
                _uiState.value = if (response.isSuccess) {
                    SearchTrendingUiState.Success(response.items)
                } else {
                    SearchTrendingUiState.Error("热搜加载失败")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SearchTrendingUiState.Error("热搜加载失败")
            }
        }
    }
}
