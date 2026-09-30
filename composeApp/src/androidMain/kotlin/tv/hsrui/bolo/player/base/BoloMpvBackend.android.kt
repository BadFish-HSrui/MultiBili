package tv.hsrui.bolo.player.base

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.Window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.utils.url.AppContext
import tv.hsrui.bolo.player.session.BoloMediaSessionService

internal actual class BoloMpvBackend actual constructor() {
    actual val retainsPausedResources: Boolean get() = false
    actual fun retainedPosition(generation: Long, positionMs: Long): Long? = null
    private val handle = BoloMpvNative.create("android").also { check(it != 0L) { "libmpv 初始化失败" } }
    init {
        try { configureBoloMpvCertificates(handle, java.io.File(AppContext.instance.cacheDir, "mpv-certificates")) }
        catch (error: Exception) { BoloMpvNative.destroy(handle); throw error }
    }
    private val outputScope = CoroutineScope(SupervisorJob() + boloMpvDispatcher)
    private var outputJob: Job? = null
    private var outputReady = CompletableDeferred<Unit>()
    private var view: SurfaceView? = null
    private var callback: SurfaceHolder.Callback? = null
    private var surface: Surface? = null
    private var foreground = true
    private var requestedFrameRate = 0f
    private var appliedFrameRateRequest: Float? = null
    private var displayManager: DisplayManager? = null
    private var refreshWindow: Window? = null
    private var originalWindowFrameRate = 0f
    private var displayId = Display.INVALID_DISPLAY
    @Volatile private var outputRevision = 0L
    @Volatile private var displayRevision = 0L
    @Volatile private var closing = false
    // 以下字段仅由串行 native dispatcher 访问。
    private var boundRevision = -1L
    private var videoRequested = true
    private var destroyed = false
    private var appliedDisplayFps = 0.0
    private var appliedDisplayId = Display.INVALID_DISPLAY
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) { refreshDisplay() }
        override fun onDisplayRemoved(id: Int) { refreshDisplay() }
        override fun onDisplayChanged(id: Int) {
            if (id == displayId || id == view?.display?.displayId) refreshDisplay()
        }
    }
    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(host: View) { refreshDisplay() }
        override fun onViewDetachedFromWindow(host: View) { refreshDisplay() }
    }

    actual suspend fun bind(output: Any) = withContext(Dispatchers.Main.immediate + NonCancellable) {
        if (closing || view === output) return@withContext
        detachView()
        val host = output as SurfaceView
        view = host
        host.addOnAttachStateChangeListener(attachmentListener)
        displayManager = host.context.getSystemService(DisplayManager::class.java).also { manager ->
            if (Build.VERSION.SDK_INT >= 36) {
                manager.registerDisplayListener(host.context.mainExecutor,
                    DisplayManager.EVENT_TYPE_DISPLAY_ADDED or DisplayManager.EVENT_TYPE_DISPLAY_REMOVED or
                        DisplayManager.EVENT_TYPE_DISPLAY_CHANGED or DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                    displayListener)
            } else {
                manager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            }
        }
        if (Build.VERSION.SDK_INT < 30) {
            refreshWindow = host.context.playerWindow()
            originalWindowFrameRate = refreshWindow?.attributes?.preferredRefreshRate ?: 0f
        }
        val listener = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (view !== host || closing) return
                resetOutput()
                surface = holder.surface
                appliedFrameRateRequest = null
                configureOutput(holder.surfaceFrame.width(), holder.surfaceFrame.height())
                refreshDisplay()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (view === host && !closing) configureOutput(width, height)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (view === host) disconnectSurface()
            }
        }
        callback = listener
        host.holder.addCallback(listener)
        if (host.holder.surface.isValid) listener.surfaceCreated(host.holder)
    }

    private fun resetOutput() {
        outputRevision++
        outputJob?.cancel()
        val previous = outputReady
        outputReady = CompletableDeferred()
        // 唤醒旧代等待者，让 awaitOutput 重新等待当前 Surface。
        previous.complete(Unit)
    }

    private fun configureOutput(width: Int, height: Int) {
        val output = surface ?: return
        if (closing || width <= 0 || height <= 0 || !output.isValid) return
        val revision = outputRevision
        val ready = outputReady
        outputJob?.cancel()
        outputJob = outputScope.launch {
            if (destroyed || closing || revision != outputRevision) return@launch
            try {
                check(BoloMpvNative.surfaceSize(handle, width, height) >= 0) { "视频 Surface 尺寸更新失败" }
                if (boundRevision != revision) {
                    check(BoloMpvNative.surface(handle, output) >= 0) { "视频 Surface 绑定失败" }
                    boundRevision = revision
                    check(BoloMpvNative.videoEnabled(handle, videoRequested) >= 0) { "视频输出恢复失败" }
                }
                ready.complete(Unit)
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }
    }

    private fun disconnectSurface() {
        applyFrameRateRequest(0f)
        resetOutput()
        surface = null
        displayRevision++
        displayId = Display.INVALID_DISPLAY
        // SurfaceHolder 销毁回调返回前必须停止使用输出。此串行段不得切回 Main。
        runBlocking(boloMpvDispatcher) {
            if (!destroyed) {
                val disabled = BoloMpvNative.videoEnabled(handle, false)
                val detached = BoloMpvNative.surface(handle, null)
                if (disabled < 0 || detached < 0) {
                    destroy()
                    outputReady.completeExceptionally(IllegalStateException("视频 Surface 解绑失败，播放器已关闭"))
                } else BoloMpvNative.displayFps(handle, 0.0)
                appliedDisplayFps = 0.0
                appliedDisplayId = Display.INVALID_DISPLAY
                boundRevision = -1L
            }
        }
    }

    private fun detachView() {
        val host = view ?: return
        displayManager?.unregisterDisplayListener(displayListener)
        displayManager = null
        host.removeOnAttachStateChangeListener(attachmentListener)
        callback?.let { host.holder.removeCallback(it) }
        disconnectSurface()
        refreshWindow?.let { window ->
            window.attributes = window.attributes.apply { preferredRefreshRate = originalWindowFrameRate }
        }
        refreshWindow = null
        requestedFrameRate = 0f
        callback = null
        view = null
    }

    // 请求值只送给 Android；传给 mpv 的值必须重新读取当前应用的 Display 回报。
    fun requestFrameRate(output: SurfaceView, fps: Float) {
        if (view !== output || closing) return
        requestedFrameRate = fps.takeIf { it.isFinite() && it > 0f } ?: 0f
        refreshDisplay()
    }

    fun setOutputForeground(output: SurfaceView, active: Boolean) {
        if (view !== output || closing) return
        foreground = active
        refreshDisplay()
    }

    private fun applyFrameRateRequest(fps: Float) {
        if (appliedFrameRateRequest == fps) return
        val output = surface?.takeIf { it.isValid } ?: return
        val applied = runCatching {
            when {
                Build.VERSION.SDK_INT >= 31 -> output.setFrameRate(fps,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                Build.VERSION.SDK_INT >= 30 -> output.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                else -> refreshWindow?.let { window ->
                    window.attributes = window.attributes.apply { preferredRefreshRate = fps }
                }
            }
        }.isSuccess
        if (applied) appliedFrameRateRequest = fps
    }

    private fun refreshDisplay() {
        val host = view ?: return
        if (closing) return
        val active = foreground && host.isAttachedToWindow && surface?.isValid == true
        applyFrameRateRequest(if (active) requestedFrameRate else 0f)
        val display = host.display?.takeIf { active && it.isValid }
        val nextId = display?.displayId ?: Display.INVALID_DISPLAY
        displayId = nextId
        // mode.refreshRate 可能是峰值；refreshRate 包含系统对当前应用的帧率限制。
        val actualFps = display?.refreshRate?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        val revision = ++displayRevision
        val output = outputRevision
        outputScope.launch {
            if (closing || destroyed || revision != displayRevision || output != outputRevision) return@launch
            if (appliedDisplayId != nextId) {
                if (appliedDisplayFps != 0.0 && BoloMpvNative.displayFps(handle, 0.0) < 0) return@launch
                appliedDisplayFps = 0.0
                appliedDisplayId = nextId
            }
            if (appliedDisplayFps != actualFps && BoloMpvNative.displayFps(handle, actualFps) >= 0)
                appliedDisplayFps = actualFps
        }
    }

    actual suspend fun detachOutput(output: Any) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        if (view === output) detachView()
    }

    actual suspend fun unbind() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        closing = true
        detachView()
        outputReady.completeExceptionally(IllegalStateException("播放器已关闭"))
        withContext(boloMpvDispatcher) { destroy() }
    }

    actual suspend fun awaitOutput(): Unit = withContext(Dispatchers.Main.immediate) {
        while (true) {
            check(!closing) { "播放器已关闭" }
            val revision = outputRevision
            outputReady.await()
            if (revision != outputRevision) continue
            val bound = withContext(boloMpvDispatcher) { !destroyed && boundRevision == revision }
            if (bound && revision == outputRevision) return@withContext
        }
    }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else BoloMpvNative.load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun videoEnabled(enabled: Boolean): Int {
        if (destroyed) return -3
        videoRequested = enabled
        return BoloMpvNative.videoEnabled(handle, enabled && boundRevision == outputRevision)
    }
    actual fun pause(paused: Boolean) = if (destroyed) -3 else BoloMpvNative.pause(handle, paused)
    actual fun speed(speed: Double) = if (destroyed) -3 else BoloMpvNative.speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else BoloMpvNative.volume(handle, volume)
    actual fun loudness(gainDb: Double, dynamicEnabled: Boolean, targetLufs: Double, rangeLu: Double, truePeakDbtp: Double) = if (destroyed) -3 else BoloMpvNative.loudness(handle, gainDb, dynamicEnabled, targetLufs, rangeLu, truePeakDbtp)
    actual fun mergeAudioChannels(enabled: Boolean) = if (destroyed) -3 else BoloMpvNative.mergeAudioChannels(handle, enabled)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else BoloMpvNative.seek(handle, seconds, request)
    actual fun poll() = if (destroyed) null else BoloMpvNative.poll(handle)?.toMpvEvent()
    actual fun info(includeDiagnostics: Boolean) = if (destroyed) null else BoloMpvNative.info(handle, includeDiagnostics)?.let {
        BoloMpvInfoSnapshot.parse(it.decodeToString(), this)
    }
    actual fun stop() = if (destroyed) -3 else BoloMpvNative.stop(handle)
    actual fun destroy() {
        if (destroyed) return
        destroyed = true
        outputScope.cancel()
        BoloMpvNative.destroy(handle)
    }
    actual suspend fun setAudioActive(active: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        BoloMediaSessionService.setAudioActive(this@BoloMpvBackend, active)
    }
}

private tailrec fun Context.playerWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.playerWindow()
    else -> null
}
