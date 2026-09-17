package tv.hsrui.network.feature.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.MediaCard
import tv.hsrui.network.model.Owner
import tv.hsrui.network.model.VideoCard

@Serializable
data class RawSearchVideosResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawSearchVideosData? = null
)

@Serializable
data class RawSearchVideosData(
    val page: Int = 0,
    val numResults: Int? = null,
    @SerialName("numPages") val pageCount: Int = -1,
    @SerialName("result") val videos: List<RawSearchVideoItem>? = null
)

@Serializable
data class RawSearchVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val author: String = "",
    val title: String = "",
    val pic: String = "",
    val play: Int = -1,
    @SerialName("video_review") val danmaku: Int = -1,
    @SerialName("review") val replies: Int = -1,
    @SerialName("pubdate") val publishDate: Long = 0,
    val duration: String = ""
)

fun RawSearchVideoItem.toVideoCard(): VideoCard {
    return VideoCard(
        avid = aid,
        bvid = bvid,
        title = title
            .replace("<em class=\"keyword\">", "")
            .replace("</em>", ""),
        publishDate = publishDate,
        duration = duration.toDurationSeconds(),
        _cover = pic,
        _stat = VideoCard.Stat(
            view = play,
            like = -1,
            danmaku = danmaku,
            reply = replies
        ),
        _owner = Owner(name = author)
    )
}

private fun String.toDurationSeconds(): Int {
    val parts = split(':').mapNotNull(String::toIntOrNull)
    if (parts.size !in 2..3) return 0

    return parts.fold(0) { total, part -> total * 60 + part }
}

@Serializable
data class RawSearchMediaResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawSearchMediaData? = null,
)

@Serializable
data class RawSearchMediaData(
    val page: Int = 0,
    val numResults: Int? = null,
    @SerialName("numPages") val pageCount: Int = -1,
    @SerialName("result") val media: List<RawSearchMediaItem>? = null,
)

@Serializable
data class RawSearchMediaItem(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val styles: String = "",
    @SerialName("angle_title") val badge: String = "",
    @SerialName("media_score") val score: RawSearchMediaScore? = null,
    @SerialName("index_show") val progressText: String = "",
)

@Serializable
data class RawSearchMediaScore(val score: Double = 0.0)

fun RawSearchMediaItem.toMediaCard(): MediaCard = MediaCard(
    seasonId = seasonId,
    title = title.replace("<em class=\"keyword\">", "").replace("</em>", ""),
    cover = cover,
    subtitle = styles,
    badge = badge,
    score = score?.score?.toString().orEmpty(),
    progressText = progressText,
)

@Serializable
data class RawSearchUsersResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawSearchUsersData? = null,
)

@Serializable
data class RawSearchUsersData(
    val page: Int = 0,
    val numResults: Int? = null,
    @SerialName("numPages") val pageCount: Int = -1,
    @SerialName("result") val users: List<SearchUserData>? = null,
)
