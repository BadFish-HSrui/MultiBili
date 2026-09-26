package tv.hsrui.bolo.player.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import kotlin.math.roundToInt

class PlayerSubtitleSettings(settingsKSafe: KSafePlain) {
    private var storedScale by settingsKSafe(1.0f, key = "player_subtitle_scale_factor")
    private var currentScale by mutableFloatStateOf(
        normalizeValue(storedScale, 1.0f, 0.5f, 2.0f),
    )
    private var previewScale by mutableStateOf<Float?>(null)

    var scale: Float
        get() = currentScale
        set(value) {
            val normalized = normalizeValue(value, 1.0f, 0.5f, 2.0f)
            storedScale = normalized
            currentScale = normalized
        }

    val effectiveScale: Float get() = previewScale ?: currentScale

    fun previewScale(value: Float?) {
        previewScale = value?.let { normalizeValue(it, 1.0f, 0.5f, 2.0f) }
    }

    private var storedHeightRatio by settingsKSafe(0.2f, key = "player_subtitle_height_ratio")
    private var currentHeightRatio by mutableFloatStateOf(
        normalizeValue(storedHeightRatio, 0.2f, 0f, 1f),
    )
    private var previewHeightRatio by mutableStateOf<Float?>(null)

    var heightRatio: Float
        get() = currentHeightRatio
        set(value) {
            val normalized = normalizeValue(value, 0.2f, 0f, 1f)
            storedHeightRatio = normalized
            currentHeightRatio = normalized
        }

    val effectiveHeightRatio: Float get() = previewHeightRatio ?: currentHeightRatio

    fun previewHeightRatio(value: Float?) {
        previewHeightRatio = value?.let { normalizeValue(it, 0.2f, 0f, 1f) }
    }

    private var storedTextAlpha by settingsKSafe(1.0f, key = "player_subtitle_text_alpha")
    private var currentTextAlpha by mutableFloatStateOf(
        normalizeValue(storedTextAlpha, 1.0f, 0.2f, 1f),
    )
    private var previewTextAlpha by mutableStateOf<Float?>(null)

    var textAlpha: Float
        get() = currentTextAlpha
        set(value) {
            val normalized = normalizeValue(value, 1.0f, 0.2f, 1f)
            storedTextAlpha = normalized
            currentTextAlpha = normalized
        }

    val effectiveTextAlpha: Float get() = previewTextAlpha ?: currentTextAlpha

    fun previewTextAlpha(value: Float?) {
        previewTextAlpha = value?.let { normalizeValue(it, 1.0f, 0.2f, 1f) }
    }

    private var storedBackgroundAlpha by settingsKSafe(0.7f, key = "player_subtitle_background_alpha")
    private var currentBackgroundAlpha by mutableFloatStateOf(
        normalizeValue(storedBackgroundAlpha, 0.7f, 0f, 1f),
    )
    private var previewBackgroundAlpha by mutableStateOf<Float?>(null)

    var backgroundAlpha: Float
        get() = currentBackgroundAlpha
        set(value) {
            val normalized = normalizeValue(value, 0.7f, 0f, 1f)
            storedBackgroundAlpha = normalized
            currentBackgroundAlpha = normalized
        }

    val effectiveBackgroundAlpha: Float get() = previewBackgroundAlpha ?: currentBackgroundAlpha

    fun previewBackgroundAlpha(value: Float?) {
        previewBackgroundAlpha = value?.let { normalizeValue(it, 0.7f, 0f, 1f) }
    }

    private fun normalizeValue(value: Float, default: Float, minimum: Float, maximum: Float): Float =
        if (!value.isFinite()) default else (value.coerceIn(minimum, maximum) * 100f).roundToInt() / 100f
}
