package tv.hsrui.bolo.player.base

import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

actual class BoloPlayerController actual constructor(
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
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

    private var lastVideoUrl: String? = null
    private var lastAudioUrl: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun bindLifecycle(lifecycle: Lifecycle) {
        try { lifecycleObserver?.let { this.lifecycle?.removeObserver(it) } } catch (_: Exception) {}
        this.lifecycle = lifecycle

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                println("[PLAYER] onStart  initialized=$initialized  lastSaved=$lastSavedPositionMs  wasPlaying=$wasPlayingBeforeBackground")
                if (!initialized) {
                    val layout = videoLayout
                    if (layout != null && layout.isAttachedToWindow) {
                        println("[PLAYER] onStart  calling bindVideo (background return)")
                        bindVideo(layout)
                    } else {
                        println("[PLAYER] onStart  skip: layoutAttached=${layout?.isAttachedToWindow}")
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                println("[PLAYER] onStop  isPlaying=${_state.value.isPlaying}  displayPos=${_state.value.currentPositionMs}  lastSaved=$lastSavedPositionMs")
                if (initialized && ::mediaPlayer.isInitialized) {
                    wasPlayingBeforeBackground = _state.value.isPlaying
                    val displayPos = _state.value.currentPositionMs
                    try {
                        val pos = mediaPlayer.getTime()
                        println("[PLAYER] onStop  getTime()=$pos")
                        // TimeChanged 间隔 ~500ms，getTime() 应在此范围内超前
                        // 若不在此范围则说明 VLC 状态异常，保持 displayPos
                        when {
                            displayPos == 0L && pos > 0 -> {
                                lastSavedPositionMs = pos
                                println("[PLAYER] onStop  first save: $pos")
                            }
                            pos >= displayPos && pos <= displayPos + 1000 -> {
                                lastSavedPositionMs = pos
                                println("[PLAYER] onStop  accepted getTime: $pos")
                            }
                            pos > 0 -> {
                                lastSavedPositionMs = displayPos
                                println("[PLAYER] onStop  getTime out of range ($pos), fallback to displayPos=$displayPos")
                            }
                        }
                    } catch (e: Exception) {
                        println("[PLAYER] onStop  getTime FAILED: ${e.message}, using displayPos=$displayPos")
                        lastSavedPositionMs = displayPos
                    }
                    println("[PLAYER] onStop  lastSavedAfter=$lastSavedPositionMs")
                    _state.value = _state.value.copy(currentPositionMs = lastSavedPositionMs)
                    release()
                }
            }
        }
        lifecycleObserver = observer
        lifecycle.addObserver(observer)
    }

    fun bindVideo(layout: VLCVideoLayout) {
        println("[PLAYER] bindVideo  initialized=$initialized  lastSaved=$lastSavedPositionMs")
        videoLayout = layout
        if (initialized) {
            println("[PLAYER] bindVideo  already initialized, skip")
            return
        }
        initialized = true
        println("[PLAYER] bindVideo  creating LibVLC + MediaPlayer")

        LibVLC.loadLibraries()
        libVLC = LibVLC(layout.context)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening ->
                    _state.value = _state.value.copy(isBuffering = true)
                MediaPlayer.Event.Buffering ->
                    _state.value = _state.value.copy(isBuffering = true)
                MediaPlayer.Event.Playing -> {
                    println("[PLAYER] Playing  pendingSeek=$pendingSeekMs  pendingPause=$pendingPauseAfterStart")
                    _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                    if (pendingSeekMs > 0) {
                        println("[PLAYER] Playing  setTime($pendingSeekMs)")
                        mediaPlayer.setTime(pendingSeekMs)
                        pendingSeekMs = 0L
                    }
                    if (pendingPauseAfterStart) {
                        println("[PLAYER] Playing  pause after seek")
                        pendingPauseAfterStart = false
                        mediaPlayer.pause()
                    }
                }
                MediaPlayer.Event.Paused ->
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                MediaPlayer.Event.Stopped ->
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                MediaPlayer.Event.EndReached ->
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                MediaPlayer.Event.EncounteredError -> {
                    println("[PLAYER] EncounteredError")
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    _state.value = _state.value.copy(currentPositionMs = event.timeChanged)
                    // 不更新 lastSavedPositionMs —— 它只在 onStop 时通过 getTime() 更新
                    // 否则 VLC 从 0 开始播放会覆盖正确的保存位置
                }
                MediaPlayer.Event.LengthChanged -> {
                    val len = event.lengthChanged
                    if (len > 0) {
                        _state.value = _state.value.copy(durationMs = len)
                    }
                }
            }
        }
        mediaPlayer.attachViews(layout, null, true, false)

        val url = lastVideoUrl
        if (url != null) {
            loadInternal(url, lastAudioUrl)
        }
    }

    actual fun load(videoUrl: String, audioUrl: String?) {
        lastVideoUrl = videoUrl
        lastAudioUrl = audioUrl
        println("[PLAYER] load  libVLC ready=${::libVLC.isInitialized}")
        if (::libVLC.isInitialized) {
            loadInternal(videoUrl, audioUrl)
        }
    }

    private fun loadInternal(videoUrl: String, audioUrl: String?) {
        val savedPosition = lastSavedPositionMs
        println("[PLAYER] loadInternal  savedPosition=$savedPosition  wasPlaying=$wasPlayingBeforeBackground  autoPlay=$autoPlay")

        pendingPauseAfterStart = false

        val media = Media(libVLC, Uri.parse(videoUrl))

        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> media.addOption(":http-referrer=$value")
                "user-agent" -> media.addOption(":http-user-agent=$value")
            }
        }

        if (audioUrl != null) {
            media.addOption(":input-slave=$audioUrl")
        }

        mediaPlayer.media = media

        pendingSeekMs = 0L
        if (savedPosition > 0) {
            pendingSeekMs = savedPosition
            _state.value = _state.value.copy(currentPositionMs = savedPosition)
        }

        val shouldPlay = autoPlay || wasPlayingBeforeBackground
        val shouldPauseAfterSeek = !shouldPlay && savedPosition > 0

        if (shouldPlay || shouldPauseAfterSeek) {
            println("[PLAYER] loadInternal  play()  shouldPauseAfterSeek=$shouldPauseAfterSeek")
            play()
            // seek 延迟到 Playing 事件执行 —— VLC 此时才完成媒体初始化
            // 若用户在上一个周期离开太快导致 seek 未执行，
            // lastSavedPositionMs 保持不变（不被 TimeChanged 覆盖），
            // 下一个周期会重试同一个正确位置
        } else {
            println("[PLAYER] loadInternal  idle")
        }
    }

    actual fun play() {
        println("[PLAYER] play()")
        mediaPlayer.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    actual fun pause() {
        println("[PLAYER] pause()")
        mediaPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekTo(positionMs: Long) {
        println("[PLAYER] seekTo($positionMs)")
        mediaPlayer.setTime(positionMs)
    }

    actual fun setVolumeGain(gain: Int) {
        mediaPlayer.setVolume((gain.coerceIn(0, 200) * 100) / 200)
    }

    actual fun release() {
        println("[PLAYER] release  initialized=$initialized  lastSaved=$lastSavedPositionMs")
        if (!initialized) {
            println("[PLAYER] release  skip: already released")
            return
        }
        scope.coroutineContext[Job]?.cancel()
        if (::mediaPlayer.isInitialized) {
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
        if (::libVLC.isInitialized) {
            try { libVLC.release() } catch (_: Exception) {}
        }
        wasPlayingBeforeBackground = false
        initialized = false
        println("[PLAYER] release  done")
    }
}
