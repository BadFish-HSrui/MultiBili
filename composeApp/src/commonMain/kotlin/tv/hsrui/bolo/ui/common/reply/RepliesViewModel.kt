package tv.hsrui.bolo.ui.common.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.reply.ReplyResponse
import tv.hsrui.network.feature.reply.ReplySort
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.reply.fetchRepliesWith

class RepliesViewModel(val replySection: ReplySectionType) : ViewModel() {
    private val _uiState = MutableStateFlow<RepliesUiState>(RepliesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _sortType = MutableStateFlow(ReplySort.Like)
    val sortType = _sortType.asStateFlow()

    var canLoadMore: Boolean = false
    var pageNumber: Int = 1
    var isLoading: Boolean = false

    init {
        loadReplies()
    }

    fun nextSortType() {
        _sortType.update {
            ReplySort.entries[(it.ordinal + 1) % ReplySort.entries.size]
        }
        refreshReplies()
    }

    suspend fun fetchReplies(): ReplyResponse =
        fetchRepliesWith(replySection = replySection, sort = sortType.value, pn = pageNumber, ps = 20)

    fun loadReplies() {
        viewModelScope.launch {
            _uiState.value = RepliesUiState.Loading
            try {
                val result = fetchReplies()
                if (result.isSuccess) {
                    _uiState.value =
                        RepliesUiState.Success(result.data.topReply, result.data.replies)
                    canLoadMore = result.data.hasMore
                } else {
                    _uiState.value = RepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.value = RepliesUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun loadMoreReplies() {
        if (isLoading || !canLoadMore) return
        isLoading = true
        pageNumber++

        viewModelScope.launch {
            try {
                val result = fetchReplies()

                if (result.isSuccess) {
                    _uiState.update { oldState ->
                        (oldState as RepliesUiState.Success).copy(
                            replies = (oldState.replies + result.data.replies).distinctBy { it.rpid })
                    }
                } else {
                    _uiState.value = RepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.value = RepliesUiState.Error(e.message ?: "其他网络错误")
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshReplies() {
        pageNumber = 1
        loadReplies()
    }
}