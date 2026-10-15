package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
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

internal object DesktopPlayerKeyboard {
    private var owner: Any? = null
    private var onKeyEvent: ((KeyEvent) -> Boolean)? = null
    private var onCancel: (() -> Unit)? = null
    private val pressedKeys = mutableSetOf<Key>()
    // 切集会重建控制层；这两键的按压状态独立于回调绑定，直到松开或窗口失焦才清理。
    private val episodePressedKeys = mutableSetOf<Key>()
    private var episodeKeyWindow: Window? = null
    private val episodeKeyListener = object : WindowAdapter() {
        override fun windowLostFocus(e: WindowEvent) = clearEpisodeKeys()
        override fun windowClosed(e: WindowEvent) = clearEpisodeKeys()
    }

    private fun clearEpisodeKeys() {
        episodePressedKeys.clear()
        episodeKeyWindow?.removeWindowFocusListener(episodeKeyListener)
        episodeKeyWindow?.removeWindowListener(episodeKeyListener)
        episodeKeyWindow = null
    }

    fun bind(owner: Any, onKeyEvent: (KeyEvent) -> Boolean, onCancel: () -> Unit) {
        cancel()
        this.owner = owner
        this.onKeyEvent = onKeyEvent
        this.onCancel = onCancel
    }

    fun unbind(owner: Any) {
        if (this.owner !== owner) return
        cancel()
        this.owner = null
        onKeyEvent = null
        onCancel = null
    }

    fun cancel() {
        pressedKeys.clear()
        onCancel?.invoke()
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        // 已接管的按键即使在松开前改变焦点，也必须结束临时倍速。
        if (event.type == KeyEventType.KeyUp) {
            val captured = pressedKeys.remove(event.key)
            val episodeCaptured = episodePressedKeys.remove(event.key)
            if (episodePressedKeys.isEmpty()) clearEpisodeKeys()
            // 播放器容器和音量弹窗也会在预览阶段接管按键，统一询问其释放状态。
            val handled = onKeyEvent?.invoke(event) == true
            if (captured || episodeCaptured || handled) return true
        }
        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) cancel()
        return false
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val episodeKey = event.key == Key.LeftBracket || event.key == Key.RightBracket
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.Spacebar || event.key == Key.Zero || event.key == Key.NumPad0 || episodeKey)) {
            val component = event.awtEventOrNull?.component ?: return false
            // 字符按键留给文本输入及候选词操作，窗口兜底也不能接管。
            if (component.inputMethodRequests != null) return false
            if (episodeKey) {
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) return false
                if (event.key in episodePressedKeys) return true
            }
        }
        val handled = onKeyEvent?.invoke(event) == true
        if (handled && event.type == KeyEventType.KeyDown) {
            pressedKeys.add(event.key)
            if (episodeKey) {
                val window = event.awtEventOrNull?.component?.let { SwingUtilities.getWindowAncestor(it) ?: it as? Window }
                if (episodeKeyWindow !== window) {
                    clearEpisodeKeys()
                    episodeKeyWindow = window
                    // 按住期间保留监听，包括切集加载时没有播放器回调的间隙。
                    window?.addWindowFocusListener(episodeKeyListener)
                    window?.addWindowListener(episodeKeyListener)
                }
                episodePressedKeys.add(event.key)
            }
        }
        return handled
    }

    fun onContentPreviewKeyEvent(event: KeyEvent): Boolean {
        // 主内容先处理空格；独立弹窗仍沿自身焦点树处理按键。
        return event.type == KeyEventType.KeyDown && event.key == Key.Spacebar && onKeyEvent(event)
    }
}

@Composable
actual fun PlayerKeyboardEffect(
    onKeyEvent: (KeyEvent) -> Boolean,
    onCancel: () -> Unit,
) {
    val window = DesktopPlayerFullscreenWindow.window
    val latestOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val latestOnCancel by rememberUpdatedState(onCancel)
    DisposableEffect(window) {
        val owner = Any()
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
            DesktopPlayerKeyboard.bind(owner, { latestOnKeyEvent(it) }, { latestOnCancel() })
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
