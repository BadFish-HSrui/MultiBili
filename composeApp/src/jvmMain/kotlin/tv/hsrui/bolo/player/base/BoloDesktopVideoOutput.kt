package tv.hsrui.bolo.player.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skiko.GraphicsApi
import java.awt.BorderLayout
import java.awt.Color
import java.awt.EventQueue
import javax.swing.JPanel

/** 宿主只传递尺寸和已完成的不可变帧，不参与 OpenGL 渲染。 */
internal class BoloDesktopVideoOutput {
    val panel = JPanel(BorderLayout()).apply { background = Color.BLACK }
    var direct by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var directDisabled = false
        private set
    @Volatile var size = IntSize.Zero
    private val failureChannel = Channel<Pair<BoloMpvBackend, Throwable>>(Channel.CONFLATED)
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
        // macOS 实测 GLCanvas 遮挡 Compose 叠加；Windows 尚未验收。
        // 只有叠加、输入及宿主退出都通过验收的后端才能加入此集合。
        val verifiedApis = emptySet<GraphicsApi>()
        return !directDisabled && candidate && window?.renderApi in verifiedApis &&
            System.getProperty("compose.interop.blending") == "true"
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

    fun failed(backend: BoloMpvBackend, cause: Throwable) {
        failureChannel.trySend(backend to cause)
    }

    fun handleFailure(controller: BoloPlayerController, backend: BoloMpvBackend, cause: Throwable) {
        check(EventQueue.isDispatchThread())
        if (controller.backend.value !== backend) return
        if (direct && !directDisabled) {
            disableDirect()
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
