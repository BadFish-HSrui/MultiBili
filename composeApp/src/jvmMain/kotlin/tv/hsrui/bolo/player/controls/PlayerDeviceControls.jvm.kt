package tv.hsrui.bolo.player.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberPlayerDeviceControls(): PlayerDeviceControls = remember {
    object : PlayerDeviceControls {
        override val supportsDeviceGestures = false
        override fun readBrightness(): Float? = null
        override fun setBrightness(value: Float) = Unit
        override fun readVolume(): Float? = null
        override fun setVolume(value: Float) = false
    }
}
