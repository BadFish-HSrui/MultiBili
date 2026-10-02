package tv.hsrui.network.feature.player

import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.utils.getTargetOrSmallerOrLargerOrNull

data class VideoSource(
    val isSuccess: Boolean,
    val message: String,
    private val _video: Map<VideoQuality, Map<VideoCodec, BiliDashObject>>,
    private val _audio: Map<AudioQuality, BiliDashObject>?,
    val isPreview: Boolean = false,
    val loudness: VideoLoudnessData? = null,
    val playbackLanguage: String = "",
    val playbackProductionType: Int = 0,
) {
    val videoQualities: List<VideoQuality> = _video.keys.sortedByDescending { it.code }
    val audioQualities: List<AudioQuality> = _audio?.keys?.sortedByDescending { it.code }.orEmpty()

    fun availableVideoCodecs(quality: VideoQuality): List<VideoCodec> =
        listOf(VideoCodec.AVC, VideoCodec.HEVC, VideoCodec.AV1).filter {
            _video[quality]?.get(it)?.getUrls()?.isNotEmpty() == true
        }

    fun getExactVideo(quality: VideoQuality, codec: VideoCodec): BiliDashObject? =
        _video[quality]?.get(codec)?.takeIf { it.getUrls().isNotEmpty() }

    fun getExactAudio(quality: AudioQuality): BiliDashObject? =
        _audio?.get(quality)?.takeIf { it.getUrls().isNotEmpty() }

    fun getVideo(quality: VideoQuality?, codec: VideoCodec): BiliDashObject =
        _video.getTargetOrSmallerOrLargerOrNull(quality ?: VideoQuality.best)
            ?.getTargetOrSmallerOrLargerOrNull(codec) ?: BiliDashObject()

    fun getAudio(quality: AudioQuality?): BiliDashObject? =
        _audio?.getTargetOrSmallerOrLargerOrNull(quality ?: AudioQuality.best)
}
