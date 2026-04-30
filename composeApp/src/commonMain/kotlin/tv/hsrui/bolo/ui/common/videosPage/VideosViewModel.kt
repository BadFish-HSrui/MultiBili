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
    var isRefreshing: Boolean = false

    init {
        startLoading()
    }

    open fun startLoading() {
        pageNumber = 1
        loadVideos()
    }

    protected abstract suspend fun fetchVideos(): VideosResult

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = VideosUiState.Loading
            try {
                val result = fetchVideos()
                if (result.isSuccess) {
                    pageNumber++
                    _uiState.value = VideosUiState.Success(result.validData.videosList)
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = VideosUiState.Error(result.message)
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
                    pageNumber++
                    _uiState.value =
                        VideosUiState.Success((_uiState.value as VideosUiState.Success).videos + result.validData.videosList)
                    canLoadMore = result.validData.canLoadMore
                } else {
                    pageNumber--
                    _uiState.value = VideosUiState.Error(result.message)
                }
            } catch (e: Exception) {
                pageNumber--
                _uiState.value = VideosUiState.Error(e.message ?: "其他网络错误")
            } finally {
                isLoading = false
            }
        }
    }

    open fun refreshVideos() {
        pageNumber = 1
        isRefreshing = true
        loadVideos()
        isRefreshing = false
    }
}