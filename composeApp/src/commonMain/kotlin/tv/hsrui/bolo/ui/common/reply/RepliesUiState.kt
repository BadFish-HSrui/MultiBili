package tv.hsrui.bolo.ui.common.reply

import tv.hsrui.network.feature.reply.ReplyItem

sealed class RepliesUiState {
    data object Loading : RepliesUiState()
    data class Success(
        val topReply: ReplyItem?,
        val replies: List<ReplyItem>,
        val totalReplyCount: Long? = null,
        val upMid: Long = 0,
        val isAssist: Boolean = false,
        val permissionUserMid: Long = 0,
    ) : RepliesUiState()
    data class Error(val message: String) : RepliesUiState()
}
