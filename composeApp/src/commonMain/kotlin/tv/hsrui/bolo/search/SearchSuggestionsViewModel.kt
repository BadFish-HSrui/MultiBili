package tv.hsrui.bolo.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.search.SearchSuggestionsResponse
import tv.hsrui.network.feature.search.fetchSearchSuggestions

sealed interface SearchSuggestionsUiState {
    data object Idle : SearchSuggestionsUiState
    data class Success(val keywords: List<String>) : SearchSuggestionsUiState
    data class Error(val message: String) : SearchSuggestionsUiState
}

class SearchSuggestionsViewModel(
    private val fetch: suspend (String) -> SearchSuggestionsResponse = ::fetchSearchSuggestions,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchSuggestionsUiState>(SearchSuggestionsUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0
    private var requestedKeyword: String? = null

    fun loadSuggestions(keyword: String, immediately: Boolean = false) {
        val term = keyword.trim()
        if (term.isEmpty()) {
            clearSuggestions()
            return
        }
        if (!immediately && requestedKeyword == term) return

        val version = ++requestVersion
        requestJob?.cancel()
        requestedKeyword = term
        // 请求期间保留当前内容，直到新结果返回后再替换。
        requestJob = viewModelScope.launch {
            try {
                val response = fetch(term)
                if (version != requestVersion) return@launch
                _uiState.value = if (response.isSuccess) {
                    SearchSuggestionsUiState.Success(response.keywords)
                } else {
                    SearchSuggestionsUiState.Error("搜索建议加载失败")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = SearchSuggestionsUiState.Error("搜索建议加载失败")
                }
            }
        }
    }

    fun clearSuggestions() {
        requestVersion++
        requestJob?.cancel()
        requestJob = null
        requestedKeyword = null
        _uiState.value = SearchSuggestionsUiState.Idle
    }
}
