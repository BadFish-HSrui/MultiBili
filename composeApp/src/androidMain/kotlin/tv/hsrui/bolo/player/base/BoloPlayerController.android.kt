package tv.hsrui.bolo.player.base

import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
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
    private var playWhenReady = autoPlay
    private val speedLock = Any()
    private val speedApplyGate = SpeedApplyGate()

    // 恢复后需要暂停（之前是暂停状态离开）
    private var pendingPauseAfterStart = false

    private var pendingSeekMs = 0L

    // 恢复位置不被 TimeChanged 覆盖，只在主动 seek 或释放底层播放器时更新。
    private var lastSavedPositionMs = 0L

    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFile: File? = null
    private var hasPendingLoadRequest = false
    private var pendingLoadStartPositionSec = 0
    private var playbackSpeed = BoloPlayerSpeed.default
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
                if (mediaPlayer == null) {
                    val layout = videoLayout
                    if (layout != null && layout.isAttachedToWindow) {
                        bindVideo(layout)
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                val player = mediaPlayer
                if (player != null) {
                    release()
                }
            }
        }
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
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
            return
        }
        videoLayout = layout
        if (existingPlayer != null) {
            attachVideoLayout(existingPlayer, layout, detachFirst = true)
            return
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
        debugLog(
            DebugEvent,
            "PlayerCreated generation=$generation nativeVerbose=$EnableVlcNativeVerbose " +
                "speedProbe=$EnableSpeedChangeProbe audioTimeStretch=default"
        )
        newPlayer.setEventListener { event ->
            if (!isCurrentPlayer(newPlayer, generation)) {
                return@setEventListener
            }
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    synchronized(speedLock) { speedApplyGate.onOpening() }
                    _state.value = _state.value.copy(isBuffering = true)
                }
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering < 100f) {
                        synchronized(speedLock) { speedApplyGate.onBuffering() }
                    } else {
                        synchronized(speedLock) { speedApplyGate.onBufferingCompleted() }
                    }
                    _state.value = _state.value.copy(isBuffering = event.buffering < 100f)
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugEvent,
                            "Buffering afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "cache=${event.buffering} ${playerSnapshot(newPlayer)}"
                        )
                    }
                }
                MediaPlayer.Event.Playing -> {
                    val tracks = readTrackSnapshot(newPlayer)
                    debugLog(
                        DebugEvent,
                        "Playing tracks=${tracks.trackCount} video=${tracks.videoCodec.ifEmpty { "unknown" }} " +
                            "${tracks.videoWidth}x${tracks.videoHeight} audio=${tracks.audioCodec.ifEmpty { "unknown" }} " +
                            "audioBr=${tracks.audioBitrate} generation=$generation"
                    )

                    _state.value = _state.value.copy(
                        isPlaying = true,
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
                    if (pendingSeekMs > 0) {
                        val seekResult = runCatching { newPlayer.setTime(pendingSeekMs) }.getOrNull()
                        if (seekResult != null && seekResult >= 0L) {
                            pendingSeekMs = 0L
                        }
                    }
                    if (pendingPauseAfterStart || !playWhenReady) {
                        pendingPauseAfterStart = false
                        newPlayer.pause()
                    }
                }
                MediaPlayer.Event.Paused -> {
                    synchronized(speedLock) { speedApplyGate.onPaused() }
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
                MediaPlayer.Event.Stopped -> {
                    synchronized(speedLock) { speedApplyGate.onInactive() }
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
                MediaPlayer.Event.EndReached -> {
                    synchronized(speedLock) { speedApplyGate.onInactive() }
                    val duration = getDurationForCompletion(newPlayer)
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        currentPosition = duration
                    )
                    lastSavedPositionMs = duration * 1000L
                }
                MediaPlayer.Event.EncounteredError -> {
                    synchronized(speedLock) { speedApplyGate.onInactive() }
                    debugLog(DebugEvent, "EncounteredError ${playerSnapshot(newPlayer)}")
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    val tc = event.timeChanged
                    val stateBefore = _state.value
                    val speed = readTransferSpeedBps(newPlayer)
                    val reportedPositionSec = (tc / 1000).toInt()
                    _state.value = stateBefore.copy(
                        currentPosition = reportedPositionSec,
                        transferSpeed = speed
                    )
                    if (isSpeedProbeActive()) {
                        val statsSnapshot = vlcStatsSnapshot(newPlayer)
                        debugLog(
                            DebugTime,
                            "TimeChanged afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} eventTimeMs=$tc " +
                                "reportedPositionSec=$reportedPositionSec transferSpeed=$speed ${statsLog(statsSnapshot)} " +
                                "${speedProbeStatsDeltaLog(statsSnapshot)} ${playerSnapshot(newPlayer)}"
                        )
                    }
                    // 不更新 lastSavedPositionMs，避免 VLC 从 0 开始播放时覆盖正确的恢复位置。
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
                        _state.value = _state.value.copy(duration = (len / 1000).toInt())
                    }
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
        // 在 Compose 中包裹原生视频组件时，必须使用 TextureView 而不是 SurfaceView。
        // SurfaceView 由于其独立的 Window 层级，经常会导致在 Compose 测量和渲染时出现尺寸不同步、四边黑边等异常情况。
        attachVideoLayout(newPlayer, layout, detachFirst = false)

        val mpd = lastMpd
        if (mpd != null) {
            val startPosition = pendingLoadStartPositionSec
            val isPendingLoad = hasPendingLoadRequest
            pendingLoadStartPositionSec = 0
            hasPendingLoadRequest = false
            loadInternal(
                mpd = mpd,
                startPosition = startPosition,
                restorePosition = !isPendingLoad
            )
        }
    }

    fun unbindVideo(layout: VLCVideoLayout) {
        if (videoLayout !== layout) {
            return
        }
        videoLayout = null
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

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        if (disposed) {
            return
        }
        lastMpd = mpd
        pendingLoadStartPositionSec = startPosition.takeIf { it > 0 } ?: 0
        hasPendingLoadRequest = true
        debugLog(
            DebugMpd,
            "LoadRequested mode=mpd startPositionSec=$startPosition hasAudio=${mpd.hasAudio} " +
                "durationSec=${mpd.durationSec} video=${mpd.videoSummary} audio=${mpd.audioSummary ?: "none"}"
        )
        if (libVLC != null && mediaPlayer != null) {
            val pendingStartPosition = pendingLoadStartPositionSec
            pendingLoadStartPositionSec = 0
            hasPendingLoadRequest = false
            loadInternal(
                mpd = mpd,
                startPosition = pendingStartPosition,
                restorePosition = false
            )
        }
    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        debugLog(DebugMpd, "MpdBuildFailed reason=commonBuilder error=${error.message}")
        onError(error)
    }

    private fun loadInternal(
        mpd: BoloDashMpd,
        startPosition: Int = 0,
        restorePosition: Boolean = true
    ) {
        val vlc = libVLC
        val player = mediaPlayer
        if (disposed || vlc == null || player == null) {
            pendingLoadStartPositionSec = startPosition.takeIf { it > 0 } ?: 0
            hasPendingLoadRequest = true
            return
        }
        val startPositionMs = startPosition.takeIf { it > 0 }?.let { it * 1000L } ?: 0L
        val savedPosition = when {
            startPositionMs > 0 -> startPositionMs
            restorePosition -> lastSavedPositionMs
            else -> 0L
        }

        pendingPauseAfterStart = false

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

            val requestedSpeed = synchronized(speedLock) { playbackSpeed }
            debugLog(DebugMpd, "MediaPrepared mode=localMpd uri=$mediaUri requestedSpeed=${requestedSpeed.title}")

            if (mediaPlayer !== player) {
                return
            }
            player.media = media
            synchronized(speedLock) { speedApplyGate.onMediaChanged() }
            if (!restorePosition) {
                lastSavedPositionMs = savedPosition
                _state.value = _state.value.copy(currentPosition = (savedPosition / 1000L).toInt())
            }
        } finally {
            media.release()
        }

        pendingSeekMs = 0L
        if (savedPosition > 0) {
            pendingSeekMs = savedPosition
            _state.value = _state.value.copy(currentPosition = (savedPosition / 1000).toInt())
        }

        val shouldPlay = playWhenReady
        val shouldPauseAfterSeek = !shouldPlay && savedPosition > 0

        if (shouldPlay || shouldPauseAfterSeek) {
            pendingPauseAfterStart = shouldPauseAfterSeek
            player.play()
            _state.value = _state.value.copy(isPlaying = shouldPlay)
            // seek 延迟到 Playing 事件执行 —— VLC 此时才完成媒体初始化
            // 若用户在上一个周期离开太快导致 seek 未执行，
            // lastSavedPositionMs 保持不变（不被 TimeChanged 覆盖），
            // 下一个周期会重试同一个正确位置
        }
    }

    actual fun play() {
        if (disposed) {
            return
        }
        playWhenReady = true
        pendingPauseAfterStart = false
        mediaPlayer?.let { player ->
            runCatching { player.play() }
        }
        _state.value = _state.value.copy(isPlaying = mediaPlayer != null)
    }

    actual fun pause() {
        if (disposed) {
            return
        }
        playWhenReady = false
        pendingPauseAfterStart = true
        mediaPlayer?.let { player ->
            runCatching { player.pause() }
        }
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekTo(position: Int) {
        if (disposed || !isSeekPositionValid(position)) {
            return
        }
        val targetPosition = position
        val requestedTime = targetPosition * 1000L
        lastSavedPositionMs = requestedTime
        pendingSeekMs = requestedTime
        _state.value = _state.value.copy(currentPosition = targetPosition)
        val player = mediaPlayer ?: return
        val currentState = runCatching { player.playerState }.getOrNull() ?: return
        // libVLC states: 5 = Stopped, 6 = Ended
        if (currentState == 5 || currentState == 6) {
            val mpd = lastMpd
            if (mpd != null) {
                loadInternal(
                    mpd = mpd,
                    startPosition = targetPosition,
                    restorePosition = false
                )
            }
        } else {
            val seekResult = runCatching { player.setTime(requestedTime) }.getOrNull()
            if (seekResult != null && seekResult >= 0L) {
                pendingSeekMs = 0L
            }
        }
    }

    actual fun setVolumeGain(gain: Int) {
        if (disposed) {
            return
        }
        mediaPlayer?.let { player ->
            runCatching { player.setVolume(gain.coerceIn(0, 200)) }
        }
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
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
                "SpeedChange begin seq=$seq previous=${previousSpeed.title} previousRate=${previousSpeed.rateNumber} " +
                    "selected=${speed.title} selectedRate=${speed.rateNumber} ${statsLog(statsBefore)} " +
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
                "SpeedChange end seq=$seq selected=${speed.title} " +
                    "stored=${synchronized(speedLock) { playbackSpeed }.title} " +
                    "${statsLog(statsAfter)} ${statsAfter?.deltaLogString(statsBefore) ?: "statsDelta=null"} " +
                    "after=${playerSnapshot(player)}"
            )
        }
    }

    actual fun release() {
        if (disposed) {
            return
        }
        val player = mediaPlayer
        val vlc = libVLC
        if (player == null && vlc == null) {
            return
        }
        if (player != null) {
            savePosition(player)
        }
        mediaPlayer = null
        libVLC = null
        playerGeneration += 1
        synchronized(speedLock) { speedApplyGate.onInactive() }
        pendingPauseAfterStart = false
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
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
        videoLayout = null
        lastMpd = null
        runCatching { lastMpdFile?.delete() }
        lastMpdFile = null
        hasPendingLoadRequest = false
        pendingLoadStartPositionSec = 0
        pendingSeekMs = 0L
    }

    private fun getDurationForCompletion(player: MediaPlayer? = mediaPlayer): Int {
        val stateDuration = _state.value.duration
        if (stateDuration > 0) return stateDuration
        return try {
            player?.let { (it.getLength() / 1000).toInt().coerceAtLeast(0) } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun isSeekPositionValid(position: Int): Boolean {
        val duration = _state.value.duration
        return position >= 0 && (duration <= 0 || position <= duration)
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
                "hasAudio=${mpd.hasAudio} durationSec=${mpd.durationSec}"
        )
        return Uri.fromFile(mpdFile)
    }

    private fun isCurrentPlayer(player: MediaPlayer, generation: Long): Boolean =
        !disposed && mediaPlayer === player && playerGeneration == generation

    private fun savePosition(player: MediaPlayer) {
        val displayPositionMs = _state.value.currentPosition * 1000L
        val nativePositionMs = runCatching { player.getTime() }.getOrNull()
        lastSavedPositionMs = when {
            nativePositionMs == null -> displayPositionMs
            displayPositionMs == 0L && nativePositionMs > 0L -> nativePositionMs
            nativePositionMs in displayPositionMs..(displayPositionMs + 1000L) -> nativePositionMs
            nativePositionMs > 0L -> displayPositionMs
            displayPositionMs > 0L -> displayPositionMs
            else -> lastSavedPositionMs
        }
        _state.value = _state.value.copy(currentPosition = (lastSavedPositionMs / 1000L).toInt())
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
                        "applyPlaybackSpeed before seq=$speedProbeSeq requested=${requestedSpeed.title} " +
                            "requestedRate=${requestedSpeed.rateNumber} ${statsLog(statsBefore)} ${playerSnapshot(player)}"
                    )
                }
                player.setRate(requestedSpeed.rateNumber)
                val nativeRate = player.getRate()
                if (
                    disposed ||
                    mediaPlayer !== player ||
                    !speedApplyGate.isCurrent(ticket)
                ) {
                    return
                }
                val storedSpeed = BoloPlayerSpeed.fromRateNumber(nativeRate)
                playbackSpeed = storedSpeed
                _state.value = _state.value.copy(playbackSpeed = storedSpeed)
                debugLog(
                    DebugSpeed,
                    "RateApplied generation=$applyGeneration requestSeq=$applyRequestSeq " +
                        "requested=${requestedSpeed.title} nativeRate=$nativeRate stored=${storedSpeed.title}"
                )
                val statsAfter = speedProbeSeq?.let { vlcStatsSnapshot(player) }
                if (speedProbeSeq != null) {
                    debugLog(
                        DebugSpeed,
                        "applyPlaybackSpeed after seq=$speedProbeSeq nativeRate=$nativeRate stored=${storedSpeed.title} " +
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
            "statePositionSec=${state.currentPosition} stateDurationSec=${state.duration} stateSpeed=${state.playbackSpeed.title}"
    }

}
