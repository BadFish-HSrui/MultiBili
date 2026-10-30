package tv.hsrui.network.liveFeature.player.enumModels

enum class LiveVideoCodec(val code: Int, val apiName: String) {
    AVC(0, "avc"),
    HEVC(1, "hevc"),
    AV1(2, "av1");

    companion object {
        private val codecCodeMap = entries.associateBy { it.code }
        private val codecNameMap = entries.associateBy { it.apiName }

        fun fromCode(code: Int): LiveVideoCodec? = codecCodeMap[code]

        fun fromName(name: String): LiveVideoCodec? = codecNameMap[name]
    }
}
