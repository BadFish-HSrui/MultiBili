package tv.hsrui.network.feature.player.enumModels

enum class VideoCodec(val code: Int) {
    AVC(7),
    HEVC(12),
    AV1(13),
    Audio(0);

    companion object {
        fun VideoCodec(code: Int): VideoCodec =
            when (code) {
                7 -> AVC
                12 -> HEVC
                13 -> AV1
                else -> Audio
            }
    }
}