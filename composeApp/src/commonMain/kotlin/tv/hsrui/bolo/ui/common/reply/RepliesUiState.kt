package tv.hsrui.bolo.ui.common.reply

import tv.hsrui.network.feature.reply.ReplyItem

sealed class RepliesUiState {
    data object Loading : RepliesUiState()
    data class Success(
        val topReply: ReplyItem?,
        val replies: List<ReplyItem>,
        val totalReplyCount: Long? = null,
    ) : RepliesUiState()
    data class Error(val message: String) : RepliesUiState()
}