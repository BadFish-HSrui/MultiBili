@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.player.base

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

internal interface BoloMpvStreamSource {
    suspend fun open(uri: String): BoloMpvStreamReader?
    fun cancel()
}

internal interface BoloMpvStreamReader {
    val job: Job
    suspend fun read(bytes: ByteArray): Int
    suspend fun seek(position: Long): Long
    suspend fun size(): Long
    suspend fun close()
}

/** 仅在 mpv 的流线程阻塞；cancel 不取锁、不等待 I/O，也不调用 mpv。 */
internal class BoloMpvStreamBridge {
    private val session = AtomicReference<BoloMpvStreamSource?>(null)
    private val readers = AtomicReference<Map<Long, BoloMpvStreamReader>>(emptyMap())
    private val sequence = AtomicLong(1L)

    fun bind(value: BoloMpvStreamSource) {
        session.exchange(value)?.takeUnless { it === value }?.cancel()
    }

    fun open(uri: String): Long = try {
        val current = session.load()
        if (current == null) 0L else {
            val reader = runBlocking { current.open(uri) }
            if (reader == null) 0L else {
                val id = sequence.fetchAndAdd(1L)
                while (true) {
                    val before = readers.load()
                    if (readers.compareAndSet(before, before + (id to reader))) break
                }
                id
            }
        }
    } catch (_: Throwable) { 0L }

    fun read(id: Long, bytes: ByteArray): Int = try {
        val entry = readers.load()[id]
        if (entry == null) -1 else runBlocking(entry.job) { entry.read(bytes) }
    } catch (_: Throwable) { -1 }

    fun seek(id: Long, position: Long): Long = try {
        val entry = readers.load()[id]
        if (entry == null) -1L else runBlocking(entry.job) { entry.seek(position) }
    } catch (_: Throwable) { -1L }

    fun size(id: Long): Long = try {
        val entry = readers.load()[id]
        if (entry == null) -1L else runBlocking(entry.job) { entry.size() }
    } catch (_: Throwable) { -1L }

    fun cancel(id: Long) { readers.load()[id]?.job?.cancel() }

    fun close(id: Long) {
        while (true) {
            val before = readers.load()
            val entry = before[id] ?: return
            if (readers.compareAndSet(before, before - id)) {
                entry.job.cancel()
                runBlocking { entry.close() }
                return
            }
        }
    }

    fun cancel() {
        session.load()?.cancel()
        readers.load().values.forEach { it.job.cancel() }
    }

    /** 只能在 mpv 已停止使用回调后调用。 */
    fun release() {
        cancel()
        session.store(null)
        readers.store(emptyMap())
    }
}
