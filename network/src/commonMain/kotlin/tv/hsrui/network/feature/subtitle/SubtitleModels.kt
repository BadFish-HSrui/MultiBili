package tv.hsrui.network.feature.subtitle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

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
    val isChinese: Boolean
        get() {
            val code = language.lowercase().removePrefix("ai-").replace('_', '-')
            return code == "zh" || code.startsWith("zh-")
        }
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
