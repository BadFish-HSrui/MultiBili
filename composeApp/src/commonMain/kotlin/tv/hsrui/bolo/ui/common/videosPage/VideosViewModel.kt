package tv.hsrui.bolo.ui.common.videosPage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.model.VideosResult

abstract class VideosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<VideosUiState>(VideosUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var canLoadMore: Boolean = false
    var pageNumber: Int = 1

    var isLoading: Boolean = false

    protected abstract suspend fun fetchVideos(): VideosResult

    open fun resetPageNumber() {
        pageNumber = 1
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = VideosUiState.Loading
            try {
                val result = fetchVideos()
                if (result.isSuccess) {
                    _uiState.value = VideosUiState.Success(result.validData.videosList)
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = VideosUiState.Error("[加载错误]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = VideosUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun loadMoreVideos() {
        if (isLoading) return
        isLoading = true
        pageNumber++

        viewModelScope.launch {
            try {
                val result = fetchVideos()
                if (result.isSuccess) {
                    _uiState.value =
                        VideosUiState.Success(
                            ((_uiState.value as VideosUiState.Success).videos
                                    + result.validData.videosList).distinctBy { it.avid })
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = VideosUiState.Error("[加载错误1]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = VideosUiState.Error(e.message ?: "其他网络错误")
            }
        }
        isLoading = false
    }

    fun refreshVideos() {
        resetPageNumber()
        loadVideos()
    }
}