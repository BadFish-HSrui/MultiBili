package tv.hsrui.bolo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import tv.hsrui.bolo.player.base.BoloPlayerController
import tv.hsrui.bolo.player.base.BoloPlayerError
import tv.hsrui.bolo.player.base.BoloPlayerState
import tv.hsrui.bolo.player.base.load
import tv.hsrui.bolo.player.danmaku.BoloDanmakuController
import tv.hsrui.bolo.player.danmaku.BoloDanmakuItem
import tv.hsrui.bolo.player.danmaku.BoloDanmakuMode
import tv.hsrui.network.feature.danmaku.DanmakuMode
import tv.hsrui.network.feature.danmaku.fetchDanmakuSegment
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.fetchVideoPlayInfo

class VideoPlayerViewModel(avid: Long, cid: Long) : ViewModel() {
    var avid: Long = avid
        set(value) {
            if (field == value) return
            field = value
            resetDanmaku()
        }
    var cid: Long = cid
        set(value) {
            if (field == value) return
            field = value
            resetDanmaku()
        }

    val danmakuController = BoloDanmakuController()
    private val danmakuSegments = mutableMapOf<Long, List<BoloDanmakuItem>>()
    private val danmakuRequests = mutableMapOf<Long, Job>()
    private val failedDanmakuSegments = mutableSetOf<Long>()
    private var danmakuWindow = emptySet<Long>()
    private var currentDanmakuSegment = 0L
    private var danmakuGeneration = 0L
    private var danmakuClosed = false
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
            controller.state.collect { playback -> synchronizeDanmaku(playback) }
        }
        viewModelScope.launch {
            loadVideo()
            playVideo()
        }
    }

    private fun playVideo(startPositionMs: Long = 0L) {
        val currentState = uiState.value
        if (currentState !is VideoPlayerUiState.Success) return

        val video = currentState.videoSource.getVideo(quality = videoQuality, codec = videoCodec)
        val audio = currentState.videoSource.getAudio(quality = audioQuality)

        videoQuality = video.quality as VideoQuality
        videoCodec = video.codec
        audioQuality = audio?.let { it.quality as AudioQuality }
        _currentVideoQuality.value = videoQuality

        awaitingPlaybackReload = true
        danmakuController.pause()
        danmakuMedia = avid to cid
        controller.load(video = video, audio = audio, startPositionMs = startPositionMs)
    }

    fun switchQuality(newVideoQuality: VideoQuality) {
        videoQuality = newVideoQuality
        playVideo(controller.state.value.displayPositionMs)
    }

    suspend fun fetchPlayInfo(): VideoSource {
        return fetchVideoPlayInfo(
            avid = avid,
            cid = cid
        )
    }

    suspend fun loadVideo() {
        val requestedAvid = avid
        val requestedCid = cid
        try {
            val result = fetchPlayInfo()
            if (avid != requestedAvid || cid != requestedCid) return
            if (result.isSuccess) {
                _uiState.value = VideoPlayerUiState.Success(result)
            } else {
                _uiState.value = VideoPlayerUiState.Error(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (avid != requestedAvid || cid != requestedCid) return
            _uiState.value = VideoPlayerUiState.Error(e.message ?: "其他网络错误")
        }
    }

    fun seekToMs(positionMs: Long) {
        danmakuController.pause()
        danmakuController.seekToMs(positionMs)
        awaitingDanmakuSeek = true
        pendingDanmakuSeek = positionMs
        controller.seekToMs(positionMs)
        // 原生层可能同步拒绝 Seek，仍需以实际位置恢复调度。
        synchronizeDanmaku(controller.state.value)
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
            speed = playback.playbackSpeed.rateNumber,
            discontinuity = awaitingDanmakuSeek,
        )
        awaitingDanmakuSeek = false
        pendingDanmakuSeek = null
        updateDanmakuWindow(playback.currentPositionMs, playback.durationMs)
    }

    private fun updateDanmakuWindow(positionMs: Long, durationMs: Long) {
        if (danmakuClosed || avid <= 0L || cid <= 0L) return
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
            segmentIndex in danmakuWindow && !danmakuClosed && danmakuRequests[segmentIndex] === requestJob
        try {
            repeat(2) { attempt ->
                try {
                    val response = fetchDanmakuSegment(cid = requestedCid, segmentIndex = segmentIndex, avid = requestedAvid)
                    if (!isCurrent()) return
                    if (response.isClosed) {
                        danmakuClosed = true
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
        danmakuGeneration += 1
        danmakuRequests.values.toList().forEach { it.cancel() }
        danmakuRequests.clear()
        danmakuSegments.clear()
        failedDanmakuSegments.clear()
        danmakuWindow = emptySet()
        currentDanmakuSegment = 0L
        danmakuClosed = false
        awaitingPlaybackReload = true
        danmakuMedia = null
        awaitingDanmakuSeek = false
        pendingDanmakuSeek = null
        danmakuController.clear()
    }

    override fun onCleared() {
        danmakuGeneration += 1
        danmakuRequests.values.toList().forEach { it.cancel() }
        danmakuRequests.clear()
        danmakuController.dispose()
        controller.dispose()
        super.onCleared()
    }
}
