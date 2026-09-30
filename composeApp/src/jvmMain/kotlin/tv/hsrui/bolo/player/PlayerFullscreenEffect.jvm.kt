package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import javax.swing.Timer

internal object DesktopPlayerFullscreenWindow {
    var window: ComposeWindow? by mutableStateOf(null)
        private set
    private var windowState: WindowState? = null
    private var player: PlayerFullscreenState? = null
    private var isSystemFullscreen = false
    private var windowedPlacement = WindowPlacement.Floating
    private var floatingBounds: Rectangle? = null
    private var originalPlacement: WindowPlacement? = null
    private var originalBounds: Rectangle? = null
    private var fullscreenOwner: PlayerFullscreenState? = null
    private var requestOwner: PlayerFullscreenState? = null
    private var requestedFullscreen: Boolean? = null
    private var requestStartedAt = 0L
    private var windowRestored = false
    private var exitAfterEnter = false
    private var escapePressed = false
    private var nativeTransition = false
    private var removeNativeListener: (() -> Unit)? = null

    // 只在异步切换期间轮询，原生按钮操作由窗口事件通知。
    private val transitionTimer = Timer(50) { observeWindow() }
    private val componentListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) = observeWindow()
        override fun componentMoved(e: ComponentEvent) = observeWindow()
        override fun componentShown(e: ComponentEvent) = observeWindow()
    }
    private val stateListener = WindowStateListener { observeWindow() }
    private val focusListener = object : WindowAdapter() {
        override fun windowLostFocus(e: WindowEvent) {
            escapePressed = false
        }
    }

    fun attach(window: ComposeWindow, state: WindowState) {
        this.window = window
        windowState = state
        window.addComponentListener(componentListener)
        window.addWindowStateListener(stateListener)
        window.addWindowFocusListener(focusListener)
        installNativeFullscreenListener(window)
        observeWindow()
    }

    fun detach(window: ComposeWindow) {
        if (this.window !== window) return
        transitionTimer.stop()
        window.removeComponentListener(componentListener)
        window.removeWindowStateListener(stateListener)
        window.removeWindowFocusListener(focusListener)
        removeNativeListener?.invoke()
        removeNativeListener = null
        nativeTransition = false
        player?.releaseSystemFullscreenOwnership = null
        player = null
        requestOwner = null
        fullscreenOwner = null
        requestedFullscreen = null
        originalPlacement = null
        originalBounds = null
        floatingBounds = null
        windowedPlacement = WindowPlacement.Floating
        isSystemFullscreen = false
        exitAfterEnter = false
        escapePressed = false
        windowState = null
        this.window = null
    }

    fun bindPlayer(state: PlayerFullscreenState) {
        player?.releaseSystemFullscreenOwnership = null
        player = state
        state.releaseSystemFullscreenOwnership = {
            if (fullscreenOwner === state) {
                fullscreenOwner = null
                exitAfterEnter = false
            }
        }
        state.updateSystemFullscreen(isSystemFullscreen, ownedByPlayer = fullscreenOwner === state)
    }

    fun unbindPlayer(state: PlayerFullscreenState) {
        if (player === state && state.systemFullscreenRequest == true &&
            state.isManualSystemFullscreen && requestedFullscreen == null
        ) {
            // 交接后直接离页时，Effect 可能尚未提交进入请求，由宿主继续完成。
            requestFullscreen(state, true)
        }
        state.releaseSystemFullscreenOwnership = null
        if (player === state) player = null
        if (requestOwner === state) requestOwner = null
        if (fullscreenOwner !== state) return
        if (requestedFullscreen == true || nativeTransition) {
            // 原生进入动画不能被页面释放取消，完成后由宿主串行退出。
            exitAfterEnter = true
        } else if (requestedFullscreen == null && isSystemFullscreen) {
            beginRequest(false, null)
        }
    }

    fun requestFullscreen(state: PlayerFullscreenState, fullscreen: Boolean) {
        if (player !== state || state.systemFullscreenRequest != fullscreen) return
        observeWindow()
        if (requestedFullscreen != null || nativeTransition || window == null || isSystemFullscreen == fullscreen ||
            (!fullscreen && fullscreenOwner !== state)
        ) {
            state.completeSystemFullscreenRequest(isSystemFullscreen, ownedByPlayer = fullscreenOwner === state)
            return
        }
        beginRequest(fullscreen, state)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.Escape) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                val state = player ?: return false
                if (!escapePressed) {
                    escapePressed = true
                    state.goBack()
                }
                true
            }
            KeyEventType.KeyUp -> escapePressed.also { escapePressed = false }
            else -> false
        }
    }

    private fun rememberWindowedState() {
        originalPlacement = windowedPlacement
        originalBounds = floatingBounds?.let(::Rectangle)
    }

    private fun installNativeFullscreenListener(window: ComposeWindow) {
        if (!System.getProperty("os.name").startsWith("Mac")) return
        try {
            // com.apple.eawt 仅存在于 macOS JDK，反射注册以保持 Windows/Linux 编译兼容。
            val listenerType = Class.forName("com.apple.eawt.FullScreenListener")
            val utilities = Class.forName("com.apple.eawt.FullScreenUtilities")
            val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "Bolo fullscreen listener"
                    else -> {
                        val update = Runnable {
                            if (this.window === window) {
                                when (method.name) {
                                    "windowEnteringFullScreen", "windowExitingFullScreen" -> {
                                        if (originalPlacement == null) rememberWindowedState()
                                        nativeTransition = true
                                        if (requestedFullscreen == null) requestStartedAt = System.nanoTime()
                                        transitionTimer.start()
                                    }
                                    "windowEnteredFullScreen", "windowExitedFullScreen" -> {
                                        nativeTransition = false
                                        observeWindow()
                                        if (requestedFullscreen == null) transitionTimer.stop()
                                    }
                                }
                            }
                        }
                        if (SwingUtilities.isEventDispatchThread()) update.run() else SwingUtilities.invokeLater(update)
                        null
                    }
                }
            }
            utilities.getMethod("addFullScreenListenerTo", Window::class.java, listenerType)
                .invoke(null, window, listener)
            removeNativeListener = {
                utilities.getMethod("removeFullScreenListenerFrom", Window::class.java, listenerType)
                    .invoke(null, window, listener)
            }
        } catch (error: ReflectiveOperationException) {
            println("Bolo 原生全屏动画监听不可用，使用窗口状态事件：${error.message}")
        }
    }

    private fun beginRequest(fullscreen: Boolean, owner: PlayerFullscreenState?) {
        if (window == null) return
        if (fullscreen) {
            rememberWindowedState()
            fullscreenOwner = owner?.takeIf { it.isSystemFullscreenOwnedByPlayer }
        }
        requestedFullscreen = fullscreen
        requestOwner = owner
        requestStartedAt = System.nanoTime()
        windowRestored = false
        nativeTransition = removeNativeListener != null && isSystemFullscreen != fullscreen
        transitionTimer.start()
        applyPlacement(if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating)
    }

    private fun applyPlacement(placement: WindowPlacement) {
        val window = window ?: return
        try {
            // 在 EDT 直接提交 Compose placement，避免 resize 回写 WindowState 覆盖待执行请求。
            // WindowState 只同步实际状态；请求和原生完成状态由宿主分别保存。
            window.placement = placement
            windowState?.placement = window.placement
        } catch (error: Exception) {
            println("Bolo 全屏切换失败：${error.message}")
            isSystemFullscreen = window.placement == WindowPlacement.Fullscreen
            player?.let { state ->
                state.updateSystemFullscreen(isSystemFullscreen, ownedByPlayer = fullscreenOwner === state)
            }
            finishRequest()
        }
    }

    private fun observeWindow() {
        val window = window ?: return
        if (nativeTransition) {
            if (System.nanoTime() - requestStartedAt < 5_000_000_000L) return
            nativeTransition = false
            if (requestedFullscreen == null) transitionTimer.stop()
        }
        if (!window.isDisplayable) {
            if (requestedFullscreen != null && System.nanoTime() - requestStartedAt >= 5_000_000_000L) {
                finishRequest()
            }
            return
        }
        val placement = window.placement
        val fullscreen = placement == WindowPlacement.Fullscreen
        if (fullscreen != isSystemFullscreen) {
            if (fullscreen && originalPlacement == null) rememberWindowedState()
            isSystemFullscreen = fullscreen
            player?.let { state ->
                state.updateSystemFullscreen(fullscreen, ownedByPlayer = fullscreenOwner === state)
            }
            if (!fullscreen && requestedFullscreen == null) {
                // 系统按钮退出同样恢复窗口状态，但不把系统进入的全屏归给页面。
                beginRequest(false, null)
            }
        }

        when (requestedFullscreen) {
            true -> if (fullscreen) finishRequest()
            false -> if (!fullscreen) {
                if (!windowRestored && placement == WindowPlacement.Floating) {
                    restoreWindow()
                } else if (windowRestored && placement == (originalPlacement ?: WindowPlacement.Floating)) {
                    finishRequest()
                }
            }
            null -> if (!fullscreen) {
                originalPlacement = null
                originalBounds = null
                fullscreenOwner = null
                windowedPlacement = placement
                if (placement == WindowPlacement.Floating) floatingBounds = Rectangle(window.bounds)
            }
        }

        if (requestedFullscreen == null && exitAfterEnter) {
            exitAfterEnter = false
            if (fullscreen) beginRequest(false, null)
        }
        if (requestedFullscreen != null && System.nanoTime() - requestStartedAt >= 5_000_000_000L) {
            println("Bolo 全屏切换未完成，保留原生窗口实际状态：${window.placement}")
            finishRequest()
        }
    }

    private fun restoreWindow() {
        val window = window ?: return
        val state = windowState ?: return
        // 必须先实际退出全屏再恢复最大化；直接设置 Maximized 不会关闭 Skiko 全屏。
        originalBounds?.let { bounds ->
            window.bounds = Rectangle(bounds)
            state.position = WindowPosition(bounds.x.dp, bounds.y.dp)
            state.size = DpSize(bounds.width.dp, bounds.height.dp)
        }
        windowRestored = true
        applyPlacement(originalPlacement ?: WindowPlacement.Floating)
    }

    private fun finishRequest() {
        val window = window ?: return
        val shouldExit = exitAfterEnter && isSystemFullscreen
        transitionTimer.stop()
        nativeTransition = false
        windowState?.placement = window.placement
        requestOwner?.takeIf { it === player }?.let { state ->
            state.completeSystemFullscreenRequest(isSystemFullscreen, ownedByPlayer = fullscreenOwner === state)
        }
        requestedFullscreen = null
        requestOwner = null
        exitAfterEnter = false
        if (!isSystemFullscreen) {
            fullscreenOwner = null
            originalPlacement = null
            originalBounds = null
            windowedPlacement = window.placement
            if (windowedPlacement == WindowPlacement.Floating) floatingBounds = Rectangle(window.bounds)
        }
        if (shouldExit) beginRequest(false, null)
    }
}

@Composable
actual fun PlayerFullscreenEffect(fullscreenState: PlayerFullscreenState, autoFullscreenOnRotateEnabled: Boolean) {
    val window = DesktopPlayerFullscreenWindow.window
    DisposableEffect(window, fullscreenState) {
        if (window != null) DesktopPlayerFullscreenWindow.bindPlayer(fullscreenState)
        onDispose { DesktopPlayerFullscreenWindow.unbindPlayer(fullscreenState) }
    }
    val request = fullscreenState.systemFullscreenRequest
    LaunchedEffect(window, fullscreenState, request) {
        if (window != null && request != null) {
            DesktopPlayerFullscreenWindow.requestFullscreen(fullscreenState, request)
        }
    }
}
