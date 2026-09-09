package tv.hsrui.bolo.player.controls

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import kotlin.math.roundToInt

@Composable
internal actual fun rememberPlayerDeviceControls(): PlayerDeviceControls {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val window = context.findActivity()?.window
    val controls = remember(context, window, lifecycleOwner) {
        object : PlayerDeviceControls {
            private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            override val supportsDeviceGestures = true
            private var requestedBrightness: Float? = null
            private var originalBrightness: Float? = null
            private var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

            override fun readBrightness(): Float? {
                val override = window?.attributes?.screenBrightness
                if (override != null && override >= 0f) return override.coerceIn(0f, 1f)
                return runCatching {
                    (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f)
                        .coerceIn(0f, 1f)
                }.getOrNull()
            }

            override fun setBrightness(value: Float) {
                requestedBrightness = value.coerceIn(0f, 1f)
                applyBrightness()
            }

            private fun applyBrightness() {
                val target = requestedBrightness ?: return
                if (!resumed || window == null) return
                val attributes = window.attributes
                if (originalBrightness == null) originalBrightness = attributes.screenBrightness
                attributes.screenBrightness = target
                window.attributes = attributes
            }

            fun restoreBrightness() {
                val original = originalBrightness ?: return
                window?.let {
                    val attributes = it.attributes
                    attributes.screenBrightness = original
                    it.attributes = attributes
                }
                originalBrightness = null
            }

            fun onResume() {
                resumed = true
                applyBrightness()
            }

            fun onPause() {
                resumed = false
                restoreBrightness()
            }

            override fun readVolume(): Float? {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
            }

            override fun setVolume(value: Float) {
                runCatching {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    } else 0
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                        (value.coerceIn(0f, 1f) * max).roundToInt().coerceIn(min, max), 0)
                }.onFailure { showSnackbarMessage("无法修改系统媒体音量") }
            }
        }
    }
    DisposableEffect(controls, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controls.onResume()
                Lifecycle.Event.ON_PAUSE -> controls.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controls.restoreBrightness()
        }
    }
    return controls
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
