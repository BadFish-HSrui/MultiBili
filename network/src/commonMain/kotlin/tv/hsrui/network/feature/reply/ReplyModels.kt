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
    @SerialName("replies") private val _replies: List<ReplyItem>? = null
) {
    val topReply get() = _topReplies?.firstOrNull()

    val hasMore get() = !_cursor.isEnd
    val loadParamsString by _cursor.paginationReply::loadParamsString

    val canReply: Boolean get() = (_replies != null)
    val replies: List<ReplyItem> get() = _replies ?: emptyList()

    @Serializable
    data class ReplyCursor(
        @SerialName("is_end") val isEnd: Boolean = true,
        @SerialName("pagination_reply") val paginationReply: PaginationReply = PaginationReply()
    ) {
        @Serializable
        data class PaginationReply(
            @SerialName("next_offset") val loadParamsString: String = ""
        )
    }
}