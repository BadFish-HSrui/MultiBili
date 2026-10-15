package tv.hsrui.bolo.player.base

import com.jogamp.opengl.GL
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLCapabilities
import com.jogamp.opengl.GLDrawableFactory
import com.jogamp.opengl.GLEventListener
import com.jogamp.opengl.GLProfile
import com.jogamp.opengl.Threading
import com.jogamp.opengl.util.AnimatorBase
import com.jogamp.opengl.util.FPSAnimator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow
import java.awt.EventQueue
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** 一个 renderer 独占一个 GL 上下文；mpv 控制调用仍由 backend 的串行 dispatcher 处理。 */
internal class BoloDesktopMpvRenderer(
    private val handle: Long,
    private val output: BoloDesktopVideoOutput,
    private val direct: Boolean,
    private val onFailure: (BoloDesktopMpvRenderer, Throwable) -> Unit,
) : GLEventListener {
    val ready = CompletableDeferred<Unit>()
    private var drawable: GLAutoDrawable? = null
    private var animator: FPSAnimator? = null
    private var nativeOutput: BoloDesktopNativeVideoOutput? = null
    private val failed = AtomicBoolean(false)
    @Volatile private var closed = false
    private var initialized = false
    private val redrawRequested = AtomicBoolean(true)
    private var framebuffer = 0
    private var texture = 0
    private var pixelWidth = 0
    private var pixelHeight = 0
    private var pixels = ByteArray(0)
    private var pixelBuffer = ByteBuffer.allocateDirect(0)

    suspend fun start() {
        withContext(Dispatchers.Swing) { output.attach(this@BoloDesktopMpvRenderer, direct) }
        if (direct) {
            val window = withContext(Dispatchers.Swing) {
                checkNotNull(DesktopPlayerFullscreenWindow.window) { "播放器窗口不可用" }
            }
            val next = BoloDesktopNativeVideoOutput(handle, output, window, { ready.complete(Unit) }, ::fail)
            nativeOutput = next
            next.start()
            if (System.getProperty("os.name").startsWith("Mac")) return
        }
        withContext(Dispatchers.Default) {
            Threading.disableSingleThreading()
            val profile = GLProfile.get(GLProfile.GL3)
            val capabilities = GLCapabilities(profile).apply {
                doubleBuffered = false
                isFBO = true
            }
            val view = GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null, capabilities, null, 1, 1)
            drawable = view
            view.autoSwapBufferMode = false
            view.addGLEventListener(this@BoloDesktopMpvRenderer)
            val driver = FPSAnimator(view, 60, true).apply {
                setModeBits(false, AnimatorBase.MODE_EXPECT_AWT_RENDERING_THREAD)
                setUncaughtExceptionHandler { _, _, cause -> fail(cause) }
            }
            animator = driver
            check(driver.start()) { "视频渲染线程启动失败" }
        }
    }

    override fun init(drawable: GLAutoDrawable) {
        // native 初始化统一延后到后台 display。
        redrawRequested.set(true)
    }

    override fun display(drawable: GLAutoDrawable) {
        if (closed || failed.get() || EventQueue.isDispatchThread()) return
        val gl = drawable.gl
        try {
            if (!initialized) {
                Thread.currentThread().name = "BoloMpv-Render-${Integer.toHexString(System.identityHashCode(this))}"
                check(BoloMpvNative.renderCreate(handle) >= 0) { "OpenGL 播放器初始化失败" }
                initialized = true
            }
            val size = output.size
            val width = size.width.coerceAtLeast(1)
            val height = size.height.coerceAtLeast(1)
            val sizeChanged = width != pixelWidth || height != pixelHeight
            val native = nativeOutput
            val geometryChanged = native?.prepareFrame(width, height) ?: false
            val dirty = BoloMpvNative.renderDirty(handle)
            val redraw = redrawRequested.getAndSet(false)
            if (!dirty && !redraw && !sizeChanged && !geometryChanged) return
            if (!direct && sizeChanged) allocateFramebuffer(gl, width, height)
            pixelWidth = width
            pixelHeight = height
            if (native != null) {
                val target = native.beginFrame()
                var rendered = false
                try {
                    check(BoloMpvNative.render(handle, target, width, height, false) >= 0) { "视频帧渲染失败" }
                    rendered = true
                } finally {
                    restoreFramebuffer(gl)
                    native.endFrame(rendered)
                }
            } else {
                check(BoloMpvNative.render(handle, framebuffer, width, height, false) >= 0) { "视频帧渲染失败" }
                gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, framebuffer)
                gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4)
                pixelBuffer.clear()
                gl.glReadPixels(0, 0, width, height, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE, pixelBuffer)
                pixelBuffer.get(pixels, 0, width * height * 4)
                // makeRaster 复制像素。回读数组可以复用，已发布图像始终不可变。
                output.publish(this, Image.makeRaster(
                    ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, width * 4,
                ))
            }
            ready.complete(Unit)
        } catch (error: Throwable) {
            fail(error)
        } finally {
            restoreFramebuffer(gl)
        }
    }

    private fun allocateFramebuffer(gl: GL, width: Int, height: Int) {
        val byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4)
        if (framebuffer == 0) {
            val names = IntArray(1)
            gl.glGenFramebuffers(1, names, 0)
            framebuffer = names[0]
            gl.glGenTextures(1, names, 0)
            texture = names[0]
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture)
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE, null)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, framebuffer)
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, texture, 0)
        check(gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE) { "离屏视频缓冲初始化失败" }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0)
        if (pixels.size < byteCount) {
            pixels = ByteArray(byteCount)
            // 不将 JVM 数组 pin 在阻塞的 glReadPixels 中，避免影响 GC 和 UI 线程。
            pixelBuffer = ByteBuffer.allocateDirect(byteCount)
        }
    }

    private fun restoreFramebuffer(gl: GL) {
        // 保留宿主真实 FBO 的恢复逻辑，避免 libmpv 绑定 FBO 0 后影响 JOGL。
        gl.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER, gl.defaultDrawFramebuffer)
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, gl.defaultReadFramebuffer)
    }

    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        redrawRequested.set(true)
    }

    override fun dispose(drawable: GLAutoDrawable) {
        if (initialized) {
            BoloMpvNative.renderFree(handle)
            initialized = false
        }
        nativeOutput?.releaseRender()
        val gl = drawable.gl
        if (framebuffer != 0) gl.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        if (texture != 0) gl.glDeleteTextures(1, intArrayOf(texture), 0)
        framebuffer = 0
        texture = 0
        pixelWidth = 0
        pixelHeight = 0
    }

    private fun fail(error: Throwable) {
        if (closed || !failed.compareAndSet(false, true)) return
        if (!ready.completeExceptionally(error)) onFailure(this, error)
    }

    suspend fun close() = withContext(NonCancellable) {
        closed = true
        val view = drawable
        try {
            try {
                nativeOutput?.close()
            } finally {
                withContext(Dispatchers.Default) {
                    // 停止帧调用后在原上下文释放 Render API 和视频资源。
                    animator?.stop()
                    view?.destroy()
                }
            }
        } finally {
            animator = null
            nativeOutput = null
            drawable = null
            withContext(Dispatchers.Swing) {
                output.detach(this@BoloDesktopMpvRenderer)
            }
            pixels = ByteArray(0)
            pixelBuffer = ByteBuffer.allocateDirect(0)
        }
    }
}
