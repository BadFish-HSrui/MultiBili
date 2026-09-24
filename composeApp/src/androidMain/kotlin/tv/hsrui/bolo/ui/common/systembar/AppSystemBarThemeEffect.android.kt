package tv.hsrui.bolo.ui.common.systembar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun AppSystemBarThemeEffect(isDarkTheme: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window) {
        onDispose { window?.let(SystemBarAppearance::clearTheme) }
    }
    // enableEdgeToEdge 会在配置变化时重新设置系统栏，随后恢复应用主题与局部覆盖。
    LaunchedEffect(window, isDarkTheme, LocalConfiguration.current) {
        window?.let { SystemBarAppearance.setTheme(it, isDarkTheme) }
    }
}
