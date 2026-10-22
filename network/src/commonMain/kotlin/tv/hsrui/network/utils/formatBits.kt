package tv.hsrui.network.utils

fun Int.formatBits(decimalPlaces: Int = 2): String =
    toLong().formatBits(decimalPlaces)

/** 按原始 bit 数选择单位，以 1000 换算至 Gb；b 保留整数，其余单位四舍五入并去尾零。 */
fun Long.formatBits(decimalPlaces: Int = 2): String {
    require(decimalPlaces >= 0) { "Decimal places must be non-negative" }

    return when {
        this >= 1_000_000_000L -> "${(this / 1_000_000_000.0).formatDecimal(decimalPlaces)} Gb"
        this >= 1_000_000L -> "${(this / 1_000_000.0).formatDecimal(decimalPlaces)} Mb"
        this >= 1_000L -> "${(this / 1_000.0).formatDecimal(decimalPlaces)} kb"
        else -> "$this b"
    }
}
