@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.hsrui.bolo.player.base.BoloMpvStreamReader
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import kotlin.concurrent.atomics.AtomicBoolean

/** 每次本地定位只替换读取器；原生关闭 source 不关闭采集会话。 */
internal class LiveCachedStreamSource(
    private val cache: LivePlaybackCache,
    private val first: LiveCachedSegment,
    generation: Long,
    format: String,
    private val onMissing: (Long?) -> Unit,
) : BoloMpvStreamSource {
    val uri = "bolo-stream://live/$generation/$format"
    val startMs = first.media.startMs
    private val job = Job()
    private val opened = AtomicBoolean(false)
    override suspend fun open(uri: String): BoloMpvStreamReader? {
        if (uri != this.uri || !job.isActive || !opened.compareAndSet(false, true)) return null
        return object : BoloMpvStreamReader {
            override val job = Job(this@LiveCachedStreamSource.job)
            private var segment = first
            private var initialization = first.initializationId != null
            private var offset = 0
            override suspend fun read(bytes: ByteArray): Int {
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        job.ensureActive()
                        val size = cache.read(segment.id, initialization, offset, bytes)
                        if (size > 0) { offset += size; return size }
                        offset = 0
                        if (initialization) initialization = false
                        else segment = cache.next(segment) ?: return -1
                    }
                } catch (missing: LivePlaybackCache.Missing) {
                    if (job.isActive) onMissing(missing.targetMs)
                    throw missing
                }
            }
            override suspend fun seek(position: Long): Long = -1
            override suspend fun size(): Long = -1
            override suspend fun close() { job.cancel() }
        }
    }
    override fun cancel() { job.cancel() }
}
