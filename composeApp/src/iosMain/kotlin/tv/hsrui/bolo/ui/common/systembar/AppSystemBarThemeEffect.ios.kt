package tv.hsrui.bolo.ui.common.systembar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import tv.hsrui.bolo.LocalStatusBarAppearance

@Composable
internal actual fun AppSystemBarThemeEffect(isDarkTheme: Boolean) {
    val appearance = LocalStatusBarAppearance.current
    DisposableEffect(appearance) {
        onDispose { appearance.setTheme(null) }
    }
    SideEffect { appearance.setTheme(isDarkTheme) }
}
