package tv.hsrui.network.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import tv.hsrui.network.utils.toHttpsUrl
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
    @SerialName("pic") @JsonNames("cover") private val _cover: String = "",
    @SerialName("pic_4_3") private val _cover43: String = "",
    @SerialName("stat") private val _stat: Stat = Stat(),
    @SerialName("owner") @JsonNames("author") private val _owner: Owner = Owner(),

    private val _publishDateString: String = "",
    private val _durationString: String = ""
) {

    val viewCount by _stat::view
    val danmakuCount by _stat::danmaku
    val likeCount by _stat::like
    val replyCount by _stat::reply
    val upMid by _owner::mid
    val upName by _owner::name
    val upAvatarUrl by lazy { _owner.face.toHttpsUrl() }
    val coverUrl by lazy { _cover.toHttpsUrl() }
    val coverUrl43 by lazy { _cover43.toHttpsUrl().ifEmpty { _cover.toHttpsUrl() } }

    val publishDateString by lazy { _publishDateString.ifEmpty { publishDate.formatToDateTime() } }
    val durationString by lazy { _durationString.ifEmpty { duration.formatToDuration() } }
    @Serializable
    data class Stat(
        val view: Int = -1,
        val like: Int = -1,
        val danmaku: Int = -1,
        val reply: Int = -1
    )

}

val VideoCardExample = VideoCard(
    avid = 115327790751441,
    bvid = "BV1gDxEzHE8Z",
    _cover = "http://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
    title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
    publishDate = 1759762729,
    _stat = VideoCard.Stat(view = 1919810, like = 114514, danmaku = 512),
    duration = 10000,
    _owner = Owner(
        name = "东洋雪莲",
        face = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg"
    )
)