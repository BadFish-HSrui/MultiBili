package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.io.File

actual class BoloPlayerController actual constructor(
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    // 可重建 —— release 后置 null，下次使用时重新创建
    internal var mediaPlayerComponent: CallbackMediaPlayerComponent? = null
    private var positionPollingJob: Job? = null

    // ── 位置恢复 ──
    private var lastSavedPositionMs = 0L
    private var wasPlayingBeforeBackground = false

    // ── 重建所需数据 ──
    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFile: File? = null
    private var playbackSpeed = BoloPlayerSpeed.default

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /** 确保 VLC 已初始化，若已释放则重建 */
    private fun ensureInitialized(): MediaPlayer? {
        mediaPlayerComponent?.let { return it.mediaPlayer() }

        return try {
            NativeDiscovery().discover()
            val component = CallbackMediaPlayerComponent()
            component.mediaPlayer().events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {
                    override fun opening(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isBuffering = true)
                    }

                    override fun playing(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                        startPositionPolling()
                        readTrackInfo(mediaPlayer)
                        applyPlaybackSpeed(mediaPlayer)
                    }

                    override fun paused(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    }

                    override fun stopped(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        stopPositionPolling()
                    }

                    override fun finished(mediaPlayer: MediaPlayer) {
                        val duration = getDurationForCompletion(mediaPlayer)
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            currentPosition = duration
                        )
                        lastSavedPositionMs = duration * 1000L
                        stopPositionPolling()
                    }

                    override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                        _state.value = _state.value.copy(isBuffering = newCache < 100f)
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                    }

                    override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                        _state.value = _state.value.copy(duration = (newLength / 1000).toInt())
                    }
                }
            )
            mediaPlayerComponent = component
            component.mediaPlayer()
        } catch (e: Exception) {
            onError(BoloPlayerError.UnknownError("VLC 初始化失败，请确认已安装 VLC Player: ${e.message}", e))
            null
        }
    }

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        lastMpd = mpd
        val player = ensureInitialized() ?: run {
            onError(BoloPlayerError.UnknownError("VLC 未初始化，无法加载视频"))
            return
        }
        val mpdFile = try {
            writeMpdFile(mpd)
        } catch (e: Exception) {
            onError(BoloPlayerError.UnknownError("DASH MPD 文件写入失败: ${e.message}", e))
            return
        }

        val opts = mutableListOf<String>()
        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> opts.add(":http-referrer=$value")
                "user-agent" -> opts.add(":http-user-agent=$value")
                else -> opts.add(":http-header-fields=$key: $value")
            }
        }
        try {
            player.media().play(mpdFile.toURI().toString(), *opts.toTypedArray())
            applyPlaybackSpeed(player)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            when {
                msg.contains("404", ignoreCase = true) || msg.contains("403", ignoreCase = true) ->
                    onError(BoloPlayerError.NetworkError("HTTP 错误: $msg", e))
                msg.contains("timeout", ignoreCase = true) || msg.contains("connection", ignoreCase = true) ->
                    onError(BoloPlayerError.NetworkError("网络错误: $msg", e))
                msg.contains("codec", ignoreCase = true) || msg.contains("format", ignoreCase = true) ->
                    onError(BoloPlayerError.FormatNotSupported("格式不支持: $msg"))
                else ->
                    onError(BoloPlayerError.UnknownError("VLC 播放错误: $msg", e))
            }
            return
        }

        val startPositionMs = startPosition.takeIf { it > 0 }?.let { it * 1000L } ?: 0L
        when {
            startPositionMs > 0 -> {
                player.controls().setTime(startPositionMs)
                _state.value = _state.value.copy(currentPosition = startPosition)
            }
            lastSavedPositionMs > 1000 -> {
                player.controls().setTime(lastSavedPositionMs)
                _state.value = _state.value.copy(currentPosition = (lastSavedPositionMs / 1000).toInt())
            }
        }

        if (!autoPlay && !wasPlayingBeforeBackground) {
            player.controls().pause()
        }

    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        onError(error)
    }

    private fun readTrackInfo(player: MediaPlayer) {
        var videoBr: Long = 0L
        var audioBr: Long = 0L
        player.media().info().videoTracks().forEach { track ->
            val codec = track.codecName() ?: ""
            val br = track.bitRate().toLong()
            if (br > 0) videoBr = br
            _state.value = _state.value.copy(
                videoCodec = codec.uppercase(),
                videoWidth = track.width(),
                videoHeight = track.height()
            )
        }
        player.media().info().audioTracks().forEach { track ->
            val codec = track.codecName() ?: ""
            val br = track.bitRate().toLong()
            if (br > 0) audioBr = br
            _state.value = _state.value.copy(audioCodec = codec.uppercase())
        }
        _state.value = _state.value.copy(videoBitrate = videoBr, audioBitrate = audioBr)
    }

    actual fun play() {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.play()
    }

    actual fun pause() {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.pause()
    }

    actual fun seekTo(position: Int) {
        if (!isSeekPositionValid(position)) {
            return
        }
        val player = mediaPlayerComponent?.mediaPlayer() ?: return
        player.controls().setTime(position * 1000L)
        _state.value = _state.value.copy(currentPosition = position)
    }

    actual fun setVolumeGain(gain: Int) {
        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayerComponent?.mediaPlayer()?.audio()?.setVolume(vlcVolume)
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        playbackSpeed = speed
        applyPlaybackSpeed()
    }

    actual fun release() {
        // 保存当前状态
        wasPlayingBeforeBackground = _state.value.isPlaying
        savePositionFromCurrent()
        // 销毁 VLC
        stopPositionPolling()
        try { mediaPlayerComponent?.mediaPlayer()?.controls()?.stop() } catch (_: Exception) {}
        try { mediaPlayerComponent?.mediaPlayer()?.release() } catch (_: Exception) {}
        try { mediaPlayerComponent?.release() } catch (_: Exception) {}
        runCatching { lastMpdFile?.delete() }
        lastMpdFile = null
        mediaPlayerComponent = null
    }

    private fun writeMpdFile(mpd: BoloDashMpd): File {
        val mpdDir = File(System.getProperty("java.io.tmpdir"), "bolo_dash_mpd").apply { mkdirs() }
        val mpdFile = File(mpdDir, "bolo_${System.currentTimeMillis()}.mpd")
        runCatching { lastMpdFile?.delete() }
        mpdFile.writeText(mpd.xml, Charsets.UTF_8)
        lastMpdFile = mpdFile
        return mpdFile
    }

    private fun savePositionFromCurrent() {
        val displayPos = _state.value.currentPosition * 1000L
        val posMs = mediaPlayerComponent?.mediaPlayer()?.status()?.time() ?: return
        when {
            displayPos == 0L && posMs > 0 -> lastSavedPositionMs = posMs
            posMs >= displayPos && posMs <= displayPos + 1000 -> lastSavedPositionMs = posMs
            posMs > 0 -> lastSavedPositionMs = displayPos
        }
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                val posMs = mediaPlayerComponent?.mediaPlayer()?.status()?.time() ?: 0L
                val stats = mediaPlayerComponent?.mediaPlayer()?.media()?.info()?.statistics()
                val speed = stats?.inputBitrate()?.toLong()?.let { if (it > 0L) it * 8 else 0L } ?: 0L
                _state.value = _state.value.copy(
                    currentPosition = (posMs / 1000).toInt(),
                    transferSpeed = speed
                )
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
    }

    private fun getDurationForCompletion(player: MediaPlayer? = mediaPlayerComponent?.mediaPlayer()): Int {
        val stateDuration = _state.value.duration
        if (stateDuration > 0) return stateDuration
        val lengthMs = try {
            player?.status()?.length() ?: 0L
        } catch (_: Exception) {
            0L
        }
        return (lengthMs / 1000).toInt().coerceAtLeast(0)
    }

    private fun isSeekPositionValid(position: Int): Boolean {
        val duration = _state.value.duration
        return position >= 0 && (duration <= 0 || position <= duration)
    }

    private fun applyPlaybackSpeed(player: MediaPlayer? = mediaPlayerComponent?.mediaPlayer()) {
        if (player != null) {
            try {
                if (player.controls().setRate(playbackSpeed.rateNumber)) {
                    playbackSpeed = BoloPlayerSpeed.fromRateNumber(player.status().rate())
                }
            } catch (_: Exception) {}
        }
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }
}
