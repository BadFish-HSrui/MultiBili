package tv.hsrui.bolo.player.danmaku

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/** 容器与弹幕宽度使用逻辑像素，返回每秒移动的逻辑像素数。 */
fun calculateBiliDanmakuSpeed(containerWidth: Float, danmakuWidth: Float): Float {
    if (!containerWidth.isFinite() || !danmakuWidth.isFinite() || containerWidth <= 0f || danmakuWidth <= 0f) return 0f
    val width = containerWidth.toDouble()
    val textWidth = danmakuWidth.toDouble()
    val duration = if (width < 558.0) {
        4.5
    } else {
        val (minimum, maximum) = when {
            width <= 888.0 -> 7.0 to 13.0
            width <= 1082.0 -> 8.0 to 14.0
            width <= 1246.0 -> 9.0 to 14.0
            width <= 1452.0 -> 10.0 to 15.0
            width <= 1744.0 -> 11.0 to 16.0
            width <= 2314.0 -> 13.0 to 18.0
            width <= 2560.0 -> 14.0 to 20.0
            else -> {
                val extra = floor((width - 2560.0) / 500.0)
                (16.0 + extra) to (22.0 + extra)
            }
        }
        val midpoint = ln((maximum - minimum) / 0.1 - 1.0) / 0.2 + 0.2
        minimum + (maximum - minimum) / (1.0 + exp(-0.2 * (textWidth / width - midpoint)))
    }
    return ((width + textWidth) / duration).coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
}
