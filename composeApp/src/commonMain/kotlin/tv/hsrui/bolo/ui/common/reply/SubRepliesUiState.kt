package tv.hsrui.bolo.ui.common.reply

import tv.hsrui.network.feature.reply.ReplyItem

sealed class SubRepliesUiState {
    data object Loading : SubRepliesUiState()
    data class Success(
        val rootReply: ReplyItem,
        val subReplies: List<ReplyItem>,
        val isRootDeleted: Boolean = false,
    ) : SubRepliesUiState()
    data class Error(val message: String) : SubRepliesUiState()
}
