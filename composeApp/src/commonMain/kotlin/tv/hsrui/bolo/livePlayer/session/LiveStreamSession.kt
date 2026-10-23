@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    localReplayEnabled: Boolean = false,
    val cacheCapacityBytes: Long = 256L * 1024 * 1024,
    private val onFailure: (Throwable) -> Unit,
) : BoloMpvStreamSource {
    val uri = "bolo-stream://live/$generation/${stream.formatName}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prepared = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val opened = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val closedComplete = CompletableDeferred<Unit>()
    private val transferMutex = Mutex()
    private val buffer = LiveStreamBuffer(mutex = transferMutex)
    private val mutablePlaylist = MutableStateFlow<LiveHlsPlaylistData?>(null)
    private var playlistUrl = url
    val playlist = mutablePlaylist.asStateFlow()
    val bufferedBytes = buffer.bufferedBytes
    private val received = AtomicLong(0L)
    val receivedBytes: Long get() = received.load()

    val isHls = stream.protocolName == "http_hls" && stream.formatName in setOf("fmp4", "ts")
    val localReplayEnabled = localReplayEnabled && isHls
    val cache = LivePlaybackCache(capacity = cacheCapacityBytes, mutex = transferMutex)
    private var collector: Job? = null
    private var nextSequence: Long? = null
    private var index = LiveHlsMediaIndex(stream.formatName)
    private var initId: Long? = null
    private var lastMediaEnd: Long? = null
    private var lastConfiguration: Long? = null
    private var indexedDiscontinuity: Long? = null
    private var checkedInitialization: LiveHlsResourceData? = null
    private var cdnIndex = 0
    private val identities = mutableMapOf<Pair<Long, Long>, List<Any?>>()

    /** 同一临界区内采集实际缓存正文量与累计下载量。 */
    suspend fun transferSnapshot(): Pair<Long, Long> = transferMutex.withLock {
        val cached = if (localReplayEnabled) cache.snapshot.value.cachedBytes else buffer.bufferedBytes.value.toLong()
        cached to received.load()
    }

    private suspend fun onReceived(count: Int) {
        withContext(NonCancellable) {
            transferMutex.withLock { received.fetchAndAdd(count.toLong()) }
        }
    }

    suspend fun prepare() {
        check(!closed.load())
        if (prepared.compareAndSet(false, true)) setCollecting(true)
        ready.await()
    }

    fun setCollecting(active: Boolean) {
        if (closed.load()) return
        val previous = collector
        previous?.cancel()
        collector = if (!active) previous else scope.launch {
            previous?.join()
            try {
                when {
                    localReplayEnabled -> runHls()
                    isHls -> runHlsInstant()
                    else -> downloadFlv()
                }
                check(ready.isCompleted) { "直播媒体为空" }
                if (localReplayEnabled) cache.finish() else buffer.finish()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                val wasReady = ready.isCompleted
                ready.completeExceptionally(error)
                if (!localReplayEnabled) buffer.close(error)
                if (wasReady && !closed.load()) onFailure(error)
            }
        }
    }

    fun retry(address: String) {
        playlistUrl = address
        setCollecting(true)
    }

    private suspend fun runHlsInstant() = coroutineScope {
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
                check(next.segments.none { segment -> oldSegments[segment.sequence]?.let {
                    identity(it) != identity(segment) || it.discontinuity != segment.discontinuity || it.isGap != segment.isGap
                } == true } && (next.segments.lastOrNull()?.sequence ?: Long.MAX_VALUE) >=
                    (previous.segments.lastOrNull()?.sequence ?: 0)) { "直播清单时间轴已变化" }
                changed = next != previous
                if (changed && initialization.isCompleted) {
                    val (resource, fingerprint) = initialization.await()
                    val nextInitialization = next.segments.lastOrNull()?.initialization
                    if (nextInitialization != null &&
                        io.ktor.http.Url(nextInitialization.url).encodedPath == io.ktor.http.Url(resource.url).encodedPath &&
                        nextInitialization.range == resource.range) {
                        check(downloadResourceToBuffer(nextInitialization, deliver = false, fingerprint = true) == fingerprint) {
                            "直播初始化内容已变化"
                        }
                    }
                }
                if (next.segments.lastOrNull()?.sequence != previous.segments.lastOrNull()?.sequence) {
                    advancedAt = TimeSource.Monotonic.markNow()
                } else check(next.ended || advancedAt.elapsedNow().inWholeMilliseconds < maxOf(30_000, next.targetDurationMs * 3)) {
                    "直播清单长时间没有新分片"
                }
                mutablePlaylist.value = next
                previous = next
            }
        }
        try {
            var sequence: Long? = null
            var previous: LiveHlsSegmentData? = null
            while (isActive) {
                val current = checkNotNull(mutablePlaylist.value)
                if (sequence == null) {
                    val available = current.segments.dropWhile { it.sequence <= (current.segments.lastOrNull { item -> item.isGap }?.sequence ?: -1) }
                    sequence = available.lastOrNull { it.startsWithKeyframe == true || current.independentSegments }?.sequence
                        ?: available.firstOrNull()?.sequence
                }
                val segment = current.segments.firstOrNull { sequence != null && it.sequence >= sequence }
                if (segment == null) {
                    if (current.ended) break
                    mutablePlaylist.first { it != current }
                    continue
                }
                check(segment.sequence == sequence && !segment.isGap) { "直播分片出现缺口" }
                check(previous?.let {
                    val previousTime = it.mediaStartTimeMs
                    val currentTime = segment.mediaStartTimeMs
                    it.discontinuity != segment.discontinuity ||
                        identity(it).last() != identity(segment).last() ||
                        previousTime != null && currentTime != null && currentTime < previousTime
                } != true) { "直播分片连续区间已变化" }
                if (previous == null) {
                    if (stream.formatName == "fmp4") checkNotNull(segment.initialization) { "直播初始化片段缺失" }
                    segment.initialization?.let {
                        initialization.complete(it to downloadResourceToBuffer(it, fingerprint = true))
                    }
                }
                downloadResourceToBuffer(segment.resource)
                previous = segment
                sequence = segment.sequence + 1
            }
        } finally { refresh.cancel() }
    }

    private suspend fun downloadResourceToBuffer(
        resource: LiveHlsResourceData,
        deliver: Boolean = true,
        fingerprint: Boolean = false,
    ): Pair<Long, Long> {
        repeat(2) { attempt ->
            var delivered = false
            var size = 0L
            var hash = -3750763034362895579L
            try {
                fetchLiveStream(resource.url, roomId, resource.range,
                    receiveLimit = if (fingerprint) 1024 * 1024 else null,
                    onReceived = ::onReceived) { bytes, offset, length ->
                    size += length
                    if (fingerprint) for (index in offset until offset + length) {
                        hash = (hash xor (bytes[index].toLong() and 255)) * 1099511628211L
                    }
                    if (deliver) {
                        // 部分正文一旦进入读取缓冲，就必须由新代次恢复，不能追加重试正文。
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

    private suspend fun runHls() = coroutineScope {
        var loadedAt = TimeSource.Monotonic.markNow()
        val first = refreshPlaylist()
        mutablePlaylist.value = first
        val refresh = launch {
            var previous = first
            var changed = true
            var advancedAt = TimeSource.Monotonic.markNow()
            while (isActive && !previous.ended) {
                val interval = if (changed) previous.targetDurationMs else maxOf(1, previous.targetDurationMs / 2)
                delay((interval - loadedAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                loadedAt = TimeSource.Monotonic.markNow()
                val next = refreshPlaylist()
                changed = next != previous
                if (next.segments.lastOrNull()?.sequence != previous.segments.lastOrNull()?.sequence) {
                    advancedAt = TimeSource.Monotonic.markNow()
                } else if (!next.ended && advancedAt.elapsedNow().inWholeMilliseconds >= maxOf(30_000, next.targetDurationMs * 3)) {
                    error("直播清单长时间没有新分片")
                }
                mutablePlaylist.value = next
                previous = next
            }
        }
        try {
            var checkedPlaylist: LiveHlsPlaylistData? = null
            while (isActive) {
                val current = checkNotNull(mutablePlaylist.value)
                val last = current.segments.lastOrNull()
                val changedIdentity = current.segments.any { segment ->
                    identities[segment.discontinuity to segment.sequence]?.let { it != identity(segment) } == true
                }
                if (changedIdentity) {
                    cache.clear()
                    identities.clear()
                    nextSequence = null
                    lastMediaEnd = null
                }
                if (nextSequence != null && last != null && nextSequence!! > last.sequence + 1) {
                    cache.clear()
                    identities.clear()
                    nextSequence = null
                    index = LiveHlsMediaIndex(stream.formatName)
                    lastMediaEnd = null
                }
                if (nextSequence == null) {
                    // 先取得最新完整随机访问组，而非任意非关键帧分片。
                    nextSequence = current.segments.lastOrNull { it.startsWithKeyframe == true }?.sequence
                        ?: current.segments.firstOrNull()?.sequence
                }
                val segment = current.segments.firstOrNull { it.sequence >= (nextSequence ?: 0) }
                if (segment == null) {
                    if (current.ended) break
                    mutablePlaylist.first { it != current }
                    continue
                }
                if (segment.isGap) { nextSequence = segment.sequence + 1; continue }
                if (stream.formatName == "fmp4" && (checkedPlaylist != current || checkedInitialization != segment.initialization)) {
                    val initialization = checkNotNull(segment.initialization) { "直播初始化片段缺失" }
                    val body = downloadResource(initialization, initialization = true)
                    try {
                        index.initialize(body)
                        val (id, changed) = cache.initialize(body)
                        initId = id
                        if (changed) { lastMediaEnd = null; nextSequence = segment.sequence }
                    } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { cache.discard(body, onlyStaged = true) } }
                    checkedPlaylist = current
                    checkedInitialization = initialization
                }
                val body = downloadResource(segment.resource)
                try {
                    if (indexedDiscontinuity != null && indexedDiscontinuity != segment.discontinuity) index.discontinuity()
                    indexedDiscontinuity = segment.discontinuity
                    val media = index.inspect(body, segment, current.independentSegments)
                    if (lastMediaEnd != null && media.startMs != null && media.startMs < lastMediaEnd!! - 250 ||
                        lastConfiguration != null && media.configurationId != null && lastConfiguration != media.configurationId) {
                        cache.clear()
                        identities.clear()
                    }
                    // 连续区间变化后即使时间仍单调，也不能沿用前一组解码依赖。
                    cache.append(body, segment.sequence, segment.discontinuity, initId, media)
                    lastMediaEnd = media.endMs
                    media.configurationId?.let { lastConfiguration = it }
                    identities[segment.discontinuity to segment.sequence] = identity(segment)
                    identities.keys.filter { it.second < segment.sequence - 64 }.forEach(identities::remove)
                    nextSequence = segment.sequence + 1
                    ready.complete(Unit)
                } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { cache.discard(body, onlyStaged = true) } }
            }
        } finally { refresh.cancel() }
    }

    private fun identity(segment: LiveHlsSegmentData): List<Any?> = listOf(
        io.ktor.http.Url(segment.resource.url).encodedPath, segment.resource.range, segment.durationMs,
        segment.mediaStartTimeMs, segment.startsWithKeyframe,
        segment.initialization?.let { io.ktor.http.Url(it.url).encodedPath to it.range },
    )

    private suspend fun refreshPlaylist(): LiveHlsPlaylistData {
        repeat(maxOf(2, stream.urls.size * 2)) { attempt ->
            try {
                return fetchLiveHlsPlaylist(playlistUrl, roomId, stream).also { playlistUrl = it.url }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (attempt + 1 == maxOf(2, stream.urls.size * 2)) throw error
                if (attempt % 2 == 1 && cdnIndex + 1 < stream.urls.size) playlistUrl = stream.urls[++cdnIndex]
                delay(250)
            }
        }
        error("直播清单请求失败")
    }

    private suspend fun downloadResource(resource: LiveHlsResourceData, initialization: Boolean = false): LivePlaybackCache.Body {
        repeat(2) { attempt ->
            val body = cache.stage()
            try {
                fetchLiveStream(resource.url, roomId, resource.range,
                    receiveLimit = if (initialization) 1024 * 1024 else 256L * 1024 * 1024,
                    onReceived = ::onReceived) { bytes, offset, length ->
                    cache.write(body, bytes, offset, length)
                }
                check(body.size > 0) { "直播媒体正文为空" }
                return body
            } catch (error: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { cache.discard(body) }
                currentCoroutineContext().ensureActive()
                if (attempt == 1) throw error
                delay(250)
            }
        }
        error("直播媒体请求失败")
    }

    private suspend fun downloadFlv() {
        fetchLiveStream(url, roomId, onReceived = ::onReceived) { bytes, offset, length ->
            buffer.write(bytes, offset, length)
            ready.complete(Unit)
        }
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
                    cache.close()
                } finally { closedComplete.complete(Unit) }
            }
        }
    }

    suspend fun awaitClosed() {
        cancel()
        closedComplete.await()
    }
}
