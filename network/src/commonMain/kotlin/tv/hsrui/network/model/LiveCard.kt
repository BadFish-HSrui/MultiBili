package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class LiveCard(
    @SerialName("roomid") val roomId: Long = 0,
    val title: String = "",
    @SerialName("user_cover") private val userCover: String = "",
    @SerialName("system_cover") private val systemCover: String = "",
    @SerialName("cover_source") private val coverSource: Int = 0,
    @SerialName("uid") val upMid: Long = 0,
    @SerialName("uname") val upName: String = "",
    @SerialName("face") private val avatar: String = "",
    @SerialName("area_v2_id") val areaId: Int = 0,
    @SerialName("area_v2_name") val areaName: String = "",
    @SerialName("area_v2_parent_id") val parentAreaId: Int = 0,
    @SerialName("area_v2_parent_name") val parentAreaName: String = "",
    @SerialName("online") val popularity: Long = 0,
    @SerialName("watched_show") private val watchedShow: WatchedShow = WatchedShow(),
) {
    val coverUrl: String
        get() = (if (coverSource == 1) systemCover.ifEmpty { userCover } else userCover.ifEmpty { systemCover }).toHttpsUrl()

    val upAvatarUrl: String
        get() = avatar.toHttpsUrl()

    val watchedCount by watchedShow::count

    val areaDisplayName: String
        get() = listOf(parentAreaName, areaName).filter { it.isNotBlank() }.joinToString(" · ")

    @Serializable
    data class WatchedShow(
        @SerialName("num") val count: Long = 0,
    )
}
