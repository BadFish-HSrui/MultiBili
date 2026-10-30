package tv.hsrui.network.utils

import java.util.Formatter
import java.util.Locale

fun Int.formatToDuration(): String = toLong().formatToDuration()

fun Long.formatToDuration(): String {
    val hours = this / 3600L
    val minutes = this % 3600L / 60L
    val seconds = this % 60L
    val formatter = Formatter(Locale.ROOT)

    return try {
        if (hours > 0) {
            formatter.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            formatter.format("%02d:%02d", minutes, seconds)
        }
        formatter.toString()
    } finally {
        formatter.close()
    }
}