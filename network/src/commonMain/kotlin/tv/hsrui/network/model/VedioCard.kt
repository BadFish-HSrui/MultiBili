package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class VideoCard(
    @SerialName("aid") val avid: Long,
    @SerialName("bvid") val bvid: String,
    @SerialName("title") val title: String,
    @SerialName("desc") val description: String,
    @SerialName("pubdate") val publishDate: Long,
    @SerialName("duration") private val _duration: Int,
    @SerialName("pic") private val pic: String,
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