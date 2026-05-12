package tv.hsrui.bolo.utils.infoFormat

fun Int.formatToDuration(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60

    return "${if (hours > 0) "${hours}:" else ""}${if (minutes > 9) minutes else "0${minutes}"}:${if (seconds > 9) seconds else "0${seconds}"}"
}