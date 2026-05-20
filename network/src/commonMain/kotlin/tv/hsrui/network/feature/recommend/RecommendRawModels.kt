package tv.hsrui.network.feature.recommend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideoCard.Stat

@Serializable
data class RawRecommendResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawRecommendData = RawRecommendData()
)

@Serializable
data class RawRecommendData(
    @SerialName("item") val items: List<RawRecommendItem> = emptyList()
)

@Serializable
data class RawRecommendItem(
    @SerialName("goto") val goto: String = "",
    @SerialName("id") val id: Long = 0,
    @SerialName("bvid") val bvid: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("pubdate") val publishDate: Long = 0,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("pic") val pic: String = "",
    @SerialName("pic_4_3") val pic43: String = "",
    val stat: RawStat = RawStat(),
    val owner: RawOwner = RawOwner()
)

@Serializable
data class RawOwner(
    val name: String = "",
    val face: String = ""
)

@Serializable
data class RawStat(
    val view: Int = -1,
    val like: Int = -1,
    val danmaku: Int = -1,
    val reply: Int = -1
)

fun RawRecommendItem.toVideoCard(): VideoCard {
    return VideoCard(
        avid = id,
        bvid = bvid,
        title = title,
        publishDate = publishDate,
        duration = duration,
        _cover = pic,
        _cover43 = pic43.ifEmpty { pic },
        _stat = Stat(
            view = stat.view,
            like = stat.like,
            danmaku = stat.danmaku,
            reply = stat.reply
        ),
        _owner = VideoCard.Owner(
            name = owner.name,
            face = owner.face
        )
    )
}