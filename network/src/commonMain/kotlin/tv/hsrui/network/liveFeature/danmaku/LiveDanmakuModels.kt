package tv.hsrui.network.liveFeature.danmaku

data class LiveDanmakuData(
    val id: String = "",
    val sentAtEpochMillis: Long? = null,
    val username: String,
    val content: String,
    val replyUsername: String = "",
    val emoticon: LiveDanmakuEmoticonData? = null,
    val mode: LiveDanmakuMode? = null,
    val fontSize: Float = 25f,
    val colorRgb: Long = 0xFFFFFF,
    val inlineEmoticons: Map<String, LiveDanmakuEmoticonData> = emptyMap(),
)

data class LiveDanmakuEmoticonData(
    val url: String,
    val text: String,
    val width: Int = 1,
    val height: Int = 1,
)

enum class LiveDanmakuMode {
    Scroll,
    Top,
    Bottom,
}
