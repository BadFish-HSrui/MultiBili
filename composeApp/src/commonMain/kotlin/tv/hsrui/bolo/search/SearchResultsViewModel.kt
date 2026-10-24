package tv.hsrui.bolo.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.search.SearchLiveType
import tv.hsrui.network.feature.search.fetchSearchLiveUsers
import tv.hsrui.network.feature.search.fetchSearchLiveRooms
import tv.hsrui.network.feature.search.SearchCategory
import tv.hsrui.network.feature.search.SearchUserOrder
import tv.hsrui.network.feature.search.SearchVideoOrder
import tv.hsrui.network.feature.search.fetchSearchMedia
import tv.hsrui.network.feature.search.fetchSearchUsers
import tv.hsrui.network.feature.search.fetchSearchVideos

class SearchResultsViewModel(
    private val keyword: String,
    private val category: SearchCategory,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchResultsUiState>(SearchResultsUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _videoOrder = MutableStateFlow(SearchVideoOrder.Comprehensive)
    val videoOrder = _videoOrder.asStateFlow()
    private val _userOrder = MutableStateFlow(SearchUserOrder.Default)
    val userOrder = _userOrder.asStateFlow()
    private val _liveType = MutableStateFlow(SearchLiveType.RoomComprehensive)
    val liveType = _liveType.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0

    init {
        refreshResults()
    }

    fun applyVideoOrder(order: SearchVideoOrder) {
        if (category != SearchCategory.Video || order == _videoOrder.value) return
        _videoOrder.value = order
        _uiState.value = SearchResultsUiState.Loading
        refreshResults()
    }

    fun applyUserOrder(order: SearchUserOrder) {
        if (category != SearchCategory.User || order == _userOrder.value) return
        _userOrder.value = order
        _uiState.value = SearchResultsUiState.Loading
        refreshResults()
    }

    fun applyLiveType(type: SearchLiveType) {
        if (category != SearchCategory.Live || type == _liveType.value) return
        _liveType.value = type
        _uiState.value = SearchResultsUiState.Loading
        refreshResults()
    }

    fun refreshResults() {
        val version = ++requestVersion
        requestJob?.cancel()
        val current = _uiState.value as? SearchResultsUiState.Success
        _uiState.value = current?.copy(
            isRefreshing = true,
            isLoadingMore = false,
            loadMoreError = null,
        ) ?: SearchResultsUiState.Loading
        val order = _videoOrder.value
        val userOrder = _userOrder.value
        val liveType = _liveType.value
        requestJob = viewModelScope.launch {
            try {
                val result = fetchResults(page = 1, order = order, userOrder = userOrder, liveType = liveType)
                if (version == requestVersion) _uiState.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = SearchResultsUiState.Error(e.message ?: "其他网络错误")
                }
            }
        }
    }

    fun loadMoreResults() {
        val current = _uiState.value as? SearchResultsUiState.Success ?: return
        if (current.isRefreshing || current.isLoadingMore || !current.hasMore) return
        val version = ++requestVersion
        val order = _videoOrder.value
        val userOrder = _userOrder.value
        val liveType = _liveType.value
        _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)
        requestJob = viewModelScope.launch {
            try {
                val result = fetchResults(page = current.page + 1, order = order, userOrder = userOrder, liveType = liveType)
                if (version != requestVersion) return@launch
                _uiState.value = result.copy(
                    anchors = (current.anchors + result.anchors).distinctBy { it.mid },
                    rooms = (current.rooms + result.rooms).distinctBy { it.roomId },
                    videos = (current.videos + result.videos).distinctBy { it.avid },
                    media = (current.media + result.media).distinctBy { it.seasonId },
                    users = (current.users + result.users).distinctBy { it.mid },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = current.copy(loadMoreError = e.message ?: "其他网络错误")
                }
            }
        }
    }

    private suspend fun fetchResults(
        page: Int,
        order: SearchVideoOrder,
        userOrder: SearchUserOrder,
        liveType: SearchLiveType,
    ): SearchResultsUiState.Success {
        return if (category == SearchCategory.Video) {
            val result = fetchSearchVideos(keyword = keyword, page = page, order = order)
            check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
            check(result.pageNumber == page) { "搜索响应页码不匹配" }
            SearchResultsUiState.Success(
                videos = result.validData.videosList.distinctBy { it.avid },
                page = result.pageNumber,
                hasMore = result.validData.canLoadMore,
            )
        } else if (category == SearchCategory.Live && liveType.apiValue == SearchLiveType.AnchorRelated.apiValue) {
            val result = fetchSearchLiveUsers(keyword = keyword, page = page, order = liveType.order)
            check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
            check(result.pageNumber == page) { "搜索响应页码不匹配" }
            SearchResultsUiState.Success(
                anchors = result.users.distinctBy { it.mid },
                page = result.pageNumber,
                hasMore = result.hasMore,
            )
        } else if (category == SearchCategory.Live) {
            val result = fetchSearchLiveRooms(keyword = keyword, page = page, order = liveType.order)
            check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
            check(result.pageNumber == page) { "搜索响应页码不匹配" }
            SearchResultsUiState.Success(
                rooms = result.rooms.distinctBy { it.roomId },
                page = result.pageNumber,
                hasMore = result.hasMore,
            )
        } else if (category == SearchCategory.User) {
            val result = fetchSearchUsers(keyword = keyword, page = page, order = userOrder)
            check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
            check(result.pageNumber == page) { "搜索响应页码不匹配" }
            SearchResultsUiState.Success(
                users = result.users.distinctBy { it.mid },
                page = result.pageNumber,
                hasMore = result.hasMore,
            )
        } else {
            val result = fetchSearchMedia(keyword = keyword, category = category, page = page)
            check(result.isSuccess) { result.message.ifBlank { "搜索失败" } }
            check(result.pageNumber == page) { "搜索响应页码不匹配" }
            SearchResultsUiState.Success(
                media = result.media.distinctBy { it.seasonId },
                page = result.pageNumber,
                hasMore = result.hasMore,
            )
        }
    }
}
