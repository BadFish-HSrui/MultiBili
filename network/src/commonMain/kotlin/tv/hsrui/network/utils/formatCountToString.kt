package tv.hsrui.network.utils

fun Int.formatCountToString(): String = toLong().formatCountToString()

fun Long.formatCountToString(): String {
    if (this < 10_000L) return toString()

    // 固定一位小数时，从 9999.95 万开始进位为亿。
    return if (this < 99_999_500L) {
        "${(this / 10_000.0).formatDecimal(1, trimTrailingZeros = false)}万"
    } else {
        "${(this / 100_000_000.0).formatDecimal(1, trimTrailingZeros = false)}亿"
    }
}
