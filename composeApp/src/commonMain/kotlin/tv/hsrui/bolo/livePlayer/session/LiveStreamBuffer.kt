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

/** 仅保留尚未交付的正文；块与读取游标独立于分片元数据。 */
internal class LiveStreamBuffer(private val capacity: Int = 16 * 1024 * 1024) {
    private class Block(val bytes: BoloPlaybackBuffer, val size: Int, var offset: Int = 0)
    private val mutex = Mutex()
    private val blocks = ArrayDeque<Block>()
    private val changed = MutableStateFlow(0L)
    private val mutableBytes = MutableStateFlow(0)
    val bufferedBytes = mutableBytes.asStateFlow()
    private var allocated = 0
    private var ended = false
    private var failure: Throwable? = null

    init { require(capacity > 0) }

    suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        var cursor = offset
        val end = offset + length
        while (cursor < end) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val count = mutex.withLock {
                check(!ended) { "直播缓冲已关闭" }
                failure?.let { throw it }
                val size = minOf(64 * 1024, end - cursor, capacity - allocated)
                if (size == 0) {
                    revision = changed.value
                    0
                } else {
                    val block = checkNotNull(allocatePlaybackBuffer(size)) { "直播播放缓冲内存不足" }
                    block.copyFrom(bytes, cursor, 0, size)
                    blocks.addLast(Block(block, size))
                    allocated += size
                    mutableBytes.value += size
                    changed.value++
                    size
                }
            }
            if (count == 0) changed.first { it != revision } else cursor += count
        }
    }

    suspend fun read(bytes: ByteArray): Int {
        require(bytes.isNotEmpty())
        while (true) {
            currentCoroutineContext().ensureActive()
            var revision = 0L
            val count = mutex.withLock {
                failure?.let { throw it }
                val block = blocks.firstOrNull()
                if (block == null) {
                    revision = changed.value
                    if (ended) 0 else null
                } else {
                    val size = minOf(bytes.size, block.size - block.offset)
                    block.bytes.copyInto(bytes, 0, block.offset, block.offset + size)
                    block.offset += size
                    mutableBytes.value -= size
                    if (block.offset == block.size) {
                        blocks.removeFirst().bytes.close()
                        allocated -= block.size
                    }
                    changed.value++
                    size
                }
            }
            if (count != null) return count
            changed.first { it != revision }
        }
    }

    suspend fun finish() = mutex.withLock {
        ended = true
        changed.value++
    }

    suspend fun close(error: Throwable? = null) = mutex.withLock {
        ended = true
        failure = error
        blocks.forEach { it.bytes.close() }
        blocks.clear()
        allocated = 0
        mutableBytes.value = 0
        changed.value++
    }
}
