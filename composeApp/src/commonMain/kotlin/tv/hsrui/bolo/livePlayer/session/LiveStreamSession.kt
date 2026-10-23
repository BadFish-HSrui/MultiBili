@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.hsrui.bolo.player.base.BoloMpvStreamReader
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import tv.hsrui.network.liveFeature.player.LiveHlsPlaylistData
import tv.hsrui.network.liveFeature.player.LiveHlsResourceData
import tv.hsrui.network.liveFeature.player.LiveHlsSegmentData
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import tv.hsrui.network.liveFeature.player.fetchLiveHlsPlaylist
import tv.hsrui.network.liveFeature.player.fetchLiveStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.TimeSource

internal class LiveStreamSession(
    val generation: Long,
    private val stream: LivePlayStreamData,
    private val roomId: Long,
    private val url: String,
    private val onFailure: (Throwable) -> Unit,
) : BoloMpvStreamSource {
    internal class RestartRequired(message: String) : Exception(message)

    val uri = "bolo-stream://live/$generation/${stream.formatName}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prepared = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val opened = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val closedComplete = CompletableDeferred<Unit>()
    private val buffer = LiveStreamBuffer()
    private val mutablePlaylist = MutableStateFlow<LiveHlsPlaylistData?>(null)
    private var playlistUrl = url
    val playlist = mutablePlaylist.asStateFlow()
    val bufferedBytes = buffer.bufferedBytes
    private val received = AtomicLong(0L)
    val receivedBytes: Long get() = received.load()

    suspend fun prepare() {
        check(!closed.load())
        if (prepared.compareAndSet(false, true)) scope.launch {
            try {
                when {
                    stream.protocolName == "http_hls" && stream.formatName in setOf("fmp4", "ts") -> runHls()
                    stream.protocolName == "http_stream" && stream.formatName == "flv" -> downloadFlv()
                    else -> error("不支持的直播容器")
                }
                check(ready.isCompleted) { "直播媒体为空" }
                buffer.finish()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                val wasReady = ready.isCompleted
                ready.completeExceptionally(error)
                buffer.close(error)
                if (wasReady && !closed.load()) onFailure(error)
            }
        }
        ready.await()
    }

    private suspend fun runHls() = coroutineScope {
        var loadedAt = TimeSource.Monotonic.markNow()
        val first = refreshPlaylist()
        mutablePlaylist.value = first
        val initialization = CompletableDeferred<Pair<LiveHlsResourceData, Pair<Long, Long>>>()
        val refresh = launch {
            var previous = first
            var changed = true
            var advancedAt = TimeSource.Monotonic.markNow()
            while (isActive && !previous.ended) {
                val interval = if (changed) previous.targetDurationMs else maxOf(1, previous.targetDurationMs / 2)
                delay((interval - loadedAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                loadedAt = TimeSource.Monotonic.markNow()
                val next = refreshPlaylist()
                val oldSegments = previous.segments.associateBy { it.sequence }
                if (next.segments.any { segment -> oldSegments[segment.sequence]?.let {
                        it.resource != segment.resource || it.initialization != segment.initialization ||
                            it.discontinuity != segment.discontinuity || it.durationMs != segment.durationMs || it.isGap != segment.isGap
                    } == true } ||
                    (next.segments.lastOrNull()?.sequence ?: Long.MAX_VALUE) < (previous.segments.lastOrNull()?.sequence ?: 0)) {
                    throw RestartRequired("直播清单时间轴已变化")
                }
                changed = next != previous
                if (changed && initialization.isCompleted) {
                    val (resource, fingerprint) = initialization.await()
                    if (next.segments.lastOrNull()?.initialization == resource &&
                        downloadResource(resource, deliver = false, fingerprint = true) != fingerprint) {
                        throw RestartRequired("直播初始化内容已变化")
                    }
                }
                if (next.segments.lastOrNull()?.sequence != previous.segments.lastOrNull()?.sequence) {
                    advancedAt = TimeSource.Monotonic.markNow()
                } else if (!next.ended && advancedAt.elapsedNow().inWholeMilliseconds >=
                    maxOf(30_000, next.targetDurationMs.coerceAtMost(Long.MAX_VALUE / 3) * 3)) {
                    error("直播清单长时间没有新分片")
                }
                mutablePlaylist.value = next
                previous = next
            }
        }
        try {
            var nextSequence: Long? = null
            var previous: LiveHlsSegmentData? = null
            while (isActive) {
                val current = checkNotNull(mutablePlaylist.value)
                if (nextSequence == null) nextSequence = current.segments.lastOrNull()?.sequence
                val firstSequence = current.segments.firstOrNull()?.sequence
                if (nextSequence != null && firstSequence != null && nextSequence < firstSequence) {
                    throw RestartRequired("直播分片已离开可用窗口")
                }
                val segment = current.segments.firstOrNull { it.sequence == nextSequence }
                if (segment == null) {
                    if (current.ended) break
                    mutablePlaylist.first { it != current }
                    continue
                }
                if (segment.isGap || previous?.let {
                    it.discontinuity != segment.discontinuity || it.initialization != segment.initialization
                } == true) throw RestartRequired("直播分片连续区间已变化")
                if (previous == null) {
                    if (stream.formatName == "fmp4") checkNotNull(segment.initialization) { "直播初始化片段缺失" }
                    segment.initialization?.let {
                        initialization.complete(it to downloadResource(it, fingerprint = true))
                    }
                }
                downloadResource(segment.resource)
                previous = segment
                nextSequence = segment.sequence + 1
            }
        } finally {
            refresh.cancel()
        }
    }

    private suspend fun refreshPlaylist(): LiveHlsPlaylistData {
        repeat(2) { attempt ->
            try {
                return fetchLiveHlsPlaylist(playlistUrl, roomId, stream).also { playlistUrl = it.url }
            }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (attempt == 1) throw error
                delay(250)
            }
        }
        error("直播清单请求失败")
    }

    private suspend fun downloadResource(
        resource: LiveHlsResourceData,
        deliver: Boolean = true,
        fingerprint: Boolean = false,
    ): Pair<Long, Long> {
        repeat(2) { attempt ->
            var delivered = false
            var size = 0L
            // 初始化片段使用 FNV-1a 与长度检测内容变化，不保留额外媒体副本。
            var hash = -3750763034362895579L
            try {
                fetchLiveStream(resource.url, roomId, resource.range,
                    receiveLimit = if (fingerprint) 1024 * 1024 else null) { bytes, offset, length ->
                    size += length
                    if (fingerprint) for (index in offset until offset + length) {
                        hash = (hash xor (bytes[index].toLong() and 255)) * 1099511628211L
                    }
                    if (deliver) {
                        received.fetchAndAdd(length.toLong())
                        // 一旦正文进入读取缓冲，失败后必须重新加载，不能追加重试正文。
                        delivered = true
                        buffer.write(bytes, offset, length)
                        ready.complete(Unit)
                    }
                }
                check(size > 0) { "直播媒体正文为空" }
                return size to hash
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (delivered || attempt == 1) throw error
                delay(250)
            }
        }
        error("直播媒体请求失败")
    }

    private suspend fun downloadFlv() {
        downloadResource(LiveHlsResourceData(url))
    }

    override suspend fun open(uri: String): BoloMpvStreamReader? {
        if (uri != this.uri || closed.load() || !opened.compareAndSet(false, true)) return null
        return object : BoloMpvStreamReader {
            override val job = Job(scope.coroutineContext[Job])
            override suspend fun read(bytes: ByteArray): Int = buffer.read(bytes)
            override suspend fun seek(position: Long): Long = -1L
            override suspend fun size(): Long = -1L
            override suspend fun close() { job.cancel() }
        }
    }

    override fun cancel() {
        if (closed.compareAndSet(false, true)) {
            ready.cancel()
            scope.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    scope.coroutineContext[Job]?.join()
                    buffer.close()
                } finally { closedComplete.complete(Unit) }
            }
        }
    }

    suspend fun awaitClosed() {
        cancel()
        closedComplete.await()
    }
}
