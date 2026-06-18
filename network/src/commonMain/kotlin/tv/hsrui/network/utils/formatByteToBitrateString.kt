package tv.hsrui.network.utils

import kotlin.math.roundToInt

fun Long.formatByteToBitrateString(): String {
    val bit = this * 8

    val kb = 1024
    val mb = kb * 1024

    return when {
        bit < mb -> "${bit / kb} Kbps"
        else -> "${((bit.toDouble() / mb) * 100).roundToInt() / 100.0} Mbps"
    }
}