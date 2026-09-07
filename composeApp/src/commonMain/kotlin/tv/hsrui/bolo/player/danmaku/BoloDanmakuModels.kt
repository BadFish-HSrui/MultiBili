package tv.hsrui.bolo.player.danmaku

data class BoloDanmakuItem(
    val id: Long,
    val progressMs: Long,
    val content: String,
    val mode: BoloDanmakuMode,
    val fontSize: Float = 25f,
    val colorRgb: Long = 0xFFFFFF,
)

enum class BoloDanmakuMode {
    Scroll,
    Top,
    Bottom,
}

/** positionMs 是最近接收的媒体调度位置，与内部动画时间无关。 */
data class BoloDanmakuState(
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val isVisible: Boolean = true,
    val itemCount: Int = 0,
)
