package tv.hsrui.network.utils

fun Int.formatCountToString(): String {
    val value = this.toLong()
    return when {
        value < 10000 -> value.toString()
        value < 100000000 -> "${(value / 1000) / 10.0F}万"
        else -> "${(value / 10000000) / 10.0F}亿"
    }
}