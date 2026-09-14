package tv.hsrui.network.feature.reply

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RepliesData = RepliesData()
) {
    val isSuccess get() = (code == 0)
}

@Serializable
data class RepliesData(
    @SerialName("cursor") private val _cursor: ReplyCursor = ReplyCursor(),
    @SerialName("top_replies") private val _topReplies: List<ReplyItem>? = null,
    @SerialName("replies") private val _replies: List<ReplyItem>? = null,
    @SerialName("control") private val _inputControl: ReplyInputControl = ReplyInputControl()
) {
    val topReply get() = _topReplies?.firstOrNull()

    val hasMore get() = !_cursor.isEnd
    val totalReplyCount: Long? get() = _cursor.totalReplyCount?.takeIf { it >= 0L }
    val loadParamsString by _cursor.paginationReply::loadParamsString

    val canReply: Boolean get() = (_replies != null)
    val replies: List<ReplyItem> get() = _replies ?: emptyList()

    val replyLabelText by _inputControl::replyLabelText

    @Serializable
    data class ReplyCursor(
        @SerialName("all_count") val totalReplyCount: Long? = null,
        @SerialName("is_end") val isEnd: Boolean = true,
        @SerialName("pagination_reply") val paginationReply: PaginationReply = PaginationReply()
    ) {
        @Serializable
        data class PaginationReply(
            @SerialName("next_offset") val loadParamsString: String = ""
        )
    }

    @Serializable
    data class ReplyInputControl(
        @SerialName("root_input_text") val replyLabelText: String = ""
    )
}