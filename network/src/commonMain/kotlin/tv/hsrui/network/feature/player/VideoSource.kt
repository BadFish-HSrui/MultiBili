package tv.hsrui.network.feature.player

import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.utils.getTargetOrSmallerOrLarger

data class VideoSource(
    val isSuccess: Boolean,
    val message: String,
    private val _video: Map<VideoQuality, Map<VideoCodec, BiliDashObject>>,
    private val _audio: Map<AudioQuality, BiliDashObject>?
) {
    fun getVideo(quality: VideoQuality?, codec: VideoCodec): BiliDashObject =
        _video.getTargetOrSmallerOrLarger(quality ?: VideoQuality.best)
            ?.getTargetOrSmallerOrLarger(codec) ?: BiliDashObject()

    fun getAudio(quality: AudioQuality?): BiliDashObject? =
        _audio?.getTargetOrSmallerOrLarger(quality ?: AudioQuality.best)
}
