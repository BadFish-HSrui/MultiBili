package tv.hsrui.network.feature.user.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.LiveCard

@Serializable
data class UserInfoResponse(
    val code: Int = -1,
    val message: String = "",
    val data: UserInfoData? = null,
) {
    val isSuccess get() = code == 0 && data != null && data.mid > 0
}

@Serializable
data class UserInfoData(
    val mid: Long = -1,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    @SerialName("level") private val levelValue: Int = 0,
    @SerialName("is_senior_member") private val isSeniorMember: Int = 0,
    private val vip: UserVipData = UserVipData(),
    @SerialName("live_room") private val liveRoom: UserLiveRoomData? = null,
) {
    val level get() = if (isSeniorMember == 1) 7 else levelValue
    val levelString get() = if (isSeniorMember == 1) "Lv.6+" else "Lv.$levelValue"
    val isVip get() = vip.isVip
    val vipTypeString get() = vip.typeString
    val isLive get() = liveRoom?.isLive == true
    val liveRoomId get() = liveRoom?.liveRoomId
    val liveRoomCard: LiveCard?
        get() {
            val room = liveRoom?.takeIf { it.hasRoom } ?: return null
            val roomId = room.liveRoomId ?: return null
            return LiveCard(
                roomId = roomId,
                title = room.title,
                userCover = room.coverUrl,
                upMid = mid,
                upName = name,
                avatar = face,
                liveStatus = if (room.isLive) 1 else 0,
                popularity = -1,
                watchedShow = LiveCard.WatchedShow(count = -1),
            )
        }
}

@Serializable
data class UserLiveRoomData(
    @SerialName("liveStatus") private val statusCode: Int? = null,
    @SerialName("roomid") private val roomId: Long? = null,
    @SerialName("roomStatus") private val roomStatusCode: Int? = null,
    val title: String = "",
    @SerialName("cover") val coverUrl: String = "",
) {
    val hasRoom get() = roomStatusCode == 1
    val isLive get() = statusCode == 1
    val liveRoomId get() = roomId?.takeIf { it > 0 }
}

@Serializable
data class UserVipData(
    @SerialName("type") private val typeCode: Int = 0,
    @SerialName("status") private val statusCode: Int = 0,
) {
    val isVip get() = statusCode == 1
    val typeString get() = when (typeCode) {
        1 -> "大会员"
        2 -> "年度大会员"
        else -> ""
    }
}

@Serializable
data class UserUpStatResponse(
    val code: Int = -1,
    val message: String = "",
    val data: UserUpStatData? = null,
) {
    val isSuccess get() = code == 0 && data != null
}

@Serializable
data class UserUpStatData(
    @SerialName("likes") val likeCount: Long? = null,
    private val archive: UserArchiveStatData? = null,
) {
    val playCount get() = archive?.playCount
}

@Serializable
data class UserArchiveStatData(
    @SerialName("view") val playCount: Long? = null,
)

@Serializable
data class UserCardStatsResponse(
    val code: Int = -1,
    val data: UserCardStatsData? = null,
) {
    val isSuccess get() = code == 0 && data != null
}

@Serializable
data class UserCardStatsData(
    @SerialName("follower") private val rawFollowerCount: Long? = null,
    @SerialName("archive_count") private val rawUploadCount: Long? = null,
) {
    val followerCount get() = rawFollowerCount?.takeIf { it >= 0 }
    val uploadCount get() = rawUploadCount?.takeIf { it >= 0 }
}
