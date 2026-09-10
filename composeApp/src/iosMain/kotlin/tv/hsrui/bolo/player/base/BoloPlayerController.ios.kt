package tv.hsrui.bolo.player.base

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerState
import cocoapods.MobileVLCKit.VLCTime
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIView
import cocoapods.MobileVLCKit.VLCMediaPlayerStateChanged
import cocoapods.MobileVLCKit.VLCMediaPlayerTimeChanged
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual class BoloPlayerController actual constructor(
    autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private enum class MediaLifecycle {
        Empty,
        Loading,
        Loaded,
        Released,
        Disposed
    }

    private class PlaybackSpeedApplyGate {
        private enum class Phase {
            Inactive,
            Loading,
            Buffering,
            Playing,
            Paused
        }

        private var phase = Phase.Inactive
        private var mediaGeneration = 0L
        private var requestRevision = 0L
        private var appliedGeneration = -1L
        private var appliedRevision = -1L

        fun onMediaChanged() {
            mediaGeneration += 1
            phase = Phase.Loading
        }

        fun onOpening() {
            phase = Phase.Loading
        }

        fun onBuffering() {
            phase = Phase.Buffering
        }

        fun onPlaying(): Boolean {
            phase = Phase.Playing
            return consumeIfNeeded()
        }

        fun onPaused() {
            phase = Phase.Paused
        }

        fun onInactive() {
            phase = Phase.Inactive
        }

        fun onSpeedRequested(): Boolean {
            requestRevision += 1
            return when (phase) {
                Phase.Playing, Phase.Paused -> consumeIfNeeded()
                Phase.Inactive, Phase.Loading, Phase.Buffering -> false
            }
        }

        private fun consumeIfNeeded(): Boolean {
            if (
                appliedGeneration == mediaGeneration &&
                appliedRevision == requestRevision
            ) {
                return false
            }
            appliedGeneration = mediaGeneration
            appliedRevision = requestRevision
            return true
        }
    }

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal val mediaPlayer = VLCMediaPlayer()
    private var isInForeground = true
    private var mediaLifecycle = MediaLifecycle.Empty
    private var mediaGeneration = 0L
    private val speedApplyGate = PlaybackSpeedApplyGate()
    private val seekCoordinator = BoloPlayerSeekCoordinator()
    private val seekLock = NSLock()
    private val seekScope = MainScope()
    private var seekTimeoutJob: Job? = null
    private var seekReadbackJob: Job? = null
    private var seekabilityKnown = false
    private var playWhenReady = autoPlay
    private var debugNextNativeSubmissionFailure = false
    private var debugNextTimeout = false
    private var debugNextNotSeekable = false
    private var debugNativeSubmissionFailureRevision: Long? = null
    private var debugTimeoutRevision: Long? = null

    // 应用生命周期通知常驻到 dispose；播放器通知按 media generation 重建。
    private val applicationObservers = mutableListOf<Any>()
    private val playerObservers = mutableListOf<Any>()

    // ── 位置恢复 ──
    private var lastSavedPositionMs = 0L

    // ── 重建所需数据 ──
    private var refreshFrameAfterSeek = false
    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFilePath: String? = null
    private var playbackSpeed = BoloPlayerSpeed.default

    init {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, null)
            setActive(true, null)
        }

        // 后台 → 保存 + 释放
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            isInForeground = false
            release()
        }
        // 前台 → 重建
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            isInForeground = true
            restoreFromSavedState()
        }
    }

    /**
     * Composable 重建时调用 —— 绑定 drawable，若 VLC 已释放则重新加载媒体。
     * 类似 Android 的 bindVideo()。
     */
    fun bindDrawable(view: UIView) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        mediaPlayer.drawable = view
        if (isInForeground) {
            restoreFromSavedState()
        }
    }

    private fun onStateChanged(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        when (player.state) {
            VLCMediaPlayerState.VLCMediaPlayerStateOpening -> {
                mediaLifecycle = MediaLifecycle.Loading
                speedApplyGate.onOpening()
                _state.value = _state.value.copy(isBuffering = true)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateBuffering -> {
                val shouldPublishBuffering = mediaLifecycle == MediaLifecycle.Loading ||
                    _state.value.isPlaying ||
                    playWhenReady ||
                    withSeekLock { seekCoordinator.pendingPositionMs != null }
                if (shouldPublishBuffering) {
                    speedApplyGate.onBuffering()
                }
                _state.value = _state.value.copy(isBuffering = shouldPublishBuffering)
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                mediaLifecycle = MediaLifecycle.Loaded
                var videoCodec = ""
                var audioCodec = ""
                var videoWidth = 0
                var videoHeight = 0
                var videoBr: Long = 0L
                var audioBr: Long = 0L
                
                val tracks = player.media?.tracksInformation as? List<Map<Any?, Any?>>
                tracks?.forEach { track ->
                    val type = track["type"] as? String
                    if (type == "video") {
                        val codecObj = track["codec"]
                        if (codecObj is String) {
                            videoCodec = codecObj.uppercase()
                        } else if (codecObj is Number) {
                            videoCodec = intToFourCC(codecObj.toInt())
                        }
                        videoWidth = (track["width"] as? Number)?.toInt() ?: 0
                        videoHeight = (track["height"] as? Number)?.toInt() ?: 0
                        videoBr = (track["bitrate"] as? Number)?.toLong()?.takeIf { it > 0L } ?: 0L
                    } else if (type == "audio") {
                        val codecObj = track["codec"]
                        if (codecObj is String) {
                            audioCodec = codecObj.uppercase()
                        } else if (codecObj is Number) {
                            audioCodec = intToFourCC(codecObj.toInt())
                        }
                        audioBr = (track["bitrate"] as? Number)?.toLong()?.takeIf { it > 0L } ?: 0L
                    }
                }
                
                _state.value = _state.value.copy(
                    isPlaying = true, 
                    isBuffering = false,
                    videoCodec = videoCodec,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    videoBitrate = videoBr,
                    audioCodec = audioCodec,
                    audioBitrate = audioBr
                )
                if (speedApplyGate.onPlaying()) {
                    applyPlaybackSpeed(player, generation)
                }
                updateNativeDuration(player)
                refreshSeekability(player, generation, confirmUnavailable = true)
                submitPendingSeekIfReady(player, generation)
                pauseAfterStartupSeekIfNeeded(player, generation)
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onPaused()
                if (playWhenReady) {
                    handleEndReached(player, generation)
                } else {
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
            }
            VLCMediaPlayerState.VLCMediaPlayerStateStopped -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateEnded -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                handleEndReached(player, generation)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                cancelCurrentSeek()
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    pendingSeekPositionMs = null
                )
                onError(BoloPlayerError.UnknownError("VLC 播放错误"))
            }
            else -> Unit
        }
    }

    private fun updateTimeAndDuration(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        val posMs = player.time.value?.longValue ?: return
        if (posMs < 0L) return

        updateNativeDuration(player, posMs)
        refreshSeekability(player, generation, confirmUnavailable = true)
        acceptObservedTime(player, generation, posMs)
    }

    private fun updateNativeDuration(player: VLCMediaPlayer, positionMs: Long? = null) {
        val mediaLengthMs = player.media?.length?.value?.longValue ?: 0L
        if (mediaLengthMs > 0L) {
            _state.value = _state.value.copy(durationMs = mediaLengthMs)
            return
        }
        val posMs = positionMs ?: player.time.value?.longValue ?: return
        // VLCKit 的 remainingTime 通常为负值（表示剩余时间），无效时返回 0。
        val remaining = player.remainingTime?.value?.longValue ?: return
        if (remaining < 0) {
            val durationMs = if (remaining == Long.MIN_VALUE || posMs > Long.MAX_VALUE + remaining) {
                Long.MAX_VALUE
            } else {
                posMs - remaining
            }
            if (durationMs > 0L) {
                _state.value = _state.value.copy(durationMs = durationMs)
            }
        }
    }

    private fun savePositionFromCurrent() {
        lastSavedPositionMs = _state.value.displayPositionMs.coerceAtLeast(0L)
    }

    private fun restoreFromSavedState() {
        if (!isInForeground) return
        if (
            mediaLifecycle != MediaLifecycle.Empty &&
            mediaLifecycle != MediaLifecycle.Released
        ) {
            return
        }
        val mpd = lastMpd
        if (mpd == null) {
            return
        }
        loadInternal(mpd, restorePosition = true)
    }

    internal actual fun load(mpd: BoloDashMpd, startPositionMs: Long) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        val normalizedStartPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
        lastMpd = mpd
        lastSavedPositionMs = normalizedStartPositionMs
        if (!isInForeground) {
            mediaGeneration += 1L
            removePlayerObservers()
            speedApplyGate.onInactive()
            beginNewMediaGeneration()
            mediaPlayer.stop()
            mediaPlayer.media = null
            val pendingPositionMs = createStartupSeek(normalizedStartPositionMs)
            mediaLifecycle = MediaLifecycle.Released
            _state.value = BoloPlayerState(
                durationMs = mpd.durationMs,
                pendingSeekPositionMs = pendingPositionMs,
                playbackSpeed = playbackSpeed
            )
            return
        }
        loadInternal(
            mpd = mpd,
            restorePosition = false,
            startPositionMs = normalizedStartPositionMs
        )
    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        onError(error)
    }

    internal actual fun injectSeekFailureForDebug(
        nativeSubmissionFailure: Boolean,
        timeout: Boolean,
        notSeekable: Boolean
    ) {
        require(listOf(nativeSubmissionFailure, timeout, notSeekable).count { it } == 1) {
            "必须且只能注入一种 seek 故障"
        }
        withSeekLock {
            debugNextNativeSubmissionFailure = nativeSubmissionFailure
            debugNextTimeout = timeout
            debugNextNotSeekable = notSeekable
        }
    }

    private fun loadInternal(
        mpd: BoloDashMpd,
        restorePosition: Boolean,
        startPositionMs: Long = 0L
    ) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        mediaGeneration += 1
        val generation = mediaGeneration
        mediaLifecycle = MediaLifecycle.Loading
        beginNewMediaGeneration()
        removePlayerObservers()
        speedApplyGate.onInactive()
        val player = mediaPlayer
        player.stop()
        player.media = null

        val nsUrl = writeMpdFile(mpd)
        if (nsUrl == null) {
            mediaLifecycle = MediaLifecycle.Released
            _state.value = BoloPlayerState(playbackSpeed = playbackSpeed)
            onError(BoloPlayerError.UnknownError("DASH MPD 文件写入失败"))
            return
        }

        installPlayerObservers(player, generation)
        val media = VLCMedia(uRL = nsUrl)
        val options = mutableMapOf<Any?, Any?>("play-and-pause" to true)
        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> options["http-referrer"] = value
                "user-agent" -> options["http-user-agent"] = value
                else -> options["http-header-fields"] = "$key: $value"
            }
        }
        media.addOptions(options)
        speedApplyGate.onMediaChanged()
        player.media = media
        mediaLifecycle = MediaLifecycle.Loaded

        val requestedPositionMs = when {
            startPositionMs > 0L -> startPositionMs
            restorePosition && lastSavedPositionMs > 0L -> lastSavedPositionMs
            else -> 0L
        }
        val seekPositionMs = normalizePositionMs(requestedPositionMs, mpd.durationMs)
        val pendingPositionMs = createStartupSeek(seekPositionMs)
        _state.value = BoloPlayerState(
            durationMs = mpd.durationMs,
            pendingSeekPositionMs = pendingPositionMs,
            playbackSpeed = playbackSpeed
        )

        // 非零起点必须先进入 Playing 完成 VLCKit 输入初始化；最终是否暂停由 playWhenReady 决定。
        if (playWhenReady || pendingPositionMs != null) {
            mediaLifecycle = MediaLifecycle.Loading
            player.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    actual fun play() {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        playWhenReady = true
        mediaLifecycle = MediaLifecycle.Loading
        mediaPlayer.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    actual fun pause() {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        playWhenReady = false
        mediaPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekToMs(positionMs: Long) {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            onError(BoloPlayerError.SeekError("当前没有可跳转的媒体"))
            return
        }

        refreshFrameAfterSeek = !playWhenReady && (_state.value.isEnded || refreshFrameAfterSeek)
        updateNativeDuration(mediaPlayer)
        val targetPositionMs = normalizePositionMs(positionMs, _state.value.durationMs)
        var previousTimeoutJob: Job? = null
        var previousReadbackJob: Job? = null
        var injectNotSeekable = false
        val revision = withSeekLock {
            val revision = seekCoordinator.requestSeek(targetPositionMs)
            debugNativeSubmissionFailureRevision =
                revision.takeIf { debugNextNativeSubmissionFailure }
            debugTimeoutRevision = revision.takeIf { debugNextTimeout }
            injectNotSeekable = debugNextNotSeekable
            debugNextNativeSubmissionFailure = false
            debugNextTimeout = false
            debugNextNotSeekable = false
            previousTimeoutJob = seekTimeoutJob
            seekTimeoutJob = null
            previousReadbackJob = seekReadbackJob
            seekReadbackJob = null
            revision
        }
        previousTimeoutJob?.cancel()
        previousReadbackJob?.cancel()
        _state.value = _state.value.copy(pendingSeekPositionMs = targetPositionMs)

        if (injectNotSeekable) {
            failSeek(revision, "调试注入：当前媒体不支持跳转")
            return
        }

        if (seekabilityKnown && !_state.value.isSeekable) {
            failSeek(revision, "当前媒体不支持跳转")
            return
        }

        val state = mediaPlayer.state
        val canConfirmSeekability = state == VLCMediaPlayerState.VLCMediaPlayerStatePlaying ||
            state == VLCMediaPlayerState.VLCMediaPlayerStatePaused
        refreshSeekability(mediaPlayer, mediaGeneration, canConfirmSeekability)
        submitPendingSeekIfReady(mediaPlayer, mediaGeneration)

        if (!seekabilityKnown && !playWhenReady && state != VLCMediaPlayerState.VLCMediaPlayerStatePlaying) {
            mediaLifecycle = MediaLifecycle.Loading
            mediaPlayer.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    actual fun setVolumeGain(gain: Int) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayer.audio?.volume = vlcVolume
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        playbackSpeed = speed
        if (speedApplyGate.onSpeedRequested()) {
            applyPlaybackSpeed(mediaPlayer, mediaGeneration)
        } else {
            // 延迟到下一次 Playing 提交 native rate；UI 仍沿用现有的选中值展示语义。
            _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
        }
    }

    actual fun release() {
        if (
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }

        savePositionFromCurrent()
        mediaLifecycle = MediaLifecycle.Released
        mediaGeneration += 1
        removePlayerObservers()
        speedApplyGate.onInactive()
        beginNewMediaGeneration()
        mediaPlayer.stop()
        mediaPlayer.media = null
        _state.value = _state.value.copy(
            isPlaying = false,
            isBuffering = false,
            pendingSeekPositionMs = null,
            isSeekable = false
        )
    }

    actual fun dispose() {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        release()
        mediaLifecycle = MediaLifecycle.Disposed
        mediaGeneration += 1
        mediaPlayer.drawable = null
        removePlayerObservers()
        removeApplicationObservers()
        seekScope.cancel()
        removeMpdFile()
        lastMpd = null
        lastSavedPositionMs = 0L
        playWhenReady = false
        _state.value = BoloPlayerState(playbackSpeed = playbackSpeed)
    }

    private fun writeMpdFile(mpd: BoloDashMpd): NSURL? {
        val dir = NSTemporaryDirectory() + "/bolo_dash_mpd"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        lastMpdFilePath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        val path = "$dir/bolo_${time(null)}.mpd"
        val bytes = mpd.xml.encodeToByteArray()
        val file = fopen(path, "wb") ?: return null
        val written = bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }
        fclose(file)
        if (written != bytes.size.convert<ULong>()) {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
            return null
        }
        lastMpdFilePath = path
        return NSURL.fileURLWithPath(path)
    }

    private fun installPlayerObservers(player: VLCMediaPlayer, generation: Long) {
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerStateChanged, player, NSOperationQueue.mainQueue
        ) { _ ->
            if (isCurrentPlayerGeneration(player, generation)) {
                onStateChanged(player, generation)
            }
        }
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerTimeChanged, player, NSOperationQueue.mainQueue
        ) { _ ->
            if (isCurrentPlayerGeneration(player, generation)) {
                updateTimeAndDuration(player, generation)
            }
        }
    }

    private fun isCurrentPlayerGeneration(player: VLCMediaPlayer, generation: Long): Boolean {
        return mediaLifecycle != MediaLifecycle.Released &&
            mediaLifecycle != MediaLifecycle.Disposed &&
            player === mediaPlayer &&
            generation == mediaGeneration
    }

    private fun removePlayerObservers() {
        playerObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        playerObservers.clear()
    }

    private fun removeApplicationObservers() {
        applicationObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        applicationObservers.clear()
    }

    private fun removeMpdFile() {
        lastMpdFilePath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        lastMpdFilePath = null
    }

    private fun intToFourCC(codec: Int): String {
        return charArrayOf(
            (codec and 0xFF).toChar(),
            ((codec shr 8) and 0xFF).toChar(),
            ((codec shr 16) and 0xFF).toChar(),
            ((codec shr 24) and 0xFF).toChar()
        ).concatToString().uppercase()
    }

    private fun beginNewMediaGeneration() {
        refreshFrameAfterSeek = false
        val jobs = withSeekLock {
            val currentJobs = seekTimeoutJob to seekReadbackJob
            seekTimeoutJob = null
            seekReadbackJob = null
            seekCoordinator.onMediaChanged()
            seekabilityKnown = false
            clearDebugSeekInjectionLocked(clearNext = true)
            currentJobs
        }
        jobs.first?.cancel()
        jobs.second?.cancel()
    }

    private fun createStartupSeek(positionMs: Long): Long? {
        if (positionMs <= 0L) return null
        return withSeekLock {
            seekCoordinator.requestSeek(positionMs)
            positionMs
        }
    }

    private fun refreshSeekability(
        player: VLCMediaPlayer,
        generation: Long,
        confirmUnavailable: Boolean
    ) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        val nativeSeekable = player.seekable
        var failedRevision: Long? = null
        withSeekLock {
            if (nativeSeekable) {
                seekabilityKnown = true
            } else if (confirmUnavailable) {
                seekabilityKnown = true
                if (seekCoordinator.pendingPositionMs != null) {
                    failedRevision = seekCoordinator.currentRevision
                }
            }
        }
        if (!isCurrentPlayerGeneration(player, generation)) return
        _state.value = _state.value.copy(isSeekable = nativeSeekable)

        val revision = failedRevision
        if (revision != null) {
            failSeek(revision, "当前媒体不支持跳转")
        } else if (nativeSeekable) {
            submitPendingSeekIfReady(player, generation)
        }
    }

    private fun submitPendingSeekIfReady(
        player: VLCMediaPlayer,
        generation: Long,
        retry: Boolean = false
    ) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        if (!seekabilityKnown || !_state.value.isSeekable) return

        var coordinatorGeneration = 0L
        var revision = 0L
        var targetPositionMs = 0L
        var injectNativeSubmissionFailure = false
        var injectTimeout = false
        val shouldSubmit = withSeekLock {
            val pendingPositionMs = seekCoordinator.pendingPositionMs
            if (pendingPositionMs == null || (!retry && seekCoordinator.isSubmitted)) {
                false
            } else {
                coordinatorGeneration = seekCoordinator.currentMediaGeneration
                revision = seekCoordinator.currentRevision
                targetPositionMs = pendingPositionMs
                seekCoordinator.markSubmitted(revision).also { marked ->
                    if (marked) {
                        injectNativeSubmissionFailure =
                            debugNativeSubmissionFailureRevision == revision
                        injectTimeout = debugTimeoutRevision == revision
                    }
                }
            }
        }
        if (!shouldSubmit) return

        if (
            !isCurrentPlayerGeneration(player, generation) ||
            !withSeekLock { seekCoordinator.isCurrent(coordinatorGeneration, revision) }
        ) {
            return
        }

        try {
            if (injectNativeSubmissionFailure) {
                throw IllegalStateException("调试注入：VLCKit 提交跳转失败")
            }
            if (!injectTimeout) {
                player.time = VLCTime.timeWithNumber(NSNumber(longLong = targetPositionMs))
                // EOF 暂停的解码器需要推进一帧，单独更新时间不会刷新画面。
                if (refreshFrameAfterSeek && !playWhenReady) player.gotoNextFrame()
            }
        } catch (error: Throwable) {
            failSeek(revision, "VLCKit 提交跳转失败", error)
            return
        }

        val submittedAttempt = withSeekLock { seekCoordinator.submittedAttempt }
        scheduleSeekTimeout(player, generation, coordinatorGeneration, revision)
        scheduleSeekReadback(
            player = player,
            generation = generation,
            coordinatorGeneration = coordinatorGeneration,
            revision = revision,
            submittedAttempt = submittedAttempt
        )
    }

    /** 暂停态可能不发送时间通知；用短周期绝对时间 readback 补足观测，超时策略保持不变。 */
    private fun scheduleSeekReadback(
        player: VLCMediaPlayer,
        generation: Long,
        coordinatorGeneration: Long,
        revision: Long,
        submittedAttempt: Int
    ) {
        val nextJob = seekScope.launch {
            try {
                repeat(BoloPlayerSeekCoordinator.ConfirmationReadbackAttempts) {
                    delay(BoloPlayerSeekCoordinator.ConfirmationReadbackIntervalMs)
                    if (!isCurrentPlayerGeneration(player, generation)) return@launch
                    val isStillCurrent = withSeekLock {
                        seekCoordinator.isCurrent(coordinatorGeneration, revision) &&
                            seekCoordinator.submittedAttempt == submittedAttempt
                    }
                    if (!isStillCurrent) return@launch

                    val nativePositionMs = player.time.value?.longValue
                        ?.takeIf { it >= 0L }
                        ?: return@repeat
                    val confirmed = acceptObservedTime(
                        player = player,
                        generation = generation,
                        positionMs = nativePositionMs,
                        expectedCoordinatorGeneration = coordinatorGeneration,
                        expectedRevision = revision,
                        expectedSubmittedAttempt = submittedAttempt
                    )
                    if (confirmed) return@launch
                }
            } finally {
                withSeekLock {
                    if (
                        seekCoordinator.isCurrent(coordinatorGeneration, revision) &&
                        seekCoordinator.submittedAttempt == submittedAttempt
                    ) {
                        seekReadbackJob = null
                    }
                }
            }
        }
        val previousJob = withSeekLock {
            if (
                seekCoordinator.isCurrent(coordinatorGeneration, revision) &&
                seekCoordinator.submittedAttempt == submittedAttempt
            ) {
                val currentJob = seekReadbackJob
                seekReadbackJob = nextJob
                currentJob
            } else {
                nextJob.cancel()
                null
            }
        }
        previousJob?.cancel()
    }

    private fun scheduleSeekTimeout(
        player: VLCMediaPlayer,
        generation: Long,
        coordinatorGeneration: Long,
        revision: Long
    ) {
        val nextJob = seekScope.launch {
            delay(BoloPlayerSeekCoordinator.AttemptTimeoutMs)
            handleSeekTimeout(player, generation, coordinatorGeneration, revision)
        }
        val previousJob = withSeekLock {
            if (seekCoordinator.isCurrent(coordinatorGeneration, revision)) {
                val currentJob = seekTimeoutJob
                seekTimeoutJob = nextJob
                currentJob
            } else {
                nextJob.cancel()
                null
            }
        }
        previousJob?.cancel()
    }

    private fun handleSeekTimeout(
        player: VLCMediaPlayer,
        generation: Long,
        coordinatorGeneration: Long,
        revision: Long
    ) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        if (!withSeekLock { seekCoordinator.isCurrent(coordinatorGeneration, revision) }) return

        val forceTimeout = withSeekLock {
            seekCoordinator.isCurrent(coordinatorGeneration, revision) &&
                debugTimeoutRevision == revision
        }
        val nativePositionMs = if (forceTimeout) {
            null
        } else {
            player.time.value?.longValue?.takeIf { it >= 0L }
        }
        if (nativePositionMs != null && acceptObservedTime(player, generation, nativePositionMs)) {
            return
        }

        val shouldRetry = withSeekLock {
            seekCoordinator.isCurrent(coordinatorGeneration, revision) &&
                seekCoordinator.canRetry(revision)
        }
        if (shouldRetry) {
            submitPendingSeekIfReady(player, generation, retry = true)
            return
        }

        failSeek(
            revision = revision,
            message = "VLCKit 跳转两次尝试均超时",
            actualPositionMs = nativePositionMs
        )
    }

    private fun acceptObservedTime(
        player: VLCMediaPlayer,
        generation: Long,
        positionMs: Long,
        expectedCoordinatorGeneration: Long? = null,
        expectedRevision: Long? = null,
        expectedSubmittedAttempt: Int? = null
    ): Boolean {
        if (!isCurrentPlayerGeneration(player, generation) || positionMs < 0L || _state.value.isEnded) return false

        var hadPending = false
        var accepted = false
        var timeoutJob: Job? = null
        var readbackJob: Job? = null
        var pendingTargetMs: Long? = null
        var pendingRevision: Long? = null
        withSeekLock {
            if (
                expectedCoordinatorGeneration != null &&
                expectedRevision != null &&
                expectedSubmittedAttempt != null &&
                (!seekCoordinator.isCurrent(expectedCoordinatorGeneration, expectedRevision) ||
                    seekCoordinator.submittedAttempt != expectedSubmittedAttempt)
            ) {
                return false
            }
            pendingTargetMs = seekCoordinator.pendingPositionMs
            hadPending = pendingTargetMs != null
            pendingRevision = pendingTargetMs?.let { seekCoordinator.currentRevision }
            val forceTimeout = pendingRevision != null && debugTimeoutRevision == pendingRevision
            accepted = !forceTimeout && seekCoordinator.acceptObservedPosition(
                    positionMs = positionMs,
                    isPlaying = player.state == VLCMediaPlayerState.VLCMediaPlayerStatePlaying,
                    playbackRate = _state.value.playbackSpeed.rateNumber
                )
            if (hadPending && accepted) {
                refreshFrameAfterSeek = false
                timeoutJob = seekTimeoutJob
                seekTimeoutJob = null
                readbackJob = seekReadbackJob
                seekReadbackJob = null
                pendingRevision?.let(::clearDebugSeekRevisionLocked)
            }
        }

        if (!accepted) {
            _state.value = _state.value.copy(transferSpeed = 0L)
            return false
        }

        val durationMs = _state.value.durationMs
        val completedAtEnd = pendingTargetMs != null && durationMs > 0L &&
            pendingTargetMs >= (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                .coerceAtLeast(0L) &&
            positionMs >= (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                .coerceAtLeast(0L)
        val confirmedPositionMs = if (completedAtEnd) durationMs else positionMs
        val nativeIsPlaying = player.state == VLCMediaPlayerState.VLCMediaPlayerStatePlaying
        _state.value = _state.value.copy(
            isPlaying = if (completedAtEnd) false else _state.value.isPlaying,
            isBuffering = if (completedAtEnd || !nativeIsPlaying || !playWhenReady) {
                false
            } else {
                _state.value.isBuffering
            },
            currentPositionMs = confirmedPositionMs,
            pendingSeekPositionMs = if (hadPending) null else _state.value.pendingSeekPositionMs,
            transferSpeed = 0L
        )
        lastSavedPositionMs = confirmedPositionMs
        timeoutJob?.cancel()
        readbackJob?.cancel()
        if (completedAtEnd) {
            speedApplyGate.onInactive()
        } else if (hadPending) {
            pauseAfterStartupSeekIfNeeded(player, generation)
        }
        return true
    }

    private fun failSeek(
        revision: Long,
        message: String,
        cause: Throwable? = null,
        actualPositionMs: Long? = null
    ) {
        var timeoutJob: Job? = null
        var readbackJob: Job? = null
        val didCancel = withSeekLock {
            if (!seekCoordinator.cancelSeek(revision)) {
                false
            } else {
                clearDebugSeekRevisionLocked(revision)
                timeoutJob = seekTimeoutJob
                seekTimeoutJob = null
                readbackJob = seekReadbackJob
                seekReadbackJob = null
                true
            }
        }
        if (!didCancel) return

        timeoutJob?.cancel()
        readbackJob?.cancel()
        _state.value = _state.value.copy(
            currentPositionMs = actualPositionMs?.coerceAtLeast(0L) ?: _state.value.currentPositionMs,
            pendingSeekPositionMs = null,
            isBuffering = if (!playWhenReady) false else _state.value.isBuffering
        )
        pauseAfterStartupSeekIfNeeded(mediaPlayer, mediaGeneration)
        onError(BoloPlayerError.SeekError(message, cause))
    }

    private fun cancelCurrentSeek() {
        var timeoutJob: Job? = null
        var readbackJob: Job? = null
        withSeekLock {
            seekCoordinator.cancelCurrentSeek()
            clearDebugSeekInjectionLocked(clearNext = false)
            timeoutJob = seekTimeoutJob
            seekTimeoutJob = null
            readbackJob = seekReadbackJob
            seekReadbackJob = null
        }
        timeoutJob?.cancel()
        readbackJob?.cancel()
    }

    private fun pauseAfterStartupSeekIfNeeded(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        if (playWhenReady || _state.value.pendingSeekPositionMs != null) return
        if (player.state != VLCMediaPlayerState.VLCMediaPlayerStatePlaying) return

        player.pause()
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
    }

    private fun handleEndReached(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        val durationMs = getDurationForCompletion(player)
        var failedRevision: Long? = null
        var timeoutJob: Job? = null
        var readbackJob: Job? = null
        withSeekLock {
            val targetPositionMs = seekCoordinator.pendingPositionMs
            if (targetPositionMs != null) {
                val lowerBoundMs = (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                    .coerceAtLeast(0L)
                val upperBoundMs = saturatingAdd(
                    durationMs,
                    BoloPlayerSeekCoordinator.ConfirmationToleranceMs
                )
                val confirmed = targetPositionMs in lowerBoundMs..upperBoundMs &&
                    seekCoordinator.acceptObservedPosition(
                        positionMs = durationMs,
                        isPlaying = false,
                        playbackRate = _state.value.playbackSpeed.rateNumber
                    )
                if (!confirmed) {
                    failedRevision = seekCoordinator.currentRevision
                    seekCoordinator.cancelSeek(seekCoordinator.currentRevision)
                }
                clearDebugSeekRevisionLocked(seekCoordinator.currentRevision)
                timeoutJob = seekTimeoutJob
                seekTimeoutJob = null
                readbackJob = seekReadbackJob
                seekReadbackJob = null
            }
        }
        timeoutJob?.cancel()
        readbackJob?.cancel()

        playWhenReady = false
        _state.value = _state.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentPositionMs = durationMs,
            durationMs = durationMs,
            pendingSeekPositionMs = null
        )
        lastSavedPositionMs = durationMs

        if (failedRevision != null) {
            onError(BoloPlayerError.SeekError("VLCKit 在未到达跳转目标时结束播放"))
        }
    }

    private fun getDurationForCompletion(player: VLCMediaPlayer): Long {
        val nativeDurationMs = player.media?.length?.value?.longValue ?: 0L
        if (nativeDurationMs > 0L) return nativeDurationMs
        if (_state.value.durationMs > 0L) return _state.value.durationMs
        return _state.value.currentPositionMs.coerceAtLeast(0L)
    }

    private fun normalizePositionMs(positionMs: Long, durationMs: Long): Long {
        val nonNegativePositionMs = positionMs.coerceAtLeast(0L)
        return if (durationMs > 0L) {
            nonNegativePositionMs.coerceAtMost(durationMs)
        } else {
            nonNegativePositionMs
        }
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun clearDebugSeekRevisionLocked(revision: Long) {
        refreshFrameAfterSeek = false
        if (debugNativeSubmissionFailureRevision == revision) {
            debugNativeSubmissionFailureRevision = null
        }
        if (debugTimeoutRevision == revision) {
            debugTimeoutRevision = null
        }
    }

    private fun clearDebugSeekInjectionLocked(clearNext: Boolean) {
        debugNativeSubmissionFailureRevision = null
        debugTimeoutRevision = null
        if (clearNext) {
            debugNextNativeSubmissionFailure = false
            debugNextTimeout = false
            debugNextNotSeekable = false
        }
    }

    private inline fun <T> withSeekLock(block: () -> T): T {
        seekLock.lock()
        return try {
            block()
        } finally {
            seekLock.unlock()
        }
    }

    private fun applyPlaybackSpeed(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        try {
            player.rate = playbackSpeed.rateNumber
            playbackSpeed = BoloPlayerSpeed.fromRateNumber(player.rate)
        } catch (_: Exception) {}
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }
}
