package tv.hsrui.network.feature.subtitle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class SubtitleListResponse(
    val code: Int = -1,
    val message: String = "",
    @SerialName("data") private val data: SubtitleListData? = null,
) {
    val isSuccess get() = code == 0
    val needLoginSubtitle: Boolean? get() = data?.needLoginSubtitle
    val subtitles: List<SubtitleItem> get() = data?.subtitles.orEmpty()
}

@Serializable
data class SubtitleListData(
    @SerialName("need_login_subtitle") val needLoginSubtitle: Boolean? = null,
    @SerialName("subtitle") private val subtitle: SubtitleData? = null,
) {
    val subtitles: List<SubtitleItem> get() = subtitle?.subtitles.orEmpty()
}

@Serializable
data class SubtitleData(
    val subtitles: List<SubtitleItem> = emptyList(),
)

@Serializable
data class SubtitleItem(
    @SerialName("lan") val language: String,
    @SerialName("lan_doc") val languageName: String,
    @SerialName("subtitle_url") private val rawUrl: String,
) {
    val url: String get() = rawUrl.toHttpsUrl()
    val isAiGenerated: Boolean get() = language.startsWith("ai-", ignoreCase = true)
    val displayName: String get() = languageName + if (isAiGenerated) "(AI)" else ""
}

@Serializable
data class SubtitleContentResponse(
    @SerialName("body") val cues: List<SubtitleCue>,
)

@Serializable
data class SubtitleCue(
    @SerialName("from") val startSeconds: Double,
    @SerialName("to") val endSeconds: Double,
    val content: String,
)
