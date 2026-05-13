package tv.hsrui.bolo.utils.infoFormat

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Long.formatToDateTime(): String {
    val time = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

    return "${time.year}年${time.month.number}月${time.day}日 ${time.hour}:${time.minute}"
}