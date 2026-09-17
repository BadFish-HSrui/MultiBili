package tv.hsrui.network.feature.video.series

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class VideoSeriesData(
    @SerialName("series_id") val seriesId: Long = 0,
    val mid: Long = 0,
    @SerialName("name") val title: String = "",
    val description: String = "",
    val total: Int = 0,
    private val cover: String = "",
) {
    val coverUrl get() = cover.toHttpsUrl()
}

@Serializable
data class VideoSeriesResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: VideoSeriesInfoData? = null,
) {
    val isSuccess get() = code == 0 && data?.meta != null
    val series get() = data?.meta
}

@Serializable
data class VideoSeriesInfoData(val meta: VideoSeriesData? = null)

@Serializable
data class VideoSeriesVideosResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: VideoSeriesVideosData? = null,
) {
    val isSuccess get() = code == 0 && data?.archives != null && data.page?.let {
        it.pageNumber > 0 && it.pageSize > 0 && (it.total ?: -1) >= 0
    } == true
    val videos get() = data?.archives.orEmpty()
    val pageNumber get() = data?.page?.pageNumber ?: 0
    val total get() = data?.page?.total
    val hasMore get() = data?.page?.let { it.pageNumber.toLong() * it.pageSize < (it.total ?: 0) } == true
}

@Serializable
data class VideoSeriesVideosData(
    val archives: List<VideoCard>? = null,
    val page: VideoSeriesPageData? = null,
)

@Serializable
data class VideoSeriesPageData(
    @SerialName("num") val pageNumber: Int = 0,
    @SerialName("size") val pageSize: Int = 0,
    val total: Int? = null,
)
