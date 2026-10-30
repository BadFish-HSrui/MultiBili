@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.player.base

import tv.hsrui.bolo.player.nativeinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
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
import platform.darwin.KERN_SUCCESS
import platform.darwin.TASK_VM_INFO
import platform.darwin.TASK_VM_INFO_COUNT
import platform.darwin.TASK_VM_INFO_REV4_COUNT
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.darwin.task_vm_info
import platform.posix.MAP_ANON
import platform.posix.MAP_FAILED
import platform.posix.MAP_PRIVATE
import platform.posix.PROT_READ
import platform.posix.PROT_WRITE
import platform.posix.mmap
import platform.posix.munmap
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal actual fun allocatePlaybackBuffer(size: Int): BoloPlaybackBuffer? {
    require(size > 0)
    val buffer = object : BoloPlaybackBuffer {
        var pointer: CPointer<ByteVar>? = null

        override fun copyInto(destination: ByteArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
            require(startIndex >= 0 && endIndex >= startIndex && endIndex <= size)
            val length = endIndex - startIndex
            require(destinationOffset >= 0 && destinationOffset <= destination.size - length)
            val source = checkNotNull(pointer)
            if (length == 0) return
            destination.usePinned { memcpy(it.addressOf(destinationOffset), source + startIndex, length.convert()) }
        }

        override fun copyFrom(source: ByteArray, sourceOffset: Int, destinationOffset: Int, length: Int) {
            require(length >= 0 && sourceOffset >= 0 && sourceOffset <= source.size - length)
            require(destinationOffset >= 0 && destinationOffset <= size - length)
            val destination = checkNotNull(pointer)
            if (length == 0) return
            source.usePinned { memcpy(destination + destinationOffset, it.addressOf(sourceOffset), length.convert()) }
        }

        override fun close() {
            val allocation = pointer ?: return
            pointer = null
            munmap(allocation, size.convert())
        }
    }
    // 独立映射在关闭时直接归还页面，避免 malloc 保留空闲块导致余量迟迟不能恢复。
    val allocation = mmap(null, size.convert(), PROT_READ or PROT_WRITE, MAP_PRIVATE or MAP_ANON, -1, 0)
    if (allocation == MAP_FAILED || allocation == null) return null
    buffer.pointer = allocation.reinterpret()
    return buffer
}

internal actual fun playbackAvailableMemoryBytes(): Long? = memScoped {
    val info = alloc<task_vm_info>()
    val count = alloc<mach_msg_type_number_tVar>()
    count.value = TASK_VM_INFO_COUNT
    val result = task_info(mach_task_self_, TASK_VM_INFO.convert(), info.ptr.reinterpret(), count.ptr)
    if (result != KERN_SUCCESS || count.value < TASK_VM_INFO_REV4_COUNT) return@memScoped null
    info.limit_bytes_remaining.takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong()
}

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
    private val streamBridge = BoloMpvStreamBridge()
    private val handle = checkNotNull(bolo_mpv_create("ios")) { "libmpv 初始化失败" }
    private val streamReference = StableRef.create(streamBridge)
    init {
        val result = memScoped {
            val callbacks = alloc<bolo_mpv_stream_callbacks>()
            callbacks.open = staticCFunction(::iosStreamOpen)
            callbacks.read = staticCFunction(::iosStreamRead)
            callbacks.seek = staticCFunction(::iosStreamSeek)
            callbacks.size = staticCFunction(::iosStreamSize)
            callbacks.cancel = staticCFunction(::iosStreamCancel)
            callbacks.close = staticCFunction(::iosStreamClose)
            bolo_mpv_set_stream_callbacks(handle, callbacks.ptr, streamReference.asCPointer())
        }
        if (result < 0) {
            bolo_mpv_destroy(handle)
            streamReference.dispose()
            error("播放数据源注册失败")
        }
    }
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
    actual fun load(streams: BoloPlaybackStreamSession, startSeconds: Double, generation: Long): Int {
        if (destroyed) return -3
        streamBridge.bind(streams)
        return bolo_mpv_load(handle, streams.videoUri, streams.audioUri, startSeconds, generation,
            videoPlayHeaders.getValue("User-Agent"), videoPlayHeaders.getValue("Referer"))
    }
    actual fun loadLive(streams: BoloMpvStreamSource, uri: String, generation: Long, prefetchEnabled: Boolean, targetSeconds: Double?, request: Long): Int {
        if (destroyed) return -3
        streamBridge.bind(streams)
        return bolo_mpv_load_live(handle, uri, generation, "", "", if (prefetchEnabled) 1 else 0, targetSeconds ?: -1.0, request)
    }
    actual fun videoEnabled(enabled: Boolean) = if (destroyed) -3 else
        bolo_mpv_video_enabled(handle, if (enabled && videoOutputAllowed.value) 1 else 0)
    actual fun pause(paused: Boolean) = if (destroyed) -3 else bolo_mpv_pause(handle, if (paused) 1 else 0)
    actual fun speed(speed: Double) = if (destroyed) -3 else bolo_mpv_speed(handle, speed)
    actual fun bufferedDurationMs(): Long? = if (destroyed) null else bolo_mpv_buffered_duration(handle)
        .takeIf { it.isFinite() && it >= 0 }?.let { (it * 1000).toLong() }
    actual fun bufferSnapshot(): BoloMpvBufferSnapshot? = if (destroyed) null else memScoped {
        val snapshot = alloc<bolo_mpv_buffer_snapshot>()
        if (bolo_mpv_read_buffer_snapshot(handle, snapshot.ptr) == 0) null else BoloMpvBufferSnapshot(
            generation = snapshot.generation,
            positionMs = snapshot.position_ms.takeUnless { it == Long.MIN_VALUE },
            bufferedEndMs = snapshot.buffered_end_ms.takeUnless { it == Long.MIN_VALUE },
            bufferedDurationMs = snapshot.buffered_duration_ms.takeUnless { it == Long.MIN_VALUE },
            underrun = snapshot.underrun != 0,
            idle = snapshot.idle != 0,
            demuxEof = snapshot.demux_eof != 0,
        )
    }
    actual fun volume(volume: Double) = if (destroyed) -3 else bolo_mpv_volume(handle, volume)
    actual fun loudness(gainDb: Double, dynamicEnabled: Boolean, targetLufs: Double, rangeLu: Double, truePeakDbtp: Double) = if (destroyed) -3 else bolo_mpv_loudness(handle, gainDb, if (dynamicEnabled) 1 else 0, targetLufs, rangeLu, truePeakDbtp)
    actual fun mergeAudioChannels(enabled: Boolean) = if (destroyed) -3 else bolo_mpv_merge_audio_channels(handle, if (enabled) 1 else 0)
    actual fun seek(seconds: Double, request: Long) = if (destroyed) -3 else bolo_mpv_seek(handle, seconds, request)
    actual fun poll(): BoloMpvEvent? = if (destroyed) null else memScoped {
        val event = alloc<bolo_mpv_event>()
        if (bolo_mpv_poll(handle, event.ptr) == 0) null
        else BoloMpvEvent(event.type, event.generation, event.request, event.error, event.value)
    }
    actual fun stop(): Int {
        streamBridge.cancel()
        return if (destroyed) -3 else bolo_mpv_stop(handle)
    }
    actual fun info(includeDiagnostics: Boolean): BoloMpvInfoSnapshot? {
        if (destroyed) return null
        val text = bolo_mpv_info(handle, if (includeDiagnostics) 1 else 0) ?: return null
        return try { BoloMpvInfoSnapshot.parse(text.toKString(), this) }
        finally { bolo_mpv_info_free(text) }
    }
    actual fun destroy() {
        if (destroyed) return
        destroyed = true
        streamBridge.cancel()
        bolo_mpv_destroy(handle)
        streamBridge.release()
        streamReference.dispose()
    }
    actual suspend fun setAudioActive(active: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        if (active && closed) false
        else IosPlayerAudioSession.setPlaybackActive(this@BoloMpvBackend, active)
    }
}

private fun iosStreamOpen(context: COpaquePointer?, uri: CPointer<ByteVar>?): Long = try {
    if (context == null || uri == null) 0L else context.asStableRef<BoloMpvStreamBridge>().get().open(uri.toKString())
} catch (_: Throwable) { 0L }

private fun iosStreamRead(context: COpaquePointer?, id: Long, buffer: CPointer<ByteVar>?, size: ULong): Long = try {
    if (context == null || buffer == null) -1L else {
        val bytes = ByteArray(minOf(size, 65536uL).toInt())
        val count = context.asStableRef<BoloMpvStreamBridge>().get().read(id, bytes)
        if (count > 0) bytes.usePinned { memcpy(buffer, it.addressOf(0), count.convert()) }
        count.toLong()
    }
} catch (_: Throwable) { -1L }

private fun iosStreamSeek(context: COpaquePointer?, id: Long, offset: Long): Long = try {
    context?.asStableRef<BoloMpvStreamBridge>()?.get()?.seek(id, offset) ?: -1L
} catch (_: Throwable) { -1L }

private fun iosStreamSize(context: COpaquePointer?, id: Long): Long = try {
    context?.asStableRef<BoloMpvStreamBridge>()?.get()?.size(id) ?: -1L
} catch (_: Throwable) { -1L }

private fun iosStreamCancel(context: COpaquePointer?, id: Long) {
    try { context?.asStableRef<BoloMpvStreamBridge>()?.get()?.cancel(id) } catch (_: Throwable) { }
}

private fun iosStreamClose(context: COpaquePointer?, id: Long) {
    try { context?.asStableRef<BoloMpvStreamBridge>()?.get()?.close(id) } catch (_: Throwable) { }
}
