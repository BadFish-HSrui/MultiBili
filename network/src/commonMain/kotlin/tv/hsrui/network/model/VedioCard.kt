package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoCard(
    @SerialName("aid") val avid: Long,
    @SerialName("bvid") val bvid: String,
    @SerialName("title") val title: String,
    @SerialName("desc") val description: String,
    @SerialName("pubdate") val publishDate: Long,
    @SerialName("duration") private val _duration: Int,
    @SerialName("pic") private val pic: String,
    @SerialName("pic_4_3") private val pic43: String? = null,
    @SerialName("stat") private val stat: Stat,
    @SerialName("owner") private val owner: Owner
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
    val coverUrl43 by lazy { pic43?.toHttpsUrl() ?: pic.toHttpsUrl() }
    val duration
        get() = if (_duration < 3600) { "${_duration / 60}:${_duration % 60}" }
            else { "${_duration / 3600}:${_duration % 3600 / 60}:${_duration % 60}" }


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
        val name: String,
        val face: String
    )
}

private fun String.toHttpsUrl(): String = when {
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}

val VideoCardExample = VideoCard(
    avid = 115327790751441,
    bvid = "BV1gDxEzHE8Z",
    pic = "http://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
    title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
    description = "原曲：青衣DJ\n人声：AI东雪莲\n图/动态图/音频：\npan.quark.cn/s/5d94a5c96ba9\n本身想跑花旦风格的，但是发现这个底模跑不出好看的\n做了22张动图，没用上的图和动图放网盘里了\n中秋快乐！\n这几天感冒严重，打火机日语完整版过几天做完",
    publishDate = 1759762729,
    stat = VideoCard.Stat(view = 1919810, like = 114514),
    _duration = 10000,
    owner = VideoCard.Owner(
        name = "东洋雪莲",
        face = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg"
    ))