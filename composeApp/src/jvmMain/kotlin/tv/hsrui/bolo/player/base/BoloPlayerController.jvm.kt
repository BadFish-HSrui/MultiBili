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
    private var lastVideoUrl: String? = null
    private var lastAudioUrl: String? = null

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
                    }

                    override fun paused(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    }

                    override fun stopped(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        stopPositionPolling()
                    }

                    override fun finished(mediaPlayer: MediaPlayer) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
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
                        _state.value = _state.value.copy(durationMs = newLength)
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

    actual fun load(videoUrl: String, audioUrl: String?) {
        lastVideoUrl = videoUrl
        lastAudioUrl = audioUrl
        val player = ensureInitialized() ?: run {
            onError(BoloPlayerError.UnknownError("VLC 未初始化，无法加载视频"))
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
        if (audioUrl != null) {
            opts.add(":input-slave=$audioUrl")
        }
        try {
            player.media().play(videoUrl, *opts.toTypedArray())
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

        // 恢复位置
        if (lastSavedPositionMs > 1000) {
            player.controls().setTime(lastSavedPositionMs)
        }

        if (!autoPlay && !wasPlayingBeforeBackground) {
            player.controls().pause()
        }

        // 读取媒体信息
        readTrackInfo(player)
    }

    private fun readTrackInfo(player: MediaPlayer) {
        var videoBr: Long? = null
        var audioBr: Long? = null
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

    actual fun seekTo(positionMs: Long) {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.setTime(positionMs)
    }

    actual fun setVolumeGain(gain: Int) {
        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayerComponent?.mediaPlayer()?.audio()?.setVolume(vlcVolume)
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
        mediaPlayerComponent = null
        scope.coroutineContext[Job]?.cancel()
    }

    private fun savePositionFromCurrent() {
        val displayPos = _state.value.currentPositionMs
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
                _state.value = _state.value.copy(currentPositionMs = posMs)
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
    }
}
