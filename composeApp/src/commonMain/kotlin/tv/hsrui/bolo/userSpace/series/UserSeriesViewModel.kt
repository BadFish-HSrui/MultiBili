package tv.hsrui.bolo.userSpace.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.video.series.VideoSeriesVideosResponse
import tv.hsrui.network.feature.video.series.fetchVideoSeries
import tv.hsrui.network.feature.video.series.fetchVideoSeriesVideos
import tv.hsrui.network.model.Owner
import tv.hsrui.network.model.VideoCard

class UserSeriesViewModel(private val mid: Long, private val seriesId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<UserSeriesUiState>(UserSeriesUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var generation = 0

    init { loadSeries() }

    fun loadSeries() {
        val version = ++generation
        loadJob?.cancel()
        loadMoreJob?.cancel()
        val previous = _uiState.value as? UserSeriesUiState.Success
        _uiState.value = previous?.copy(
            isRefreshing = true, refreshError = null, isLoadingMore = false, loadMoreError = null,
        ) ?: UserSeriesUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val series = fetchVideoSeries(seriesId)
                check(series.seriesId == seriesId && series.mid == mid) { "系列信息不匹配，请重试" }
                val result = fetchVideoSeriesVideos(series.mid, series.seriesId)
                val videos = availableVideos(result, page = 1, ownerMid = series.mid)
                if (version != generation) return@launch
                _uiState.value = UserSeriesUiState.Success(series, videos, page = 1, canLoadMore = result.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                val message = e.message ?: "系列加载失败"
                val current = _uiState.value as? UserSeriesUiState.Success
                _uiState.value = current?.copy(isRefreshing = false, refreshError = message) ?: UserSeriesUiState.Error(message)
            }
        }
    }

    fun loadMoreVideos() {
        val state = _uiState.value as? UserSeriesUiState.Success ?: return
        if (state.isRefreshing || state.isLoadingMore || !state.canLoadMore || state.refreshError != null) return
        val version = generation
        val page = state.page + 1
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        loadMoreJob = viewModelScope.launch {
            try {
                val result = fetchVideoSeriesVideos(state.series.mid, state.series.seriesId, page)
                val videos = availableVideos(result, page, state.series.mid)
                if (version != generation) return@launch
                _uiState.update {
                    val current = it as? UserSeriesUiState.Success ?: return@update it
                    current.copy(
                        videos = (current.videos + videos).distinctBy { video -> video.avid },
                        page = page, canLoadMore = result.hasMore, isLoadingMore = false, loadMoreError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                _uiState.update {
                    val current = it as? UserSeriesUiState.Success ?: return@update it
                    current.copy(isLoadingMore = false, loadMoreError = e.message ?: "系列视频加载失败")
                }
            }
        }
    }

    private fun availableVideos(result: VideoSeriesVideosResponse, page: Int, ownerMid: Long): List<VideoCard> {
        check(result.isSuccess) { result.message.ifBlank { "系列视频加载失败（${result.code}）" } }
        check(result.pageNumber == page) { "系列视频页码不匹配，请重试" }
        val videos = result.videos.filter { it.avid > 0 }.distinctBy { it.avid }.map {
            if (it.upMid > 0) it else it.copy(_owner = Owner(mid = ownerMid, name = it.upName, face = it.upAvatarUrl))
        }
        check(videos.isNotEmpty() || result.total == 0) { "系列视频列表为空，但接口总数不为零，请重试" }
        return videos
    }
}
