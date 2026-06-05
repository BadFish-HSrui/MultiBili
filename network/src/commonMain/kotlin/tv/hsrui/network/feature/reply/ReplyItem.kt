package tv.hsrui.network.feature.reply

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.feature.reply.actions.ReplyLikeState
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class ReplyItem(
    @SerialName("rpid") val rpid: Long = 0,
    @SerialName("root") val rootRpid: Long = 0,
    @SerialName("oid") val oid: Long = 0,
    @SerialName("type") val typeCode: Int = 0,
    @SerialName("mid") val userMid: Long = 0,
    @SerialName("rcount") val replyCount: Int = 0,
    @SerialName("state") private val _stateCode: Int = 0,
    @SerialName("fansgrade") private val _fansCode: Int = 0,
    @SerialName("ctime") private val _replyDate: Long = 0,
    @SerialName("like") val likeCount: Int = -1,
    @SerialName("action") val actionCode: Int = 0,
    @SerialName("member") private val _userInfo: ReplyUserInfo = ReplyUserInfo(),
    @SerialName("content") val content: ReplyContent = ReplyContent(),
    @SerialName("replies") val previewReplies: List<ReplyItem>? = null,
    @SerialName("up_action") private val _upAction: UpAction = UpAction()
) {
    val isHide: Boolean get() = (_stateCode == 17)
    val isFans: Boolean get() = (_fansCode == 1)
    val replyDateString by lazy { _replyDate.formatToDateTime() }
    val likeState get() = when(actionCode) {
        1 -> ReplyLikeState.Like
        2 -> ReplyLikeState.Dislike
        else -> ReplyLikeState.Normal
    }

    val userName by _userInfo::userName
    val userAvatarUrl by lazy { _userInfo.userAvatar.toHttpsUrl() }
    val userLevel by _userInfo.userLevel::level
    val isFollowing by _userInfo::isFollowing
    val isFollowed by _userInfo::isFollowed

    val isUpLiked by _upAction::isUpLiked
    val isUpReplied by _upAction::isUpReplied

    @Serializable
    data class ReplyUserInfo(
        @SerialName("uname") val userName: String = "",
        @SerialName("avatar") val userAvatar: String = "",
        @SerialName("level_info") val userLevel: ReplyUserLevel = ReplyUserLevel(),
        @SerialName("following") val isFollowing: Int = 0,
        @SerialName("is_followed") val isFollowed: Int = 0
    ) {
        @Serializable
        data class ReplyUserLevel(
            @SerialName("current_level") val level: Int = 0
        )
    }

    @Serializable
    data class UpAction(
        @SerialName("like") val isUpLiked: Boolean = false,
        @SerialName("reply") val isUpReplied: Boolean = false
    )

    @Serializable
    data class ReplyContent(
        @SerialName("message") val text: String = "",
        @SerialName("emote") val emote: Map<String, EmoteItem> = emptyMap(),
        @SerialName("jump_url") val jump: Map<String, JumpUrlItem> = emptyMap()
    ) {
        val hasExternalLink get() = jumpAppsName.isNotEmpty()

        val jumpAppsName: List<String> =
            jump.values.map { it.jumpAppName }.filter { it.isNotEmpty() }.distinct()

        @Serializable
        data class EmoteItem(
            @SerialName("text") val text: String,
            @SerialName("url") private val _url: String = "",
            @SerialName("meta") private val _metadata: EmoteMetadata = EmoteMetadata()
        ) {
            val url by lazy { _url.toHttpsUrl() }
            val isBig get() = (_metadata.size == 2)

            @Serializable
            data class EmoteMetadata(
                val size: Int = 1,
            )
        }

        @Serializable
        data class JumpUrlItem(
            @SerialName("title") val title: String = "",
            @SerialName("prefix_icon") private val _icon: String,
            @SerialName("app_url_schema") val jumpAppUrl: String = "",
            @SerialName("app_name") val jumpAppName: String = "",
        ) {
            val iconUrl get() = (_icon.toHttpsUrl())
        }
    }
}

