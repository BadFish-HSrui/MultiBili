@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.player.base

import cocoapods.BoloNativePlayer.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

private var audioSessionOwner: BoloMpvBackend? = null

internal actual class BoloMpvBackend actual constructor() {
    private val handle = checkNotNull(bolo_mpv_create("ios")) { "libmpv 初始化失败" }
    private val outputReady = CompletableDeferred<Unit>()
    private var outputFailure: Throwable? = null
    private var host: UIView? = null
    private var view: BoloMpvView? = null
    private var closed = false
    private var destroyed = false

    actual suspend fun bind(output: Any) = withContext(Dispatchers.Main.immediate) {
        if (closed || host === output) return@withContext
        view?.close()
        host = output as UIView
        val nativeView = BoloMpvView(player = handle.rawValue.toLong())
        nativeView.setFrame(output.bounds)
        nativeView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        output.addSubview(nativeView)
        view = nativeView
        if (nativeView.prepare()) outputReady.complete(Unit)
        else outputReady.completeExceptionally(IllegalStateException("EAGL 播放器初始化失败"))
    }

    fun suspendRendering() { view?.suspendRendering() }
    fun resumeRendering() {
        if (view?.prepare() == false) outputFailure = IllegalStateException("EAGL 输出恢复失败")
    }
    actual suspend fun unbind() = withContext(Dispatchers.Main.immediate) {
        closed = true
        view?.close()
        view = null
        host = null
    }
    actual suspend fun awaitOutput() { outputFailure?.let { throw it }; outputReady.await() }
    actual fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String) = if (destroyed) -3 else bolo_mpv_load(handle, video, audio, startSeconds, generation, userAgent, referrer)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else bolo_mpv_pause(handle, if (paused) 1 else 0)
    actual fun speed(speed: Double) = if (destroyed) -3 else bolo_mpv_speed(handle, speed)
    actual fun volume(volume: Double) = if (destroyed) -3 else bolo_mpv_volume(handle, volume)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else bolo_mpv_seek(handle, seconds, request)
    actual fun poll(): BoloMpvEvent? = if (destroyed) null else memScoped {
        val event = alloc<bolo_mpv_event>()
        if (bolo_mpv_poll(handle, event.ptr) == 0) null
        else BoloMpvEvent(event.type, event.generation, event.request, event.error, event.value)
    }
    actual fun stop() = if (destroyed) -3 else bolo_mpv_stop(handle)
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
