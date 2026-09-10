package tv.hsrui.bolo.player.base

import kotlinx.cinterop.useContents
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import cocoapods.VLCKit.VLCLibrary
import cocoapods.VLCKit.VLCMedia
import cocoapods.VLCKit.VLCMediaPlayer
import cocoapods.VLCKit.VLCMediaPlayerDelegateProtocol
import cocoapods.VLCKit.VLCMediaPlayerState
import cocoapods.VLCKit.VLCMediaTrack
import cocoapods.VLCKit.VLCMediaTrackTypeAudio
import cocoapods.VLCKit.VLCMediaTrackTypeVideo
import cocoapods.VLCKit.VLCTime
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputLatency
import platform.AVFAudio.outputVolume
import platform.AVFAudio.sampleRate
import platform.AVFAudio.IOBufferDuration
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.darwin.NSObject
import cocoapods.VLCKit.VLCMediaPlayerStateChangedNotification
import cocoapods.VLCKit.VLCMediaPlayerTimeChangedNotification
import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np
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

    /**
     * 倍速提交门。
     *
     * VLC 4 移除了 Buffering 状态，缓冲进度改由 delegate 上报，且缓冲结束不会再产生一次
     * Playing 状态变更。因此这里不能再用 buffering 回调锁定相位：那样会让之后所有倍速
     * 请求都停在"延迟提交"分支，而没有任何补提交时机。门只区分媒体是否已挂载可写 rate。
     */
    private class PlaybackSpeedApplyGate {
        private enum class Phase {
            Inactive,
            Loading,
            Ready
        }

        private var phase = Phase.Inactive
        private var mediaGeneration = 0L
        private var readyGeneration = -1L

        fun onMediaChanged() {
            mediaGeneration += 1
            phase = Phase.Loading
        }

        fun onOpening() {
            phase = Phase.Loading
        }

        /** 媒体输入已建立；返回 true 表示该媒体代次尚未下发过倍速，调用方必须补提交一次。 */
        fun onMediaReady(): Boolean {
            phase = Phase.Ready
            if (readyGeneration == mediaGeneration) return false
            readyGeneration = mediaGeneration
            return true
        }

        fun onInactive() {
            phase = Phase.Inactive
            readyGeneration = -1L
        }

        /** 媒体已就绪时允许直接写 native rate；否则由就绪分支补提交。 */
        fun canApplyNow(): Boolean = phase == Phase.Ready
    }

    /** 把 VLCKit 回调工作移出回调栈用的调度范围（短延迟后切回主线程）。 */
    private val deferScope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal val mediaPlayer = VLCMediaPlayer()
    // VLCKit 4 移除了 Buffering 状态，缓冲进度只通过 delegate 上报，这里持有强引用避免 delegate 被释放。
    private val playerDelegates = mutableListOf<NSObject>()
    private var isInForeground = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    private var resumeAfterBackgroundEnabled = false
    private var backgroundStartedMs: Long? = null
    private var backgroundPositionMs = 0L
    private var backgroundEnded = false
    private var resumeEligible = false
    private var needsRestoreSeek = false
    private var outputNeedsRefresh = false
    private var restoringPlayback = false
    private var recoveryFailed = false
    private var lifecycleRevision = 0L
    private var backgroundReleaseJob: Job? = null
    private var recoveryJob: Job? = null
    private var boundDrawable: UIView? = null
    private var seekFrameHolder: UIView? = null
    private var seekFrameWatchJob: Job? = null
    private var volumeGain = 100
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
            // 音频会话仅在实际启动媒体时激活。
        }

        // 后台 → 保存 + 释放
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            suspendPlayback()
        }
        // 前台 → 重建
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            isInForeground = true
            resumePlaybackIfReady()
        }
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidReceiveMemoryWarningNotification, null, NSOperationQueue.mainQueue
        ) { _ ->
            if (!isInForeground) releaseResources()
        }
    }

    /**
     * Composable 重建时调用 —— 绑定 drawable，若 VLC 已释放则重新加载媒体。
     * 类似 Android 的 bindVideo()。
     */
    fun bindDrawable(view: UIView) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return
        if (boundDrawable !== view) outputNeedsRefresh = true
        boundDrawable = view
        if (!isInForeground || view.window == null) return
        if (mediaPlayer.drawable !== view) mediaPlayer.drawable = view
        if (_state.value.isPlaybackSuspended) {
            resumePlaybackIfReady()
        } else if (mediaLifecycle != MediaLifecycle.Loading) {
            restoreFromSavedState()
        }
    }

    fun unbindDrawable(view: UIView) {
        if (boundDrawable !== view) return
        removeSeekFrameHolder()
        boundDrawable = null
        mediaPlayer.drawable = null
        outputNeedsRefresh = true
    }

    /**
     * 跳转空档保留上一帧。
     *
     * libvlc 4 的精确跳转会重建解码器，video context 随之变化，`vout_ChangeSource()` 因此失败，
     * 整个 video output 被销毁重建；Apple 平台默认的 `samplebufferdisplay` 使用全新的
     * `AVSampleBufferDisplayLayer`，在首个 sample buffer 入队前没有任何内容，旧显示视图又已被移除，
     * 于是跳转期间露出黑底。VLC 3 的 vout 不在跳转时重建，上一帧始终留在屏幕上，所以旧版不闪。
     *
     * 这里在提交跳转前把当前画面快照放到渲染视图下层：VLC 视图在场时被完全覆盖，
     * VLC 视图被移除的空档由快照顶上，等新帧显示或超时后再移除，观感与旧版一致。
     */
    private fun holdLastFrameForSeek() {
        val view = boundDrawable ?: return
        if (view.window == null || _state.value.isPlaybackSuspended) return
        removeSeekFrameHolder()
        // 容器里已有 VLC 的渲染视图，才说明屏幕上有画面可保留；首次加载阶段不放快照。
        if (view.subviews.isEmpty()) return
        val snapshot = view.snapshotViewAfterScreenUpdates(false) ?: return
        snapshot.setFrame(view.bounds)
        snapshot.setAutoresizingMask(
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        )
        view.addSubview(snapshot)
        view.sendSubviewToBack(snapshot)
        seekFrameHolder = snapshot
        val baselinePictures = displayedPictures()
        seekFrameWatchJob = seekScope.launch {
            val deadline = continuousTimeMs() + SeekFrameHoldTimeoutMs
            while (continuousTimeMs() < deadline) {
                delay(SeekFrameWatchIntervalMs)
                val pictures = displayedPictures()
                if (pictures != null && baselinePictures != null && pictures > baselinePictures) break
            }
            detachSeekFrameHolder()
        }
    }

    private fun detachSeekFrameHolder() {
        seekFrameHolder?.removeFromSuperview()
        seekFrameHolder = null
    }

    private fun removeSeekFrameHolder() {
        seekFrameWatchJob?.cancel()
        seekFrameWatchJob = null
        detachSeekFrameHolder()
    }

    actual fun setResumeAfterBackgroundEnabled(enabled: Boolean) {
        resumeAfterBackgroundEnabled = enabled
        if (!enabled) resumeEligible = false
    }

    /** 含息屏时间的单调时钟；`CLOCK_MONOTONIC_RAW` 对应 Darwin 的 mach_continuous_time。 */
    private fun continuousTimeMs(): Long =
        (clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt()) / 1_000_000uL).toLong()

    private fun suspendPlayback() {
        if (!isInForeground || mediaLifecycle == MediaLifecycle.Disposed) return
        val wasRestoring = restoringPlayback
        isInForeground = false
        lifecycleRevision += 1L
        recoveryJob?.cancel()
        restoringPlayback = false
        backgroundReleaseJob?.cancel()
        backgroundStartedMs = continuousTimeMs()
        if (!wasRestoring) {
            backgroundPositionMs = _state.value.displayPositionMs
            backgroundEnded = _state.value.isEnded
            resumeEligible = playWhenReady && !backgroundEnded
        }
        needsRestoreSeek = needsRestoreSeek || _state.value.isSeeking ||
            mediaLifecycle != MediaLifecycle.Loaded || wasRestoring
        playWhenReady = false
        cancelCurrentSeek()
        _state.value = _state.value.copy(
            isPlaybackSuspended = true, isPlaying = false, isBuffering = false,
            currentPositionMs = backgroundPositionMs, pendingSeekPositionMs = null,
        )
        mediaPlayer.pause()
        mediaPlayer.drawable = null
        outputNeedsRefresh = true
        AVAudioSession.sharedInstance().setActive(false, null)
        val revision = lifecycleRevision
        backgroundReleaseJob = seekScope.launch {
            delay(60_000L)
            if (revision == lifecycleRevision && !isInForeground) releaseResources()
        }
    }

    /** 高于该值的画面计数只可能来自未初始化内存，视为不可用。 */
    private fun sanitizePictureCount(pictures: ULong): Int? =
        pictures.takeIf { it <= MaxPlausibleDisplayedPictures }?.toInt()

    /**
     * 读取已显示画面计数。
     *
     * `-mediaPlayer.media.statistics` 在 `libvlc_media_get_stats` 失败时会直接拷贝未初始化的
     * `libvlc_media_stats_t`，应用侧无法从返回值判断有效性，只能按量级过滤；越界一律当作未知。
     * 该计数只允许作为"画面是否更新"的提示，不能作为恢复完成或 seek 的判定条件。
     */
    private fun displayedPictures(): Int? = mediaPlayer.media?.statistics?.useContents {
        sanitizePictureCount(displayedPictures)
    }

    private fun resumePlaybackIfReady() {
        if (!isInForeground || mediaLifecycle == MediaLifecycle.Disposed ||
            !_state.value.isPlaybackSuspended || restoringPlayback) return
        val view = boundDrawable ?: return
        if (view.window == null) return
        backgroundReleaseJob?.cancel()
        val startedMs = backgroundStartedMs
        if (startedMs != null && continuousTimeMs() - startedMs >= 60_000L) releaseResources()
        backgroundStartedMs = null
        restoringPlayback = true
        val revision = ++lifecycleRevision
        recoveryJob = seekScope.launch {
            try {
                for (attempt in 0..1) {
                    if (revision != lifecycleRevision || !isInForeground) return@launch
                    val mpd = lastMpd
                    if (mpd == null) {
                        recoveryFailed = false
                        _state.value = _state.value.copy(isPlaybackSuspended = false)
                        return@launch
                    }
                    if (attempt > 0) {
                        releaseResources()
                        recoveryFailed = false
                    }
                    val recreate = mediaLifecycle == MediaLifecycle.Released || mediaPlayer.media == null || attempt > 0
                    mediaPlayer.drawable = view
                    val previousPictures = if (recreate) null else displayedPictures()
                    val targetMs = if (backgroundEnded) (backgroundPositionMs - 50L).coerceAtLeast(0L) else backgroundPositionMs
                    mediaPlayer.audio?.volume = 0
                    if (recreate) {
                        recoveryFailed = false
                        loadInternal(mpd, restorePosition = false, startPositionMs = targetMs)
                    } else {
                        // 媒体仍在时只需要把画面拉回后台前位置；pending seek 由 seek 提交流程下发。
                        seekToMsInternal(targetMs, holdFrame = false)
                    }
                    val restored = withTimeoutOrNull(RestoreTimeoutMs) {
                        while (!recoveryFailed) {
                            val playback = _state.value
                            // 恢复完成以位置为准：VLC 4 的画面计数在暂停态不增长，
                            // 且底层读取可能返回未初始化值，不能作为完成条件。
                            val seekAccepted = isRestorePositionAccepted(
                                positionMs = playback.currentPositionMs,
                                targetMs = backgroundPositionMs,
                                observingSeek = !recreate &&
                                    withSeekLock { seekCoordinator.pendingPositionMs != null }
                            )
                            val reachable = _state.value.isSeekable || (seekabilityKnown && !_state.value.isSeekable)
                            if (!playback.isBuffering && reachable && seekAccepted) break
                            delay(RestorePollIntervalMs)
                        }
                        !recoveryFailed
                    } == true
                    if (!restored) continue
                    if (revision != lifecycleRevision || !isInForeground) return@launch
                    mediaPlayer.pause()
                    mediaPlayer.audio?.volume = volumeGain
                    applyPendingPlaybackSpeed(mediaPlayer, mediaGeneration)
                    val shouldResume = resumeAfterBackgroundEnabled && resumeEligible && !backgroundEnded
                    resumeEligible = false
                    needsRestoreSeek = false
                    outputNeedsRefresh = false
                    _state.value = _state.value.copy(
                        isPlaybackSuspended = false, isPlaying = false, isBuffering = false,
                        currentPositionMs = if (backgroundEnded) _state.value.durationMs else _state.value.currentPositionMs,
                    )
                    restoringPlayback = false
                    if (shouldResume) play() else AVAudioSession.sharedInstance().setActive(false, null)
                    observeRestoredFrame(previousPictures, revision)
                    return@launch
                }
                if (revision == lifecycleRevision && isInForeground) {
                    releaseResources()
                    resumeEligible = false
                    _state.value = _state.value.copy(isPlaybackSuspended = false, isPlaying = false, isBuffering = false)
                    onError(BoloPlayerError.UnknownError("播放器恢复失败，请重新打开视频"))
                }
            } finally {
                if (revision == lifecycleRevision) restoringPlayback = false
            }
        }
    }

    /**
     * 恢复位置是否已被 native 接受。
     *
     * 有 pending seek 时只认 seek 确认：跳转目标可能远离当前帧，`displayPositionMs` 会被
     * pending 目标污染，不能当成位置已回位。等待 seek 确认期间允许位置越过目标
     * [MaxRestoreDriftMs]，但暂停态 seek 不前进，越界只可能来自错误回读。
     */
    private fun isRestorePositionAccepted(positionMs: Long, targetMs: Long, observingSeek: Boolean): Boolean {
        if (observingSeek) {
            val driftMs = positionMs - targetMs
            return driftMs >= -BoloPlayerSeekCoordinator.ConfirmationToleranceMs &&
                driftMs <= MaxRestoreDriftMs
        }
        return kotlin.math.abs(positionMs - targetMs) <= BoloPlayerSeekCoordinator.ConfirmationToleranceMs
    }

    /**
     * 恢复完成后异步等待新画面呈现。
     *
     * 画面计数缺失时不做任何判断：恢复是否成立已由位置确认，画面呈现只是观感收尾。
     */
    private fun observeRestoredFrame(baselinePictures: Int?, revision: Long) {
        if (baselinePictures == null) return
        seekScope.launch {
            val deadline = continuousTimeMs() + RestoreFrameObserveMs
            while (continuousTimeMs() < deadline) {
                delay(SeekFrameWatchIntervalMs)
                if (revision != lifecycleRevision || !isInForeground) return@launch
                val pictures = displayedPictures() ?: return@launch
                if (pictures > baselinePictures) return@launch
            }
        }
    }

    /** VLCKit 4 的 Buffering 状态已移除，缓冲进度经 delegate 回调恢复原有发布与倍速 gate 语义。 */
    private fun handleBufferingChanged(player: VLCMediaPlayer, progress: Float) {
        if (player !== mediaPlayer || !isInForeground) return
        if (mediaLifecycle == MediaLifecycle.Released || mediaLifecycle == MediaLifecycle.Disposed) return
        // progress >= 1 是唯一可信的"缓冲完成"信号；其余进度值只在真正需要缓冲时发布。
        val shouldPublishBuffering = mediaLifecycle == MediaLifecycle.Loading ||
            _state.value.isPlaying ||
            playWhenReady ||
            withSeekLock { seekCoordinator.pendingPositionMs != null }
        _state.value = _state.value.copy(
            isBuffering = if (progress >= 1.0f) false else shouldPublishBuffering
        )
    }

    private fun onStateChanged(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        logAudioPlayback("STATE")
        if (!isInForeground) {
            if (player.state == VLCMediaPlayerState.VLCMediaPlayerStatePlaying) player.pause()
            if (player.state == VLCMediaPlayerState.VLCMediaPlayerStateError) recoveryFailed = true
            return
        }

        when (player.state) {
            VLCMediaPlayerState.VLCMediaPlayerStateOpening -> {
                mediaLifecycle = MediaLifecycle.Loading
                speedApplyGate.onOpening()
                _state.value = _state.value.copy(isBuffering = true)
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                mediaLifecycle = MediaLifecycle.Loaded
                var videoCodec = ""
                var audioCodec = ""
                var videoWidth = 0
                var videoHeight = 0
                var videoBr: Long = 0L
                var audioBr: Long = 0L
                
                // VLCKit 4 的 tracksInformation 由字典数组改为 VLCMediaTrack 对象数组。
                val tracks = player.media?.tracksInformation as? List<VLCMediaTrack>
                tracks?.forEach { track ->
                    when (track.type) {
                        VLCMediaTrackTypeVideo -> {
                            videoCodec = track.codecName().uppercase()
                            videoWidth = (track.video?.width ?: 0u).toInt()
                            videoHeight = (track.video?.height ?: 0u).toInt()
                            videoBr = track.bitrate.toLong().takeIf { it > 0L } ?: 0L
                        }
                        VLCMediaTrackTypeAudio -> {
                            audioCodec = track.codecName().uppercase()
                            audioBr = track.bitrate.toLong().takeIf { it > 0L } ?: 0L
                        }
                        else -> Unit
                    }
                }
                
                _state.value = _state.value.copy(
                    isPlaying = !_state.value.isPlaybackSuspended, 
                    isBuffering = false,
                    videoCodec = videoCodec,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    videoBitrate = videoBr,
                    audioCodec = audioCodec,
                    audioBitrate = audioBr
                )
                // 本函数已由 deferOffCallback 移出回调栈，此处可安全访问播放器。
                applyPendingPlaybackSpeed(player, generation)
                updateNativeDuration(player)
                refreshSeekability(player, generation, confirmUnavailable = true)
                submitPendingSeekIfReady(player, generation)
                pauseAfterStartupSeekIfNeeded(player, generation)
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                mediaLifecycle = MediaLifecycle.Loaded
                // 暂停态同样代表媒体输入已建立；启动 seek 完成后会在此补提交倍速。
                applyPendingPlaybackSpeed(player, generation)
                if (playWhenReady) {
                    handleEndReached(player, generation)
                } else {
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
            }
            // VLCKit 4 不再提供 Ended 状态，播放结束由 Stopped 上报。
            VLCMediaPlayerState.VLCMediaPlayerStateStopped -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                handleEndReached(player, generation)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                if (restoringPlayback) recoveryFailed = true
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

        logAudioPlayback("TIME")
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
        if (restoringPlayback || _state.value.isPlaybackSuspended) return
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

        lifecycleRevision += 1L
        recoveryJob?.cancel()
        restoringPlayback = false
        resumeEligible = false
        backgroundEnded = false
        val normalizedStartPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
        if (_state.value.isPlaybackSuspended) {
            backgroundPositionMs = normalizedStartPositionMs
            needsRestoreSeek = true
        }
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
                isPlaybackSuspended = true,
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

        removeSeekFrameHolder()
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
        val suspended = _state.value.isPlaybackSuspended

        val requestedPositionMs = when {
            startPositionMs > 0L -> startPositionMs
            restorePosition && lastSavedPositionMs > 0L -> lastSavedPositionMs
            else -> 0L
        }
        val seekPositionMs = normalizePositionMs(requestedPositionMs, mpd.durationMs)
        val pendingPositionMs = createStartupSeek(seekPositionMs)
        _state.value = BoloPlayerState(
            isPlaybackSuspended = suspended,
            durationMs = mpd.durationMs,
            pendingSeekPositionMs = pendingPositionMs,
            playbackSpeed = playbackSpeed
        )

        // 非零起点必须先进入 Playing 完成 VLCKit 输入初始化；最终是否暂停由 playWhenReady 决定。
        if (isInForeground && (playWhenReady || pendingPositionMs != null)) {
            mediaLifecycle = MediaLifecycle.Loading
            AVAudioSession.sharedInstance().setActive(true, null)
            player.audio?.volume = if (suspended) 0 else volumeGain
            player.play()
            _state.value = _state.value.copy(isPlaying = !suspended)
        }
    }

    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    actual fun play() {
        if (!isInForeground || _state.value.isPlaybackSuspended) return
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        playWhenReady = true
        logAudioPlayback("PLAY_REQUEST")
        val startedMs = continuousTimeMs()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val succeeded = AVAudioSession.sharedInstance().setActive(true, error.ptr)
            println(
                "[AudioPlayback] timeMs=${continuousTimeMs()} event=ACTIVATE success=$succeeded " +
                    "durationMs=${continuousTimeMs() - startedMs} errorDomain=${error.value?.domain} errorCode=${error.value?.code}"
            )
        }
        mediaPlayer.audio?.volume = volumeGain
        mediaLifecycle = MediaLifecycle.Loading
        mediaPlayer.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    actual fun pause() {
        resumeEligible = false
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        logAudioPlayback("PAUSE_REQUEST")
        playWhenReady = false
        mediaPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /**
     * 只读取自有状态与 AVAudioSession；不再读取 VLCMediaPlayer 的 rate/time/audio/statistics。
     * VLCKit 4 的这些属性会同步进入 libvlc（`libvlc_media_player_get_rate` 等），
     * 播放线程进入终态后调用会在主线程永久阻塞 —— 这不是版本升级能接受的代价。
     */
    private fun logAudioPlayback(event: String) {
        val session = AVAudioSession.sharedInstance()
        val routes = session.currentRoute.outputs.map { (it as? platform.AVFAudio.AVAudioSessionPortDescription)?.portType }
        if (event == "PLAY_REQUEST") {
            val library = VLCLibrary.sharedLibrary()
            println("[AudioPlayback] timeMs=${continuousTimeMs()} event=ENGINE version=${library.version} changeset=${library.changeset}")
        }
        println(
            "[AudioPlayback] timeMs=${continuousTimeMs()} event=$event generation=$mediaGeneration " +
                "lifecycle=$mediaLifecycle suspended=${_state.value.isPlaybackSuspended} " +
                "positionMs=${_state.value.currentPositionMs} rate=${playbackSpeed.rateNumber} vlcVolume=$volumeGain " +
                "systemVolume=${session.outputVolume} category=${session.category} mode=${session.mode} routeTypes=$routes " +
                "sampleRate=${session.sampleRate} ioBufferSec=${session.IOBufferDuration} outputLatencySec=${session.outputLatency}"
        )
    }

    actual fun seekToMs(positionMs: Long) {
        if (!isInForeground || (_state.value.isPlaybackSuspended && !restoringPlayback)) return
        seekToMsInternal(positionMs, holdFrame = true)
    }

    /**
     * 恢复流程与用户跳转共用同一套提交逻辑。
     *
     * `isPlaybackSuspended` 在恢复期间仍为 true，但恢复中允许提交跳转；恢复时后台前画面已不在
     * 屏幕上，不需要再压快照，因此 `holdFrame` 只对用户跳转生效。
     */
    private fun seekToMsInternal(positionMs: Long, holdFrame: Boolean) {
        if (!isInForeground) return
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            onError(BoloPlayerError.SeekError("当前没有可跳转的媒体"))
            return
        }

        refreshFrameAfterSeek = !playWhenReady && (_state.value.isEnded || refreshFrameAfterSeek || restoringPlayback)
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
        // 恢复路径可能停在 Paused：必须先读到 native seekable，否则 pending seek 永远不提交。
        refreshSeekability(mediaPlayer, mediaGeneration, canConfirmSeekability)
        if (holdFrame) {
            // 提交 native 跳转前先压住当前画面：vout 重建期间渲染层为空。
            holdLastFrameForSeek()
        }
        submitPendingSeekIfReady(mediaPlayer, mediaGeneration)

        if (!seekabilityKnown && !playWhenReady && state != VLCMediaPlayerState.VLCMediaPlayerStatePlaying) {
            mediaLifecycle = MediaLifecycle.Loading
            mediaPlayer.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    actual fun setVolumeGain(gain: Int) {
        volumeGain = gain.coerceIn(0, 200)
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayer.audio?.volume = if (_state.value.isPlaybackSuspended) 0 else vlcVolume
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        playbackSpeed = speed
        if (speedApplyGate.canApplyNow()) {
            applyPlaybackSpeed(mediaPlayer, mediaGeneration)
        } else {
            // 媒体尚未就绪：先发布选择值，等 Playing/Paused 分支补提交 native rate。
            _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
        }
    }

    actual fun release() {
        lifecycleRevision += 1L
        backgroundReleaseJob?.cancel()
        recoveryJob?.cancel()
        restoringPlayback = false
        resumeEligible = false
        backgroundStartedMs = null
        releaseResources()
        boundDrawable = null
        mediaPlayer.drawable = null
        _state.value = _state.value.copy(isPlaybackSuspended = false)
    }

    private fun releaseResources() {
        if (
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }

        if (_state.value.isPlaybackSuspended) lastSavedPositionMs = backgroundPositionMs else savePositionFromCurrent()
        removeSeekFrameHolder()
        mediaLifecycle = MediaLifecycle.Released
        mediaGeneration += 1
        removePlayerObservers()
        speedApplyGate.onInactive()
        beginNewMediaGeneration()
        mediaPlayer.stop()
        mediaPlayer.media = null
        AVAudioSession.sharedInstance().setActive(false, null)
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
        deferScope.cancel()
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
        // VLCKit 4 在 libvlc 输入线程上同步回调观察者，并用 NSOperationQueue.mainQueue 投递：
        // 输入线程会一直阻塞在 waitUntilFinished，且该线程此刻持有播放器锁。
        // 因此在回调栈内（包括嵌套的 mainQueue 块）做任何 player.* 同步调用都会死锁；
        // 必须用定时器把工作移出回调栈，等输入线程退出回调并释放锁之后再访问播放器。
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerStateChangedNotification, player, null
        ) { _ ->
            deferOffCallback {
                if (isCurrentPlayerGeneration(player, generation)) {
                    onStateChanged(player, generation)
                }
            }
        }
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerTimeChangedNotification, player, null
        ) { _ ->
            deferOffCallback {
                if (isCurrentPlayerGeneration(player, generation)) {
                    updateTimeAndDuration(player, generation)
                }
            }
        }
        val delegate = BufferingDelegate()
        playerDelegates += delegate
        player.delegate = delegate
    }

    /**
     * 把回调工作移出当前回调栈：在后台调度器上延迟一个极短间隔，再切回主线程执行。
     * 直接 async 到主队列仍会被输入线程的 waitUntilFinished 包住，因此必须离开回调栈并带延迟，
     * 等输入线程退出回调、释放播放器锁之后再访问 player.*。
     */
    private fun deferOffCallback(block: () -> Unit) {
        deferScope.launch {
            delay(PlayerWorkDeferralMs)
            withContext(Dispatchers.Main) { block() }
        }
    }

    private inner class BufferingDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerBufferingChanged(progress: Float) {
            // delegate 回调来自 libvlc 事件队列，状态发布统一回到主队列。
            NSOperationQueue.mainQueue.addOperationWithBlock {
                handleBufferingChanged(mediaPlayer, progress)
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
        playerDelegates.clear()
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
        if (positionMs <= 0L && !restoringPlayback) return null
        if (restoringPlayback) refreshFrameAfterSeek = true
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
        if (!isInForeground || !isCurrentPlayerGeneration(player, generation) || positionMs < 0L || _state.value.isEnded) return false

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

    /**
     * 提交 native rate 并发布选中倍速。
     *
     * native setter 完成不代表 rate 立即生效（VLC 需要一个输入周期收敛），因此回读值是过渡值，
     * 不作为发布依据；期间换了媒体代次则放弃本次提交。
     */
    private fun applyPlaybackSpeed(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        if (generation != mediaGeneration) return

        val requested = playbackSpeed
        try {
            player.rate = requested.rateNumber
        } catch (_: Exception) {}
        // native setter 完成不代表 rate 立即生效（VLC 需要一次输入线程周期），回读值是过渡值；
        // 因此发布用户选择值，native 会收敛到同一目标。
        _state.value = _state.value.copy(playbackSpeed = requested)
    }

    /**
     * 媒体就绪后补提交倍速。
     *
     * 无条件重下发当前意图：native rate 在重建媒体后可能回到 1x，且"请求早于媒体就绪"时
     * 唯一的状态同步时机就是这里。
     */
    private fun applyPendingPlaybackSpeed(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return
        if (!speedApplyGate.onMediaReady()) return
        applyPlaybackSpeed(player, generation)
    }

}

/** VLCKit 回调离开回调栈后的最短延迟。 */
private const val PlayerWorkDeferralMs = 20L

/** 跳转空档保留上一帧的最长时长与观测间隔。 */
private const val SeekFrameHoldTimeoutMs = 2_000L
private const val SeekFrameWatchIntervalMs = 30L

/** 恢复流程的最长等待；位置确认在毫秒级完成，这里只兜住异常情况。 */
private const val RestoreTimeoutMs = 8_000L

/** 恢复期间的完成条件轮询间隔，与 seek 确认 readback 保持同一量级。 */
private const val RestorePollIntervalMs = 50L

/** 恢复期间允许的位置越界：暂停态 seek 不前进，超出该范围视为错误回读。 */
private const val MaxRestoreDriftMs = 3_000L

/** 恢复成立后等待新画面呈现的最长观测时长。 */
private const val RestoreFrameObserveMs = 2_000L

/** 画面计数的合理上界；越界视为读取失败。 */
private val MaxPlausibleDisplayedPictures = 100_000_000uL
