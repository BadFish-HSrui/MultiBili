package tv.hsrui.bolo.main.live.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.liveFeature.recommend.fetchLiveRecommend

class LiveRecommendViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LiveRecommendUiState>(LiveRecommendUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0

    init {
        refreshRooms()
    }

    fun refreshRooms() {
        val version = ++requestVersion
        requestJob?.cancel()
        val previous = _uiState.value as? LiveRecommendUiState.Success
        _uiState.value = previous?.copy(
            isRefreshing = true,
            refreshError = null,
            isLoadingMore = false,
            loadMoreError = null,
        ) ?: LiveRecommendUiState.Loading

        requestJob = viewModelScope.launch {
            try {
                val result = fetchLiveRecommend(page = 1, pageSize = 30)
                check(result.isSuccess) { result.message.ifBlank { "直播推荐加载失败（${result.code}）" } }
                if (version != requestVersion) return@launch
                _uiState.value = LiveRecommendUiState.Success(
                    rooms = result.rooms.distinctBy { it.roomId },
                    page = 1,
                    hasMore = result.rooms.isNotEmpty(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                val message = error.message ?: "直播推荐加载失败"
                _uiState.value = previous?.copy(
                    isRefreshing = false,
                    refreshError = message,
                    isLoadingMore = false,
                    loadMoreError = null,
                ) ?: LiveRecommendUiState.Error(message)
            }
        }
    }

    fun loadMoreRooms() {
        val current = _uiState.value as? LiveRecommendUiState.Success ?: return
        if (current.isRefreshing || current.isLoadingMore || !current.hasMore || current.refreshError != null) return
        val version = requestVersion
        val nextPage = current.page + 1
        _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)

        requestJob = viewModelScope.launch {
            try {
                val result = fetchLiveRecommend(page = nextPage, pageSize = 30)
                check(result.isSuccess) { result.message.ifBlank { "直播推荐加载失败（${result.code}）" } }
                if (version != requestVersion) return@launch
                _uiState.value = LiveRecommendUiState.Success(
                    rooms = (current.rooms + result.rooms).distinctBy { it.roomId },
                    page = nextPage,
                    hasMore = result.rooms.isNotEmpty(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                _uiState.value = current.copy(
                    isLoadingMore = false,
                    loadMoreError = error.message ?: "直播推荐加载失败",
                )
            }
        }
    }
}
