package tv.hsrui.bolo.boloSetting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain

class BoloSettings(settingsKSafe: KSafePlain) {
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

    private fun normalizeDanmakuSpeed(value: Float): Float =
        if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
}
