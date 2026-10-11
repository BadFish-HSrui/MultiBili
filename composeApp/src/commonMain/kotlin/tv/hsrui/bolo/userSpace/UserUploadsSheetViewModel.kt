package tv.hsrui.bolo.userSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.network.feature.user.space.UserSpaceUploadOrder
import tv.hsrui.network.feature.user.space.fetchUserSpaceUploads
import tv.hsrui.network.login.storage.LoginStorage

class UserUploadsSheetViewModel(
    private val mid: Long,
    private val loginStorage: LoginStorage,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserUploadsSheetUiState())
    val uiState = _uiState.asStateFlow()
    private var currentUserMid = readCurrentUserMid()
    private var requestVersion = 0
    private var requestJob: Job? = null
    private var pageNumber = 0

    init {
        refreshVideos()
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect {
                val identity = readCurrentUserMid()
                if (identity != currentUserMid) {
                    currentUserMid = identity
                    pageNumber = 0
                    _uiState.value = UserUploadsSheetUiState(refreshGeneration = _uiState.value.refreshGeneration)
                    refreshVideos()
                }
            }
        }
    }

    private fun readCurrentUserMid(): Long =
        if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L

    fun refreshVideos() = loadPage(append = false)

    fun loadMoreVideos() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore || !state.hasMore ||
            state.videos !is VideosUiState.Success || state.refreshError != null
        ) return
        loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val version = ++requestVersion
        requestJob?.cancel()
        val identity = currentUserMid
        val nextPage = if (append) pageNumber + 1 else 1
        val previous = _uiState.value.videos as? VideosUiState.Success
        _uiState.value = _uiState.value.copy(
            videos = previous ?: VideosUiState.Loading,
            isRefreshing = !append,
            isLoadingMore = append,
            refreshError = null,
            loadMoreError = null,
            refreshGeneration = _uiState.value.refreshGeneration + if (append) 0 else 1,
        )
        requestJob = viewModelScope.launch {
            try {
                check(mid > 0) { "用户 UID 无效" }
                val result = fetchUserSpaceUploads(mid, nextPage, UserSpaceUploadOrder.Latest)
                ensureActive()
                if (version != requestVersion || identity != readCurrentUserMid()) return@launch
                check(result.isSuccess) { result.message.ifBlank { "视频投稿加载失败（${result.code}）" } }
                val videos = result.videos.filter { it.avid > 0 }.distinctBy { it.avid }
                check(videos.isNotEmpty() || result.total == 0) { "投稿列表为空，但接口总数不为零，请重试" }
                pageNumber = nextPage
                _uiState.value = _uiState.value.copy(
                    videos = VideosUiState.Success(
                        (if (append) previous?.videos.orEmpty() + videos else videos).distinctBy { it.avid },
                    ),
                    hasMore = result.hasMore,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion && identity == readCurrentUserMid()) {
                    val message = e.message ?: "视频投稿加载失败"
                    _uiState.value = when {
                        append -> _uiState.value.copy(loadMoreError = message)
                        previous != null -> _uiState.value.copy(refreshError = message)
                        else -> _uiState.value.copy(videos = VideosUiState.Error(message), hasMore = false)
                    }
                }
            } finally {
                if (version == requestVersion) {
                    _uiState.value = _uiState.value.copy(isRefreshing = false, isLoadingMore = false)
                }
            }
        }
    }
}
