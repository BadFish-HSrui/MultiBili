package tv.hsrui.network.feature.reply

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyResponse(
    val code: Int = -1,
    val message: String = "",
    val data: ReplyData = ReplyData()
) {
    val isSuccess get() = (code == 0)
}

@Serializable
data class ReplyData(
    @SerialName("page") private val _page: ReplyPage = ReplyPage(),
    @SerialName("upper") private val _upper: ReplyUpper = ReplyUpper(),
    @SerialName("replies") private val _replies: List<ReplyItem>? = null
) {
    val rootReplyCount by _page::rootCount
    val allReplyCount by _page::allCount

    val hasTopReply: Boolean get() = (_upper.top != null)
    val topReply: ReplyItem get() = _upper.top ?: ReplyItem()

    val canReply: Boolean get() = (_replies != null)
    val replies: List<ReplyItem> get() = _replies ?: emptyList()

    @Serializable
    data class ReplyPage(
        @SerialName("num") val pn: Int = 1,
        @SerialName("size") val ps: Int = 20,
        @SerialName("rootCount") val rootCount: Int = 0,
        @SerialName("acount") val allCount: Int = 0
    )

    @Serializable
    data class ReplyUpper(
        val top: ReplyItem? = null
    )
}