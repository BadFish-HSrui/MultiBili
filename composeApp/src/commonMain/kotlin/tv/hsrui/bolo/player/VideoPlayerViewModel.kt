package tv.hsrui.bolo.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.player.base.BoloPlayerController
import tv.hsrui.bolo.player.base.BoloPlayerError
import tv.hsrui.bolo.player.base.BoloPlayerSource
import tv.hsrui.bolo.player.base.BoloPlayerState
import tv.hsrui.bolo.player.base.load
import tv.hsrui.bolo.player.danmaku.BoloDanmakuController
import tv.hsrui.bolo.player.danmaku.BoloDanmakuItem
import tv.hsrui.bolo.player.danmaku.BoloDanmakuMode
import tv.hsrui.bolo.player.settings.BoloPlayerSettings
import tv.hsrui.bolo.player.subtitle.BoloSubtitleController
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.network.feature.danmaku.DanmakuMode
import tv.hsrui.network.feature.danmaku.fetchDanmakuSegment
import tv.hsrui.network.feature.danmaku.fetchDanmakuView
import tv.hsrui.network.feature.danmaku.sendDanmaku as postDanmaku
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.HighEnergyProgressData
import tv.hsrui.network.feature.player.fetchHighEnergyProgress
import tv.hsrui.network.feature.player.PlayerInfoResponse
import tv.hsrui.network.feature.player.PlayerChapterData
import tv.hsrui.network.feature.player.fetchPlayerInfo
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.fetchVideoPlayInfo
import tv.hsrui.network.feature.player.fetchMediaPlayInfo
import tv.hsrui.network.feature.subtitle.fetchSubtitleInfo
import tv.hsrui.network.login.storage.LoginStorage

class VideoPlayerViewModel(
    avid: Long,
    cid: Long,
    episodeId: Long? = null,
    private val initialPlayerInfo: PlayerInfoResponse? = null,
) : ViewModel() {
    private val settings: BoloSettings = getKoin().get()
    private val playerSettings: BoloPlayerSettings = getKoin().get()
    private val loginStorage: LoginStorage = getKoin().get()
    var avid: Long = avid
        private set
    var cid: Long = cid
        private set
    var episodeId: Long? = episodeId
        private set
    var singleEpisodeLoopEnabled by mutableStateOf(false)
    private var sourceLoadJob: Job? = null
    private var subtitleLoadJob: Job? = null
    private var highEnergyProgressLoadJob: Job? = null
    private var highEnergyProgressGeneration = 0L
    private var highEnergyProgressRequested = false
    private val _highEnergyProgress = MutableStateFlow<HighEnergyProgressData?>(null)
    val highEnergyProgress = _highEnergyProgress.asStateFlow()
    private val _chapters = MutableStateFlow<List<PlayerChapterData>>(emptyList())
    val chapters = _chapters.asStateFlow()
    private var sourceGeneration = 0L
    private var autoPlayOnOpen = true
    internal var pendingPlayWhenReady by mutableStateOf<Boolean?>(null)
        private set
    private var resumeFromHistoryOnOpen = true
    private var playerInfo: PlayerInfoResponse? = null
    private var playbackGeneration = -1L
    private var lastConfirmedPositionMs: Long? = null

    val subtitleController = BoloSubtitleController(viewModelScope)
    val danmakuController = BoloDanmakuController()
    private val playbackReportController = PlaybackReportController()
    private val danmakuSegments = mutableMapOf<Long, List<BoloDanmakuItem>>()
    private val danmakuRequests = mutableMapOf<Long, Job>()
    private val failedDanmakuSegments = mutableSetOf<Long>()
    private val sentDanmaku = linkedMapOf<Long, BoloDanmakuItem>()
    private var danmakuContextJob: Job? = null
    private var danmakuSendJob: Job? = null
    private var danmakuCooldownJob: Job? = null
    private var danmakuPlayerInfo by mutableStateOf<PlayerInfoResponse?>(null)
    private var danmakuSendingAllowed by mutableStateOf(false)
    private var danmakuCoolingDown by mutableStateOf(false)
    private var danmakuSending by mutableStateOf(false)
    var danmakuInputOpen by mutableStateOf(false)
        private set
    val danmakuDraft = mutableStateOf("")
    var danmakuSendError by mutableStateOf<String?>(null)
        private set
    private var danmakuInputRevision = 0L
    private var danmakuResumeRevision: Long? = null
    val danmakuMaxLength: Int get() = danmakuPlayerInfo?.danmakuMaxLength ?: 0
    val canSendDanmaku: Boolean
        get() {
            val playback = controller.state.value
            return !playbackClosed && playbackForeground && danmakuSendingAllowed &&
                danmakuPlayerInfo?.canSendDanmaku == true && !_danmakuClosed.value &&
                danmakuController.state.value.isVisible && uiState.value is VideoPlayerUiState.Success &&
                playbackGeneration == sourceGeneration && playback.hasConfirmedPosition &&
                !awaitingPlaybackReload && !playback.isSeeking && !playback.isRebuilding &&
                !playback.isPlaybackSuspended && !playback.isEnded
        }
    val danmakuSubmitEnabled: Boolean get() = canSendDanmaku && !danmakuSending && !danmakuCoolingDown &&
        danmakuDraft.value.isNotBlank() && danmakuDraft.value.length <= danmakuMaxLength
    val danmakuInputEnabled: Boolean get() = canSendDanmaku && !danmakuSending && !danmakuCoolingDown
    private var danmakuWindow = emptySet<Long>()
    private var currentDanmakuSegment = 0L
    private var danmakuGeneration = 0L
    private val _danmakuClosed = MutableStateFlow(false)
    val danmakuClosed = _danmakuClosed.asStateFlow()
    private var playbackForeground = true
    internal var playbackClosed = false
        private set
    private var awaitingPlaybackReload = true
    private var danmakuMedia: Pair<Long, Long>? = null
    private var awaitingDanmakuSeek = false
    private var pendingDanmakuSeek: Long? = null
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentVideoQuality = MutableStateFlow(VideoQuality.best)
    val currentVideoQuality = _currentVideoQuality.asStateFlow()

    private val _currentAudioQuality = MutableStateFlow<AudioQuality?>(null)
    val currentAudioQuality = _currentAudioQuality.asStateFlow()

    var videoQuality: VideoQuality = VideoQuality.best
    var audioQuality: AudioQuality? = AudioQuality.best
    private var currentVideoCodec = settings.playback.defaultVideoCodec

    var isLoading: Boolean = false
    private var playbackLoadJob: Job? = null

    val controller = BoloPlayerController(onError = { e ->
        when (e) {
            is BoloPlayerError.NetworkError -> showSnackbarMessage(e.message)
            is BoloPlayerError.DecoderError -> println("解码: ${e.message}")
            is BoloPlayerError.FormatNotSupported -> println("格式不支持: ${e.message}")
            is BoloPlayerError.SeekError -> println("跳转失败: ${e.message}")
            is BoloPlayerError.UnknownError -> println("未知错误: ${e.message}")
        }
    })

    init {
        controller.onRefreshSource = ::refreshPlayInfo
        if (getPlatform().type == PlatformType.Desktop) {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                snapshotFlow {
                    if (playerSettings.controls.desktopMuted) 0 else playerSettings.controls.desktopVolumePercent
                }.collect(controller::setVolumeGain)
            }
        }
        viewModelScope.launch {
            controller.state.collect { playback ->
                playbackReportController.updatePlayback(playback, controller.backend.value != null)
                if (playbackGeneration == sourceGeneration && playback.hasConfirmedPosition &&
                    !playback.isPlaybackSuspended && !playback.isRebuilding && !playback.isSeeking
                ) {
                    lastConfirmedPositionMs = playback.currentPositionMs
                }
                if (playback.isPlaybackSuspended) {
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

    fun switchMedia(avid: Long, cid: Long, episodeId: Long? = null, forceReload: Boolean = false, initialPlayerInfo: PlayerInfoResponse? = null) {
        if (!forceReload && this.avid == avid && this.cid == cid && this.episodeId == episodeId) return
        val opensNewMedia = sourceGeneration == 0L || this.avid != avid || this.cid != cid || this.episodeId != episodeId
        if (opensNewMedia || (forceReload && _highEnergyProgress.value == null)) {
            highEnergyProgressLoadJob?.cancel()
            highEnergyProgressGeneration += 1
            highEnergyProgressRequested = false
            _highEnergyProgress.value = null
        }
        val retryPositionMs = if (opensNewMedia) null else {
            controller.state.value.takeIf { it.hasConfirmedPosition && playbackGeneration == sourceGeneration }
                ?.displayPositionMs ?: lastConfirmedPositionMs
        }
        if (opensNewMedia) {
            sentDanmaku.clear()
            danmakuDraft.value = ""
            _chapters.value = emptyList()
            singleEpisodeLoopEnabled = false
            playerInfo = (initialPlayerInfo ?: this.initialPlayerInfo).takeIf { sourceGeneration == 0L }
            lastConfirmedPositionMs = null
            autoPlayOnOpen = settings.playback.autoPlayOnOpenEnabled
            resumeFromHistoryOnOpen = settings.playback.resumeFromHistoryEnabled
            videoQuality = settings.playback.defaultVideoQuality
            audioQuality = settings.playback.defaultAudioQuality
        }
        playbackReportController.beforeReload(controller.state.value, controller.backend.value != null)
        playbackReportController.openMedia(avid, cid)
        sourceLoadJob?.cancel()
        playbackLoadJob?.cancel()
        controller.cancelSourceRefresh(clearSource = true)
        val generation = ++sourceGeneration
        controller.pause()
        pendingPlayWhenReady = autoPlayOnOpen
        this.avid = avid
        this.cid = cid
        this.episodeId = episodeId
        resetDanmaku()
        if (opensNewMedia) {
            setDanmakuVisible(settings.playback.autoEnableDanmakuOnOpenEnabled || playerSettings.controls.danmakuEnabled)
        }
        _uiState.value = VideoPlayerUiState.Loading
        sourceLoadJob = viewModelScope.launch {
            loadVideo()
            if (generation == sourceGeneration) {
                playVideo(startPositionMs = retryPositionMs ?: resumePositionMs(), autoPlay = pendingPlayWhenReady == true)
                if (uiState.value is VideoPlayerUiState.Error) pendingPlayWhenReady = null
            }
        }
    }

    fun setDanmakuVisible(visible: Boolean) {
        if (_danmakuClosed.value) return
        danmakuController.setVisible(visible)
        playerSettings.controls.danmakuEnabled = visible
    }

    fun openDanmakuInput() {
        if (!danmakuInputEnabled || danmakuInputOpen) return
        val wasPlaying = controller.state.value.isPlaying
        pause()
        danmakuResumeRevision = controller.playIntentRevision.takeIf { wasPlaying }
        danmakuInputRevision += 1L
        danmakuSendError = null
        danmakuInputOpen = true
    }

    fun dismissDanmakuInput(resumePlayback: Boolean = true) {
        val resumeRevision = danmakuResumeRevision
        danmakuResumeRevision = null
        danmakuInputOpen = false
        danmakuInputRevision += 1L
        if (resumePlayback && resumeRevision == controller.playIntentRevision &&
            playbackForeground && !playbackClosed && !controller.state.value.isEnded &&
            playbackGeneration == sourceGeneration
        ) play()
    }

    private fun refreshDanmakuContext() {
        danmakuContextJob?.cancel()
        danmakuSendingAllowed = false
        val info = playerInfo?.takeIf { it.matchesRequest(avid, cid, loginStorage.cookies.sessData) }
        danmakuPlayerInfo = info
        if (info?.canSendDanmaku != true || !loginStorage.isLoggedIn) return
        val generation = danmakuGeneration
        val requestedAvid = avid
        val requestedCid = cid
        danmakuContextJob = viewModelScope.launch {
            try {
                val response = fetchDanmakuView(requestedAvid, requestedCid)
                if (generation != danmakuGeneration || playbackClosed) return@launch
                danmakuSendingAllowed = response.canSend
                if (response.isClosed) {
                    _danmakuClosed.value = true
                    danmakuRequests.values.toList().forEach { it.cancel() }
                    danmakuRequests.clear()
                    danmakuSegments.clear()
                    danmakuController.clear()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 发送权限未知时隐藏入口，不影响已有播放与弹幕加载。
            }
        }
    }

    fun sendDanmaku(message: String) {
        if (!danmakuInputOpen || !danmakuSubmitEnabled || message != danmakuDraft.value) return
        val requestedAvid = avid
        val requestedCid = cid
        val generation = danmakuGeneration
        val inputRevision = danmakuInputRevision
        val progressMs = controller.state.value.currentPositionMs.coerceAtLeast(0L)
        val cooldownMs = danmakuPlayerInfo?.danmakuCooldownMs ?: return
        danmakuSending = true
        danmakuSendError = null
        danmakuSendJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val requestJob = currentCoroutineContext().job
            try {
                val response = postDanmaku(requestedAvid, requestedCid, progressMs, message)
                if (generation != danmakuGeneration || playbackClosed) return@launch
                if (!response.isSuccess) {
                    if (response.code == 36711) danmakuSendingAllowed = false
                    danmakuSendError = if (response.code == 0) "发送结果缺少弹幕 ID，请勿重复发送" else
                        "[${response.code}]: ${response.message}"
                    return@launch
                }
                val item = BoloDanmakuItem(
                    id = response.id,
                    progressMs = progressMs,
                    content = response.content ?: message,
                    mode = BoloDanmakuMode.Scroll,
                    isOwn = true,
                )
                sentDanmaku[item.id] = item
                publishDanmakuSegments()
                danmakuController.showImmediately(item)
                danmakuCoolingDown = true
                danmakuCooldownJob?.cancel()
                danmakuCooldownJob = viewModelScope.launch {
                    delay(cooldownMs)
                    danmakuCoolingDown = false
                }
                if (danmakuDraft.value == message) danmakuDraft.value = ""
                if (danmakuInputOpen && danmakuInputRevision == inputRevision) dismissDanmakuInput()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (generation == danmakuGeneration && !playbackClosed) {
                    danmakuSendError = "弹幕发送失败或结果未确认，请检查网络后重试"
                }
            } finally {
                if (danmakuSendJob === requestJob) {
                    danmakuSending = false
                    danmakuSendJob = null
                }
            }
        }
        danmakuSendJob?.start()
    }

    fun setDesktopVolume(percent: Int) {
        if (getPlatform().type != PlatformType.Desktop) return
        Snapshot.withMutableSnapshot {
            playerSettings.controls.desktopVolumePercent = percent
            playerSettings.controls.desktopMuted = false
        }
    }

    fun adjustDesktopVolume(delta: Int) {
        setDesktopVolume(playerSettings.controls.desktopVolumePercent + delta)
    }

    fun toggleDesktopMuted() {
        if (getPlatform().type != PlatformType.Desktop) return
        Snapshot.withMutableSnapshot {
            if (playerSettings.controls.desktopVolumePercent == 0) {
                playerSettings.controls.desktopVolumePercent = 100
                playerSettings.controls.desktopMuted = false
            } else {
                playerSettings.controls.desktopMuted = !playerSettings.controls.desktopMuted
            }
        }
    }

    private fun playVideo(startPositionMs: Long = 0L, autoPlay: Boolean = false) {
        val currentState = uiState.value
        if (currentState !is VideoPlayerUiState.Success) return
        playbackReportController.beforeReload(controller.state.value, controller.backend.value != null)

        val video = currentState.videoSource.getVideo(quality = videoQuality, codec = settings.playback.defaultVideoCodec)
        val audio = currentState.videoSource.getAudio(quality = audioQuality)
        currentVideoCodec = video.codec

        videoQuality = video.quality as VideoQuality
        audioQuality = audio?.let { it.quality as AudioQuality }
        _currentVideoQuality.value = videoQuality
        _currentAudioQuality.value = audioQuality

        awaitingPlaybackReload = true
        danmakuController.pause()
        danmakuMedia = avid to cid
        subtitleController.synchronize(startPositionMs)
        playbackLoadJob?.cancel()
        val generation = sourceGeneration
        val intentRevision = controller.playIntentRevision
        playbackLoadJob = viewModelScope.launch {
            controller.setLoudnessSettings(
                settings.playback.loudnessMode, settings.playback.dynamicLoudnessEnabled,
                settings.playback.dynamicLoudnessTargetLufs.toDouble(), settings.playback.dynamicLoudnessRangeLu.toDouble(),
                settings.playback.dynamicLoudnessTruePeakDbtp.toDouble(),
            )
            controller.load(
                video = video,
                audio = audio,
                startPositionMs = startPositionMs,
                loudness = currentState.videoSource.loudness,
                sortCdn = settings.playback.optimizePlaybackSourceEnabled,
            )
            currentCoroutineContext().ensureActive()
            if (generation != sourceGeneration) return@launch
            playbackGeneration = generation
            playbackReportController.mediaLoaded()
            if (pendingPlayWhenReady ?: (autoPlay && controller.playIntentRevision == intentRevision)) {
                controller.play(allowSourceRefreshRetry = false)
            }
            pendingPlayWhenReady = null
        }
    }

    fun onPlaybackPageEntered() {
        playbackReportController.enterPage()
    }

    fun onPlaybackPageExited() {
        controller.cancelSourceRefresh()
        dismissDanmakuInput(resumePlayback = false)
        danmakuDraft.value = ""
        danmakuSendJob?.cancel()
        singleEpisodeLoopEnabled = false
        playbackReportController.updatePlayback(controller.state.value, controller.backend.value != null)
        playbackReportController.leavePage()
    }

    fun setBackgroundPlaybackAllowed(allowed: Boolean) {
        playbackReportController.setBackgroundPlaybackAllowed(allowed)
    }

    fun onPlaybackForegroundChanged(active: Boolean) {
        playbackForeground = active
        if (!active) {
            dismissDanmakuInput(resumePlayback = false)
            danmakuController.pause()
            danmakuRequests.values.toList().forEach { it.cancel() }
            danmakuRequests.clear()
        }
        playbackReportController.updatePlayback(controller.state.value, controller.backend.value != null)
        playbackReportController.setForeground(active)
    }

    fun switchQuality(newVideoQuality: VideoQuality) {
        val source = (uiState.value as? VideoPlayerUiState.Success)?.videoSource ?: return
        if (newVideoQuality == currentVideoQuality.value || newVideoQuality !in source.videoQualities) return
        if (settings.playback.recordQualitySelectionEnabled) settings.playback.defaultVideoQuality = newVideoQuality
        videoQuality = newVideoQuality
        playVideo(controller.state.value.displayPositionMs)
    }

    fun switchAudioQuality(newAudioQuality: AudioQuality) {
        val source = (uiState.value as? VideoPlayerUiState.Success)?.videoSource ?: return
        if (newAudioQuality == currentAudioQuality.value || newAudioQuality !in source.audioQualities) return
        if (settings.playback.recordQualitySelectionEnabled) settings.playback.defaultAudioQuality = newAudioQuality
        audioQuality = newAudioQuality
        playVideo(controller.state.value.displayPositionMs)
    }

    suspend fun fetchPlayInfo(): VideoSource {
        return episodeId?.let { fetchMediaPlayInfo(it) } ?: fetchVideoPlayInfo(avid = avid, cid = cid)
    }

    private suspend fun refreshPlayInfo(): BoloPlayerSource {
        val generation = sourceGeneration
        repeat(2) { attempt ->
            if (playbackClosed || generation != sourceGeneration) {
                throw CancellationException("播放源请求已失效")
            }
            val result = try {
                withTimeout(10_000L) { fetchPlayInfo() }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val retryable = error is IOException || error is HttpRequestTimeoutException || error is TimeoutCancellationException
                if (attempt == 0 && retryable) {
                    delay(1_000L)
                    return@repeat
                }
                throw IllegalStateException("播放地址刷新失败，请重试")
            }
            currentCoroutineContext().ensureActive()
            if (playbackClosed || generation != sourceGeneration) {
                throw CancellationException("播放源请求已失效")
            }
            if (!result.isSuccess) throw IllegalStateException(result.message.ifBlank { "播放地址刷新失败，请重试" })
            // 取流期间的选档操作优先；续期不重新读取默认编码，也不触发媒体打开流程。
            val video = result.getVideo(videoQuality, currentVideoCodec)
            val audio = result.getAudio(audioQuality)
            videoQuality = video.quality as? VideoQuality ?: throw IllegalStateException("播放地址刷新失败，请重试")
            audioQuality = audio?.quality as? AudioQuality
            currentVideoCodec = video.codec
            _currentVideoQuality.value = videoQuality
            _currentAudioQuality.value = audioQuality
            _uiState.value = VideoPlayerUiState.Success(result)
            return BoloPlayerSource(video, audio, result.loudness, settings.playback.optimizePlaybackSourceEnabled)
        }
        throw IllegalStateException("播放地址刷新失败，请重试")
    }

    private fun resumePositionMs(): Long {
        if (!resumeFromHistoryOnOpen || !loginStorage.isLoggedIn) return 0L
        val info = playerInfo?.takeIf { it.matchesRequest(avid, cid, loginStorage.cookies.sessData) } ?: return 0L
        val source = (uiState.value as? VideoPlayerUiState.Success)?.videoSource ?: return 0L
        val durationSeconds = maxOf(source.getVideo(videoQuality, settings.playback.defaultVideoCodec).duration,
            source.getAudio(audioQuality)?.duration ?: 0L)
        val durationMs = durationSeconds.coerceIn(0L, Long.MAX_VALUE / 1000L) * 1000L
        return info.resumePositionMs(cid, durationMs)
    }

    suspend fun loadVideo() {
        val requestedAvid = avid
        val requestedCid = cid
        val generation = sourceGeneration
        val requestedSession = loginStorage.cookies.sessData
        val requestedIsMedia = episodeId != null
        fun isUnplayableUpowerVideo(): Boolean =
            loginStorage.cookies.sessData == requestedSession && playerInfo?.takeIf {
                it.matchesRequest(requestedAvid, requestedCid, requestedSession)
            }?.isUnplayableUpowerVideo == true

        subtitleLoadJob?.cancel()
        subtitleController.clear()
        subtitleController.autoChineseOnly = settings.playback.subtitleAutoChineseOnly
        subtitleController.autoExcludeAi = settings.playback.subtitleAutoExcludeAi
        subtitleController.smartEnabled = settings.playback.subtitleSmartEnabled
        subtitleController.alwaysOn = settings.playback.subtitleAlwaysOn
        val subtitleGeneration = subtitleController.beginSubtitleLoad(requestedAvid, requestedCid) {
            loginStorage.cookies.sessData == requestedSession
        }
        try {
            val result = coroutineScope {
                val infoRequest = async {
                    playerInfo?.takeIf { it.matchesRequest(requestedAvid, requestedCid, requestedSession) }
                        ?: fetchPlayerInfo(requestedAvid, requestedCid).also {
                            currentCoroutineContext().ensureActive()
                            if (generation == sourceGeneration) playerInfo = it
                        }
                }
                fetchPlayInfo().also { infoRequest.await() }
            }
            currentCoroutineContext().ensureActive()
            if (generation != sourceGeneration || avid != requestedAvid || cid != requestedCid) return
            if (isUnplayableUpowerVideo()) {
                _uiState.value = VideoPlayerUiState.Error("无此充电视频播放权限")
                return
            }
            if (result.isSuccess) {
                _chapters.value = playerInfo?.takeIf {
                    it.matchesRequest(requestedAvid, requestedCid, requestedSession) &&
                        loginStorage.cookies.sessData == requestedSession
                }?.chapters.orEmpty()
                _uiState.value = VideoPlayerUiState.Success(result)
                refreshDanmakuContext()
                loadHighEnergyProgress()
                subtitleLoadJob = viewModelScope.launch {
                    try {
                        val info = playerInfo?.takeIf { it.matchesRequest(requestedAvid, requestedCid, requestedSession) }
                        if (loginStorage.cookies.sessData != requestedSession) return@launch
                        val subtitles = fetchSubtitleInfo(
                            requestedAvid, requestedCid, requestedIsMedia,
                            result.playbackLanguage, result.playbackProductionType,
                            info?.asrLanguage, info?.ocrLanguage,
                        )
                        currentCoroutineContext().ensureActive()
                        if (generation != sourceGeneration || avid != requestedAvid || cid != requestedCid ||
                            loginStorage.cookies.sessData != requestedSession
                        ) return@launch
                        subtitleController.loadSubtitleList(requestedAvid, requestedCid, subtitles.subtitles, subtitleGeneration)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (generation == sourceGeneration) println("字幕列表加载失败")
                    }
                }
            } else {
                _uiState.value = VideoPlayerUiState.Error(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != sourceGeneration || avid != requestedAvid || cid != requestedCid) return
            _uiState.value = VideoPlayerUiState.Error(
                if (isUnplayableUpowerVideo()) "无此充电视频播放权限" else e.message ?: "其他网络错误"
            )
        }
    }

    private fun loadHighEnergyProgress() {
        if (highEnergyProgressRequested || playbackClosed || avid <= 0L || cid <= 0L) return
        highEnergyProgressRequested = true
        val requestedAvid = avid
        val requestedCid = cid
        val generation = highEnergyProgressGeneration
        highEnergyProgressLoadJob = viewModelScope.launch {
            try {
                val data = fetchHighEnergyProgress(requestedAvid, requestedCid)
                currentCoroutineContext().ensureActive()
                if (!playbackClosed && generation == highEnergyProgressGeneration &&
                    requestedAvid == avid && requestedCid == cid
                ) {
                    _highEnergyProgress.value = data
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 可选曲线加载失败不影响播放，也不自动重复请求。
            }
        }
    }

    fun play() {
        if (controller.state.value.isPlaybackSuspended) return
        if (pendingPlayWhenReady != null) {
            pendingPlayWhenReady = true
            controller.pause() // 保持旧媒体暂停，同时使此前的中断恢复票据失效。
            return
        }
        if (uiState.value !is VideoPlayerUiState.Success) return
        controller.play()
    }

    fun pause() {
        if (pendingPlayWhenReady != null) pendingPlayWhenReady = false
        controller.pause()
    }

    fun seekToMs(positionMs: Long, autoPlayAfterSeek: Boolean = false) {
        if (playbackClosed) return
        val playbackBeforeSeek = controller.state.value
        if (playbackBeforeSeek.isPlaybackSuspended) return
        danmakuController.pause()
        danmakuController.seekToMs(positionMs)
        awaitingDanmakuSeek = true
        pendingDanmakuSeek = positionMs
        subtitleController.synchronize(positionMs)
        controller.seekToMs(positionMs, autoPlayAfterSeek)
        // 原生层可能同步拒绝 Seek，仍需以实际位置恢复调度。
        synchronizeDanmaku(controller.state.value)
        subtitleController.synchronize(controller.state.value.displayPositionMs)
    }

    private fun synchronizeDanmaku(playback: BoloPlayerState) {
        if (!playbackForeground) { danmakuController.pause(); return }
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
                    val loggedIn = loginStorage.isLoggedIn
                    val userHash = danmakuPlayerInfo?.danmakuUserHash.orEmpty()
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
                            isOwn = loggedIn && (item.isOwn ||
                                (item.senderHash.isNotEmpty() && item.senderHash == userHash)),
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
        danmakuController.load(danmakuSegments.entries.sortedBy { it.key }.flatMap { it.value } + sentDanmaku.values)
    }

    private fun resetDanmaku() {
        dismissDanmakuInput(resumePlayback = false)
        danmakuContextJob?.cancel()
        danmakuSendJob?.cancel()
        danmakuSendJob = null
        danmakuSending = false
        danmakuSendingAllowed = false
        danmakuPlayerInfo = null
        danmakuSendError = null
        subtitleLoadJob?.cancel()
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

    fun closePlayback() {
        if (playbackClosed) return
        playbackClosed = true
        _chapters.value = emptyList()
        highEnergyProgressGeneration += 1
        highEnergyProgressLoadJob?.cancel()
        _highEnergyProgress.value = null
        viewModelScope.cancel()
        subtitleLoadJob?.cancel()
        onPlaybackPageExited()
        playbackReportController.close()
        sourceGeneration += 1
        sourceLoadJob?.cancel()
        playbackLoadJob?.cancel()
        subtitleController.clear()
        danmakuGeneration += 1
        danmakuRequests.values.toList().forEach { it.cancel() }
        danmakuRequests.clear()
        danmakuController.dispose()
        controller.dispose()
    }

    override fun onCleared() {
        closePlayback()
        super.onCleared()
    }
}
