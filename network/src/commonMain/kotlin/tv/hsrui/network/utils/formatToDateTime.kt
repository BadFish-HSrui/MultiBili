package tv.hsrui.network.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Long.formatToDateTime(
    year: Boolean = true,
    monthDay: Boolean = true,
    hourMinute: Boolean = true,
    second: Boolean = false
): String {
    val time = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

    return buildString {
        if (year) append("${time.year}年")
        if (monthDay) append("${time.month.number}月${time.day}日")
        if (hourMinute) {
            if (monthDay) append(' ')
            append("${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}")
            if (second) append(":${time.second.toString().padStart(2, '0')}")
        }
    }
}