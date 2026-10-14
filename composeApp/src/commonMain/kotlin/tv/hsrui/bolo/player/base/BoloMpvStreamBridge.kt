@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.player.base

import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

/** 仅在 mpv 的流线程阻塞；cancel 不取锁、不等待 I/O，也不调用 mpv。 */
internal class BoloMpvStreamBridge {
    private class Entry(val session: BoloPlaybackStreamSession, val reader: BoloPlaybackStreamSession.Reader)
    private val session = AtomicReference<BoloPlaybackStreamSession?>(null)
    private val readers = AtomicReference<Map<Long, Entry>>(emptyMap())
    private val sequence = AtomicLong(1L)

    fun bind(value: BoloPlaybackStreamSession) {
        session.exchange(value)?.takeUnless { it === value }?.cancel()
    }

    fun open(uri: String): Long = try {
        val current = session.load()
        val track = when {
            current == null -> null
            uri == current.videoUri -> BoloPlaybackTrack.Video
            uri == current.audioUri -> BoloPlaybackTrack.Audio
            else -> null
        }
        if (current == null || track == null) 0L else {
            val reader = runBlocking { current.open(track) }
            if (reader == null) 0L else {
                val id = sequence.fetchAndAdd(1L)
                val entry = Entry(current, reader)
                while (true) {
                    val before = readers.load()
                    if (readers.compareAndSet(before, before + (id to entry))) break
                }
                id
            }
        }
    } catch (_: Throwable) { 0L }

    fun read(id: Long, bytes: ByteArray): Int = try {
        val entry = readers.load()[id]
        if (entry == null) -1 else runBlocking(entry.reader.job) { entry.session.read(entry.reader, bytes) }
    } catch (_: Throwable) { -1 }

    fun seek(id: Long, position: Long): Long = try {
        val entry = readers.load()[id]
        if (entry == null) -1L else runBlocking(entry.reader.job) { entry.session.seek(entry.reader, position) }
    } catch (_: Throwable) { -1L }

    fun size(id: Long): Long = try {
        val entry = readers.load()[id]
        if (entry == null) -1L else runBlocking(entry.reader.job) { entry.session.size(entry.reader) }
    } catch (_: Throwable) { -1L }

    fun cancel(id: Long) { readers.load()[id]?.reader?.job?.cancel() }

    fun close(id: Long) {
        while (true) {
            val before = readers.load()
            val entry = before[id] ?: return
            if (readers.compareAndSet(before, before - id)) {
                entry.reader.job.cancel()
                runBlocking { entry.session.close(entry.reader) }
                return
            }
        }
    }

    fun cancel() {
        session.load()?.cancel()
        readers.load().values.forEach { it.reader.job.cancel() }
    }

    /** 只能在 mpv 已停止使用回调后调用。 */
    fun release() {
        cancel()
        session.store(null)
        readers.store(emptyMap())
    }
}
