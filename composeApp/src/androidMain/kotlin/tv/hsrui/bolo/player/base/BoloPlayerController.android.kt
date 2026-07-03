package tv.hsrui.bolo.player.base

import android.net.Uri
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
    private var hasPendingLoadRequest = false
    private var pendingLoadStartPositionSec = 0

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
        libVLC = LibVLC(layout.context)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening ->
                    _state.value = _state.value.copy(isBuffering = true)
                MediaPlayer.Event.Buffering ->
                    _state.value = _state.value.copy(isBuffering = true)
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
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    val tc = event.timeChanged
                    val stateBefore = _state.value
                    val stats = mediaPlayer.media?.stats
                    // 使用 inputBitrate 作为实时传输速度 (bps，所以可能需要乘 8，如果它是 bytes/sec，需要转换，但先直接返回长整型)
                    val speed = stats?.inputBitrate?.toLong()?.let { if (it > 0L) it * 8 else 0L } ?: 0L
                    val reportedPositionSec = (tc / 1000).toInt()
                    _state.value = stateBefore.copy(
                        currentPosition = reportedPositionSec,
                        transferSpeed = speed
                    )
                    // 不更新 lastSavedPositionMs —— 它只在 onStop 时通过 getTime() 更新
                    // 否则 VLC 从 0 开始播放会覆盖正确的保存位置
                }
                MediaPlayer.Event.LengthChanged -> {
                    val len = event.lengthChanged
                    if (len > 0) {
                        _state.value = _state.value.copy(duration = (len / 1000).toInt())
                    }
                }
            }
        }
        // 在 Compose 中包裹原生视频组件时，必须使用 TextureView 而不是 SurfaceView。
        // SurfaceView 由于其独立的 Window 层级，经常会导致在 Compose 测量和渲染时出现尺寸不同步、四边黑边等异常情况。
        attachVideoLayout(layout, detachFirst = false)

        val url = lastVideoUrl
        if (url != null) {
            val startPosition = pendingLoadStartPositionSec
            val isPendingLoad = hasPendingLoadRequest
            pendingLoadStartPositionSec = 0
            hasPendingLoadRequest = false
            loadInternal(
                videoUrl = url,
                audioUrl = lastAudioUrl,
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

    actual fun load(videoUrl: String, audioUrl: String?, startPosition: Int) {
        lastVideoUrl = videoUrl
        lastAudioUrl = audioUrl
        pendingLoadStartPositionSec = startPosition.takeIf { it > 0 } ?: 0
        hasPendingLoadRequest = true
        if (::libVLC.isInitialized) {
            val pendingStartPosition = pendingLoadStartPositionSec
            pendingLoadStartPositionSec = 0
            hasPendingLoadRequest = false
            loadInternal(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                startPosition = pendingStartPosition,
                restorePosition = false
            )
        }
    }

    private fun loadInternal(
        videoUrl: String,
        audioUrl: String?,
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
            val url = lastVideoUrl
            if (url != null) {
                lastSavedPositionMs = targetPosition * 1000L
                _state.value = _state.value.copy(currentPosition = targetPosition)
                loadInternal(
                    videoUrl = url,
                    audioUrl = lastAudioUrl,
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

}
