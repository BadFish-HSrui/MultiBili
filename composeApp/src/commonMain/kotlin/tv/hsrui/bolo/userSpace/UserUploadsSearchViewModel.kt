package tv.hsrui.bolo.userSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.feature.user.space.UserSpaceUploadOrder
import tv.hsrui.network.feature.user.space.fetchUserSpaceUploads
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.VideoCard

data class UserUploadsSearchUiState(
    val keyword: String = "",
    val order: UserSpaceUploadOrder = UserSpaceUploadOrder.Latest,
    val videos: List<VideoCard> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val hasMore: Boolean = false,
    val revision: Int = 0,
)

class UserUploadsSearchViewModel(
    private val mid: Long,
    private val loginStorage: LoginStorage = getKoin().get(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserUploadsSearchUiState())
    val uiState = _uiState.asStateFlow()
    private var currentUserMid = readCurrentUserMid()
    private var requestVersion = 0
    private var requestJob: Job? = null
    private var pageNumber = 0

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect {
                val identity = readCurrentUserMid()
                if (identity != currentUserMid) {
                    currentUserMid = identity
                    requestVersion++
                    requestJob?.cancel()
                    val state = _uiState.value
                    _uiState.value = UserUploadsSearchUiState(order = state.order, revision = state.revision + 1)
                    pageNumber = 0
                    if (state.keyword.isNotEmpty()) search(state.keyword)
                }
            }
        }
    }

    private fun readCurrentUserMid(): Long =
        if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L

    fun search(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        requestVersion++
        requestJob?.cancel()
        pageNumber = 0
        _uiState.value = UserUploadsSearchUiState(
            keyword = keyword,
            order = _uiState.value.order,
            revision = _uiState.value.revision + 1,
        )
        loadPage(append = false)
    }

    fun setOrder(order: UserSpaceUploadOrder) {
        if (order == _uiState.value.order) return
        _uiState.value = _uiState.value.copy(order = order)
        search(_uiState.value.keyword)
    }

    fun refreshVideos() = search(_uiState.value.keyword)

    fun loadMoreVideos() {
        val state = _uiState.value
        if (state.keyword.isEmpty() || state.isSearching || state.isLoadingMore || !state.hasMore || state.error != null) return
        loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val version = ++requestVersion
        val identity = currentUserMid
        val keyword = _uiState.value.keyword
        val order = _uiState.value.order
        val nextPage = pageNumber + 1
        _uiState.value = _uiState.value.copy(
            isSearching = !append, isLoadingMore = append, error = null, loadMoreError = null,
        )
        requestJob = viewModelScope.launch {
            try {
                check(mid > 0) { "用户信息无效" }
                val result = fetchUserSpaceUploads(mid = mid, pageNumber = nextPage, order = order, keyword = keyword)
                if (version != requestVersion || identity != readCurrentUserMid()) return@launch
                check(result.isSuccess) { result.message.ifBlank { "视频投稿搜索失败（${result.code}）" } }
                val videos = result.videos.filter { it.avid > 0 }.distinctBy { it.avid }
                check(videos.isNotEmpty() || result.total == 0) { "投稿列表为空，但接口总数不为零，请重试" }
                pageNumber = nextPage
                _uiState.value = _uiState.value.copy(
                    videos = (if (append) _uiState.value.videos + videos else videos).distinctBy { it.avid },
                    hasMore = result.hasMore,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion && identity == readCurrentUserMid()) {
                    val message = e.message ?: "视频投稿搜索失败"
                    _uiState.value = if (append) _uiState.value.copy(loadMoreError = message)
                    else _uiState.value.copy(error = message)
                }
            } finally {
                if (version == requestVersion) {
                    _uiState.value = _uiState.value.copy(isSearching = false, isLoadingMore = false)
                }
            }
        }
    }
}
