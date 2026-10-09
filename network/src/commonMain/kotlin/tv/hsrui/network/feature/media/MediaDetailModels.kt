package tv.hsrui.network.feature.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.MediaCard
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class MediaSeasonResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    @SerialName("result") val season: MediaSeasonData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && (season?.seasonId ?: 0L) > 0L
    val message: String
        get() = if (code == 0 && !isSuccess) "媒体响应缺少剧集信息" else "[$code] $responseMessage"
}

@Serializable
data class MediaSeasonData(
    @SerialName("season_id") val seasonId: Long,
    @SerialName("type") val seasonType: Int = 0,
    val title: String = "",
    @SerialName("cover") private val cover: String = "",
    @SerialName("evaluate") val description: String = "",
    @SerialName("new_ep") private val update: MediaSeasonUpdate? = null,
    private val rating: MediaRating? = null,
    private val stat: MediaSeasonStatistics = MediaSeasonStatistics(),
    @SerialName("up_info") private val upInfo: MediaUpInfo? = null,
    @SerialName("seasons") private val seasonList: List<MediaSeasonSummary>? = null,
    @SerialName("episodes") private val episodeList: List<MediaEpisode>? = null,
) {
    val coverUrl: String get() = cover.toHttpsUrl()
    val progressText: String get() = update?.description.orEmpty()
    val scoreText: String get() = rating?.scoreText.orEmpty()
    val viewCount by stat::views
    val danmakuCount by stat::danmakus
    val upMid: Long get() = upInfo?.mid ?: 0L
    val episodes: List<MediaEpisode>
        get() = episodeList.orEmpty().filter { !it.isHidden && it.episodeId > 0L }.distinctBy { it.episodeId }
    val seasons: List<MediaSeasonSummary>
        get() {
            val available = seasonList.orEmpty().filter { it.seasonId > 0L }.distinctBy { it.seasonId }
            return if (available.any { it.seasonId == seasonId }) available
            else listOf(MediaSeasonSummary(seasonId, title)) + available
        }
}

@Serializable
data class MediaSeasonSummary(
    @SerialName("season_id") val seasonId: Long,
    @SerialName("season_title") val title: String = "",
)

@Serializable
data class MediaEpisode(
    @SerialName("id") val episodeId: Long,
    @SerialName("aid") val avid: Long = 0L,
    val cid: Long = 0L,
    val bvid: String = "",
    private val title: String = "",
    @SerialName("long_title") private val longTitle: String = "",
    @SerialName("show_title") private val showTitle: String = "",
    val badge: String = "",
    @SerialName("is_view_hide") val isHidden: Boolean = false,
) {
    val displayTitle: String
        get() = showTitle.ifBlank { listOf(title, longTitle).filter(String::isNotBlank).joinToString(" ") }
            .ifBlank { "未命名剧集" }
    val isAvailable: Boolean get() = !isHidden && episodeId > 0L && avid > 0L && cid > 0L
}

@Serializable
data class MediaRating(private val score: Double = 0.0) {
    val scoreText: String get() = if (score > 0.0) "${score}分" else ""
    val scoreValue: String get() = if (score > 0.0) score.toString() else ""
}

@Serializable
data class MediaSeasonUpdate(@SerialName("desc") val description: String = "")

@Serializable
data class MediaSeasonStatistics(val views: Long = 0L, val danmakus: Long = 0L)

@Serializable
data class MediaUpInfo(val mid: Long = 0L)

@Serializable
data class MediaRecommendationsResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    private val data: MediaRecommendationsData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && data != null
    val message: String
        get() = if (code == 0 && data == null) "媒体推荐响应缺少数据" else "[$code] $responseMessage"
    val media: List<MediaCard> get() = data?.media.orEmpty()
}

@Serializable
data class MediaRecommendationsData(
    @SerialName("season") private val seasons: List<MediaRecommendation>? = null,
) {
    val media: List<MediaCard>
        get() = seasons.orEmpty().filter { it.seasonId > 0L }.distinctBy { it.seasonId }.map { it.toMediaCard() }
}

@Serializable
data class MediaRecommendation(
    @SerialName("season_id") val seasonId: Long,
    private val title: String = "",
    private val subtitle: String = "",
    private val badge: String = "",
    private val rating: MediaRating? = null,
    @SerialName("new_ep") private val update: MediaRecommendationUpdate? = null,
) {
    fun toMediaCard(): MediaCard = MediaCard(
        seasonId = seasonId,
        title = title,
        cover = update?.coverUrl.orEmpty(),
        subtitle = subtitle,
        badge = badge,
        score = rating?.scoreValue.orEmpty(),
        progressText = update?.progressText.orEmpty(),
    )
}

@Serializable
data class MediaRecommendationUpdate(
    @SerialName("index_show") val progressText: String = "",
    @SerialName("cover") val coverUrl: String = "",
)
