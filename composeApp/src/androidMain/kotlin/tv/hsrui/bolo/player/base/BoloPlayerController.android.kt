package tv.hsrui.bolo.player.base

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

actual class BoloPlayerController actual constructor(
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private companion object {
        private const val DebugTag = "BoloPlayer"
        private const val DebugEvent = "[Bolo_Player_Debug_Event]"
        private const val DebugSpeed = "[Bolo_Player_Debug_Speed]"
        private const val DebugAudio = "[Bolo_Player_Debug_Audio]"
        private const val DebugTime = "[Bolo_Player_Debug_Time]"
        private const val DebugMpd = "[Bolo_Player_Debug_MPD]"
        private const val SpeedProbeWindowMs = 8_000L
        private const val EnableVlcNativeVerbose = false
        private const val EnableSpeedChangeProbe = false
    }

    private data class VlcStatsSnapshot(
        val readBytes: Int,
        val inputBitrate: Float,
        val demuxReadBytes: Int,
        val demuxBitrate: Float,
        val demuxCorrupted: Int,
        val demuxDiscontinuity: Int,
        val decodedVideo: Int,
        val decodedAudio: Int,
        val displayedPictures: Int,
        val lostPictures: Int,
        val playedAbuffers: Int,
        val lostAbuffers: Int,
        val sentPackets: Int,
        val sentBytes: Int,
        val sendBitrate: Float
    ) {
        fun toLogString(): String =
            "stats(readBytes=$readBytes inputBitrate=$inputBitrate demuxReadBytes=$demuxReadBytes demuxBitrate=$demuxBitrate " +
                "demuxCorrupted=$demuxCorrupted demuxDiscontinuity=$demuxDiscontinuity decodedVideo=$decodedVideo " +
                "decodedAudio=$decodedAudio displayedPictures=$displayedPictures lostPictures=$lostPictures " +
                "playedAbuffers=$playedAbuffers lostAbuffers=$lostAbuffers sentPackets=$sentPackets sentBytes=$sentBytes " +
                "sendBitrate=$sendBitrate)"

        fun deltaLogString(previous: VlcStatsSnapshot?): String {
            if (previous == null) return "statsDelta=first"
            return "statsDelta(readBytes=${readBytes - previous.readBytes} demuxReadBytes=${demuxReadBytes - previous.demuxReadBytes} " +
                "demuxCorrupted=${demuxCorrupted - previous.demuxCorrupted} demuxDiscontinuity=${demuxDiscontinuity - previous.demuxDiscontinuity} " +
                "decodedVideo=${decodedVideo - previous.decodedVideo} decodedAudio=${decodedAudio - previous.decodedAudio} " +
                "displayedPictures=${displayedPictures - previous.displayedPictures} lostPictures=${lostPictures - previous.lostPictures} " +
                "playedAbuffers=${playedAbuffers - previous.playedAbuffers} lostAbuffers=${lostAbuffers - previous.lostAbuffers} " +
                "sentPackets=${sentPackets - previous.sentPackets} sentBytes=${sentBytes - previous.sentBytes})"
        }
    }

    private data class TrackSnapshot(
        val trackCount: Int = 0,
        val videoCodec: String = "",
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val videoBitrate: Long = 0L,
        val audioCodec: String = "",
        val audioBitrate: Long = 0L
    )

    private class SpeedApplyGate {
        data class ApplyTicket(
            val mediaGeneration: Long,
            val requestRevision: Long
        )

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
        private var phaseBeforeBuffering = Phase.Inactive

        @Synchronized
        fun onMediaChanged() {
            mediaGeneration += 1
            phase = Phase.Loading
            phaseBeforeBuffering = Phase.Loading
        }

        @Synchronized
        fun onOpening() {
            phase = Phase.Loading
            phaseBeforeBuffering = Phase.Loading
        }

        @Synchronized
        fun onBuffering() {
            if (phase != Phase.Buffering) {
                phaseBeforeBuffering = phase
            }
            phase = Phase.Buffering
        }

        @Synchronized
        fun onBufferingCompleted() {
            if (phase == Phase.Buffering) {
                phase = phaseBeforeBuffering
            }
        }

        @Synchronized
        fun onPlaying(): ApplyTicket? {
            phase = Phase.Playing
            return consumeIfNeeded()
        }

        @Synchronized
        fun onPaused() {
            phase = Phase.Paused
        }

        @Synchronized
        fun onInactive() {
            phase = Phase.Inactive
            phaseBeforeBuffering = Phase.Inactive
        }

        @Synchronized
        fun onSpeedRequested(): ApplyTicket? {
            requestRevision += 1
            return when (phase) {
                Phase.Playing, Phase.Paused -> consumeIfNeeded()
                Phase.Inactive, Phase.Loading, Phase.Buffering -> null
            }
        }

        @Synchronized
        fun isCurrent(ticket: ApplyTicket): Boolean =
            appliedGeneration == ticket.mediaGeneration &&
                appliedRevision == ticket.requestRevision &&
                mediaGeneration == ticket.mediaGeneration &&
                phase != Phase.Inactive

        private fun consumeIfNeeded(): ApplyTicket? {
            if (appliedGeneration == mediaGeneration && appliedRevision == requestRevision) {
                return null
            }
            appliedGeneration = mediaGeneration
            appliedRevision = requestRevision
            return ApplyTicket(mediaGeneration, requestRevision)
        }
    }

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal var videoLayout: VLCVideoLayout? = null
    @Volatile
    private var libVLC: LibVLC? = null
    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    private var disposed = false
    private var playerGeneration = 0L

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var resumeAfterBackgroundEnabled = false
    private var inBackground = false
    private var backgroundStartedMs: Long? = null
    private var backgroundPositionMs = 0L
    private var backgroundEnded = false
    private var resumeEligible = false
    private var needsRestoreSeek = false
    private var outputNeedsRefresh = false
    private var surfacesReady = false
    private var restoringPlayback = false
    private var recoveryFailed = false
    private var lifecycleRevision = 0L
    private var backgroundReleaseJob: Job? = null
    private var recoveryJob: Job? = null
    private var callbacksContext: Context? = null
    private var volumeGain = 100
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() { releaseBackgroundResources() }
        override fun onTrimMemory(level: Int) {
            if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releaseBackgroundResources()
        }
    }
    private var playWhenReady = autoPlay
    private val speedLock = Any()
    private val speedApplyGate = SpeedApplyGate()
    private val seekLock = Any()
    private val nativeSeekSubmitLock = Any()
    private val seekCoordinator = BoloPlayerSeekCoordinator()
    private val seekScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var seekTimeoutJob: Job? = null
    private var seekReadbackJob: Job? = null
    private var mediaReadyForSeek = false
    private var seekabilityKnown = false
    private var nativeIsPlaying = false
    private var eventListenerInstaller: ((Long) -> Unit)? = null
    private var debugNextNativeSubmissionFailure = false
    private var debugNextTimeout = false
    private var debugNextNotSeekable = false
    private var debugNativeSubmissionFailureRevision: Long? = null
    private var debugTimeoutRevision: Long? = null

    // 恢复后需要暂停（之前是暂停状态离开）
    private var frameRefreshRevision: Long? = null
    private var frameRefreshDisplayedPictures: Int? = null
    private var pendingPauseAfterStart = false

    // 保留 pending target 或最后一次已确认 native 时间，供底层播放器重建后恢复。
    private var lastSavedPositionMs = 0L

    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFile: File? = null
    private var hasPendingLoadRequest = false
    private var pendingLoadStartPositionMs = 0L
    private var playbackSpeed = 1f
    private var speedProbeUntilWallTimeMs = 0L
    private var speedProbeStartWallTimeMs = 0L
    private var speedChangeSeq = 0L
    private var activeSpeedChangeSeq = 0L
    private var lastSpeedProbeStats: VlcStatsSnapshot? = null

    fun bindLifecycle(owner: LifecycleOwner) {
        if (disposed || lifecycleOwner === owner) {
            return
        }
        removeLifecycleObserver()
        lifecycleOwner = owner

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                inBackground = false
                resumePlaybackIfReady()
            }
            override fun onStop(owner: LifecycleOwner) {
                suspendPlayback()
            }
        }
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    actual fun setResumeAfterBackgroundEnabled(enabled: Boolean) {
        resumeAfterBackgroundEnabled = enabled
        if (!enabled) resumeEligible = false
    }

    private fun suspendPlayback() {
        if (disposed || inBackground) return
        val wasRestoring = restoringPlayback
        inBackground = true
        lifecycleRevision += 1L
        recoveryJob?.cancel()
        restoringPlayback = false
        backgroundReleaseJob?.cancel()
        backgroundStartedMs = SystemClock.elapsedRealtime()
        if (!wasRestoring) {
            backgroundPositionMs = _state.value.displayPositionMs
            backgroundEnded = _state.value.isEnded
            resumeEligible = playWhenReady && !backgroundEnded
        }
        needsRestoreSeek = needsRestoreSeek || _state.value.isSeeking || !mediaReadyForSeek || wasRestoring
        playWhenReady = false
        synchronized(seekLock) {
            seekTimeoutJob?.cancel()
            seekReadbackJob?.cancel()
            seekCoordinator.cancelCurrentSeek()
            clearDebugSeekInjectionLocked(clearNext = true)
            pendingPauseAfterStart = false
            _state.value = _state.value.copy(
                isPlaybackSuspended = true, isPlaying = false, isBuffering = false,
                currentPositionMs = backgroundPositionMs, pendingSeekPositionMs = null,
            )
        }
        mediaPlayer?.let { player -> runCatching { player.pause() } }
        val revision = lifecycleRevision
        backgroundReleaseJob = seekScope.launch(Dispatchers.Main) {
            delay(60_000L)
            if (revision == lifecycleRevision && inBackground) releaseResources()
        }
    }

    private fun releaseBackgroundResources() {
        seekScope.launch(Dispatchers.Main) {
            if (inBackground && !disposed) releaseResources()
        }
    }

    private fun resumePlaybackIfReady() {
        if (disposed || inBackground || !_state.value.isPlaybackSuspended || restoringPlayback) return
        val layout = videoLayout ?: return
        if (!layout.isAttachedToWindow) return
        backgroundReleaseJob?.cancel()
        val startedMs = backgroundStartedMs
        if (startedMs != null && SystemClock.elapsedRealtime() - startedMs >= 60_000L) releaseResources()
        backgroundStartedMs = null
        restoringPlayback = true
        val revision = ++lifecycleRevision
        recoveryJob = seekScope.launch(Dispatchers.Main) {
            try {
                for (attempt in 0..1) {
                    if (revision != lifecycleRevision || inBackground || disposed) return@launch
                    recoveryFailed = false
                    val recreate = mediaPlayer == null || attempt > 0
                    if (attempt > 0) releaseResources()
                    if (lastMpd == null) {
                        _state.value = _state.value.copy(isPlaybackSuspended = false)
                        return@launch
                    }
                    if (recreate) {
                        lastSavedPositionMs = backgroundPositionMs
                        bindVideo(layout)
                    }
                    val outputReady = withTimeoutOrNull(10_000L) {
                        while (!surfacesReady && !recoveryFailed) delay(50L)
                        !recoveryFailed
                    } == true
                    if (!outputReady) continue
                    val player = mediaPlayer ?: continue
                    player.setVolume(0)
                    if (!recreate && (needsRestoreSeek || outputNeedsRefresh)) {
                        seekToMs(backgroundPositionMs)
                    }
                    val restored = withTimeoutOrNull(22_000L) {
                        while (!recoveryFailed) {
                            val playback = _state.value
                            val positionMatches = kotlin.math.abs(playback.currentPositionMs - backgroundPositionMs) <=
                                BoloPlayerSeekCoordinator.ConfirmationToleranceMs
                            if (mediaReadyForSeek && !playback.isSeeking && !playback.isBuffering && positionMatches) break
                            delay(50L)
                        }
                        !recoveryFailed
                    } == true
                    if (!restored) continue
                    if (revision != lifecycleRevision || inBackground || disposed) return@launch
                    player.pause()
                    player.setVolume(volumeGain)
                    val shouldResume = resumeAfterBackgroundEnabled && resumeEligible && !backgroundEnded
                    resumeEligible = false
                    needsRestoreSeek = false
                    outputNeedsRefresh = false
                    _state.value = _state.value.copy(
                        isPlaybackSuspended = false, isPlaying = false, isBuffering = false,
                        currentPositionMs = if (backgroundEnded) _state.value.durationMs else _state.value.currentPositionMs,
                    )
                    restoringPlayback = false
                    if (shouldResume) play()
                    return@launch
                }
                if (revision == lifecycleRevision && !inBackground && !disposed) {
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

    fun unbindLifecycle(owner: LifecycleOwner): Boolean {
        if (lifecycleOwner !== owner) {
            return false
        }
        removeLifecycleObserver()
        return true
    }

    private fun removeLifecycleObserver() {
        val boundOwner = lifecycleOwner
        val observer = lifecycleObserver
        lifecycleObserver = null
        lifecycleOwner = null
        if (boundOwner != null && observer != null) {
            try {
                boundOwner.lifecycle.removeObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    fun bindVideo(layout: VLCVideoLayout) {
        if (disposed) {
            return
        }
        val existingPlayer = mediaPlayer
        if (videoLayout === layout && existingPlayer != null) {
            resumePlaybackIfReady()
            return
        }
        videoLayout = layout
        if (existingPlayer != null) {
            outputNeedsRefresh = true
            attachVideoLayout(existingPlayer, layout, detachFirst = true)
            resumePlaybackIfReady()
            return
        }

        if (inBackground) return
        if (callbacksContext == null) {
            callbacksContext = layout.context.applicationContext.also { it.registerComponentCallbacks(memoryCallbacks) }
        }
        LibVLC.loadLibraries()
        val libVlcOptions = arrayListOf<String>().apply {
            if (EnableVlcNativeVerbose) add("-vv")
        }
        val newLibVLC = LibVLC(layout.context, libVlcOptions)
        val newPlayer = try {
            MediaPlayer(newLibVLC)
        } catch (error: Throwable) {
            runCatching { newLibVLC.release() }
            throw error
        }
        val generation = ++playerGeneration
        libVLC = newLibVLC
        mediaPlayer = newPlayer
        newPlayer.setVolume(if (_state.value.isPlaybackSuspended) 0 else volumeGain)
        newPlayer.vlcVout.addCallback(object : IVLCVout.Callback {
            override fun onSurfacesCreated(vlcVout: IVLCVout) {
                if (!isCurrentPlayer(newPlayer, generation)) return
                surfacesReady = true
                seekScope.launch(Dispatchers.Main) { resumePlaybackIfReady() }
            }
            override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
                if (!isCurrentPlayer(newPlayer, generation)) return
                surfacesReady = false
                outputNeedsRefresh = true
            }
        })
        debugLog(
            DebugEvent,
            "PlayerCreated generation=$generation nativeVerbose=$EnableVlcNativeVerbose " +
                "speedProbe=$EnableSpeedChangeProbe audioTimeStretch=default"
        )
        val installEventListener: (Long) -> Unit = { eventMediaGeneration ->
            newPlayer.setEventListener eventListener@ { event ->
            if (
                !isCurrentPlayer(newPlayer, generation) ||
                synchronized(seekLock) {
                    seekCoordinator.currentMediaGeneration != eventMediaGeneration
                }
            ) {
                return@eventListener
            }
            if (inBackground) {
                if (event.type == MediaPlayer.Event.Playing) runCatching { newPlayer.pause() }
                if (event.type == MediaPlayer.Event.EncounteredError) recoveryFailed = true
                return@eventListener
            }
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    synchronized(speedLock) { speedApplyGate.onOpening() }
                    synchronized(seekLock) {
                        mediaReadyForSeek = false
                        nativeIsPlaying = false
                    }
                    _state.value = _state.value.copy(isBuffering = true)
                }
                MediaPlayer.Event.Buffering -> {
                    val shouldPublishBuffering = synchronized(seekLock) {
                        event.buffering < 100f && (
                            (!mediaReadyForSeek && _state.value.currentPositionMs < _state.value.durationMs) ||
                                _state.value.isPlaying ||
                                seekCoordinator.pendingPositionMs != null ||
                                pendingPauseAfterStart
                            )
                    }
                    if (shouldPublishBuffering) {
                        synchronized(speedLock) { speedApplyGate.onBuffering() }
                    } else if (event.buffering >= 100f) {
                        synchronized(speedLock) { speedApplyGate.onBufferingCompleted() }
                    }
                    _state.value = _state.value.copy(isBuffering = shouldPublishBuffering)
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugEvent,
                            "Buffering afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "cache=${event.buffering} ${playerSnapshot(newPlayer)}"
                        )
                    }
                }
                MediaPlayer.Event.Playing -> {
                    val publishPlaying = synchronized(seekLock) {
                        nativeIsPlaying = true
                        mediaReadyForSeek = true
                        (!pendingPauseAfterStart || playWhenReady) && !_state.value.isPlaybackSuspended
                    }
                    val tracks = readTrackSnapshot(newPlayer)
                    debugLog(
                        DebugEvent,
                        "Playing tracks=${tracks.trackCount} video=${tracks.videoCodec.ifEmpty { "unknown" }} " +
                            "${tracks.videoWidth}x${tracks.videoHeight} audio=${tracks.audioCodec.ifEmpty { "unknown" }} " +
                            "audioBr=${tracks.audioBitrate} generation=$generation"
                    )

                    _state.value = _state.value.copy(
                        isPlaying = publishPlaying,
                        isBuffering = false,
                        videoCodec = tracks.videoCodec,
                        videoWidth = tracks.videoWidth,
                        videoHeight = tracks.videoHeight,
                        videoBitrate = tracks.videoBitrate,
                        audioCodec = tracks.audioCodec,
                        audioBitrate = tracks.audioBitrate
                    )
                    val speedTicket = synchronized(speedLock) { speedApplyGate.onPlaying() }
                    speedTicket?.let { ticket ->
                        applyPlaybackSpeed(
                            player = newPlayer,
                            ticket = ticket,
                            speedProbeSeq = activeSpeedChangeSeq.takeIf { isSpeedProbeActive() }
                        )
                    }
                    val seekable = runCatching { newPlayer.isSeekable }.getOrDefault(false)
                    handleSeekabilityChanged(newPlayer, generation, seekable)
                    val shouldPauseNow = synchronized(seekLock) {
                        (pendingPauseAfterStart || !playWhenReady) &&
                            seekCoordinator.pendingPositionMs == null
                    }
                    if (shouldPauseNow) {
                        synchronized(seekLock) { pendingPauseAfterStart = false }
                        newPlayer.pause()
                    }
                }
                MediaPlayer.Event.Paused -> {
                    synchronized(speedLock) { speedApplyGate.onPaused() }
                    synchronized(seekLock) { nativeIsPlaying = false }
                    if (playWhenReady) {
                        handleEndReached(newPlayer, generation, keepMediaReady = true)
                    } else {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    }
                }
                MediaPlayer.Event.Stopped -> {
                    synchronized(speedLock) { speedApplyGate.onInactive() }
                    synchronized(seekLock) {
                        nativeIsPlaying = false
                        mediaReadyForSeek = false
                        seekabilityKnown = false
                    }
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
                MediaPlayer.Event.EndReached -> {
                    handleEndReached(newPlayer, generation)
                }
                MediaPlayer.Event.EncounteredError -> {
                    if (restoringPlayback) recoveryFailed = true
                    synchronized(speedLock) { speedApplyGate.onInactive() }
                    debugLog(DebugEvent, "EncounteredError ${playerSnapshot(newPlayer)}")
                    synchronized(seekLock) {
                        nativeIsPlaying = false
                        mediaReadyForSeek = false
                        seekabilityKnown = false
                        seekTimeoutJob?.cancel()
                        seekTimeoutJob = null
                        seekReadbackJob?.cancel()
                        seekReadbackJob = null
                        seekCoordinator.cancelCurrentSeek()
                        clearDebugSeekInjectionLocked(clearNext = false)
                        lastSavedPositionMs = _state.value.currentPositionMs
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            pendingSeekPositionMs = null,
                            isSeekable = false
                        )
                    }
                    onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    val tc = event.timeChanged
                    val speed = readTransferSpeedBps(newPlayer)
                    handleObservedTime(newPlayer, generation, tc, speed)
                    if (isSpeedProbeActive()) {
                        val statsSnapshot = vlcStatsSnapshot(newPlayer)
                        debugLog(
                            DebugTime,
                            "TimeChanged afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} eventTimeMs=$tc " +
                                "reportedPositionMs=${_state.value.currentPositionMs} transferSpeed=$speed ${statsLog(statsSnapshot)} " +
                                "${speedProbeStatsDeltaLog(statsSnapshot)} ${playerSnapshot(newPlayer)}"
                        )
                    }
                }
                MediaPlayer.Event.PositionChanged -> {
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugTime,
                            "PositionChanged afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "position=${event.positionChanged} ${playerSnapshot(newPlayer)}"
                        )
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    val len = event.lengthChanged
                    if (len > 0) {
                        _state.value = _state.value.copy(durationMs = len)
                    }
                }
                MediaPlayer.Event.SeekableChanged -> {
                    handleSeekabilityChanged(newPlayer, generation, event.seekable)
                }
                MediaPlayer.Event.Vout -> {
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugEvent,
                            "Vout afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "count=${event.voutCount} ${playerSnapshot(newPlayer)}"
                        )
                    }
                }
                MediaPlayer.Event.ESAdded ->
                    logEsChangeDuringSpeedProbe("ESAdded", event, newPlayer)
                MediaPlayer.Event.ESDeleted ->
                    logEsChangeDuringSpeedProbe("ESDeleted", event, newPlayer)
                MediaPlayer.Event.ESSelected ->
                    logEsChangeDuringSpeedProbe("ESSelected", event, newPlayer)
            }
            }
        }
        eventListenerInstaller = installEventListener
        installEventListener(synchronized(seekLock) { seekCoordinator.currentMediaGeneration })
        // 在 Compose 中包裹原生视频组件时，必须使用 TextureView 而不是 SurfaceView。
        // SurfaceView 由于其独立的 Window 层级，经常会导致在 Compose 测量和渲染时出现尺寸不同步、四边黑边等异常情况。
        attachVideoLayout(newPlayer, layout, detachFirst = false)

        val mpd = lastMpd
        if (mpd != null) {
            val startPositionMs = pendingLoadStartPositionMs
            val isPendingLoad = hasPendingLoadRequest
            pendingLoadStartPositionMs = 0L
            hasPendingLoadRequest = false
            loadInternal(
                mpd = mpd,
                startPositionMs = startPositionMs,
                restorePosition = !isPendingLoad
            )
        }
    }

    fun unbindVideo(layout: VLCVideoLayout) {
        if (videoLayout !== layout) {
            return
        }
        videoLayout = null
        surfacesReady = false
        outputNeedsRefresh = true
        mediaPlayer?.let { player ->
            try { player.detachViews() } catch (_: Exception) {}
        }
    }

    private fun attachVideoLayout(player: MediaPlayer, layout: VLCVideoLayout, detachFirst: Boolean) {
        if (detachFirst) {
            try { player.detachViews() } catch (_: Exception) {}
        }
        player.attachViews(layout, null, true, true)
        try {
            player.scale = 0f
        } catch (_: Exception) {}
    }

    internal actual fun load(mpd: BoloDashMpd, startPositionMs: Long) {
        if (disposed) {
            return
        }
        lifecycleRevision += 1L
        recoveryJob?.cancel()
        restoringPlayback = false
        resumeEligible = false
        backgroundEnded = false
        if (_state.value.isPlaybackSuspended) {
            backgroundPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
            needsRestoreSeek = true
            releaseResources()
        }
        lastMpd = mpd
        pendingLoadStartPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
        hasPendingLoadRequest = true
        debugLog(
            DebugMpd,
            "LoadRequested mode=mpd startPositionMs=$startPositionMs hasAudio=${mpd.hasAudio} " +
                "durationMs=${mpd.durationMs} video=${mpd.videoSummary} audio=${mpd.audioSummary ?: "none"}"
        )
        if (!inBackground && libVLC != null && mediaPlayer != null) {
            val pendingStartPositionMs = pendingLoadStartPositionMs
            pendingLoadStartPositionMs = 0L
            hasPendingLoadRequest = false
            loadInternal(
                mpd = mpd,
                startPositionMs = pendingStartPositionMs,
                restorePosition = false
            )
        }
    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        debugLog(DebugMpd, "MpdBuildFailed reason=commonBuilder error=${error.message}")
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
        synchronized(seekLock) {
            debugNextNativeSubmissionFailure = nativeSubmissionFailure
            debugNextTimeout = timeout
            debugNextNotSeekable = notSeekable
        }
    }

    private fun loadInternal(
        mpd: BoloDashMpd,
        startPositionMs: Long = 0L,
        restorePosition: Boolean = true
    ) {
        val vlc = libVLC
        val player = mediaPlayer
        if (disposed || vlc == null || player == null) {
            pendingLoadStartPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
            hasPendingLoadRequest = true
            return
        }
        val normalizedStartPositionMs = normalizePositionMs(startPositionMs, mpd.durationMs)
        val savedPositionMs = when {
            normalizedStartPositionMs > 0L -> normalizedStartPositionMs
            restorePosition -> lastSavedPositionMs
            else -> 0L
        }.let { normalizePositionMs(it, mpd.durationMs) }

        val mediaUri = try {
            buildDashMpdUri(mpd)
        } catch (e: IllegalArgumentException) {
            debugLog(
                DebugMpd,
                "MpdBuildFailed reason=invalidInput error=${e.message} " +
                    "video=${mpd.videoSummary} audio=${mpd.audioSummary ?: "none"}"
            )
            onError(BoloPlayerError.FormatNotSupported(e.message ?: "DASH MPD 构建参数无效"))
            return
        } catch (e: Exception) {
            debugLog(
                DebugMpd,
                "MpdBuildFailed reason=unexpected error=${e.message} " +
                    "video=${mpd.videoSummary} audio=${mpd.audioSummary ?: "none"}"
            )
            onError(BoloPlayerError.UnknownError("DASH MPD 构建失败: ${e.message}", e))
            return
        }

        val media = Media(vlc, mediaUri)
        try {
            videoPlayHeaders.forEach { (key, value) ->
                when (key.lowercase()) {
                    "referer" -> media.addOption(":http-referrer=$value")
                    "user-agent" -> media.addOption(":http-user-agent=$value")
                }
            }

            // EOF 时暂停输入，保留最后一帧及直接 seek 的能力。
            media.addOption(":play-and-pause")
            val requestedSpeed = synchronized(speedLock) { playbackSpeed }
            debugLog(DebugMpd, "MediaPrepared mode=localMpd uri=$mediaUri requestedSpeed=$requestedSpeed")

            if (mediaPlayer !== player) {
                return
            }
            synchronized(speedLock) { speedApplyGate.onMediaChanged() }
            val eventMediaGeneration = synchronized(seekLock) {
                seekTimeoutJob?.cancel()
                seekTimeoutJob = null
                seekReadbackJob?.cancel()
                seekReadbackJob = null
                val newMediaGeneration = seekCoordinator.onMediaChanged()
                clearDebugSeekInjectionLocked(clearNext = true)
                mediaReadyForSeek = false
                seekabilityKnown = false
                nativeIsPlaying = false
                pendingPauseAfterStart = false
                lastSavedPositionMs = savedPositionMs
                val pendingPositionMs = if (savedPositionMs > 0L || restoringPlayback) {
                    val revision = seekCoordinator.requestSeek(savedPositionMs)
                    if (restoringPlayback) {
                        frameRefreshRevision = revision
                        frameRefreshDisplayedPictures = 0
                    }
                    savedPositionMs
                } else {
                    null
                }
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = true,
                    currentPositionMs = 0L,
                    durationMs = mpd.durationMs,
                    pendingSeekPositionMs = pendingPositionMs,
                    isSeekable = false
                )
                newMediaGeneration
            }
            eventListenerInstaller?.invoke(eventMediaGeneration)
            player.media = media
        } finally {
            media.release()
        }

        val shouldPlay = playWhenReady
        val shouldPauseAfterSeek = !shouldPlay && (savedPositionMs > 0L || restoringPlayback)

        if (shouldPlay || shouldPauseAfterSeek) {
            synchronized(seekLock) { pendingPauseAfterStart = shouldPauseAfterSeek }
            player.play()
            _state.value = _state.value.copy(isPlaying = shouldPlay, isBuffering = true)
            // seek 延迟到 Playing 事件执行 —— VLC 此时才完成媒体初始化
        }
    }

    actual fun play() {
        if (_state.value.isPlaybackSuspended || inBackground) return
        if (disposed) {
            return
        }
        playWhenReady = true
        synchronized(seekLock) { pendingPauseAfterStart = false }
        mediaPlayer?.let { player ->
            runCatching { player.play() }
        }
        _state.value = _state.value.copy(isPlaying = mediaPlayer != null)
    }

    actual fun pause() {
        resumeEligible = false
        if (disposed) {
            return
        }
        playWhenReady = false
        synchronized(seekLock) {
            pendingPauseAfterStart = seekCoordinator.pendingPositionMs != null
        }
        mediaPlayer?.let { player ->
            runCatching { player.pause() }
        }
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekToMs(positionMs: Long) {
        if (disposed) return
        if (inBackground || (_state.value.isPlaybackSuspended && !restoringPlayback)) return

        val player = mediaPlayer
        val refreshFrame = !playWhenReady && (_state.value.isEnded || frameRefreshRevision != null || restoringPlayback)
        val targetPositionMs = normalizePositionMs(positionMs, _state.value.durationMs)
        var revision = 0L
        var mediaGeneration = 0L
        var submitNow = false
        var startForInitialization = false
        var injectNotSeekable = false
        var seekError: BoloPlayerError.SeekError? = null
        synchronized(seekLock) {
            seekTimeoutJob?.cancel()
            seekTimeoutJob = null
            seekReadbackJob?.cancel()
            seekReadbackJob = null
            revision = seekCoordinator.requestSeek(targetPositionMs)
            frameRefreshRevision = revision.takeIf { refreshFrame }
            frameRefreshDisplayedPictures = null
            if (refreshFrame) pendingPauseAfterStart = true
            mediaGeneration = seekCoordinator.currentMediaGeneration
            debugNativeSubmissionFailureRevision =
                revision.takeIf { debugNextNativeSubmissionFailure }
            debugTimeoutRevision = revision.takeIf { debugNextTimeout }
            injectNotSeekable = debugNextNotSeekable
            debugNextNativeSubmissionFailure = false
            debugNextTimeout = false
            debugNextNotSeekable = false
            lastSavedPositionMs = targetPositionMs
            _state.value = _state.value.copy(pendingSeekPositionMs = targetPositionMs)
            if (player == null && lastMpd != null) {
                pendingLoadStartPositionMs = targetPositionMs
                hasPendingLoadRequest = true
            }

            when {
                injectNotSeekable -> {
                    seekCoordinator.cancelSeek(revision)
                    clearDebugSeekRevisionLocked(revision)
                    lastSavedPositionMs = _state.value.currentPositionMs
                    _state.value = _state.value.copy(
                        pendingSeekPositionMs = null,
                        isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                    )
                    seekError = BoloPlayerError.SeekError("调试注入：当前媒体不支持跳转")
                }
                seekabilityKnown && !_state.value.isSeekable -> {
                    seekCoordinator.cancelSeek(revision)
                    clearDebugSeekRevisionLocked(revision)
                    lastSavedPositionMs = _state.value.currentPositionMs
                    _state.value = _state.value.copy(
                        pendingSeekPositionMs = null,
                        isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                    )
                    seekError = BoloPlayerError.SeekError("当前媒体不支持跳转")
                }
                player != null && mediaReadyForSeek && _state.value.isSeekable -> submitNow = true
                player != null && !nativeIsPlaying -> {
                    pendingPauseAfterStart = !playWhenReady
                    startForInitialization = true
                }
            }
        }

        seekError?.let {
            onError(it)
            return
        }
        if (submitNow && player != null) {
            submitSeek(player, playerGeneration, mediaGeneration, revision)
        } else if (startForInitialization && player != null) {
            runCatching { player.play() }
                .onFailure { failSeek(mediaGeneration, revision, "跳转初始化播放失败", it) }
        }
    }

    actual fun setVolumeGain(gain: Int) {
        volumeGain = gain.coerceIn(0, 200)
        if (disposed) {
            return
        }
        mediaPlayer?.let { player ->
            runCatching { player.setVolume(if (_state.value.isPlaybackSuspended) 0 else volumeGain) }
        }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        if (disposed) {
            return
        }
        val previousSpeed = synchronized(speedLock) { playbackSpeed }
        val seq = ++speedChangeSeq
        activeSpeedChangeSeq = seq
        val player = mediaPlayer
        val statsBefore = if (EnableSpeedChangeProbe && player != null) {
            speedProbeStartWallTimeMs = System.currentTimeMillis()
            speedProbeUntilWallTimeMs = speedProbeStartWallTimeMs + SpeedProbeWindowMs
            vlcStatsSnapshot(player).also { lastSpeedProbeStats = it }
        } else {
            null
        }
        if (EnableSpeedChangeProbe) {
            debugLog(
                DebugSpeed,
                "SpeedChange begin seq=$seq previous=$previousSpeed previousRate=$previousSpeed " +
                    "selected=$speed selectedRate=$speed ${statsLog(statsBefore)} " +
                    "before=${playerSnapshot(player)}"
            )
        }
        val applyTicket = synchronized(speedLock) {
            playbackSpeed = speed
            _state.value = _state.value.copy(playbackSpeed = speed)
            speedApplyGate.onSpeedRequested()
        }
        if (applyTicket != null && player != null) {
            applyPlaybackSpeed(
                player = player,
                ticket = applyTicket,
                speedProbeSeq = seq.takeIf { EnableSpeedChangeProbe }
            )
        }
        if (EnableSpeedChangeProbe) {
            val statsAfter = player?.let(::vlcStatsSnapshot)
            debugLog(
                DebugSpeed,
                "SpeedChange end seq=$seq selected=$speed " +
                    "stored=${synchronized(speedLock) { playbackSpeed }} " +
                    "${statsLog(statsAfter)} ${statsAfter?.deltaLogString(statsBefore) ?: "statsDelta=null"} " +
                    "after=${playerSnapshot(player)}"
            )
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
        _state.value = _state.value.copy(isPlaybackSuspended = false)
    }

    private fun releaseResources() {
        if (disposed) {
            return
        }
        surfacesReady = false
        val player = mediaPlayer
        val vlc = libVLC
        if (player == null && vlc == null) {
            return
        }
        if (player != null) {
            if (_state.value.isPlaybackSuspended) lastSavedPositionMs = backgroundPositionMs else savePosition(player)
        }
        synchronized(seekLock) {
            seekTimeoutJob?.cancel()
            seekTimeoutJob = null
            seekReadbackJob?.cancel()
            seekReadbackJob = null
            nativeIsPlaying = false
            mediaReadyForSeek = false
            seekabilityKnown = false
            mediaPlayer = null
            libVLC = null
            eventListenerInstaller = null
            clearDebugSeekInjectionLocked(clearNext = true)
            playerGeneration += 1
        }
        synchronized(speedLock) { speedApplyGate.onInactive() }
        synchronized(seekLock) { pendingPauseAfterStart = false }
        _state.value = _state.value.copy(
            isPlaying = false,
            isBuffering = false,
            isSeekable = false
        )
        if (player != null) {
            try { player.setEventListener(null) } catch (_: Exception) {}
            try { player.detachViews() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
        }
        if (vlc != null) {
            try { vlc.release() } catch (_: Exception) {}
        }
        runCatching { lastMpdFile?.delete() }
        lastMpdFile = null
    }

    actual fun dispose() {
        if (disposed) return

        release()
        disposed = true
        removeLifecycleObserver()
        callbacksContext?.unregisterComponentCallbacks(memoryCallbacks)
        callbacksContext = null
        videoLayout = null
        lastMpd = null
        runCatching { lastMpdFile?.delete() }
        lastMpdFile = null
        hasPendingLoadRequest = false
        pendingLoadStartPositionMs = 0L
        synchronized(seekLock) {
            seekTimeoutJob?.cancel()
            seekTimeoutJob = null
            seekReadbackJob?.cancel()
            seekReadbackJob = null
            seekCoordinator.cancelCurrentSeek()
            seekCoordinator.onMediaChanged()
            clearDebugSeekInjectionLocked(clearNext = true)
            mediaReadyForSeek = false
            seekabilityKnown = false
            nativeIsPlaying = false
            pendingPauseAfterStart = false
            lastSavedPositionMs = 0L
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                pendingSeekPositionMs = null,
                isSeekable = false
            )
        }
        seekScope.cancel()
    }

    private fun handleSeekabilityChanged(
        player: MediaPlayer,
        playerGeneration: Long,
        seekable: Boolean
    ) {
        var mediaGeneration = 0L
        var revision = 0L
        var shouldSubmit = false
        var shouldReportUnavailable = false
        synchronized(seekLock) {
            if (!isCurrentPlayer(player, playerGeneration)) return
            // 同一个 MediaPlayer 会复用；Playing 前无法区分旧媒体队列中残留的 seekable 事件。
            // 首次 Playing 会立即通过 isSeekable() 复核并发布当前媒体的权威值。
            if (!mediaReadyForSeek) return
            seekabilityKnown = true
            _state.value = _state.value.copy(isSeekable = seekable)
            if (seekCoordinator.pendingPositionMs != null) {
                mediaGeneration = seekCoordinator.currentMediaGeneration
                revision = seekCoordinator.currentRevision
                if (!seekable) {
                    seekTimeoutJob?.cancel()
                    seekTimeoutJob = null
                    seekReadbackJob?.cancel()
                    seekReadbackJob = null
                    if (seekCoordinator.cancelSeek(revision)) {
                        clearDebugSeekRevisionLocked(revision)
                        lastSavedPositionMs = _state.value.currentPositionMs
                        _state.value = _state.value.copy(
                            pendingSeekPositionMs = null,
                            isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                        )
                        shouldReportUnavailable = true
                    }
                } else if (mediaReadyForSeek && !seekCoordinator.isSubmitted) {
                    shouldSubmit = true
                }
            }
        }

        if (shouldSubmit) {
            submitSeek(player, playerGeneration, mediaGeneration, revision)
        }
        if (shouldReportUnavailable) {
            pauseAfterStartupSeekIfNeeded(player, playerGeneration)
            onError(BoloPlayerError.SeekError("当前媒体不支持跳转"))
        }
    }

    private fun submitSeek(
        player: MediaPlayer,
        playerGeneration: Long,
        mediaGeneration: Long,
        revision: Long
    ) {
        var submittedAttempt = 0
        var submittedTargetMs = 0L
        var shouldScheduleTimeout = false
        var injectNativeSubmissionFailure = false
        var injectTimeout = false
        var failure: BoloPlayerError.SeekError? = null
        synchronized(nativeSeekSubmitLock) {
            var shouldCallNative = false
            synchronized(seekLock) {
                if (
                    !isCurrentPlayer(player, playerGeneration) ||
                    !seekCoordinator.isCurrent(mediaGeneration, revision)
                ) {
                    return
                }
                if (!mediaReadyForSeek || !seekabilityKnown) return
                if (!_state.value.isSeekable) {
                    seekCoordinator.cancelSeek(revision)
                    clearDebugSeekRevisionLocked(revision)
                    lastSavedPositionMs = _state.value.currentPositionMs
                    _state.value = _state.value.copy(
                        pendingSeekPositionMs = null,
                        isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                    )
                    failure = BoloPlayerError.SeekError("当前媒体不支持跳转")
                } else {
                    submittedTargetMs = seekCoordinator.pendingPositionMs ?: return
                    if (seekCoordinator.markSubmitted(revision)) {
                        submittedAttempt = seekCoordinator.submittedAttempt
                        injectNativeSubmissionFailure =
                            debugNativeSubmissionFailureRevision == revision
                        injectTimeout = debugTimeoutRevision == revision
                        seekTimeoutJob?.cancel()
                        seekTimeoutJob = null
                        seekReadbackJob?.cancel()
                        seekReadbackJob = null
                        shouldCallNative = true
                    }
                }
            }
            if (shouldCallNative) {
                val nativeResult: Result<Long> = when {
                    injectNativeSubmissionFailure -> Result.failure(
                        IllegalStateException("调试注入：VLC 提交跳转失败")
                    )
                    injectTimeout -> Result.success(0L)
                    else -> runCatching {
                        val refreshFrame = synchronized(seekLock) { frameRefreshRevision == revision && !playWhenReady }
                        if (refreshFrame) {
                            frameRefreshDisplayedPictures = vlcStatsSnapshot(player)?.displayedPictures
                        }
                        val result = player.setTime(submittedTargetMs, false)
                        if (result >= 0L && refreshFrame) player.play()
                        result
                    }
                }
                synchronized(seekLock) {
                    if (
                        isCurrentPlayer(player, playerGeneration) &&
                        seekCoordinator.isCurrent(mediaGeneration, revision)
                    ) {
                        nativeResult.fold(
                            onSuccess = { result ->
                                if (result < 0L) {
                                    seekCoordinator.cancelSeek(revision)
                                    clearDebugSeekRevisionLocked(revision)
                                    lastSavedPositionMs = _state.value.currentPositionMs
                                    _state.value = _state.value.copy(
                                        pendingSeekPositionMs = null,
                                        isBuffering = if (!playWhenReady) {
                                            false
                                        } else {
                                            _state.value.isBuffering
                                        }
                                    )
                                    failure = BoloPlayerError.SeekError("VLC 拒绝了跳转请求")
                                } else if (seekCoordinator.submittedAttempt == submittedAttempt) {
                                    if (!injectTimeout) {
                                        debugNativeSubmissionFailureRevision = null
                                    }
                                    shouldScheduleTimeout = true
                                }
                            },
                            onFailure = { error ->
                                seekCoordinator.cancelSeek(revision)
                                clearDebugSeekRevisionLocked(revision)
                                lastSavedPositionMs = _state.value.currentPositionMs
                                _state.value = _state.value.copy(
                                    pendingSeekPositionMs = null,
                                    isBuffering = if (!playWhenReady) {
                                        false
                                    } else {
                                        _state.value.isBuffering
                                    }
                                )
                                failure = BoloPlayerError.SeekError("VLC 提交跳转失败", error)
                            }
                        )
                    }
                }
            }
        }

        failure?.let { error ->
            pauseAfterStartupSeekIfNeeded(player, playerGeneration)
            onError(error)
            return
        }
        if (shouldScheduleTimeout) {
            debugLog(
                DebugTime,
                "SeekSubmitted mediaGeneration=$mediaGeneration seekRevision=$revision " +
                    "requestedMs=$submittedTargetMs submittedAttempt=$submittedAttempt precise=true"
            )
            scheduleSeekTimeout(
                player = player,
                playerGeneration = playerGeneration,
                mediaGeneration = mediaGeneration,
                revision = revision,
                submittedAttempt = submittedAttempt
            )
            scheduleSeekReadback(
                player = player,
                playerGeneration = playerGeneration,
                mediaGeneration = mediaGeneration,
                revision = revision,
                submittedAttempt = submittedAttempt
            )
        }
    }

    /**
     * 暂停态的 LibVLC 可能不发送 TimeChanged；短周期 readback 仍作为 native 时间观测，
     * 且必须通过同一确认窗口。10 秒任务继续负责重试与最终失败，不由本任务替代。
     */
    private fun scheduleSeekReadback(
        player: MediaPlayer,
        playerGeneration: Long,
        mediaGeneration: Long,
        revision: Long,
        submittedAttempt: Int
    ) {
        val readbackJob = seekScope.launch {
            try {
                repeat(BoloPlayerSeekCoordinator.ConfirmationReadbackAttempts) {
                    delay(BoloPlayerSeekCoordinator.ConfirmationReadbackIntervalMs)
                    val isStillCurrent = synchronized(seekLock) {
                        isCurrentPlayer(player, playerGeneration) &&
                            seekCoordinator.isCurrent(mediaGeneration, revision) &&
                            seekCoordinator.submittedAttempt == submittedAttempt
                    }
                    if (!isStillCurrent) return@launch

                    val nativePositionMs = runCatching { player.getTime() }
                        .getOrNull()
                        ?.takeIf { it >= 0L }
                        ?: return@repeat
                    val confirmed = handleObservedTime(
                        player = player,
                        playerGeneration = playerGeneration,
                        positionMs = nativePositionMs,
                        transferSpeed = _state.value.transferSpeed,
                        expectedMediaGeneration = mediaGeneration,
                        expectedRevision = revision,
                        expectedSubmittedAttempt = submittedAttempt
                    )
                    if (confirmed) return@launch
                }
            } finally {
                synchronized(seekLock) {
                    if (
                        seekCoordinator.isCurrent(mediaGeneration, revision) &&
                        seekCoordinator.submittedAttempt == submittedAttempt
                    ) {
                        seekReadbackJob = null
                    }
                }
            }
        }
        synchronized(seekLock) {
            if (
                isCurrentPlayer(player, playerGeneration) &&
                seekCoordinator.isCurrent(mediaGeneration, revision) &&
                seekCoordinator.submittedAttempt == submittedAttempt
            ) {
                seekReadbackJob?.cancel()
                seekReadbackJob = readbackJob
            } else {
                readbackJob.cancel()
            }
        }
    }

    private fun scheduleSeekTimeout(
        player: MediaPlayer,
        playerGeneration: Long,
        mediaGeneration: Long,
        revision: Long,
        submittedAttempt: Int
    ) {
        val timeoutJob = seekScope.launch {
            delay(BoloPlayerSeekCoordinator.AttemptTimeoutMs)
            handleSeekTimeout(
                player = player,
                playerGeneration = playerGeneration,
                mediaGeneration = mediaGeneration,
                revision = revision,
                submittedAttempt = submittedAttempt
            )
        }
        synchronized(seekLock) {
            if (
                isCurrentPlayer(player, playerGeneration) &&
                seekCoordinator.isCurrent(mediaGeneration, revision) &&
                seekCoordinator.submittedAttempt == submittedAttempt
            ) {
                seekTimeoutJob?.cancel()
                seekTimeoutJob = timeoutJob
            } else {
                timeoutJob.cancel()
            }
        }
    }

    private fun handleSeekTimeout(
        player: MediaPlayer,
        playerGeneration: Long,
        mediaGeneration: Long,
        revision: Long,
        submittedAttempt: Int
    ) {
        val isStillCurrent = synchronized(seekLock) {
            isCurrentPlayer(player, playerGeneration) &&
                seekCoordinator.isCurrent(mediaGeneration, revision) &&
                seekCoordinator.submittedAttempt == submittedAttempt
        }
        if (!isStillCurrent) return

        val forceTimeout = synchronized(seekLock) {
            seekCoordinator.isCurrent(mediaGeneration, revision) &&
                debugTimeoutRevision == revision
        }
        val nativePositionMs = if (forceTimeout) {
            null
        } else {
            runCatching { player.getTime() }
                .getOrNull()
                ?.takeIf { it >= 0L }
        }
        val playbackRate = synchronized(speedLock) { playbackSpeed }
        var confirmed = false
        var retry = false
        var failed = false
        synchronized(seekLock) {
            if (
                !isCurrentPlayer(player, playerGeneration) ||
                !seekCoordinator.isCurrent(mediaGeneration, revision) ||
                seekCoordinator.submittedAttempt != submittedAttempt
            ) {
                return
            }
            seekTimeoutJob = null
            if (
                nativePositionMs != null &&
                seekCoordinator.acceptObservedPosition(
                    nativePositionMs,
                    nativeIsPlaying,
                    playbackRate
                )
            ) {
                lastSavedPositionMs = nativePositionMs
                seekReadbackJob?.cancel()
                seekReadbackJob = null
                _state.value = _state.value.copy(
                    currentPositionMs = nativePositionMs,
                    pendingSeekPositionMs = null,
                    isBuffering = if (!nativeIsPlaying || !playWhenReady) {
                        false
                    } else {
                        _state.value.isBuffering
                    }
                )
                clearDebugSeekRevisionLocked(revision)
                confirmed = true
            } else if (seekCoordinator.canRetry(revision)) {
                retry = true
            } else if (seekCoordinator.cancelSeek(revision)) {
                clearDebugSeekRevisionLocked(revision)
                if (nativePositionMs != null) {
                    lastSavedPositionMs = nativePositionMs
                }
                _state.value = _state.value.copy(
                    currentPositionMs = nativePositionMs ?: _state.value.currentPositionMs,
                    pendingSeekPositionMs = null,
                    isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                )
                lastSavedPositionMs = _state.value.currentPositionMs
                failed = true
            }
        }

        when {
            confirmed -> pauseAfterStartupSeekIfNeeded(player, playerGeneration)
            retry -> submitSeek(player, playerGeneration, mediaGeneration, revision)
            failed -> {
                pauseAfterStartupSeekIfNeeded(player, playerGeneration)
                onError(BoloPlayerError.SeekError("跳转两次尝试均超时"))
            }
        }
    }

    private fun handleObservedTime(
        player: MediaPlayer,
        playerGeneration: Long,
        positionMs: Long,
        transferSpeed: Long,
        expectedMediaGeneration: Long? = null,
        expectedRevision: Long? = null,
        expectedSubmittedAttempt: Int? = null
    ): Boolean {
        val playbackRate = synchronized(speedLock) { playbackSpeed }
        var shouldPause = false
        var completedAtEnd = false
        var acceptedObservation = false
        val displayedPictures = if (synchronized(seekLock) { frameRefreshRevision != null }) {
            vlcStatsSnapshot(player)?.displayedPictures
        } else null
        synchronized(seekLock) {
            if (!isCurrentPlayer(player, playerGeneration) || inBackground || _state.value.isEnded) return false
            if (
                expectedMediaGeneration != null &&
                expectedRevision != null &&
                expectedSubmittedAttempt != null &&
                (!seekCoordinator.isCurrent(expectedMediaGeneration, expectedRevision) ||
                    seekCoordinator.submittedAttempt != expectedSubmittedAttempt)
            ) {
                return false
            }
            val pendingTargetMs = seekCoordinator.pendingPositionMs
            val pendingRevision = pendingTargetMs?.let { seekCoordinator.currentRevision }
            val forceTimeout = pendingRevision != null && debugTimeoutRevision == pendingRevision
            if (pendingRevision != null && frameRefreshRevision == pendingRevision && !playWhenReady) {
                val previousPictures = frameRefreshDisplayedPictures
                if (!nativeIsPlaying || (previousPictures != null &&
                        (displayedPictures == null || displayedPictures <= previousPictures))) return false
            }
            val refreshingFrame = pendingRevision != null && frameRefreshRevision == pendingRevision && !playWhenReady
            val accepted = mediaReadyForSeek && !forceTimeout && seekCoordinator.acceptObservedPosition(
                positionMs = positionMs,
                isPlaying = nativeIsPlaying,
                playbackRate = playbackRate
            )
            if (accepted) {
                acceptedObservation = true
                seekTimeoutJob?.cancel()
                seekTimeoutJob = null
                seekReadbackJob?.cancel()
                seekReadbackJob = null
                pendingRevision?.let(::clearDebugSeekRevisionLocked)
                val durationMs = _state.value.durationMs
                completedAtEnd = pendingTargetMs != null && durationMs > 0L &&
                    pendingTargetMs >= (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                        .coerceAtLeast(0L) &&
                    positionMs >= (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                        .coerceAtLeast(0L)
                val confirmedPositionMs = if (completedAtEnd) durationMs else positionMs
                lastSavedPositionMs = confirmedPositionMs
                if (completedAtEnd) {
                    pendingPauseAfterStart = false
                }
                _state.value = _state.value.copy(
                    isPlaying = if (completedAtEnd) false else _state.value.isPlaying,
                    isBuffering = if (refreshingFrame && !completedAtEnd) {
                        true
                    } else if (completedAtEnd || !nativeIsPlaying || !playWhenReady) {
                        false
                    } else {
                        _state.value.isBuffering
                    },
                    currentPositionMs = confirmedPositionMs,
                    pendingSeekPositionMs = null,
                    transferSpeed = transferSpeed
                )
                if (!completedAtEnd && pendingPauseAfterStart && !playWhenReady) {
                    pendingPauseAfterStart = false
                    shouldPause = true
                }
            } else {
                _state.value = _state.value.copy(transferSpeed = transferSpeed)
            }
        }
        if (shouldPause) {
            runCatching { player.pause() }
            if (isCurrentPlayer(player, playerGeneration)) {
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            }
        }
        if (completedAtEnd) {
            synchronized(speedLock) { speedApplyGate.onInactive() }
        }
        return acceptedObservation
    }

    private fun handleEndReached(player: MediaPlayer, playerGeneration: Long, keepMediaReady: Boolean = false) {
        val durationMs = getDurationForCompletion(player)
        val playbackRate = synchronized(speedLock) { playbackSpeed }
        var ended = false
        var seekFailed = false
        synchronized(seekLock) {
            if (!isCurrentPlayer(player, playerGeneration)) return
            val targetPositionMs = seekCoordinator.pendingPositionMs
            val revision = targetPositionMs?.let { seekCoordinator.currentRevision }
            val confirmedSeek = targetPositionMs != null && durationMs > 0L &&
                targetPositionMs >= (durationMs - BoloPlayerSeekCoordinator.ConfirmationToleranceMs)
                    .coerceAtLeast(0L) &&
                seekCoordinator.acceptObservedPosition(
                    durationMs,
                    isPlaying = false,
                    playbackRate = playbackRate
                )
            if (targetPositionMs == null || confirmedSeek) {
                revision?.let(::clearDebugSeekRevisionLocked)
                ended = true
            } else {
                seekCoordinator.cancelSeek(revision ?: seekCoordinator.currentRevision)
                revision?.let(::clearDebugSeekRevisionLocked)
                seekFailed = true
                ended = true
            }
            if (ended) {
                seekTimeoutJob?.cancel()
                seekTimeoutJob = null
                seekReadbackJob?.cancel()
                seekReadbackJob = null
                nativeIsPlaying = false
                mediaReadyForSeek = keepMediaReady
                pendingPauseAfterStart = false
                playWhenReady = false
                lastSavedPositionMs = durationMs
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    currentPositionMs = durationMs,
                    durationMs = durationMs.takeIf { it > 0L } ?: _state.value.durationMs,
                    pendingSeekPositionMs = null
                )
            }
        }
        if (ended) {
            synchronized(speedLock) { speedApplyGate.onInactive() }
        }
        if (seekFailed) {
            onError(BoloPlayerError.SeekError("媒体在跳转目标确认前结束播放"))
        }
    }

    private fun failSeek(
        mediaGeneration: Long,
        revision: Long,
        message: String,
        cause: Throwable? = null
    ) {
        var shouldReport = false
        synchronized(seekLock) {
            if (seekCoordinator.isCurrent(mediaGeneration, revision)) {
                seekTimeoutJob?.cancel()
                seekTimeoutJob = null
                seekReadbackJob?.cancel()
                seekReadbackJob = null
                seekCoordinator.cancelSeek(revision)
                clearDebugSeekRevisionLocked(revision)
                lastSavedPositionMs = _state.value.currentPositionMs
                _state.value = _state.value.copy(
                    pendingSeekPositionMs = null,
                    isBuffering = if (!playWhenReady) false else _state.value.isBuffering
                )
                shouldReport = true
            }
        }
        if (shouldReport) {
            mediaPlayer?.let { pauseAfterStartupSeekIfNeeded(it, playerGeneration) }
            onError(BoloPlayerError.SeekError(message, cause))
        }
    }

    private fun pauseAfterStartupSeekIfNeeded(player: MediaPlayer, playerGeneration: Long) {
        val shouldPause = synchronized(seekLock) {
            if (!isCurrentPlayer(player, playerGeneration)) {
                false
            } else if (
                pendingPauseAfterStart &&
                !playWhenReady &&
                seekCoordinator.pendingPositionMs == null
            ) {
                pendingPauseAfterStart = false
                true
            } else {
                false
            }
        }
        if (shouldPause) {
            runCatching { player.pause() }
        }
    }

    private fun getDurationForCompletion(player: MediaPlayer? = mediaPlayer): Long {
        val nativeDurationMs = runCatching { player?.getLength() }
            .getOrNull()
            ?.takeIf { it > 0L }
        return nativeDurationMs ?: _state.value.durationMs.coerceAtLeast(0L)
    }

    private fun normalizePositionMs(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceAtLeast(0L).let { normalized ->
            if (durationMs > 0L) normalized.coerceAtMost(durationMs) else normalized
        }

    private fun clearDebugSeekRevisionLocked(revision: Long) {
        if (frameRefreshRevision == revision) {
            frameRefreshRevision = null
            frameRefreshDisplayedPictures = null
        }
        if (debugNativeSubmissionFailureRevision == revision) {
            debugNativeSubmissionFailureRevision = null
        }
        if (debugTimeoutRevision == revision) {
            debugTimeoutRevision = null
        }
    }

    private fun clearDebugSeekInjectionLocked(clearNext: Boolean) {
        frameRefreshRevision = null
        frameRefreshDisplayedPictures = null
        debugNativeSubmissionFailureRevision = null
        debugTimeoutRevision = null
        if (clearNext) {
            debugNextNativeSubmissionFailure = false
            debugNextTimeout = false
            debugNextNotSeekable = false
        }
    }

    private fun buildDashMpdUri(mpd: BoloDashMpd): Uri {
        val context = videoLayout?.context ?: throw IllegalStateException("视频组件尚未绑定，无法创建 MPD")
        val mpdDir = File(context.cacheDir, "bolo_dash_mpd").apply { mkdirs() }
        val mpdFile = File(mpdDir, "bolo_${System.currentTimeMillis()}.mpd")
        runCatching { lastMpdFile?.delete() }
        mpdFile.writeText(mpd.xml, Charsets.UTF_8)
        lastMpdFile = mpdFile
        debugLog(
            DebugMpd,
            "MpdFileWritten path=${mpdFile.absolutePath} bytes=${mpdFile.length()} " +
                "hasAudio=${mpd.hasAudio} durationMs=${mpd.durationMs}"
        )
        return Uri.fromFile(mpdFile)
    }

    private fun isCurrentPlayer(player: MediaPlayer, generation: Long): Boolean =
        !disposed && mediaPlayer === player && playerGeneration == generation

    private fun savePosition(player: MediaPlayer) {
        if (mediaPlayer !== player) return
        lastSavedPositionMs = _state.value.displayPositionMs
    }

    private fun readTrackSnapshot(player: MediaPlayer): TrackSnapshot {
        val media = runCatching { player.media }.getOrNull() ?: return TrackSnapshot()
        return try {
            val trackCount = runCatching { media.trackCount }.getOrDefault(0)
            var videoCodec = ""
            var audioCodec = ""
            var videoWidth = 0
            var videoHeight = 0
            var videoBitrate = 0L
            var audioBitrate = 0L
            for (index in 0 until trackCount) {
                val track = runCatching { media.getTrack(index) }.getOrNull() ?: continue
                when (track.type) {
                    IMedia.Track.Type.Video -> {
                        val videoTrack = track as IMedia.VideoTrack
                        videoWidth = videoTrack.width
                        videoHeight = videoTrack.height
                        videoBitrate = videoTrack.bitrate.toLong().takeIf { it > 0L } ?: 0L
                        videoCodec = videoTrack.codec?.uppercase() ?: ""
                    }
                    IMedia.Track.Type.Audio -> {
                        val audioTrack = track as IMedia.AudioTrack
                        audioBitrate = audioTrack.bitrate.toLong().takeIf { it > 0L } ?: 0L
                        audioCodec = audioTrack.codec?.uppercase() ?: ""
                    }
                }
            }
            TrackSnapshot(
                trackCount = trackCount,
                videoCodec = videoCodec,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoBitrate = videoBitrate,
                audioCodec = audioCodec,
                audioBitrate = audioBitrate
            )
        } finally {
            runCatching { media.release() }
        }
    }

    private fun readTransferSpeedBps(player: MediaPlayer): Long {
        val media = runCatching { player.media }.getOrNull() ?: return 0L
        return try {
            val inputBitrate = runCatching { media.stats.inputBitrate }.getOrNull() ?: return 0L
            inputBitrate.toLong().takeIf { it > 0L }?.times(8L) ?: 0L
        } finally {
            runCatching { media.release() }
        }
    }

    private fun applyPlaybackSpeed(
        player: MediaPlayer,
        ticket: SpeedApplyGate.ApplyTicket,
        speedProbeSeq: Long? = null
    ) {
        val applyGeneration = playerGeneration
        val applyRequestSeq = ticket.requestRevision
        synchronized(speedLock) {
            if (disposed || mediaPlayer !== player || !speedApplyGate.isCurrent(ticket)) {
                return
            }
            val requestedSpeed = playbackSpeed
            try {
                val statsBefore = speedProbeSeq?.let { vlcStatsSnapshot(player) }
                if (speedProbeSeq != null) {
                    debugLog(
                        DebugSpeed,
                        "applyPlaybackSpeed before seq=$speedProbeSeq requested=$requestedSpeed " +
                            "requestedRate=$requestedSpeed ${statsLog(statsBefore)} ${playerSnapshot(player)}"
                    )
                }
                player.setRate(requestedSpeed)
                val nativeRate = player.getRate()
                if (
                    disposed ||
                    mediaPlayer !== player ||
                    !speedApplyGate.isCurrent(ticket)
                ) {
                    return
                }
                val storedSpeed = nativeRate.takeIf { it.isFinite() && it > 0f } ?: playbackSpeed
                playbackSpeed = storedSpeed
                _state.value = _state.value.copy(playbackSpeed = storedSpeed)
                debugLog(
                    DebugSpeed,
                    "RateApplied generation=$applyGeneration requestSeq=$applyRequestSeq " +
                        "requested=$requestedSpeed nativeRate=$nativeRate stored=$storedSpeed"
                )
                val statsAfter = speedProbeSeq?.let { vlcStatsSnapshot(player) }
                if (speedProbeSeq != null) {
                    debugLog(
                        DebugSpeed,
                        "applyPlaybackSpeed after seq=$speedProbeSeq nativeRate=$nativeRate stored=$storedSpeed " +
                            "${statsLog(statsAfter)} ${statsAfter?.deltaLogString(statsBefore) ?: "statsDelta=null"} " +
                            playerSnapshot(player)
                    )
                }
            } catch (e: Exception) {
                debugLog(
                    DebugSpeed,
                    "RateApplyFailed generation=$applyGeneration requestSeq=$applyRequestSeq error=${e.message}"
                )
                if (speedApplyGate.isCurrent(ticket)) {
                    _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
                }
            }
        }
    }

    private fun isSpeedProbeActive(): Boolean =
        EnableSpeedChangeProbe && System.currentTimeMillis() <= speedProbeUntilWallTimeMs

    private fun speedProbeElapsedMs(): Long =
        System.currentTimeMillis() - speedProbeStartWallTimeMs

    private fun logEsChangeDuringSpeedProbe(name: String, event: MediaPlayer.Event, player: MediaPlayer) {
        if (!isSpeedProbeActive()) return
        debugLog(
            DebugAudio,
            "$name afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                "type=${event.esChangedType} id=${event.esChangedID} ${playerSnapshot(player)}"
        )
    }

    private fun vlcStatsSnapshot(player: MediaPlayer): VlcStatsSnapshot? {
        val media = runCatching { player.media }.getOrNull() ?: return null
        return try {
            val stats = runCatching { media.stats }.getOrNull() ?: return null
            VlcStatsSnapshot(
                readBytes = stats.readBytes,
                inputBitrate = stats.inputBitrate,
                demuxReadBytes = stats.demuxReadBytes,
                demuxBitrate = stats.demuxBitrate,
                demuxCorrupted = stats.demuxCorrupted,
                demuxDiscontinuity = stats.demuxDiscontinuity,
                decodedVideo = stats.decodedVideo,
                decodedAudio = stats.decodedAudio,
                displayedPictures = stats.displayedPictures,
                lostPictures = stats.lostPictures,
                playedAbuffers = stats.playedAbuffers,
                lostAbuffers = stats.lostAbuffers,
                sentPackets = stats.sentPackets,
                sentBytes = stats.sentBytes,
                sendBitrate = stats.sendBitrate
            )
        } finally {
            runCatching { media.release() }
        }
    }

    private fun statsLog(stats: VlcStatsSnapshot?): String =
        stats?.toLogString() ?: "stats=null"

    private fun speedProbeStatsDeltaLog(stats: VlcStatsSnapshot?): String {
        if (stats == null) {
            lastSpeedProbeStats = null
            return "statsDelta=null"
        }
        val previous = lastSpeedProbeStats
        lastSpeedProbeStats = stats
        return stats.deltaLogString(previous)
    }

    private fun debugLog(prefix: String, message: String) {
        Log.d(DebugTag, "$prefix $message")
    }

    private fun playerSnapshot(player: MediaPlayer? = mediaPlayer): String {
        if (player == null) {
            return "mediaPlayer=notAvailable"
        }
        val nativeTime = runCatching { player.getTime() }.getOrNull()
        val nativeLength = runCatching { player.getLength() }.getOrNull()
        val nativePosition = runCatching { player.position }.getOrNull()
        val nativeRate = runCatching { player.getRate() }.getOrNull()
        val nativeState = runCatching { player.playerState }.getOrNull()
        val volume = runCatching { player.getVolume() }.getOrNull()
        val audioTrack = runCatching { player.getAudioTrack() }.getOrNull()
        val audioTrackCount = runCatching { player.getAudioTracksCount() }.getOrNull()
        val audioDelayUs = runCatching { player.getAudioDelay() }.getOrNull()
        val videoTrack = runCatching { player.getVideoTrack() }.getOrNull()
        val videoTrackCount = runCatching { player.getVideoTracksCount() }.getOrNull()
        val state = _state.value
        return "nativeState=$nativeState nativeTimeMs=$nativeTime nativeLengthMs=$nativeLength nativePosition=$nativePosition " +
            "nativeRate=$nativeRate volume=$volume audioTrack=$audioTrack/$audioTrackCount audioDelayUs=$audioDelayUs " +
            "videoTrack=$videoTrack/$videoTrackCount statePlaying=${state.isPlaying} stateBuffering=${state.isBuffering} " +
            "statePositionMs=${state.currentPositionMs} stateDurationMs=${state.durationMs} " +
            "pendingSeekPositionMs=${state.pendingSeekPositionMs} stateSeekable=${state.isSeekable} " +
            "stateSpeed=${state.playbackSpeed}"
    }

}
