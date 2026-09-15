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
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private var audioSessionOwner: BoloMpvBackend? = null

internal actual class BoloMpvBackend actual constructor() {
    private val handle = checkNotNull(bolo_mpv_create("ios")) { "libmpv 初始化失败" }
    private var outputReady = CompletableDeferred<Unit>()
    private var outputGeneration = 0L
    private var host: UIView? = null
    private var view: BoloMpvView? = null
    private var closed = false
    private var destroyed = false

    actual suspend fun bind(output: Any) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        if (closed || host === output) return@withContext
        view?.let { closeOutput(it) }
        if (closed) return@withContext
        host = output as UIView
        val nativeView = BoloMpvView(player = handle.rawValue.toLong())
        nativeView.setFrame(output.bounds)
        nativeView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        output.addSubview(nativeView)
        view = nativeView
        prepareOutput(nativeView)
    }

    private fun prepareOutput(nativeView: BoloMpvView) {
        // 首次 bind 复用构造时的 latch：controller 可能已经在等待它。
        val pending = outputReady
        val generation = ++outputGeneration
        nativeView.prepareWithCompletion { ready ->
            if (!closed && view === nativeView && generation == outputGeneration) {
                if (ready) pending.complete(Unit)
                else pending.completeExceptionally(IllegalStateException("EAGL 视频输出准备失败或已中断"))
            }
        }
    }

    fun resumeRendering() {
        if (!closed) view?.let {
            // 初次准备可能与 DidBecomeActive 重叠，不能丢弃 controller 正在等待的 latch。
            if (outputReady.isCompleted) outputReady = CompletableDeferred()
            prepareOutput(it)
        }
    }

    private suspend fun closeOutput(nativeView: BoloMpvView) = suspendCoroutine<Unit> { continuation ->
        nativeView.closeWithCompletion { continuation.resume(Unit) }
    }

    actual suspend fun unbind() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        closed = true
        // 等待专用队列释放 Render API 后才允许 controller 销毁 mpv；Main 协程仅挂起。
        view?.let { closeOutput(it) }
        view = null
        host = null
    }
    actual suspend fun awaitOutput() { outputReady.await() }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else bolo_mpv_load(handle, video, audio, startSeconds, generation, userAgent, referrer)
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
    actual fun info(): BoloMpvInfoSnapshot? {
        if (destroyed) return null
        val text = bolo_mpv_info(handle) ?: return null
        return try { BoloMpvInfoSnapshot.parse(text.toKString(), this) }
        finally { bolo_mpv_info_free(text) }
    }
    actual fun destroy() { if (!destroyed) { destroyed = true; bolo_mpv_destroy(handle) } }
    actual suspend fun setAudioActive(active: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        val session = AVAudioSession.sharedInstance()
        if (active && closed) false
        else if (active) {
            val configured = session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, null)
            val activated = configured && session.setActive(true, null)
            if (activated) audioSessionOwner = this@BoloMpvBackend
            activated
        } else if (audioSessionOwner === this@BoloMpvBackend) {
            val result = session.setActive(false, null)
            if (result) audioSessionOwner = null
            result
        } else true
    }
}
