package tv.hsrui.network.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import tv.hsrui.network.feature.utils.toHttpsUrl
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDuration

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VideoCard(
    @SerialName("aid") val avid: Long = 0,
    @SerialName("bvid") val bvid: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("pubdate") val publishDate: Long = 0,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("pic") @JsonNames("cover") private val pic: String = "",
    @SerialName("pic_4_3") private val pic43: String = "",
    @SerialName("stat") private val stat: Stat = Stat(),
    @SerialName("owner") @JsonNames("author") private val owner: Owner = Owner(),

    private val _publishDateString: String = "",
    private val _durationString: String = ""
) {

    val viewCount by stat::view
    val danmakuCount by stat::danmaku
    val likeCount by stat::like
    val coinCount by stat::coin
    val favoriteCount by stat::favorite
    val replyCount by stat::reply
    val upName by owner::name
    val upAvatarUrl by lazy { owner.face.toHttpsUrl() }
    val coverUrl by lazy { pic.toHttpsUrl() }
    val coverUrl43 by lazy { pic43.toHttpsUrl().ifEmpty { pic.toHttpsUrl() } }

    val publishDateString by lazy { _publishDateString.ifEmpty { publishDate.formatToDateTime() } }
    val durationString by lazy { _durationString.ifEmpty { duration.formatToDuration() } }
    @Serializable
    data class Stat(
        val view: Int = -1,
        val like: Int = -1,
        val coin: Int = -1,
        val favorite: Int = -1,
        val danmaku: Int = -1,
        val reply: Int = -1
    )

    @Serializable
    data class Owner(
        val mid: Long = 0,
        val name: String = "",
        val face: String = ""
    )
}

val VideoCardExample = VideoCard(
    avid = 115327790751441,
    bvid = "BV1gDxEzHE8Z",
    pic = "http://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
    title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
    publishDate = 1759762729,
    stat = VideoCard.Stat(view = 1919810, like = 114514),
    duration = 10000,
    owner = VideoCard.Owner(
        name = "东洋雪莲",
        face = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg"
    )
)