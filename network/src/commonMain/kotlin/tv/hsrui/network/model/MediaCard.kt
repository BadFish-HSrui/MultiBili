package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class MediaCard(
    @SerialName("season_id") val seasonId: Long,
    @SerialName("cover") private val cover: String = "",
    val title: String = "",
    @SerialName("subTitle") val subtitle: String = "",
    val badge: String = "",
    @SerialName("score") private val score: String = "",
    @SerialName("index_show") val progressText: String = "",
) {
    val coverUrl: String get() = cover.toHttpsUrl()
    val scoreText: String
        get() = if ((score.toFloatOrNull() ?: 0f) > 0f) "${score}分" else ""
}
