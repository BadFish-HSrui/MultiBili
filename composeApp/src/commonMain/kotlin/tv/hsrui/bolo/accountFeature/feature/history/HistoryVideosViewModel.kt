package tv.hsrui.bolo.accountFeature.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.history.HistoryLoadParams
import tv.hsrui.network.feature.history.HistoryVideosResponse
import tv.hsrui.network.feature.history.fetchHistoryVideos

open class HistoryVideosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HistoryVideosUiState>(HistoryVideosUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var canLoadMore: Boolean = false
    private var loadParams: HistoryLoadParams = HistoryLoadParams()
    var isLoading: Boolean = false

    init {
        loadVideos()
    }


    open suspend fun firstLoad(): HistoryVideosResponse {
        return fetchHistoryVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = HistoryVideosUiState.Loading
            try {
                val result = firstLoad()
                if (result.isSuccess) {
                    loadParams = result.validData.loadParams
                    _uiState.value = HistoryVideosUiState.Success(result.validData.list.distinctBy { it.recordKey })
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = HistoryVideosUiState.Error("[Api请求错误0]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = HistoryVideosUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun loadMoreVideos() {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            try {
                val result = fetchHistoryVideos(loadParams = loadParams)
                if (result.isSuccess) {
                    loadParams = result.validData.loadParams
                    canLoadMore = result.validData.canLoadMore
                    _uiState.value =
                        HistoryVideosUiState.Success(
                            ((_uiState.value as HistoryVideosUiState.Success).videos
                                    + result.validData.list).distinctBy { it.recordKey })
                } else {
                    _uiState.value = HistoryVideosUiState.Error("[Api请求错误1]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = HistoryVideosUiState.Error(e.message ?: "其他网络错误")
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshVideos() {
        _uiState.value = HistoryVideosUiState.Loading
        loadParams = HistoryLoadParams()
        loadVideos()
    }

    fun removeItem(recordKey: String) {
        if (_uiState.value is HistoryVideosUiState.Success) {
            _uiState.value = HistoryVideosUiState.Success(
                (_uiState.value as HistoryVideosUiState.Success).videos.filter {
                    it.recordKey != recordKey
                }
            )
        }
    }
}