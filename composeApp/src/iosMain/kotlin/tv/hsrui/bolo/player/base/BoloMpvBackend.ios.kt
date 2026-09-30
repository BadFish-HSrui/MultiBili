@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.player.base

import cocoapods.BoloNativePlayer.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 仅在 Main 使用；播放和手势分别持有会话，互不释放对方的使用权。 */
internal object IosPlayerAudioSession {
    private var playbackOwner: BoloMpvBackend? = null
    private val volumeOwners = mutableSetOf<Any>()
    private var active = false

    private fun activate(): Boolean {
        val session = AVAudioSession.sharedInstance()
        val configured = session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, null)
        val activated = configured && session.setActive(true, null)
        if (activated) active = true
        return activated
    }

    private fun deactivateIfUnused(): Boolean {
        if (!active || playbackOwner != null || volumeOwners.isNotEmpty()) return true
        val deactivated = AVAudioSession.sharedInstance().setActive(false, null)
        if (deactivated) active = false
        return deactivated
    }

    fun setPlaybackActive(owner: BoloMpvBackend, active: Boolean): Boolean {
        if (active) {
            if (!activate()) return false
            playbackOwner = owner
            return true
        }
        if (playbackOwner !== owner) return true
        playbackOwner = null
        return deactivateIfUnused()
    }

    fun beginVolumeAdjustment(owner: Any): Boolean {
        if (owner in volumeOwners) return true
        if (!activate()) return false
        volumeOwners.add(owner)
        return true
    }

    fun endVolumeAdjustment(owner: Any) {
        if (volumeOwners.remove(owner)) deactivateIfUnused()
    }
}

internal actual class BoloMpvBackend actual constructor() {
    private val handle = checkNotNull(bolo_mpv_create("ios")) { "libmpv 初始化失败" }
    private val outputMutex = Mutex()
    private var outputReady = CompletableDeferred<Unit>()
    private var outputGeneration = 0L
    private val videoOutputAllowed = MutableStateFlow(false)
    private var host: UIView? = null
    private var view: BoloMpvView? = null
    private var closed = false
    private var destroyed = false
    actual val retainsPausedResources: Boolean get() = true
    actual fun retainedPosition(generation: Long, positionMs: Long): Long? {
        if (destroyed || !videoOutputAllowed.value) return null
        val position = bolo_mpv_retained_position(handle, generation, positionMs / 1000.0)
        return position.takeIf { it.isFinite() && it >= 0 }?.let { (it * 1000).toLong() }
    }

    actual suspend fun bind(output: Any) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        outputMutex.withLock {
            if (closed || host === output) return@withLock
            videoOutputAllowed.value = false
            view?.let {
                ++outputGeneration
                if (outputReady.isCompleted) outputReady = CompletableDeferred()
                closeOutput(it)
            }
            if (closed) return@withLock
            host = output as UIView
            val nativeView = BoloMpvView(player = handle.rawValue.toLong())
            nativeView.setFrame(output.bounds)
            nativeView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            output.addSubview(nativeView)
            view = nativeView
            prepareOutput(nativeView)
        }
    }

    private fun prepareOutput(nativeView: BoloMpvView) {
        // 短暂失活复用已准备输出；不能临时关轨而触发解码器重建。
        // 首次 bind 复用构造时的 latch：controller 可能已经在等待它。
        val pending = outputReady
        val generation = ++outputGeneration
        nativeView.prepareWithCompletion { ready ->
            if (!closed && view === nativeView && generation == outputGeneration) {
                if (ready) {
                    videoOutputAllowed.value = true
                    pending.complete(Unit)
                }
                else {
                    videoOutputAllowed.value = false
                    pending.completeExceptionally(IllegalStateException("EAGL 视频输出准备失败或已中断"))
                }
            }
        }
        if (UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive) {
            // 失活时允许继续处理音频；未准备过的输出仍保持关轨。
            // 回到活跃状态会建立新的等待，再准备输出。
            pending.complete(Unit)
        }
    }

    fun suspendOutput(background: Boolean = false) {
        if (closed) return
        if (background) bolo_mpv_background(handle, 1)
        ++outputGeneration
        // 解除正在加载的输出等待，避免系统面板停留超过初始化超时后误报错误。
        outputReady.complete(Unit)
        // 原生视图独立监听同一通知并同步排空 GL，避免在此重复执行。
    }

    fun resumeRendering() {
        if (!destroyed) bolo_mpv_background(handle, 0)
        if (!closed) view?.let {
            // 初次准备可能与 DidBecomeActive 重叠，不能丢弃 controller 正在等待的 latch。
            if (outputReady.isCompleted) outputReady = CompletableDeferred()
            prepareOutput(it)
        }
    }

    private suspend fun closeOutput(nativeView: BoloMpvView) = suspendCoroutine<Unit> { continuation ->
        nativeView.closeWithCompletion { continuation.resume(Unit) }
    }

    actual suspend fun detachOutput(output: Any) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        outputMutex.withLock {
            if (host !== output || closed) return@withLock
            videoOutputAllowed.value = false
            ++outputGeneration
            bolo_mpv_background(handle, 0)
            withContext(boloMpvDispatcher) { videoEnabled(false) }
            view?.let { closeOutput(it) }
            view = null
            host = null
            if (outputReady.isCompleted) outputReady = CompletableDeferred()
        }
    }

    actual suspend fun unbind() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        outputMutex.withLock {
            closed = true
            videoOutputAllowed.value = false
            // 等待专用队列释放 Render API 后才允许 controller 销毁 mpv；Main 协程仅挂起。
            view?.let { closeOutput(it) }
            view = null
            host = null
        }
    }
    actual suspend fun awaitOutput() { outputReady.await() }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else bolo_mpv_load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun videoEnabled(enabled: Boolean) = if (destroyed) -3 else
        bolo_mpv_video_enabled(handle, if (enabled && videoOutputAllowed.value) 1 else 0)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else bolo_mpv_pause(handle, if (paused) 1 else 0)
    actual fun speed(speed: Double) = if (destroyed) -3 else bolo_mpv_speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else bolo_mpv_volume(handle, volume)
    actual fun loudness(gainDb: Double, dynamicEnabled: Boolean, targetLufs: Double, rangeLu: Double, truePeakDbtp: Double) = if (destroyed) -3 else bolo_mpv_loudness(handle, gainDb, if (dynamicEnabled) 1 else 0, targetLufs, rangeLu, truePeakDbtp)
    actual fun mergeAudioChannels(enabled: Boolean) = if (destroyed) -3 else bolo_mpv_merge_audio_channels(handle, if (enabled) 1 else 0)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else bolo_mpv_seek(handle, seconds, request)
    actual fun poll(): BoloMpvEvent? = if (destroyed) null else memScoped {
        val event = alloc<bolo_mpv_event>()
        if (bolo_mpv_poll(handle, event.ptr) == 0) null
        else BoloMpvEvent(event.type, event.generation, event.request, event.error, event.value)
    }
    actual fun stop() = if (destroyed) -3 else bolo_mpv_stop(handle)
    actual fun info(includeDiagnostics: Boolean): BoloMpvInfoSnapshot? {
        if (destroyed) return null
        val text = bolo_mpv_info(handle, if (includeDiagnostics) 1 else 0) ?: return null
        return try { BoloMpvInfoSnapshot.parse(text.toKString(), this) }
        finally { bolo_mpv_info_free(text) }
    }
    actual fun destroy() { if (!destroyed) { destroyed = true; bolo_mpv_destroy(handle) } }
    actual suspend fun setAudioActive(active: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        if (active && closed) false
        else IosPlayerAudioSession.setPlaybackActive(this@BoloMpvBackend, active)
    }
}
