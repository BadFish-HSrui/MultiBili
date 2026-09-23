package tv.hsrui.bolo.utils.url

import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent

actual fun setSystemLinkHandlingEnabled(enabled: Boolean): Boolean = !enabled

actual fun isSystemLinkHandlingEnabled(): Boolean = false

internal actual fun openSystemLinkSettings(): Boolean = false

internal actual fun observeExternalLinkActivation(onActivation: () -> Unit): () -> Unit {
    var disposed = false
    var active = Window.getWindows().any { it.isFocused }
    val listener = AWTEventListener { event ->
        if (event is WindowEvent) {
            when (event.id) {
                WindowEvent.WINDOW_LOST_FOCUS -> if (event.oppositeWindow == null) {
                    EventQueue.invokeLater {
                        if (!disposed && Window.getWindows().none { it.isFocused }) active = false
                    }
                }
                WindowEvent.WINDOW_GAINED_FOCUS -> {
                    if (!active && event.oppositeWindow == null) onActivation()
                    active = true
                }
            }
        }
    }
    val toolkit = Toolkit.getDefaultToolkit()
    toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_FOCUS_EVENT_MASK)
    if (active) onActivation()
    return {
        disposed = true
        toolkit.removeAWTEventListener(listener)
    }
}
