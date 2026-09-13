package tv.hsrui.bolo.player.base

import com.jogamp.opengl.GL
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLCapabilities
import com.jogamp.opengl.GLEventListener
import com.jogamp.opengl.GLProfile
import com.jogamp.opengl.awt.GLJPanel
import com.jogamp.opengl.util.FPSAnimator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel

internal actual class BoloMpvBackend actual constructor() {
    private val handle = BoloMpvNative.create("desktop").also { check(it != 0L) { "libmpv 初始化失败" } }
    init {
        if (System.getProperty("os.name").startsWith("Linux")) {
            try { configureBoloMpvCertificates(handle, java.io.File(System.getProperty("java.io.tmpdir"), "bolo-mpv-certificates")) }
            catch (error: Exception) { BoloMpvNative.destroy(handle); throw error }
        }
    }
    private val outputMutex = Mutex()
    private val outputReady = CompletableDeferred<Unit>()
    private var host: JPanel? = null
    private var panel: GLJPanel? = null
    private var animator: FPSAnimator? = null
    @Volatile private var closed = false
    private var destroyed = false
    private var initialized = false
    private var resized = true

    actual suspend fun bind(output: Any) = outputMutex.withLock {
        withContext(Dispatchers.Swing) {
            if (closed || host === output) return@withContext
            detach()
            if (closed) return@withContext
            try {
                host = output as JPanel
                val view = GLJPanel(GLCapabilities(GLProfile.get(GLProfile.GL3)))
                panel = view
                view.addGLEventListener(object : GLEventListener {
                    override fun init(drawable: GLAutoDrawable) {
                        val result = BoloMpvNative.renderCreate(handle)
                        initialized = result >= 0
                        if (initialized) outputReady.complete(Unit)
                        else outputReady.completeExceptionally(IllegalStateException("OpenGL 播放器初始化失败（$result）"))
                    }
                    override fun display(drawable: GLAutoDrawable) {
                        if (!initialized || closed) return
                        val dirty = BoloMpvNative.renderDirty(handle)
                        if (!dirty && !resized) return
                        resized = false
                        val fbo = IntArray(1)
                        drawable.gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, fbo, 0)
                        BoloMpvNative.render(handle, fbo[0], drawable.surfaceWidth, drawable.surfaceHeight, true)
                    }
                    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) { resized = true }
                    override fun dispose(drawable: GLAutoDrawable) {
                        if (initialized) BoloMpvNative.renderFree(handle)
                        initialized = false
                    }
                })
                host?.add(view, BorderLayout.CENTER)
                host?.revalidate()
                animator = FPSAnimator(view, 60, true).also { it.start() }
            } catch (error: Exception) { outputReady.completeExceptionally(error) }
        }
    }

    actual suspend fun unbind() { closed = true; outputMutex.withLock { detach() } }

    private suspend fun detach() {
        // 不在 EDT 等待 Animator：其 AWT 任务也可能等待 EDT。
        withContext(Dispatchers.Default) { animator?.stop() }
        animator = null
        withContext(Dispatchers.Swing) {
            panel?.destroy()
            panel?.let { host?.remove(it) }
            host?.revalidate()
            panel = null
            host = null
        }
    }
    actual suspend fun awaitOutput() { outputReady.await() }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else BoloMpvNative.load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else BoloMpvNative.pause(handle, paused)
    actual fun speed(speed: Double) = if (destroyed) -3 else BoloMpvNative.speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else BoloMpvNative.volume(handle, volume)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else BoloMpvNative.seek(handle, seconds, request)
    actual fun poll() = if (destroyed) null else BoloMpvNative.poll(handle)?.toMpvEvent()
    actual fun info() = if (destroyed) null else BoloMpvNative.info(handle)?.let {
        BoloMpvInfoSnapshot.parse(it.decodeToString(), this)
    }
    actual fun stop() = if (destroyed) -3 else BoloMpvNative.stop(handle)
    actual fun destroy() { if (!destroyed) { destroyed = true; BoloMpvNative.destroy(handle) } }
    actual suspend fun setAudioActive(active: Boolean) = true
}
