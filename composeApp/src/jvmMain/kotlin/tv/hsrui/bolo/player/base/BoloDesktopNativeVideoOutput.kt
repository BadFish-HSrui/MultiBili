package tv.hsrui.bolo.player.base

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.EventQueue
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/** 原生图层只呈现视频，位置和输入继续由 Compose 宿主管理。 */
internal class BoloDesktopNativeVideoOutput(
    private val player: Long,
    private val output: BoloDesktopVideoOutput,
    private val window: ComposeWindow,
    private val onReady: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    @Volatile private var handle = 0L
    private var layer: SkiaLayer? = null
    private var contentHandle = 0L
    @Volatile private var geometry = IntArray(0)
    private var appliedGeometry: IntArray? = null
    private var renderWidth = 0
    private var renderHeight = 0
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val componentListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) = updateGeometry()
        override fun componentShown(event: ComponentEvent) = updateGeometry()
        override fun componentHidden(event: ComponentEvent) = updateGeometry()
        override fun componentMoved(event: ComponentEvent) = updateGeometry()
    }
    private val windowListener = object : WindowAdapter() {
        override fun windowIconified(event: WindowEvent) = updateGeometry()
        override fun windowDeiconified(event: WindowEvent) = updateGeometry()
    }
    private val geometryListener: () -> Unit = ::updateGeometry

    suspend fun start() = withContext(Dispatchers.Swing) {
        check(!closed.get())
        loadBoloMpvLibrary()
        if (windows) {
            val host = checkNotNull(findLayer(window)) { "Compose 原生视频宿主不可用" }
            check(host.renderApi == GraphicsApi.DIRECT3D && host.transparency) { "原生视频需要透明 Direct3D 宿主" }
            contentHandle = host.contentHandle
            check(contentHandle != 0L) { "Compose 视频窗口尚未创建" }
            layer = host
        } else {
            val jawt = File(System.getProperty("java.home"), "lib/${System.mapLibraryName("jawt")}")
            handle = create(player, window, jawt.absolutePath)
            check(handle != 0L) { "无法创建原生视频图层" }
        }
        window.addComponentListener(componentListener)
        window.addWindowListener(windowListener)
        output.geometryListener = geometryListener
        updateGeometry()
    }

    private fun updateGeometry() {
        check(EventQueue.isDispatchThread())
        val current = handle
        if (closed.get() || failed.get()) return
        val bounds = output.bounds
        val scale = window.graphicsConfiguration.defaultTransform.scaleX
        val visible = output.visible && window.isShowing && window.extendedState and Frame.ICONIFIED == 0 &&
            bounds.width > 0 && bounds.height > 0
        try {
            if (windows) {
                val host = checkNotNull(layer)
                check(host.contentHandle == contentHandle && host.renderApi == GraphicsApi.DIRECT3D) { "Compose 视频宿主已更换" }
                val origin = SwingUtilities.convertPoint(window.contentPane, 0, 0, host.canvas)
                val clip = if (visible) output.clipBounds.intersect(bounds) else bounds
                geometry = intArrayOf((bounds.left + origin.x * scale).roundToInt(),
                    (bounds.top + origin.y * scale).roundToInt(), if (visible) 1 else 0,
                    (clip.left - bounds.left).roundToInt(), (clip.top - bounds.top).roundToInt(),
                    (clip.right - bounds.left).roundToInt(), (clip.bottom - bounds.top).roundToInt())
                return
            }
            if (current == 0L) return
            val refreshRate = window.graphicsConfiguration.device.displayMode.refreshRate.takeIf { it in 1..240 } ?: 60
            check(update(current, window, bounds.left / scale, bounds.top / scale,
                bounds.width / scale, bounds.height / scale, scale, refreshRate.toDouble(), visible) >= 0) { "原生视频图层更新失败" }
        } catch (error: Throwable) {
            fail(error)
        }
    }

    @Suppress("unused") // JNI 回调签名必须保持稳定。
    private fun onNativeState(source: Long, status: Int, message: String?) {
        if (closed.get() || source != handle) return
        if (status == 1) onReady() else fail(IllegalStateException(message ?: "原生视频输出失败"))
    }

    private fun fail(error: Throwable) {
        if (!closed.get() && failed.compareAndSet(false, true)) onFailure(error)
    }

    // Windows 资源只在 renderer 的 GL 线程创建、使用和销毁；EDT 仅发布布局快照。
    internal fun prepareFrame(width: Int, height: Int): Boolean {
        check(windows)
        val next = geometry
        check(next.size == 7) { "原生视频布局不可用" }
        if (handle == 0L) {
            handle = windowsCreate(contentHandle)
            check(handle != 0L) { "无法创建 Windows 原生视频输出" }
        }
        if (next === appliedGeometry && width == renderWidth && height == renderHeight) return false
        windowsUpdate(handle, next[0], next[1], width, height, next[2] != 0, next[3], next[4], next[5], next[6])
        appliedGeometry = next
        renderWidth = width
        renderHeight = height
        return true
    }

    internal fun beginFrame(): Int = windowsBegin(handle).also { check(it != 0) { "共享视频帧缓冲不可用" } }

    internal fun endFrame(present: Boolean) = windowsEnd(handle, present)

    internal fun releaseRender() {
        if (!windows) return
        val previous = handle
        handle = 0L
        if (previous != 0L) windowsDestroy(previous)
    }

    private fun findLayer(container: Container): SkiaLayer? = container.components.firstNotNullOfOrNull {
        (it as? SkiaLayer) ?: (it as? Container)?.let(::findLayer)
    }

    suspend fun close() = withContext(NonCancellable) {
        if (!closed.compareAndSet(false, true)) return@withContext
        val previous = withContext(Dispatchers.Swing) {
            if (output.geometryListener === geometryListener) output.geometryListener = null
            window.removeComponentListener(componentListener)
            window.removeWindowListener(windowListener)
            // Windows 的在途帧仍可能使用句柄，由 drawable.dispose 在原上下文释放。
            if (windows) 0L else handle.also { handle = 0L }
        }
        if (previous != 0L) withContext(Dispatchers.Default) { destroy(previous) }
    }

    private external fun create(player: Long, window: ComposeWindow, jawtPath: String): Long
    private external fun update(handle: Long, window: ComposeWindow, x: Double, y: Double,
        width: Double, height: Double, scale: Double, refreshRate: Double, visible: Boolean): Int
    private external fun destroy(handle: Long)
    private external fun windowsCreate(hwnd: Long): Long
    private external fun windowsUpdate(handle: Long, x: Int, y: Int, width: Int, height: Int, visible: Boolean,
        clipLeft: Int, clipTop: Int, clipRight: Int, clipBottom: Int)
    private external fun windowsBegin(handle: Long): Int
    private external fun windowsEnd(handle: Long, present: Boolean)
    private external fun windowsDestroy(handle: Long)
}
