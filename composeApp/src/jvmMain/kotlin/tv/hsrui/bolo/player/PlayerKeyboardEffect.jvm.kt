package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.AWTEvent
import java.awt.Toolkit
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
            // 播放器容器和音量弹窗也会在预览阶段接管按键，统一询问其释放状态。
            val handled = onKeyEvent?.invoke(event) == true
            if (captured || handled) return true
        }
        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) cancel()
        return false
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val handled = onKeyEvent?.invoke(event) == true
        if (handled && event.type == KeyEventType.KeyDown) pressedKeys.add(event.key)
        return handled
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
