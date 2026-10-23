package tv.hsrui.network.liveFeature.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RawLiveRoomInfoResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawLiveRoomInfoData? = null,
)

@Serializable
internal data class RawLiveRoomInfoData(
    @SerialName("room_info") val roomInfo: RawLiveRoomInfo? = null,
    @SerialName("anchor_info") val anchorInfo: RawLiveAnchorInfo? = null,
)

@Serializable
internal data class RawLiveRoomInfo(
    @SerialName("room_id") val roomId: Long,
    val uid: Long,
    val title: String = "",
    val cover: String = "",
    val keyframe: String = "",
    @SerialName("live_status") val liveStatus: Int,
)

@Serializable
internal data class RawLiveAnchorInfo(
    @SerialName("base_info") val baseInfo: RawLiveAnchorBaseInfo? = null,
)

@Serializable
internal data class RawLiveAnchorBaseInfo(
    @SerialName("uname") val name: String = "",
)
