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
import kotlinx.coroutines.delay
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
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal val boloMpvDispatcher = Dispatchers.Default.limitedParallelism(1)

/** 共享播放策略。状态只在 Main 更新，普通 native 调用与渲染上下文分离。 */
class BoloPlayerController(
    private val autoPlay: Boolean = true,
    private val onError: (BoloPlayerError) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BoloPlayerState())
    val state: StateFlow<BoloPlayerState> = mutableState.asStateFlow()
    private val mutableInfo = MutableStateFlow(BoloPlayerInfo())
    val info: StateFlow<BoloPlayerInfo> = mutableInfo.asStateFlow()
    private var mediaInfo = BoloPlayerInfo()
    private var infoPanelVisible = false
    private var lastInfoSample: TimeMark? = null
    private val mutableBackend = MutableStateFlow<BoloMpvBackend?>(null)
    internal val backend: StateFlow<BoloMpvBackend?> = mutableBackend.asStateFlow()
    private val coordinator = BoloPlayerSeekCoordinator()
    private var videoUrls = emptyList<String>()
    private var audioUrls = emptyList<String>()
    private var videoIndex = 0
    private var audioIndex = 0
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
    private var playWhenReady = autoPlay
    private var resumeEnabled = false
    private var resumeEligible = false
    private var restoring = false
    private var recoveryAttempted = false
    private var volumeGain = 100
    private var speedRevision = 0L
    private var nativeSeeking = false
    private var requestSequence = 0L
    private var activeSeekRequest = 0L
    private var debugSubmissionFailure = false
    private var debugTimeout = false
    private var debugNotSeekable = false

    internal suspend fun loadMedia(
        video: BiliDashObject,
        audio: BiliDashObject?,
        start: Long,
        loudness: VideoLoudnessData?,
        sortCdn: Boolean,
    ) {
        withContext(Dispatchers.Main.immediate) {
            if (disposed) return@withContext
            cancelRebuild()
            loudnessJob?.cancel()
            loudnessRevision++
            mediaLoudness = loudness
            videoUrls = video.getUrls(sortCDN = sortCdn)
            audioUrls = audio?.getUrls(sortCDN = sortCdn).orEmpty()
            mediaInfo = BoloPlayerInfo(
                video = BoloPlayerVideoInfo(
                    nominalBitrateBps = video.bandwidth.takeIf { it > 0 },
                    width = video.width.takeIf { it > 0 },
                    height = video.height.takeIf { it > 0 },
                    fps = parsePlayerFrameRate(video.frameRate),
                ),
                audio = audio?.let { BoloPlayerAudioInfo(nominalBitrateBps = it.bandwidth.takeIf { bitrate -> bitrate > 0 }) },
            )
            resetInfo()
            videoIndex = 0
            audioIndex = 0
            resumeEligible = false
            recoveryAttempted = false
            val duration = maxOf(video.duration, audio?.duration ?: 0L).coerceIn(0L, Long.MAX_VALUE / 1000) * 1000
            mutableState.value = BoloPlayerState(isPlaybackSuspended = inBackground,
                durationMs = duration, currentPositionMs = start.coerceAtLeast(0), playbackSpeed = state.value.playbackSpeed)
            if (videoUrls.isEmpty() || (audio != null && audioUrls.isEmpty())) fail(BoloPlayerError.NetworkError("播放地址为空"))
            else if (!inBackground) startLoad(start.coerceAtLeast(0))
            else {
                loadJob?.cancel()
                seekJob?.cancel()
                ready = false
                generation = coordinator.onMediaChanged()
            }
        }
    }

    private fun startLoad(position: Long) {
        resetInfo()
        loudnessJob?.cancel()
        loadJob?.cancel()
        seekJob?.cancel()
        ready = false
        seekabilityKnown = false
        nativeSeeking = false
        activeSeekRequest = 0
        intentJob?.cancel()
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
                        // 与销毁互斥；创建期间的取消不能遗失已分配的 native handle。
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
                withTimeout(8_000) { current.awaitOutput() }
                if (generation != expected || inBackground || disposed) return@launch
                check(current.setAudioActive(true)) { "音频会话激活失败" }
                applyLoudness(current)
                if (generation != expected || inBackground || disposed) return@launch
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
            while (isActive && mutableBackend.value === engine) {
                val events = withContext(boloMpvDispatcher) {
                    buildList { repeat(128) { add(engine.poll() ?: return@buildList) } }
                }
                for (event in events) {
                    if (mutableBackend.value !== engine) break
                    if (event.generation == generation && !disposed) handleEvent(event)
                }
                sampleInfo(engine)
                delay(20)
            }
        }
    }

    internal fun setInfoPanelVisible(visible: Boolean) {
        scope.launch {
            if (disposed) return@launch
            infoPanelVisible = visible
            lastInfoSample = null
            mutableInfo.value = info.value.withoutDynamicValues()
        }
    }

    private fun resetInfo() {
        lastInfoSample = null
        loudnessConfigured = null
        appliedLoudnessGainDb = null
        mutableInfo.value = mediaInfo.withLoudnessInfo()
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
                lastInfoSample = null
                mutableInfo.value = info.value.withoutDynamicValues()
            }
            return
        }
        if (lastInfoSample?.elapsedNow()?.inWholeMilliseconds?.let { it < 500 } == true) return
        lastInfoSample = TimeSource.Monotonic.markNow()
        val expected = generation
        val audioRevision = mergeAudioRevision
        val snapshot = try {
            withContext(boloMpvDispatcher) { engine.info() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (disposed || inBackground || generation != expected || mutableBackend.value !== engine ||
            audioRevision != mergeAudioRevision) return
        if (!infoPanelVisible && !state.value.isBuffering) {
            mutableInfo.value = info.value.withoutDynamicValues()
            return
        }
        if (snapshot == null || snapshot.instance !== engine || snapshot.generation != expected) {
            mutableInfo.value = mediaInfo.withLoudnessInfo()
            return
        }
        val native = snapshot.info
        val seeking = state.value.isSeeking
        mutableInfo.value = native.copy(
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

    private fun handleEvent(event: BoloMpvEvent) {
        when (event.type) {
            BoloMpvEvent.Loaded -> {
                ready = true
                loadJob?.cancel()
                val target = state.value.pendingSeekPositionMs ?: state.value.currentPositionMs
                coordinator.requestSeek(target)
                mutableState.value = state.value.copy(pendingSeekPositionMs = target, hasConfirmedPosition = false)
                submitSeek()
            }
            BoloMpvEvent.Position -> observePosition(event.value, event.request)
            BoloMpvEvent.Restart -> if (!state.value.isSeeking) observePosition(event.value, event.request)
            BoloMpvEvent.Duration -> secondsToMs(event.value)?.takeIf { it > 0 }?.let {
                mutableState.value = state.value.copy(durationMs = it)
            }
            BoloMpvEvent.Seekable -> {
                seekabilityKnown = true
                mutableState.value = state.value.copy(isSeekable = event.value != 0.0)
                if (ready && state.value.isSeeking) submitSeek()
            }
            BoloMpvEvent.Buffering -> if (ready && !inBackground) {
                val buffering = event.value != 0.0 && (playWhenReady || state.value.isSeeking)
                mutableState.value = state.value.copy(isBuffering = buffering,
                    isPlaying = playWhenReady && !buffering && !state.value.isSeeking && !state.value.isEnded)
            }
            BoloMpvEvent.Paused -> if (ready && !inBackground) {
                mutableState.value = state.value.copy(isPlaying = event.value == 0.0 && playWhenReady &&
                    !state.value.isBuffering && !state.value.isSeeking && !state.value.isEnded)
            }
            BoloMpvEvent.Seeking -> nativeSeeking = event.value != 0.0
            BoloMpvEvent.Eof -> if (ready && !inBackground && !state.value.isSeeking && event.value != 0.0) {
                coordinator.cancelCurrentSeek()
                seekJob?.cancel()
                playWhenReady = false
                restoring = false
                mutableState.value = state.value.copy(isEnded = true, isPlaying = false, isBuffering = false, isRebuilding = false,
                    isPlaybackSuspended = false, pendingSeekPositionMs = null,
                    currentPositionMs = state.value.durationMs.takeIf { it > 0 } ?: state.value.currentPositionMs)
                applyPlayIntent()
            }
            BoloMpvEvent.SeekReply -> if (event.request == activeSeekRequest && event.error < 0) failSeek("跳转命令失败（${event.error}）")
            BoloMpvEvent.Error -> retrySource(event.error, event.value.toInt())
            BoloMpvEvent.Overflow -> fail(BoloPlayerError.UnknownError("播放器事件队列溢出，请重新加载"))
        }
    }

    private fun observePosition(seconds: Double, request: Long) {
        val position = secondsToMs(seconds) ?: return
        if (!ready || inBackground || debugTimeout) return
        if (state.value.isSeeking && request != activeSeekRequest) return
        if (!state.value.isSeeking && nativeSeeking) return
        // UI 在 seek/缓冲期间会标为未播放，不能据此忽略原生继续前进的时间。
        if (!coordinator.acceptObservedPosition(position, playWhenReady && !state.value.isEnded, state.value.playbackSpeed)) return
        val wasPending = state.value.isSeeking
        mutableState.value = state.value.copy(currentPositionMs = position,
            pendingSeekPositionMs = coordinator.pendingPositionMs, hasConfirmedPosition = true)
        if (wasPending && !state.value.isSeeking) {
            seekJob?.cancel()
            restoring = false
            recoveryAttempted = false
            mutableState.value = state.value.copy(isPlaybackSuspended = false, isBuffering = false, isRebuilding = false)
            applyPlayIntent()
        }
    }

    private fun retrySource(error: Int, failedTrack: Int) {
        if (inBackground || disposed) return
        val position = state.value.displayPositionMs
        if (failedTrack == 2 && audioIndex + 1 < audioUrls.size) audioIndex++
        else if (failedTrack != 2 && videoIndex + 1 < videoUrls.size) videoIndex++
        else if (failedTrack == 0 && audioIndex + 1 < audioUrls.size) audioIndex++
        else {
            fail(if (error == -17) BoloPlayerError.FormatNotSupported("媒体格式不支持")
                else BoloPlayerError.NetworkError("音视频加载失败（$error），备用地址已用尽"))
            return
        }
        startLoad(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        scope.launch {
            if (disposed) return@launch
            mutableState.value = state.value.copy(playbackSpeed = speed)
            val revision = ++speedRevision
            val engine = mutableBackend.value ?: return@launch
            speedJob?.cancel()
            speedJob = scope.launch {
                val result = withContext(boloMpvDispatcher) { engine.speed(speed.toDouble()) }
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
            if (!inBackground) loudnessJob = scope.launch { applyLoudness(engine) }
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
                mutableBackend.value !== engine || disposed || inBackground) return
            updateLoudnessInfo(result >= 0, gain.takeIf { result >= 0 })
            if (result < 0)
                onError(BoloPlayerError.UnknownError("音量均衡配置失败（$result），已停用本次均衡"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (revision != loudnessRevision || expected != generation || mutableBackend.value !== engine || disposed) return
            // 平台调用异常同样尽力恢复无均衡，不让可选音效中断播放。
            withContext(NonCancellable + boloMpvDispatcher) { runCatching { engine.loudness(0.0, false) } }
            if (revision == loudnessRevision && expected == generation && mutableBackend.value === engine && !disposed && !inBackground) {
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
            if (inBackground) return@launch
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
            if (disposed || inBackground || state.value.isRebuilding || videoUrls.isEmpty()) return@launch
            val expected = generation
            val lifecycle = lifecycleRevision
            mutableState.value = state.value.copy(isRebuilding = true, hasConfirmedPosition = false)
            rebuildJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    releaseEngine()
                    if (!isActive || disposed || inBackground || generation != expected + 1 ||
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

    fun play() {
        scope.launch {
            if (disposed || inBackground || state.value.isPlaybackSuspended) return@launch
            playWhenReady = true
            if (mutableBackend.value == null && videoUrls.isNotEmpty()) startLoad(state.value.displayPositionMs)
            else applyPlayIntent()
        }
    }

    fun pause() {
        scope.launch {
            if (disposed) return@launch
            playWhenReady = false
            resumeEligible = false
            mutableState.value = state.value.copy(isPlaying = false, isBuffering = state.value.isSeeking)
            applyPlayIntent()
        }
    }

    private fun applyPlayIntent() {
        val engine = mutableBackend.value ?: return
        val expected = generation
        intentJob?.cancel()
        intentJob = scope.launch {
            val playing = playWhenReady && ready && !inBackground && !state.value.isSeeking && !state.value.isEnded
            if (playing && !engine.setAudioActive(true)) { fail(BoloPlayerError.DecoderError("音频会话激活失败")); return@launch }
            val result = withContext(boloMpvDispatcher) { engine.pause(!playing) }
            if (generation != expected || mutableBackend.value !== engine) return@launch
            if (!playing && !restoring) engine.setAudioActive(false)
            if (result < 0) fail(BoloPlayerError.DecoderError("播放状态更新失败（$result）"))
            else mutableState.value = state.value.copy(isPlaying = playing && !state.value.isBuffering)
        }
    }

    fun seekToMs(positionMs: Long) {
        scope.launch {
            if (disposed || inBackground) return@launch
            val target = positionMs.coerceAtLeast(0).let { if (state.value.durationMs > 0) it.coerceAtMost(state.value.durationMs) else it }
            seekJob?.cancel()
            activeSeekRequest = ++requestSequence
            coordinator.requestSeek(target)
            mutableInfo.value = info.value.withoutDynamicValues()
            lastInfoSample = null
            mutableState.value = state.value.copy(pendingSeekPositionMs = target, isEnded = false, isBuffering = true)
            if (ready) submitSeek()
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
        if (debugNotSeekable) { debugNotSeekable = false; failSeek("当前媒体不支持跳转"); return }
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
                val result = if (debugSubmissionFailure) -1 else withContext(boloMpvDispatcher) {
                    engine.seek(target / 1000.0, request)
                }
                debugSubmissionFailure = false
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
        coordinator.cancelCurrentSeek()
        seekJob?.cancel()
        debugTimeout = false
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
                if (generation == expected + 1 && lifecycle == lifecycleRevision && !inBackground && !disposed)
                    startLoad(position)
            }
        } else if (restoring) fail(BoloPlayerError.DecoderError("播放器恢复失败：$message"))
        else { onError(BoloPlayerError.SeekError(message)); applyPlayIntent() }
    }

    fun setResumeAfterBackgroundEnabled(enabled: Boolean) {
        scope.launch { resumeEnabled = enabled; if (!enabled) resumeEligible = false }
    }

    internal fun setForeground(foreground: Boolean, outputWasReleased: Boolean = false) {
        scope.launch {
            if (disposed || inBackground == !foreground) return@launch
            inBackground = !foreground
            val lifecycle = ++lifecycleRevision
            backgroundJob?.cancel()
            if (!foreground) {
                cancelRebuild()
                mergeAudioJob?.cancel()
                loudnessJob?.cancel()
                resetInfo()
                resumeEligible = playWhenReady && !state.value.isEnded
                playWhenReady = false
                restoring = false
                loadJob?.cancel()
                seekJob?.cancel()
                intentJob?.cancel()
                val saved = state.value.displayPositionMs
                coordinator.cancelCurrentSeek()
                if (outputWasReleased || !ready) {
                    // 释放 VO 或中断加载时隔离迟到事件，恢复后重新 load。
                    ready = false
                    generation = coordinator.onMediaChanged()
                }
                mutableState.value = state.value.copy(currentPositionMs = saved, pendingSeekPositionMs = null,
                    isPlaybackSuspended = true, isPlaying = false, isBuffering = false, hasConfirmedPosition = false)
                mutableBackend.value?.let { engine ->
                    withContext(boloMpvDispatcher) { engine.pause(true) }
                    if (lifecycle == lifecycleRevision && inBackground && mutableBackend.value === engine)
                        engine.setAudioActive(false)
                }
                if (lifecycle != lifecycleRevision || !inBackground) return@launch
                backgroundJob = scope.launch {
                    delay(60_000)
                    if (lifecycle == lifecycleRevision && inBackground) releaseEngine()
                }
            } else if (videoUrls.isNotEmpty()) {
                restoring = true
                recoveryAttempted = false
                playWhenReady = resumeEnabled && resumeEligible && !state.value.isEnded
                resumeEligible = false
                val position = state.value.displayPositionMs
                if (ready && mutableBackend.value != null) {
                    val engine = mutableBackend.value!!
                    val mergeChannels = mergeAudioChannelsEnabled
                    val result = withContext(boloMpvDispatcher) { engine.mergeAudioChannels(mergeChannels) }
                    if (lifecycle != lifecycleRevision || inBackground || disposed || mutableBackend.value !== engine) return@launch
                    if (result < 0) onError(BoloPlayerError.UnknownError("合并多声道配置失败（$result）"))
                    applyLoudness(engine)
                    if (lifecycle != lifecycleRevision || inBackground || disposed || mutableBackend.value !== engine) return@launch
                    coordinator.requestSeek(position)
                    mutableState.value = state.value.copy(pendingSeekPositionMs = position)
                    submitSeek()
                } else startLoad(position)
            }
        }
    }

    internal fun outputAttached() {
        scope.launch { if (!disposed && !inBackground && mutableBackend.value == null && videoUrls.isNotEmpty()) startLoad(state.value.displayPositionMs) }
    }
    internal fun releaseBackgroundResources() { scope.launch { if (inBackground) releaseEngine() } }
    fun release() { scope.launch { if (!disposed) { cancelRebuild(); releaseEngine() } } }

    private suspend fun releaseEngine() {
        resetInfo()
        loadJob?.cancel()
        eventsJob?.cancel()
        seekJob?.cancel()
        speedJob?.cancel()
        mergeAudioJob?.cancel()
        loudnessJob?.cancel()
        intentJob?.cancel()
        generation = coordinator.onMediaChanged()
        ready = false
        val engine = mutableBackend.value
        mutableBackend.value = null
        mutableState.value = state.value.copy(currentPositionMs = state.value.displayPositionMs,
            pendingSeekPositionMs = null, isPlaying = false, isBuffering = false, hasConfirmedPosition = false)
        withContext(NonCancellable) {
            engineMutex.withLock {
                if (engine != null) {
                    try {
                        withContext(boloMpvDispatcher) { engine.stop() }
                    } finally {
                        try {
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
    }

    fun dispose() {
        scope.launch {
            if (disposed) return@launch
            disposed = true
            cancelRebuild()
            backgroundJob?.cancel()
            releaseEngine()
            scope.cancel()
        }
    }

    private fun fail(error: BoloPlayerError) {
        cancelRebuild()
        ready = false
        restoring = false
        playWhenReady = false
        loadJob?.cancel()
        seekJob?.cancel()
        coordinator.cancelCurrentSeek()
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = false,
            pendingSeekPositionMs = null, isPlaybackSuspended = inBackground, hasConfirmedPosition = false)
        val expected = generation
        scope.launch {
            if (expected != generation) return@launch
            releaseEngine()
            if (generation == expected + 1 && !disposed) onError(error)
        }
    }

    internal fun injectSeekFailureForDebug(nativeSubmissionFailure: Boolean, timeout: Boolean, notSeekable: Boolean) {
        scope.launch { debugSubmissionFailure = nativeSubmissionFailure; debugTimeout = timeout; debugNotSeekable = notSeekable }
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
