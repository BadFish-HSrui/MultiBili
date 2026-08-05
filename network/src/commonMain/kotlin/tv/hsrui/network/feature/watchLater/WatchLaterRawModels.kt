package tv.hsrui.network.feature.watchLater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.feature.history.HistoryVideoCard
import tv.hsrui.network.model.Owner
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class WatchLaterRawResponse(
    val code: Int = -1,
    val message: String = "",
    val data: WatchLaterData = WatchLaterData()
) {
    val isSuccess get() = (code == 0)

    @Serializable
    data class WatchLaterData(
        val count: Int = 0,
        val list: List<WatchLaterRawItem> = emptyList()
    )

    @Serializable
    data class WatchLaterRawItem(
        @SerialName("aid") val avid: Long = 0,
        @SerialName("bvid") val bvid: String = "",
        @SerialName("title") val title: String = "",
        @SerialName("pic") private val _cover: String = "",
        @SerialName("owner") private val _owner: Owner = Owner(),
        @SerialName("add_at") val addTime: Long = 0,
        @SerialName("progress") val watchProgress: Int = 0,
        @SerialName("duration") val duration: Int = 0,
    ) {
        val upName by _owner::name
        val upAvatarUrl by lazy { _owner.face.toHttpsUrl() }
        val upMid by _owner::mid
        val coverUrl by lazy { _cover.toHttpsUrl() }
    }
}

fun WatchLaterRawResponse.WatchLaterRawItem.toHistoryVideoCard(): HistoryVideoCard {
    return HistoryVideoCard(
        avid = avid,
        bvid = bvid,
        title = title,
        subtitle = "",
        coverUrl = coverUrl,
        upName = upName,
        upAvatarUrl = upAvatarUrl,
        upMid = upMid,
        addTime = addTime,
        watchProgress = watchProgress,
        duration = duration,
        regionString = "",
        typeString = ""
    )
}
