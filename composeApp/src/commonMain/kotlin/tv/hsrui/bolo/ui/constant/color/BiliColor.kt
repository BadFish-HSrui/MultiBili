package tv.hsrui.bolo.ui.constant.color

import androidx.compose.ui.graphics.Color

object BiliColor {
    const val LV1 = 0xFFBFBFBF
    const val LV2 = 0xFF8AD39D
    const val LV3 = 0XFF7ACCF1
    const val LV4 = 0XFFFEB98D
    const val LV5 = 0XFFEE6829
    const val LV6 = 0XFFFE0000
    const val BIG = 0xFFFC6397

    fun getLevelColor(level: Int): Color {
        return Color(
            when (level) {
                2 -> LV2
                3 -> LV3
                4 -> LV4
                5 -> LV5
                6, 7 -> LV6
                else -> LV1
            }
        )
    }
}