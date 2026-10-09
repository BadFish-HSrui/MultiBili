package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tv.hsrui.bolo.boloSetting.PlaybackLoudnessMode
import tv.hsrui.network.feature.player.BiliDashObject
import tv.hsrui.network.feature.player.VideoLoudnessData
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal val boloMpvDispatcher = Dispatchers.Default.limitedParallelism(1)

internal data class BoloPlayerSource(
    val video: BiliDashObject,
    val audio: BiliDashObject?,
    val loudness: VideoLoudnessData?,
    val sortCdn: Boolean,
)

/** 共享播放策略。状态只在 Main 更新，普通 native 调用与渲染上下文分离。 */
class BoloPlayerController(
    private val autoPlay: Boolean = true,
    private val onError: (BoloPlayerError) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BoloPlayerState())
    val state: StateFlow<BoloPlayerState> = mutableState.asStateFlow()
    internal var onPlaybackObservation: ((BoloPlaybackObservation) -> Unit)? = null
    private val playbackClock = BoloPlaybackClock()
    internal val playbackObservation: BoloPlaybackObservation?
        get() = playbackClock.latest.value?.takeIf { it.generation == generation }

    private fun publishPlaybackObservation() {
        playbackObservation?.let { onPlaybackObservation?.invoke(it) }
    }

    private val mutableInfo = MutableStateFlow(BoloPlayerInfo())
    val info: StateFlow<BoloPlayerInfo> = mutableInfo.asStateFlow()
    private var mediaInfo = BoloPlayerInfo()
    private var infoPanelVisible = false
    private var lastInfoSample: TimeMark? = null
    private val diagnosticsSampler = BoloPlayerDiagnosticsSampler()
    private var diagnosticsRevision = 0L
    private val mutableBackend = MutableStateFlow<BoloMpvBackend?>(null)
    internal val backend: StateFlow<BoloMpvBackend?> = mutableBackend.asStateFlow()
    private val coordinator = BoloPlayerSeekCoordinator()
    private var videoUrls = emptyList<String>()
    private var audioUrls = emptyList<String>()
    private var videoIndex = 0
    private var audioIndex = 0
    private var urlExpirations = emptyMap<String, Long?>()
    internal var onRefreshSource: (suspend () -> BoloPlayerSource)? = null
    private var sourceRefreshJob: Job? = null
    private var sourceRevision = 0L
    private var sourceRefreshAttempted = false
    private var sourceNeedsRefresh = false
    private var advancingSince: TimeMark? = null
    private var lastAdvance: TimeMark? = null
    private var endedBeforeSeek = false
    private var generation = 0L
    private var lifecycleRevision = 0L
    private var loadJob: Job? = null
    private var eventsJob: Job? = null
    private var seekJob: Job? = null
    private var speedJob: Job? = null
    private var intentJob: Job? = null
    private var backgroundJob: Job? = null
    private var rebuildJob: Job? = null
    private var mergeAudioJob: Job? = null
    private var loudnessJob: Job? = null
    private var loudnessMode = PlaybackLoudnessMode.Standard
    private var dynamicLoudnessEnabled = false
    private var dynamicLoudnessTargetLufs = -14.0
    private var dynamicLoudnessRangeLu = 11.0
    private var dynamicLoudnessTruePeakDbtp = -2.0
    private var mediaLoudness: VideoLoudnessData? = null
    private var loudnessRevision = 0L
    private var loudnessConfigured: Boolean? = null
    private var appliedLoudnessGainDb: Double? = null
    private val engineMutex = Mutex()
    private var mergeAudioChannelsEnabled = false
    private var mergeAudioRevision = 0L
    private var ready = false
    private var seekabilityKnown = false
    private var disposed = false
    private var inBackground = false
    internal var backgroundPlaybackEnabled = false
        private set
    private var videoOutputActive = true
    private var resourcesSuspended = false
    internal var playIntentRevision = 0L
        private set
    private var playWhenReady = autoPlay
        set(value) {
            if (field != value) resetDiagnostics()
            field = value
            mutableState.value = state.value.copy(playWhenReady = value)
        }
    private var resumeEnabled = false
    private var resumeEligible = false
    private var restoring = false
    private var recoveryAttempted = false
    private var volumeGain = 100
    private var speedRevision = 0L
    private var nativeSeeking = false
        set(value) {
            if (field != value) resetDiagnostics()
            field = value
        }
    private var requestSequence = 0L
    private var activeSeekRequest = 0L

    internal suspend fun loadMedia(
        video: BiliDashObject,
        audio: BiliDashObject?,
        start: Long,
        loudness: VideoLoudnessData?,
        sortCdn: Boolean,
    ) {
        withContext(Dispatchers.Main.immediate) {
            if (disposed) return@withContext
            if (sourceRefreshJob != null) {
                mutableState.value = state.value.copy(pendingSeekPositionMs = start.coerceAtLeast(0L))
                return@withContext
            }
            val intent = playWhenReady
            cancelSourceRefresh()
            playWhenReady = intent
            sourceRefreshAttempted = false
            endedBeforeSeek = state.value.isEnded
            cancelRebuild()
            setSource(BoloPlayerSource(video, audio, loudness, sortCdn))
            resetInfo()
            recoveryAttempted = false
            val duration = maxOf(video.duration, audio?.duration ?: 0L).coerceIn(0L, Long.MAX_VALUE / 1000) * 1000
            mutableState.value = BoloPlayerState(isPlaybackSuspended = resourcesSuspended, playWhenReady = playWhenReady,
                durationMs = duration, currentPositionMs = start.coerceAtLeast(0), playbackSpeed = state.value.playbackSpeed)
            if (videoUrls.isEmpty() || (audio != null && audioUrls.isEmpty())) fail(BoloPlayerError.NetworkError("播放地址为空"))
            else if (!resourcesSuspended) startLoad(start.coerceAtLeast(0))
            else {
                loadJob?.cancel()
                seekJob?.cancel()
                ready = false
                publishPlaybackObservation()
                generation = coordinator.onMediaChanged()
            }
        }
    }

    private fun setSource(source: BoloPlayerSource) {
        loudnessJob?.cancel()
        loudnessRevision++
        mediaLoudness = source.loudness
        val video = source.video
        val audio = source.audio
        videoUrls = video.getUrls(sortCDN = source.sortCdn)
        audioUrls = audio?.getUrls(sortCDN = source.sortCdn).orEmpty()
        urlExpirations = video.urlExpiresAtEpochSeconds + audio?.urlExpiresAtEpochSeconds.orEmpty()
        videoIndex = 0
        audioIndex = 0
        mediaInfo = BoloPlayerInfo(
            video = BoloPlayerVideoInfo(
                nominalBitrateBps = video.bandwidth.takeIf { it > 0 },
                width = video.width.takeIf { it > 0 },
                height = video.height.takeIf { it > 0 },
                fps = parsePlayerFrameRate(video.frameRate),
            ),
            audio = audio?.let { BoloPlayerAudioInfo(nominalBitrateBps = it.bandwidth.takeIf { bitrate -> bitrate > 0 }) },
        )
    }

    private fun isExpired(url: String?): Boolean = urlExpirations[url]?.let {
        Clock.System.now().epochSeconds >= it
    } == true

    private fun needsSourceRefresh(): Boolean = sourceNeedsRefresh ||
        isExpired(videoUrls.getOrNull(videoIndex)) || isExpired(audioUrls.getOrNull(audioIndex))

    private fun beginSourceRecovery(position: Long) {
        if (disposed || resourcesSuspended) return
        mutableState.value = state.value.copy(pendingSeekPositionMs = position, isPlaying = false,
            isBuffering = true, isEnded = false, hasConfirmedPosition = false)
        if (sourceRefreshJob != null) return
        val refresh = onRefreshSource
        if (sourceRefreshAttempted || refresh == null) {
            failSourceRecovery()
            return
        }
        sourceRefreshAttempted = true
        sourceNeedsRefresh = true
        advancingSince = null
        cancelRebuild()
        loadJob?.cancel()
        seekJob?.cancel()
        ready = false
        publishPlaybackObservation()
        generation = coordinator.onMediaChanged()
        applyPlayIntent()
        val revision = ++sourceRevision
        sourceRefreshJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val source = refresh()
                coroutineContext.ensureActive()
                if (revision != sourceRevision || disposed) return@launch
                setSource(source)
                sourceNeedsRefresh = false
                sourceRefreshJob = null
                if (videoUrls.isEmpty() || (source.audio != null && audioUrls.isEmpty())) failSourceRecovery()
                else if (!resourcesSuspended) startLoad(state.value.displayPositionMs)
            } catch (error: CancellationException) {
                if (revision == sourceRevision) cancelSourceRefresh()
                throw error
            } catch (error: Exception) {
                if (revision == sourceRevision && !disposed) failSourceRecovery(error.message)
            } finally {
                if (revision == sourceRevision) sourceRefreshJob = null
            }
        }
        sourceRefreshJob?.start()
    }

    private fun failSourceRecovery(message: String? = null) {
        sourceNeedsRefresh = true
        mutableState.value = state.value.copy(isEnded = endedBeforeSeek || state.value.isEnded)
        fail(BoloPlayerError.NetworkError(message ?: "播放地址刷新失败，请重试"))
    }

    internal fun cancelSourceRefresh(clearSource: Boolean = false) {
        sourceRevision++
        val interrupted = sourceRefreshJob != null || (sourceRefreshAttempted && state.value.isSeeking)
        sourceRefreshJob?.cancel()
        sourceRefreshJob = null
        if (!interrupted && !clearSource) return
        sourceNeedsRefresh = true
        sourceRefreshAttempted = false
        loadJob?.cancel()
        seekJob?.cancel()
        ready = false
        publishPlaybackObservation()
        generation = coordinator.onMediaChanged()
        playWhenReady = false
        mutableState.value = state.value.copy(pendingSeekPositionMs = null, isPlaying = false,
            isBuffering = false, isEnded = endedBeforeSeek || state.value.isEnded, hasConfirmedPosition = false)
        if (clearSource) {
            resumeEligible = false
            resourcesSuspended = false
            videoUrls = emptyList()
            audioUrls = emptyList()
            sourceNeedsRefresh = false
            endedBeforeSeek = false
            mutableState.value = state.value.copy(isEnded = false)
        }
        applyPlayIntent()
    }

    private fun allowSourceRecovery() {
        if (sourceRefreshJob == null) sourceRefreshAttempted = false
        advancingSince = null
    }

    private fun startLoad(position: Long) {
        if (disposed || resourcesSuspended) return
        backgroundJob?.cancel()
        if (sourceRefreshJob != null || needsSourceRefresh()) {
            beginSourceRecovery(position)
            return
        }
        advancingSince = null
        resetInfo()
        loudnessJob?.cancel()
        loadJob?.cancel()
        seekJob?.cancel()
        ready = false
        seekabilityKnown = false
        nativeSeeking = false
        activeSeekRequest = 0
        intentJob?.cancel()
        publishPlaybackObservation()
        generation = coordinator.onMediaChanged()
        val expected = generation
        mutableState.value = state.value.copy(isBuffering = true, isPlaying = false, isEnded = false,
            isSeekable = false, hasConfirmedPosition = false, pendingSeekPositionMs = position)
        loadJob = scope.launch {
            try {
                val current = engineMutex.withLock {
                    if (!isActive || disposed || generation != expected) return@launch
                    var engine = mutableBackend.value
                    if (engine == null) {
                        // 创建与旧实例停止串行；创建期间的取消不能遗失已分配的 native handle。
                        engine = withContext(NonCancellable + boloMpvDispatcher) { BoloMpvBackend() }
                        if (!isActive || disposed || generation != expected) {
                            withContext(NonCancellable + boloMpvDispatcher) { engine.destroy() }
                            return@launch
                        }
                        mutableBackend.value = engine
                        startEvents(engine)
                    }
                    engine
                }
                if (!inBackground && videoOutputActive) withTimeout(8_000) { current.awaitOutput() }
                withContext(boloMpvDispatcher) {
                    check(current.videoEnabled(!inBackground && videoOutputActive) >= 0) { "视频轨配置失败" }
                }
                if (generation != expected || resourcesSuspended || disposed) return@launch
                check(current.setAudioActive(playWhenReady)) { "音频会话激活失败" }
                applyLoudness(current)
                if (generation != expected || resourcesSuspended || disposed) return@launch
                if (needsSourceRefresh()) { beginSourceRecovery(state.value.displayPositionMs); return@launch }
                val speed = state.value.playbackSpeed.toDouble()
                val volume = volumeGain.toDouble()
                val video = videoUrls[videoIndex]
                val audio = audioUrls.getOrNull(audioIndex)
                val mergeChannels = mergeAudioChannelsEnabled
                val result = withContext(boloMpvDispatcher) {
                    val mergeResult = current.mergeAudioChannels(mergeChannels)
                    check(mergeResult >= 0) { "合并多声道配置失败（$mergeResult）" }
                    current.volume(volume)
                    current.speed(speed)
                    playbackClock.begin(expected, speed)
                    current.load(video, audio, position / 1000.0, expected, videoPlayHeaders.getValue("User-Agent"), videoPlayHeaders.getValue("Referer"))
                }
                if (generation != expected) return@launch
                if (result < 0) retrySource(result, 0) else {
                    delay(20_000)
                    if (generation == expected && !ready) retrySource(-1, 0)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == expected) fail(BoloPlayerError.DecoderError("视频输出准备超时", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (generation == expected) fail(BoloPlayerError.DecoderError("播放器初始化失败：${e.message}", e))
            }
        }
    }

    private fun startEvents(engine: BoloMpvBackend) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            val events = Channel<BoloMpvEvent>(Channel.UNLIMITED)
            // 原生采样不等待 Main 消费；暂停/缓冲的耗时边界不会被 UI 卡顿推迟。
            val polling = launch(boloMpvDispatcher) {
                try {
                    while (isActive && mutableBackend.value === engine) {
                        for (index in 0 until 128) {
                            val event = engine.poll() ?: break
                            playbackClock.observe(event)
                            events.send(event)
                        }
                        playbackClock.sample()
                        delay(20)
                    }
                } finally { events.close() }
            }
            val infoSampling = launch {
                while (isActive && mutableBackend.value === engine) {
                    sampleInfo(engine)
                    delay(20)
                }
            }
            try {
                for (event in events) {
                    if (mutableBackend.value !== engine) break
                    if (event.generation != generation || disposed) continue
                    val observed = handleEvent(event)
                    if (event.generation != generation || disposed) continue
                    event.observation?.let { observation ->
                        onPlaybackObservation?.invoke(if (observed) observation.withPosition(state.value.currentPositionMs) else observation)
                    }
                }
            } finally { polling.cancel(); infoSampling.cancel(); events.cancel() }
        }
    }

    internal fun setInfoPanelVisible(visible: Boolean) {
        scope.launch {
            if (disposed) return@launch
            infoPanelVisible = visible
            resetDiagnostics()
            lastInfoSample = null
            mutableInfo.value = info.value.withoutDynamicValues()
        }
    }

    private fun resetInfo() {
        resetDiagnostics()
        lastInfoSample = null
        loudnessConfigured = null
        appliedLoudnessGainDb = null
        mutableInfo.value = mediaInfo.withLoudnessInfo()
    }

    private fun resetDiagnostics() {
        diagnosticsRevision++
        diagnosticsSampler.reset()
        mutableInfo.value = info.value.copy(
            decoderDroppedFramesPerSecond = null,
            outputDroppedFramesPerSecond = null,
            avSyncDifferenceMs = null,
        )
    }

    private fun BoloPlayerInfo.withLoudnessInfo(): BoloPlayerInfo = copy(
        audio = audio?.copy(
            loudnessMode = loudnessMode,
            dynamicLoudnessEnabled = dynamicLoudnessEnabled,
            loudnessConfigured = loudnessConfigured,
            loudnessData = mediaLoudness,
            loudnessGainDb = appliedLoudnessGainDb,
            dynamicLoudnessTargetLufs = dynamicLoudnessTargetLufs.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
            dynamicLoudnessRangeLu = dynamicLoudnessRangeLu.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
            dynamicLoudnessTruePeakDbtp = dynamicLoudnessTruePeakDbtp.takeIf { loudnessConfigured == true && dynamicLoudnessEnabled },
        ),
    )

    private fun updateLoudnessInfo(configured: Boolean?, gainDb: Double? = null) {
        loudnessConfigured = configured
        appliedLoudnessGainDb = gainDb
        mutableInfo.value = info.value.withLoudnessInfo()
    }

    private suspend fun sampleInfo(engine: BoloMpvBackend) {
        if (disposed || inBackground || mutableBackend.value !== engine ||
            (!infoPanelVisible && !state.value.isBuffering)) {
            if (lastInfoSample != null) {
                resetDiagnostics()
                lastInfoSample = null
                mutableInfo.value = info.value.withoutDynamicValues()
            }
            return
        }
        if (lastInfoSample?.elapsedNow()?.inWholeMilliseconds?.let { it < 500 } == true) return
        lastInfoSample = TimeSource.Monotonic.markNow()
        val expected = generation
        val audioRevision = mergeAudioRevision
        val diagnosticsVersion = diagnosticsRevision
        val includeDiagnostics = infoPanelVisible
        val snapshot = try {
            withContext(boloMpvDispatcher) { engine.info(includeDiagnostics) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (disposed || inBackground || resourcesSuspended || generation != expected || mutableBackend.value !== engine ||
            audioRevision != mergeAudioRevision || diagnosticsVersion != diagnosticsRevision) return
        if (!infoPanelVisible && !state.value.isBuffering) {
            mutableInfo.value = info.value.withoutDynamicValues()
            return
        }
        if (snapshot == null || snapshot.instance !== engine || snapshot.generation != expected) {
            resetDiagnostics()
            mutableInfo.value = mediaInfo.withLoudnessInfo()
            return
        }
        val seeking = state.value.isSeeking
        val diagnosticsActive = includeDiagnostics && ready && videoOutputActive && playWhenReady &&
            state.value.hasConfirmedPosition && !seeking && !nativeSeeking &&
            !state.value.isEnded && !state.value.isRebuilding
        val native = if (diagnosticsActive) {
            diagnosticsSampler.sample(snapshot.info)
        } else {
            diagnosticsSampler.reset()
            snapshot.info
        }
        mutableInfo.value = native.copy(
            avSyncDifferenceMs = native.avSyncDifferenceMs.takeIf { diagnosticsActive && state.value.isPlaying },
            video = native.video.copy(
                nominalBitrateBps = mediaInfo.video.nominalBitrateBps,
                width = native.video.width ?: mediaInfo.video.width,
                height = native.video.height ?: mediaInfo.video.height,
                fps = native.video.fps ?: mediaInfo.video.fps,
                playbackBitrateBps = native.video.playbackBitrateBps.takeUnless { seeking },
                fragmentIndex = native.video.fragmentIndex.takeUnless { seeking },
            ),
            audio = native.audio?.copy(
                nominalBitrateBps = mediaInfo.audio?.nominalBitrateBps,
                playbackBitrateBps = native.audio.playbackBitrateBps.takeUnless { seeking },
                fragmentIndex = native.audio.fragmentIndex.takeUnless { seeking },
                outputChannelLayout = native.audio.outputChannelLayout.takeUnless { mergeAudioJob?.isActive == true },
                outputChannelCount = native.audio.outputChannelCount.takeUnless { mergeAudioJob?.isActive == true },
                channelsMerged = native.audio.channelsMerged.takeUnless { mergeAudioJob?.isActive == true },
            ) ?: mediaInfo.audio,
            downloadBytesPerSecond = native.downloadBytesPerSecond.takeIf {
                native.video.downloadBytesPerSecond != null &&
                    (mediaInfo.audio == null || native.audio?.downloadBytesPerSecond != null)
            },
        ).withLoudnessInfo()
    }

    private fun handleEvent(event: BoloMpvEvent): Boolean {
        when (event.type) {
            BoloMpvEvent.Loaded -> {
                resetDiagnostics()
                ready = true
                loadJob?.cancel()
                val target = state.value.pendingSeekPositionMs ?: state.value.currentPositionMs
                coordinator.requestSeek(target)
                mutableState.value = state.value.copy(pendingSeekPositionMs = target, hasConfirmedPosition = false)
                submitSeek()
            }
            BoloMpvEvent.Position -> return observePosition(event.value, event.request)
            BoloMpvEvent.Restart -> if (!state.value.isSeeking) return observePosition(event.value, event.request)
            BoloMpvEvent.Duration -> secondsToMs(event.value)?.takeIf { it > 0 }?.let {
                mutableState.value = state.value.copy(durationMs = it)
            }
            BoloMpvEvent.Seekable -> {
                seekabilityKnown = true
                mutableState.value = state.value.copy(isSeekable = event.value != 0.0)
                if (ready && state.value.isSeeking) submitSeek()
            }
            BoloMpvEvent.Buffering -> if (ready && !resourcesSuspended) {
                val buffering = event.value != 0.0 && (playWhenReady || state.value.isSeeking)
                mutableState.value = state.value.copy(isBuffering = buffering,
                    isPlaying = playWhenReady && !buffering && !state.value.isSeeking && !state.value.isEnded)
            }
            BoloMpvEvent.Paused -> if (ready && !resourcesSuspended) {
                if (event.value != 0.0) resetDiagnostics()
                mutableState.value = state.value.copy(isPlaying = event.value == 0.0 && playWhenReady &&
                    !state.value.isBuffering && !state.value.isSeeking && !state.value.isEnded)
            }
            BoloMpvEvent.Seeking -> nativeSeeking = event.value != 0.0
            BoloMpvEvent.Eof -> if (ready && !resourcesSuspended && !state.value.isSeeking && event.value != 0.0) {
                endedBeforeSeek = true
                advancingSince = null
                coordinator.cancelCurrentSeek()
                seekJob?.cancel()
                playWhenReady = false
                restoring = false
                mutableState.value = state.value.copy(isEnded = true, isPlaying = false, isBuffering = false, isRebuilding = false,
                    isPlaybackSuspended = false, pendingSeekPositionMs = null,
                    currentPositionMs = state.value.durationMs.takeIf { it > 0 } ?: state.value.currentPositionMs)
                applyPlayIntent()
                scheduleBackgroundRelease()
            }
            BoloMpvEvent.SeekReply -> if (event.request == activeSeekRequest && event.error < 0) failSeek("跳转命令失败（${event.error}）")
            BoloMpvEvent.Error -> retrySource(event.error, event.value.toInt())
            BoloMpvEvent.Overflow -> fail(BoloPlayerError.UnknownError("播放器事件队列溢出，请重新加载"))
        }
        return false
    }

    private fun observePosition(seconds: Double, request: Long): Boolean {
        val position = secondsToMs(seconds) ?: return false
        if (!ready || resourcesSuspended) return false
        if (state.value.isSeeking && request != activeSeekRequest) return false
        if (!state.value.isSeeking && nativeSeeking) return false
        // UI 在 seek/缓冲期间会标为未播放，不能据此忽略原生继续前进的时间。
        if (!coordinator.acceptObservedPosition(position, playWhenReady && !state.value.isEnded, state.value.playbackSpeed)) return false
        val wasPending = state.value.isSeeking
        if (sourceRefreshAttempted) {
            val playing = !wasPending && state.value.isPlaying && !state.value.isBuffering
            if (!playing || position < state.value.currentPositionMs ||
                lastAdvance?.elapsedNow()?.inWholeMilliseconds?.let { it >= 2_000L } == true) advancingSince = null
            if (playing && position > state.value.currentPositionMs) {
                if (advancingSince == null) advancingSince = TimeSource.Monotonic.markNow()
                else if (advancingSince!!.elapsedNow().inWholeMilliseconds >= 10_000L) sourceRefreshAttempted = false
                lastAdvance = TimeSource.Monotonic.markNow()
            }
        }
        mutableState.value = state.value.copy(currentPositionMs = position,
            pendingSeekPositionMs = coordinator.pendingPositionMs, hasConfirmedPosition = true)
        if (wasPending && !state.value.isSeeking) {
            endedBeforeSeek = false
            seekJob?.cancel()
            restoring = false
            recoveryAttempted = false
            mutableState.value = state.value.copy(isPlaybackSuspended = false, isBuffering = false, isRebuilding = false)
            applyPlayIntent()
            scheduleBackgroundRelease()
        }
        return true
    }

    private fun retrySource(error: Int, failedTrack: Int) {
        if (resourcesSuspended || disposed) return
        if (sourceRefreshJob != null) return
        val position = state.value.displayPositionMs
        val nextVideo = (videoIndex + 1 until videoUrls.size).firstOrNull { !isExpired(videoUrls[it]) }
        val nextAudio = (audioIndex + 1 until audioUrls.size).firstOrNull { !isExpired(audioUrls[it]) }
        if (failedTrack == 2 && nextAudio != null) audioIndex = nextAudio
        else if (failedTrack != 2 && nextVideo != null) videoIndex = nextVideo
        else if (failedTrack == 0 && nextAudio != null) audioIndex = nextAudio
        else {
            if (error == -17) fail(BoloPlayerError.FormatNotSupported("媒体格式不支持"))
            else beginSourceRecovery(position)
            return
        }
        startLoad(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        scope.launch {
            if (disposed) return@launch
            if (state.value.playbackSpeed != speed) resetDiagnostics()
            mutableState.value = state.value.copy(playbackSpeed = speed)
            val revision = ++speedRevision
            val engine = mutableBackend.value ?: return@launch
            speedJob?.cancel()
            speedJob = scope.launch {
                val result = withContext(boloMpvDispatcher) {
                    engine.speed(speed.toDouble()).also { if (it >= 0) playbackClock.setSpeed(speed.toDouble()) }
                }
                if (result < 0 && revision == speedRevision && mutableBackend.value === engine)
                    onError(BoloPlayerError.UnknownError("设置倍速失败（$result）"))
            }
        }
    }

    fun setVolumeGain(gain: Int) {
        scope.launch {
            if (disposed) return@launch
            volumeGain = gain.coerceIn(0, 200)
            val volume = volumeGain.toDouble()
            mutableBackend.value?.let { withContext(boloMpvDispatcher) { it.volume(volume) } }
        }
    }

    fun setLoudnessSettings(
        mode: PlaybackLoudnessMode,
        dynamicEnabled: Boolean,
        targetLufs: Double = -14.0,
        rangeLu: Double = 11.0,
        truePeakDbtp: Double = -2.0,
    ) {
        val target = targetLufs.takeIf { it.isFinite() && it in -70.0..-5.0 } ?: -14.0
        val range = rangeLu.takeIf { it.isFinite() && it in 1.0..50.0 } ?: 11.0
        val peak = truePeakDbtp.takeIf { it.isFinite() && it in -9.0..0.0 } ?: -2.0
        scope.launch {
            if (disposed || (loudnessMode == mode && dynamicLoudnessEnabled == dynamicEnabled &&
                dynamicLoudnessTargetLufs == target && dynamicLoudnessRangeLu == range &&
                dynamicLoudnessTruePeakDbtp == peak)) return@launch
            loudnessMode = mode
            dynamicLoudnessEnabled = dynamicEnabled
            dynamicLoudnessTargetLufs = target
            dynamicLoudnessRangeLu = range
            dynamicLoudnessTruePeakDbtp = peak
            loudnessRevision++
            loudnessJob?.cancel()
            updateLoudnessInfo(null)
            val engine = mutableBackend.value ?: return@launch
            if (!resourcesSuspended) loudnessJob = scope.launch { applyLoudness(engine) }
        }
    }

    private suspend fun applyLoudness(engine: BoloMpvBackend) {
        val revision = loudnessRevision
        val expected = generation
        val dynamicEnabled = dynamicLoudnessEnabled
        val gain = if (dynamicEnabled) 0.0 else loudnessMode.gainDb(mediaLoudness)
        val target = dynamicLoudnessTargetLufs
        val range = dynamicLoudnessRangeLu
        val peak = dynamicLoudnessTruePeakDbtp
        updateLoudnessInfo(null)
        try {
            val result = withContext(boloMpvDispatcher) { engine.loudness(gain, dynamicEnabled, target, range, peak) }
            if (revision != loudnessRevision || expected != generation ||
                mutableBackend.value !== engine || disposed || resourcesSuspended) return
            updateLoudnessInfo(result >= 0, gain.takeIf { result >= 0 })
            if (result < 0)
                onError(BoloPlayerError.UnknownError("音量均衡配置失败（$result），已停用本次均衡"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (revision != loudnessRevision || expected != generation || mutableBackend.value !== engine || disposed) return
            // 平台调用异常同样尽力恢复无均衡，不让可选音效中断播放。
            withContext(NonCancellable + boloMpvDispatcher) { runCatching { engine.loudness(0.0, false) } }
            if (revision == loudnessRevision && expected == generation && mutableBackend.value === engine && !disposed && !resourcesSuspended) {
                updateLoudnessInfo(false)
                onError(BoloPlayerError.UnknownError("音量均衡配置失败：${error.message}"))
            }
        }
    }

    fun setMergeAudioChannelsEnabled(enabled: Boolean) {
        scope.launch {
            if (disposed || mergeAudioChannelsEnabled == enabled) return@launch
            mergeAudioChannelsEnabled = enabled
            val revision = ++mergeAudioRevision
            mergeAudioJob?.cancel()
            mutableInfo.value = info.value.copy(audio = info.value.audio?.copy(
                outputChannelLayout = null, outputChannelCount = null, channelsMerged = null,
            ))
            lastInfoSample = null
            val engine = mutableBackend.value ?: return@launch
            if (resourcesSuspended) return@launch
            mergeAudioJob = scope.launch {
                try {
                    val result = withContext(boloMpvDispatcher) { engine.mergeAudioChannels(enabled) }
                    if (result < 0 && revision == mergeAudioRevision && mutableBackend.value === engine)
                        onError(BoloPlayerError.UnknownError("合并多声道配置失败（$result）"))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (revision == mergeAudioRevision && mutableBackend.value === engine)
                        onError(BoloPlayerError.UnknownError("合并多声道配置失败：${error.message}"))
                } finally {
                    if (revision == mergeAudioRevision) lastInfoSample = null
                }
            }
        }
    }

    fun rebuild() {
        scope.launch {
            if (disposed || resourcesSuspended || state.value.isRebuilding || videoUrls.isEmpty()) return@launch
            allowSourceRecovery()
            if (sourceRefreshJob != null || needsSourceRefresh()) {
                endedBeforeSeek = endedBeforeSeek || state.value.isEnded
                beginSourceRecovery(state.value.displayPositionMs)
                return@launch
            }
            val expected = generation
            val lifecycle = lifecycleRevision
            resetDiagnostics()
            mutableState.value = state.value.copy(isRebuilding = true, hasConfirmedPosition = false)
            rebuildJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    releaseEngine()
                    if (!isActive || disposed || resourcesSuspended || generation != expected + 1 ||
                        lifecycleRevision != lifecycle) return@launch
                    restoring = false
                    recoveryAttempted = false
                    startLoad(state.value.displayPositionMs)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (generation == expected + 1 && lifecycleRevision == lifecycle && !disposed)
                        fail(BoloPlayerError.DecoderError("重建播放器失败：${error.message}", error))
                }
            }.also { it.start() }
        }
    }

    private fun cancelRebuild() {
        rebuildJob?.cancel()
        rebuildJob = null
        mutableState.value = state.value.copy(isRebuilding = false)
    }

    fun play(allowSourceRefreshRetry: Boolean = true) {
        scope.launch {
            playIntentRevision++
            if (disposed) return@launch
            resumeEligible = false
            backgroundJob?.cancel()
            resourcesSuspended = false
            if (allowSourceRefreshRetry) allowSourceRecovery()
            playWhenReady = true
            if (state.value.isEnded) {
                seekToMs(0L)
            } else if (sourceRefreshJob != null || needsSourceRefresh()) beginSourceRecovery(state.value.displayPositionMs)
            else if (state.value.isPlaybackSuspended || (!ready && loadJob?.isActive != true)) restorePlayback()
            else applyPlayIntent()
        }
    }

    fun pause() {
        scope.launch {
            playIntentRevision++
            if (disposed) return@launch
            advancingSince = null
            playWhenReady = false
            resumeEligible = false
            mutableState.value = state.value.copy(isPlaying = false, isBuffering = state.value.isSeeking)
            applyPlayIntent()
            scheduleBackgroundRelease()
        }
    }

    internal suspend fun pauseAndObserve() {
        withContext(Dispatchers.Main.immediate) {
            pause()
            intentJob?.join()
            withContext(boloMpvDispatcher) { playbackClock.sample() }
            publishPlaybackObservation()
        }
    }

    private fun applyPlayIntent() {
        val engine = mutableBackend.value ?: return
        val expected = generation
        intentJob?.cancel()
        intentJob = scope.launch {
            val playing = playWhenReady && ready && !resourcesSuspended && !state.value.isSeeking && !state.value.isEnded
            if (playing && !engine.setAudioActive(true)) { fail(BoloPlayerError.DecoderError("音频会话激活失败")); return@launch }
            val result = withContext(boloMpvDispatcher) { engine.pause(!playing).also { if (it >= 0) playbackClock.setPaused(!playing) } }
            if (generation != expected || mutableBackend.value !== engine) return@launch
            if (!playing && !playWhenReady) engine.setAudioActive(false)
            if (result < 0) fail(BoloPlayerError.DecoderError("播放状态更新失败（$result）"))
            else mutableState.value = state.value.copy(isPlaying = playing && !state.value.isBuffering)
        }
    }

    fun seekToMs(positionMs: Long, autoPlayAfterSeek: Boolean = false) {
        scope.launch {
            if (disposed) return@launch
            backgroundJob?.cancel()
            resourcesSuspended = false
            resetDiagnostics()
            allowSourceRecovery()
            endedBeforeSeek = endedBeforeSeek || state.value.isEnded
            if (autoPlayAfterSeek) {
                resumeEligible = false
                playIntentRevision++
                playWhenReady = true
            }
            val target = positionMs.coerceAtLeast(0).let { if (state.value.durationMs > 0) it.coerceAtMost(state.value.durationMs) else it }
            if (sourceRefreshJob != null || needsSourceRefresh()) {
                beginSourceRecovery(target)
                return@launch
            }
            seekJob?.cancel()
            activeSeekRequest = ++requestSequence
            coordinator.requestSeek(target)
            mutableInfo.value = info.value.withoutDynamicValues()
            lastInfoSample = null
            mutableState.value = state.value.copy(pendingSeekPositionMs = target, isEnded = false, isBuffering = true)
            if (ready) submitSeek()
            else if (loadJob?.isActive != true && videoUrls.isNotEmpty()) startLoad(target)
        }
    }

    private fun submitSeek() {
        if (!ready || coordinator.isSubmitted || !seekabilityKnown) return
        if (!state.value.isSeekable) {
            if (coordinator.pendingPositionMs == 0L && activeSeekRequest == 0L) {
                // load(start=0) 已提交；仍须等待当前媒体的真实时间观测。
                coordinator.markSubmitted(coordinator.currentRevision)
                val expected = generation
                seekJob = scope.launch {
                    delay(BoloPlayerSeekCoordinator.AttemptTimeoutMs)
                    if (expected == generation && state.value.isSeeking) failSeek("起点确认超时")
                }
            } else failSeek("当前媒体不支持跳转")
            return
        }
        val engine = mutableBackend.value ?: return
        val target = coordinator.pendingPositionMs ?: return
        val expected = generation
        val revision = coordinator.currentRevision
        seekJob?.cancel()
        seekJob = scope.launch {
            repeat(BoloPlayerSeekCoordinator.MaxAttempts) {
                if (!coordinator.isCurrent(expected, revision)) return@launch
                activeSeekRequest = ++requestSequence
                val request = activeSeekRequest
                val result = withContext(boloMpvDispatcher) {
                    playbackClock.beginSeek()
                    engine.seek(target / 1000.0, request)
                }
                if (!coordinator.isCurrent(expected, revision)) return@launch
                if (result < 0) { failSeek("跳转提交失败（$result）"); return@launch }
                coordinator.markSubmitted(revision)
                delay(BoloPlayerSeekCoordinator.AttemptTimeoutMs)
            }
            if (coordinator.isCurrent(expected, revision)) failSeek("跳转确认超时")
        }
    }

    private fun failSeek(message: String) {
        val position = state.value.displayPositionMs
        if (onRefreshSource != null) {
            beginSourceRecovery(position)
            return
        }
        coordinator.cancelCurrentSeek()
        seekJob?.cancel()
        mutableState.value = state.value.copy(pendingSeekPositionMs = null, isBuffering = false)
        if (state.value.isRebuilding) {
            fail(BoloPlayerError.DecoderError("重建后恢复进度失败：$message"))
        } else if (restoring && !recoveryAttempted) {
            recoveryAttempted = true
            val expected = generation
            val lifecycle = lifecycleRevision
            scope.launch {
                if (expected != generation) return@launch
                releaseEngine()
                if (generation == expected + 1 && lifecycle == lifecycleRevision && !resourcesSuspended && !disposed)
                    startLoad(position)
            }
        } else if (restoring) fail(BoloPlayerError.DecoderError("播放器恢复失败：$message"))
        else { onError(BoloPlayerError.SeekError(message)); applyPlayIntent() }
    }

    fun setResumeAfterBackgroundEnabled(enabled: Boolean) {
        scope.launch { resumeEnabled = enabled; if (!enabled) resumeEligible = false }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean, playbackRequested: Boolean = playWhenReady) {
        if (disposed || backgroundPlaybackEnabled == enabled) return
        backgroundPlaybackEnabled = enabled
        if (inBackground && !enabled) setForeground(false, force = true, playbackRequested = playbackRequested)
    }

    internal fun setForeground(foreground: Boolean, force: Boolean = false, playbackRequested: Boolean = playWhenReady) {
        scope.launch {
            if (disposed || (!force && inBackground == !foreground)) return@launch
            inBackground = !foreground
            resetDiagnostics()
            val lifecycle = ++lifecycleRevision
            backgroundJob?.cancel()
            if (!foreground && !backgroundPlaybackEnabled) {
                // 自动暂停只发生在退后台或关闭开关时，后续系统命令可以恢复资源。
                resumeEligible = playbackRequested && !state.value.isEnded
                playIntentRevision++
                resourcesSuspended = true
                cancelRebuild()
                mergeAudioJob?.cancel()
                loudnessJob?.cancel()
                resetInfo()
                val saved = state.value.displayPositionMs
                cancelSourceRefresh()
                playWhenReady = false
                restoring = false
                loadJob?.cancel()
                seekJob?.cancel()
                intentJob?.cancel()
                coordinator.cancelCurrentSeek()
                if (!ready) generation = coordinator.onMediaChanged()
                mutableState.value = state.value.copy(currentPositionMs = saved, pendingSeekPositionMs = null,
                    isPlaybackSuspended = true, isPlaying = false, isBuffering = false, hasConfirmedPosition = false)
                mutableBackend.value?.let { engine ->
                    withContext(boloMpvDispatcher) { engine.pause(true); playbackClock.setPaused(true); engine.videoEnabled(false) }
                    if (lifecycle == lifecycleRevision && resourcesSuspended && mutableBackend.value === engine)
                        engine.setAudioActive(false)
                }
                if (lifecycle == lifecycleRevision) scheduleBackgroundRelease()
                return@launch
            }
            if (foreground) {
                playWhenReady = playbackRequested || (resumeEnabled && resumeEligible && !state.value.isEnded)
                resumeEligible = false
                resourcesSuspended = false
            }
            if (state.value.isPlaybackSuspended) {
                restorePlayback()
                return@launch
            }
            try {
                val engine = mutableBackend.value
                if (engine != null) {
                    if (foreground && videoOutputActive) withTimeout(8_000) { engine.awaitOutput() }
                    if (lifecycle != lifecycleRevision || disposed || mutableBackend.value !== engine) return@launch
                    withContext(boloMpvDispatcher) { engine.videoEnabled(foreground && videoOutputActive) }
                }
                if (lifecycle != lifecycleRevision || disposed) return@launch
                if (foreground && (sourceRefreshJob != null || needsSourceRefresh())) {
                    endedBeforeSeek = endedBeforeSeek || state.value.isEnded
                    beginSourceRecovery(state.value.displayPositionMs)
                } else if (!ready && loadJob?.isActive != true && sourceRefreshJob == null && videoUrls.isNotEmpty()) {
                    startLoad(state.value.displayPositionMs)
                } else applyPlayIntent()
                scheduleBackgroundRelease()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { onError(BoloPlayerError.DecoderError("画面恢复失败：${error.message}", error)) }
        }
    }

    private suspend fun restorePlayback() {
        if (disposed || resourcesSuspended || videoUrls.isEmpty()) return
        restoring = true
        recoveryAttempted = false
        val position = state.value.displayPositionMs
        if (needsSourceRefresh()) {
            endedBeforeSeek = endedBeforeSeek || state.value.isEnded
            beginSourceRecovery(position)
            return
        }
        val engine = mutableBackend.value
        if (!ready || engine == null) {
            if (loadJob?.isActive != true) startLoad(position)
            return
        }
        val lifecycle = lifecycleRevision
        val expected = generation
        val seekRevision = coordinator.currentRevision
        fun isCurrent() = lifecycle == lifecycleRevision && generation == expected && !resourcesSuspended &&
            !disposed && mutableBackend.value === engine && coordinator.currentRevision == seekRevision
        try {
            if (!inBackground && videoOutputActive) withTimeout(8_000) { engine.awaitOutput() }
            if (!isCurrent()) return
            val result = withContext(boloMpvDispatcher) {
                engine.videoEnabled(!inBackground && videoOutputActive)
                engine.mergeAudioChannels(mergeAudioChannelsEnabled)
            }
            if (!isCurrent()) return
            if (result < 0) onError(BoloPlayerError.UnknownError("合并多声道配置失败（$result）"))
            applyLoudness(engine)
            if (!isCurrent()) return
            val retainedPosition = withContext(boloMpvDispatcher) { engine.retainedPosition(expected, position) }
            if (!isCurrent()) return
            if (retainedPosition != null || state.value.isEnded) {
                nativeSeeking = false
                restoring = false
                recoveryAttempted = false
                mutableState.value = state.value.copy(currentPositionMs = retainedPosition ?: position,
                    pendingSeekPositionMs = null, hasConfirmedPosition = retainedPosition != null,
                    isPlaybackSuspended = false, isBuffering = false, isRebuilding = false)
                applyPlayIntent()
                scheduleBackgroundRelease()
            } else {
                coordinator.requestSeek(position)
                mutableState.value = state.value.copy(pendingSeekPositionMs = position, isBuffering = true)
                submitSeek()
            }
        } catch (error: TimeoutCancellationException) {
            if (isCurrent()) fail(BoloPlayerError.DecoderError("画面恢复超时", error))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (isCurrent()) fail(BoloPlayerError.DecoderError("画面恢复失败", error))
        }
    }

    private fun canReleaseBackgroundResources() = inBackground && !playWhenReady && !restoring &&
        !state.value.isSeeking && !state.value.isRebuilding && loadJob?.isActive != true && sourceRefreshJob == null

    private fun scheduleBackgroundRelease() {
        backgroundJob?.cancel()
        if (!canReleaseBackgroundResources() || mutableBackend.value?.retainsPausedResources != false) return
        val expected = generation
        val revision = playIntentRevision
        backgroundJob = scope.launch {
            delay(60_000)
            if (!disposed && expected == generation && revision == playIntentRevision && canReleaseBackgroundResources()) {
                resourcesSuspended = true
                mutableState.value = state.value.copy(isPlaybackSuspended = true)
                releaseEngine()
            }
        }
    }

    internal fun setVideoOutputActive(active: Boolean) {
        scope.launch {
            if (videoOutputActive != active) resetDiagnostics()
            videoOutputActive = active
            val engine = mutableBackend.value ?: return@launch
            if (active && !inBackground) {
                try { withTimeout(8_000) { engine.awaitOutput() } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { return@launch }
            }
            if (mutableBackend.value === engine && !disposed)
                withContext(boloMpvDispatcher) { engine.videoEnabled(videoOutputActive && !inBackground) }
        }
    }

    internal fun outputAttached() {
        setVideoOutputActive(true)
        scope.launch { if (!disposed && !inBackground && !resourcesSuspended && sourceRefreshJob == null && mutableBackend.value == null && videoUrls.isNotEmpty()) startLoad(state.value.displayPositionMs) }
    }

    internal fun outputDetached(output: Any) {
        scope.launch {
            val engine = mutableBackend.value ?: return@launch
            engine.detachOutput(output)
        }
    }

    internal fun releaseBackgroundResources() {
        scope.launch {
            if (!disposed && canReleaseBackgroundResources()) {
                backgroundJob?.cancel()
                resourcesSuspended = true
                mutableState.value = state.value.copy(isPlaybackSuspended = true)
                releaseEngine()
            }
        }
    }
    fun release() { scope.launch { if (!disposed) { cancelSourceRefresh(); cancelRebuild(); releaseEngine() } } }

    private suspend fun releaseEngine() {
        resetInfo()
        loadJob?.cancel()
        eventsJob?.cancel()
        seekJob?.cancel()
        speedJob?.cancel()
        mergeAudioJob?.cancel()
        loudnessJob?.cancel()
        intentJob?.cancel()
        publishPlaybackObservation()
        generation = coordinator.onMediaChanged()
        ready = false
        val engine = mutableBackend.value
        mutableBackend.value = null
        mutableState.value = state.value.copy(currentPositionMs = state.value.displayPositionMs,
            pendingSeekPositionMs = null, isPlaying = false, isBuffering = false, hasConfirmedPosition = false)
        withContext(NonCancellable) {
            if (engine != null) {
                try {
                    engineMutex.withLock {
                        withContext(boloMpvDispatcher) { playbackClock.setPaused(true); engine.stop() }
                    }
                } finally {
                    try {
                        // iOS 后台的 GL 清理可能等待前台，不能占住新音频实例的创建锁。
                        engine.unbind()
                    } finally {
                        try {
                            withContext(boloMpvDispatcher) { engine.destroy() }
                        } finally {
                            engine.setAudioActive(false)
                        }
                    }
                }
            }
        }
    }

    fun dispose() {
        scope.launch {
            if (disposed) return@launch
            disposed = true
            cancelSourceRefresh()
            cancelRebuild()
            backgroundJob?.cancel()
            releaseEngine()
            scope.cancel()
        }
    }

    private fun fail(error: BoloPlayerError) {
        sourceRevision++
        sourceRefreshJob?.cancel()
        sourceRefreshJob = null
        cancelRebuild()
        ready = false
        restoring = false
        playWhenReady = false
        loadJob?.cancel()
        seekJob?.cancel()
        coordinator.cancelCurrentSeek()
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = false,
            isEnded = endedBeforeSeek || state.value.isEnded,
            pendingSeekPositionMs = null, isPlaybackSuspended = resourcesSuspended, playWhenReady = playWhenReady, hasConfirmedPosition = false)
        val expected = generation
        scope.launch {
            if (expected != generation) return@launch
            releaseEngine()
            if (generation == expected + 1 && !disposed) onError(error)
        }
    }

    private fun secondsToMs(value: Double): Long? = value.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE / 1000.0 }
        ?.let { (it * 1000).roundToLong() }
}

suspend fun BoloPlayerController.load(
    video: BiliDashObject,
    audio: BiliDashObject? = null,
    startPositionMs: Long = 0L,
    loudness: VideoLoudnessData? = null,
    sortCdn: Boolean = false,
) = loadMedia(video, audio, startPositionMs, loudness, sortCdn)
