package tv.hsrui.bolo.ui.theme

import androidx.compose.ui.graphics.Color

object BiliColor {
    private const val LV1 = 0xFFBFBFBF
    private const val LV2 = 0xFF8AD39D
    private const val LV3 = 0XFF7ACCF1
    private const val LV4 = 0XFFFEB98D
    private const val LV5 = 0XFFEE6829
    private const val LV6 = 0XFFFE0000
    private const val THEME = 0xFFFE679A
    private const val BLUE = 0xFF02AEEC

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

    val ThemeColor = Color(THEME)
    val Blue = Color(BLUE)
}