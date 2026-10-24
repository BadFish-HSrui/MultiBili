package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.hsrui.bolo.player.base.BoloPlaybackBuffer
import tv.hsrui.bolo.player.base.allocatePlaybackBuffer
import tv.hsrui.bolo.player.base.playbackAvailableMemoryBytes

internal data class LiveCachedSegment(
    val id: Long,
    val sequence: Long,
    val continuity: Long,
    val initializationId: Long?,
    val media: LiveHlsMediaIndexData,
    val groupId: Long?,
    val size: Int,
)

data class LiveCacheRange(val startMs: Long, val endMs: Long)

internal data class LiveCacheSnapshot(
    val revision: Long = 0,
    val segments: List<LiveCachedSegment> = emptyList(),
    val ranges: List<LiveCacheRange> = emptyList(),
    val allocatedBytes: Long = 0,
    val capacityBytes: Long = 256L * 1024 * 1024,
    val ended: Boolean = false,
    val epoch: Long = 0,
    /** 实际留存的正文长度，不含已分配块内的空余空间。 */
    val cachedBytes: Long = 0,
    val loadingBytes: Long = 0,
    val loadingCapacityBytes: Long = 32L * 1024 * 1024,
    val historyBytes: Long = 0,
    val historyCapacityBytes: Long = 0,
)

/** 加载与历史分别计费，发布时只转移块所有权；读游标不阻止历史淘汰。 */
internal class LivePlaybackCache(
    private val capacity: Long = 256L * 1024 * 1024,
    private val allocate: (Int) -> BoloPlaybackBuffer? = ::allocatePlaybackBuffer,
    private val availableMemory: () -> Long? = ::playbackAvailableMemoryBytes,
    private val mutex: Mutex = Mutex(),
    private val retainHistory: Boolean = true,
    private val loadingCapacity: Long = if (retainHistory) 32L * 1024 * 1024 else capacity,
) {
    internal class Missing(val targetMs: Long?) : Exception("直播缓存位置已不可用") {
        var candidate: Int? = null
    }

    internal class Body {
        val blocks = mutableListOf<BoloPlaybackBuffer>()
        var size = 0
        var released = false
        var history = false
        var sequence: Long? = null
        var redundant = false
        var expectedSize: Long? = null
        var owner: Job? = null
        var candidate = -1
        var sourcePlaylist: tv.hsrui.network.liveFeature.player.LiveHlsPlaylistData? = null
        private val byte = ByteArray(1)
        fun byteAt(index: Int): Int {
            require(index in 0 until size)
            blocks[index / BLOCK].copyInto(byte, 0, index % BLOCK, index % BLOCK + 1)
            return byte[0].toInt() and 255
        }
        fun copy(offset: Int, destination: ByteArray, count: Int): Int {
            val length = minOf(count, size - offset)
            var copied = 0
            while (copied < length) {
                val position = offset + copied
                val amount = minOf(length - copied, BLOCK - position % BLOCK)
                blocks[position / BLOCK].copyInto(destination, copied, position % BLOCK, position % BLOCK + amount)
                copied += amount
            }
            return length
        }
        fun sameContent(other: Body): Boolean {
            if (size != other.size) return false
            val left = ByteArray(BLOCK)
            val right = ByteArray(BLOCK)
            var offset = 0
            while (offset < size) {
                val count = copy(offset, left, minOf(BLOCK, size - offset))
                other.copy(offset, right, count)
                for (i in 0 until count) if (left[i] != right[i]) return false
                offset += count
            }
            return true
        }
    }
    private class Entry(val segment: LiveCachedSegment, val body: Body)
    private val entries = ArrayDeque<Entry>()
    private val initializations = mutableMapOf<Long, Body>()
    private val staging = mutableSetOf<Body>()
    private var initializationId: Long? = null
    private var serial = 0L
    private var epoch = 0L
    private var allocated = 0L
    private var cached = 0L
    private var loading = 0L
    private var history = 0L
    private var headSequence: Long? = null
    private var closed = false
    private var ended = false
    private var lastAppended: LiveCachedSegment? = null
    private val mutableSnapshot = MutableStateFlow(
        LiveCacheSnapshot(
            capacityBytes = capacity,
            loadingCapacityBytes = loadingCapacity,
            historyCapacityBytes = if (retainHistory) capacity else 0,
        )
    )
    val snapshot = mutableSnapshot.asStateFlow()

    suspend fun stage(sequence: Long? = null, redundant: Boolean = false): Body {
        val owner = currentCoroutineContext()[Job]
        return mutex.withLock {
            check(!closed)
            Body().also {
                it.sequence = sequence
                it.redundant = redundant
                it.owner = owner
                staging.add(it)
            }
        }
    }

    suspend fun prioritize(sequence: Long) = mutex.withLock {
        headSequence = sequence
        publish()
    }

    suspend fun expect(body: Body, size: Long?) = mutex.withLock {
        check(size == null || size <= loadingCapacity) { "直播分片超过加载容量" }
        body.expectedSize = size
        publish()
    }

    suspend fun hold(body: Body, owner: Job?) = mutex.withLock { body.owner = owner }

    suspend fun reset(body: Body) = mutex.withLock {
        check(body in staging && !body.history)
        val owner = body.owner
        free(body)
        body.owner = owner
        body.released = false
        body.size = 0
        body.expectedSize = null
        publish()
    }

    suspend fun matchesInitialization(body: Body): Boolean = mutex.withLock {
        initializationId?.let(initializations::get)?.sameContent(body) == true
    }

    suspend fun write(body: Body, bytes: ByteArray, offset: Int, length: Int) {
        var copied = 0
        while (copied < length) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val written = mutex.withLock {
                check(!closed && body in staging && !body.released)
                if (body.size % BLOCK == 0) {
                    check((body.blocks.size + 1L) * BLOCK <= loadingCapacity) { "直播分片超过加载容量" }
                    val head = staging.firstOrNull { it.sequence == headSequence && !it.redundant }
                        ?: staging.firstOrNull { it.sequence == headSequence }
                    val necessary = body === head || head == null && body.sequence == null
                    val reserve = if (!necessary && head != null && head !== body) {
                        head.expectedSize?.let { ((it + BLOCK - 1) / BLOCK - head.blocks.size).coerceAtLeast(0) * BLOCK }
                            ?: loadingCapacity
                    } else if (!necessary && headSequence != null && body.sequence != headSequence) loadingCapacity else 0L
                    if (loading + BLOCK + reserve > loadingCapacity) {
                        if (necessary) {
                            staging.filter {
                                it !== body && it !== head && (it.redundant || it.sequence != null && it.sequence != headSequence)
                            }.forEach { it.owner?.cancel(CancellationException("直播加载优先保留当前分片")) }
                            check(
                                staging.any { it !== body && it.blocks.isNotEmpty() } || !retainHistory && entries.isNotEmpty()
                            ) { "直播必要分片无法容纳于加载容量" }
                        }
                        revision = mutableSnapshot.value.revision
                        return@withLock 0
                    }
                    var recycled: BoloPlaybackBuffer? = null
                    try {
                        // 物理内存紧张先回收历史，不改变任何逻辑预算或用户设置。
                        if (availableMemory()?.let { it < MEMORY_RESERVE + BLOCK } == true && retainHistory) {
                            while (entries.isNotEmpty() && recycled == null) recycled = evictGroup(recycle = true)
                        }
                        if (recycled == null && availableMemory()?.let { it < MEMORY_RESERVE + BLOCK } != true) {
                            recycled = try { allocate(BLOCK) } catch (_: OutOfMemoryError) { null }
                        }
                        while (recycled == null && retainHistory && entries.isNotEmpty()) recycled = evictGroup(recycle = true)
                        if (recycled == null) {
                            val victims = staging.filter {
                                it !== body && it !== head && (it.redundant || it.sequence != null && it.sequence != headSequence)
                            }
                            if (necessary && victims.any { it.blocks.isNotEmpty() }) {
                                victims.forEach { it.owner?.cancel(CancellationException("直播内存不足，释放预取")) }
                                publish()
                                revision = mutableSnapshot.value.revision
                                return@withLock 0
                            }
                            if (!necessary) throw CancellationException("直播内存不足，停止预取")
                            error("直播加载内存不足")
                        }
                        body.blocks += recycled
                        allocated += BLOCK
                        loading += BLOCK
                    } catch (error: Throwable) {
                        recycled?.close()
                        publish()
                        throw error
                    }
                }
                val amount = minOf(length - copied, BLOCK - body.size % BLOCK)
                body.blocks.last().copyFrom(bytes, offset + copied, body.size % BLOCK, amount)
                body.size += amount
                cached += amount
                publish()
                amount
            }
            if (written == 0) snapshot.first { it.revision != revision } else copied += written
        }
    }

    suspend fun discard(body: Body, onlyStaged: Boolean = false) = mutex.withLock {
        if (!onlyStaged || body in staging) { staging.remove(body); free(body); publish() }
    }

    /** 返回初始化标识及内容是否变化；相同地址也逐字节核对。 */
    suspend fun initialize(body: Body): Pair<Long, Boolean> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        check(body in staging && !closed)
        val old = initializationId?.let(initializations::get)
        if (old != null && old.sameContent(body)) {
            staging.remove(body); free(body); publish()
            return@withLock checkNotNull(initializationId) to false
        }
        val changed = old != null
        if (changed) clearEntries()
        initializationId = ++serial
        staging.remove(body)
        body.owner = null
        initializations[serial] = body
        pruneInitializations()
        publish()
        serial to changed
    }

    suspend fun append(body: Body, sequence: Long, continuity: Long, init: Long?, media: LiveHlsMediaIndexData) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        check(!closed && body in staging && body.size > 0 && (init == null || init in initializations))
        val previous = lastAppended
        val id = ++serial
        val start = media.decodeStartMs ?: media.startMs
        val previousEnd = previous?.media?.let { it.decodeEndMs ?: it.endMs }
        val contiguous = media.dependenciesKnown && previous != null && previous.sequence + 1 == sequence && previous.continuity == continuity &&
            previous.initializationId == init && start != null && previousEnd != null && kotlin.math.abs(start - previousEnd) <= 250
        val group = if (media.independent && media.startMs != null) id else previous?.groupId?.takeIf { contiguous }
        if (retainHistory) {
            val initialization = init?.let(initializations::get)
            val required = body.blocks.size.toLong() * BLOCK +
                (initialization?.takeUnless { it.history }?.blocks?.size ?: 0).toLong() * BLOCK
            check(required <= capacity) { "直播分片及初始化超过本地缓存容量" }
            while (history + required > capacity && entries.isNotEmpty()) evictGroup()
            check(history + required <= capacity) { "直播本地缓存容量不足" }
            for (owned in listOfNotNull(body, initialization).filter { !it.history }) {
                val size = owned.blocks.size.toLong() * BLOCK
                owned.history = true
                loading -= size
                history += size
            }
        }
        staging.remove(body)
        val segment = LiveCachedSegment(id, sequence, continuity, init, media, group, body.size)
        body.owner = null
        entries.addLast(Entry(segment, body))
        lastAppended = segment
        ended = false
        publish()
    }

    suspend fun clear() = mutex.withLock { currentCoroutineContext().ensureActive(); clearEntries(); publish() }
    suspend fun finish() = mutex.withLock { ended = true; publish() }
    suspend fun close() = mutex.withLock {
        closed = true
        clearEntries()
        staging.toList().forEach { free(it) }
        staging.clear()
        initializations.values.forEach { free(it) }
        initializations.clear()
        initializationId = null
        publish()
    }

    fun select(targetMs: Long?): LiveCachedSegment? {
        val value = snapshot.value
        val starts = value.segments.filter { it.groupId == it.id }
        if (targetMs == null) {
            val end = value.segments.lastOrNull()?.media?.endMs
            return starts.lastOrNull { end != null && it.media.startMs!! <= end - 4_000 }
                ?: starts.firstOrNull() ?: value.segments.firstOrNull()
        }
        val range = value.ranges.firstOrNull { targetMs >= it.startMs && targetMs < it.endMs }
        return if (range != null) starts.lastOrNull { it.media.startMs!! <= targetMs && it.media.startMs >= range.startMs }
        else starts.firstOrNull { it.media.startMs!! >= targetMs } ?: starts.lastOrNull()
    }

    fun contains(position: Long?): Boolean = position != null && snapshot.value.ranges.any { position >= it.startMs && position < it.endMs }

    suspend fun awaitSegment(): LiveCachedSegment {
        snapshot.first { it.segments.isNotEmpty() || it.ended || closed }
        return select(null) ?: error("直播媒体为空")
    }

    suspend fun consume(id: Long) = mutex.withLock {
        if (!retainHistory) {
            while (entries.firstOrNull()?.segment?.id?.let { it <= id } == true) free(entries.removeFirst().body)
            pruneInitializations()
            publish()
        }
    }

    suspend fun read(id: Long, init: Boolean, offset: Int, bytes: ByteArray): Int = mutex.withLock {
        check(!closed) { "直播缓存已关闭" }
        val entry = entries.firstOrNull { it.segment.id == id } ?: throw Missing(select(null)?.media?.startMs)
        val body = if (init) entry.segment.initializationId?.let(initializations::get) else entry.body
        body?.copy(offset, bytes, bytes.size) ?: 0
    }

    suspend fun next(segment: LiveCachedSegment, expectedEpoch: Long = snapshot.value.epoch): LiveCachedSegment? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val value = snapshot.value
            check(!closed) { "直播缓存已关闭" }
            if (value.epoch != expectedEpoch) throw Missing(null)
            val next = value.segments.firstOrNull { it.id > segment.id }
            if (next != null) {
                val start = next.media.decodeStartMs ?: next.media.startMs
                val end = segment.media.decodeEndMs ?: segment.media.endMs
                val continuous = next.sequence == segment.sequence + 1 && next.continuity == segment.continuity &&
                    next.initializationId == segment.initializationId &&
                    (start == null || end == null || kotlin.math.abs(start - end) <= 250)
                if (!continuous) throw Missing(null)
                return next
            }
            if (value.ended) return null
            snapshot.first { it.revision != value.revision }
        }
    }

    private fun evictGroup(recycle: Boolean = false): BoloPlaybackBuffer? {
        val first = entries.removeFirst()
        val group = first.segment.groupId
        val reused = free(first.body, recycle)
        while (entries.firstOrNull()?.let { it.segment.groupId == group || it.segment.groupId == null } == true) free(entries.removeFirst().body)
        pruneInitializations()
        return reused
    }
    private fun clearEntries() {
        staging.filter { it.sequence != null }.forEach { it.owner?.cancel() }
        epoch++
        lastAppended = null
        entries.forEach { free(it.body) }
        entries.clear()
        ended = false
        pruneInitializations()
    }
    private fun pruneInitializations() {
        val used = entries.mapNotNull { it.segment.initializationId }.toSet() + listOfNotNull(initializationId)
        initializations.keys.filter { it !in used }.forEach { initializations.remove(it)?.let { free(it) } }
    }
    private fun free(body: Body, recycle: Boolean = false): BoloPlaybackBuffer? {
        if (body.released) return null
        body.released = true
        body.owner = null
        body.sourcePlaylist = null
        val size = body.blocks.size.toLong() * BLOCK
        allocated -= size
        if (body.history) history -= size else loading -= size
        cached -= body.size
        val reused = if (recycle) body.blocks.firstOrNull() else null
        body.blocks.forEach { if (it !== reused) it.close() }
        body.blocks.clear()
        return reused
    }
    private fun publish() {
        val segments = entries.map { it.segment }
        val ranges = mutableListOf<LiveCacheRange>()
        var preceding: LiveCachedSegment? = null
        for (segment in segments) {
            val start = segment.media.startMs
            val end = segment.media.endMs
            if (segment.groupId == null || start == null || end == null) { preceding = null; continue }
            val previous = preceding
            if (previous != null && previous.continuity == segment.continuity && previous.sequence + 1 == segment.sequence &&
                kotlin.math.abs((segment.media.decodeStartMs ?: start) - (previous.media.decodeEndMs ?: previous.media.endMs!!)) <= 250) {
                ranges[ranges.lastIndex] = ranges.last().copy(endMs = maxOf(ranges.last().endMs, end))
            } else ranges += LiveCacheRange(start, end)
            preceding = segment
        }
        mutableSnapshot.value = LiveCacheSnapshot(
            mutableSnapshot.value.revision + 1,
            segments,
            ranges,
            allocated,
            capacity,
            ended || closed,
            epoch,
            cached,
            loading,
            loadingCapacity,
            history,
            if (retainHistory) capacity else 0,
        )
    }
    private companion object {
        const val BLOCK = 64 * 1024
        const val MEMORY_RESERVE = 64L * 1024 * 1024
    }
}
