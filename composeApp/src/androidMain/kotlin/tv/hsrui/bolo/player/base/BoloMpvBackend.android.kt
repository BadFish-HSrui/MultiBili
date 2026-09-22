package tv.hsrui.bolo.player.base

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.utils.url.AppContext
import tv.hsrui.bolo.player.session.BoloMediaSessionService

internal actual class BoloMpvBackend actual constructor() {
    private val handle = BoloMpvNative.create("android").also { check(it != 0L) { "libmpv 初始化失败" } }
    init {
        try { configureBoloMpvCertificates(handle, java.io.File(AppContext.instance.cacheDir, "mpv-certificates")) }
        catch (error: Exception) { BoloMpvNative.destroy(handle); throw error }
    }
    private val outputMutex = Mutex()
    private var outputReady = CompletableDeferred<Unit>()
    private val resizeScope = CoroutineScope(SupervisorJob() + boloMpvDispatcher)
    private var resizeJob: Job? = null
    private var outputWidth = 0
    private var outputHeight = 0
    private var texture: TextureView? = null
    private var surface: Surface? = null
    private var retainedTexture: SurfaceTexture? = null
    private var surfaceBound = false
    @Volatile private var closing = false
    private var destroyed = false // 仅由串行 native dispatcher 访问。

    actual suspend fun bind(output: Any) = withContext(Dispatchers.Main.immediate + NonCancellable) {
        outputMutex.withLock {
            if (closing || texture === output) return@withLock
            try {
                texture?.surfaceTextureListener = null
                val view = output as TextureView
                if (closing) return@withLock
                texture = view
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(value: SurfaceTexture, width: Int, height: Int) { resize(width, height); connect(view, value) }
                    override fun onSurfaceTextureSizeChanged(value: SurfaceTexture, width: Int, height: Int) { resize(width, height) }
                    override fun onSurfaceTextureUpdated(value: SurfaceTexture) { }
                    // TextureView 临时离开窗口时保留输出；native 销毁完成后统一释放。
                    override fun onSurfaceTextureDestroyed(value: SurfaceTexture) = false
                }
                view.surfaceTexture?.let { resize(view.width, view.height); connect(view, it) }
            } catch (error: Exception) { outputReady.completeExceptionally(error) }
        }
    }

    private fun resize(width: Int, height: Int) {
        if (closing || width <= 0 || height <= 0) return
        outputWidth = width
        outputHeight = height
        resizeJob?.cancel()
        resizeJob = resizeScope.launch {
            if (!destroyed) BoloMpvNative.surfaceSize(handle, width, height)
        }
    }

    private fun connect(view: TextureView, value: SurfaceTexture) {
        if (closing) return
        val retained = retainedTexture
        if (retained != null) {
            if (value !== retained) view.setSurfaceTexture(retained)
            return
        }
        retainedTexture = value
        surface = Surface(value)
        // wid 尚未加载媒体，只保存稳定的 Surface 引用；普通 native 调用由 awaitOutput 串行提交。
        outputReady.complete(Unit)
    }

    actual suspend fun detachOutput(output: Any) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        outputMutex.withLock {
            if (texture !== output || closing) return@withLock
            withContext(boloMpvDispatcher) {
                if (!destroyed) { videoEnabled(false); BoloMpvNative.surface(handle, null) }
            }
            resizeJob?.cancel()
            texture?.surfaceTextureListener = null
            surface?.release()
            if (texture?.isAvailable != true) retainedTexture?.release()
            texture = null
            retainedTexture = null
            surface = null
            surfaceBound = false
            if (outputReady.isCompleted) outputReady = CompletableDeferred()
        }
    }

    actual suspend fun unbind() = withContext(Dispatchers.Main.immediate) {
        outputMutex.withLock {
            closing = true
            // 先等待 core 退出，确保 TextureView 重新接管纹理后可安全销毁它。
            withContext(boloMpvDispatcher) { destroy() }
            surface?.release()
            surface = null
            texture?.surfaceTextureListener = null
            // 是否脱离窗口只在 Main 判断，涵盖等待 core 销毁期间发生的 detach。
            if (texture?.isAvailable != true) retainedTexture?.release()
            retainedTexture = null
            texture = null
        }
    }
    actual suspend fun awaitOutput() {
        outputReady.await()
        val output = surface ?: error("视频 Surface 不可用")
        val width = outputWidth
        val height = outputHeight
        withContext(boloMpvDispatcher) {
            check(!destroyed) { "播放器已关闭" }
            check(BoloMpvNative.surfaceSize(handle, width, height) >= 0) { "视频 Surface 尺寸更新失败" }
            if (!surfaceBound) {
                check(BoloMpvNative.surface(handle, output) >= 0) { "视频 Surface 绑定失败" }
                surfaceBound = true
            }
        }
    }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else BoloMpvNative.load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun videoEnabled(enabled: Boolean) = if (destroyed) -3 else BoloMpvNative.videoEnabled(handle, enabled)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else BoloMpvNative.pause(handle, paused)
    actual fun speed(speed: Double) = if (destroyed) -3 else BoloMpvNative.speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else BoloMpvNative.volume(handle, volume)
    actual fun loudness(gainDb: Double, dynamicEnabled: Boolean, targetLufs: Double, rangeLu: Double, truePeakDbtp: Double) = if (destroyed) -3 else BoloMpvNative.loudness(handle, gainDb, dynamicEnabled, targetLufs, rangeLu, truePeakDbtp)
    actual fun mergeAudioChannels(enabled: Boolean) = if (destroyed) -3 else BoloMpvNative.mergeAudioChannels(handle, enabled)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else BoloMpvNative.seek(handle, seconds, request)
    actual fun poll() = if (destroyed) null else BoloMpvNative.poll(handle)?.toMpvEvent()
    actual fun info() = if (destroyed) null else BoloMpvNative.info(handle)?.let {
        BoloMpvInfoSnapshot.parse(it.decodeToString(), this)
    }
    actual fun stop() = if (destroyed) -3 else BoloMpvNative.stop(handle)
    actual fun destroy() {
        if (destroyed) return
        destroyed = true
        resizeScope.cancel()
        BoloMpvNative.destroy(handle)
    }
    actual suspend fun setAudioActive(active: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        BoloMediaSessionService.setAudioActive(this@BoloMpvBackend, active)
    }
}
