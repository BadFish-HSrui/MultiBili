package tv.hsrui.bolo.player.base

/** 所有访问由下载会话的 mutex 串行化；分配中的块也计入上限。 */
internal class BoloPlaybackCache {
    private class Block(val track: BoloPlaybackTrack, val start: Long) {
        val bytes = ByteArray(BlockSize)
        val ranges = mutableListOf<BoloPlaybackByteRange>()
        var reserved = false
        var accessed = 0L
    }

    private val blocks = mutableMapOf<Pair<BoloPlaybackTrack, Long>, Block>()
    private var clock = 0L
    val allocatedBytes: Long get() = blocks.size.toLong() * BlockSize

    fun firstMissing(track: BoloPlaybackTrack, from: Long): Long {
        var position = from
        while (true) {
            val block = blocks[track to (position / BlockSize)] ?: return position
            val range = block.ranges.firstOrNull { position >= it.start && position < it.endExclusive } ?: return position
            position = range.endExclusive
        }
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
    ): Boolean {
        val keys = (start / BlockSize..(end - 1) / BlockSize).map { track to it }
        val needed = keys.count { it !in blocks }.toLong() * BlockSize
        val forwardNeeded = keys.count { it !in blocks && (it.second + 1) * BlockSize > positions.getValue(track) }.toLong() * BlockSize
        var forwardAllocated = blocks.values.count { it.start + BlockSize > positions.getValue(it.track) }.toLong() * BlockSize
        val candidates = blocks.values.filter { block ->
            !block.reserved && block.start >= HeaderBytes && (block.track to (block.start / BlockSize)) !in keys &&
                readers[block.track].orEmpty().none { it >= block.start && it < block.start + BlockSize }
        }.sortedWith(compareBy<Block> { block ->
            val cursor = positions.getValue(block.track)
            val target = targets.getValue(block.track)
            // 旧位置的前向预取先回收，再回收最近读取位置之前的数据。
            if (block.start >= cursor + target) 0 else if (block.start + BlockSize <= cursor) 1 else 2
        }.thenBy { it.accessed })
        for (block in candidates) {
            if (allocatedBytes + needed <= Capacity && forwardAllocated + forwardNeeded <= ForwardBytes) break
            if (allocatedBytes + needed <= Capacity && block.start + BlockSize <= positions.getValue(block.track)) continue
            blocks.remove(block.track to (block.start / BlockSize))
            if (block.start + BlockSize > positions.getValue(block.track)) forwardAllocated -= BlockSize
        }
        if (allocatedBytes + needed > Capacity || forwardAllocated + forwardNeeded > ForwardBytes) return false
        for (key in keys) blocks.getOrPut(key) { Block(track, key.second * BlockSize) }.reserved = true
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
            bytes.copyInto(block.bytes, at, source, source + count)
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
                if (block.ranges.isEmpty()) iterator.remove()
            }
        }
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
        const val Capacity = 200L * 1024 * 1024
        const val ForwardBytes = 150L * 1024 * 1024
        const val MinimumBytes = 4L * 1024 * 1024
        const val HeaderBytes = 1024L * 1024
    }
}
