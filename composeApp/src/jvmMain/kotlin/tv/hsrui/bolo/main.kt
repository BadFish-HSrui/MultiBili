package tv.hsrui.bolo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.jvm_icon
import org.jetbrains.compose.resources.painterResource
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Multi Bili",
        icon = painterResource(Res.drawable.jvm_icon)
    ) {
        DisposableEffect(window) {
            DesktopPlayerFullscreenWindow.window = window
            onDispose {
                if (DesktopPlayerFullscreenWindow.window === window) {
                    DesktopPlayerFullscreenWindow.window = null
                }
            }
        }
        App()
    }
}
