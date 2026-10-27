package tv.hsrui.network.liveFeature.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    @SerialName("watched_show") val watchedShow: RawLiveRoomWatchedShow? = null,
    @SerialName("news_info") val newsInfo: RawLiveRoomNewsInfo? = null,
    @SerialName("super_chat_info") val superChatInfo: JsonObject? = null,
)

@Serializable
internal data class RawLiveRoomInfo(
    @SerialName("room_id") val roomId: Long,
    val uid: Long,
    val title: String = "",
    val cover: String = "",
    val keyframe: String = "",
    @SerialName("live_status") val liveStatus: Int,
    @SerialName("live_start_time") val liveStartTimeEpochSeconds: Long? = null,
    val description: String = "",
    @SerialName("online") val popularity: Long? = null,
    @SerialName("area_id") val areaId: Int? = null,
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_id") val parentAreaId: Int? = null,
    @SerialName("parent_area_name") val parentAreaName: String = "",
)

@Serializable
internal data class RawLiveAnchorInfo(
    @SerialName("base_info") val baseInfo: RawLiveAnchorBaseInfo? = null,
    @SerialName("relation_info") val relationInfo: RawLiveAnchorRelationInfo? = null,
)

@Serializable
internal data class RawLiveAnchorBaseInfo(
    @SerialName("uname") val name: String = "",
    @SerialName("face") val avatarUrl: String = "",
)

@Serializable
internal data class RawLiveRoomWatchedShow(
    @SerialName("num") val count: Long? = null,
)

@Serializable
internal data class RawLiveAnchorRelationInfo(
    @SerialName("attention") val followerCount: Long? = null,
)

@Serializable
internal data class RawLiveRoomNewsInfo(
    val content: String = "",
)
