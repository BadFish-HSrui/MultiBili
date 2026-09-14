package tv.hsrui.bolo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.jvm_icon
import org.jetbrains.compose.resources.painterResource
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow

fun main() {
    System.setProperty("compose.interop.blending", "true")
    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            onKeyEvent = DesktopPlayerFullscreenWindow::onKeyEvent,
            title = "Multi Bili",
            icon = painterResource(Res.drawable.jvm_icon)
        ) {
            DisposableEffect(window, windowState) {
                DesktopPlayerFullscreenWindow.attach(window, windowState)
                onDispose { DesktopPlayerFullscreenWindow.detach(window) }
            }
            App()
        }
    }
}
