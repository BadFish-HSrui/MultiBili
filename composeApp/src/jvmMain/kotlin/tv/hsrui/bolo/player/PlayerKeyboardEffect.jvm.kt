package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import tv.hsrui.bolo.player.settings.PlayerShortcutAction
import tv.hsrui.bolo.player.settings.PlayerShortcutBinding
import tv.hsrui.bolo.player.settings.shortcutAction

internal object DesktopPlayerKeyboard {
    private var owner: Any? = null
    private var onKeyEvent: ((KeyEvent) -> Boolean)? = null
    private var onCancel: (() -> Unit)? = null
    private var bindings: Map<String, List<PlayerShortcutBinding?>> = emptyMap()
    // 保留按下时的操作，包括切集加载时控制层解绑的间隙。
    private val pressedActions = mutableMapOf<Key, PlayerShortcutAction>()
    private val cancelledKeys = mutableSetOf<Key>()
    private var keyWindow: Window? = null
    private val keyListener = object : WindowAdapter() {
        override fun windowLostFocus(e: WindowEvent) = clearPressedKeys()
        override fun windowClosed(e: WindowEvent) = clearPressedKeys()
    }

    private fun clearPressedKeys() {
        cancel()
        pressedActions.clear()
        cancelledKeys.clear()
        detachKeyWindow()
    }

    private fun detachKeyWindow() {
        keyWindow?.removeWindowFocusListener(keyListener)
        keyWindow?.removeWindowListener(keyListener)
        keyWindow = null
    }

    fun bind(owner: Any, bindings: Map<String, List<PlayerShortcutBinding?>>, onKeyEvent: (KeyEvent) -> Boolean, onCancel: () -> Unit) {
        cancel()
        this.owner = owner
        this.bindings = bindings
        this.onKeyEvent = onKeyEvent
        this.onCancel = onCancel
    }

    fun updateBindings(owner: Any, bindings: Map<String, List<PlayerShortcutBinding?>>) {
        if (this.owner !== owner || this.bindings == bindings) return
        cancel()
        this.bindings = bindings
    }

    fun unbind(owner: Any) {
        if (this.owner !== owner) return
        cancel()
        this.owner = null
        onKeyEvent = null
        onCancel = null
    }

    fun cancel() {
        cancelledKeys.addAll(pressedActions.keys)
        onCancel?.invoke()
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) {
            val captured = pressedActions.remove(event.key) != null
            val cancelled = cancelledKeys.remove(event.key)
            if (pressedActions.isEmpty()) detachKeyWindow()
            // 同时释放控制层或音量弹窗直接接管的按键，以及提前松开的修饰键。
            val handled = onKeyEvent?.invoke(event) == true
            return captured || cancelled || handled
        }
        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) cancel()
        return false
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val action = pressedActions[event.key] ?: bindings.shortcutAction(event)
        if (event.type == KeyEventType.KeyDown) {
            if (event.key in cancelledKeys) return true
            if (action != null) {
                val component = event.awtEventOrNull?.component ?: return false
                if (component.inputMethodRequests != null) return false
                if (action.isEpisode && event.key in pressedActions) return true
            }
        }
        val handled = onKeyEvent?.invoke(event) == true
        if (handled && action != null && event.type == KeyEventType.KeyDown) {
            pressedActions[event.key] = action
            val window = event.awtEventOrNull?.component?.let { SwingUtilities.getWindowAncestor(it) ?: it as? Window }
            if (keyWindow !== window) {
                detachKeyWindow()
                keyWindow = window
                window?.addWindowFocusListener(keyListener)
                window?.addWindowListener(keyListener)
            }
        }
        return handled
    }

    fun onContentPreviewKeyEvent(event: KeyEvent): Boolean {
        // 保留播放／暂停的主内容优先级，并随用户改键同步；输入会话始终让行。
        return event.type == KeyEventType.KeyDown &&
            bindings.shortcutAction(event) == PlayerShortcutAction.TogglePlayback && onKeyEvent(event)
    }
}

@Composable
actual fun PlayerKeyboardEffect(
    bindings: Map<String, List<PlayerShortcutBinding?>>,
    onKeyEvent: (KeyEvent) -> Boolean,
    onCancel: () -> Unit,
) {
    val window = DesktopPlayerFullscreenWindow.window
    val latestOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val latestOnCancel by rememberUpdatedState(onCancel)
    val owner = remember(window) { Any() }
    SideEffect { DesktopPlayerKeyboard.updateBindings(owner, bindings) }
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) {
                DesktopPlayerKeyboard.cancel()
            }
        }
        val mouseListener = AWTEventListener { event ->
            if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED &&
                SwingUtilities.getWindowAncestor(event.component) === window) {
                DesktopPlayerKeyboard.cancel()
            }
        }
        if (window != null) {
            DesktopPlayerKeyboard.bind(owner, bindings, { latestOnKeyEvent(it) }, { latestOnCancel() })
            window.addWindowFocusListener(listener)
            Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, AWTEvent.MOUSE_EVENT_MASK)
        }
        onDispose {
            window?.removeWindowFocusListener(listener)
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener)
            DesktopPlayerKeyboard.unbind(owner)
        }
    }
}
