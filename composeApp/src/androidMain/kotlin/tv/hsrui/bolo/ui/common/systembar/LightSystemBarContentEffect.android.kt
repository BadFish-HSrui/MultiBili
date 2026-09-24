package tv.hsrui.bolo.ui.common.systembar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.IdentityHashMap

@Composable
internal actual fun LightSystemBarContentEffect(includeNavigationBar: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(view, context, includeNavigationBar) {
        val window = view.findDialogWindow() ?: context.findActivity()?.window
        if (window == null) {
            onDispose {}
        } else {
            SystemBarAppearance.acquire(window, includeNavigationBar)
            onDispose { SystemBarAppearance.release(window, includeNavigationBar) }
        }
    }
}

internal object SystemBarAppearance {
    private class State(val window: Window) {
        val controller: WindowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.decorView)
        val lightStatusBar = controller.isAppearanceLightStatusBars
        val lightNavigationBar = controller.isAppearanceLightNavigationBars
        @Suppress("DEPRECATION")
        val statusBarColor = window.statusBarColor
        @Suppress("DEPRECATION")
        val navigationBarColor = window.navigationBarColor
        val navigationBarContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else null
        var statusBarRequests = 0
        var navigationBarRequests = 0
        var isDarkTheme: Boolean? = null
    }

    private val states = IdentityHashMap<Window, State>()

    fun setTheme(window: Window, isDarkTheme: Boolean) {
        val state = states.getOrPut(window) { State(window) }
        state.isDarkTheme = isDarkTheme
        apply(window)
    }

    fun clearTheme(window: Window) {
        val state = states[window] ?: return
        state.isDarkTheme = null
        apply(window)
    }

    fun acquire(window: Window, includeNavigationBar: Boolean) {
        val state = states.getOrPut(window) { State(window) }
        state.statusBarRequests++
        if (includeNavigationBar) state.navigationBarRequests++
        apply(window)
    }

    fun release(window: Window, includeNavigationBar: Boolean) {
        val state = states[window] ?: return
        if (includeNavigationBar) state.navigationBarRequests--
        state.statusBarRequests--
        apply(window)
    }

    @Suppress("DEPRECATION")
    fun apply(window: Window) {
        val state = states[window] ?: return
        val lightStatusBar = state.isDarkTheme?.not() ?: state.lightStatusBar
        val lightNavigationBar = state.isDarkTheme?.not() ?: state.lightNavigationBar
        state.controller.isAppearanceLightStatusBars = state.statusBarRequests == 0 && lightStatusBar
        state.controller.isAppearanceLightNavigationBars = state.navigationBarRequests == 0 && lightNavigationBar
        window.statusBarColor = if (state.statusBarRequests > 0) Color.BLACK else state.statusBarColor
        window.navigationBarColor = when {
            state.navigationBarRequests > 0 -> Color.BLACK
            // Android 8–9 不提供系统对比度保护，背景随图标一起切换。
            state.isDarkTheme != null && Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.Q ->
                if (lightNavigationBar) Color.argb(230, 255, 255, 255) else Color.argb(128, 27, 27, 27)
            else -> state.navigationBarColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced =
                state.navigationBarRequests == 0 && (state.navigationBarContrast ?: true)
        }
        if (state.isDarkTheme == null && state.statusBarRequests == 0 && state.navigationBarRequests == 0) {
            states.remove(window)
        }
    }
}

private fun View.findDialogWindow(): Window? {
    var current: View? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
