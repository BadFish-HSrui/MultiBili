package tv.hsrui.bolo.main.home.recommend

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.recommend.fetchRecommendVideos
import tv.hsrui.network.model.VideosResult
import kotlin.random.Random

class RecommendViewModel : VideosViewModel() {
    init {
        pageNumber = Random.nextInt(114514)
        loadVideos()
    }

    override suspend fun fetchVideos(): VideosResult {
        return fetchRecommendVideos(freshIndex = pageNumber, ps = 24)
    }

    override fun refreshVideos() {
        _uiState.value = VideosUiState.Loading
        isRefreshing = true
        viewModelScope.launch {
            try {
                val result = fetchVideos()
                if (result.isSuccess) {
                    pageNumber++
                    _uiState.value = VideosUiState.Success(result.validData.videosList)
                    canLoadMore = result.validData.canLoadMore
                } else {
                    _uiState.value = VideosUiState.Error("[Api请求错误0]: " + result.message)
                }
            } catch (e: Exception) {
                _uiState.value = VideosUiState.Error(e.message ?: "其他网络错误")
            }
        }
        isRefreshing = false
    }
}