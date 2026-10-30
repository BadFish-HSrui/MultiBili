package tv.hsrui.bolo.accountFeature.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.history.HistoryLoadParams
import tv.hsrui.network.feature.history.HistoryResponse
import tv.hsrui.network.feature.history.fetchHistory

open class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var canLoadMore: Boolean = false
    private var loadParams: HistoryLoadParams = HistoryLoadParams()
    var isLoading: Boolean = false

    init {
        loadItems()
    }


    open suspend fun firstLoad(): HistoryResponse {
        return fetchHistory()
    }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            try {
                val result = firstLoad()
                if (result.isSuccess) {
                    loadParams = result.validData.loadParams
                    _uiState.value = HistoryUiState.Success(result.validData.items.distinctBy { it.recordKey })
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = HistoryUiState.Error("[Api请求错误0]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = HistoryUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun loadMoreItems() {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            try {
                val result = fetchHistory(loadParams = loadParams)
                if (result.isSuccess) {
                    loadParams = result.validData.loadParams
                    canLoadMore = result.validData.canLoadMore
                    _uiState.value =
                        HistoryUiState.Success(
                            ((_uiState.value as HistoryUiState.Success).items
                                    + result.validData.items).distinctBy { it.recordKey })
                } else {
                    _uiState.value = HistoryUiState.Error("[Api请求错误1]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = HistoryUiState.Error(e.message ?: "其他网络错误")
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshItems() {
        _uiState.value = HistoryUiState.Loading
        loadParams = HistoryLoadParams()
        loadItems()
    }

    fun removeItem(recordKey: String) {
        if (_uiState.value is HistoryUiState.Success) {
            _uiState.value = HistoryUiState.Success(
                (_uiState.value as HistoryUiState.Success).items.filter {
                    it.recordKey != recordKey
                }
            )
        }
    }
}