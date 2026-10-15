package tv.hsrui.bolo.player.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skiko.GraphicsApi
import java.awt.EventQueue

/** 宿主传递布局和已完成的不可变帧，不参与 OpenGL 渲染。 */
internal class BoloDesktopVideoOutput {
    var direct by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var directDisabled = false
        private set
    @Volatile var size = IntSize.Zero
    var bounds = ComposeRect.Zero
        private set
    var clipBounds = ComposeRect.Zero
        private set
    var visible = false
        private set
    var geometryListener: (() -> Unit)? = null
    private val failureChannel = Channel<Triple<BoloMpvBackend, Any, Throwable>>(Channel.CONFLATED)
    val failures = failureChannel.receiveAsFlow()
    private val frameLock = Any()
    private var owner: Any? = null
    private var pending: Image? = null
    private var displayed: Image? = null
    private var invalidationQueued = false
    private var revision by mutableLongStateOf(0L)

    fun supportsDirect(window: ComposeWindow?): Boolean {
        val candidate = when {
            System.getProperty("os.name").startsWith("Mac") -> window?.renderApi == GraphicsApi.METAL
            System.getProperty("os.name").startsWith("Windows") -> window?.renderApi == GraphicsApi.DIRECT3D
            else -> false // 当前 Compose OpenGL 后端不支持同等 interop blending。
        }
        // macOS 和 Windows 均使用同一宿主下层的原生视频图层。
        // 只有叠加、输入及宿主退出都通过验收的后端才能加入此集合。
        val verifiedApis = setOf(GraphicsApi.METAL, GraphicsApi.DIRECT3D)
        return !directDisabled && candidate && window?.renderApi in verifiedApis &&
            System.getProperty("compose.interop.blending") == "true"
    }

    fun updateGeometry(next: ComposeRect, isVisible: Boolean, clip: ComposeRect = next) {
        check(EventQueue.isDispatchThread())
        if (bounds == next && visible == isVisible && clipBounds == clip) return
        bounds = next
        clipBounds = clip
        visible = isVisible
        geometryListener?.invoke()
    }

    fun attach(next: Any, useDirect: Boolean) {
        check(EventQueue.isDispatchThread())
        synchronized(frameLock) {
            clearFrames()
            owner = next
        }
        direct = useDirect
        error = null
    }

    fun detach(previous: Any) {
        check(EventQueue.isDispatchThread())
        synchronized(frameLock) {
            if (owner !== previous) return
            owner = null
            clearFrames()
        }
        direct = false
    }

    fun disableDirect() {
        check(EventQueue.isDispatchThread())
        directDisabled = true
    }

    fun showError(cause: Throwable) {
        check(EventQueue.isDispatchThread())
        error = "视频输出失败：${cause.message ?: cause.javaClass.simpleName}"
    }

    fun failed(backend: BoloMpvBackend, source: Any, cause: Throwable) {
        synchronized(frameLock) {
            if (owner === source) failureChannel.trySend(Triple(backend, source, cause))
        }
    }

    fun handleFailure(controller: BoloPlayerController, backend: BoloMpvBackend, source: Any, cause: Throwable) {
        check(EventQueue.isDispatchThread())
        // 同一 backend 也可能解绑后重建输出，队列中的旧 renderer 错误不能影响新宿主。
        if (controller.backend.value !== backend || synchronized(frameLock) { owner !== source }) return
        if (direct && !directDisabled) {
            disableDirect()
            println("Bolo 视频直出运行失败，改用离屏输出：${cause.message}")
            controller.rebuild()
        } else {
            showError(cause)
            controller.release()
        }
    }

    fun publish(source: Any, image: Image) {
        synchronized(frameLock) {
            if (owner !== source) {
                image.close()
                return
            }
            pending?.close()
            pending = image
            // EDT 忙碌时只积累一个失效通知和最新一帧。
            if (invalidationQueued) return
            invalidationQueued = true
        }
        EventQueue.invokeLater {
            synchronized(frameLock) { invalidationQueued = false }
            revision++
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        revision // 仅在绘制阶段订阅帧变化，避免逐帧重组播放器 UI。
        synchronized(frameLock) {
            pending?.let { next ->
                displayed?.close()
                displayed = next
                pending = null
            }
            // Skia 记录绘制时持有图像的 native 引用；后台从不改写其像素。
            displayed?.let { canvas.drawImageRect(it, Rect.makeWH(width, height)) }
        }
    }

    private fun clearFrames() {
        pending?.close()
        displayed?.close()
        pending = null
        displayed = null
        revision++
    }
}
