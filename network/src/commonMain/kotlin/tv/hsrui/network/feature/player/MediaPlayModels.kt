package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MediaPlayResponse(
    private val code: Int = -1,
    private val message: String = "",
    private val result: MediaPlayData? = null,
) {
    fun toVideoSource(): VideoSource {
        val data = result
        val error = when {
            code == -10403 -> "此内容需要大会员"
            code != 0 -> "[$code] ${message.ifBlank { "无法获取媒体播放权限" }}"
            data == null -> "媒体响应缺少播放信息"
            !data.isSuccess -> data.message
            data.isDrm -> "当前剧集使用 DRM 加密，暂不支持播放"
            else -> null
        }
        if (error != null) return VideoSource(false, error, emptyMap(), null)
        return VideoPlayResponse(code = 0, data = data!!.playData).toVideoSource()
            .copy(isPreview = data.isPreview)
    }
}

@Serializable
data class MediaPlayData(
    private val code: Int = -1,
    @SerialName("error_code") private val errorCode: Int = 0,
    @SerialName("message") private val responseMessage: String = "",
    @SerialName("is_drm") val isDrm: Boolean = false,
    @SerialName("is_preview") private val preview: Int = 0,
    private val dash: VideoPlayData.DashData? = null,
    private val volume: JsonElement? = null,
) {
    val isSuccess: Boolean get() = code == 0 && errorCode == 0
    val message: String
        get() = if (code == -10403 || errorCode == -10403) {
            "此内容需要大会员"
        } else {
            "[${if (code != 0) code else errorCode}] ${responseMessage.ifBlank { "无法获取媒体播放权限" }}"
        }
    val isPreview: Boolean get() = preview == 1
    val playData: VideoPlayData get() = VideoPlayData(dash ?: VideoPlayData.DashData(), volume)
}
