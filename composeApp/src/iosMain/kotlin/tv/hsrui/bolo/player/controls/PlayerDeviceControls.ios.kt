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
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIScreen
import platform.UIKit.UISlider
import tv.hsrui.bolo.player.base.IosPlayerAudioSession
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberPlayerDeviceControls(): PlayerDeviceControls {
    val host = LocalUIViewController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val volumeView = remember(host) {
        MPVolumeView(frame = CGRectMake(-1000.0, -1000.0, 200.0, 40.0)).apply {
            hidden = true
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
            private var disposed = false
            private var audioInterrupted = false
            private var volumeAdjustmentOwner: Any? = null
            private val canAdjustVolume: Boolean
                get() = !disposed && resumed && !audioInterrupted && host.view.window != null &&
                    UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive

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
                endVolumeAdjustment()
                restoreBrightness()
            }

            fun onAudioInterruption(began: Boolean) {
                audioInterrupted = began
                if (began) endVolumeAdjustment()
            }

            fun dispose() {
                disposed = true
                endVolumeAdjustment()
                restoreBrightness()
            }

            override fun readVolume(): Float = AVAudioSession.sharedInstance().outputVolume
            override fun beginVolumeAdjustment(): Float? {
                endVolumeAdjustment()
                if (!canAdjustVolume) return null
                val owner = Any()
                volumeAdjustmentOwner = owner
                volumeView.hidden = false
                if (!IosPlayerAudioSession.beginVolumeAdjustment(owner)) {
                    endVolumeAdjustment()
                    showSnackbarMessage("无法修改系统媒体音量")
                    return null
                }
                // 激活会话期间若发生失活或取消，不遗留临时使用权。
                if (volumeAdjustmentOwner !== owner || !canAdjustVolume) {
                    IosPlayerAudioSession.endVolumeAdjustment(owner)
                    endVolumeAdjustment()
                    return null
                }
                volumeView.layoutIfNeeded()
                if (volumeView.subviews.none { it is UISlider }) {
                    endVolumeAdjustment()
                    showSnackbarMessage("当前设备无法调节系统音量")
                    return null
                }
                return readVolume().coerceIn(0f, 1f)
            }

            override fun setVolume(value: Float): Boolean {
                if (volumeAdjustmentOwner == null || !canAdjustVolume) {
                    endVolumeAdjustment()
                    return false
                }
                val slider = volumeView.subviews.filterIsInstance<UISlider>().firstOrNull()
                if (slider == null) {
                    endVolumeAdjustment()
                    showSnackbarMessage("当前设备无法调节系统音量")
                    return false
                }
                slider.setValue(value.coerceIn(0f, 1f), animated = false)
                slider.sendActionsForControlEvents(UIControlEventValueChanged)
                return volumeAdjustmentOwner != null && canAdjustVolume
            }

            override fun endVolumeAdjustment() {
                volumeView.hidden = true
                val owner = volumeAdjustmentOwner
                volumeAdjustmentOwner = null
                if (owner != null) IosPlayerAudioSession.endVolumeAdjustment(owner)
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
        val notifications = NSNotificationCenter.defaultCenter
        val notificationObservers = listOf(
            notifications.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
                controls.endVolumeAdjustment()
            },
            notifications.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
                when ((note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.longValue) {
                    1L -> controls.onAudioInterruption(began = true)
                    0L -> controls.onAudioInterruption(began = false)
                }
            },
            notifications.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue) {
                controls.endVolumeAdjustment()
            },
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            notificationObservers.forEach { notifications.removeObserver(it) }
            controls.dispose()
            volumeView.removeFromSuperview()
        }
    }
    return controls
}
