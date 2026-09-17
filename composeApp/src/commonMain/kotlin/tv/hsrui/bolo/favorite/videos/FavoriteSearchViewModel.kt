package tv.hsrui.bolo.favorite.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.feature.favorite.FavoriteVideoCard
import tv.hsrui.network.feature.favorite.fetchFavoriteFolderContent
import tv.hsrui.network.login.storage.LoginStorage

data class FavoriteSearchUiState(
    val keyword: String = "",
    val accountMid: Long = 0,
    val videos: List<FavoriteVideoCard> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val hasMore: Boolean = false,
    val revision: Int = 0,
    val ownerMid: Long = 0,
)

class FavoriteSearchViewModel(private val mediaId: Long, private val loginStorage: LoginStorage = getKoin().get()) : ViewModel() {
    private var currentUserMid = readCurrentUserMid()
    private val _uiState = MutableStateFlow(FavoriteSearchUiState(accountMid = currentUserMid))
    val uiState = _uiState.asStateFlow()

    private var pageNumber = 0
    private var requestVersion = 0
    private var requestJob: Job? = null
    private val removedIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect { mid ->
                if (mid != currentUserMid) {
                    currentUserMid = mid
                    val keyword = _uiState.value.keyword
                    requestVersion++
                    requestJob?.cancel()
                    pageNumber = 0
                    removedIds.clear()
                    _uiState.value = FavoriteSearchUiState(accountMid = mid, revision = _uiState.value.revision + 1)
                    if (keyword.isNotEmpty()) search(keyword)
                }
            }
        }
    }

    private fun readCurrentUserMid(): Long =
        if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L

    fun canManageNow(): Boolean = currentUserMid > 0 && currentUserMid == readCurrentUserMid() &&
        _uiState.value.ownerMid == currentUserMid

    fun search(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        requestVersion++
        requestJob?.cancel()
        pageNumber = 0
        removedIds.clear()
        _uiState.value = FavoriteSearchUiState(keyword = keyword, accountMid = currentUserMid, revision = _uiState.value.revision + 1)
        loadPage(append = false)
    }

    fun refreshVideos() {
        search(_uiState.value.keyword)
    }

    fun loadMoreVideos() {
        val state = _uiState.value
        if (state.keyword.isEmpty() || state.isSearching || state.isLoadingMore || !state.hasMore || state.error != null) return
        loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val version = ++requestVersion
        val identity = currentUserMid
        val keyword = _uiState.value.keyword
        _uiState.value = _uiState.value.copy(
            isSearching = !append,
            isLoadingMore = append,
            error = null,
            loadMoreError = null,
        )
        requestJob = viewModelScope.launch {
            try {
                do {
                    val nextPage = pageNumber + 1
                    val result = withTimeoutOrNull(15_000) {
                        fetchFavoriteFolderContent(mediaId = mediaId, keyword = keyword, pageNumber = nextPage)
                    } ?: error("搜索请求超时")
                    if (version != requestVersion || identity != readCurrentUserMid()) return@launch
                    check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
                    val videos = result.videos.filter { it.avid !in removedIds }
                    pageNumber = nextPage
                    _uiState.value = _uiState.value.copy(
                        videos = (_uiState.value.videos + videos).filter { it.avid !in removedIds }.distinctBy { it.avid },
                        hasMore = result.hasMore,
                        ownerMid = result.folderInfo?.mid ?: 0L,
                    )
                } while (videos.isEmpty() && result.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion && identity == readCurrentUserMid()) {
                    val message = e.message ?: "其他网络错误"
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

    fun removeItem(avid: Long) {
        removedIds += avid
        _uiState.value = _uiState.value.copy(videos = _uiState.value.videos.filter { it.avid != avid })
    }
}
