package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    private val volume: JsonElement? = null,
    @SerialName("cur_language") private val currentLanguage: String? = null,
    @SerialName("cur_production_type") private val currentProductionType: Int? = null,
) {
    val playbackLanguage: String get() = currentLanguage?.trim().orEmpty()
    val playbackProductionType: Int
        get() = if (playbackLanguage.isEmpty()) 0 else currentProductionType?.takeIf { it in 1..2 } ?: 2

    val loudness: VideoLoudnessData? get() = VideoLoudnessData.fromJson(volume)

    val videoFormatMap: Map<VideoQuality, Map<VideoCodec, BiliDashObject>>
        get() = _dashData.video
            .map { it.withFallbackDuration(_dashData.duration) }
            .filter { it.quality is VideoQuality }
            .groupBy { it.quality as VideoQuality }
            .mapValues { (_, dashObjectList) -> dashObjectList.associateBy { it.codec } }

    val audioFormatMap: Map<AudioQuality, BiliDashObject>?
        get() = _dashData.audio
            ?.map { it.withFallbackDuration(_dashData.duration) }
            ?.filter { it.quality is AudioQuality }
            ?.associateBy { it.quality as AudioQuality }

    @Serializable
    data class DashData(
        val duration: Long = 0L,
        val video: List<BiliDashObject> = emptyList(),
        val audio: List<BiliDashObject>? = emptyList()
    )
}

@Serializable
data class BiliDashObject(
    @SerialName("id") private val _qualityCode: Int = 0,
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("backup_url") val backupUrl: List<String> = emptyList(),
    @SerialName("codecs") val codecString: String = "",
    @SerialName("codecid") private val _codecCode: Int = 0,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("frame_rate") val frameRate: String = "",
    @SerialName("segment_base") val segmentBase: BiliSegmentBase? = null,
    val bandwidth: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
) {
    val quality: Quality? by lazy {
        _qualityCode.let { if (it < 256) VideoQuality(it) else AudioQuality(it) }
    }

    val codec by lazy { VideoCodec(_codecCode) }

    fun withFallbackDuration(duration: Long): BiliDashObject =
        if (this.duration > 0 || duration <= 0) this else copy(duration = duration)
}

@Serializable
data class BiliSegmentBase(
    @SerialName("initialization") val initialization: String = "",
    @SerialName("Initialization") val initializationPascalCase: String = "",
    @SerialName("index_range") val indexRange: String = "",
    @SerialName("indexRange") val indexRangeCamelCase: String = ""
) {
    val resolvedInitialization: String
        get() = initialization.ifBlank { initializationPascalCase }

    val resolvedIndexRange: String
        get() = indexRange.ifBlank { indexRangeCamelCase }
}

fun VideoPlayResponse.toVideoSource(): VideoSource {
    val isSuccess: Boolean
    val message: String

    when {
        !this.isSuccess -> {
            isSuccess = false
            message = if (code == -10403) "此内容需要大会员" else "[$code]: ${this.message}"
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
        _audio = this.data.audioFormatMap,
        loudness = this.data.loudness,
        playbackLanguage = this.data.playbackLanguage,
        playbackProductionType = this.data.playbackProductionType,
    )
}
