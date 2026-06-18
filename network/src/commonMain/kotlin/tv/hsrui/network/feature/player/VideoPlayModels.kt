package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.AudioQuality.Companion.AudioQuality
import tv.hsrui.network.feature.player.enumModels.Quality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoCodec.Companion.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.enumModels.VideoQuality.Companion.VideoQuality

@Serializable
data class VideoPlayResponse(
    val code: Int = -1,
    val message: String = "",
    val data: VideoPlayData = VideoPlayData()
) {
    val isSuccess get() = (code == 0)
}

@Serializable
data class VideoPlayData(
    @SerialName("dash") private val _dashData: DashData = DashData(),
) {
    val videoFormatMap: Map<VideoQuality, Map<VideoCodec, BiliDashObject>>
        get() = _dashData.video
            .filter { it.quality is VideoQuality }
            .groupBy { it.quality as VideoQuality }
            .mapValues { (_, dashObjectList) -> dashObjectList.associateBy { it.codec } }

    val audioFormatMap: Map<AudioQuality, BiliDashObject>?
        get() = _dashData.audio
            ?.filter { it.quality is AudioQuality }
            ?.associateBy { it.quality as AudioQuality }

    @Serializable
    data class DashData(
        val video: List<BiliDashObject> = emptyList(),
        val audio: List<BiliDashObject>? = emptyList()
    )
}

@Serializable
data class BiliDashObject(
    @SerialName("id") private val _qualityCode: Int = 0,
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("codecs") val codecString: String = "",
    @SerialName("codecid") private val _codecCode: Int = 0
) {
    val quality: Quality? by lazy {
        _qualityCode.let { if (it < 256) VideoQuality(it) else AudioQuality(it) }
    }

    val codec by lazy { VideoCodec(_codecCode) }
}

fun VideoPlayResponse.toVideoSource(): VideoSource {
    val isSuccess: Boolean
    val message: String

    when {
        !this.isSuccess -> {
            isSuccess = false
            message = "[$code]: ${this.message}"
        }

        this.data.videoFormatMap.isEmpty() -> {
            isSuccess = false
            message = "[视频源获取错误]: 画质列表为空"
        }

        this.data.audioFormatMap?.isEmpty() ?: false -> {
            isSuccess = false
            message = "[视频源获取错误]: 音质列表为空"
        }

        else -> {
            isSuccess = true
            message = "[$code]: ${this.message}"
        }
    }

    return VideoSource(
        isSuccess = isSuccess,
        message = message,
        _video = this.data.videoFormatMap,
        _audio = this.data.audioFormatMap
    )
}