package tv.hsrui.network.feature.recommend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideoCard.Stat

@Serializable
data class RawRecommendResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawRecommendData? = null
)

@Serializable
data class RawRecommendData(
    @SerialName("item") val items: List<RawRecommendItem> = emptyList()
)

@Serializable
data class RawRecommendItem(
    @SerialName("goto") val goto: String,
    @SerialName("id") val id: Long,
    @SerialName("bvid") val bvid: String,
    @SerialName("title") val title: String,
    @SerialName("pubdate") val publishDate: Long,
    @SerialName("duration") val duration: Int,
    @SerialName("pic") val pic: String,
    @SerialName("pic_4_3") val pic43: String? = null,
    val stat: RawStat? = null,
    val owner: RawOwner
)

@Serializable
data class RawOwner(
    val name: String,
    val face: String
)

@Serializable
data class RawStat(
    val view: Int = -1,
    val like: Int = -1,
    val coin: Int = -1,
    val favorite: Int = -1,
    val danmaku: Int = -1,
    val reply: Int = -1
)

fun RawRecommendItem.toVideoCard(): VideoCard {
    return VideoCard(
        avid = id,
        bvid = bvid,
        title = title,
        description = "主页推荐Api无简介",
        publishDate = publishDate,
        _duration = duration,
        pic = pic,
        pic43 = if (pic43 != "" ) { pic43 } else null,
        stat = Stat(
            view = stat?.view ?: -1,
            like = stat?.like ?: -1,
            coin = stat?.coin ?: -1,
            favorite = stat?.favorite ?: -1,
            danmaku = stat?.danmaku ?: -1,
            reply = stat?.reply ?: -1
        ),
        owner = VideoCard.Owner(
            name = owner.name,
            face = owner.face
        )
    )
}