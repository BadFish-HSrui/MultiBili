package tv.hsrui.network.liveFeature.player.enumModels

enum class LiveVideoQuality(val code: Int, val title: String) {
    FLUENT(80, "流畅"),
    HD(150, "高清"),
    SUPER_HD(250, "超清"),
    BLU_RAY(400, "蓝光"),
    ORIGINAL(10000, "原画"),
    QHD(15000, "2K"),
    UHD(20000, "4K"),
    DOLBY(30000, "杜比");

    companion object {
        private val qualityCodeMap = entries.associateBy { it.code }

        fun fromCode(code: Int): LiveVideoQuality? = qualityCodeMap[code]
    }
}
