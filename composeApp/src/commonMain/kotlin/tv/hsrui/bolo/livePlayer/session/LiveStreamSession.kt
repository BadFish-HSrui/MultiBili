@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.player.base.BoloMpvStreamReader
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import tv.hsrui.network.liveFeature.player.LiveHlsPlaylistData
import tv.hsrui.network.liveFeature.player.LiveHlsResourceData
import tv.hsrui.network.liveFeature.player.LiveHlsSegmentData
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import tv.hsrui.network.liveFeature.player.fetchLiveHlsPlaylist
import tv.hsrui.network.liveFeature.player.fetchLiveStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.TimeSource

internal class LiveStreamSession(
    val generation: Long,
    private val stream: LivePlayStreamData,
    private val roomId: Long,
    private val url: String,
    localReplayEnabled: Boolean = false,
    val cacheCapacityBytes: Long = 256L * 1024 * 1024,
    private val optimizePlaybackSourceEnabled: Boolean = true,
    private val liveBufferDurationMs: Long = 4_000,
    private val onFailure: (Throwable) -> Unit,
) : BoloMpvStreamSource {
    val uri = "bolo-stream://live/$generation/${stream.formatName}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prepared = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val opened = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val closedComplete = CompletableDeferred<Unit>()
    private val transferMutex = Mutex()
    private val buffer = LiveStreamBuffer(mutex = transferMutex)
    private val mutablePlaylist = MutableStateFlow<LiveHlsPlaylistData?>(null)
    private var playlistUrl = url
    val playlist = mutablePlaylist.asStateFlow()
    val bufferedBytes = buffer.bufferedBytes
    private val received = AtomicLong(0L)
    val receivedBytes: Long get() = received.load()

    val isHls = stream.protocolName == "http_hls" && stream.formatName in setOf("fmp4", "ts")
    val localReplayEnabled = localReplayEnabled && isHls
    val cache = LivePlaybackCache(
        capacity = if (this.localReplayEnabled) cacheCapacityBytes else 32L * 1024 * 1024,
        mutex = transferMutex,
        retainHistory = this.localReplayEnabled,
        liveBufferDurationMs = liveBufferDurationMs,
    )
    private val playlistMutex = Mutex()
    private val configurationMutex = Mutex()
    private val routeRevision = AtomicLong(0L)
    private val forwardMs = AtomicLong(-1L)
    private val rebuffering = AtomicBoolean(true)
    private val waitingForSegment = AtomicBoolean(false)
    private var checkedRoute = -1L
    private var checkedDiscontinuity: Long? = null
    private var initializationCheckedAt = TimeSource.Monotonic.markNow()
    private var collector: Job? = null
    private var nextSequence: Long? = null
    private var restartSequence: Long? = null
    private var index = LiveHlsMediaIndex(stream.formatName)
    private var initId: Long? = null
    private var lastMediaEnd: Long? = null
    private var lastConfiguration: Long? = null
    private var indexedDiscontinuity: Long? = null
    private var checkedInitialization: LiveHlsResourceData? = null
    private var cdnIndex = stream.urls.indexOf(url).coerceAtLeast(0)
    private val bodyRequests = Semaphore(if (optimizePlaybackSourceEnabled) 3 else 1)
    private val recentDurations = MutableStateFlow<List<Long>>(emptyList())
    internal val requestEvents = MutableSharedFlow<List<Long>>(extraBufferCapacity = 64)
    internal val activeBodyRequests = AtomicLong(0)
    internal val peakBodyRequests = AtomicLong(0)
    internal val hedgeCount = AtomicLong(0)
    internal val duplicateBytes = AtomicLong(0)
    private val bodyAttempts = mutableMapOf<Triple<Long, Long, Int>, Int>()
    private val identities = mutableMapOf<Pair<Long, Long>, List<Any?>>()

    /** 同一临界区内采集实际缓存正文量与累计下载量。 */
    suspend fun transferSnapshot(): Pair<Long, Long> = transferMutex.withLock {
        val cached = if (isHls) cache.snapshot.value.cachedBytes else buffer.bufferedBytes.value.toLong()
        cached to received.load()
    }

    private suspend fun onReceived(count: Int) {
        withContext(NonCancellable) {
            transferMutex.withLock { received.fetchAndAdd(count.toLong()) }
        }
    }

    fun updateBuffer(bufferedMs: Long?, buffering: Boolean) {
        forwardMs.store(bufferedMs ?: -1L)
        rebuffering.store(buffering)
    }

    suspend fun prepare() {
        check(!closed.load())
        if (prepared.compareAndSet(false, true)) setCollecting(true)
        ready.await()
    }

    fun setCollecting(active: Boolean) {
        if (closed.load() || finished.load()) return
        val previous = collector
        previous?.cancel()
        collector = if (!active) previous else scope.launch {
            previous?.join()
            try {
                if (isHls) runHls() else downloadFlv()
                check(ready.isCompleted) { "直播媒体为空" }
                if (isHls) cache.finish() else buffer.finish()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                val wasReady = ready.isCompleted
                ready.completeExceptionally(error)
                if (!isHls) buffer.close(error)
                if (wasReady && !closed.load()) onFailure(error)
            }
        }
    }

    fun retry(address: String) {
        if (closed.load() || finished.load()) return
        val previous = collector
        previous?.cancel()
        collector = scope.launch {
            previous?.join()
            playlistMutex.withLock {
                playlistUrl = address
                cdnIndex = stream.urls.indexOf(address).coerceAtLeast(0)
                routeRevision.fetchAndAdd(1L)
            }
            // 下一采集任务等待本任务退出，避免两个写入者交叠。
            setCollecting(true)
        }
    }

    suspend fun finishCollection() {
        if (closed.load() || !finished.compareAndSet(false, true)) return
        collector?.cancelAndJoin()
        collector = null
        if (isHls) cache.finish() else buffer.finish()
        ready.complete(Unit)
    }

    private suspend fun runHls() = coroutineScope {
        configurationMutex.withLock { bodyAttempts.clear() }
        val window = mutableMapOf<Long, Pair<CompletableDeferred<Result<LivePlaybackCache.Body>>, Job>>()
        var windowEpoch = cache.snapshot.value.epoch
        suspend fun clearWindow() {
            withContext(NonCancellable) {
                window.values.forEach { it.second.cancel() }
                window.values.forEach { it.second.join() }
                window.clear()
            }
        }
        var loadedAt = TimeSource.Monotonic.markNow()
        val first = refreshPlaylist()
        val refresh = launch {
            var previous = first
            var changed = true
            var advancedAt = TimeSource.Monotonic.markNow()
            while (isActive && !previous.ended) {
                val duration = previous.segments.lastOrNull()?.durationMs ?: previous.targetDurationMs
                val interval = if (waitingForSegment.load() && forwardMs.load() in 0..1_999) 250L
                    else (if (changed) duration else duration / 2).coerceIn(250, 2_000)
                delay((interval - loadedAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                loadedAt = TimeSource.Monotonic.markNow()
                val next = refreshPlaylist()
                changed = next != previous
                if (next.segments.lastOrNull()?.sequence != previous.segments.lastOrNull()?.sequence) {
                    advancedAt = TimeSource.Monotonic.markNow()
                } else check(next.ended || advancedAt.elapsedNow().inWholeMilliseconds < maxOf(30_000, next.targetDurationMs * 3)) {
                    "直播清单长时间没有新分片"
                }
                previous = next
            }
        }
        val verification = if (!optimizePlaybackSourceEnabled) null else launch {
            while (isActive) {
                delay(1_000)
                val resource = configurationMutex.withLock {
                    checkedInitialization?.takeIf { initializationCheckedAt.elapsedNow().inWholeMilliseconds >= 30_000 }
                } ?: continue
                val revision = routeRevision.load()
                try {
                    val body = coroutineScope { downloadResource(resource, initialization = true, redundant = true) }
                    try {
                        configurationMutex.withLock {
                            if (revision == routeRevision.load() && resource == checkedInitialization) {
                                index.initialize(body)
                                val (id, changed) = cache.initialize(body)
                                initId = id
                                initializationCheckedAt = TimeSource.Monotonic.markNow()
                                if (changed) {
                                    identities.clear()
                                    nextSequence = null
                                    lastMediaEnd = null
                                    lastConfiguration = null
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) { cache.discard(body, onlyStaged = true) }
                    }
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    // 后台复核失败不清空已验证的缓冲，下一轮继续复核。
                    configurationMutex.withLock { initializationCheckedAt = TimeSource.Monotonic.markNow() }
                }
            }
        }
        try {
            while (isActive) {
                val (current, revision, sourceIndex) = playlistMutex.withLock {
                    Triple(checkNotNull(mutablePlaylist.value), routeRevision.load(), cdnIndex)
                }
                val segment = configurationMutex.withLock {
                    val last = current.segments.lastOrNull()
                    val changedIdentity = current.segments.any { segment ->
                        identities[segment.discontinuity to segment.sequence]?.let { it != identity(segment) } == true
                    }
                    val expected = nextSequence
                    val candidate = current.segments.firstOrNull { it.sequence >= (expected ?: 0) }
                    if (
                        changedIdentity || expected != null && (last != null && expected > last.sequence + 1 ||
                            candidate != null && (candidate.sequence != expected || candidate.isGap ||
                                indexedDiscontinuity != null && indexedDiscontinuity != candidate.discontinuity))
                    ) {
                        cache.clear()
                        identities.clear()
                        nextSequence = null
                        index = LiveHlsMediaIndex(stream.formatName)
                        checkedInitialization = null
                        restartSequence = null
                        lastMediaEnd = null
                        lastConfiguration = null
                    }
                    if (nextSequence == null) {
                        val tail = current.segments.takeLastWhile {
                            !it.isGap && it.discontinuity == last?.discontinuity &&
                                (restartSequence == null || it.sequence >= restartSequence!!)
                        }
                        var duration = 0L
                        var start = 0
                        for (i in tail.indices.reversed()) {
                            duration += tail[i].durationMs
                            start = i
                            if (duration >= liveBufferDurationMs) break
                        }
                        nextSequence = tail.take(start + 1).lastOrNull {
                            it.startsWithKeyframe == true || current.independentSegments && it.startsWithKeyframe != false
                        }?.sequence ?: tail.firstOrNull {
                            it.startsWithKeyframe != false
                        }?.sequence
                    }
                    current.segments.firstOrNull { it.sequence == nextSequence }
                }
                if (segment == null) {
                    if (current.ended) break
                    waitingForSegment.store(true)
                    try {
                        mutablePlaylist.first { it != current }
                    } finally {
                        waitingForSegment.store(false)
                    }
                    continue
                }
                if (windowEpoch != cache.snapshot.value.epoch || window.keys.any { it < segment.sequence }) clearWindow()
                windowEpoch = cache.snapshot.value.epoch
                cache.prioritize(segment.sequence)
                try {
                    if (stream.formatName == "fmp4") {
                        val initialization = checkNotNull(segment.initialization) { "直播初始化片段缺失" }
                        val (checkInitialization, recheckInitialization) = configurationMutex.withLock {
                            val changed = checkedInitialization != initialization || checkedRoute != revision ||
                                checkedDiscontinuity != segment.discontinuity
                            changed to (!optimizePlaybackSourceEnabled &&
                                initializationCheckedAt.elapsedNow().inWholeMilliseconds >= 30_000)
                        }
                        if (checkInitialization || recheckInitialization) {
                            // 切配置前释放旧窗口，防止已完成后片占满加载预算而阻塞必要初始化。
                            clearWindow()
                            try {
                                val body = downloadResource(initialization, initialization = true)
                                try {
                                    configurationMutex.withLock {
                                        if (revision == routeRevision.load()) {
                                            index.initialize(body)
                                            val (id, changed) = cache.initialize(body)
                                            initId = id
                                            checkedInitialization = initialization
                                            checkedRoute = revision
                                            checkedDiscontinuity = segment.discontinuity
                                            initializationCheckedAt = TimeSource.Monotonic.markNow()
                                            if (changed) {
                                                identities.clear()
                                                lastMediaEnd = null
                                                lastConfiguration = null
                                                nextSequence = null
                                            }
                                        }
                                    }
                                } finally {
                                    withContext(NonCancellable) { cache.discard(body, onlyStaged = true) }
                                }
                            } catch (error: Exception) {
                                currentCoroutineContext().ensureActive()
                                if (checkInitialization) throw error
                                // 串行复核在分片之间执行；暂时失败仍沿用已验证的初始化。
                                configurationMutex.withLock {
                                    initializationCheckedAt = TimeSource.Monotonic.markNow()
                                }
                            }
                            if (revision != routeRevision.load() || nextSequence == null) continue
                        }
                    }
                    val epoch = cache.snapshot.value.epoch
                    val candidates = listOfNotNull(segment, if (!optimizePlaybackSourceEnabled) null else current.segments.firstOrNull {
                        it.sequence == segment.sequence + 1 && !it.isGap && it.discontinuity == segment.discontinuity && it.initialization == segment.initialization
                    })
                    for (pending in candidates) {
                        val old = window[pending.sequence]
                        if (old != null && (!old.second.isCancelled || pending.sequence != segment.sequence)) continue
                        old?.second?.join()
                        val result = CompletableDeferred<Result<LivePlaybackCache.Body>>()
                        val owner = launch {
                            var completed: LivePlaybackCache.Body? = null
                            try {
                                completed = downloadResource(
                                    pending.resource,
                                    segment = pending,
                                    sourcePlaylist = current,
                                    candidateIndex = sourceIndex,
                                )
                                cache.hold(completed, currentCoroutineContext()[Job])
                                result.complete(Result.success(completed))
                                awaitCancellation()
                            } catch (error: Exception) {
                                result.complete(Result.failure(error))
                                if (error is CancellationException) throw error
                            } finally {
                                withContext(NonCancellable) { completed?.let { cache.discard(it, onlyStaged = true) } }
                            }
                        }
                        window[pending.sequence] = result to owner
                    }
                    val task = checkNotNull(window[segment.sequence])
                    val body = task.first.await().getOrThrow()
                    try {
                        configurationMutex.withLock {
                            if (epoch == cache.snapshot.value.epoch && !body.released) {
                                if (indexedDiscontinuity != null && indexedDiscontinuity != segment.discontinuity) index.discontinuity()
                                val media = index.inspect(body, segment, current.independentSegments)
                                val start = media.decodeStartMs ?: media.startMs
                                if (
                                    lastMediaEnd != null && start != null && kotlin.math.abs(start - lastMediaEnd!!) > 250 ||
                                    lastConfiguration != null && media.configurationId != null && lastConfiguration != media.configurationId
                                ) {
                                    cache.clear()
                                    identities.clear()
                                    lastMediaEnd = null
                                    lastConfiguration = null
                                    restartSequence = segment.sequence
                                    nextSequence = null
                                } else {
                                    cache.append(body, segment.sequence, segment.discontinuity, initId, media)
                                    indexedDiscontinuity = segment.discontinuity
                                    lastMediaEnd = media.decodeEndMs ?: media.endMs
                                    media.configurationId?.let { lastConfiguration = it }
                                    identities[segment.discontinuity to segment.sequence] = identity(segment)
                                    identities.keys.filter { it.second < segment.sequence - 64 }.forEach(identities::remove)
                                    nextSequence = segment.sequence + 1
                                    ready.complete(Unit)
                                    body.sourcePlaylist?.let { selected ->
                                        playlistMutex.withLock {
                                            if (body.candidate != cdnIndex) {
                                                cdnIndex = body.candidate
                                                playlistUrl = selected.url
                                                mutablePlaylist.value = selected
                                                routeRevision.fetchAndAdd(1)
                                            }
                                        }
                                    }
                                    body.sourcePlaylist = null
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            task.second.cancelAndJoin()
                            window.remove(segment.sequence)
                        }
                    }
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    clearWindow()
                    if (cache.snapshot.value.epoch != windowEpoch) continue
                    // 全部正文候选耗尽，仅真实缺口允许在本会话重建。
                    val replacementIndex = (error as? LivePlaybackCache.Missing)?.candidate
                    val replacement = if (replacementIndex == null) refreshPlaylist(advance = true, failedRevision = revision)
                    else refreshPlaylist(candidate = replacementIndex).also { replacement ->
                        playlistMutex.withLock {
                            cdnIndex = replacementIndex
                            playlistUrl = replacement.url
                            mutablePlaylist.value = replacement
                            routeRevision.fetchAndAdd(1)
                        }
                    }
                    val matching = replacement.segments.firstOrNull { it.sequence == segment.sequence }
                    if (matching == null || matching.discontinuity != segment.discontinuity || identity(matching) != identity(segment)) {
                        configurationMutex.withLock {
                            cache.clear()
                            identities.clear()
                            nextSequence = null
                            index = LiveHlsMediaIndex(stream.formatName)
                            checkedInitialization = null
                            restartSequence = null
                            lastMediaEnd = null
                            lastConfiguration = null
                        }
                    } else if (error !is LivePlaybackCache.Missing) throw error
                }
            }
        } finally {
            refresh.cancel()
            verification?.cancel()
            clearWindow()
        }
    }

    private fun identity(segment: LiveHlsSegmentData): List<Any?> = listOf(
        io.ktor.http.Url(segment.resource.url).encodedPath, segment.resource.range, segment.mediaStartTimeMs,
        segment.initialization?.let { io.ktor.http.Url(it.url).encodedPath to it.range },
    )

    private suspend fun refreshPlaylist(
        advance: Boolean = false,
        failedRevision: Long = routeRevision.load(),
        candidate: Int? = null,
    ): LiveHlsPlaylistData {
        if (candidate != null) return fetchLiveHlsPlaylist(stream.urls[candidate], roomId, stream)
        return playlistMutex.withLock {
            if (advance && failedRevision == routeRevision.load()) {
                check(cdnIndex + 1 < stream.urls.size) { "直播媒体候选地址已耗尽" }
                playlistUrl = stream.urls[++cdnIndex]
                routeRevision.fetchAndAdd(1L)
            }
            var failure: Exception? = null
            while (true) {
                repeat(2) { attempt ->
                    try {
                        return@withLock fetchLiveHlsPlaylist(playlistUrl, roomId, stream).also {
                            playlistUrl = it.url
                            mutablePlaylist.value = it
                        }
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        failure = error
                        if (attempt == 0) delay(250)
                    }
                }
                if (cdnIndex + 1 >= stream.urls.size) throw checkNotNull(failure)
                playlistUrl = stream.urls[++cdnIndex]
                routeRevision.fetchAndAdd(1L)
            }
            @Suppress("UNREACHABLE_CODE")
            error("直播清单请求失败")
        }
    }

    private suspend fun downloadResource(
        resource: LiveHlsResourceData,
        initialization: Boolean = false,
        segment: LiveHlsSegmentData? = null,
        sourcePlaylist: LiveHlsPlaylistData? = null,
        sequence: Long? = null,
        redundant: Boolean = false,
        progress: (Long, Long, Boolean, Long?) -> Unit = { _, _, _, _ -> },
        candidateIndex: Int = -1,
        requestOwner: Job? = null,
    ): LivePlaybackCache.Body {
        if (segment != null) return coroutineScope {
            val raceOwner = currentCoroutineContext()[Job]
            val preferred = if (candidateIndex >= 0) candidateIndex else playlistMutex.withLock { cdnIndex }
            val order = listOf(preferred) + stream.urls.indices.filter { it != preferred }
            val results = Channel<Pair<Int, Result<LivePlaybackCache.Body>>>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            val bodies = mutableListOf<LivePlaybackCache.Body>()
            val resultMutex = Mutex()
            val changedCandidate = AtomicLong(-1)
            val elapsed = List(order.size) { AtomicLong(0) }
            val received = List(order.size) { AtomicLong(0) }
            val lastProgress = List(order.size) { AtomicLong(0) }
            val expected = List(order.size) { AtomicLong(-1) }
            val blocked = List(order.size) { AtomicBoolean(false) }
            val completedCandidates = mutableSetOf<Int>()
            val totalReceived = AtomicLong(0)
            var winner: LivePlaybackCache.Body? = null
            var launched = 0
            var completed = 0
            var failure: Throwable? = null
            val samples = recentDurations.value.sorted()
            val threshold = if (samples.size < 5) 900L
                else (samples[((samples.size * 95 + 99) / 100 - 1)] * 3 / 2).coerceIn(600, 1_200)
            fun startCandidate() {
                val position = launched++
                val candidate = order[position]
                if (position > 0 && completed < position) hedgeCount.fetchAndAdd(1)
                jobs += launch {
                    try {
                        val list = if (position == 0) checkNotNull(sourcePlaylist) else refreshPlaylist(candidate = candidate)
                        val matching = list.segments.firstOrNull { it.sequence == segment.sequence }
                        if (
                            position > 0 && (matching != null && (matching.isGap || matching.discontinuity != segment.discontinuity || identity(matching) != identity(segment)) ||
                                matching == null && list.segments.firstOrNull()?.sequence?.let { it > segment.sequence } == true)
                        ) {
                            changedCandidate.compareAndSet(-1, candidate.toLong())
                        }
                        check(matching != null && !matching.isGap && matching.discontinuity == segment.discontinuity && identity(matching) == identity(segment)) {
                            "候选尚未发布相同直播分片"
                        }
                        if (position > 0 && stream.formatName == "fmp4") {
                            val init = downloadResource(
                                checkNotNull(matching.initialization),
                                initialization = true,
                                sequence = segment.sequence,
                                redundant = true,
                                candidateIndex = candidate,
                            )
                            try {
                                if (!cache.matchesInitialization(init)) {
                                    changedCandidate.compareAndSet(-1, candidate.toLong())
                                    error("候选直播初始化内容变化")
                                }
                            } finally {
                                withContext(NonCancellable) { cache.discard(init, onlyStaged = true) }
                            }
                        }
                        val body = downloadResource(
                            matching.resource,
                            sequence = segment.sequence,
                            redundant = position > 0,
                            requestOwner = if (position == 0) raceOwner else currentCoroutineContext()[Job],
                            candidateIndex = candidate,
                            progress = { network, bytes, waiting, size ->
                                totalReceived.fetchAndAdd(bytes)
                                elapsed[position].store(network)
                                received[position].fetchAndAdd(bytes)
                                if (bytes > 0) lastProgress[position].store(network)
                                blocked[position].store(waiting)
                                expected[position].store(size ?: -1)
                            },
                        )
                        body.candidate = candidate
                        body.sourcePlaylist = list
                        withContext(NonCancellable) { resultMutex.withLock { bodies.add(body) } }
                        results.send(position to Result.success(body))
                        awaitCancellation()
                    } catch (error: Exception) {
                        results.trySend(position to Result.failure(error))
                    }
                }
            }
            try {
                startCandidate()
                while (winner == null) {
                    currentCoroutineContext().ensureActive()
                    val outcome = results.tryReceive().getOrNull()
                    if (outcome != null) {
                        val (position, result) = outcome
                        completedCandidates.add(position)
                        completed++
                        if (result.isSuccess) {
                            winner = result.getOrThrow()
                            recentDurations.update { (it + elapsed[position].load()).takeLast(20) }
                            break
                        }
                        failure = result.exceptionOrNull()
                        if (completed == launched && launched == order.size) {
                            if (changedCandidate.load() >= 0) throw LivePlaybackCache.Missing(null).also { it.candidate = changedCandidate.load().toInt() }
                            throw checkNotNull(failure)
                        }
                    }
                    // 网络等待从最近一次进度继续计时，块预算等待暂停该时钟。
                    val monitoring = (0 until launched).firstOrNull { it !in completedCandidates } ?: 0
                    val network = elapsed[monitoring].load()
                    val ahead = forwardMs.load()
                    val bytes = received[monitoring].load()
                    val remaining = expected[monitoring].load().takeIf { it > bytes }?.let { (it - bytes) * network / bytes.coerceAtLeast(1) }
                    val slow = !blocked[monitoring].load() && network >= threshold &&
                        (rebuffering.load() || ahead <= 0 || network - lastProgress[monitoring].load() >= threshold ||
                            remaining != null && remaining >= ahead - 1_000 || network >= threshold * 2)
                    val isHead = configurationMutex.withLock { nextSequence == segment.sequence }
                    if (launched < order.size && launched - completed < 2 &&
                        (completed == launched || optimizePlaybackSourceEnabled && slow && isHead)) startCandidate()
                    delay(50)
                }
                checkNotNull(winner)
            } finally {
                val caller = currentCoroutineContext()[Job]
                withContext(NonCancellable) {
                    jobs.forEach { it.cancel() }
                    jobs.forEach { it.join() }
                    bodies.filter { it !== winner || caller?.isActive != true }.forEach { cache.discard(it, onlyStaged = true) }
                    duplicateBytes.fetchAndAdd((totalReceived.load() - (winner?.size ?: 0)).coerceAtLeast(0))
                    results.close()
                }
            }
        }
        val requestEpoch = cache.snapshot.value.epoch
        val body = cache.stage(sequence, redundant)
        if (requestOwner != null) cache.hold(body, requestOwner)
        var returned = false
        var total: Long? = null
        var responseUrl: String? = null
        val network = AtomicLong(0L)
        var transferred = 0L
        try {
            repeat(2) { attempt ->
                var successful = false
                val backpressure = AtomicBoolean(false)
                val prefix = body.size.takeIf { it >= 64 * 1024 && total != null } ?: 0
                if (prefix == 0 && body.size > 0) cache.reset(body)
                val start = resource.range?.first ?: 0L
                val end = resource.range?.last ?: total?.minus(1)
                val overlapStart = if (prefix > 0) prefix - 32 * 1024 else 0
                val requested = if (prefix > 0) (start + overlapStart)..checkNotNull(end) else resource.range
                var compareOffset = overlapStart
                var resuming = prefix > 0
                val excluded = AtomicLong(0L)
                var progressJob: Job? = null
                val before = transferred
                try {
                    bodyRequests.withPermit {
                        if (!initialization && sequence != null) configurationMutex.withLock {
                            val key = Triple(requestEpoch, sequence, candidateIndex)
                            val count = bodyAttempts[key] ?: 0
                            check(count < 2) { "直播分片候选尝试次数已耗尽" }
                            bodyAttempts[key] = count + 1
                            bodyAttempts.keys.filter { it.first != requestEpoch || it.second < sequence - 64 }.forEach(bodyAttempts::remove)
                        }
                        val active = activeBodyRequests.fetchAndAdd(1) + 1
                        while (true) {
                            val peak = peakBodyRequests.load()
                            if (peak >= active || peakBodyRequests.compareAndSet(peak, active)) break
                        }
                        try {
                            coroutineScope {
                                val clockStart = TimeSource.Monotonic.markNow()
                                progressJob = launch {
                                    while (isActive) {
                                        delay(50)
                                        if (!backpressure.load()) {
                                            network.store((clockStart.elapsedNow().inWholeMilliseconds - excluded.load()).coerceAtLeast(0))
                                            progress(network.load(), 0, false, body.expectedSize)
                                        }
                                    }
                                }
                                val ahead = forwardMs.load()
                                val budget = if (initialization || rebuffering.load() || ahead <= 0) 6_000L
                                    else (ahead - 500).coerceIn(2_000, 6_000)
                                try {
                                    fetchLiveStream(
                                        resource.url,
                                        roomId,
                                        requested,
                                        receiveLimit = if (initialization) 1024 * 1024 else cache.snapshot.value.loadingCapacityBytes,
                                        onResponse = { response ->
                                            if (resuming && response.statusCode == 200) {
                                                cache.reset(body)
                                                resuming = false
                                            }
                                            if (resuming) check(response.totalLength == total && response.url == responseUrl) {
                                                "直播续传资源身份变化"
                                            }
                                            total = response.totalLength
                                            responseUrl = response.url
                                            cache.expect(body, resource.range?.let { it.last - it.first + 1 } ?: total)
                                        },
                                        onReceived = { count ->
                                            onReceived(count)
                                            transferred += count
                                            progress(network.load(), count.toLong(), false, body.expectedSize)
                                        },
                                        responseTimeoutMillis = 3_000,
                                        readTimeoutMillis = 2_000,
                                        networkTimeoutMillis = budget,
                                        allowFullResponse = resuming && resource.range == null,
                                        onProgress = { network.store(it) },
                                    ) { bytes, offset, length ->
                                        var verified = 0
                                        if (resuming && compareOffset < prefix) {
                                            verified = minOf(length, prefix - compareOffset)
                                            for (i in 0 until verified) check(body.byteAt(compareOffset + i) == (bytes[offset + i].toInt() and 255)) {
                                                "直播续传重叠正文不一致"
                                            }
                                            compareOffset += verified
                                        }
                                        val waiting = TimeSource.Monotonic.markNow()
                                        backpressure.store(true)
                                        progress(network.load(), 0, true, body.expectedSize)
                                        try {
                                            if (length > verified) cache.write(body, bytes, offset + verified, length - verified)
                                        } finally {
                                            excluded.fetchAndAdd(waiting.elapsedNow().inWholeMilliseconds)
                                            backpressure.store(false)
                                            progress(network.load(), 0, false, body.expectedSize)
                                        }
                                    }
                                } finally {
                                    progressJob.cancel()
                                }
                            }
                        } finally {
                            activeBodyRequests.fetchAndAdd(-1)
                        }
                    }
                    check(body.size > 0 && (!resuming || compareOffset == prefix)) { "直播媒体为空或续传不完整" }
                    successful = true
                    returned = true
                    return body
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (attempt == 1) throw error
                    delay(250)
                } finally {
                    progressJob?.cancel()
                    requestEvents.tryEmit(
                        listOf(
                            sequence ?: -1,
                            candidateIndex.toLong(),
                            attempt.toLong(),
                            network.load(),
                            transferred - before,
                            if (successful) 1 else 0,
                            if (redundant) 1 else 0,
                        )
                    )
                }
            }
            error("直播媒体请求失败")
        } finally {
            if (!returned) withContext(NonCancellable) { cache.discard(body, onlyStaged = true) }
        }
    }

    private suspend fun downloadFlv() {
        fetchLiveStream(url, roomId, onReceived = ::onReceived, responseTimeoutMillis = 3_000, readTimeoutMillis = 2_000) { bytes, offset, length ->
            buffer.write(bytes, offset, length)
            ready.complete(Unit)
        }
    }

    override suspend fun open(uri: String): BoloMpvStreamReader? {
        if (uri != this.uri || closed.load() || !opened.compareAndSet(false, true)) return null
        return object : BoloMpvStreamReader {
            override val job = Job(scope.coroutineContext[Job])
            override suspend fun read(bytes: ByteArray): Int = buffer.read(bytes)
            override suspend fun seek(position: Long): Long = -1L
            override suspend fun size(): Long = -1L
            override suspend fun close() { job.cancel() }
        }
    }

    override fun cancel() {
        if (closed.compareAndSet(false, true)) {
            ready.cancel()
            scope.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    scope.coroutineContext[Job]?.join()
                    buffer.close()
                    cache.close()
                } finally { closedComplete.complete(Unit) }
            }
        }
    }

    suspend fun awaitClosed() {
        cancel()
        closedComplete.await()
    }
}
