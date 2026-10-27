package tv.hsrui.bolo.livePlayer.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import tv.hsrui.bolo.livePlayer.session.LivePlaybackTimeline
import tv.hsrui.bolo.livePlayer.session.LiveCachedStreamSource
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.setting.live.LiveSettings
import tv.hsrui.bolo.player.base.BoloMpvBackend
import tv.hsrui.bolo.player.base.BoloMpvEvent
import tv.hsrui.bolo.player.base.BoloPlayerDiagnosticsSampler
import tv.hsrui.bolo.player.base.BoloPlayerInfo
import tv.hsrui.bolo.player.base.BoloPlayerOutputController
import tv.hsrui.bolo.player.base.boloMpvDispatcher
import tv.hsrui.bolo.player.base.withoutDynamicValues
import tv.hsrui.bolo.livePlayer.session.LiveStreamSession
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
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
    private var ready = false
    private var foreground = true
    private var videoOutputActive = true
    private var disposed = false
    private var volume = 100
    private var sourceFrameRate = 0f
    private var loadJob: Job? = null
    private var eventsJob: Job? = null
    private var intentJob: Job? = null
    private var volumeJob: Job? = null
    private var outputJob: Job? = null
    private var detachJob: Job? = null
    private val commandJobs = mutableSetOf<Job>()
    private var releaseJob: Job? = null
    var playIntentRevision: Long = 0L
        private set

    init {
        scope.launch {
            while (isActive) {
                if (!localReplayEnabled) playbackTimeline.observeInstant(state.value.playWhenReady)
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
        val expected = ++generation
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
            isPlaybackSuspended = !foreground, isEnded = false, error = null)
        val closing = releaseJob
        loadJob = scope.launch {
            try {
                previousLoad?.join()
                retiringStream?.awaitClosed()
                retiringStream = null
                previousCommands.forEach { it.join() }
                closing?.join()
                if (!isCurrent(expected)) return@launch
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
                            startEvents(created)
                            created
                        }
                    } ?: return@launch
                }
                val current = engine
                if (foreground && videoOutputActive) withTimeout(8_000) { current.awaitOutput() }
                if (!isCurrent(expected, current)) return@launch
                check(current.setAudioActive(state.value.playWhenReady)) { "音频会话激活失败" }
                if (!isCurrent(expected, current)) return@launch
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
                                if (collecting.localReplayEnabled) snapshot else snapshot.copy(ranges = emptyList()),
                                collecting.playlist.value,
                            )
                            val position = timeline.value.positionMs
                            if (ready && state.value.playWhenReady && snapshot.ranges.isNotEmpty() &&
                                (snapshot.epoch != readerEpoch || collecting.localReplayEnabled && position != null && position < snapshot.ranges.first().startMs)
                            ) startLoad()
                        }
                    }
                }
                streams.prepare()
                if (!isCurrent(expected, current)) return@launch
                val source: BoloMpvStreamSource
                val uri: String
                if (streams.isHls) {
                    streams.cache.awaitSegment()
                    val segment = checkNotNull(streams.cache.select(targetMs))
                    if (!streams.localReplayEnabled) streams.cache.consume(segment.id - 1)
                    val cached = LiveCachedStreamSource(streams.cache, segment, expected, checkNotNull(stream).formatName) { next ->
                        scope.launch {
                            if (isCurrent(expected, current)) {
                                readerInvalidated = true
                                if (state.value.playWhenReady) startLoad(next) else ready = false
                            }
                        }
                    }
                    source = cached
                    uri = cached.uri
                    readerStartMs = cached.startMs
                    readerEpoch = streams.cache.snapshot.value.epoch
                    playbackTimeline.seek(cached.startMs, live)
                } else {
                    source = streams
                    uri = streams.uri
                    readerStartMs = null
                }
                reader = source
                val gain = volume.toDouble()
                val visible = foreground && videoOutputActive
                val result = withContext(boloMpvDispatcher) {
                    if (!isCurrent(expected, current)) return@withContext 0
                    check(current.videoEnabled(visible) >= 0) { "视频轨配置失败" }
                    check(current.volume(gain) >= 0) { "音量配置失败" }
                    check(current.speed(1.0) >= 0) { "直播播放速度配置失败" }
                    current.loadLive(source, uri, expected, bufferingEnabled = !playbackBufferDisabled)
                }
                if (!isCurrent(expected, current)) return@launch
                if (result < 0) retrySource("直播流打开失败（$result）")
                else {
                    delay(20_000)
                    if (isCurrent(expected, current) && !ready) retrySource("直播流加载超时")
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

    private fun startEvents(engine: BoloMpvBackend) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            var samples = 0
            while (isActive && !disposed && mutableBackend.value === engine) {
                val events = withContext(boloMpvDispatcher) {
                    buildList {
                        repeat(128) { add(engine.poll() ?: return@buildList) }
                    }
                }
                if (mutableBackend.value !== engine || disposed) break
                for (event in events) {
                    if (event.generation == generation && mutableBackend.value === engine) handleEvent(event)
                }
                if (++samples >= 50 && ready && !infoPanelVisible && mutableBackend.value === engine) {
                    samples = 0
                    val expected = generation
                    val snapshot = withContext(boloMpvDispatcher) { engine.info(false) }
                    if (snapshot != null && snapshot.generation == expected && isCurrent(expected, engine)) {
                        sourceFrameRate = snapshot.info.video.fps?.takeIf { it.isFinite() && it > 0 }
                            ?.toFloat() ?: 0f
                        updateFrameRate()
                    }
                }
                delay(20)
            }
        }
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
        var updated = info.value.copy(media = media)
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
            BoloMpvEvent.Position -> if (event.value.isFinite() && event.value >= 0 && state.value.playWhenReady) {
                var position = (event.value * 1000).toLong()
                if (stream?.formatName == "ts") {
                    val origin = readerStartMs ?: position
                    val wrap = (1L shl 33) / 90
                    while (position < origin - wrap / 2) position += wrap
                    while (position > origin + wrap / 2) position -= wrap
                }
                if (readerStartMs?.let { position >= it - 250 } != false) playbackTimeline.observe(position)
            }
            BoloMpvEvent.Loaded -> {
                ready = true
                loadedAt = TimeSource.Monotonic.markNow()
                loadJob?.cancel()
                loadJob = null
                mutableState.value = state.value.copy(isBuffering = state.value.playWhenReady)
                applyPlayIntent()
            }
            BoloMpvEvent.Paused -> if (ready) {
                mutableState.value = state.value.copy(isPlaying = event.value == 0.0 && state.value.playWhenReady &&
                    !state.value.isBuffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Buffering -> if (ready) {
                val buffering = event.value != 0.0 && state.value.playWhenReady
                mutableState.value = state.value.copy(isBuffering = buffering,
                    isPlaying = state.value.playWhenReady && !buffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Restart -> if (ready && state.value.playWhenReady) {
                mutableState.value = state.value.copy(isBuffering = false,
                    isPlaying = !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Eof -> if (foreground && event.value != 0.0) {
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
            if (reload || streamSession?.isHls != true) startLoad(timeline.value.positionMs, timeline.value.isLive)
        } else if (urlIndex + 1 < urls.size) {
            urlIndex++
            urlRetries = 0
            streamSession?.retry(urls[urlIndex])
            if (reload || streamSession?.isHls != true) startLoad(timeline.value.positionMs, timeline.value.isLive)
        } else fail(message, ended)
    }

    private fun resetRetriesAfterPlayback() {
        if (state.value.isPlaying && loadedAt?.elapsedNow()?.inWholeSeconds?.let { it >= 30 } == true) {
            urlRetries = 0
            loadedAt = null
        }
    }

    private fun fail(message: String, ended: Boolean = false) {
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false,
            isBuffering = false, isEnded = ended, error = message)
        closeEngine(suspended = false)
    }

    fun seekToMs(positionMs: Long) {
        val streams = streamSession?.takeIf { it.localReplayEnabled } ?: return
        val latest = streams.cache.snapshot.value.segments.lastOrNull()?.media?.endMs ?: return
        if (timeline.value.ranges.isEmpty()) return
        val live = positionMs >= latest
        val target = if (live) null else streams.cache.select(positionMs)?.media?.startMs
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
        val position = timeline.value.positionMs
        if (atLatest || streams.cache.snapshot.value.epoch != readerEpoch || !streams.cache.contains(position)) startLoad()
        else if (!ready) startLoad(position, timeline.value.isLive)
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
        loadJob?.cancel()
        loadJob = null
        ready = false
        playbackTimeline.setPaused(true)
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false, isBuffering = false,
            isEnded = true, error = null)
        updateFrameRate()
    }

    fun pause() {
        if (disposed) return
        playIntentRevision++
        playbackTimeline.setPaused(true)
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false, isBuffering = false)
        updateFrameRate()
        if (localReplayEnabled) applyPlayIntent() else closeEngine(suspended = true)
    }

    private fun applyPlayIntent() {
        intentJob?.cancel()
        val engine = mutableBackend.value ?: return
        val expected = generation
        val intent = playIntentRevision
        intentJob = launchCommand {
            val playing = state.value.playWhenReady && foreground
            try {
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!playing) withContext(boloMpvDispatcher) {
                    if (isCurrent(expected, engine) && playIntentRevision == intent) engine.pause(true)
                }
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                val active = engine.setAudioActive(playing)
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!active) {
                    fail("音频会话激活失败")
                    return@launchCommand
                }
                if (playing && ready) {
                    val result = withContext(boloMpvDispatcher) {
                        if (isCurrent(expected, engine) && playIntentRevision == intent) engine.pause(false) else 0
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

    fun setForeground(active: Boolean) {
        if (disposed || foreground == active) return
        foreground = active
        resetInfoSampling()
        if (!active) {
            pause()
            streamSession?.setCollecting(false)
            if (!ready) closeEngine(suspended = true)
        } else {
            streamSession?.takeIf { it.localReplayEnabled }?.setCollecting(true)
            if (streamSession == null && localReplayEnabled) startLoad()
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
    }

    override fun rebuild() {
        if (disposed || stream == null) return
        closeEngine(suspended = true)
        if (foreground) startLoad(timeline.value.positionMs, timeline.value.isLive)
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
        generation++
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
