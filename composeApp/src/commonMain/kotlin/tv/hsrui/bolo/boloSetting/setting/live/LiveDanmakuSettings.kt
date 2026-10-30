package tv.hsrui.bolo.boloSetting.setting.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMode
import kotlin.math.roundToInt

class LiveDanmakuSettings(settingsKSafe: KSafePlain) {
    private var storedEnabled by settingsKSafe(true, key = "bolo_live_danmaku_enabled")
    private var currentEnabled by mutableStateOf(storedEnabled)
    var enabled: Boolean
        get() = currentEnabled
        set(value) {
            if (currentEnabled == value) return
            storedEnabled = value
            currentEnabled = value
        }

    private var storedScale by settingsKSafe(1f, key = "bolo_live_danmaku_scale_factor")
    private var currentScale by mutableStateOf(normalize(storedScale, 1f, .5f, 2f, 1))
    var scale: Float
        get() = currentScale
        set(value) {
            val next = normalize(value, 1f, .5f, 2f, 1)
            if (currentScale == next) return
            storedScale = next
            currentScale = next
        }

    private var storedAlpha by settingsKSafe(.7f, key = "bolo_live_danmaku_alpha")
    private var currentAlpha by mutableStateOf(normalize(storedAlpha, .7f, .2f, 1f, 1))
    var alpha: Float
        get() = currentAlpha
        set(value) {
            val next = normalize(value, .7f, .2f, 1f, 1)
            if (currentAlpha == next) return
            storedAlpha = next
            currentAlpha = next
        }

    private var storedSpeed by settingsKSafe(1f, key = "bolo_live_danmaku_speed_factor")
    private var currentSpeed by mutableStateOf(normalize(storedSpeed, 1f, .5f, 2f, 1))
    var speed: Float
        get() = currentSpeed
        set(value) {
            val next = normalize(value, 1f, .5f, 2f, 1)
            if (currentSpeed == next) return
            storedSpeed = next
            currentSpeed = next
        }

    private var storedDisplayAreaRatio by settingsKSafe(.5f, key = "bolo_live_danmaku_display_area_ratio")
    private var currentDisplayAreaRatio by mutableStateOf(normalize(storedDisplayAreaRatio, .5f, .2f, 1f, 5))
    var displayAreaRatio: Float
        get() = currentDisplayAreaRatio
        set(value) {
            val next = normalize(value, .5f, .2f, 1f, 5)
            if (currentDisplayAreaRatio == next) return
            storedDisplayAreaRatio = next
            currentDisplayAreaRatio = next
        }

    private var storedTopBottomScrollEnabled by settingsKSafe(false, key = "bolo_live_danmaku_top_bottom_scroll_enabled")
    private var currentTopBottomScrollEnabled by mutableStateOf(storedTopBottomScrollEnabled)
    var topBottomScrollEnabled: Boolean
        get() = currentTopBottomScrollEnabled
        set(value) {
            if (currentTopBottomScrollEnabled == value) return
            storedTopBottomScrollEnabled = value
            currentTopBottomScrollEnabled = value
        }

    private var storedExtraLineSpacingEnabled by settingsKSafe(false, key = "bolo_live_danmaku_extra_line_spacing_enabled")
    private var currentExtraLineSpacingEnabled by mutableStateOf(storedExtraLineSpacingEnabled)
    var extraLineSpacingEnabled: Boolean
        get() = currentExtraLineSpacingEnabled
        set(value) {
            if (currentExtraLineSpacingEnabled == value) return
            storedExtraLineSpacingEnabled = value
            currentExtraLineSpacingEnabled = value
        }

    private var storedScrollEnabled by settingsKSafe(true, key = "bolo_live_danmaku_scroll_enabled")
    private var currentScrollEnabled by mutableStateOf(storedScrollEnabled)
    var scrollEnabled: Boolean
        get() = currentScrollEnabled
        set(value) {
            if (currentScrollEnabled == value) return
            storedScrollEnabled = value
            currentScrollEnabled = value
        }

    private var storedTopEnabled by settingsKSafe(true, key = "bolo_live_danmaku_top_enabled")
    private var currentTopEnabled by mutableStateOf(storedTopEnabled)
    var topEnabled: Boolean
        get() = currentTopEnabled
        set(value) {
            if (currentTopEnabled == value) return
            storedTopEnabled = value
            currentTopEnabled = value
        }

    private var storedBottomEnabled by settingsKSafe(true, key = "bolo_live_danmaku_bottom_enabled")
    private var currentBottomEnabled by mutableStateOf(storedBottomEnabled)
    var bottomEnabled: Boolean
        get() = currentBottomEnabled
        set(value) {
            if (currentBottomEnabled == value) return
            storedBottomEnabled = value
            currentBottomEnabled = value
        }

    private var alphaPreview by mutableStateOf<Float?>(null)
    val effectiveAlpha: Float get() = alphaPreview ?: alpha

    fun previewAlpha(value: Float?) {
        alphaPreview = value?.let { normalize(it, .7f, .2f, 1f, 1) }
    }

    fun allows(mode: LiveDanmakuMode?): Boolean = enabled && when (mode) {
        LiveDanmakuMode.Scroll -> scrollEnabled
        LiveDanmakuMode.Top -> topEnabled
        LiveDanmakuMode.Bottom -> bottomEnabled
        null -> false
    }

    private fun normalize(value: Float, default: Float, min: Float, max: Float, step: Int): Float =
        if (!value.isFinite()) default else
            ((value.coerceIn(min, max) * 100f / step).roundToInt() * step / 100f).coerceIn(min, max)
}
