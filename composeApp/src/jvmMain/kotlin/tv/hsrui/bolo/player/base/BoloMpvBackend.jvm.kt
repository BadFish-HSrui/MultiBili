package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow

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
    private var output: BoloDesktopVideoOutput? = null
    private var renderer: BoloDesktopMpvRenderer? = null
    @Volatile private var closed = false
    private var destroyed = false

    actual suspend fun bind(output: Any) = outputMutex.withLock {
        if (closed || this.output === output) return@withLock
        check(this.output == null) { "视频输出已绑定" }
        val host = output as BoloDesktopVideoOutput
        this.output = host
        try {
            var direct = withContext(Dispatchers.Swing) {
                host.supportsDirect(DesktopPlayerFullscreenWindow.window)
            }
            while (!closed) {
                val next = BoloDesktopMpvRenderer(handle, host, direct) { cause ->
                    if (!closed) host.failed(this@BoloMpvBackend, cause)
                }
                renderer = next
                try {
                    next.start()
                    withTimeout(if (direct) 3_000L else 4_000L) { next.ready.await() }
                    outputReady.complete(Unit)
                    break
                } catch (error: Throwable) {
                    next.close()
                    renderer = null
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    if (!direct || closed) throw error
                    withContext(Dispatchers.Swing) { host.disableDirect() }
                    println("Bolo mpv GPU 输出初始化失败，改用离屏输出：${error.message}")
                    direct = false
                }
            }
        } catch (error: TimeoutCancellationException) {
            withContext(Dispatchers.Swing) { host.showError(error) }
            outputReady.completeExceptionally(error)
        } catch (error: CancellationException) {
            outputReady.completeExceptionally(error)
            throw error
        } catch (error: Throwable) {
            withContext(Dispatchers.Swing) { host.showError(error) }
            outputReady.completeExceptionally(error)
        }
    }

    actual suspend fun unbind() {
        closed = true
        outputMutex.withLock {
            renderer?.close()
            renderer = null
            output = null
        }
    }
    actual suspend fun awaitOutput() { outputReady.await() }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else BoloMpvNative.load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else BoloMpvNative.pause(handle, paused)
    actual fun speed(speed: Double) = if (destroyed) -3 else BoloMpvNative.speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else BoloMpvNative.volume(handle, volume)
    actual fun mergeAudioChannels(enabled: Boolean) = if (destroyed) -3 else BoloMpvNative.mergeAudioChannels(handle, enabled)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else BoloMpvNative.seek(handle, seconds, request)
    actual fun poll() = if (destroyed) null else BoloMpvNative.poll(handle)?.toMpvEvent()
    actual fun info() = if (destroyed) null else BoloMpvNative.info(handle)?.let {
        BoloMpvInfoSnapshot.parse(it.decodeToString(), this)
    }
    actual fun stop() = if (destroyed) -3 else BoloMpvNative.stop(handle)
    actual fun destroy() { if (!destroyed) { destroyed = true; BoloMpvNative.destroy(handle) } }
    actual suspend fun setAudioActive(active: Boolean) = true
}
