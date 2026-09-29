package tv.hsrui.bolo.player.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import kotlin.math.roundToInt

class PlayerDanmakuSettings(settingsKSafe: KSafePlain) {
    private var storedFilterLevel by settingsKSafe(0, key = "player_danmaku_filter_level")
    private var currentFilterLevel by mutableIntStateOf(storedFilterLevel.coerceIn(0, 10))

    var filterLevel: Int
        get() = currentFilterLevel
        set(value) {
            val level = value.coerceIn(0, 10)
            if (level == currentFilterLevel) return
            storedFilterLevel = level
            currentFilterLevel = level
        }

    private var storedScale by settingsKSafe(1.0f, key = "player_danmaku_scale_factor")
    private var currentScale by mutableFloatStateOf(storedScale)

    var scale: Float
        get() = currentScale
        set(value) {
            val scale = if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
            storedScale = scale
            currentScale = scale
        }

    private var storedAlpha by settingsKSafe(0.7f, key = "player_danmaku_alpha")
    private var currentAlpha by mutableFloatStateOf(normalizeAlpha(storedAlpha))
    private var previewAlpha by mutableStateOf<Float?>(null)

    var alpha: Float
        get() = currentAlpha
        set(value) {
            val normalized = normalizeAlpha(value)
            storedAlpha = normalized
            currentAlpha = normalized
        }

    val effectiveAlpha: Float get() = previewAlpha ?: currentAlpha

    fun previewAlpha(value: Float?) {
        previewAlpha = value?.let(::normalizeAlpha)
    }

    private fun normalizeAlpha(value: Float): Float =
        if (!value.isFinite()) 0.7f else (value.coerceIn(0.2f, 1f) * 100f).roundToInt() / 100f

    private var storedSpeed by settingsKSafe(1.0f, key = "player_danmaku_speed_factor")
    private var currentSpeed by mutableFloatStateOf(normalizeSpeed(storedSpeed))

    var speed: Float
        get() = currentSpeed
        set(value) {
            val speed = normalizeSpeed(value)
            storedSpeed = speed
            currentSpeed = speed
        }

    private var storedDisplayAreaRatio by settingsKSafe(0.5f, key = "player_danmaku_display_area_ratio")
    private var currentDisplayAreaRatio by mutableFloatStateOf(
        normalizeDisplayAreaRatio(storedDisplayAreaRatio),
    )

    var displayAreaRatio: Float
        get() = currentDisplayAreaRatio
        set(value) {
            val ratio = normalizeDisplayAreaRatio(value)
            storedDisplayAreaRatio = ratio
            currentDisplayAreaRatio = ratio
        }

    private fun normalizeDisplayAreaRatio(value: Float): Float =
        if (!value.isFinite()) 0.5f else (value.coerceIn(0.2f, 1.0f) * 20f).roundToInt() / 20f

    private var storedTopBottomScrollEnabled by settingsKSafe(
        false,
        key = "player_danmaku_top_bottom_scroll_enabled",
    )
    private var currentTopBottomScrollEnabled by mutableStateOf(storedTopBottomScrollEnabled)

    var topBottomScrollEnabled: Boolean
        get() = currentTopBottomScrollEnabled
        set(value) {
            storedTopBottomScrollEnabled = value
            currentTopBottomScrollEnabled = value
        }

    private var storedExtraLineSpacingEnabled by settingsKSafe(
        false,
        key = "player_danmaku_extra_line_spacing_enabled",
    )
    private var currentExtraLineSpacingEnabled by mutableStateOf(storedExtraLineSpacingEnabled)

    var extraLineSpacingEnabled: Boolean
        get() = currentExtraLineSpacingEnabled
        set(value) {
            storedExtraLineSpacingEnabled = value
            currentExtraLineSpacingEnabled = value
        }

    private var storedScrollEnabled by settingsKSafe(true, key = "player_danmaku_scroll_enabled")
    private var currentScrollEnabled by mutableStateOf(storedScrollEnabled)

    var scrollEnabled: Boolean
        get() = currentScrollEnabled
        set(value) {
            storedScrollEnabled = value
            currentScrollEnabled = value
        }

    private var storedTopEnabled by settingsKSafe(true, key = "player_danmaku_top_enabled")
    private var currentTopEnabled by mutableStateOf(storedTopEnabled)

    var topEnabled: Boolean
        get() = currentTopEnabled
        set(value) {
            storedTopEnabled = value
            currentTopEnabled = value
        }

    private var storedBottomEnabled by settingsKSafe(true, key = "player_danmaku_bottom_enabled")
    private var currentBottomEnabled by mutableStateOf(storedBottomEnabled)

    var bottomEnabled: Boolean
        get() = currentBottomEnabled
        set(value) {
            storedBottomEnabled = value
            currentBottomEnabled = value
        }

    private fun normalizeSpeed(value: Float): Float =
        if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
}
