package tv.hsrui.network.utils

fun Int.formatCountToString(): String {
    return when {
        this < 10000 -> this.toString()
        this < 100000000 -> "${(this / 1000) / 10.0F}万"
        else -> "${(this / 10000000) / 10.0F}亿"
    }
}

fun Long.formatCountToString(): String {
    return when {
        this < 10000L -> this.toString()
        this < 100000000L -> "${(this / 1000L) / 10.0}万"
        else -> "${(this / 10000000L) / 10.0}亿"
    }
}