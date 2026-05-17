package tv.hsrui.network.utils

fun Int.formatToDuration(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60

    return buildString {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}