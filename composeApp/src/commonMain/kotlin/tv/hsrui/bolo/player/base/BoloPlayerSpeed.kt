package tv.hsrui.bolo.player.base

import kotlin.math.abs

enum class BoloPlayerSpeed(
    val title: String,
    val rateNumber: Float
) {
    // 枚举顺序即倍速选择菜单的展示顺序：大倍速在上，小倍速在下。
    SPEED_3X("3x", 3f),
    SPEED_2X("2x", 2f),
    SPEED_1_5X("1.5x", 1.5f),
    SPEED_1_25X("1.25x", 1.25f),
    SPEED_1X("1x", 1f),
    SPEED_0_75X("0.75x", 0.75f),
    SPEED_0_5X("0.5x", 0.5f);

    companion object {
        val default: BoloPlayerSpeed = SPEED_1X

        fun fromRateNumber(rateNumber: Float): BoloPlayerSpeed {
            if (!rateNumber.isFinite() || rateNumber <= 0f) {
                return default
            }
            // 按速率取最近档位，结果不依赖枚举声明顺序。
            return entries.minBy { abs(it.rateNumber - rateNumber) }
        }
    }
}
