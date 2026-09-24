package tv.hsrui.bolo

import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.swing.SkiaSwingLayer
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

@OptIn(ExperimentalSkikoApi::class)
internal class DesktopInputMethod(private val window: ComposeWindow) : AutoCloseable {
    private val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val bootstrapComponent = FocusComponent(inputMethodsEnabled = true)
    private val parkingComponent = FocusComponent(inputMethodsEnabled = false)
    private var attached = false
    private var updatePending = false
    private var inputComponent: Component? = null
    private val focusListener = PropertyChangeListener { scheduleUpdate() }
    private val keyDispatcher = KeyEventDispatcher(::dispatchKeyEvent)
    private val windowListener = object : WindowAdapter() {
        override fun windowOpened(event: WindowEvent) = scheduleUpdate()
        override fun windowGainedFocus(event: WindowEvent) = scheduleUpdate()
        override fun windowClosed(event: WindowEvent) = close()
    }

    fun attach() {
        if (attached) return
        attached = true
        window.layeredPane.add(bootstrapComponent)
        window.layeredPane.add(parkingComponent)
        focusManager.addPropertyChangeListener("focusOwner", focusListener)
        focusManager.addKeyEventDispatcher(keyDispatcher)
        window.addWindowListener(windowListener)
        window.addWindowFocusListener(windowListener)
        scheduleUpdate()
    }

    fun scheduleUpdate() {
        if (!attached || updatePending) return
        updatePending = true
        // 等待文本会话和临时 AWT 焦点切换完成，再同步窗口的实际输入请求。
        EventQueue.invokeLater {
            updatePending = false
            if (!attached || !window.isDisplayable || !window.isFocused) return@invokeLater
            val owner = window.focusOwner
            if (owner != null && (owner is SkiaSwingLayer || (owner.parent as? SkiaLayer)?.canvas === owner)) {
                inputComponent = owner
            }
            val component = inputComponent?.takeIf {
                it.isShowing && SwingUtilities.getWindowAncestor(it) === window
            } ?: return@invokeLater
            if (component.inputMethodRequests != null) {
                if (owner === parkingComponent || owner === bootstrapComponent) component.requestFocusInWindow()
            } else if (owner === component) {
                // 临时交接保留 Compose 内部焦点，避免中断按钮导航和文本会话恢复。
                // 先建立输入上下文，再交给禁用输入法的组件，使首次启动和结束编辑走相同路径。
                bootstrapComponent.requestTemporaryFocus()
            } else if (owner === bootstrapComponent) {
                parkingComponent.requestTemporaryFocus()
            }
        }
    }

    private fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val owner = focusManager.focusOwner
        if (!attached || !window.isFocused ||
            (owner !== parkingComponent && owner !== bootstrapComponent) || event.component !== owner) return false
        val component = inputComponent?.takeIf {
            it.isShowing && SwingUtilities.getWindowAncestor(it) === window
        } ?: return false
        // 非编辑时保留快捷键和导航，避免文字事件沿焦点链写入已结束输入会话的文本框。
        if (event.id == KeyEvent.KEY_TYPED && component.inputMethodRequests == null) return true
        // redispatchEvent 绕过焦点分发器，保留 Compose 原有按键处理且不会递归转发。
        event.source = component
        focusManager.redispatchEvent(component, event)
        return true
    }

    override fun close() {
        if (!attached) return
        attached = false
        focusManager.removePropertyChangeListener("focusOwner", focusListener)
        focusManager.removeKeyEventDispatcher(keyDispatcher)
        window.removeWindowListener(windowListener)
        window.removeWindowFocusListener(windowListener)
        if ((parkingComponent.isFocusOwner || bootstrapComponent.isFocusOwner) && window.isFocused) {
            inputComponent?.requestFocusInWindow()
        }
        window.layeredPane.remove(bootstrapComponent)
        window.layeredPane.remove(parkingComponent)
        inputComponent = null
    }

    private class FocusComponent(inputMethodsEnabled: Boolean) : Component() {
        init {
            isFocusable = true
            focusTraversalKeysEnabled = false
            if (inputMethodsEnabled) enableEvents(AWTEvent.KEY_EVENT_MASK)
            enableInputMethods(inputMethodsEnabled)
            setBounds(0, 0, 0, 0)
        }

        fun requestTemporaryFocus(): Boolean = super.requestFocusInWindow(true)
    }
}
