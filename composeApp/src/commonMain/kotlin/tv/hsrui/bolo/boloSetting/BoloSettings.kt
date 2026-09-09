package tv.hsrui.bolo.boloSetting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain

class BoloSettings(settingsKSafe: KSafePlain) {
    private var storedDanmakuFilterLevel by settingsKSafe(0, key = "danmaku_filter_level")
    private var currentDanmakuFilterLevel by mutableIntStateOf(storedDanmakuFilterLevel.coerceIn(0, 10))

    var danmakuFilterLevel: Int
        get() = currentDanmakuFilterLevel
        set(value) {
            val level = value.coerceIn(0, 10)
            if (level == currentDanmakuFilterLevel) return
            storedDanmakuFilterLevel = level
            currentDanmakuFilterLevel = level
        }

    private var storedDanmakuScale by settingsKSafe(1.0f, key = "danmaku_scale_factor")
    private var currentDanmakuScale by mutableFloatStateOf(storedDanmakuScale)

    var danmakuScale: Float
        get() = currentDanmakuScale
        set(value) {
            val scale = if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
            storedDanmakuScale = scale
            currentDanmakuScale = scale
        }

    private var storedDanmakuSpeed by settingsKSafe(1.0f, key = "danmaku_speed_factor")
    private var currentDanmakuSpeed by mutableFloatStateOf(normalizeDanmakuSpeed(storedDanmakuSpeed))

    var danmakuSpeed: Float
        get() = currentDanmakuSpeed
        set(value) {
            val speed = normalizeDanmakuSpeed(value)
            storedDanmakuSpeed = speed
            currentDanmakuSpeed = speed
        }

    private var storedDanmakuExtraLineSpacingEnabled by settingsKSafe(
        false,
        key = "danmaku_extra_line_spacing_enabled",
    )
    private var currentDanmakuExtraLineSpacingEnabled by mutableStateOf(storedDanmakuExtraLineSpacingEnabled)

    var danmakuExtraLineSpacingEnabled: Boolean
        get() = currentDanmakuExtraLineSpacingEnabled
        set(value) {
            storedDanmakuExtraLineSpacingEnabled = value
            currentDanmakuExtraLineSpacingEnabled = value
        }

    private fun normalizeDanmakuSpeed(value: Float): Float =
        if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
}
