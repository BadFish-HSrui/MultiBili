package tv.hsrui.bolo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.jvm_icon
import org.jetbrains.compose.resources.painterResource
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow
import tv.hsrui.bolo.player.DesktopPlayerKeyboard
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import java.awt.Dimension

fun main() {
    System.setProperty("compose.interop.blending", "true")
    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        Window(
            onCloseRequest = { BoloPlaybackSession.current?.close(); exitApplication() },
            state = windowState,
            onPreviewKeyEvent = DesktopPlayerKeyboard::onPreviewKeyEvent,
            onKeyEvent = { event ->
                DesktopPlayerFullscreenWindow.onKeyEvent(event) || DesktopPlayerKeyboard.onKeyEvent(event)
            },
            title = "Multi Bili",
            icon = painterResource(Res.drawable.jvm_icon)
        ) {
            DisposableEffect(window, windowState) {
                window.minimumSize = Dimension(720, 600)
                DesktopPlayerFullscreenWindow.attach(window, windowState)
                onDispose { DesktopPlayerFullscreenWindow.detach(window) }
            }
            App()
        }
    }
}
