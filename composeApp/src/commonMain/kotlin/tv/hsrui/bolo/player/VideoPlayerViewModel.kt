package tv.hsrui.bolo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.base.BoloPlayerController
import tv.hsrui.bolo.player.base.BoloPlayerError
import tv.hsrui.bolo.player.base.BoloPlayerSeekCoordinator
import tv.hsrui.bolo.player.base.BoloPlayerState
import tv.hsrui.bolo.player.base.load
import tv.hsrui.bolo.player.danmaku.BoloDanmakuController
import tv.hsrui.bolo.player.danmaku.BoloDanmakuItem
import tv.hsrui.bolo.player.danmaku.BoloDanmakuMode
import tv.hsrui.bolo.player.subtitle.BoloSubtitleController
import tv.hsrui.network.feature.danmaku.DanmakuMode
import tv.hsrui.network.feature.danmaku.fetchDanmakuSegment
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.fetchVideoPlayInfo
import tv.hsrui.network.feature.player.fetchMediaPlayInfo

class VideoPlayerViewModel(avid: Long, cid: Long, episodeId: Long? = null) : ViewModel() {
    private val settings: BoloSettings = getKoin().get()
    var avid: Long = avid
        private set
    var cid: Long = cid
        private set
    var episodeId: Long? = episodeId
        private set
    private var sourceLoadJob: Job? = null
    private var sourceGeneration = 0L
    private var autoPlayOnOpen = true

    val subtitleController = BoloSubtitleController(viewModelScope)
    val danmakuController = BoloDanmakuController()
    private val playbackReportController = PlaybackReportController()
    private val danmakuSegments = mutableMapOf<Long, List<BoloDanmakuItem>>()
    private val danmakuRequests = mutableMapOf<Long, Job>()
    private val failedDanmakuSegments = mutableSetOf<Long>()
    private var danmakuWindow = emptySet<Long>()
    private var currentDanmakuSegment = 0L
    private var danmakuGeneration = 0L
    private val _danmakuClosed = MutableStateFlow(false)
    val danmakuClosed = _danmakuClosed.asStateFlow()
    private var awaitingPlaybackReload = true
    private var danmakuMedia: Pair<Long, Long>? = null
    private var awaitingDanmakuSeek = false
    private var pendingDanmakuSeek: Long? = null
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentVideoQuality = MutableStateFlow(VideoQuality.best)
    val currentVideoQuality = _currentVideoQuality.asStateFlow()

    var videoQuality: VideoQuality = VideoQuality.best
    var videoCodec: VideoCodec = VideoCodec.HEVC
    var audioQuality: AudioQuality? = AudioQuality.best

    var isLoading: Boolean = false
    private var playbackLoadJob: Job? = null
    private var replayJob: Job? = null

    val controller = BoloPlayerController(onError = { e ->
        when (e) {
            is BoloPlayerError.NetworkError -> println("网络错误: ${e.message}")
            is BoloPlayerError.DecoderError -> println("解码: ${e.message}")
            is BoloPlayerError.FormatNotSupported -> println("格式不支持: ${e.message}")
            is BoloPlayerError.SeekError -> println("跳转失败: ${e.message}")
            is BoloPlayerError.UnknownError -> println("未知错误: ${e.message}")
        }
    })

    init {
        viewModelScope.launch {
            controller.state.collect { playback ->
                playbackReportController.updatePlayback(playback, controller.backend.value != null)
                if (playback.isPlaybackSuspended) {
                    replayJob?.cancel()
                    danmakuController.pause()
                    return@collect
                }
                synchronizeDanmaku(playback)
                if (danmakuMedia == (this@VideoPlayerViewModel.avid to this@VideoPlayerViewModel.cid) && !awaitingPlaybackReload) {
                    subtitleController.synchronize(playback.displayPositionMs)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(250L)
                playbackReportController.updatePlayback(controller.state.value, controller.backend.value != null)
            }
        }
        if (avid > 0L && cid > 0L) switchMedia(avid, cid, episodeId, forceReload = true)
    }

    fun switchMedia(avid: Long, cid: Long, episodeId: Long? = null, forceReload: Boolean = false) {
        if (!forceReload && this.avid == avid && this.cid == cid && this.episodeId == episodeId) return
        val opensNewMedia = sourceGeneration == 0L || this.avid != avid || this.cid != cid || this.episodeId != episodeId
        if (opensNewMedia) autoPlayOnOpen = settings.playerAutoPlayOnOpenEnabled
        playbackReportController.beforeReload(controller.state.value, controller.backend.value != null)
        playbackReportController.openMedia(avid, cid)
        sourceLoadJob?.cancel()
        playbackLoadJob?.cancel()
        replayJob?.cancel()
        val generation = ++sourceGeneration
        controller.pause()
        this.avid = avid
        this.cid = cid
        this.episodeId = episodeId
        resetDanmaku()
        if (opensNewMedia) {
            setDanmakuVisible(settings.playerAutoEnableDanmakuOnOpenEnabled || settings.danmakuEnabled)
        }
        _uiState.value = VideoPlayerUiState.Loading
        sourceLoadJob = viewModelScope.launch {
            loadVideo()
            if (generation == sourceGeneration) playVideo(autoPlay = autoPlayOnOpen)
        }
    }

    fun setDanmakuVisible(visible: Boolean) {
        if (_danmakuClosed.value) return
        danmakuController.setVisible(visible)
        settings.danmakuEnabled = visible
    }

    private fun playVideo(startPositionMs: Long = 0L, autoPlay: Boolean = false) {
        replayJob?.cancel()
        val currentState = uiState.value
        if (currentState !is VideoPlayerUiState.Success) return
        playbackReportController.beforeReload(controller.state.value, controller.backend.value != null)

        val video = currentState.videoSource.getVideo(quality = videoQuality, codec = videoCodec)
        val audio = currentState.videoSource.getAudio(quality = audioQuality)

        videoQuality = video.quality as VideoQuality
        videoCodec = video.codec
        audioQuality = audio?.let { it.quality as AudioQuality }
        _currentVideoQuality.value = videoQuality

        awaitingPlaybackReload = true
        danmakuController.pause()
        danmakuMedia = avid to cid
        subtitleController.loadSubtitleList(avid, cid)
        subtitleController.synchronize(startPositionMs)
        playbackLoadJob?.cancel()
        val generation = sourceGeneration
        playbackLoadJob = viewModelScope.launch {
            controller.setLoudnessSettings(
                settings.playerLoudnessMode, settings.playerDynamicLoudnessEnabled,
                settings.playerDynamicLoudnessTargetLufs.toDouble(), settings.playerDynamicLoudnessRangeLu.toDouble(),
                settings.playerDynamicLoudnessTruePeakDbtp.toDouble(),
            )
            controller.load(video = video, audio = audio, startPositionMs = startPositionMs, loudness = currentState.videoSource.loudness)
            currentCoroutineContext().ensureActive()
            if (generation != sourceGeneration) return@launch
            playbackReportController.mediaLoaded()
            if (autoPlay) controller.play()
        }
    }

    fun onPlaybackPageEntered() {
        playbackReportController.enterPage()
    }

    fun onPlaybackPageExited() {
        playbackReportController.updatePlayback(controller.state.value, controller.backend.value != null)
        playbackReportController.leavePage()
    }

    fun onPlaybackForegroundChanged(active: Boolean) {
        playbackReportController.updatePlayback(controller.state.value, controller.backend.value != null)
        playbackReportController.setForeground(active)
    }

    fun switchQuality(newVideoQuality: VideoQuality) {
        videoQuality = newVideoQuality
        playVideo(controller.state.value.displayPositionMs)
    }

    suspend fun fetchPlayInfo(): VideoSource {
        return episodeId?.let { fetchMediaPlayInfo(it) } ?: fetchVideoPlayInfo(avid = avid, cid = cid)
    }

    suspend fun loadVideo() {
        val requestedAvid = avid
        val requestedCid = cid
        val generation = sourceGeneration
        try {
            val result = fetchPlayInfo()
            currentCoroutineContext().ensureActive()
            if (generation != sourceGeneration || avid != requestedAvid || cid != requestedCid) return
            if (result.isSuccess) {
                _uiState.value = VideoPlayerUiState.Success(result)
            } else {
                _uiState.value = VideoPlayerUiState.Error(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != sourceGeneration || avid != requestedAvid || cid != requestedCid) return
            _uiState.value = VideoPlayerUiState.Error(e.message ?: "其他网络错误")
        }
    }

    fun play() {
        if (uiState.value !is VideoPlayerUiState.Success) return
        if (controller.state.value.isPlaybackSuspended) return
        if (!controller.state.value.isEnded) {
            if (replayJob?.isActive != true) controller.play()
            return
        }
        if (replayJob?.isActive == true) return
        seekToMs(0L)
        replayJob = viewModelScope.launch {
            // 先确认 EOF 暂停媒体已跳离结尾，再恢复播放。
            val playback = controller.state.first { !it.isPlaybackSuspended && !it.isSeeking && !it.isBuffering }
            if (playback.currentPositionMs in 0L..BoloPlayerSeekCoordinator.ConfirmationToleranceMs) {
                controller.play()
            }
        }
    }

    fun pause() {
        replayJob?.cancel()
        controller.pause()
    }

    fun seekToMs(positionMs: Long, autoPlayAfterSeek: Boolean = false) {
        val playbackBeforeSeek = controller.state.value
        if (playbackBeforeSeek.isPlaybackSuspended) return
        val shouldResume = autoPlayAfterSeek && !playbackBeforeSeek.isPlaying
        val targetMs = if (playbackBeforeSeek.durationMs > 0L) {
            positionMs.coerceIn(0L, playbackBeforeSeek.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        replayJob?.cancel()
        danmakuController.pause()
        danmakuController.seekToMs(positionMs)
        awaitingDanmakuSeek = true
        pendingDanmakuSeek = positionMs
        subtitleController.synchronize(positionMs)
        controller.seekToMs(positionMs)
        // 原生层可能同步拒绝 Seek，仍需以实际位置恢复调度。
        synchronizeDanmaku(controller.state.value)
        subtitleController.synchronize(controller.state.value.displayPositionMs)
        if (shouldResume) {
            replayJob = viewModelScope.launch {
                val playback = controller.state.first { !it.isPlaybackSuspended && !it.isSeeking && !it.isBuffering }
                val toleranceMs = BoloPlayerSeekCoordinator.ConfirmationToleranceMs
                if (!playback.isEnded && playback.currentPositionMs in
                    (targetMs - toleranceMs).coerceAtLeast(0L)..(targetMs + toleranceMs)
                ) {
                    controller.play()
                }
            }
        }
    }

    private fun synchronizeDanmaku(playback: BoloPlayerState) {
        if (danmakuMedia != (avid to cid)) return
        val pending = playback.pendingSeekPositionMs
        if (pending != null && (!awaitingPlaybackReload || awaitingDanmakuSeek)) {
            if (!awaitingDanmakuSeek || pendingDanmakuSeek != pending) {
                danmakuController.pause()
                danmakuController.seekToMs(pending)
            }
            awaitingDanmakuSeek = true
            pendingDanmakuSeek = pending
        }
        if (playback.isSeeking || playback.isBuffering) {
            danmakuController.pause()
            return
        }
        if (awaitingPlaybackReload && playback.durationMs <= 0L) return
        // 重载期间的暂态零位置不能覆盖清晰度切换前的调度位置。
        if (awaitingPlaybackReload && !playback.isPlaying && playback.currentPositionMs == 0L) return
        awaitingPlaybackReload = false
        danmakuController.syncPlayback(
            positionMs = playback.currentPositionMs,
            isPlaying = playback.isPlaying && (playback.durationMs <= 0L || playback.currentPositionMs < playback.durationMs),
            speed = playback.playbackSpeed,
            discontinuity = awaitingDanmakuSeek,
        )
        awaitingDanmakuSeek = false
        pendingDanmakuSeek = null
        updateDanmakuWindow(playback.currentPositionMs, playback.durationMs)
    }

    private fun updateDanmakuWindow(positionMs: Long, durationMs: Long) {
        if (_danmakuClosed.value || avid <= 0L || cid <= 0L) return
        val segment = positionMs.coerceAtLeast(0L) / 360_000L + 1L
        val lastSegment = if (durationMs > 0L) (durationMs - 1L) / 360_000L + 1L else Long.MAX_VALUE
        val window = (maxOf(1L, segment - 1L)..minOf(lastSegment, segment + 1L)).toSet()
        if (segment != currentDanmakuSegment) {
            currentDanmakuSegment = segment
            failedDanmakuSegments.remove(segment)
        }
        if (window != danmakuWindow) {
            danmakuWindow = window
            danmakuRequests.keys.filter { it !in window }.forEach { danmakuRequests.remove(it)?.cancel() }
            failedDanmakuSegments.retainAll(window)
            if (danmakuSegments.keys.retainAll(window)) publishDanmakuSegments()
        }
        // 当前段优先；上一段仍可能包含正在展示的弹幕。
        for (index in listOf(segment, segment - 1L, segment + 1L)) {
            if (index !in window || index in danmakuSegments || index in danmakuRequests || index in failedDanmakuSegments) continue
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) { loadDanmakuSegment(index) }
            danmakuRequests[index] = job
            job.start()
        }
    }

    private suspend fun loadDanmakuSegment(segmentIndex: Long) {
        val generation = danmakuGeneration
        val requestedAvid = avid
        val requestedCid = cid
        val requestJob = currentCoroutineContext().job
        fun isCurrent() = generation == danmakuGeneration && requestedAvid == avid && requestedCid == cid &&
            segmentIndex in danmakuWindow && !_danmakuClosed.value && danmakuRequests[segmentIndex] === requestJob
        try {
            repeat(2) { attempt ->
                try {
                    val response = fetchDanmakuSegment(cid = requestedCid, segmentIndex = segmentIndex, avid = requestedAvid)
                    if (!isCurrent()) return
                    if (response.isClosed) {
                        _danmakuClosed.value = true
                        danmakuSegments.clear()
                        danmakuController.clear()
                        danmakuRequests.values.filter { it !== requestJob }.forEach { it.cancel() }
                        return
                    }
                    danmakuSegments[segmentIndex] = response.items.mapNotNull { item ->
                        val mode = when (item.mode) {
                            DanmakuMode.Scroll -> BoloDanmakuMode.Scroll
                            DanmakuMode.Top -> BoloDanmakuMode.Top
                            DanmakuMode.Bottom -> BoloDanmakuMode.Bottom
                            else -> return@mapNotNull null
                        }
                        BoloDanmakuItem(
                            id = item.id,
                            progressMs = item.progressMs.toLong(),
                            content = item.content,
                            mode = mode,
                            fontSize = item.fontSize.takeIf { it > 0 }?.toFloat() ?: 25f,
                            colorRgb = item.colorRgb,
                            weight = item.weight,
                        )
                    }
                    publishDanmakuSegments()
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isCurrent()) return
                    if (attempt == 1) {
                        failedDanmakuSegments.add(segmentIndex)
                        println("弹幕分段 $segmentIndex 加载失败: ${e.message}")
                    } else {
                        delay(1_000L)
                    }
                }
            }
        } finally {
            if (danmakuRequests[segmentIndex] === requestJob) danmakuRequests.remove(segmentIndex)
        }
    }

    private fun publishDanmakuSegments() {
        danmakuController.load(danmakuSegments.entries.sortedBy { it.key }.flatMap { it.value })
    }

    private fun resetDanmaku() {
        subtitleController.clear()
        danmakuGeneration += 1
        danmakuRequests.values.toList().forEach { it.cancel() }
        danmakuRequests.clear()
        danmakuSegments.clear()
        failedDanmakuSegments.clear()
        danmakuWindow = emptySet()
        currentDanmakuSegment = 0L
        _danmakuClosed.value = false
        awaitingPlaybackReload = true
        danmakuMedia = null
        awaitingDanmakuSeek = false
        pendingDanmakuSeek = null
        danmakuController.clear()
    }

    override fun onCleared() {
        onPlaybackPageExited()
        playbackReportController.close()
        sourceGeneration += 1
        sourceLoadJob?.cancel()
        playbackLoadJob?.cancel()
        replayJob?.cancel()
        subtitleController.clear()
        danmakuGeneration += 1
        danmakuRequests.values.toList().forEach { it.cancel() }
        danmakuRequests.clear()
        danmakuController.dispose()
        controller.dispose()
        super.onCleared()
    }
}
