package tv.hsrui.network.liveFeature.room

import tv.hsrui.network.utils.toHttpsUrl

data class LiveRoomInfoData(
    val roomId: Long,
    val title: String,
    val upMid: Long,
    val upName: String,
    val coverUrl: String,
    val isLive: Boolean,
    val liveStartTimeEpochSeconds: Long? = null,
)

internal fun RawLiveRoomInfoResponse.toLiveRoomInfo(): LiveRoomInfoData {
    check(code == 0) { "直播间信息获取失败（$code）：$message" }
    val info = checkNotNull(data) { "直播间信息为空" }
    val room = checkNotNull(info.roomInfo) { "直播间信息为空" }
    val anchor = checkNotNull(info.anchorInfo?.baseInfo) { "主播信息为空" }
    check(room.roomId > 0 && room.uid > 0 && room.title.isNotBlank() && anchor.name.isNotBlank()) {
        "直播间信息不完整"
    }
    return LiveRoomInfoData(
        roomId = room.roomId,
        title = room.title,
        upMid = room.uid,
        upName = anchor.name,
        coverUrl = room.cover.ifBlank { room.keyframe }.trim().toHttpsUrl(),
        isLive = room.liveStatus == 1,
        liveStartTimeEpochSeconds = room.liveStartTimeEpochSeconds?.takeIf { it > 0 },
    )
}
