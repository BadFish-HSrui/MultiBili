package tv.hsrui.network.feature.reply

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubReplyResponse(
    val code: Int = -1,
    val message: String = "",
    val data: SubRepliesData = SubRepliesData()
) {
    val isSuccess get() = (code == 0)
}

@Serializable
data class SubRepliesData(
    @SerialName("root") val rootReply: ReplyItem = ReplyItem(),
    @SerialName("replies") val subReplies: List<ReplyItem> = emptyList(),
    @SerialName("control") private val _inputControl: SubReplyInputControl = SubReplyInputControl(),
    @SerialName("page") private val _page: SubReplyPage = SubReplyPage()
) {
    val hasMore get() = ((_page.num * _page.size) < _page.count)

    val replyLabelText by _inputControl::replyLabelText

    @Serializable
    data class SubReplyInputControl(
        @SerialName("child_input_text") val replyLabelText: String = ""
    )

    @Serializable
    data class SubReplyPage(
        val count: Int = 0,
        val num: Int = 1,
        val size: Int = 20
    )
}