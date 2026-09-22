package tv.hsrui.bolo.ui.common.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.reply.SubReplyResponse
import tv.hsrui.network.feature.reply.fetchSubRepliesWith

class SubRepliesViewModel(val replySection: ReplySectionType, private val rootReply: ReplyItem) : ViewModel() {
    private val deletedReplyIds = mutableSetOf<Long>()
    private val _uiState = MutableStateFlow<SubRepliesUiState>(SubRepliesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var pageNumber: Int = 1

    var canLoadMore: Boolean = false
    var isLoading: Boolean = false

    init {
        loadSubReplies()
    }

    private fun SubRepliesUiState.Success.withoutDeletedReplies() = copy(
        rootReply = rootReply.withoutDeletedReplies(deletedReplyIds) ?: rootReply,
        subReplies = subReplies.mapNotNull { it.withoutDeletedReplies(deletedReplyIds) },
        isRootDeleted = rootReply.rpid in deletedReplyIds,
    )

    fun removeReply(reply: ReplyItem) {
        deletedReplyIds.add(reply.rpid)
        _uiState.update { state ->
            if (state is SubRepliesUiState.Success) state.withoutDeletedReplies() else state
        }
    }

    fun updateReply(reply: ReplyItem) {
        _uiState.update { oldState ->
            if (oldState is SubRepliesUiState.Success) {
                oldState.copy(
                    rootReply = if (oldState.rootReply.rpid == reply.rpid) reply else oldState.rootReply,
                    subReplies = oldState.subReplies.map { if (it.rpid == reply.rpid) reply else it }
                ).withoutDeletedReplies()
            } else {
                oldState
            }
        }
    }

    suspend fun fetchSubReplies(): SubReplyResponse =
        fetchSubRepliesWith(
            replySection = replySection,
            rootReplyID = rootReply.rpid,
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
                        SubRepliesUiState.Success(result.data.rootReply ?: rootReply, result.data.subReplies)
                            .withoutDeletedReplies()
                    canLoadMore = result.data.hasMore
                } else {
                    _uiState.value = SubRepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
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
                        if (oldState !is SubRepliesUiState.Success) return@update oldState
                        oldState.copy(
                            subReplies = (oldState.subReplies + result.data.subReplies).distinctBy { it.rpid }
                        ).withoutDeletedReplies()
                    }
                    canLoadMore = result.data.hasMore
                } else {
                    _uiState.value = SubRepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
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
