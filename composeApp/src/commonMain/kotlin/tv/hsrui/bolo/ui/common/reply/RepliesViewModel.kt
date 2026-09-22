package tv.hsrui.bolo.ui.common.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplyResponse
import tv.hsrui.network.feature.reply.ReplySort
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.reply.fetchRepliesWith
import tv.hsrui.network.login.storage.LoginStorage

class RepliesViewModel(val replySection: ReplySectionType) : ViewModel() {
    private val loginStorage: LoginStorage = getKoin().get()
    private val deletedReplyIds = mutableSetOf<Long>()
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var requestGeneration = 0L
    private var observedUserMid = currentUserMid()
    private val _uiState = MutableStateFlow<RepliesUiState>(RepliesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _sortType = MutableStateFlow(ReplySort.Popular)
    val sortType = _sortType.asStateFlow()
    var replyLabelText = ""

    var canLoadMore: Boolean = false
    var loadParamsString: String = ""
    var isLoading: Boolean = false

    init {
        loadReplies()
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect { mid ->
                if (mid != observedUserMid) {
                    observedUserMid = mid
                    refreshReplies()
                }
            }
        }
    }

    private fun currentUserMid(): Long =
        if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L

    private fun RepliesUiState.Success.withoutDeletedReplies() = copy(
        topReply = topReply?.withoutDeletedReplies(deletedReplyIds),
        replies = replies.mapNotNull { it.withoutDeletedReplies(deletedReplyIds) },
    )

    fun removeReply(reply: ReplyItem) {
        deletedReplyIds.add(reply.rpid)
        _uiState.update { state ->
            if (state is RepliesUiState.Success) state.withoutDeletedReplies() else state
        }
    }

    fun nextSortType() {
        _sortType.update {
            ReplySort.entries[(it.ordinal + 1) % ReplySort.entries.size]
        }
        refreshReplies()
    }

    fun setSortType(sort: ReplySort) {
        _sortType.value = sort
        refreshReplies()
    }

    fun updateReply(reply: ReplyItem) {
        _uiState.update { oldState ->
            if (oldState is RepliesUiState.Success) {
                oldState.copy(
                    topReply = if (oldState.topReply?.rpid == reply.rpid) reply else oldState.topReply,
                    replies = oldState.replies.map { if (it.rpid == reply.rpid) reply else it }
                ).withoutDeletedReplies()
            } else {
                oldState
            }
        }
    }

    suspend fun fetchReplies(): ReplyResponse =
        fetchRepliesWith(
            replySection = replySection,
            sort = sortType.value,
            loadParamsString = loadParamsString,
        )

    fun loadReplies() {
        val generation = ++requestGeneration
        val userMid = currentUserMid()
        loadJob?.cancel()
        loadMoreJob?.cancel()
        isLoading = false
        canLoadMore = false
        loadJob = viewModelScope.launch {
            _uiState.value = RepliesUiState.Loading
            try {
                val result = fetchReplies()
                if (generation != requestGeneration || userMid != currentUserMid()) return@launch
                if (result.isSuccess) {
                    _uiState.value =
                        RepliesUiState.Success(
                            topReply = result.data.topReply,
                            replies = result.data.replies,
                            totalReplyCount = result.data.totalReplyCount,
                            upMid = result.data.upMid,
                            isAssist = result.data.isAssist,
                            permissionUserMid = userMid,
                        ).withoutDeletedReplies()
                    canLoadMore = result.data.hasMore
                    loadParamsString = result.data.loadParamsString
                    replyLabelText = result.data.replyLabelText
                } else {
                    _uiState.value = RepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration && userMid == currentUserMid()) {
                    _uiState.value = RepliesUiState.Error(e.message ?: "其他网络错误")
                }
            }
        }
    }

    fun loadMoreReplies() {
        if (isLoading || !canLoadMore) return
        isLoading = true

        val generation = requestGeneration
        val userMid = currentUserMid()
        loadMoreJob = viewModelScope.launch {
            try {
                val result = fetchReplies()
                if (generation != requestGeneration || userMid != currentUserMid()) return@launch

                if (result.isSuccess) {
                    _uiState.update { oldState ->
                        if (oldState !is RepliesUiState.Success) return@update oldState
                        oldState.copy(
                            replies = (oldState.replies + result.data.replies).distinctBy { it.rpid },
                            totalReplyCount = result.data.totalReplyCount ?: oldState.totalReplyCount,
                            upMid = result.data.upMid.takeIf { it > 0 } ?: oldState.upMid,
                            isAssist = result.data.isAssist,
                            permissionUserMid = userMid,
                        ).withoutDeletedReplies()
                    }
                    canLoadMore = result.data.hasMore
                    loadParamsString = result.data.loadParamsString
                } else {
                    _uiState.value = RepliesUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration && userMid == currentUserMid()) {
                    _uiState.value = RepliesUiState.Error(e.message ?: "其他网络错误")
                }
            } finally {
                if (generation == requestGeneration) isLoading = false
            }
        }
    }

    fun refreshReplies() {
        loadParamsString = ""
        loadReplies()
    }
}

internal fun ReplyItem.withoutDeletedReplies(deletedReplyIds: Set<Long>): ReplyItem? {
    if (rpid in deletedReplyIds || rootRpid in deletedReplyIds) return null
    val previews = previewReplies?.mapNotNull { it.withoutDeletedReplies(deletedReplyIds) }
    return if (previews == previewReplies) this else copy(previewReplies = previews)
}
