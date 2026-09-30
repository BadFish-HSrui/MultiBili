package tv.hsrui.bolo.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import tv.hsrui.bolo.ui.common.systembar.SystemBarAppearance

@Composable
actual fun PlayerFullscreenEffect(fullscreenState: PlayerFullscreenState, autoFullscreenOnRotateEnabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context, lifecycleOwner, fullscreenState) {
        context.findActivity()?.let { AndroidPlayerFullscreenController(it, lifecycleOwner, fullscreenState) }
    }
    val isFullscreen = fullscreenState.isFullscreen
    val manualTarget = fullscreenState.manualOrientationTarget
    DisposableEffect(controller) {
        controller?.bind()
        onDispose { controller?.unbind() }
    }
    SideEffect {
        controller?.update(autoFullscreenOnRotateEnabled, isFullscreen, manualTarget)
    }
}

private class AndroidPlayerFullscreenController(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val state: PlayerFullscreenState,
) {
    private class Host(activity: Activity) {
        val originalOrientation = activity.requestedOrientation
        val originalSystemUiVisibility = activity.window.decorView.systemUiVisibility
        val owners = mutableListOf<AndroidPlayerFullscreenController>()
    }

    private companion object {
        val hosts = mutableMapOf<Activity, Host>()
    }

    private var autoRotate = false
    private var configured = false
    private var applying = false
    private var deviceLandscape: Boolean? = null
    private var sensorEnabled = false
    private var appliedFullscreen: Boolean? = null
    private val active get() = hosts[activity]?.owners?.lastOrNull() === this &&
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private val rotationAllowed get() = Settings.System.getInt(
        activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0,
    ) == 1
    private val orientationListener = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(orientation: Int) {
            // 仅解除手动方向保护；传感器本身不能触发全屏或绕过系统锁定。
            deviceLandscape = when (orientation) {
                in 0..30, in 330..359 -> false
                in 60..120, in 240..300 -> true
                else -> null
            }
            apply()
        }
    }
    private val lifecycleObserver = LifecycleEventObserver { _, _ ->
        deviceLandscape = null
        appliedFullscreen = null
        apply()
    }
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) {
            appliedFullscreen = null
            apply()
        }
    }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { apply() }
    private val rotationObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = apply()
    }

    fun bind() {
        val host = hosts.getOrPut(activity) { Host(activity) }
        host.owners.add(this)
        host.owners.dropLast(1).forEach { it.apply() }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        activity.window.decorView.viewTreeObserver.apply {
            addOnWindowFocusChangeListener(focusListener)
            addOnGlobalLayoutListener(layoutListener)
        }
        activity.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, rotationObserver,
        )
    }

    fun update(autoRotate: Boolean, fullscreen: Boolean, manualTarget: Boolean?) {
        this.autoRotate = autoRotate
        configured = true
        // 参数使 Compose 跟踪手动操作和全屏布局变化；状态仍以同一个页面对象为准。
        if (state.isFullscreen == fullscreen && state.manualOrientationTarget == manualTarget) apply()
    }

    private fun observedLandscape(): Boolean? {
        if (activity.isInMultiWindowMode) return null
        val view = activity.window.decorView
        if (view.width <= 0 || view.height <= 0) return null
        val landscape = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> true
            Configuration.ORIENTATION_PORTRAIT -> false
            else -> return null
        }
        return landscape.takeIf { (view.width > view.height) == it }
    }

    private fun apply() {
        if (applying) return
        val shouldSyncLayout = configured && active && state.isPhone && autoRotate
        val shouldObserve = shouldSyncLayout && rotationAllowed
        if (shouldObserve != sensorEnabled) {
            sensorEnabled = shouldObserve
            deviceLandscape = null
            if (shouldObserve && orientationListener.canDetectOrientation()) orientationListener.enable()
            else orientationListener.disable()
        }
        if (!configured || !active) return
        applying = true
        try {
            val observed = observedLandscape()
            if (shouldObserve && observed != null && observed == deviceLandscape) {
                state.releaseManualOrientationTarget(observed)
            }
            val orientation = when {
                !state.isPhone -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                autoRotate && state.manualOrientationTarget == null -> ActivityInfo.SCREEN_ORIENTATION_USER
                state.isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
            // 系统锁定只停止传感器监听；已发生的界面旋转仍须同步全屏布局。
            if (shouldSyncLayout && observed != null) state.updateFullscreenFromRotation(observed)
            if (appliedFullscreen != state.isFullscreen) {
                appliedFullscreen = state.isFullscreen
                if (state.isFullscreen) hideSystemBars(activity)
                else hosts[activity]?.let { showSystemBars(activity, it.originalSystemUiVisibility) }
            }
        } finally {
            applying = false
        }
    }

    fun unbind() {
        orientationListener.disable()
        sensorEnabled = false
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        activity.contentResolver.unregisterContentObserver(rotationObserver)
        activity.window.decorView.viewTreeObserver.takeIf { it.isAlive }?.apply {
            removeOnWindowFocusChangeListener(focusListener)
            removeOnGlobalLayoutListener(layoutListener)
        }
        val host = hosts[activity] ?: return
        val wasOwner = host.owners.lastOrNull() === this
        host.owners.remove(this)
        if (host.owners.isEmpty()) {
            hosts.remove(activity)
            activity.requestedOrientation = host.originalOrientation
            showSystemBars(activity, host.originalSystemUiVisibility)
        } else if (wasOwner) {
            host.owners.last().let {
                it.appliedFullscreen = null
                it.apply()
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
