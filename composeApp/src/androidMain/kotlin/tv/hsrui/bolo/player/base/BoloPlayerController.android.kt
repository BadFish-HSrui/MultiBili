package tv.hsrui.bolo.player.base

import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
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

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal var videoLayout: VLCVideoLayout? = null
    private lateinit var libVLC: LibVLC
    internal lateinit var mediaPlayer: MediaPlayer
    private var initialized = false

    private var wasPlayingBeforeBackground = false
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var lifecycle: Lifecycle? = null

    // 恢复后需要暂停（之前是暂停状态离开）
    private var pendingPauseAfterStart = false

    private var pendingSeekMs = 0L

    // 位置恢复（仅在 onStop 更新，不被 TimeChanged 覆盖）
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

    fun bindLifecycle(lifecycle: Lifecycle) {
        try { lifecycleObserver?.let { this.lifecycle?.removeObserver(it) } } catch (_: Exception) {}
        this.lifecycle = lifecycle

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!initialized) {
                    val layout = videoLayout
                    if (layout != null && layout.isAttachedToWindow) {
                        bindVideo(layout)
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                if (initialized && ::mediaPlayer.isInitialized) {
                    wasPlayingBeforeBackground = _state.value.isPlaying
                    val displayPos = _state.value.currentPosition * 1000L
                    try {
                        val pos = mediaPlayer.getTime()
                        // TimeChanged 间隔 ~500ms，getTime() 应在此范围内超前
                        // 若不在此范围则说明 VLC 状态异常，保持 displayPos
                        when {
                            displayPos == 0L && pos > 0 -> lastSavedPositionMs = pos
                            pos >= displayPos && pos <= displayPos + 1000 -> lastSavedPositionMs = pos
                            pos > 0 -> lastSavedPositionMs = displayPos
                        }
                    } catch (e: Exception) {
                        lastSavedPositionMs = displayPos
                    }
                    _state.value = _state.value.copy(currentPosition = (lastSavedPositionMs / 1000).toInt())
                    release()
                }
            }
        }
        lifecycleObserver = observer
        lifecycle.addObserver(observer)
    }

    fun bindVideo(layout: VLCVideoLayout) {
        if (videoLayout === layout && initialized) {
            return
        }
        videoLayout = layout
        if (initialized) {
            if (::mediaPlayer.isInitialized) {
                attachVideoLayout(layout, detachFirst = true)
            }
            return
        }
        initialized = true

        LibVLC.loadLibraries()
        val libVlcOptions = arrayListOf<String>().apply {
            if (EnableVlcNativeVerbose) add("-vv")
        }
        libVLC = LibVLC(layout.context, libVlcOptions)
        mediaPlayer = MediaPlayer(libVLC)
        debugLog(DebugEvent, "PlayerCreated nativeVerbose=$EnableVlcNativeVerbose audioTimeStretch=default")
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    _state.value = _state.value.copy(isBuffering = true)
                }
                MediaPlayer.Event.Buffering -> {
                    _state.value = _state.value.copy(isBuffering = event.buffering < 100f)
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugEvent,
                            "Buffering afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "cache=${event.buffering} ${playerSnapshot()}"
                        )
                    }
                }
                MediaPlayer.Event.Playing -> {
                    var videoCodec = ""
                    var audioCodec = ""
                    var videoWidth = 0
                    var videoHeight = 0
                    var videoBr: Long = 0L
                    var audioBr: Long = 0L

                    val trackCount = mediaPlayer.media?.trackCount ?: 0
                    for (i in 0 until trackCount) {
                        val track = mediaPlayer.media?.getTrack(i) ?: continue
                        when (track.type) {
                            IMedia.Track.Type.Video -> {
                                val vTrack = track as IMedia.VideoTrack
                                videoWidth = vTrack.width
                                videoHeight = vTrack.height
                                videoBr = vTrack.bitrate.toLong().takeIf { it > 0 } ?: 0L
                                videoCodec = vTrack.codec?.uppercase() ?: ""
                            }
                            IMedia.Track.Type.Audio -> {
                                val aTrack = track as IMedia.AudioTrack
                                audioBr = aTrack.bitrate.toLong().takeIf { it > 0 } ?: 0L
                                audioCodec = aTrack.codec?.uppercase() ?: ""
                            }
                        }
                    }
                    debugLog(
                        DebugEvent,
                        "Playing tracks=$trackCount video=${videoCodec.ifEmpty { "unknown" }} ${videoWidth}x$videoHeight " +
                            "audio=${audioCodec.ifEmpty { "unknown" }} audioBr=$audioBr ${playerSnapshot()}"
                    )
                    
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
                    applyPlaybackSpeed()
                    if (pendingSeekMs > 0) {
                        mediaPlayer.setTime(pendingSeekMs)
                        pendingSeekMs = 0L
                    }
                    if (pendingPauseAfterStart) {
                        pendingPauseAfterStart = false
                        mediaPlayer.pause()
                    }
                }
                MediaPlayer.Event.Paused -> {
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
                MediaPlayer.Event.Stopped -> {
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                }
                MediaPlayer.Event.EndReached -> {
                    val duration = getDurationForCompletion()
                    _state.value = _state.value.copy(
                        isPlaying = false, 
                        isBuffering = false,
                        currentPosition = duration
                    )
                    lastSavedPositionMs = duration * 1000L
                }
                MediaPlayer.Event.EncounteredError -> {
                    debugLog(DebugEvent, "EncounteredError ${playerSnapshot()}")
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    val tc = event.timeChanged
                    val stateBefore = _state.value
                    val statsSnapshot = vlcStatsSnapshot()
                    // 使用 inputBitrate 作为实时传输速度 (bps，所以可能需要乘 8，如果它是 bytes/sec，需要转换，但先直接返回长整型)
                    val speed = statsSnapshot?.inputBitrate?.toLong()?.let { if (it > 0L) it * 8 else 0L } ?: 0L
                    val reportedPositionSec = (tc / 1000).toInt()
                    _state.value = stateBefore.copy(
                        currentPosition = reportedPositionSec,
                        transferSpeed = speed
                    )
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugTime,
                            "TimeChanged afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} eventTimeMs=$tc " +
                                "reportedPositionSec=$reportedPositionSec transferSpeed=$speed ${statsLog(statsSnapshot)} " +
                                "${speedProbeStatsDeltaLog(statsSnapshot)} ${playerSnapshot()}"
                        )
                    }
                    // 不更新 lastSavedPositionMs —— 它只在 onStop 时通过 getTime() 更新
                    // 否则 VLC 从 0 开始播放会覆盖正确的保存位置
                }
                MediaPlayer.Event.PositionChanged -> {
                    if (isSpeedProbeActive()) {
                        debugLog(
                            DebugTime,
                            "PositionChanged afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                                "position=${event.positionChanged} ${playerSnapshot()}"
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
                                "count=${event.voutCount} ${playerSnapshot()}"
                        )
                    }
                }
                MediaPlayer.Event.ESAdded ->
                    logEsChangeDuringSpeedProbe("ESAdded", event)
                MediaPlayer.Event.ESDeleted ->
                    logEsChangeDuringSpeedProbe("ESDeleted", event)
                MediaPlayer.Event.ESSelected ->
                    logEsChangeDuringSpeedProbe("ESSelected", event)
            }
        }
        // 在 Compose 中包裹原生视频组件时，必须使用 TextureView 而不是 SurfaceView。
        // SurfaceView 由于其独立的 Window 层级，经常会导致在 Compose 测量和渲染时出现尺寸不同步、四边黑边等异常情况。
        attachVideoLayout(layout, detachFirst = false)

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
        if (initialized && ::mediaPlayer.isInitialized) {
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
        }
    }

    private fun attachVideoLayout(layout: VLCVideoLayout, detachFirst: Boolean) {
        if (detachFirst) {
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
        }
        mediaPlayer.attachViews(layout, null, true, true)
        try {
            mediaPlayer.scale = 0f
        } catch (_: Exception) {}
    }

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        lastMpd = mpd
        pendingLoadStartPositionSec = startPosition.takeIf { it > 0 } ?: 0
        hasPendingLoadRequest = true
        debugLog(
            DebugMpd,
            "LoadRequested mode=mpd startPositionSec=$startPosition hasAudio=${mpd.hasAudio} " +
                "durationSec=${mpd.durationSec} video=${mpd.videoSummary} audio=${mpd.audioSummary ?: "none"}"
        )
        if (::libVLC.isInitialized) {
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

        val media = Media(libVLC, mediaUri)

        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> media.addOption(":http-referrer=$value")
                "user-agent" -> media.addOption(":http-user-agent=$value")
            }
        }

        debugLog(DebugMpd, "MediaPrepared mode=localMpd uri=$mediaUri requestedSpeed=${playbackSpeed.title}")

        mediaPlayer.media = media
        applyPlaybackSpeed()

        pendingSeekMs = 0L
        if (savedPosition > 0) {
            pendingSeekMs = savedPosition
            _state.value = _state.value.copy(currentPosition = (savedPosition / 1000).toInt())
        }

        val shouldPlay = autoPlay || (restorePosition && wasPlayingBeforeBackground)
        val shouldPauseAfterSeek = !shouldPlay && savedPosition > 0

        if (shouldPlay || shouldPauseAfterSeek) {
            pendingPauseAfterStart = shouldPauseAfterSeek
            play()
            // seek 延迟到 Playing 事件执行 —— VLC 此时才完成媒体初始化
            // 若用户在上一个周期离开太快导致 seek 未执行，
            // lastSavedPositionMs 保持不变（不被 TimeChanged 覆盖），
            // 下一个周期会重试同一个正确位置
        }
    }

    actual fun play() {
        mediaPlayer.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    actual fun pause() {
        mediaPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekTo(position: Int) {
        if (!isSeekPositionValid(position)) {
            return
        }
        val targetPosition = position
        val currentState = mediaPlayer.playerState
        // libVLC states: 5 = Stopped, 6 = Ended
        if (currentState == 5 || currentState == 6) {
            val mpd = lastMpd
            if (mpd != null) {
                lastSavedPositionMs = targetPosition * 1000L
                _state.value = _state.value.copy(currentPosition = targetPosition)
                loadInternal(
                    mpd = mpd,
                    startPosition = targetPosition,
                    restorePosition = false
                )
            }
        } else {
            val requestedTime = targetPosition * 1000L
            mediaPlayer.setTime(requestedTime)
            _state.value = _state.value.copy(currentPosition = targetPosition)
        }
    }

    actual fun setVolumeGain(gain: Int) {
        mediaPlayer.setVolume(gain.coerceIn(0, 200))
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        val previousSpeed = playbackSpeed
        val seq = ++speedChangeSeq
        activeSpeedChangeSeq = seq
        speedProbeStartWallTimeMs = System.currentTimeMillis()
        speedProbeUntilWallTimeMs = speedProbeStartWallTimeMs + SpeedProbeWindowMs
        val statsBefore = vlcStatsSnapshot()
        lastSpeedProbeStats = statsBefore
        debugLog(
            DebugSpeed,
            "SpeedChange begin seq=$seq previous=${previousSpeed.title} previousRate=${previousSpeed.rateNumber} " +
                "selected=${speed.title} selectedRate=${speed.rateNumber} ${statsLog(statsBefore)} before=${playerSnapshot()}"
        )
        playbackSpeed = speed
        applyPlaybackSpeed(seq)
        val statsAfter = vlcStatsSnapshot()
        debugLog(
            DebugSpeed,
            "SpeedChange end seq=$seq selected=${speed.title} stored=${playbackSpeed.title} " +
                "${statsLog(statsAfter)} ${statsAfter?.deltaLogString(statsBefore) ?: "statsDelta=null"} after=${playerSnapshot()}"
        )
    }

    actual fun release() {
        if (!initialized) {
            return
        }
        if (::mediaPlayer.isInitialized) {
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
        if (::libVLC.isInitialized) {
            try { libVLC.release() } catch (_: Exception) {}
        }
        runCatching { lastMpdFile?.delete() }
        lastMpdFile = null
        videoLayout = null
        initialized = false
    }

    private fun getDurationForCompletion(): Int {
        val stateDuration = _state.value.duration
        if (stateDuration > 0) return stateDuration
        return try {
            if (::mediaPlayer.isInitialized) (mediaPlayer.getLength() / 1000).toInt().coerceAtLeast(0) else 0
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

    private fun applyPlaybackSpeed(speedProbeSeq: Long? = null) {
        if (::mediaPlayer.isInitialized) {
            try {
                val requestedSpeed = playbackSpeed
                val statsBefore = vlcStatsSnapshot()
                if (speedProbeSeq != null) {
                    debugLog(
                        DebugSpeed,
                        "applyPlaybackSpeed before seq=$speedProbeSeq requested=${requestedSpeed.title} " +
                            "requestedRate=${requestedSpeed.rateNumber} ${statsLog(statsBefore)} ${playerSnapshot()}"
                    )
                }
                mediaPlayer.setRate(requestedSpeed.rateNumber)
                val nativeRate = mediaPlayer.getRate()
                playbackSpeed = BoloPlayerSpeed.fromRateNumber(nativeRate)
                val statsAfter = vlcStatsSnapshot()
                if (speedProbeSeq != null) {
                    debugLog(
                        DebugSpeed,
                        "applyPlaybackSpeed after seq=$speedProbeSeq nativeRate=$nativeRate stored=${playbackSpeed.title} " +
                            "${statsLog(statsAfter)} ${statsAfter?.deltaLogString(statsBefore) ?: "statsDelta=null"} ${playerSnapshot()}"
                    )
                }
            } catch (e: Exception) {
                debugLog(DebugSpeed, "applyPlaybackSpeed failed seq=${speedProbeSeq ?: "none"} error=${e.message} ${playerSnapshot()}")
            }
        }
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }

    private fun isSpeedProbeActive(): Boolean =
        System.currentTimeMillis() <= speedProbeUntilWallTimeMs

    private fun speedProbeElapsedMs(): Long =
        System.currentTimeMillis() - speedProbeStartWallTimeMs

    private fun logEsChangeDuringSpeedProbe(name: String, event: MediaPlayer.Event) {
        if (!isSpeedProbeActive()) return
        debugLog(
            DebugAudio,
            "$name afterSpeedSwitch seq=$activeSpeedChangeSeq wallElapsedMs=${speedProbeElapsedMs()} " +
                "type=${event.esChangedType} id=${event.esChangedID} ${playerSnapshot()}"
        )
    }

    private fun vlcStatsSnapshot(): VlcStatsSnapshot? {
        if (!::mediaPlayer.isInitialized) return null
        val stats = runCatching { mediaPlayer.media?.stats }.getOrNull() ?: return null
        return VlcStatsSnapshot(
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

    private fun playerSnapshot(): String {
        if (!::mediaPlayer.isInitialized) {
            return "mediaPlayer=notInitialized"
        }
        val nativeTime = runCatching { mediaPlayer.getTime() }.getOrNull()
        val nativeLength = runCatching { mediaPlayer.getLength() }.getOrNull()
        val nativePosition = runCatching { mediaPlayer.position }.getOrNull()
        val nativeRate = runCatching { mediaPlayer.getRate() }.getOrNull()
        val nativeState = runCatching { mediaPlayer.playerState }.getOrNull()
        val volume = runCatching { mediaPlayer.getVolume() }.getOrNull()
        val audioTrack = runCatching { mediaPlayer.getAudioTrack() }.getOrNull()
        val audioTrackCount = runCatching { mediaPlayer.getAudioTracksCount() }.getOrNull()
        val audioDelayUs = runCatching { mediaPlayer.getAudioDelay() }.getOrNull()
        val videoTrack = runCatching { mediaPlayer.getVideoTrack() }.getOrNull()
        val videoTrackCount = runCatching { mediaPlayer.getVideoTracksCount() }.getOrNull()
        val state = _state.value
        return "nativeState=$nativeState nativeTimeMs=$nativeTime nativeLengthMs=$nativeLength nativePosition=$nativePosition " +
            "nativeRate=$nativeRate volume=$volume audioTrack=$audioTrack/$audioTrackCount audioDelayUs=$audioDelayUs " +
            "videoTrack=$videoTrack/$videoTrackCount statePlaying=${state.isPlaying} stateBuffering=${state.isBuffering} " +
            "statePositionSec=${state.currentPosition} stateDurationSec=${state.duration} stateSpeed=${state.playbackSpeed.title}"
    }

}
