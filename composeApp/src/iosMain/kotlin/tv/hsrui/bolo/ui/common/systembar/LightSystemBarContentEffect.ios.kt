package tv.hsrui.bolo.ui.common.systembar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import tv.hsrui.bolo.LocalStatusBarAppearance

@Composable
internal actual fun LightSystemBarContentEffect(includeNavigationBar: Boolean) {
    val appearance = LocalStatusBarAppearance.current
    DisposableEffect(appearance) {
        appearance.acquire()
        onDispose { appearance.release() }
    }
}
