package tv.hsrui.bolo.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import tv.hsrui.bolo.ui.common.systembar.SystemBarAppearance

@Composable
actual fun PlayerFullscreenEffect(fullscreenState: PlayerFullscreenState) {
    val isFullscreen = fullscreenState.isFullscreen
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner, isFullscreen) {
        val activity = context.findActivity()
        if (!isFullscreen || activity == null) {
            onDispose {}
        } else {
            val window = activity.window
            val originalOrientation = activity.requestedOrientation
            val originalSystemUiVisibility = window.decorView.systemUiVisibility

            fun applyFullscreen() {
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                hideSystemBars(activity)
            }

            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) applyFullscreen()
            }
            val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) applyFullscreen()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            applyFullscreen()

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                window.decorView.viewTreeObserver.takeIf { it.isAlive }
                    ?.removeOnWindowFocusChangeListener(focusListener)
                activity.requestedOrientation = originalOrientation
                showSystemBars(activity, originalSystemUiVisibility)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun hideSystemBars(activity: Activity) {
    val window = activity.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        SystemBarAppearance.apply(window)
    }
}

private fun showSystemBars(activity: Activity, originalSystemUiVisibility: Int) {
    val window = activity.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.show(WindowInsets.Type.systemBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = originalSystemUiVisibility
        SystemBarAppearance.apply(window)
    }
}
