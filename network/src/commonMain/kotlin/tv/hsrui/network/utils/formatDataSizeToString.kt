package tv.hsrui.network.utils

import kotlin.math.roundToInt

fun Long.formatDataSizeToString(): String {
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L

    return when {
        this < 0 -> "error"
        this < kb -> "${this}B"
        this < mb -> "${((this.toDouble() / kb) * 100).roundToInt() / 100.0}KB"
        this < gb -> "${((this.toDouble() / mb) * 100).roundToInt() / 100.0}MB"
        else -> "${((this.toDouble() / gb) * 100).roundToInt() / 100.0}GB"
    }
}