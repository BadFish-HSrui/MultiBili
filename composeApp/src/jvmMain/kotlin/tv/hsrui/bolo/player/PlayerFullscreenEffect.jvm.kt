package tv.hsrui.bolo.player

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window

internal object DesktopPlayerFullscreenWindow {
    var window: Window? = null
}

@Composable
actual fun PlayerFullscreenEffect(isFullscreen: Boolean) {
    DisposableEffect(isFullscreen) {
        val window = DesktopPlayerFullscreenWindow.window
        if (!isFullscreen || window == null) {
            onDispose {}
        } else {
            val device = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .defaultScreenDevice
            val originalFullScreenWindow = device.fullScreenWindow
            val frame = window as? Frame
            val originalExtendedState = frame?.extendedState

            device.fullScreenWindow = window
            frame?.extendedState = Frame.MAXIMIZED_BOTH
            window.toFront()
            window.requestFocus()

            onDispose {
                if (device.fullScreenWindow === window) {
                    device.fullScreenWindow = originalFullScreenWindow
                }
                if (originalExtendedState != null) {
                    frame.extendedState = originalExtendedState
                }
            }
        }
    }
}
