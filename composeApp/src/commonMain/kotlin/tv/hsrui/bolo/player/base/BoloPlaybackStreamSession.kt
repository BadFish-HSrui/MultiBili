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
import tv.hsrui.network.feature.player.PlayerStreamException
import tv.hsrui.network.feature.player.PlayerStreamResponseData
import tv.hsrui.network.feature.player.fetchPlayerStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.TimeSource

internal enum class BoloPlaybackTrack { Video, Audio }
internal data class BoloPlaybackStreamSource(val track: BoloPlaybackTrack, val url: String, val nominalBitrateBps: Long?)
internal data class BoloPlaybackByteRange(val start: Long, val endExclusive: Long)
internal data class BoloPlaybackTrackSnapshot(
    val totalBytes: Long?,
    val cachedRanges: List<BoloPlaybackByteRange>,
    val forwardBytes: Long,
    val backwardBytes: Long,
    val receivedBytes: Long,
    val position: Long,
    val downloadBytesPerSecond: Long?,
)
internal data class BoloPlaybackStreamSnapshot(
    val generation: Long,
    val video: BoloPlaybackTrackSnapshot,
    val audio: BoloPlaybackTrackSnapshot?,
    val allocatedBytes: Long,
)

internal class BoloPlaybackStreamSession(
    val generation: Long,
    sources: List<BoloPlaybackStreamSource>,
    private val onFailure: (BoloPlaybackTrack, Throwable) -> Unit,
) {
    internal class Reader internal constructor(val track: BoloPlaybackTrack, val job: Job) {
        val position = AtomicLong(0)
        var waiting = false // 会话 mutex 保护。
    }

    private class Track(val source: BoloPlaybackStreamSource) {
        val ready = CompletableDeferred<Unit>()
        val readers = mutableSetOf<Reader>()
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    val hasFailed: Boolean get() = failed.load()
    private val mutex = Mutex()
    private val changed = MutableStateFlow(0L)
    private val cache = BoloPlaybackCache()
    private val tracks = sources.associate { it.track to Track(it) }
    private val clock = TimeSource.Monotonic.markNow()
    private var prepared = false
    private var suspended = false
    private var videoPrefetch = true
    val videoUri: String get() = "bolo-stream://$generation/video"
    val audioUri: String? get() = "bolo-stream://$generation/audio".takeIf { BoloPlaybackTrack.Audio in tracks }

    suspend fun prepare() {
        mutex.withLock {
            check(!closed.load())
            if (!prepared) {
                prepared = true
                tracks.values.forEach { track -> scope.launch { runDownload(track.source.track) } }
            }
        }
        tracks.values.forEach { it.ready.await() }
    }

    fun cancel() {
        if (closed.compareAndSet(false, true)) {
            tracks.values.forEach { it.ready.cancel() }
            scope.cancel()
        }
    }

    suspend fun setActivity(networkEnabled: Boolean, prefetchVideo: Boolean) = mutex.withLock {
        suspended = !networkEnabled
        videoPrefetch = prefetchVideo
        tracks.values.forEach { track ->
            if (suspended || (!prefetchVideo && track.source.track == BoloPlaybackTrack.Video && track.readers.none { it.waiting })) {
                track.request?.cancel()
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
        tracks[reader.track]?.readers?.remove(reader)
        reader.job.cancel()
        signal()
    }

    suspend fun seek(reader: Reader, position: Long): Long = mutex.withLock {
        if (position < 0 || closed.load() || !reader.job.isActive) return@withLock -1L
        val track = tracks.getValue(reader.track)
        reader.position.store(position)
        reader.waiting = false
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
                    signal()
                    return@withLock 0
                }
                val found = cache.read(reader.track, position, buffer)
                if (found > 0) {
                    reader.position.store(position + found)
                    reader.waiting = false
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

    private fun signal() { changed.value += 1 }

    private fun requestNeeded(track: Track) = track.readers.any {
        it.job.isActive && it.waiting && it.position.load() >= track.requestStart && it.position.load() < track.requestEnd
    }

    private fun targets(): Map<BoloPlaybackTrack, Long> {
        val active = tracks.values.filter { track ->
            val waiting = track.readers.any { it.waiting && it.job.isActive && (track.total == null || it.position.load() < track.total!!) }
            (track.source.track != BoloPlaybackTrack.Video || videoPrefetch || waiting) &&
                (track.total == null || track.position < track.total!! || waiting)
        }
        val known = active.all { (it.source.nominalBitrateBps ?: 0L) > 0L }
        val weight = if (known) active.sumOf { it.source.nominalBitrateBps!!.toDouble() } else active.size.toDouble()
        val extra = BoloPlaybackCache.ForwardBytes - active.size * BoloPlaybackCache.MinimumBytes
        return tracks.mapValues { (_, track) ->
            if (track !in active) 0L else BoloPlaybackCache.MinimumBytes +
                (extra * (if (known) track.source.nominalBitrateBps!!.toDouble() else 1.0) / weight).toLong()
        }
    }

    private suspend fun runDownload(kind: BoloPlaybackTrack) {
        val track = tracks.getValue(kind)
        while (!closed.load() && !failed.load()) {
            var revision = 0L
            val request = mutex.withLock {
                revision = changed.value
                if (suspended) return@withLock null
                val pending = track.readers.firstOrNull { it.waiting && it.job.isActive }
                val cursor = pending?.position?.load() ?: track.position
                val target = targets()
                val canPrefetch = kind != BoloPlaybackTrack.Video || videoPrefetch
                if (!canPrefetch && pending == null && track.metadata != null) return@withLock null
                val start = cache.firstMissing(kind, cursor)
                val limit = if (canPrefetch) target.getValue(kind) else BoloPlaybackCache.MinimumBytes
                val windowEnd = (cursor + limit) / BoloPlaybackCache.BlockSize * BoloPlaybackCache.BlockSize
                val end = minOf(start + BoloPlaybackCache.MinimumBytes, windowEnd, track.total ?: Long.MAX_VALUE)
                if (end <= start) return@withLock null
                if (!cache.reserve(kind, start, end, tracks.mapValues { it.value.position }, target,
                        tracks.mapValues { it.value.readers.filter { reader -> reader.job.isActive }.map { reader -> reader.position.load() } })) return@withLock null
                if (pending != null) { track.readers.remove(pending); track.readers.add(pending) }
                val job = Job(scope.coroutineContext[Job])
                track.request = job
                track.requestStart = start
                track.requestEnd = end
                track.allocationStart = start
                track.allocationEnd = end
                job
            }
            if (request == null) {
                changed.first { it != revision }
                continue
            }
            var retryDelay = 0L
            try {
                withContext(request) {
                    val start = track.requestStart
                    val end = track.requestEnd
                    val previous = track.metadata
                    val response = fetchPlayerStream(track.source.url, start, end,
                        validator = previous?.etag?.takeUnless { it.startsWith("W/") } ?: previous?.lastModified,
                        continueFullResponse = true,
                        onResponse = { metadata -> mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            validate(track, metadata)
                            track.metadata = metadata.copy(
                                etag = metadata.etag ?: track.metadata?.etag,
                                lastModified = metadata.lastModified ?: track.metadata?.lastModified,
                            )
                            if (metadata.statusCode == 200) track.requestEnd = metadata.totalBytes ?: Long.MAX_VALUE
                            metadata.totalBytes?.let { track.total = it }
                            track.ready.complete(Unit)
                            signal()
                        } },
                        onReceived = { count -> mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            track.received += count
                            val slot = clock.elapsedNow().inWholeMilliseconds / 50
                            track.speedBuckets[slot] = (track.speedBuckets[slot] ?: 0L) + count
                            track.speedBuckets.keys.removeAll { it < slot - 20 }
                        } },
                        write = { offset, bytes, at, count ->
                            if (track.metadata?.statusCode == 200) writeSequential(track, offset, bytes, at, count)
                            else mutex.withLock {
                                currentCoroutineContext().ensureActive()
                                cache.write(kind, offset, bytes, at, count)
                                signal()
                            }
                        },
                    )
                    mutex.withLock {
                        validate(track, response)
                        response.totalBytes?.let { track.total = it }
                    }
                }
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (error: Throwable) {
                val retry = when (error) {
                    is PlayerStreamException -> error.retryable
                    is IllegalArgumentException, is IllegalStateException -> false
                    else -> error is Exception
                }
                if (retry && track.failures < 2) {
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
                // 取消当前请求只结束该次范围；会话停止时由 GC 回收全部缓存。
                withContext(kotlinx.coroutines.NonCancellable) {
                    mutex.withLock {
                        cache.finish(kind)
                        if (track.request === request) track.request = null
                        signal()
                    }
                }
                request.complete()
            }
            if (retryDelay > 0) delay(retryDelay)
        }
    }

    /** 忽略 Range 的源保持同一响应，缓存满时背压；向前 seek 可直接跳过未消费前缀。 */
    private suspend fun writeSequential(track: Track, offset: Long, bytes: ByteArray, at: Int, count: Int) {
        var position = offset
        val end = offset + count
        val kind = track.source.track
        while (position < end) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val progressed = mutex.withLock {
                currentCoroutineContext().ensureActive()
                val pending = track.readers.firstOrNull { it.waiting && it.job.isActive }
                val cursor = pending?.position?.load() ?: track.position
                if (position < cursor) {
                    position = minOf(cursor, end)
                    track.requestStart = position
                    return@withLock true
                }
                val target = targets()
                val limit = if (kind == BoloPlaybackTrack.Video && !videoPrefetch) BoloPlaybackCache.MinimumBytes else target.getValue(kind)
                val windowEnd = (cursor + limit) / BoloPlaybackCache.BlockSize * BoloPlaybackCache.BlockSize
                if (position >= track.allocationEnd || position < track.allocationStart) {
                    cache.finish(kind)
                    track.allocationStart = position
                    track.allocationEnd = position
                    val allocationEnd = minOf(position + BoloPlaybackCache.MinimumBytes, windowEnd, track.total ?: Long.MAX_VALUE)
                    if (allocationEnd > position && cache.reserve(kind, position, allocationEnd,
                            tracks.mapValues { it.value.position }, target,
                            tracks.mapValues { it.value.readers.filter { reader -> reader.job.isActive }.map { reader -> reader.position.load() } })) {
                        track.allocationEnd = allocationEnd
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
            if (!progressed) changed.first { it != revision }
        }
    }

    private fun validate(track: Track, response: PlayerStreamResponseData) {
        val previous = track.metadata
        if ((track.total != null && response.totalBytes != null && track.total != response.totalBytes) ||
            (previous?.etag != null && response.etag != null && previous.etag != response.etag) ||
            (previous?.lastModified != null && response.lastModified != null && previous.lastModified != response.lastModified)) {
            throw PlayerStreamException("播放资源发生变化", false)
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
            return BoloPlaybackTrackSnapshot(track.total, ranges, forward, backward, track.received, track.position, speed)
        }
        BoloPlaybackStreamSnapshot(generation, checkNotNull(sample(BoloPlaybackTrack.Video)), sample(BoloPlaybackTrack.Audio), cache.allocatedBytes)
    }
}
