package tv.hsrui.bolo.player.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGRectMake
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIScreen
import platform.UIKit.UISlider
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberPlayerDeviceControls(): PlayerDeviceControls {
    val host = LocalUIViewController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val volumeView = remember(host) {
        MPVolumeView(frame = CGRectMake(-1000.0, -1000.0, 200.0, 40.0)).apply {
            userInteractionEnabled = false
        }
    }
    val controls = remember(host, volumeView, lifecycleOwner) {
        object : PlayerDeviceControls {
            override val supportsDeviceGestures = true
            private fun screen(): UIScreen = host.view.window?.screen ?: UIScreen.mainScreen
            override fun readBrightness(): Float = screen().brightness.toFloat()
            private var requestedBrightness: Double? = null
            private var originalBrightness: Double? = null
            private var modifiedScreen: UIScreen? = null
            private var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

            override fun setBrightness(value: Float) {
                requestedBrightness = value.coerceIn(0f, 1f).toDouble()
                applyBrightness()
            }

            private fun applyBrightness() {
                val target = requestedBrightness ?: return
                if (!resumed) return
                val currentScreen = screen()
                if (modifiedScreen != currentScreen) restoreBrightness()
                if (originalBrightness == null) {
                    originalBrightness = currentScreen.brightness
                    modifiedScreen = currentScreen
                }
                currentScreen.brightness = target
            }

            fun restoreBrightness() {
                originalBrightness?.let { modifiedScreen?.brightness = it }
                originalBrightness = null
                modifiedScreen = null
            }

            fun onResume() {
                resumed = true
                applyBrightness()
            }

            fun onPause() {
                resumed = false
                restoreBrightness()
            }
            override fun readVolume(): Float = AVAudioSession.sharedInstance().outputVolume
            override fun setVolume(value: Float) {
                val slider = volumeView.subviews.filterIsInstance<UISlider>().firstOrNull()
                if (slider == null) {
                    showSnackbarMessage("当前设备无法调节系统音量")
                    return
                }
                slider.setValue(value.coerceIn(0f, 1f), animated = false)
                slider.sendActionsForControlEvents(UIControlEventValueChanged)
            }
        }
    }
    DisposableEffect(host, volumeView, controls, lifecycleOwner) {
        host.view.addSubview(volumeView)
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
            volumeView.removeFromSuperview()
        }
    }
    return controls
}
