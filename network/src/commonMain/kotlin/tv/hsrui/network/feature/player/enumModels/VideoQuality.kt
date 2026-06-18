package tv.hsrui.network.feature.player.enumModels

enum class VideoQuality(
    override val code: Int,
    override val title: String,
    override val shortTitle: String
) : Quality {
    LD_360P(16, "360P 流畅", "360P"),
    SD_480P(32, "480P 清晰", "480P"),
    HD_720P(64, "720P 高清", "720P"),
    HD_720P_60FPS(74, "720P 高帧率", "720P60"),
    FHD_1080P(80, "1080P 全高清", "1080P"),
    FHD_1080P_HIGH_BITRATE(112, "1080P 高码率", "1080P+"),
    FHD_1080P_60FPS(116, "1080P 高帧率", "1080P60"),
    UHD(120, "4K 超清", "4K");

    companion object {
        private val qualityCodeMap = entries.associateBy { it.code }

        val best get() = entries.maxBy { it.code }

        fun VideoQuality(code: Int): VideoQuality? {
            return qualityCodeMap[code]
        }
    }
}