package tv.hsrui.network.feature.video.list

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VideoListType(val value: Int) { Uploads(1), Favorite(3), Series(5) }

@Serializable
enum class VideoListSort(val value: Int) { Default(1), MostPlayed(2), MostFavorited(3) }

@Serializable
data class VideoListInfoResponse(
    val code: Int = -1,
    val message: String = "",
    val data: VideoListInfoData? = null,
) {
    val isSuccess get() = code == 0 && data != null
}

@Serializable
data class VideoListInfoData(
    val id: Long = 0,
    val mid: Long = 0,
    val title: String = "",
)

@Serializable
data class VideoListVideosResponse(
    val code: Int = -1,
    val message: String = "",
    val data: VideoListVideosData? = null,
) {
    val isSuccess get() = code == 0 && data?.hasMore != null && (data.total ?: -1) >= 0
}

@Serializable
data class VideoListVideosData(
    @SerialName("media_list") private val mediaList: List<VideoListItemData>? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
    @SerialName("total_count") val total: Int? = null,
) {
    val items get() = mediaList.orEmpty()
}

@Serializable
data class VideoListItemData(
    val id: Long = 0,
    private val type: Int = 0,
    val title: String = "",
    private val attr: Long? = null,
) {
    val key get() = "$type:$id"
    val cursorType get() = type
    val isVideo get() = type == 2
    val isAvailable get() = id > 0 && isVideo && attr?.let { it and 1L == 0L } == true
    val unavailableMessage get() = if (!isVideo) "暂不支持" else "不可播放"
}
