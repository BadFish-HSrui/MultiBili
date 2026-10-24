package tv.hsrui.network.feature.search

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import tv.hsrui.network.utils.toHttpsUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.LiveCard
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
    val mid: Long = 0,
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
        _owner = Owner(mid = mid, name = author)
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


@Serializable
data class RawSearchLiveRoomsResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawSearchLiveRoomsData? = null,
)

@Serializable
data class RawSearchLiveRoomsData(
    val page: Int = 0,
    val numResults: Int? = null,
    @SerialName("numPages") val pageCount: Int = -1,
    @SerialName("result") val rooms: List<RawSearchLiveRoomItem>? = null,
)

@Serializable
data class RawSearchLiveRoomItem(
    @SerialName("roomid") val roomId: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val uface: String = "",
    val title: String = "",
    @SerialName("user_cover") val userCover: String = "",
    val cover: String = "",
    @SerialName("cate_name") val categoryName: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    val online: Long? = null,
    @SerialName("watched_show") val watchedShow: LiveCard.WatchedShow? = null,
)

fun RawSearchLiveRoomItem.toLiveCard(): LiveCard = LiveCard(
    roomId = roomId,
    title = title.replace("<em class=\"keyword\">", "").replace("</em>", ""),
    userCover = userCover,
    systemCover = cover,
    upMid = uid,
    upName = uname.replace("<em class=\"keyword\">", "").replace("</em>", ""),
    avatar = uface,
    areaName = categoryName.replace("<em class=\"keyword\">", "").replace("</em>", ""),
    liveStatus = liveStatus,
    popularity = online ?: -1,
    watchedShow = watchedShow ?: LiveCard.WatchedShow(count = -1),
)

@Serializable
data class RawSearchLiveUsersResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawSearchLiveUsersData? = null,
)

@Serializable
data class RawSearchLiveUsersData(
    val page: Int = 0,
    val numResults: Int? = null,
    @SerialName("numPages") val pageCount: Int = -1,
    @SerialName("result") val users: List<RawSearchLiveUserItem>? = null,
)

@Serializable
data class RawSearchLiveUserItem(
    val uid: Long = 0,
    val uname: String = "",
    val uface: String = "",
    @SerialName("roomid") val roomId: Long = 0,
    val attentions: Long? = null,
    @SerialName("cate_name") val categoryName: String = "",
    @SerialName("live_status") val liveStatus: Int? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("live_time") val liveTime: String? = null,
)

fun RawSearchLiveUserItem.toSearchLiveUserData(): SearchLiveUserData = SearchLiveUserData(
    mid = uid,
    name = uname.replace("<em class=\"keyword\">", "").replace("</em>", ""),
    avatarUrl = uface.toHttpsUrl(),
    roomId = roomId,
    followerCount = attentions?.takeIf { it >= 0 },
    areaName = categoryName.replace("<em class=\"keyword\">", "").replace("</em>", "").trim(),
    isLive = liveStatus?.let { it == 1 } ?: isLive,
    liveStartTime = try {
        liveTime?.let {
            LocalDateTime.parse(it.replace(' ', 'T')).toInstant(TimeZone.of("Asia/Shanghai"))
                .epochSeconds.takeIf { seconds -> seconds > 0 }
        }
    } catch (_: IllegalArgumentException) {
        null
    },
)