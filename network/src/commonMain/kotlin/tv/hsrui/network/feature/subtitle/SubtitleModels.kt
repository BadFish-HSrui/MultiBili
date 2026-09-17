@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package tv.hsrui.network.feature.subtitle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SubtitleResponse(
    @ProtoNumber(1) private val subtitle: SubtitleData? = null,
) {
    val subtitles: List<SubtitleItem> get() = subtitle?.subtitles.orEmpty()
    val isSuccess: Boolean get() = subtitle != null
}

@Serializable
data class SubtitleData(
    @ProtoNumber(3) val subtitles: List<SubtitleItem> = emptyList(),
)

@Serializable
data class SubtitleItem(
    @ProtoNumber(3) @SerialName("lan") val language: String = "",
    @ProtoNumber(4) @SerialName("lan_doc") val languageName: String = "",
    @ProtoNumber(5) @SerialName("subtitle_url") private val rawUrl: String = "",
    @ProtoNumber(7) @SerialName("type") private val subtitleType: Int = 0,
    @ProtoNumber(11) private val role: Int = 0,
    @ProtoNumber(13) private val format: Int = 0,
) {
    val url: String by lazy { resolveSubtitleUrl(rawUrl) }
    val isMain: Boolean get() = role == 1
    val isAss: Boolean get() = format == 1
    val isPlayable: Boolean get() = (format == 0 || isAss) && url.isNotBlank()
    val isAiGenerated: Boolean get() = subtitleType == 1 || language.startsWith("ai-", ignoreCase = true)
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
