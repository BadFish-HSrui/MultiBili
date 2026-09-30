package tv.hsrui.bolo.player.controls

import androidx.compose.runtime.Composable

internal interface PlayerDeviceControls {
    val supportsDeviceGestures: Boolean
    fun readBrightness(): Float?
    fun setBrightness(value: Float)
    fun readVolume(): Float?
    fun beginVolumeAdjustment(): Float? = readVolume()
    fun setVolume(value: Float): Boolean
    fun endVolumeAdjustment() = Unit
}

@Composable
internal expect fun rememberPlayerDeviceControls(): PlayerDeviceControls
