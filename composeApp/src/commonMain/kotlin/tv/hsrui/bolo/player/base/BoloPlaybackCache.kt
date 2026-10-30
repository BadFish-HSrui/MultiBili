package tv.hsrui.bolo.player.base

import kotlin.time.TimeSource

/** 所有访问由下载会话的 mutex 串行化；分配中的块也计入上限。 */
internal class BoloPlaybackCache(
    val capacityBytes: Long,
    private val availableMemory: () -> Long? = ::playbackAvailableMemoryBytes,
    private val allocate: (Int) -> BoloPlaybackBuffer? = ::allocatePlaybackBuffer,
    private val nowMillis: () -> Long = TimeSource.Monotonic.markNow().let { origin ->
        { origin.elapsedNow().inWholeMilliseconds }
    },
) {
    private class Block(var track: BoloPlaybackTrack, var start: Long, val bytes: BoloPlaybackBuffer) {
        val ranges = mutableListOf<BoloPlaybackByteRange>()
        var reserved = false
        var accessed = 0L
    }

    private val blocks = mutableMapOf<Pair<BoloPlaybackTrack, Long>, Block>()
    private var clock = 0L
    private var nextMemoryCheck = 0L
    private var allocationRetryAt = 0L
    private var memoryBytes: Long? = null
    var memoryLimited = false
        private set
    var memoryBlocked = false
        private set
    val allocatedBytes: Long get() = blocks.size.toLong() * BlockSize
    val effectiveCapacityBytes: Long get() = if (memoryLimited) allocatedBytes else capacityBytes

    fun refreshMemory() {
        val now = nowMillis()
        if (memoryLimited && now < nextMemoryCheck) return
        memoryBytes = availableMemory()?.coerceAtLeast(0L)
        nextMemoryCheck = now + MemoryRetryMillis
        val growthThreshold = if (memoryLimited) ResumeBytes else ReserveBytes + BlockSize
        val limited = memoryBytes?.let { it < growthThreshold } ?: true
        if (limited != memoryLimited && (limited || now >= allocationRetryAt)) {
            memoryLimited = limited
        }
    }

    private fun limitMemory(allocationFailed: Boolean = false) {
        memoryLimited = true
        memoryBlocked = true
        nextMemoryCheck = nowMillis() + MemoryRetryMillis
        if (allocationFailed) allocationRetryAt = nextMemoryCheck
    }

    private fun backwardBytes(positions: Map<BoloPlaybackTrack, Long>): Long =
        blocks.values.count { it.start / BlockSize < positions.getValue(it.track) / BlockSize }.toLong() * BlockSize

    fun forwardTarget(positions: Map<BoloPlaybackTrack, Long>): Long =
        effectiveCapacityBytes.let { it - minOf(backwardBytes(positions), it / 4) }

    fun firstMissing(track: BoloPlaybackTrack, from: Long): Long {
        var position = from
        while (true) {
            val block = blocks[track to (position / BlockSize)] ?: return position
            val range = block.ranges.firstOrNull { position >= it.start && position < it.endExclusive } ?: return position
            position = range.endExclusive
        }
    }

    fun missingBytes(track: BoloPlaybackTrack, position: Long, length: Int): Int {
        val end = position + length
        var missing = length
        if (length == 0) return 0
        for (key in position / BlockSize..(end - 1) / BlockSize) {
            blocks[track to key]?.ranges?.forEach { range ->
                missing -= (minOf(end, range.endExclusive) - maxOf(position, range.start)).coerceAtLeast(0L).toInt()
            }
        }
        return missing
    }

    fun read(track: BoloPlaybackTrack, position: Long, buffer: ByteArray, offset: Int = 0): Int {
        val block = blocks[track to (position / BlockSize)] ?: return 0
        val range = block.ranges.firstOrNull { position >= it.start && position < it.endExclusive } ?: return 0
        val count = minOf((buffer.size - offset).toLong(), range.endExclusive - position).toInt()
        block.bytes.copyInto(buffer, offset, (position - block.start).toInt(), (position - block.start).toInt() + count)
        block.accessed = ++clock
        return count
    }

    fun reserve(
        track: BoloPlaybackTrack,
        start: Long,
        end: Long,
        positions: Map<BoloPlaybackTrack, Long>,
        targets: Map<BoloPlaybackTrack, Long>,
        readers: Map<BoloPlaybackTrack, List<Long>>,
        required: Boolean,
    ): Boolean {
        memoryBlocked = false
        val keys = (start / BlockSize..(end - 1) / BlockSize).map { track to it }
        val missing = keys.filter { it !in blocks }
        val needed = missing.size.toLong() * BlockSize
        val capacity = effectiveCapacityBytes
        val room = (capacityBytes - allocatedBytes).coerceAtLeast(0L)
        // 复用不消耗余量；未知余量时只允许必要读取逐块尝试。
        val memory = if (needed > 0 && (!memoryLimited || required)) {
            availableMemory()?.coerceAtLeast(0L)
        } else memoryBytes
        memoryBytes = memory
        val growthBytes = memory?.let { (it - ReserveBytes).coerceAtLeast(0L) }
            ?: if (required) BlockSize.toLong() else 0L
        val allowedGrowth = minOf(room, growthBytes) / BlockSize * BlockSize
        if (!memoryLimited && minOf(needed, room) > allowedGrowth) {
            limitMemory()
            return false // 先按实际容量重新计算窗口，再选择淘汰对象。
        }
        var backward = backwardBytes(positions)
        fun outsideWindow(block: Block): Boolean =
            block.start / BlockSize >= positions.getValue(block.track) / BlockSize + targets.getValue(block.track) / BlockSize
        val candidates = blocks.values.filter { block ->
            !block.reserved && block.start >= HeaderBytes && (block.track to (block.start / BlockSize)) !in keys &&
                readers[block.track].orEmpty().none { it >= block.start && it < block.start + BlockSize }
        }.sortedWith(compareBy<Block> { block ->
            // 旧位置的前向预取先回收，再回收最近读取位置之前的数据。
            if (outsideWindow(block)) 0 else if (block.start / BlockSize < positions.getValue(block.track) / BlockSize) 1 else 2
        }.thenBy { it.accessed })
        var available = if (memoryLimited) 0L else allowedGrowth
        val reused = mutableListOf<Block>()
        for (block in candidates) {
            if (available >= needed) break
            val behind = block.start / BlockSize < positions.getValue(block.track) / BlockSize
            // 纯预取不能互相淘汰有效窗口，也不能为扩大前向窗口削减已不足四分之一的后向缓存。
            if (!required && !outsideWindow(block) && (!behind || backward <= capacity / 4)) continue
            reused.add(block)
            available += BlockSize
            if (behind) backward -= BlockSize
        }
        if (memoryLimited && required && needed - available <= BlockSize && needed - available <= allowedGrowth) {
            available = needed
        }
        if (available < needed) {
            memoryBlocked = memoryLimited
            return false
        }
        val created = mutableListOf<Block>()
        repeat(missing.size - reused.size) {
            val bytes = allocate(BlockSize)
            if (bytes == null) {
                created.forEach { it.bytes.close() }
                limitMemory(allocationFailed = true)
                return false
            }
            created.add(Block(track, 0, bytes))
        }
        // 所有新增缓冲准备成功后才修改旧映射；淘汰块直接复用，不产生双份缓冲峰值。
        reused.forEach { blocks.remove(it.track to (it.start / BlockSize)) }
        missing.forEachIndexed { index, key ->
            val block = if (index < reused.size) reused[index] else created[index - reused.size]
            block.track = track
            block.start = key.second * BlockSize
            block.ranges.clear()
            blocks[key] = block
        }
        keys.forEach { blocks.getValue(it).reserved = true }
        return true
    }

    fun write(track: BoloPlaybackTrack, position: Long, bytes: ByteArray, offset: Int, length: Int) {
        var source = offset
        var cursor = position
        while (source < offset + length) {
            val block = checkNotNull(blocks[track to (cursor / BlockSize)])
            check(block.reserved)
            val at = (cursor - block.start).toInt()
            val count = minOf(BlockSize - at, offset + length - source)
            block.bytes.copyFrom(bytes, source, at, count)
            var first = cursor
            var last = cursor + count
            val iterator = block.ranges.iterator()
            while (iterator.hasNext()) {
                val range = iterator.next()
                if (range.start <= last && range.endExclusive >= first) {
                    first = minOf(first, range.start)
                    last = maxOf(last, range.endExclusive)
                    iterator.remove()
                }
            }
            block.ranges.add(BoloPlaybackByteRange(first, last))
            block.ranges.sortBy { it.start }
            block.accessed = ++clock
            source += count
            cursor += count
        }
    }

    fun finish(track: BoloPlaybackTrack) {
        val iterator = blocks.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next().value
            if (block.track == track) {
                block.reserved = false
                if (block.ranges.isEmpty()) {
                    iterator.remove()
                    block.bytes.close()
                }
            }
        }
    }

    fun close() {
        blocks.values.forEach { it.bytes.close() }
        blocks.clear()
    }

    fun ranges(track: BoloPlaybackTrack): List<BoloPlaybackByteRange> {
        val result = mutableListOf<BoloPlaybackByteRange>()
        blocks.values.filter { it.track == track }.flatMap { it.ranges }.sortedBy { it.start }.forEach { range ->
            val previous = result.lastOrNull()
            if (previous != null && previous.endExclusive == range.start) {
                result[result.lastIndex] = BoloPlaybackByteRange(previous.start, range.endExclusive)
            } else result.add(range)
        }
        return result
    }

    companion object {
        const val BlockSize = 64 * 1024
        const val MinimumBytes = 4L * 1024 * 1024
        const val HeaderBytes = 1024L * 1024
        const val ReserveBytes = 64L * 1024 * 1024
        const val ResumeBytes = 72L * 1024 * 1024
        const val MemoryRetryMillis = 500L
    }
}
