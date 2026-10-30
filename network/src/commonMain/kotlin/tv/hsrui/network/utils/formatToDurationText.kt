package tv.hsrui.network.utils

import kotlin.math.ceil
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Long.formatToDurationText(): String {
    val duration = coerceAtLeast(0).seconds
    if (duration < 1.minutes) return "${duration.inWholeSeconds}秒"
    if (duration < 1.hours) {
        val minutes = ceil(duration / 1.minutes).toLong()
        return if (minutes == 60L) "1小时" else "${minutes}分钟"
    }
    if (duration < 1.days) {
        val hours = ceil(duration / 1.hours).toLong()
        return if (hours == 24L) "1天" else "${hours}小时"
    }
    if (duration < 7.days) {
        val days = ceil(duration / 1.days).toLong()
        return if (days == 7L) "1周" else "${days}天"
    }
    return "${duration.inWholeDays / 7}周"
}