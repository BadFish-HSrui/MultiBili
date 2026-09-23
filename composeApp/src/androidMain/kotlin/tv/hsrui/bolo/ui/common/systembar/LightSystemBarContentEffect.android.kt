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

private object SystemBarAppearance {
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
    }

    private val states = IdentityHashMap<Window, State>()

    fun acquire(window: Window, includeNavigationBar: Boolean) {
        val state = states.getOrPut(window) { State(window) }
        if (state.statusBarRequests++ == 0) {
            state.controller.isAppearanceLightStatusBars = false
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.BLACK
        }
        if (includeNavigationBar && state.navigationBarRequests++ == 0) {
            state.controller.isAppearanceLightNavigationBars = false
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.BLACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    fun release(window: Window, includeNavigationBar: Boolean) {
        val state = states[window] ?: return
        if (includeNavigationBar && --state.navigationBarRequests == 0) {
            state.controller.isAppearanceLightNavigationBars = state.lightNavigationBar
            @Suppress("DEPRECATION")
            window.navigationBarColor = state.navigationBarColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = state.navigationBarContrast ?: true
            }
        }
        if (--state.statusBarRequests == 0) {
            state.controller.isAppearanceLightStatusBars = state.lightStatusBar
            @Suppress("DEPRECATION")
            window.statusBarColor = state.statusBarColor
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
