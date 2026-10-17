@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.network.feature.player.PlayerStreamException
import tv.hsrui.network.feature.player.PlayerStreamIndexData
import tv.hsrui.network.feature.player.PlayerStreamResponseData
import tv.hsrui.network.feature.player.fetchPlayerStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.TimeSource

internal enum class BoloPlaybackTrack { Video, Audio }
internal data class BoloPlaybackStreamSource(
    val track: BoloPlaybackTrack,
    val urls: List<String>,
    val nominalBitrateBps: Long?,
    val index: PlayerStreamIndexData? = null,
)
internal data class BoloPlaybackByteRange(val start: Long, val endExclusive: Long)
internal data class BoloPlaybackTrackSnapshot(
    val totalBytes: Long?,
    val cachedRanges: List<BoloPlaybackByteRange>,
    val forwardBytes: Long,
    val backwardBytes: Long,
    val receivedBytes: Long,
    val position: Long,
    val downloadBytesPerSecond: Long?,
    val bufferedRanges: List<BoloPlaybackTimeRange> = emptyList(),
    val indexKnown: Boolean = false,
)
internal data class BoloPlaybackStreamSnapshot(
    val generation: Long,
    val video: BoloPlaybackTrackSnapshot,
    val audio: BoloPlaybackTrackSnapshot?,
    val allocatedBytes: Long,
    val effectiveCapacityBytes: Long = allocatedBytes,
    val memoryLimited: Boolean = false,
    val forwardTargetBytes: Long = 0L,
)

internal class BoloPlaybackMemoryException : Exception("播放缓存无法取得可用内存")

internal class BoloPlaybackStreamSession(
    val generation: Long,
    sources: List<BoloPlaybackStreamSource>,
    private val optimizationEnabled: Boolean = true,
    private val cache: BoloPlaybackCache = BoloPlaybackCache(
        getKoin().get<BoloSettings>().playback.cacheSizeMiB.toLong() * 1024 * 1024,
        availableMemory = {
            // 新预留还会启动下载暂存；先覆盖其最坏增长，再保留缓存自身的运行余量。
            playbackAvailableMemoryBytes()?.let {
                (it - if (optimizationEnabled) BoloPlaybackDownloader.StagingLimit else 0).coerceAtLeast(0L)
            }
        },
    ),
    private val onFailure: (BoloPlaybackTrack, Throwable) -> Unit,
) {
    internal class Reader internal constructor(val track: BoloPlaybackTrack, val job: Job) {
        val position = AtomicLong(0)
        var waiting = false // 会话 mutex 保护。
    }

    private class Track(val source: BoloPlaybackStreamSource, optimizationEnabled: Boolean) {
        val candidates = if (optimizationEnabled) BoloPlaybackCandidates(source.urls) else null
        val ready = CompletableDeferred<Unit>()
        val readers = mutableSetOf<Reader>()
        val urgencyStart = AtomicLong(-1L)
        var position = 0L
        var total: Long? = null
        var metadata: PlayerStreamResponseData? = null
        var received = 0L
        val speedBuckets = mutableMapOf<Long, Long>()
        var request: Job? = null
        var requestStart = 0L
        var requestEnd = 0L
        var allocationStart = 0L
        var allocationEnd = 0L
        var failures = 0
        var index: BoloPlaybackIndex? = null
        var indexRead: BoloPlaybackByteRange? = null
        var indexDisabled = false
        var indexReceived = 0L
        var indexFailures = 0
        var requestIsIndex = false
        var fullResponseSeen = false
        var resourceChanged = false
        var waitingForMemory = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    val hasFailed: Boolean get() = failed.load()
    private val requiredMemoryWait = AtomicBoolean(false)
    val hasRequiredMemoryWait: Boolean get() = requiredMemoryWait.load()
    private val closedComplete = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val changed = MutableStateFlow(0L)
    private val downloader = BoloPlaybackDownloader()
    private val tracks = sources.associate { it.track to Track(it, optimizationEnabled) }
    private val demand = AtomicReference<BoloPlaybackDownloadDemand?>(null)
    private val clock = TimeSource.Monotonic.markNow()
    private var prepared = false
    private var suspended = false
    private var videoPrefetch = true
    val videoUri: String get() = "bolo-stream://$generation/video"
    val audioUri: String? get() = "bolo-stream://$generation/audio".takeIf { BoloPlaybackTrack.Audio in tracks }

    fun updateDemand(value: BoloPlaybackDownloadDemand) {
        if (value.generation != generation || closed.load()) return
        demand.store(value)
        if (optimizationEnabled) downloader.updateDemand(value)
    }

    suspend fun prepare() {
        mutex.withLock {
            check(!closed.load())
            if (!prepared) {
                prepared = true
                tracks.values.forEach { track ->
                    scope.launch { runDownload(track.source.track) }
                    scope.launch { prepareIndex(track) }
                }
            }
        }
        tracks.values.forEach { it.ready.await() }
    }

    fun cancel() {
        if (closed.compareAndSet(false, true)) {
            requiredMemoryWait.store(false)
            tracks.values.forEach { it.ready.cancel() }
            scope.cancel()
            // 清理不能是已取消作用域的子任务，也不能在其子任务内等待父 Job。
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    scope.coroutineContext[Job]?.join()
                    mutex.withLock {
                        cache.close()
                        signal()
                    }
                } finally {
                    closedComplete.complete(Unit)
                }
            }
        }
    }

    suspend fun awaitClosed() {
        cancel()
        closedComplete.await()
    }

    suspend fun setActivity(networkEnabled: Boolean, prefetchVideo: Boolean) = mutex.withLock {
        suspended = !networkEnabled
        videoPrefetch = prefetchVideo
        tracks.values.forEach { track ->
            if (suspended || (!prefetchVideo && track.source.track == BoloPlaybackTrack.Video && track.readers.none { it.waiting })) {
                track.request?.cancel()
                memoryWait(track, false)
            }
        }
        signal()
    }

    suspend fun open(track: BoloPlaybackTrack): Reader? = mutex.withLock {
        if (closed.load()) return@withLock null
        val state = tracks[track] ?: return@withLock null
        Reader(track, Job(scope.coroutineContext[Job])).also { state.readers.add(it) }
    }

    suspend fun close(reader: Reader) = mutex.withLock {
        tracks[reader.track]?.let { track ->
            track.readers.remove(reader)
            if (track.readers.none { it.waiting && it.job.isActive }) memoryWait(track, false)
        }
        reader.job.cancel()
        signal()
    }

    suspend fun seek(reader: Reader, position: Long): Long = mutex.withLock {
        if (position < 0 || closed.load() || !reader.job.isActive) return@withLock -1L
        val track = tracks.getValue(reader.track)
        reader.position.store(position)
        reader.waiting = false
        memoryWait(track, false)
        track.position = position
        if ((position < track.requestStart || position >= track.requestEnd) && !requestNeeded(track)) track.request?.cancel()
        signal()
        position
    }

    suspend fun size(reader: Reader): Long = mutex.withLock { tracks.getValue(reader.track).total ?: -1L }

    suspend fun read(reader: Reader, buffer: ByteArray): Int {
        while (true) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val count = mutex.withLock {
                if (closed.load() || failed.load() || !reader.job.isActive) return@withLock -1
                val track = tracks.getValue(reader.track)
                val position = reader.position.load()
                if (track.total?.let { position >= it } == true) {
                    reader.waiting = false
                    if (track.readers.none { it.waiting && it.job.isActive }) memoryWait(track, false)
                    signal()
                    return@withLock 0
                }
                val found = cache.read(reader.track, position, buffer)
                if (found > 0) {
                    reader.position.store(position + found)
                    reader.waiting = false
                    memoryWait(track, false)
                    track.position = position + found
                    signal()
                    found
                } else {
                    reader.waiting = true
                    track.position = position
                    if ((position < track.requestStart || position >= track.requestEnd) && !requestNeeded(track)) track.request?.cancel()
                    signal()
                    revision = changed.value
                    null
                }
            }
            if (count != null) return count
            changed.first { it != revision }
        }
    }

    private fun signal() {
        tracks.values.forEach { track ->
            track.urgencyStart.store(track.readers.firstOrNull { it.waiting && it.job.isActive }?.position?.load() ?: -1L)
        }
        changed.value += 1
    }

    private fun requestNeeded(track: Track) = track.readers.any {
        it.job.isActive && it.waiting && it.position.load() >= track.requestStart && it.position.load() < track.requestEnd
    }

    private suspend fun prepareIndex(track: Track) {
        try {
            track.ready.await()
            val index = BoloPlaybackIndex.parse(track.source.track, track.source.index,
                totalBytes = { mutex.withLock { track.total } }, read = { start, length -> readIndexBytes(track, start, length) })
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (!closed.load() && !failed.load() && !track.indexDisabled) track.index = index
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 索引是可选显示信息；格式错误不影响媒体读取。
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    track.indexRead = null
                    signal()
                }
            }
        }
    }

    private suspend fun readIndexBytes(track: Track, start: Long, length: Int): ByteArray? {
        require(start >= 0 && length in 1..BoloPlaybackIndex.ReadLimit && start <= Long.MAX_VALUE - length)
        val bytes = ByteArray(length)
        var offset = 0
        try {
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                var revision = 0L
                val count = mutex.withLock {
                    if (closed.load() || failed.load() || track.indexDisabled ||
                        track.total?.let { start + length > it } == true) return@withLock -1
                    val position = start + offset
                    val found = cache.read(track.source.track, position, bytes, offset)
                    if (found > 0) {
                        track.indexRead = null
                        return@withLock found
                    }
                    // 200 只借用现有连续响应；已经跳过的字节不为索引重新下载。
                    if (track.fullResponseSeen && (track.request == null || position < track.requestStart)) return@withLock -1
                    if (!track.fullResponseSeen && track.indexReceived >= BoloPlaybackIndex.ScanLimit) return@withLock -1
                    track.indexRead = BoloPlaybackByteRange(position, start + length)
                    if (!track.fullResponseSeen && (position < track.requestStart || position >= track.requestEnd) &&
                        !requestNeeded(track)) track.request?.cancel()
                    signal()
                    revision = changed.value
                    0
                }
                if (count < 0) return null
                if (count > 0) offset += count else changed.first { it != revision }
            }
            return bytes
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock { track.indexRead = null; signal() } }
        }
    }

    private fun targets(positions: Map<BoloPlaybackTrack, Long>): Map<BoloPlaybackTrack, Long> {
        val active = tracks.values.filter { track ->
            val waiting = track.readers.any { it.waiting && it.job.isActive && (track.total == null || it.position.load() < track.total!!) }
            (track.source.track != BoloPlaybackTrack.Video || videoPrefetch || waiting) &&
                (track.total == null || track.position < track.total!! || waiting)
        }.sortedBy { it.source.track.ordinal }
        val budget = cache.forwardTarget(positions) / BoloPlaybackCache.BlockSize
        val limits = active.associate { track ->
            val remaining = track.total?.let { total ->
                if (total <= positions.getValue(track.source.track)) 0L else
                    (total - 1) / BoloPlaybackCache.BlockSize + 1 - positions.getValue(track.source.track) / BoloPlaybackCache.BlockSize
            } ?: budget
            track.source.track to minOf(remaining, budget)
        }
        val allocated = tracks.mapValues { 0L }.toMutableMap()
        var baseRemaining = budget
        val baseOrder = active.sortedByDescending { track -> track.readers.any { it.waiting && it.job.isActive } }
        for ((index, track) in baseOrder.withIndex()) {
            val kind = track.source.track
            val remainingTracks = baseOrder.size - index
            val count = minOf(
                limits.getValue(kind),
                BoloPlaybackCache.MinimumBytes / BoloPlaybackCache.BlockSize,
                (baseRemaining + remainingTracks - 1) / remainingTracks,
            )
            allocated[kind] = count
            baseRemaining -= count
        }
        val known = active.all { (it.source.nominalBitrateBps ?: 0L) > 0L }
        var remaining = budget - allocated.values.sum()
        while (remaining > 0) {
            val available = active.filter { allocated.getValue(it.source.track) < limits.getValue(it.source.track) }
            if (available.isEmpty()) break
            val weight = if (known) available.sumOf { it.source.nominalBitrateBps!!.toDouble() } else available.size.toDouble()
            var granted = 0L
            available.forEach { track ->
                val kind = track.source.track
                val share = (remaining * (if (known) track.source.nominalBitrateBps!!.toDouble() else 1.0) / weight).toLong()
                val count = minOf(share, limits.getValue(kind) - allocated.getValue(kind))
                allocated[kind] = allocated.getValue(kind) + count
                granted += count
            }
            remaining -= granted
            if (granted == 0L) {
                for (track in available) {
                    if (remaining == 0L) break
                    val kind = track.source.track
                    allocated[kind] = allocated.getValue(kind) + 1
                    remaining--
                }
            }
        }
        return allocated.mapValues { it.value * BoloPlaybackCache.BlockSize }
    }

    private fun memoryWait(track: Track, waiting: Boolean) {
        if (track.waitingForMemory == waiting) return
        track.waitingForMemory = waiting
        requiredMemoryWait.store(tracks.values.any { it.waitingForMemory })
        signal()
    }

    /** 返回实际预留末尾；失败时先缩为一个块，内存限额变化后使用新的完整窗口快照。 */
    private fun reserve(
        track: Track,
        start: Long,
        end: Long,
        positions: Map<BoloPlaybackTrack, Long>,
        target: Map<BoloPlaybackTrack, Long>,
        required: Boolean,
        isIndex: Boolean,
    ): Long {
        val readers = tracks.mapValues { (_, current) ->
            current.readers.filter { it.job.isActive }.map { it.position.load() }
        }
        val kind = track.source.track
        if (cache.reserve(kind, start, end, positions, target, readers, required)) {
            memoryWait(track, false)
            return end
        }
        val currentTarget = targets(positions)
        val blockRemaining = BoloPlaybackCache.BlockSize - start % BoloPlaybackCache.BlockSize
        val blockEnd = start + minOf(blockRemaining, Long.MAX_VALUE - start)
        val retryEnd = minOf(end, blockEnd)
        val position = positions.getValue(kind)
        val windowEnd = position + minOf(currentTarget.getValue(kind), Long.MAX_VALUE - position)
        if ((required || retryEnd <= windowEnd) &&
            cache.reserve(kind, start, retryEnd, positions, currentTarget, readers, required)
        ) {
            memoryWait(track, false)
            return retryEnd
        }
        if (!cache.memoryBlocked) return start
        if (isIndex) {
            track.indexDisabled = true
            track.index = null
            track.indexRead = null
            signal()
        } else if (required) {
            // 其他轨尚在写入时不能偷用其预留；取消不再需要的预取，待 finally 归还后重算。
            val pending = tracks.values.filter { it !== track && it.request != null }
            pending.filter { !requestNeeded(it) }.forEach { it.request?.cancel() }
            memoryWait(track, true)
            if (pending.isEmpty() || pending.all { it.waitingForMemory && it.request?.isActive == true }) {
                throw BoloPlaybackMemoryException()
            }
        }
        return start
    }

    private suspend fun waitForChange(revision: Long) {
        val limited = mutex.withLock { cache.memoryLimited }
        if (limited) {
            withTimeoutOrNull(BoloPlaybackCache.MemoryRetryMillis) { changed.first { it != revision } }
        } else {
            changed.first { it != revision }
        }
    }

    private fun failMemory(kind: BoloPlaybackTrack, error: BoloPlaybackMemoryException) {
        if (failed.compareAndSet(false, true)) {
            tracks.getValue(kind).ready.completeExceptionally(error)
            try {
                onFailure(kind, error)
            } finally {
                cancel()
            }
        }
    }

    private suspend fun runDownload(kind: BoloPlaybackTrack) {
        val track = tracks.getValue(kind)
        while (!closed.load() && !failed.load()) {
            var revision = 0L
            val request = try {
                mutex.withLock {
                    revision = changed.value
                    if (suspended) return@withLock null
                    cache.refreshMemory()
                    val pending = track.readers.firstOrNull { it.waiting && it.job.isActive }
                    if (pending == null && track.metadata != null) memoryWait(track, false)
                    val indexRead = track.indexRead?.takeIf {
                        pending == null && videoPrefetch && !track.indexDisabled && !track.fullResponseSeen &&
                            track.indexReceived < BoloPlaybackIndex.ScanLimit
                    }
                    val cursor = pending?.position?.load() ?: indexRead?.start ?: track.position
                    val positions = tracks.mapValues { it.value.position }
                    val target = targets(positions)
                    val required = pending != null || indexRead != null || track.metadata == null
                    val canPrefetch = kind != BoloPlaybackTrack.Video || videoPrefetch
                    if (!canPrefetch && pending == null && track.metadata != null) return@withLock null
                    val start = cache.firstMissing(kind, cursor)
                    val limit = if (!canPrefetch || (required && target.getValue(kind) == 0L)) BoloPlaybackCache.MinimumBytes else target.getValue(kind)
                    val windowEnd = (cursor + minOf(limit, Long.MAX_VALUE - cursor)) / BoloPlaybackCache.BlockSize * BoloPlaybackCache.BlockSize
                    var end = if (indexRead != null) minOf(indexRead.endExclusive,
                        start + minOf(BoloPlaybackIndex.ReadLimit.toLong(), Long.MAX_VALUE - start), track.total ?: Long.MAX_VALUE)
                    else minOf(start + minOf(BoloPlaybackCache.MinimumBytes, Long.MAX_VALUE - start), windowEnd, track.total ?: Long.MAX_VALUE)
                    if (end <= start) return@withLock null
                    end = reserve(track, start, end, positions, target, required, isIndex = indexRead != null)
                    if (end <= start) return@withLock null
                    if (pending != null) { track.readers.remove(pending); track.readers.add(pending) }
                    val job = Job(scope.coroutineContext[Job])
                    track.request = job
                    track.requestIsIndex = indexRead != null
                    track.requestStart = start
                    track.requestEnd = end
                    track.allocationStart = start
                    track.allocationEnd = end
                    job
                }
            } catch (error: BoloPlaybackMemoryException) {
                failMemory(kind, error)
                return
            }
            if (request == null) {
                waitForChange(revision)
                continue
            }
            var retryDelay = 0L
            val isIndex = track.requestIsIndex
            try {
                val priority = mutex.withLock {
                    when {
                        isIndex -> BoloPlaybackRequestPriority.Metadata
                        track.metadata == null || demand.load()?.pendingSeekMs != null -> BoloPlaybackRequestPriority.Startup
                        track.readers.any { it.waiting && it.job.isActive } -> BoloPlaybackRequestPriority.Playback
                        else -> BoloPlaybackRequestPriority.Prefetch
                    }
                }
                val index = mutex.withLock { track.index }
                withContext(request) {
                    val start = track.requestStart
                    val end = track.requestEnd
                    val requestMetadata = AtomicReference<PlayerStreamResponseData?>(null)
                    val onResponse: suspend (PlayerStreamResponseData) -> Unit = { metadata -> mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        if (!request.isActive || closed.load() || failed.load() || track.request !== request)
                            throw CancellationException("播放下载请求已失效")
                        validate(track, metadata)
                        requestMetadata.store(metadata)
                        track.metadata = if (optimizationEnabled) metadata else metadata.copy(
                            etag = metadata.etag ?: track.metadata?.etag,
                            lastModified = metadata.lastModified ?: track.metadata?.lastModified,
                        )
                        if (metadata.statusCode == 200) {
                            track.fullResponseSeen = true
                            if (!isIndex) track.requestEnd = metadata.totalBytes ?: Long.MAX_VALUE
                        }
                        metadata.totalBytes?.let { track.total = it }
                        track.ready.complete(Unit)
                        signal()
                    } }
                    val onReceived: suspend (Int) -> Unit = { count -> withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock {
                        track.received += count
                        if (isIndex) track.indexReceived += count
                        val slot = clock.elapsedNow().inWholeMilliseconds / 50
                        track.speedBuckets[slot] = (track.speedBuckets[slot] ?: 0L) + count
                        track.speedBuckets.keys.removeAll { it < slot - 20 }
                    } } }
                    val write: suspend (Long, ByteArray, Int, Int) -> Unit = { offset, bytes, at, count ->
                        if (requestMetadata.load()?.statusCode == 200 && !isIndex) writeSequential(track, request, offset, bytes, at, count)
                        else mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            if (!request.isActive || closed.load() || failed.load() || track.request !== request)
                                throw CancellationException("播放下载请求已失效")
                            cache.write(kind, offset, bytes, at, count)
                            signal()
                        }
                    }
                    val receiveLimit = if (isIndex) BoloPlaybackIndex.ScanLimit - track.indexReceived else null
                    val response = if (optimizationEnabled) downloader.download(checkNotNull(track.candidates), start, end,
                        priority = priority,
                        deadlineMs = { position ->
                            val current = demand.load()
                            val segment = index?.segments?.firstOrNull { position >= it.startByte && position < it.endByte }
                            if (current == null || segment == null || isIndex) null
                            else if (current.pendingSeekMs != null || current.buffering) 0L
                            else if (!current.playing) null
                            else ((segment.startMs - current.positionMs).coerceAtLeast(0L) / current.playbackSpeed).toLong()
                        },
                        urgencyStart = { track.urgencyStart.load().takeIf { it >= 0L } },
                        readHead = { buffer -> mutex.withLock { cache.read(kind, 0L, buffer) } },
                        missingBytes = { offset, count -> mutex.withLock { cache.missingBytes(kind, offset, count) } },
                        continueFullResponse = !isIndex, receiveLimit = receiveLimit,
                        onResponse = onResponse, onReceived = onReceived, write = write,
                    ) else fetchPlayerStream(track.source.urls.first(), start, end,
                        validator = track.metadata?.etag?.takeUnless { it.startsWith("W/") },
                        continueFullResponse = !isIndex, receiveLimit = receiveLimit,
                        onResponse = onResponse, onReceived = onReceived, write = write,
                    )
                    mutex.withLock {
                        validate(track, response)
                        response.totalBytes?.let { track.total = it }
                    }
                }
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (error: BoloPlaybackMemoryException) {
                failMemory(kind, error)
            } catch (error: Throwable) {
                if (error is PlayerStreamException && error.resourceChanged) track.resourceChanged = true
                val retry = when (error) {
                    is PlayerStreamException -> error.retryable &&
                        (optimizationEnabled || error.statusCode !in listOf(412, 429))
                    is IllegalArgumentException, is IllegalStateException -> false
                    else -> error is Exception
                }
                if (isIndex && !track.resourceChanged) {
                    mutex.withLock {
                        if (retry && track.indexFailures < 2 && track.indexReceived < BoloPlaybackIndex.ScanLimit &&
                            !track.fullResponseSeen) {
                            track.indexFailures++
                            retryDelay = if (track.indexFailures == 1) 250 else 1_000
                        } else {
                            track.indexDisabled = true
                            track.index = null
                        }
                        signal()
                    }
                } else if (retry && track.failures < 2) {
                    track.failures++
                    retryDelay = if (track.failures == 1) 250 else 1_000
                } else {
                    track.ready.completeExceptionally(error)
                    if (failed.compareAndSet(false, true)) {
                        onFailure(kind, error)
                        cancel()
                    }
                }
            } finally {
                // download 的所有分块及输家已退出，才能释放该轨外层范围的预留。
                withContext(kotlinx.coroutines.NonCancellable) {
                    mutex.withLock {
                        cache.finish(kind)
                        if (track.request === request) track.request = null
                        memoryWait(track, false)
                        signal()
                    }
                }
                request.complete()
            }
            if (retryDelay > 0) {
                if (isIndex) withTimeoutOrNull(retryDelay) {
                    changed.first { mutex.withLock { suspended || track.readers.any { it.waiting && it.job.isActive } } }
                } else delay(retryDelay)
            }
        }
    }

    /** 忽略 Range 的源保持同一响应，缓存满时背压；向前 seek 可直接跳过未消费前缀。 */
    private suspend fun writeSequential(track: Track, request: Job, offset: Long, bytes: ByteArray, at: Int, count: Int) {
        var position = offset
        val end = offset + count
        val kind = track.source.track
        while (position < end) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val progressed = mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (!request.isActive || closed.load() || failed.load() || track.request !== request)
                    throw CancellationException("播放下载请求已失效")
                cache.refreshMemory()
                val pending = track.readers.firstOrNull { it.waiting && it.job.isActive }
                if (pending == null) memoryWait(track, false)
                val cursor = pending?.position?.load() ?: track.position
                if (position < cursor) {
                    position = minOf(cursor, end)
                    track.requestStart = position
                    return@withLock true
                }
                val positions = tracks.mapValues { it.value.position }
                val target = targets(positions)
                val required = pending != null || track.indexRead != null
                val limit = if ((kind == BoloPlaybackTrack.Video && !videoPrefetch) || (required && target.getValue(kind) == 0L))
                    BoloPlaybackCache.MinimumBytes else target.getValue(kind)
                val windowEnd = (cursor + minOf(limit, Long.MAX_VALUE - cursor)) / BoloPlaybackCache.BlockSize * BoloPlaybackCache.BlockSize
                if (position >= track.allocationEnd || position < track.allocationStart) {
                    cache.finish(kind)
                    track.allocationStart = position
                    track.allocationEnd = position
                    val allocationEnd = minOf(position + minOf(BoloPlaybackCache.MinimumBytes, Long.MAX_VALUE - position), windowEnd, track.total ?: Long.MAX_VALUE)
                    if (allocationEnd > position) {
                        track.allocationEnd = reserve(track, position, allocationEnd, positions, target,
                            required, isIndex = pending == null && track.indexRead != null)
                    }
                }
                val next = minOf(end, track.allocationEnd, windowEnd)
                if (!suspended && next > position) {
                    cache.write(kind, position, bytes, at + (position - offset).toInt(), (next - position).toInt())
                    position = next
                    track.requestStart = position
                    signal()
                    true
                } else {
                    revision = changed.value
                    false
                }
            }
            if (!progressed) waitForChange(revision)
        }
    }

    private fun validate(track: Track, response: PlayerStreamResponseData) {
        val previous = track.metadata
        if ((track.total != null && response.totalBytes != null && track.total != response.totalBytes) ||
            (!optimizationEnabled && previous?.etag != null && response.etag != null && previous.etag != response.etag)) {
            track.resourceChanged = true
            throw PlayerStreamException("播放资源发生变化", false, resourceChanged = true)
        }
    }

    suspend fun snapshot(): BoloPlaybackStreamSnapshot = mutex.withLock {
        val now = clock.elapsedNow().inWholeMilliseconds
        fun sample(kind: BoloPlaybackTrack): BoloPlaybackTrackSnapshot? {
            val track = tracks[kind] ?: return null
            val ranges = cache.ranges(kind)
            val forward = ranges.sumOf { (it.endExclusive - maxOf(it.start, track.position)).coerceAtLeast(0L) }
            val backward = ranges.sumOf { (minOf(it.endExclusive, track.position) - it.start).coerceAtLeast(0L) }
            val speed = if (track.metadata == null) null else
                track.speedBuckets.filterKeys { it > now / 50 - 20 }.values.sum() * 1_000 / now.coerceIn(1L, 1_000L)
            return BoloPlaybackTrackSnapshot(track.total, ranges, forward, backward, track.received, track.position, speed,
                track.index?.bufferedRanges(ranges).orEmpty(), track.index != null)
        }
        BoloPlaybackStreamSnapshot(
            generation = generation,
            video = checkNotNull(sample(BoloPlaybackTrack.Video)),
            audio = sample(BoloPlaybackTrack.Audio),
            allocatedBytes = cache.allocatedBytes,
            effectiveCapacityBytes = cache.effectiveCapacityBytes,
            memoryLimited = cache.memoryLimited,
            forwardTargetBytes = cache.forwardTarget(tracks.mapValues { it.value.position }),
        )
    }
}
