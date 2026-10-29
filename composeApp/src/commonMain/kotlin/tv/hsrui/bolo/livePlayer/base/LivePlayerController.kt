package tv.hsrui.bolo.livePlayer.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.setting.live.LiveSettings
import tv.hsrui.bolo.livePlayer.session.LiveHlsInput
import tv.hsrui.bolo.livePlayer.session.LivePlaybackTimeline
import tv.hsrui.bolo.livePlayer.session.LiveStreamSession
import tv.hsrui.bolo.livePlayer.session.latestPlaybackPositionMs
import tv.hsrui.bolo.player.base.BoloMpvBackend
import tv.hsrui.bolo.player.base.BoloMpvEvent
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import tv.hsrui.bolo.player.base.BoloPlaybackClock
import tv.hsrui.bolo.player.base.BoloPlaybackObservation
import tv.hsrui.bolo.player.base.BoloPlayerDiagnosticsSampler
import tv.hsrui.bolo.player.base.BoloPlayerInfo
import tv.hsrui.bolo.player.base.BoloPlayerOutputController
import tv.hsrui.bolo.player.base.boloMpvDispatcher
import tv.hsrui.bolo.player.base.withoutDynamicValues
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/** 直播调度与字节流独立于点播；公开操作归 Main 所有。 */
class LivePlayerController(
    private val settings: LiveSettings = getKoin().get<BoloSettings>().live,
) : BoloPlayerOutputController() {
    // 播放器生命周期内固定配置，切流和内部重建不重新读取设置。
    private val configuredLocalReplayEnabled = settings.localReplayEnabled
    private val optimizePlaybackSourceEnabled = settings.optimizePlaybackSourceEnabled
    private val playbackBufferDisabled = settings.playbackBufferDisabled
    private val liveBufferDurationMs = if (playbackBufferDisabled) 0L else 4_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(LivePlayerState())
    val state: StateFlow<LivePlayerState> = mutableState.asStateFlow()
    private val mutableInfo = MutableStateFlow(LivePlayerInfo())
    val info: StateFlow<LivePlayerInfo> = mutableInfo.asStateFlow()
    private var infoPanelVisible = false
    private var infoRevision = 0L
    private var lastInfoSample: TimeMark? = null
    private val diagnosticsSampler = BoloPlayerDiagnosticsSampler()
    private val transferOrigin = TimeSource.Monotonic.markNow()
    private val transferSamples = ArrayDeque<Pair<Duration, Long>>()
    private var sampledStream: LiveStreamSession? = null
    private val mutableBackend = MutableStateFlow<BoloMpvBackend?>(null)
    internal override val backend: StateFlow<BoloMpvBackend?> = mutableBackend.asStateFlow()
    private val mutableFrameRate = MutableStateFlow(0f)
    internal override val outputFrameRate: StateFlow<Float> = mutableFrameRate.asStateFlow()
    private var stream: LivePlayStreamData? = null
    private var roomId = 0L
    private var urls = emptyList<String>()
    private var urlIndex = 0
    private var urlRetries = 0
    private var loadedAt: TimeMark? = null
    private var streamSession: LiveStreamSession? = null
    private var localReplayEnabled = false
    private var cacheCapacityBytes = 256L * 1024 * 1024
    private val playbackTimeline = LivePlaybackTimeline(liveBufferDurationMs = liveBufferDurationMs)
    val timeline = playbackTimeline.state
    private var timelineJob: Job? = null
    private var reader: BoloMpvStreamSource? = null
    private var readerStartMs: Long? = null
    private var readerInvalidated = false
    private var retiringStream: LiveStreamSession? = null
    private var broadcastStart: Long? = null
    private var readerEpoch = -1L
    private var sourceRevision = 0L
    private var generation = 0L
    private var seekRequestSequence = 0L
    private var activeSeekRequest = 0L
    private var seekingLatest = false
    private var seekConfirmationJob: Job? = null
    private var ready = false
    private var foreground = true
    private var videoOutputActive = true
    private var disposed = false
    private var volume = 100
    private var mergeAudioChannelsEnabled = settings.mergeAudioChannelsEnabled
    private var mergeAudioRevision = 0L
    private var mergeAudioJob: Job? = null
    private var dynamicLoudnessEnabled = settings.dynamicLoudnessEnabled
    private var dynamicLoudnessTargetLufs = settings.dynamicLoudnessTargetLufs.toDouble()
    private var dynamicLoudnessRangeLu = settings.dynamicLoudnessRangeLu.toDouble()
    private var dynamicLoudnessTruePeakDbtp = settings.dynamicLoudnessTruePeakDbtp.toDouble()
    private var loudnessConfigured: Boolean? = null
    private var loudnessRevision = 0L
    private var loudnessJob: Job? = null
    private var sourceFrameRate = 0f
    private var loadJob: Job? = null
    private var eventsJob: Job? = null
    private var intentJob: Job? = null
    private var volumeJob: Job? = null
    private var outputJob: Job? = null
    private var detachJob: Job? = null
    private val commandJobs = mutableSetOf<Job>()
    private var releaseJob: Job? = null
    private var playbackClock: BoloPlaybackClock? = null
    private var playbackObserver: ((BoloPlaybackObservation) -> Unit)? = null
    private var observationJob: Job? = null
    internal var onPlaybackObservation: ((BoloPlaybackObservation) -> Unit)? = null
    internal val playbackObservation get() = playbackClock?.latest?.value?.takeIf { it.generation == generation }
    var playIntentRevision: Long = 0L
        private set

    init {
        scope.launch {
            while (isActive) {
                if (streamSession?.isHls != true) playbackTimeline.observeInstant(state.value.isPlaying)
                else playbackTimeline.tick()
                sampleBuffer()
                sampleInfo()
                delay(250)
            }
        }
    }

    fun load(stream: LivePlayStreamData, roomId: Long, playWhenReady: Boolean, liveStartTimeEpochSeconds: Long? = null) {
        if (disposed) return
        playIntentRevision++
        val keepCache = streamSession?.localReplayEnabled == true && this.roomId == roomId &&
            this.stream?.currentQn == stream.currentQn && this.stream?.codec == stream.codec &&
            this.stream?.formatName == stream.formatName && broadcastStart == liveStartTimeEpochSeconds
        if (!keepCache) {
            streamSession?.let { retiringStream = it }
            streamSession?.cancel()
            streamSession = null
            timelineJob?.cancel()
            playbackTimeline.reset()
            sourceRevision++
            localReplayEnabled = configuredLocalReplayEnabled && stream.protocolName == "http_hls" &&
                stream.formatName in setOf("fmp4", "ts")
            cacheCapacityBytes = settings.cacheSizeMiB.toLong() * 1024 * 1024
        }
        broadcastStart = liveStartTimeEpochSeconds
        playbackTimeline.setStartTime(liveStartTimeEpochSeconds)
        playbackTimeline.setPaused(!playWhenReady)
        this.stream = stream
        mutableInfo.value = LivePlayerInfo(currentQn = stream.currentQn,
            protocolName = stream.protocolName, formatName = stream.formatName)
        this.roomId = roomId
        urls = stream.urls.filter { it.isNotBlank() }.distinct()
        urlIndex = 0
        urlRetries = 0
        mutableState.value = LivePlayerState(localReplayEnabled = localReplayEnabled, playWhenReady = playWhenReady && foreground)
        if (roomId <= 0L || urls.isEmpty()) {
            fail("直播播放地址为空")
            return
        }
        if (!foreground || !localReplayEnabled && !playWhenReady) {
            closeEngine(suspended = true)
            return
        }
        if (keepCache) streamSession?.retry(urls.first())
        startLoad()
    }

    private fun startLoad(targetMs: Long? = null, live: Boolean = targetMs == null) {
        if (disposed || !foreground || urls.isEmpty() || !localReplayEnabled && !state.value.playWhenReady) return
        if (state.value.isBroadcastEnded && (streamSession == null || !localReplayEnabled)) return
        stopPlaybackObservation()
        val expected = ++generation
        activeSeekRequest = 0
        seekingLatest = targetMs == null
        seekConfirmationJob?.cancel()
        seekConfirmationJob = null
        resetInfoSampling(clearMedia = true)
        readerInvalidated = false
        val previousReader = reader
        reader = null
        previousReader?.cancel()
        val previousLoad = loadJob
        previousLoad?.cancel()
        val previousCommands = commandJobs.toList()
        previousCommands.forEach { it.cancel() }
        intentJob = null
        ready = false
        loadedAt = null
        sourceFrameRate = 0f
        mutableFrameRate.value = 0f
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = true,
            isPlaybackSuspended = !foreground, isEnded = false, error = null,
            pendingSeekPositionMs = targetMs)
        val closing = releaseJob
        val detaching = detachJob
        loadJob = scope.launch {
            try {
                withTimeout(20_000) {
                    previousLoad?.join()
                    (previousReader as? LiveHlsInput)?.awaitClosed()
                    retiringStream?.awaitClosed()
                    retiringStream = null
                    previousCommands.forEach { it.join() }
                    closing?.join()
                    detaching?.join()
                    observationJob?.join()
                    if (!isCurrent(expected)) return@withTimeout
                    var engine = mutableBackend.value
                    if (engine == null) {
                        // 构造完成后必须交接或销毁，取消不能遗失 native handle。
                        engine = withContext(NonCancellable) {
                            val created = withContext(boloMpvDispatcher) { BoloMpvBackend() }
                            if (!isCurrent(expected)) {
                                withContext(boloMpvDispatcher) { created.destroy() }
                                null
                            } else {
                                mutableBackend.value = created
                                created
                            }
                        } ?: return@withTimeout
                    }
                    val current = engine
                    withContext(boloMpvDispatcher) {
                        if (isCurrent(expected, current)) check(current.pause(true) >= 0) { "直播定位暂停失败" }
                    }
                    if (foreground && videoOutputActive) withTimeout(8_000) { current.awaitOutput() }
                    if (!isCurrent(expected, current)) return@withTimeout
                    check(current.setAudioActive(state.value.playWhenReady)) { "音频会话激活失败" }
                    if (!isCurrent(expected, current)) return@withTimeout
                    var streams = streamSession
                    if (streams == null) {
                        val revision = sourceRevision
                        lateinit var collecting: LiveStreamSession
                        collecting = LiveStreamSession(
                            revision,
                            checkNotNull(stream),
                            roomId,
                            urls[urlIndex],
                            localReplayEnabled = localReplayEnabled,
                            cacheCapacityBytes = cacheCapacityBytes,
                            optimizePlaybackSourceEnabled = optimizePlaybackSourceEnabled,
                            liveBufferDurationMs = liveBufferDurationMs,
                        ) { error ->
                            scope.launch {
                                if (!disposed && foreground && sourceRevision == revision && streamSession === collecting) {
                                    retrySource("直播媒体读取失败：${error.message ?: "未知错误"}", reload = false)
                                }
                            }
                        }
                        streams = collecting
                        streamSession = streams
                        timelineJob?.cancel()
                        timelineJob = if (!collecting.isHls) null else scope.launch {
                            collecting.cache.snapshot.collect { snapshot ->
                                playbackTimeline.update(
                                    snapshot,
                                    collecting.playlist.value,
                                    exposeRanges = collecting.localReplayEnabled,
                                )
                                val position = state.value.pendingSeekPositionMs ?: timeline.value.positionMs
                                if (ready && state.value.playWhenReady && snapshot.ranges.isNotEmpty() &&
                                    (snapshot.epoch != readerEpoch || collecting.localReplayEnabled && position != null && position < snapshot.ranges.first().startMs)
                                ) startLoad()
                            }
                        }
                    }
                    streams.prepare()
                    if (!isCurrent(expected, current)) return@withTimeout
                    val source: BoloMpvStreamSource
                    val uri: String
                    var playbackTargetMs: Long? = null
                    if (streams.isHls) {
                        streams.cache.awaitSegment()
                        val initial = streams.cache.snapshot.value
                        seekingLatest = targetMs == null || targetMs < (initial.ranges.firstOrNull()?.startMs ?: 0) ||
                            targetMs > (initial.ranges.lastOrNull()?.endMs ?: Long.MAX_VALUE)
                        if (seekingLatest) {
                            val tail = streams.playlist.value?.segments?.lastOrNull { !it.isGap }?.sequence
                            streams.cache.snapshot.first { value ->
                                value.epoch != initial.epoch || value.ended || tail == null ||
                                    value.lastReceivedSegment?.sequence?.let { it >= tail } == true
                            }
                            check(streams.cache.snapshot.value.epoch == initial.epoch) { "直播起播缓存已失效" }
                        }
                        if (!isCurrent(expected, current)) return@withTimeout
                        playbackTargetMs = checkNotNull(streams.cache.resolveTarget(targetMs)) { "直播定位时间不可用" }
                        val segment = checkNotNull(streams.cache.select(playbackTargetMs)) { "直播定位关键帧不可用" }
                        val cached = LiveHlsInput(streams.cache, segment, expected, checkNotNull(stream).formatName,
                            targetDurationMs = streams.playlist.value?.targetDurationMs ?: 0) {
                            scope.launch {
                                if (isCurrent(expected, current) && !readerInvalidated) {
                                    readerInvalidated = true
                                    if (state.value.playWhenReady) startLoad(restoreTarget())
                                    else ready = false
                                }
                            }
                        }
                        source = cached
                        reader = cached
                        uri = cached.start()
                        readerStartMs = cached.startMs
                        readerEpoch = streams.cache.snapshot.value.epoch
                        activeSeekRequest = ++seekRequestSequence
                        mutableState.value = state.value.copy(pendingSeekPositionMs = playbackTargetMs)
                        playbackTimeline.prepareSeek(playbackTargetMs, seekingLatest || live)
                    } else {
                        source = streams
                        uri = streams.uri
                        readerStartMs = null
                        mutableState.value = state.value.copy(pendingSeekPositionMs = null)
                    }
                    reader = source
                    applyMergeAudioChannels(expected, current)
                    applyDynamicLoudness(expected, current)
                    val gain = volume.toDouble()
                    val visible = foreground && videoOutputActive
                    val clock = BoloPlaybackClock()
                    playbackClock = clock
                    playbackObserver = onPlaybackObservation
                    val targetSeconds = playbackTargetMs?.div(1000.0)
                    val request = activeSeekRequest
                    val result = withContext(boloMpvDispatcher) {
                        if (!isCurrent(expected, current)) return@withContext 0
                        check(current.videoEnabled(visible) >= 0) { "视频轨配置失败" }
                        check(current.volume(gain) >= 0) { "音量配置失败" }
                        check(current.speed(1.0) >= 0) { "直播播放速度配置失败" }
                        clock.begin(expected, 1.0)
                        if (targetSeconds != null) clock.beginSeek()
                        current.loadLive(source, uri, expected, bufferingEnabled = !playbackBufferDisabled,
                            targetSeconds = targetSeconds, request = request)
                    }
                    if (!isCurrent(expected, current)) return@withTimeout
                    if (result < 0) retrySource("直播流打开失败（$result）")
                    else {
                        startEvents(current, clock, playbackObserver)
                        // 确认定位后取消此任务；准备与定位共用外层加载期限。
                        awaitCancellation()
                    }
                }
            } catch (error: TimeoutCancellationException) {
                if (isCurrent(expected)) retrySource("直播加载超时")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(expected)) retrySource("直播播放失败：${error.message ?: "未知错误"}")
            }
        }
    }

    private fun isCurrent(expected: Long, engine: BoloMpvBackend? = mutableBackend.value): Boolean =
        !disposed && generation == expected && mutableBackend.value === engine

    private fun restoreTarget(): Long? = state.value.pendingSeekPositionMs ?: timeline.value.positionMs

    private fun confirmSeek(position: Long, request: Long) {
        val target = state.value.pendingSeekPositionMs ?: return
        val engine = mutableBackend.value ?: return
        if (request != activeSeekRequest || kotlin.math.abs(position - target) > 250 ||
            seekConfirmationJob?.isActive == true) return
        if (streamSession?.cache?.snapshot?.value?.epoch != readerEpoch) {
            startLoad(restoreTarget())
            return
        }
        val expected = generation
        val input = reader as? LiveHlsInput ?: return
        seekConfirmationJob = launchCommand {
            input.confirm()
            if (!isCurrent(expected, engine) || activeSeekRequest != request || !foreground) return@launchCommand
            playbackTimeline.confirmSeek(position, seekingLatest)
            loadJob?.cancel()
            loadJob = null
            mutableState.value = state.value.copy(pendingSeekPositionMs = null, isBuffering = false, isPlaying = false)
            applyPlayIntent()
        }
    }

    private fun mediaPosition(seconds: Double): Long? {
        if (!seconds.isFinite() || seconds < 0 || seconds > Long.MAX_VALUE / 1000.0) return null
        var position = (seconds * 1000).roundToLong()
        if (stream?.formatName == "ts") {
            val origin = readerStartMs ?: position
            val wrap = (1L shl 33) / 90
            while (position < origin - wrap / 2) position += wrap
            while (position > origin + wrap / 2) position -= wrap
        }
        return position
    }

    private fun startEvents(engine: BoloMpvBackend, clock: BoloPlaybackClock, observer: ((BoloPlaybackObservation) -> Unit)?) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            val events = Channel<BoloMpvEvent>(Channel.UNLIMITED)
            val polling = launch(boloMpvDispatcher) {
                try {
                    while (isActive && playbackClock === clock && mutableBackend.value === engine) {
                        for (index in 0 until 128) {
                            val event = engine.poll() ?: break
                            clock.observe(event)
                            events.send(event)
                        }
                        clock.sample()
                        delay(20)
                    }
                } finally { events.close() }
            }
            val infoSampling = launch {
                while (isActive && playbackClock === clock && mutableBackend.value === engine) {
                    delay(1_000)
                    if (ready && !infoPanelVisible) {
                        val expected = generation
                        val snapshot = withContext(boloMpvDispatcher) { engine.info(false) }
                        if (snapshot != null && snapshot.generation == expected && isCurrent(expected, engine)) {
                            sourceFrameRate = snapshot.info.video.fps?.takeIf { it.isFinite() && it > 0 }?.toFloat() ?: 0f
                            updateFrameRate()
                        }
                    }
                }
            }
            try {
                for (event in events) {
                    event.observation?.let { observer?.invoke(it) }
                    if (!disposed && event.generation == generation && mutableBackend.value === engine) handleEvent(event)
                }
            } finally { polling.cancel(); infoSampling.cancel(); events.cancel() }
        }
    }

    /** 先封存旧计时器，再允许新代次采样；最终快照仍交给旧统计会话。 */
    private fun stopPlaybackObservation() {
        eventsJob?.cancel()
        eventsJob = null
        val clock = playbackClock ?: return
        val observer = playbackObserver
        playbackClock = null
        playbackObserver = null
        val previous = observationJob
        observationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                previous?.join()
                val snapshot = withContext(boloMpvDispatcher) {
                    clock.setPaused(true)
                    clock.latest.value
                }
                snapshot?.let { observer?.invoke(it) }
            }
        }
    }

    internal suspend fun awaitPlaybackObservation() { observationJob?.join() }

    internal suspend fun pauseAndObserve() {
        pause()
        intentJob?.join()
        awaitPlaybackObservation()
        val clock = playbackClock ?: return
        val snapshot = withContext(boloMpvDispatcher) { clock.sample() }
        if (playbackClock === clock) playbackObserver?.invoke(snapshot)
    }

    internal fun setInfoPanelVisible(visible: Boolean) {
        if (disposed || infoPanelVisible == visible) return
        infoPanelVisible = visible
        resetInfoSampling()
    }

    private fun resetInfoSampling(clearMedia: Boolean = false) {
        infoRevision++
        lastInfoSample = null
        diagnosticsSampler.reset()
        if (clearMedia) loudnessConfigured = null
        transferSamples.clear()
        sampledStream = null
        mutableInfo.value = info.value.copy(
            media = if (clearMedia) BoloPlayerInfo() else info.value.media.withoutDynamicValues(),
            downloadBytesPerSecond = null,
        )
    }

    private suspend fun sampleBuffer() {
        val streams = streamSession ?: return
        val engine = mutableBackend.value
        val expected = generation
        val nativeAhead = if (ready && foreground && engine != null) {
            withContext(boloMpvDispatcher) { if (isCurrent(expected, engine)) engine.bufferedDurationMs() else null }
        } else null
        if (!isCurrent(expected, engine) || streamSession !== streams) return
        // 已进入 demux 的时间与待交付分片可能重叠，只按连续区间终点计算。
        val position = timeline.value.positionMs
        val range = if (position != null && nativeAhead != null) streams.cache.snapshot.value.ranges.firstOrNull {
            it.startMs <= position + nativeAhead + 250 && it.endMs >= position
        } else null
        val ahead = if (position != null && range != null) maxOf(nativeAhead ?: 0, range.endMs - position) else nativeAhead
        streams.updateBuffer(ahead, !ready || state.value.isBuffering || !state.value.playWhenReady)
    }

    private suspend fun sampleInfo() {
        if (disposed || !foreground || !infoPanelVisible) return
        if (lastInfoSample?.elapsedNow()?.let { it < 500.milliseconds } == true) return
        lastInfoSample = TimeSource.Monotonic.markNow()
        val expected = generation
        val revision = infoRevision
        val streams = streamSession
        val engine = mutableBackend.value
        val intent = playIntentRevision
        val diagnostics = ready && videoOutputActive && state.value.isPlaying
        if (!diagnostics) diagnosticsSampler.reset()
        val snapshot = if (ready && engine != null) {
            try {
                withContext(boloMpvDispatcher) { engine.info(true) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
        } else null
        val transfer = streams?.transferSnapshot()
        if (!isCurrent(expected, engine) || !foreground || !infoPanelVisible ||
            revision != infoRevision || streams !== streamSession) return
        val media = if (snapshot?.generation == expected) {
            sourceFrameRate = snapshot.info.video.fps?.takeIf { it.isFinite() && it > 0 }?.toFloat() ?: 0f
            updateFrameRate()
            if (diagnostics && intent == playIntentRevision && videoOutputActive && state.value.isPlaying) {
                diagnosticsSampler.sample(snapshot.info)
            } else {
                diagnosticsSampler.reset()
                snapshot.info.copy(decoderDroppedFramesPerSecond = null, outputDroppedFramesPerSecond = null,
                    avSyncDifferenceMs = null)
            }
        } else {
            diagnosticsSampler.reset()
            info.value.media.withoutDynamicValues()
        }
        var updated = info.value.copy(media = media.withLoudnessInfo())
        if (streams != null && transfer != null) {
            val (cachedBytes, received) = transfer
            val now = transferOrigin.elapsedNow()
            val previous = transferSamples.lastOrNull()
            if (sampledStream !== streams || previous != null &&
                (now - previous.first > 1500.milliseconds || received < previous.second)) transferSamples.clear()
            sampledStream = streams
            transferSamples.addLast(now to received)
            while (transferSamples.size > 1 && now - transferSamples[1].first >= 1.seconds) transferSamples.removeFirst()
            val (start, bytes) = transferSamples.first()
            val elapsed = now - start
            val speed = if (elapsed in 1.seconds..2.seconds) {
                ((received - bytes) / elapsed.toDouble(DurationUnit.SECONDS)).toLong().coerceAtLeast(0)
            } else null
            updated = updated.withTransferStatistics(
                receivedBytes = received,
                cachedBytes = cachedBytes,
                downloadBytesPerSecond = speed,
                timeline = timeline.value.takeIf { streams.localReplayEnabled },
            )
        } else updated = updated.copy(receivedBytes = null, cachedBytes = null, downloadBytesPerSecond = null,
            cachedDurationMs = null, forwardDurationMs = null, liveOffsetMs = null)
        mutableInfo.value = updated
    }

    private fun handleEvent(event: BoloMpvEvent) {
        when (event.type) {
            BoloMpvEvent.Position -> if (!state.value.isSeeking && state.value.playWhenReady) {
                mediaPosition(event.value)?.let { position ->
                    if (readerStartMs?.let { position >= it - 250 } != false) playbackTimeline.observe(position)
                }
            }
            BoloMpvEvent.Loaded -> {
                ready = true
                loadedAt = TimeSource.Monotonic.markNow()
                if (!state.value.isSeeking) { loadJob?.cancel(); loadJob = null }
                mutableState.value = state.value.copy(isBuffering = state.value.isSeeking || state.value.playWhenReady)
                applyPlayIntent()
            }
            BoloMpvEvent.Paused -> if (ready) {
                mutableState.value = state.value.copy(isPlaying = event.value == 0.0 && state.value.playWhenReady &&
                    !state.value.isSeeking && !state.value.isBuffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Buffering -> if (ready) {
                val buffering = state.value.isSeeking || event.value != 0.0 && state.value.playWhenReady
                mutableState.value = state.value.copy(isBuffering = buffering,
                    isPlaying = state.value.playWhenReady && !buffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Restart -> if (ready) {
                if (state.value.isSeeking) {
                    mediaPosition(event.value)?.let { confirmSeek(it, event.request) }
                } else if (state.value.playWhenReady) {
                    mutableState.value = state.value.copy(isBuffering = false,
                        isPlaying = !state.value.isPlaybackSuspended)
                }
            }
            BoloMpvEvent.Eof -> if (foreground && !state.value.isSeeking && event.value != 0.0) {
                if (state.value.isBroadcastEnded) finishReading()
                else if (ready) retrySource("直播流已结束", ended = true)
            }
            BoloMpvEvent.Error -> if (foreground && !readerInvalidated) retrySource("直播流播放失败（${event.error}）")
            BoloMpvEvent.Overflow -> fail("播放器事件队列溢出，请重新加载")
        }
        updateFrameRate()
    }

    private fun retrySource(message: String, ended: Boolean = false, reload: Boolean = true) {
        if (disposed) return
        if (state.value.isBroadcastEnded) { finishReading(); return }
        resetRetriesAfterPlayback()
        val retainCache = streamSession?.isHls == true && (reader != null || streamSession?.cache?.snapshot?.value?.segments?.isNotEmpty() == true)
        if (!retainCache) {
            streamSession?.let { retiringStream = it }
            streamSession?.cancel()
            streamSession = null
        }
        if (!ended && urlRetries < 1) {
            urlRetries++
            streamSession?.retry(urls[urlIndex])
            if (reload || streamSession?.isHls != true) startLoad(restoreTarget(), timeline.value.isLive)
        } else if (urlIndex + 1 < urls.size) {
            urlIndex++
            urlRetries = 0
            streamSession?.retry(urls[urlIndex])
            if (reload || streamSession?.isHls != true) startLoad(restoreTarget(), timeline.value.isLive)
        } else fail(message, ended)
    }

    private fun resetRetriesAfterPlayback() {
        if (state.value.isPlaying && loadedAt?.elapsedNow()?.inWholeSeconds?.let { it >= 30 } == true) {
            urlRetries = 0
            loadedAt = null
        }
    }

    private fun fail(message: String, ended: Boolean = false) {
        playbackTimeline.cancelSeek()
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false,
            isBuffering = false, isEnded = ended, error = message, pendingSeekPositionMs = null)
        closeEngine(suspended = false)
    }

    fun seekToMs(positionMs: Long) {
        val streams = streamSession?.takeIf { it.localReplayEnabled } ?: return
        val latest = streams.cache.snapshot.value.latestPlaybackPositionMs(liveBufferDurationMs) ?: return
        if (timeline.value.ranges.isEmpty()) return
        val live = positionMs >= latest
        val target = if (live) null else positionMs
        startLoad(target, live)
    }

    fun resumePlayback(atLatest: Boolean = false): Boolean {
        val streams = streamSession?.takeIf { it.localReplayEnabled } ?: return false
        if (disposed || !foreground || state.value.error != null || state.value.isEnded ||
            atLatest && state.value.isBroadcastEnded
        ) return false
        playIntentRevision++
        playbackTimeline.setPaused(false)
        mutableState.value = state.value.copy(playWhenReady = true, error = null, isEnded = false)
        val position = state.value.pendingSeekPositionMs ?: timeline.value.positionMs
        if (atLatest) startLoad()
        else if (loadJob?.isActive == true && state.value.isSeeking) applyPlayIntent()
        else if (streams.cache.snapshot.value.epoch != readerEpoch || !streams.cache.contains(position)) startLoad()
        else if (!ready) startLoad(restoreTarget(), timeline.value.isLive)
        else applyPlayIntent()
        return true
    }

    fun finishBroadcast() {
        if (disposed || state.value.isBroadcastEnded) return
        mutableState.value = state.value.copy(isBroadcastEnded = true, error = null)
        playbackTimeline.finish()
        val streams = streamSession
        val expected = sourceRevision
        scope.launch {
            streams?.finishCollection()
            if (disposed || sourceRevision != expected || streamSession !== streams) return@launch
            if (streams == null || localReplayEnabled && streams.cache.snapshot.value.segments.isEmpty() ||
                mutableBackend.value == null && !localReplayEnabled) finishReading()
        }
    }

    private fun finishReading() {
        stopPlaybackObservation()
        loadJob?.cancel()
        loadJob = null
        ready = false
        seekConfirmationJob?.cancel()
        seekConfirmationJob = null
        activeSeekRequest = 0
        playbackTimeline.cancelSeek()
        playbackTimeline.setPaused(true)
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false, isBuffering = false,
            isEnded = true, error = null, pendingSeekPositionMs = null)
        updateFrameRate()
    }

    fun pause() {
        if (disposed) return
        playIntentRevision++
        playbackTimeline.setPaused(true)
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false, isBuffering = state.value.isSeeking)
        updateFrameRate()
        if (localReplayEnabled) applyPlayIntent() else closeEngine(suspended = true)
    }

    private fun applyPlayIntent() {
        intentJob?.cancel()
        val engine = mutableBackend.value ?: return
        val expected = generation
        val intent = playIntentRevision
        val clock = playbackClock
        intentJob = launchCommand {
            val playing = state.value.playWhenReady && foreground && ready && !state.value.isSeeking
            try {
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!playing) withContext(boloMpvDispatcher) {
                    if (isCurrent(expected, engine) && playIntentRevision == intent) {
                        if (engine.pause(true) >= 0) clock?.setPaused(true)
                    }
                }
                if (playbackClock === clock) playbackObservation?.let { playbackObserver?.invoke(it) }
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                val active = engine.setAudioActive(playing)
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!active) {
                    fail("音频会话激活失败")
                    return@launchCommand
                }
                if (playing && ready) {
                    val result = withContext(boloMpvDispatcher) {
                        if (isCurrent(expected, engine) && playIntentRevision == intent)
                            engine.pause(false).also { if (it >= 0) clock?.setPaused(false) } else 0
                    }
                    if (isCurrent(expected, engine) && result < 0) retrySource("直播播放命令失败（$result）")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (isCurrent(expected, engine)) fail("直播播放失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun setVolume(percent: Int) {
        if (disposed) return
        volume = percent.coerceIn(0, 200)
        val engine = mutableBackend.value ?: return
        volumeJob?.cancel()
        val expected = generation
        volumeJob = launchCommand {
            withContext(boloMpvDispatcher) {
                if (isCurrent(expected, engine)) engine.volume(volume.toDouble())
            }
        }
    }

    fun setMergeAudioChannelsEnabled(enabled: Boolean) {
        if (disposed || mergeAudioChannelsEnabled == enabled) return
        mergeAudioChannelsEnabled = enabled
        mergeAudioRevision++
        mergeAudioJob?.cancel()
        infoRevision++
        lastInfoSample = null
        mutableInfo.value = info.value.copy(media = info.value.media.copy(audio = info.value.media.audio?.copy(
            outputChannelLayout = null, outputChannelCount = null, channelsMerged = null,
        )))
        val engine = mutableBackend.value ?: return
        if (!foreground) return
        val expected = generation
        mergeAudioJob = launchCommand { applyMergeAudioChannels(expected, engine) }
    }

    private suspend fun applyMergeAudioChannels(expected: Long, engine: BoloMpvBackend) {
        val revision = mergeAudioRevision
        val enabled = mergeAudioChannelsEnabled
        try {
            val result = withContext(boloMpvDispatcher) {
                if (isCurrent(expected, engine) && revision == mergeAudioRevision && foreground)
                    engine.mergeAudioChannels(enabled) else null
            }
            if (result != null && result < 0 && isCurrent(expected, engine) && revision == mergeAudioRevision)
                println("合并多声道配置失败（$result）")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(expected, engine) && revision == mergeAudioRevision)
                println("合并多声道配置失败：${error.message}")
        } finally {
            if (isCurrent(expected, engine) && revision == mergeAudioRevision) {
                infoRevision++
                lastInfoSample = null
            }
        }
    }

    fun setDynamicLoudnessSettings(enabled: Boolean, targetLufs: Double, rangeLu: Double, truePeakDbtp: Double) {
        val target = targetLufs.takeIf { it.isFinite() && it in -70.0..-5.0 } ?: -14.0
        val range = rangeLu.takeIf { it.isFinite() && it in 1.0..50.0 } ?: 11.0
        val peak = truePeakDbtp.takeIf { it.isFinite() && it in -9.0..0.0 } ?: -2.0
        if (disposed || dynamicLoudnessEnabled == enabled && dynamicLoudnessTargetLufs == target &&
            dynamicLoudnessRangeLu == range && dynamicLoudnessTruePeakDbtp == peak) return
        dynamicLoudnessEnabled = enabled
        dynamicLoudnessTargetLufs = target
        dynamicLoudnessRangeLu = range
        dynamicLoudnessTruePeakDbtp = peak
        loudnessRevision++
        loudnessJob?.cancel()
        updateLoudnessInfo(null)
        val engine = mutableBackend.value ?: return
        if (!foreground) return
        val expected = generation
        loudnessJob = launchCommand { applyDynamicLoudness(expected, engine) }
    }

    private fun BoloPlayerInfo.withLoudnessInfo(): BoloPlayerInfo = copy(
        audio = audio?.copy(
            dynamicLoudnessEnabled = dynamicLoudnessEnabled,
            loudnessConfigured = loudnessConfigured,
            loudnessGainDb = 0.0.takeIf { loudnessConfigured == true },
            dynamicLoudnessTargetLufs = dynamicLoudnessTargetLufs.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
            dynamicLoudnessRangeLu = dynamicLoudnessRangeLu.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
            dynamicLoudnessTruePeakDbtp = dynamicLoudnessTruePeakDbtp.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
        ),
    )

    private fun updateLoudnessInfo(configured: Boolean?) {
        loudnessConfigured = configured
        infoRevision++
        lastInfoSample = null
        mutableInfo.value = info.value.copy(media = info.value.media.withLoudnessInfo())
    }

    private suspend fun applyDynamicLoudness(expected: Long, engine: BoloMpvBackend) {
        val revision = loudnessRevision
        val enabled = dynamicLoudnessEnabled
        val target = dynamicLoudnessTargetLufs
        val range = dynamicLoudnessRangeLu
        val peak = dynamicLoudnessTruePeakDbtp
        updateLoudnessInfo(null)
        try {
            val result = withContext(boloMpvDispatcher) {
                if (isCurrent(expected, engine) && revision == loudnessRevision && foreground)
                    engine.loudness(0.0, enabled, target, range, peak) else null
            }
            if (result == null || !isCurrent(expected, engine) || revision != loudnessRevision || !foreground) return
            updateLoudnessInfo(result >= 0)
            if (result < 0) println("直播音量均衡配置失败（$result），已停用本次均衡")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 可选音效失败不打断播放；回退也必须核对代次，避免覆盖新配置。
            withContext(NonCancellable + boloMpvDispatcher) {
                if (isCurrent(expected, engine) && revision == loudnessRevision && foreground)
                    runCatching { engine.loudness(0.0, false) }
            }
            if (isCurrent(expected, engine) && revision == loudnessRevision && foreground) {
                updateLoudnessInfo(false)
                println("直播音量均衡配置失败：${error.message}")
            }
        }
    }

    fun setForeground(active: Boolean) {
        if (disposed || foreground == active) return
        foreground = active
        resetInfoSampling()
        if (!active) {
            pause()
            streamSession?.setCollecting(false)
            if (!ready || state.value.isSeeking) closeEngine(suspended = true)
        } else {
            streamSession?.takeIf { it.localReplayEnabled }?.setCollecting(true)
            if (streamSession == null && localReplayEnabled) startLoad()
            else mutableBackend.value?.let { engine ->
                mergeAudioJob?.cancel()
                val expected = generation
                mergeAudioJob = launchCommand { applyMergeAudioChannels(expected, engine) }
                loudnessJob?.cancel()
                loudnessJob = launchCommand { applyDynamicLoudness(expected, engine) }
            }
        }
        mutableState.value = state.value.copy(isPlaybackSuspended = !active || mutableBackend.value == null)
        updateFrameRate()
        applyVideoOutput()
    }

    fun setVideoOutputActive(active: Boolean) {
        if (disposed) return
        videoOutputActive = active
        updateFrameRate()
        applyVideoOutput()
    }

    private fun applyVideoOutput() {
        outputJob?.cancel()
        val engine = mutableBackend.value ?: return
        val expected = generation
        outputJob = launchCommand {
            try {
                if (foreground && videoOutputActive) withTimeout(8_000) { engine.awaitOutput() }
                withContext(boloMpvDispatcher) {
                    if (isCurrent(expected, engine)) engine.videoEnabled(foreground && videoOutputActive)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 输出错误由加载或平台宿主报告。 */ }
        }
    }

    private fun updateFrameRate() {
        mutableFrameRate.value = sourceFrameRate.takeIf {
            state.value.playWhenReady && !state.value.isEnded && !state.value.isPlaybackSuspended &&
                foreground && videoOutputActive
        } ?: 0f
    }

    private fun launchCommand(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            finally { coroutineContext[Job]?.let(commandJobs::remove) }
        }
        commandJobs.add(job)
        job.start()
        return job
    }

    internal override fun outputAttached() = setVideoOutputActive(true)

    internal override fun outputDetached(output: Any) {
        val engine = mutableBackend.value ?: return
        val previous = detachJob
        detachJob = scope.launch { previous?.join(); engine.detachOutput(output) }
        if (state.value.isSeeking) startLoad(restoreTarget(), timeline.value.isLive)
    }

    override fun rebuild() {
        if (disposed || stream == null) return
        val target = restoreTarget()
        closeEngine(suspended = true)
        if (foreground) startLoad(target, timeline.value.isLive)
    }

    fun releaseBackgroundResources() {
        if (!disposed && !foreground && !state.value.playWhenReady) closeEngine(suspended = true)
    }

    override fun release() {
        if (!disposed) closeEngine(suspended = true)
    }

    internal fun clearMedia() {
        if (!disposed) {
            sourceRevision++
            streamSession?.cancel()
            streamSession = null
            stream = null
            urls = emptyList()
            broadcastStart = null
            localReplayEnabled = false
            timelineJob?.cancel()
            playbackTimeline.reset()
            playbackTimeline.setStartTime(null)
            mutableInfo.value = LivePlayerInfo()
            closeEngine(suspended = true)
            mutableState.value = LivePlayerState()
        }
    }

    private fun closeEngine(suspended: Boolean) {
        stopPlaybackObservation()
        generation++
        seekConfirmationJob?.cancel()
        seekConfirmationJob = null
        activeSeekRequest = 0
        if (!localReplayEnabled) {
            streamSession?.let { retiringStream = it }
            streamSession?.cancel()
            streamSession = null
        }
        resetInfoSampling(clearMedia = true)
        reader?.cancel()
        reader = null
        ready = false
        mutableFrameRate.value = 0f
        val loading = loadJob
        loadJob = null
        loading?.cancel()
        val events = eventsJob
        eventsJob = null
        events?.cancel()
        val commands = commandJobs.toList()
        commands.forEach { it.cancel() }
        intentJob = null
        volumeJob = null
        mergeAudioJob = null
        loudnessJob = null
        outputJob = null
        val detach = detachJob
        detachJob = null
        val engine = mutableBackend.value
        mutableBackend.value = null
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = false, isPlaybackSuspended = suspended)
        val closing = releaseJob
        releaseJob = scope.launch {
            try {
                withContext(NonCancellable) {
                    loading?.join()
                    events?.join()
                    commands.forEach { it.join() }
                    detach?.join()
                    closing?.join()
                    awaitPlaybackObservation()
                    if (engine != null) {
                        try {
                            withContext(boloMpvDispatcher) { engine.stop() }
                        } finally {
                            try { engine.unbind() }
                            finally {
                                try { withContext(boloMpvDispatcher) { engine.destroy() } }
                                finally { engine.setAudioActive(false) }
                            }
                        }
                    }
                }
            } finally {
                if (disposed) scope.cancel()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        sourceRevision++
        streamSession?.cancel()
        streamSession = null
        timelineJob?.cancel()
        mutableState.value = state.value.copy(playWhenReady = false)
        closeEngine(suspended = false)
    }
}
