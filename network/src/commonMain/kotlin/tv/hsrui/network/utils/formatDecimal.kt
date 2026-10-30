package tv.hsrui.network.utils

import java.math.BigDecimal
import java.math.RoundingMode

/** 按 HALF_UP 保留小数位，默认去尾零，并始终输出普通十进制字符串。 */
fun Double.formatDecimal(
    decimalPlaces: Int = 2,
    trimTrailingZeros: Boolean = true,
): String {
    require(isFinite()) { "Value must be finite" }
    require(decimalPlaces >= 0) { "Decimal places must be non-negative" }

    val rounded = BigDecimal.valueOf(this).setScale(decimalPlaces, RoundingMode.HALF_UP)
    return (if (trimTrailingZeros) rounded.stripTrailingZeros() else rounded).toPlainString()
}
