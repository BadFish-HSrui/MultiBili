@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.feature.player.PlayerStreamException
import tv.hsrui.network.feature.player.PlayerStreamResponseData
import tv.hsrui.network.feature.player.fetchPlayerStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.math.ceil
import kotlin.time.TimeSource

internal data class BoloPlaybackDownloadDemand(
    val generation: Long = 0,
    val seekRevision: Long = 0,
    val positionMs: Long = 0,
    val pendingSeekMs: Long? = null,
    val playbackSpeed: Double = 1.0,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val bufferedAheadMs: Long = -1,
)

internal enum class BoloPlaybackRequestPriority { Metadata, Startup, Playback, Prefetch }

/** 同一播放会话的音视频、索引和抢救请求共用许可与暂存预算。 */
internal class BoloPlaybackDownloader {
    private class Waiter(val bytes: Int, val request: Boolean, val primary: Boolean, val priority: () -> Int, val since: Long)
    private class Attempt(val candidate: BoloPlaybackCandidates.Candidate, var bytes: ByteArray) {
        val received = AtomicInt(0)
        val lastReceivedMs = AtomicLong(0L)
        val startedMs = AtomicLong(-1L)
    }
    private class Completion(val error: Throwable?)

    private val mutex = Mutex()
    // 两条轨道各保留一个外层窗口，避免等待传输许可的前缀占满全部暂存。
    private val operations = Semaphore(2)
    private val changed = MutableStateFlow(0L)
    private val demand = MutableStateFlow(BoloPlaybackDownloadDemand())
    private val clock = TimeSource.Monotonic.markNow()
    private val waiters = mutableListOf<Waiter>()
    private val levels = intArrayOf(8, 12, 16, 24, 32, 48, 64)
    private val restingUntil = LongArray(levels.size)
    private val delivered = mutableMapOf<Long, Long>()
    private val assigned = mutableMapOf<String, Int>()
    private var active = 0
    private var primaryActive = 0
    private var staging = 0
    private var rescues = 0
    private var baseLevel = 0
    private var startupLevel = 2
    private var startupUntil = 30_000L
    private var lastChange = -2_500L
    private var lastActivity = -2_000L
    private var pressureSince = 0L
    private var previousAhead = -1L
    private var previousSample = 0L
    private var trialStarted = 0L
    private var trialBase = 0
    private var trialRate = 0.0
    private var trialStalled = false
    private var busyMillis = 0L
    private var sampledMillis = 0L
    private var sampleAt = 0L
    private var lastSeekRevision = 0L
    private var lastGeneration = 0L

    fun updateDemand(value: BoloPlaybackDownloadDemand) {
        demand.value = value
        changed.update { it + 1 }
    }

    private fun now() = clock.elapsedNow().inWholeMilliseconds

    private fun target(): Int {
        val time = now()
        var level = if (time < startupUntil) maxOf(baseLevel, startupLevel) else baseLevel
        while (level > 0 && restingUntil[level] > time) level--
        return levels[level]
    }

    private fun sample() {
        val time = now()
        val elapsed = time - sampleAt
        if (elapsed > 0) {
            sampledMillis += elapsed
            if (active >= target() * 0.6) busyMillis += elapsed
            sampleAt = time
        }
        delivered.keys.removeAll { it < time / 250 - 20 }
        val state = demand.value
        if (state.generation != lastGeneration || state.seekRevision != lastSeekRevision) {
            lastGeneration = state.generation
            lastSeekRevision = state.seekRevision
            startupLevel = maxOf(baseLevel, 2)
            startupUntil = time + 30_000
            pressureSince = 0
            previousAhead = -1
        }
        if (time - lastChange < 2_500) return
        if (time < startupUntil && state.bufferedAheadMs >= 15_000 && startupLevel > baseLevel) {
            startupLevel--
            lastChange = time
        }
        if (trialStarted > 0 && time - trialStarted >= 10_000) {
            val rate = delivered.values.sum() / 5.0
            if (!trialStalled && sampledMillis > 0 && busyMillis.toDouble() / sampledMillis >= 0.6 &&
                trialRate > 0 && rate < trialRate) {
                restingUntil[baseLevel] = time + 90_000
                baseLevel = trialBase
                startupLevel = minOf(startupLevel, baseLevel)
                lastChange = time
            }
            trialStarted = 0
        }
        val stalled = state.playing && state.buffering
        if (stalled) trialStalled = true
        if (time - previousSample >= 1_000) {
            val pressure = state.playing && state.bufferedAheadMs in 0..5_999 &&
                time - lastActivity < 1_500 && previousAhead >= 0 && state.bufferedAheadMs <= previousAhead + 50
            if (pressure) { if (pressureSince == 0L) pressureSince = time } else pressureSince = 0
            previousAhead = state.bufferedAheadMs
            previousSample = time
        }
        if ((stalled || (pressureSince > 0 && time - pressureSince >= 1_000)) && baseLevel < levels.lastIndex) {
            val next = baseLevel + 1
            if (restingUntil[next] <= time) {
                trialBase = baseLevel
                trialRate = delivered.values.sum() / 5.0
                baseLevel = next
                trialStarted = time
                trialStalled = stalled
                busyMillis = 0
                sampledMillis = 0
                lastChange = time
                pressureSince = 0
            }
        }
    }

    private suspend fun feedback(error: Throwable) = mutex.withLock {
        if (error is PlayerStreamException && error.statusCode in listOf(412, 429)) {
            val rejected = levels.indexOf(target()).coerceAtLeast(1)
            for (index in rejected..levels.lastIndex) restingUntil[index] = now() + 180_000
            baseLevel = minOf(baseLevel, rejected - 1)
            startupLevel = minOf(startupLevel, rejected - 1)
            trialStarted = 0
            lastChange = now()
            changed.update { it + 1 }
        } else if (error is PlayerStreamException && error.message == "播放连接超时" &&
            active >= target() * 0.6 && now() - lastChange >= 2_500 && baseLevel < levels.lastIndex &&
            restingUntil[baseLevel + 1] <= now()) {
            trialBase = baseLevel
            trialRate = delivered.values.sum() / 5.0
            baseLevel++
            trialStarted = now()
            trialStalled = false
            busyMillis = 0
            sampledMillis = 0
            lastChange = now()
            changed.update { it + 1 }
        }
    }

    private suspend fun acquire(bytes: Int, request: Boolean, priority: () -> Int, primary: Boolean = false): Waiter {
        require(bytes in 0..StagingLimit)
        val waiter = Waiter(bytes, request, primary, priority, now())
        mutex.withLock { waiters.add(waiter) }
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                var version = 0L
                val granted = mutex.withLock {
                    sample()
                    val eligible = waiters.filter { staging + it.bytes <= StagingLimit && (!it.request || active < target()) &&
                        (!it.primary || primaryActive < target() - ceil(target() / 8.0).toInt()) }
                    val best = eligible.minByOrNull { it.priority() - ((now() - it.since) / 900).coerceAtMost(20).toInt() }
                    if (best === waiter) {
                        waiters.remove(waiter)
                        staging += bytes
                        if (request) active++
                        if (primary) primaryActive++
                        changed.update { it + 1 }
                        true
                    } else { version = changed.value; false }
                }
                if (granted) return waiter
                withTimeoutOrNull(100) { changed.first { it != version } }
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) { mutex.withLock { waiters.remove(waiter); changed.update { it + 1 } } }
            throw error
        }
    }

    private suspend fun release(lease: Waiter) = withContext(NonCancellable) {
        mutex.withLock {
            sample()
            staging -= lease.bytes
            if (lease.request) active--
            if (lease.primary) primaryActive--
            check(staging >= 0 && active >= 0)
            changed.update { it + 1 }
        }
    }

    private suspend fun received(count: Int, callback: suspend (Int) -> Unit) {
        mutex.withLock { lastActivity = now() }
        callback(count)
    }

    private suspend fun accepted(count: Int) = withContext(NonCancellable) { mutex.withLock {
        val slot = now() / 250
        delivered[slot] = (delivered[slot] ?: 0) + count
    } }

    suspend fun download(
        candidates: BoloPlaybackCandidates,
        start: Long,
        endExclusive: Long,
        priority: BoloPlaybackRequestPriority,
        deadlineMs: (Long) -> Long? = { null },
        urgencyStart: () -> Long? = { null },
        readHead: suspend (ByteArray) -> Int = { 0 },
        missingBytes: suspend (Long, Int) -> Int = { _, count -> count },
        continueFullResponse: Boolean = true,
        receiveLimit: Long? = null,
        onResponse: suspend (PlayerStreamResponseData) -> Unit,
        onReceived: suspend (Int) -> Unit,
        write: suspend (Long, ByteArray, Int, Int) -> Unit,
    ): PlayerStreamResponseData = operations.withPermit { coroutineScope {
        require(start >= 0 && endExclusive > start && endExclusive - start <= 4L * 1024 * 1024)
        val responseLock = Mutex()
        var response: PlayerStreamResponseData? = null
        var headEnd = start
        val bodyBytes = kotlinx.coroutines.sync.Mutex()
        var receivedBytes = 0L
        val counted: suspend (Int) -> Unit = { count ->
            withContext(NonCancellable) { bodyBytes.withLock {
                receivedBytes += count
                received(count, onReceived)
                if (receiveLimit != null && receivedBytes > receiveLimit) throw PlayerStreamException("媒体请求超过接收上限", false)
            } }
        }
        var initialEnd = start
        if (start == 0L && priority == BoloPlaybackRequestPriority.Startup && !candidates.hasCanonical) {
            var initialResponse: PlayerStreamResponseData? = null
            coroutineScope {
                val selected = CompletableDeferred<BoloPlaybackCandidates.Candidate?>()
                var winner: BoloPlaybackCandidates.Candidate? = null
                val attempts = candidates.initialCandidates().mapIndexed { index, candidate ->
                    candidate to launch {
                        delay(when (index) { 0 -> 0L; 1 -> 120L; else -> 300L })
                        if (selected.isCompleted) return@launch
                        val lease = acquire(BlockSize, true, { priority.ordinal * 10 })
                        val began = now()
                        var metadata: PlayerStreamResponseData? = null
                        var received = 0L
                        try {
                            // 首个有效正文确定唯一写入者，备用无需等待首地址超时后重新探测。
                            val result = fetchPlaybackRange(candidate.url, start, minOf(endExclusive, BlockSize.toLong()), candidate.validator,
                                headerTimeoutMillis = 1_500, readTimeoutMillis = 1_000, requestTimeoutMillis = 2_500,
                                onResponse = { metadata = it },
                                onReceived = { count -> received += count; counted(count) },
                                write = { offset, bytes, at, count ->
                                    responseLock.withLock {
                                        currentCoroutineContext().ensureActive()
                                        if (winner == null) {
                                            val value = checkNotNull(metadata)
                                            candidates.validate(candidate, value)
                                            onResponse(value)
                                            winner = candidate
                                            selected.complete(candidate)
                                        }
                                        if (winner !== candidate) throw CancellationException("起播备用请求已取消")
                                    }
                                    currentCoroutineContext().ensureActive()
                                    val delivered = missingBytes(offset, count)
                                    write(offset, bytes, at, count)
                                    accepted(delivered)
                                    initialEnd = offset + count
                                })
                            candidates.completeInitial(candidate, result)
                            candidates.success(candidate, initialEnd, now() - began)
                            initialResponse = result
                        } catch (error: CancellationException) { throw error }
                        catch (error: Throwable) {
                            if (error is BoloPlaybackMemoryException) throw error
                            candidates.failure(candidate, error, received)
                            feedback(error)
                            if ((error as? PlayerStreamException)?.let {
                                    it.resourceChanged || it.message == "媒体请求超过接收上限"
                                } == true) throw error
                        } finally { release(lease) }
                    }
                }
                launch { attempts.forEach { it.second.join() }; selected.complete(null) }
                try {
                    val chosen = selected.await()
                    attempts.forEach { (candidate, job) -> if (candidate !== chosen) job.cancel() }
                    attempts.forEach { it.second.join() }
                } finally {
                    withContext(NonCancellable) {
                        attempts.forEach { it.second.cancel() }
                        attempts.forEach { it.second.join() }
                    }
                }
            }
            headEnd = initialEnd
            initialResponse?.let { metadata ->
                if (initialEnd >= minOf(endExclusive, metadata.totalBytes ?: endExclusive)) return@coroutineScope metadata
            }
        }
        val hadCanonical = candidates.hasCanonical
        val cachedCanonical = candidates.initial()?.takeIf { it.verified }
        val ready = CompletableDeferred<Unit>()
        val preparation = launch {
            try {
                candidates.prepare(probe = { candidate, accept ->
                    val lease = acquire(2 * BlockSize, true, { 0 })
                    try {
                        val bytes = ByteArray(BlockSize)
                        var length = 0
                        val began = now()
                        val cached = if (candidate === cachedCanonical) readHead(bytes) else 0
                        val expected = minOf(BlockSize.toLong(), candidate.totalBytes ?: BlockSize.toLong()).toInt()
                        val metadata = if (cached == expected && cached > 0) {
                            length = cached
                            checkNotNull(candidate.metadata).copy(start = 0, endExclusive = cached.toLong())
                        } else {
                            fetchPlaybackProbe(candidate.url, candidate.validator, counted) { offset, source, at, size ->
                                source.copyInto(bytes, offset.toInt(), at, at + size)
                                length = maxOf(length, offset.toInt() + size)
                            }.also { candidates.success(candidate, length.toLong(), now() - began) }
                        }
                        accept(metadata, bytes, length)
                    } catch (error: CancellationException) { throw error }
                    catch (error: Throwable) {
                        if (error !is BoloPlaybackMemoryException) feedback(error)
                        throw error
                    }
                    finally { release(lease) }
                }, onReady = { metadata, bytes, length ->
                    if (hadCanonical) {
                        val sampleLease = acquire(BlockSize, false, { 0 })
                        try {
                            val previous = ByteArray(BlockSize)
                            val count = readHead(previous)
                            if (count > 0 && (count > length || (0 until count).any { previous[it] != bytes[it] })) {
                                throw PlayerStreamException("播放资源样本发生变化", false, resourceChanged = true)
                            }
                        } finally { release(sampleLease) }
                    }
                    responseLock.withLock {
                        response = metadata
                        onResponse(metadata)
                        if (start < length) {
                            val end = minOf(endExclusive, length.toLong())
                            val from = maxOf(start, initialEnd)
                            if (from < end) {
                                val delivered = missingBytes(from, (end - from).toInt())
                                write(from, bytes, from.toInt(), (end - from).toInt())
                                accepted(delivered)
                            }
                            headEnd = maxOf(end, initialEnd)
                        }
                        ready.complete(Unit)
                    }
                })
                ready.complete(Unit)
            } catch (error: Throwable) {
                ready.completeExceptionally(error)
                throw error
            }
        }
        ready.await()
        val available = candidates.ranked()
        var rangeFailure: Throwable? = null
        try {
            if (available.isNotEmpty()) {
                val total = candidates.totalBytes
                val end = minOf(endExclusive, total ?: endExclusive)
                if (response == null) {
                    response = PlayerStreamResponseData(206, start, end, total, null, null)
                    onResponse(response!!)
                }
                if (headEnd < end) {
                    val concurrency = mutex.withLock { sample(); target() }
                    val speed = available.maxOfOrNull { it.bytesPerSecond } ?: 0.0
                    val adaptive = if (speed <= 0) BlockSize else
                        ((speed * 0.6 / BlockSize).toInt().coerceIn(1, 16) * BlockSize)
                    val wanted = ceil((end - headEnd).toDouble() / adaptive).toInt().coerceAtLeast(1)
                    val pieces = maxOf(wanted, minOf(concurrency, ceil((end - headEnd).toDouble() / BlockSize).toInt()))
                    val size = ceil((end - headEnd).toDouble() / pieces).toInt().coerceAtMost(1024 * 1024)
                    coroutineScope {
                        var cursor = headEnd
                        val jobs = mutableListOf<Job>()
                        while (cursor < end) {
                            val pieceStart = cursor
                            val pieceEnd = minOf(end, cursor + size)
                            jobs += launch {
                                fetchPiece(candidates, pieceStart, pieceEnd, priority, deadlineMs, urgencyStart, missingBytes, counted, write)
                            }
                            cursor = pieceEnd
                        }
                        jobs.forEach { it.join() }
                    }
                }
                withContext(NonCancellable) { preparation.cancel(); preparation.join() }
                return@coroutineScope response!!.copy(start = start, endExclusive = minOf(end, candidates.totalBytes ?: end), totalBytes = candidates.totalBytes)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (error is BoloPlaybackMemoryException) throw error
            if ((error as? PlayerStreamException)?.resourceChanged == true) throw error
            rangeFailure = error
        }
        preparation.join()
        if (candidates.ranked().isNotEmpty()) throw rangeFailure ?: PlayerStreamException("播放候选地址已耗尽", false)
        val fallback = candidates.fallback() ?: throw rangeFailure ?: PlayerStreamException("播放候选地址已耗尽", false)
        if (!continueFullResponse) throw PlayerStreamException("索引请求不启动完整媒体响应", false)
        // 分块作用域已结束，200 只保留一条连续响应；已有缓存需先验证响应头部。
        val lease = acquire(2 * BlockSize, true, { priority.ordinal * 10 })
        try {
            val reference = ByteArray(BlockSize)
            val compareCount = if (candidates.hasCanonical) readHead(reference) else 0
            val expectedTotal = candidates.totalBytes
            val expected = minOf(BlockSize.toLong(), expectedTotal ?: BlockSize.toLong()).toInt()
            if (candidates.hasCanonical && compareCount != expected) {
                throw PlayerStreamException("播放源切换缺少资源样本", false, resourceChanged = true)
            }
            var checked = 0
            var published = false
            var metadata: PlayerStreamResponseData? = null
            val result = fetchPlaybackRange(fallback.url, 0, BlockSize.toLong(), fallback.validator,
                strictRange = false, continueFullResponse = true, receiveLimit = receiveLimit,
                onResponse = { value ->
                    if (value.statusCode != 200) throw PlayerStreamException("播放连续响应状态发生变化", false)
                    if (expectedTotal != null && value.totalBytes != expectedTotal) {
                        throw PlayerStreamException("播放连续响应长度无法确认", false, resourceChanged = true)
                    }
                    candidates.validate(fallback, value)
                    metadata = value
                    if (compareCount == 0) { onResponse(value); published = true }
                }, onReceived = counted, write = { offset, bytes, at, count ->
                    if (!published) {
                        val compare = minOf(count, compareCount - checked)
                        if ((0 until compare).any { reference[checked + it] != bytes[at + it] }) {
                            throw PlayerStreamException("播放源资源样本不一致", false, resourceChanged = true)
                        }
                        checked += compare
                        if (checked == compareCount) { onResponse(metadata!!); published = true }
                    }
                    if (published) {
                        val skip = (start - offset).coerceIn(0L, count.toLong()).toInt()
                        if (skip < count) write(offset + skip, bytes, at + skip, count - skip)
                    }
                })
            if (!published || checked < compareCount) {
                throw PlayerStreamException("播放源资源样本不完整", false, resourceChanged = true)
            }
            candidates.validate(fallback, result)
            result
        } finally { release(lease) }
    } }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun fetchPiece(
        candidates: BoloPlaybackCandidates, start: Long, end: Long,
        priority: BoloPlaybackRequestPriority, deadlineMs: (Long) -> Long?, urgencyStart: () -> Long?,
        missingBytes: suspend (Long, Int) -> Int,
        onReceived: suspend (Int) -> Unit, write: suspend (Long, ByteArray, Int, Int) -> Unit,
    ) {
        val size = (end - start).toInt()
        val score = {
            val urgent = urgencyStart()?.let { it in start until end } == true
            val deadline = deadlineMs(start)
            priority.ordinal * 10 - (if (urgent) 30 else 0) - when {
                deadline == null -> 0
                deadline <= 0 -> 12
                deadline <= 750 -> 9
                deadline <= 2_000 -> 6
                else -> 0
            }
        }
        val storage = acquire(size, false, score)
        val assignments = mutableListOf<String>()
        try {
            val prefix = ByteArray(size)
            var retained = 0
            var lastError: Throwable = PlayerStreamException("播放分块下载失败", false)
            val began = now()
            for (round in 0..2) {
                if (round > 0) {
                    if (now() - began >= 25_000) break
                    delay(round * 500L)
                }
                val ranked = candidates.ranked().take(8)
                if (ranked.isEmpty()) break
                val choices = mutex.withLock {
                    ranked.sortedByDescending { (it.bytesPerSecond.takeIf { speed -> speed > 0 } ?: 1_000_000.0) /
                        (1 + (assigned[it.url] ?: 0)) }.also {
                        val url = it.first().url
                        assigned[url] = (assigned[url] ?: 0) + 1
                        assignments.add(url)
                    }
                }
                var next = 0
                while (next < choices.size) {
                    val primary = choices[next++]
                    val backup = choices.getOrNull(next)
                    val won = AtomicBoolean(false)
                    val completed = supervisorScope {
                        val events = Channel<Completion>(Channel.UNLIMITED)
                        val attempts = mutableListOf<Job>()
                        val first = Attempt(primary, prefix)
                        first.received.store(retained)
                        var rescue: Attempt? = null
                        var launchedRescue = false
                        var finished = 0
                        fun launchAttempt(attempt: Attempt, prefixLength: Int, isRescue: Boolean) = launch {
                            var lease: Waiter? = null
                            try {
                                lease = acquire(BlockSize + if (isRescue) size else 0, true,
                                    { score() - if (isRescue) 20 else 0 }, primary = !isRescue)
                                if (isRescue) {
                                    attempt.bytes = ByteArray(size)
                                    // prefixLength 仅包含已写完的连续前缀，后续写入不会覆盖它。
                                    first.bytes.copyInto(attempt.bytes, 0, 0, prefixLength)
                                    attempt.received.store(prefixLength)
                                }
                                val beganAttempt = now()
                                attempt.lastReceivedMs.store(beganAttempt)
                                attempt.startedMs.store(beganAttempt)
                                val metadata = fetchPlaybackPiece(attempt.candidate.url, start + prefixLength, end,
                                    attempt.candidate.validator, onReceived = onReceived, write = { offset, bytes, at, count ->
                                        val targetOffset = (offset - start).toInt()
                                        bytes.copyInto(attempt.bytes, targetOffset, at, at + count)
                                        attempt.received.store(targetOffset + count)
                                        attempt.lastReceivedMs.store(now())
                                    }, onResponse = { candidates.validate(attempt.candidate, it) })
                                candidates.validate(attempt.candidate, metadata)
                                val completeSize = if (metadata.statusCode == 416) 0 else
                                    ((metadata.endExclusive ?: end) - start).toInt()
                                if (completeSize !in 0..size || (completeSize > 0 && attempt.received.load() != completeSize)) {
                                    throw PlayerStreamException("播放分块未完整接收", true)
                                }
                                candidates.success(attempt.candidate, (completeSize - prefixLength).coerceAtLeast(0).toLong(), now() - beganAttempt)
                                if (won.compareAndSet(false, true)) {
                                    currentCoroutineContext().ensureActive()
                                    val delivered = missingBytes(start, completeSize)
                                    if (completeSize > 0) write(start, attempt.bytes, 0, completeSize)
                                    accepted(delivered)
                                    events.send(Completion(null))
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                val failure = if (error is BoloPlaybackMemoryException) error else {
                                    val sourceFailure = try {
                                        candidates.failure(attempt.candidate, error, (attempt.received.load() - prefixLength).toLong())
                                        error
                                    } catch (changed: Throwable) { changed }
                                    feedback(sourceFailure)
                                    sourceFailure
                                }
                                events.send(Completion(failure))
                            } finally {
                                if (isRescue) attempt.bytes = ByteArray(0)
                                if (lease != null) release(lease)
                            }
                        }
                        try {
                            attempts += launchAttempt(first, retained, false)
                            val deadline = deadlineMs(start)
                            val startup = priority == BoloPlaybackRequestPriority.Startup || demand.value.buffering
                            val rate = primary.bytesPerSecond
                            val delayMs = if (startup) { if (deadline == null) 250L else 200L }
                                else if (rate > 0) (size / rate * 1_500).toLong().coerceIn(250, 900) else 900L
                            while (true) {
                                val event = select<Completion?> {
                                    events.onReceive { it }
                                    onTimeout(50) { null }
                                }
                                if (event != null) {
                                    if (event.error == null) return@supervisorScope true
                                    lastError = event.error
                                    if (lastError is BoloPlaybackMemoryException || won.load() || (lastError as? PlayerStreamException)?.let {
                                        it.resourceChanged || it.message == "媒体请求超过接收上限"
                                    } == true) throw lastError
                                    finished++
                                }
                                val count = first.received.load()
                                val runningMs = now() - first.startedMs.load()
                                val currentRate = if (runningMs > 0) (count - retained) * 1_000.0 / runningMs else 0.0
                                val remaining = deadlineMs(start)
                                val eta = if (currentRate > 0) (size - count) / currentRate * 1_000 else Double.POSITIVE_INFINITY
                                val safelyAhead = remaining != null && eta <= remaining - 1_500
                                val due = first.startedMs.load() >= 0 && runningMs >= delayMs
                                val needRescue = finished > 0 || (due && !safelyAhead &&
                                    (remaining == null || count == retained || now() - first.lastReceivedMs.load() >= delayMs ||
                                        currentRate < rate * 0.6 || backup?.bytesPerSecond == 0.0 ||
                                        (backup?.bytesPerSecond ?: 0.0) > currentRate * 1.5 || eta > remaining))
                                if (!launchedRescue && backup != null && needRescue && !won.load()) {
                                    val allowed = mutex.withLock {
                                        if (rescues < ceil(target() / 8.0).toInt().coerceAtMost(8) &&
                                            staging + size + BlockSize <= StagingLimit) { rescues++; true } else false
                                    }
                                    if (allowed) {
                                        launchedRescue = true
                                        next++
                                        val prefixLength = count.takeIf { it >= 32 * 1024 && it < size } ?: 0
                                        rescue = Attempt(backup, ByteArray(0))
                                        attempts += launchAttempt(rescue, prefixLength, true)
                                    }
                                }
                                if (finished >= if (launchedRescue) 2 else 1) break
                            }
                            false
                        } finally {
                            withContext(NonCancellable) { attempts.forEach { it.cancel() }; attempts.forEach { it.join() } }
                            if (launchedRescue) withContext(NonCancellable) { mutex.withLock { rescues-- } }
                            if (!won.load()) retained = first.received.load().takeIf { it in 32 * 1024 until size } ?: 0
                            events.close()
                        }
                    }
                    if (completed) return
                }
            }
            throw PlayerStreamException("播放候选地址已耗尽", false, lastError)
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                assignments.forEach { url -> assigned[url] = ((assigned[url] ?: 1) - 1).coerceAtLeast(0) }
            } }
            release(storage)
        }
    }

    companion object {
        private const val BlockSize = 64 * 1024
        const val StagingLimit = 16 * 1024 * 1024
    }
}

internal suspend fun fetchPlaybackRange(
    url: String, start: Long, endExclusive: Long, validator: String?,
    strictRange: Boolean = true, continueFullResponse: Boolean = false, receiveLimit: Long? = null,
    headerTimeoutMillis: Long = if (strictRange) 5_500 else 15_000,
    readTimeoutMillis: Long = if (strictRange) 4_000 else 30_000,
    requestTimeoutMillis: Long? = if (strictRange) 15_000 else null,
    onResponse: suspend (PlayerStreamResponseData) -> Unit,
    onReceived: suspend (Int) -> Unit,
    write: suspend (Long, ByteArray, Int, Int) -> Unit,
): PlayerStreamResponseData = fetchPlayerStream(url, start, endExclusive, validator,
    continueFullResponse, receiveLimit, onResponse, onReceived, write,
    strictRange = strictRange, headerTimeoutMillis = headerTimeoutMillis,
    readTimeoutMillis = readTimeoutMillis, requestTimeoutMillis = requestTimeoutMillis)

internal suspend fun fetchPlaybackPiece(
    url: String, start: Long, endExclusive: Long, validator: String?,
    onResponse: suspend (PlayerStreamResponseData) -> Unit = {},
    onReceived: suspend (Int) -> Unit,
    write: suspend (Long, ByteArray, Int, Int) -> Unit,
): PlayerStreamResponseData = fetchPlaybackRange(url, start, endExclusive, validator,
    onResponse = onResponse, onReceived = onReceived, write = write)

internal suspend fun fetchPlaybackProbe(
    url: String, validator: String?, onReceived: suspend (Int) -> Unit,
    write: suspend (Long, ByteArray, Int, Int) -> Unit,
): PlayerStreamResponseData = fetchPlaybackPiece(url, 0, 64L * 1024, validator,
    onReceived = onReceived, write = write)
