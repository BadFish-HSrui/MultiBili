@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import tv.hsrui.network.feature.player.PlayerStreamException
import tv.hsrui.network.feature.player.PlayerStreamResponseData
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.TimeSource

/** 只使用同一选中音视频流的 API 主备地址；验证通过前不会混合写入媒体范围。 */
internal class BoloPlaybackCandidates(urls: List<String>) {
    internal class Measurement(val speed: Double, val at: Long)

    internal class Candidate internal constructor(val url: String, private val now: () -> Long) {
        private val metadataValue = AtomicReference<PlayerStreamResponseData?>(null)
        private val rangeValue = AtomicInt(-1)
        internal var metadata: PlayerStreamResponseData?
            get() = metadataValue.load()
            set(value) { metadataValue.store(value) }
        internal var rangeSupported: Boolean?
            get() = rangeValue.load().let { if (it < 0) null else it == 1 }
            set(value) { rangeValue.store(when (value) { true -> 1; false -> 0; null -> -1 }) }
        internal var verified = false
        internal var rejected = false
        internal var failures = 0
        internal var emptyFailures = 0
        internal var blockedUntil = 0L
        internal val measurement = AtomicReference(Measurement(0.0, -MeasurementLifetime))
        internal var confirmedHeadBytes = 0

        val validator: String?
            get() = metadata?.let { it.etag?.takeUnless { etag -> etag.startsWith("W/") } ?: it.lastModified }
        val totalBytes: Long? get() = metadata?.totalBytes
        val supportsRange: Boolean get() = rangeSupported == true
        val bytesPerSecond: Double
            get() = measurement.load().let { if (now() - it.at < MeasurementLifetime) it.speed else 0.0 }
    }

    private class Sample(
        val candidate: Candidate,
        val response: PlayerStreamResponseData,
        val bytes: ByteArray,
        val count: Int,
    )

    private val clock = TimeSource.Monotonic.markNow()
    private val mutex = Mutex()
    private val preparing = Mutex()
    private val candidates = urls.filter { it.isNotBlank() }.distinct().map { Candidate(it, ::now) }
    private val canonicalValue = AtomicReference<Candidate?>(null)
    private var canonical: Candidate?
        get() = canonicalValue.load()
        set(value) { canonicalValue.store(value) }
    private var prepared = false
    private val totalValue = AtomicLong(-1)
    private var total: Long?
        get() = totalValue.load().takeIf { it >= 0 }
        set(value) { totalValue.store(value ?: -1) }
    val totalBytes: Long? get() = total
    val hasCanonical: Boolean get() = canonical != null

    private fun now() = clock.elapsedNow().inWholeMilliseconds

    suspend fun initial(): Candidate? = mutex.withLock {
        canonical ?: candidates.firstOrNull { !it.rejected && it.rangeSupported != false }
    }

    suspend fun initialCandidates(): List<Candidate> = mutex.withLock {
        candidates.filter { !it.rejected && it.rangeSupported != false }.take(3)
    }

    suspend fun completeInitial(candidate: Candidate, response: PlayerStreamResponseData) = mutex.withLock {
        require(candidate === canonical && response.statusCode == 206 && response.start == 0L)
        validateLocked(candidate, response)
        candidate.confirmedHeadBytes = minOf(ProbeBytes.toLong(), response.endExclusive ?: 0L).toInt()
        candidate.verified = candidate.confirmedHeadBytes == minOf(ProbeBytes.toLong(), total ?: ProbeBytes.toLong()).toInt()
    }

    suspend fun prepare(
        probe: suspend (Candidate, suspend (PlayerStreamResponseData, ByteArray, Int) -> Unit) -> Unit,
        onReady: suspend (PlayerStreamResponseData, ByteArray, Int) -> Unit = { _, _, _ -> },
    ) = preparing.withLock {
        if (mutex.withLock { prepared }) return@withLock
        val previousCanonical = mutex.withLock { canonical }
        if (mutex.withLock { previousCanonical?.verified == true && total == null }) {
            mutex.withLock { prepared = true }
            return@withLock
        }
        val pending = mutex.withLock {
            candidates.filter { !it.rejected && it.rangeSupported != false }
                .sortedBy { if (it === previousCanonical) 0 else 1 }
        }
        if (previousCanonical != null && previousCanonical !in pending) {
            throw PlayerStreamException("播放基准地址不可用", true)
        }
        val baseline = CompletableDeferred<Sample>()
        val compared = pending.map { CompletableDeferred<Unit>() }
        val permits = Semaphore(3)
        var canonicalFailure: Throwable? = null
        var baselineAccepted = false
        try { coroutineScope {
            pending.forEachIndexed { index, candidate ->
                launch {
                    try {
                        val stagger = if (previousCanonical?.verified == true) (index - 1).coerceAtLeast(0) else index
                        delay(when (stagger) { 0 -> 0L; 1 -> 120L; else -> 300L })
                        permits.withPermit {
                            var callbackFailure: Throwable? = null
                            try {
                                probe(candidate) { response, bytes, count ->
                                    requireSample(response, bytes, count)
                                    val first = mutex.withLock {
                                        validateLocked(candidate, response)
                                        candidate.confirmedHeadBytes = count
                                        if (!baseline.isCompleted && (previousCanonical == null || candidate === previousCanonical)) {
                                            canonical = candidate
                                            total = response.totalBytes
                                            candidate.verified = true
                                            baseline.complete(Sample(candidate, response, bytes, count))
                                            true
                                        } else false
                                    }
                                    if (first) {
                                        try {
                                            onReady(response, bytes, count)
                                            baselineAccepted = true
                                        } catch (error: Throwable) {
                                            callbackFailure = error
                                            throw error
                                        }
                                        compared[index].complete(Unit)
                                        // probe 的借用数组和预算许可保持到所有比较结束，不复制基准样本。
                                        compared.forEach { it.await() }
                                    } else {
                                        val sample = baseline.await()
                                        mutex.withLock {
                                            val matches = sample.response.totalBytes != null &&
                                                response.totalBytes == sample.response.totalBytes && count == sample.count &&
                                                (0 until count).all { bytes[it] == sample.bytes[it] }
                                            candidate.verified = matches
                                            if (!matches && sample.response.totalBytes != null) candidate.rejected = true
                                        }
                                        compared[index].complete(Unit)
                                    }
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                if (error === callbackFailure) throw error
                                if (error !== canonicalFailure) failure(candidate, error, 0)
                                if (candidate === previousCanonical && !baseline.isCompleted) {
                                    canonicalFailure = error
                                    baseline.completeExceptionally(error)
                                }
                            }
                        }
                    } finally {
                        compared[index].complete(Unit)
                    }
                }
            }
        } } finally {
            // 窗口交付后取消慢探测，保留已验证地址；未完成者不参与当前会话混拼。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    prepared = baselineAccepted
                    if (!baselineAccepted) candidates.forEach { it.verified = false }
                }
            }
        }
        canonicalFailure?.let { throw it }
        mutex.withLock { prepared = true }
    }

    suspend fun ranked(excluding: Set<String> = emptySet()): List<Candidate> = mutex.withLock {
        val pool = candidates.filter { it.verified && !it.rejected && it.rangeSupported == true && it.url !in excluding }
        val available = pool.filter { it.blockedUntil <= now() }.ifEmpty { pool }
        val best = available.maxOfOrNull { it.bytesPerSecond } ?: 0.0
        available.filter { it.bytesPerSecond == 0.0 || it.bytesPerSecond >= best / 12 }
            .sortedByDescending { it.bytesPerSecond }
    }

    suspend fun fallback(): Candidate? = mutex.withLock {
        val baseline = canonical
        if (baseline != null && total == null) {
            baseline.takeIf { it.rangeSupported == false && !it.rejected }
        } else candidates.firstOrNull { it.rangeSupported == false && !it.rejected }
    }

    suspend fun validate(candidate: Candidate, response: PlayerStreamResponseData) = mutex.withLock {
        require(candidate in candidates)
        validateLocked(candidate, response)
        if (canonical == null) canonical = candidate
        if (candidate === canonical && response.totalBytes != null) total = response.totalBytes
    }

    private fun validateLocked(candidate: Candidate, response: PlayerStreamResponseData) {
        if (candidate.rejected) throw PlayerStreamException("播放候选地址不可用", false)
        val previous = candidate.metadata
        val responseTotal = response.totalBytes
        val knownTotal = total
        val responseEnd = response.endExclusive
        val changed = (total != null && response.totalBytes != null && total != response.totalBytes) ||
            (knownTotal != null && response.statusCode == 206 && responseEnd != null && responseEnd > knownTotal) ||
            (responseTotal != null && responseTotal < (canonical?.confirmedHeadBytes ?: 0)) ||
            (previous?.etag != null && response.etag != null && previous.etag != response.etag) ||
            (previous?.lastModified != null && response.lastModified != null && previous.lastModified != response.lastModified)
        if (changed) {
            candidate.rejected = true
            candidate.verified = false
            throw PlayerStreamException("播放资源发生变化", false, resourceChanged = candidate === canonical)
        }
        if (response.statusCode == 200 && candidate.rangeSupported == true && candidate.validator != null) {
            candidate.rejected = true
            candidate.verified = false
            throw PlayerStreamException("播放资源发生变化", false, resourceChanged = candidate === canonical)
        }
        if (response.statusCode == 200) candidate.rangeSupported = false
        if (response.statusCode == 206) candidate.rangeSupported = true
        candidate.metadata = response.copy(
            etag = response.etag ?: previous?.etag,
            lastModified = response.lastModified ?: previous?.lastModified,
            totalBytes = response.totalBytes ?: previous?.totalBytes,
        )
    }

    suspend fun success(candidate: Candidate, bytes: Long, elapsedMs: Long) = mutex.withLock {
        require(candidate in candidates)
        candidate.failures = 0
        candidate.emptyFailures = 0
        candidate.blockedUntil = 0
        if (bytes >= SpeedSampleBytes && elapsedMs > 0) {
            val speed = bytes * 1_000.0 / elapsedMs
            val previous = candidate.measurement.load().speed
            candidate.measurement.store(Measurement(if (previous > 0) previous * 0.7 + speed * 0.3 else speed, now()))
        }
    }

    suspend fun failure(candidate: Candidate, error: Throwable, bytes: Long) = mutex.withLock {
        require(candidate in candidates)
        if (error is CancellationException) return@withLock
        val streamError = error as? PlayerStreamException
        if (streamError?.message == "媒体请求超过接收上限") return@withLock
        if (streamError?.resourceChanged == true ||
            (streamError?.statusCode == 200 && candidate === canonical && candidate.validator != null)) {
            candidate.rejected = true
            candidate.verified = false
            if (candidate === canonical) {
                throw PlayerStreamException("播放资源发生变化", false, error, resourceChanged = true)
            }
        }
        if (streamError?.statusCode == 200) {
            candidate.rangeSupported = false
            candidate.verified = false
            return@withLock
        }
        candidate.failures++
        candidate.blockedUntil = now() + 3_000L * (1L shl candidate.failures.coerceAtMost(4))
        if (bytes <= 0) {
            candidate.emptyFailures++
            val refused = streamError?.statusCode?.let { it in 400..499 } == true
            if (candidate.emptyFailures >= 2 && (!refused || canonical?.verified == true)) {
                candidate.rejected = true
                candidate.verified = false
            }
        }
    }

    private fun requireSample(response: PlayerStreamResponseData, bytes: ByteArray, count: Int) {
        val expected = minOf(ProbeBytes.toLong(), response.totalBytes ?: ProbeBytes.toLong()).toInt()
        if (response.statusCode != 206 || response.start != 0L || response.endExclusive != count.toLong() ||
            count <= 0 || count > bytes.size || count != expected) {
            throw PlayerStreamException("播放候选探测范围无效", false, statusCode = response.statusCode)
        }
    }

    private companion object {
        const val ProbeBytes = 64 * 1024
        const val SpeedSampleBytes = 48 * 1024
        const val MeasurementLifetime = 90_000L
    }
}
