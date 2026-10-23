package tv.hsrui.bolo.livePlayer.session

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
)

/** 完整分片与在途正文共用块预算；读游标不持有内存，也不阻止淘汰。 */
internal class LivePlaybackCache(
    private val capacity: Long = 256L * 1024 * 1024,
    private val allocate: (Int) -> BoloPlaybackBuffer? = ::allocatePlaybackBuffer,
    private val availableMemory: () -> Long? = ::playbackAvailableMemoryBytes,
    private val mutex: Mutex = Mutex(),
) {
    internal class Missing(val targetMs: Long?) : Exception("直播缓存位置已不可用")
    internal class Body {
        val blocks = mutableListOf<BoloPlaybackBuffer>()
        var size = 0
        var released = false
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
    private var effectiveCapacity = capacity
    private var closed = false
    private var ended = false
    private val mutableSnapshot = MutableStateFlow(LiveCacheSnapshot(capacityBytes = capacity))
    val snapshot = mutableSnapshot.asStateFlow()

    suspend fun stage(): Body = mutex.withLock { check(!closed); Body().also(staging::add) }

    suspend fun write(body: Body, bytes: ByteArray, offset: Int, length: Int) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        check(!closed && body in staging && !body.released)
        var copied = 0
        try {
            while (copied < length) {
                if (body.size % BLOCK == 0) {
                    var recycled: BoloPlaybackBuffer? = null
                    try {
                        val memory = availableMemory()
                        if (memory != null && memory < MEMORY_RESERVE + BLOCK) {
                            effectiveCapacity = minOf(effectiveCapacity, maxOf(BLOCK.toLong(), allocated - MEMORY_RESERVE + memory))
                        }
                        while (allocated + BLOCK > effectiveCapacity && entries.isNotEmpty()) {
                            val released = evictGroup(recycle = recycled == null)
                            if (released != null) recycled = released
                        }
                        check(allocated + BLOCK <= effectiveCapacity) { "直播分片超过可用缓存容量" }
                        if (recycled == null) recycled = allocate(BLOCK)
                        if (recycled == null && entries.isNotEmpty()) {
                            recycled = evictGroup(recycle = true)
                            effectiveCapacity = minOf(effectiveCapacity, allocated + BLOCK)
                        }
                        checkNotNull(recycled) { "直播缓存内存不足" }
                    } catch (error: Throwable) {
                        recycled?.close()
                        throw error
                    }
                    val block = checkNotNull(recycled)
                    body.blocks += block
                    allocated += BLOCK
                }
                val amount = minOf(length - copied, BLOCK - body.size % BLOCK)
                body.blocks.last().copyFrom(bytes, offset + copied, body.size % BLOCK, amount)
                body.size += amount
                cached += amount
                copied += amount
            }
        } finally {
            publish()
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
        initializations[serial] = body
        pruneInitializations()
        publish()
        serial to changed
    }

    suspend fun append(body: Body, sequence: Long, continuity: Long, init: Long?, media: LiveHlsMediaIndexData) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        check(!closed && body in staging && body.size > 0 && (init == null || init in initializations))
        val previous = entries.lastOrNull()?.segment
        val id = ++serial
        val contiguous = media.dependenciesKnown && previous != null && previous.sequence + 1 == sequence && previous.continuity == continuity &&
            previous.initializationId == init && media.startMs != null && previous.media.endMs != null &&
            kotlin.math.abs(media.startMs - previous.media.endMs) <= 250
        val group = if (media.independent && media.startMs != null) id else previous?.groupId?.takeIf { contiguous }
        staging.remove(body)
        entries.addLast(Entry(LiveCachedSegment(id, sequence, continuity, init, media, group, body.size), body))
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
        if (targetMs == null) return starts.lastOrNull() ?: value.segments.firstOrNull()
        val range = value.ranges.firstOrNull { targetMs >= it.startMs && targetMs < it.endMs }
        return if (range != null) starts.lastOrNull { it.media.startMs!! <= targetMs && it.media.startMs >= range.startMs }
        else starts.firstOrNull { it.media.startMs!! >= targetMs } ?: starts.lastOrNull()
    }

    fun contains(position: Long?): Boolean = position != null && snapshot.value.ranges.any { position >= it.startMs && position < it.endMs }

    suspend fun awaitSegment(): LiveCachedSegment {
        snapshot.first { it.segments.isNotEmpty() || it.ended || closed }
        return select(null) ?: error("直播媒体为空")
    }

    suspend fun read(id: Long, init: Boolean, offset: Int, bytes: ByteArray): Int = mutex.withLock {
        check(!closed) { "直播缓存已关闭" }
        val entry = entries.firstOrNull { it.segment.id == id } ?: throw Missing(select(null)?.media?.startMs)
        val body = if (init) entry.segment.initializationId?.let(initializations::get) else entry.body
        body?.copy(offset, bytes, bytes.size) ?: 0
    }

    suspend fun next(segment: LiveCachedSegment): LiveCachedSegment? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val value = snapshot.value
            check(!closed) { "直播缓存已关闭" }
            val next = value.segments.firstOrNull { it.id > segment.id }
            if (next != null) {
                val continuous = next.sequence == segment.sequence + 1 && next.continuity == segment.continuity &&
                    next.initializationId == segment.initializationId &&
                    (next.media.startMs == null || segment.media.endMs == null || kotlin.math.abs(next.media.startMs - segment.media.endMs) <= 250)
                if (!continuous) throw Missing(value.segments.firstOrNull { it.id >= next.id && it.groupId == it.id }?.media?.startMs)
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
    private fun clearEntries() { epoch++; entries.forEach { free(it.body) }; entries.clear(); ended = false; pruneInitializations() }
    private fun pruneInitializations() {
        val used = entries.mapNotNull { it.segment.initializationId }.toSet() + listOfNotNull(initializationId)
        initializations.keys.filter { it !in used }.forEach { initializations.remove(it)?.let { free(it) } }
    }
    private fun free(body: Body, recycle: Boolean = false): BoloPlaybackBuffer? {
        if (body.released) return null
        body.released = true
        allocated -= body.blocks.size.toLong() * BLOCK
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
                kotlin.math.abs(start - ranges.last().endMs) <= 250) {
                ranges[ranges.lastIndex] = ranges.last().copy(endMs = end)
            } else ranges += LiveCacheRange(start, end)
            preceding = segment
        }
        mutableSnapshot.value = LiveCacheSnapshot(mutableSnapshot.value.revision + 1, segments, ranges,
            allocated, effectiveCapacity, ended || closed, epoch, cached)
    }
    private companion object {
        const val BLOCK = 64 * 1024
        const val MEMORY_RESERVE = 64L * 1024 * 1024
    }
}
