package tv.hsrui.network.feature.player.enumModels

enum class AudioQuality(
    override val code: Int, override val title: String,
    override val shortTitle: String
) : Quality {
    QUALITY_64K(30216, "64K 低码率","64K"),
    QUALITY_132K(30232,"132K 标准" ,"132K"),
    QUALITY_192K(30280, "192K 高码率","192K"),
    DOLBY_ATMOS(30250,"杜比全景声" ,"杜比"),
    HI_RES_LOSSLESS(30251,"Hi-Res无损" ,"无损");

    companion object {
        private val qualityCodeMap = entries.associateBy { it.code }

        fun AudioQuality(code: Int): AudioQuality? {
            return qualityCodeMap[code]
        }
    }
}