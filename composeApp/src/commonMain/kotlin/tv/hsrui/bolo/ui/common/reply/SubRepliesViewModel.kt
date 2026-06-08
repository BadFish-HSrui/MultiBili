package tv.hsrui.bolo.ui.common.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.reply.SubReplyResponse
import tv.hsrui.network.feature.reply.fetchSubRepliesWith

class SubRepliesViewModel(val replySection: ReplySectionType, val rootReplyID: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<SubRepliesUiState>(SubRepliesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var pageNumber: Int = 1

    var canLoadMore: Boolean = false
    var isLoading: Boolean = false

    init {
        loadSubReplies()
    }

    fun updateReply(reply: ReplyItem) {
        _uiState.update { oldState ->
            if (oldState is SubRepliesUiState.Success) {
                oldState.copy(
                    subReplies = oldState.subReplies.map { if (it.rpid == reply.rpid) reply else it }
                )
            } else {
                oldState
            }
        }
    }

    suspend fun fetchSubReplies(): SubReplyResponse =
        fetchSubRepliesWith(
            replySection = replySection,
            rootReplyID = rootReplyID,
            pageNumber = pageNumber,
            pageSize = 20
        )

    fun loadSubReplies() {
        viewModelScope.launch {
            _uiState.value = SubRepliesUiState.Loading
            try {
                val result = fetchSubReplies()
                if (result.isSuccess) {
                    _uiState.value =
                        SubRepliesUiState.Success(result.data.rootReply, result.data.subReplies)
                    canLoadMore = result.data.hasMore
                } else {
                    _uiState.value = SubRepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.value = SubRepliesUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun loadMoreSubReplies() {
        if (isLoading || !canLoadMore) return
        isLoading = true
        pageNumber++

        viewModelScope.launch {
            try {
                val result = fetchSubReplies()
                if (result.isSuccess) {
                    _uiState.update { oldState ->
                        (oldState as SubRepliesUiState.Success).copy(
                            subReplies = (oldState.subReplies + result.data.subReplies).distinctBy { it.rpid })
                    }
                    canLoadMore = result.data.hasMore
                } else {
                    _uiState.value = SubRepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.value = SubRepliesUiState.Error(e.message ?: "其他网络错误")
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshSubReplies() {
        pageNumber = 1
        loadSubReplies()
    }
}