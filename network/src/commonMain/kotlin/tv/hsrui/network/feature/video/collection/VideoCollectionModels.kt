package tv.hsrui.network.feature.video.collection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class VideoCollectionSummaryData(
    @SerialName("season_id") val seasonId: Long = 0,
    val mid: Long = 0,
    @SerialName("title") private val rawTitle: String = "",
    @SerialName("name") private val name: String = "",
    @SerialName("cover") private val cover: String = "",
    val description: String = "",
    val total: Int = 0,
) {
    val title get() = rawTitle.ifBlank { name.removePrefix("合集·") }
    val coverUrl get() = cover.toHttpsUrl()
}

@Serializable
data class VideoCollectionData(
    @SerialName("id") val seasonId: Long = 0,
    val title: String = "",
    @SerialName("intro") val description: String = "",
    @SerialName("ep_count") val total: Int = 0,
    val sections: List<VideoCollectionSectionData> = emptyList(),
)

@Serializable
data class VideoCollectionSectionData(
    @SerialName("id") val sectionId: Long = 0,
    val title: String = "",
    val episodes: List<VideoCollectionEpisodeData> = emptyList(),
)

@Serializable
data class VideoCollectionEpisodeData(
    @SerialName("id") val episodeId: Long = 0,
    @SerialName("aid") val avid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    @SerialName("arc") private val archive: VideoCard = VideoCard(),
) {
    val key get() = if (episodeId > 0) episodeId.toString() else "$avid:$cid"
    val isAvailable get() = avid > 0 && cid > 0
    val displayTitle get() = title.ifBlank { archive.title }
    val videoCard get() = archive.copy(avid = avid, bvid = bvid, title = displayTitle)
}

@Serializable
data class VideoCollectionVideosResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: VideoCollectionVideosData? = null,
) {
    val isSuccess get() = code == 0 && data?.meta != null && data.archives != null && data.page?.total != null
    val collection get() = data?.meta
    val videos get() = data?.archives.orEmpty()
    val hasMore get() = data?.page?.let { it.pageNumber.toLong() * it.pageSize < (it.total ?: 0) } == true
}

@Serializable
data class VideoCollectionVideosData(
    val meta: VideoCollectionSummaryData? = null,
    val archives: List<VideoCard>? = null,
    val page: VideoCollectionPageData? = null,
)

@Serializable
data class VideoCollectionPageData(
    @SerialName("page_num") val pageNumber: Int = 1,
    @SerialName("page_size") val pageSize: Int = 30,
    val total: Int? = null,
)
