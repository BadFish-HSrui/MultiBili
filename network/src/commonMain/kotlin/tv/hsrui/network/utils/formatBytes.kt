package tv.hsrui.network.utils

fun Int.formatBytes(decimalPlaces: Int = 2): String =
    this.toLong().formatBytes(decimalPlaces)

/** 按原始字节数选择单位，以 1024 换算至 GB；B 保留整数，其余单位四舍五入并去尾零。 */
fun Long.formatBytes(decimalPlaces: Int = 2): String {
    require(decimalPlaces >= 0) { "Decimal places must be non-negative" }

    return when {
        this >= 1_073_741_824L -> "${(this / 1_073_741_824.0).formatDecimal(decimalPlaces)} GB"
        this >= 1_048_576L -> "${(this / 1_048_576.0).formatDecimal(decimalPlaces)} MB"
        this >= 1_024L -> "${(this / 1_024.0).formatDecimal(decimalPlaces)} KB"
        else -> "$this B"
    }
}
