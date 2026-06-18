package tv.hsrui.bolo.player.base

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerState
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIView
import cocoapods.MobileVLCKit.VLCMediaPlayerStateChanged
import cocoapods.MobileVLCKit.VLCMediaPlayerTimeChanged

@OptIn(ExperimentalForeignApi::class)
actual class BoloPlayerController actual constructor(
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal val mediaPlayer = VLCMediaPlayer()
    private var wasPlayingBeforeBackground = false
    private var hasReachedPlaying = false
    private var initialized = false

    // ── 位置恢复 ──
    private var lastSavedPositionMs = 0L

    // ── 重建所需数据 ──
    private var lastVideoUrl: String? = null
    private var lastAudioUrl: String? = null

    // ── 延迟 seek ── (VLC 需要时间打开媒体，seek 必须在 Playing 事件中执行)
    private var pendingSeekMs = 0L
    private var pendingPauseAfterSeek = false

    init {
        println("[PLAYER] init  creating VLCMediaPlayer")
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, null)
            setActive(true, null)
        }

        NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerStateChanged, null, NSOperationQueue.mainQueue
        ) { _ -> onStateChanged() }

        NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerTimeChanged, null, NSOperationQueue.mainQueue
        ) { _ -> updateTimeAndDuration() }

        // 后台 → 保存 + 释放
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            println("[PLAYER] didEnterBackground  isPlaying=${_state.value.isPlaying}")
            release()
        }
        // 前台 → 重建
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            println("[PLAYER] willEnterForeground  lastSaved=$lastSavedPositionMs  wasPlaying=$wasPlayingBeforeBackground  lastUrl=${lastVideoUrl != null}")
            restoreFromSavedState()
        }
    }

    /**
     * Composable 重建时调用 —— 绑定 drawable，若 VLC 已释放则重新加载媒体。
     * 类似 Android 的 bindVideo()。
     */
    fun bindDrawable(view: UIView) {
        println("[PLAYER] bindDrawable  initialized=$initialized  lastSaved=$lastSavedPositionMs  lastUrl=${lastVideoUrl != null}")
        mediaPlayer.drawable = view
        if (!initialized || mediaPlayer.media == null) {
            // VLC 已释放（release() 清空了 media），需要重建
            val url = lastVideoUrl
            if (url != null) {
                println("[PLAYER] bindDrawable  rebuilding from saved state")
                initialized = true
                hasReachedPlaying = false
                loadInternal(url, lastAudioUrl, restorePosition = true)
            } else {
                println("[PLAYER] bindDrawable  no URL cached, waiting for load()")
                initialized = true
            }
        } else {
            println("[PLAYER] bindDrawable  already playing, just rebind drawable")
        }
    }

    private fun onStateChanged() {
        when (mediaPlayer.state) {
            VLCMediaPlayerState.VLCMediaPlayerStateOpening ->
                if (!hasReachedPlaying)
                    _state.value = _state.value.copy(isBuffering = true)
            VLCMediaPlayerState.VLCMediaPlayerStateBuffering ->
                if (!hasReachedPlaying)
                    _state.value = _state.value.copy(isBuffering = true)
            VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                println("[PLAYER] state=Playing  pendingSeek=$pendingSeekMs  pendingPause=$pendingPauseAfterSeek")
                _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                if (pendingSeekMs > 0) {
                    val ms = pendingSeekMs
                    pendingSeekMs = 0L
                    val mediaLength = mediaPlayer.media?.length?.value?.longValue ?: 0L
                    println("[PLAYER] Playing seek: mediaLength=$mediaLength  targetMs=$ms")
                    if (mediaLength > 0 && ms > 1000 && ms < mediaLength) {
                        val ratio = ms.toFloat() / mediaLength.toFloat()
                        println("[PLAYER] Playing seek to ratio=$ratio")
                        mediaPlayer.position = ratio
                        _state.value = _state.value.copy(currentPositionMs = ms)
                    }
                }
                if (pendingPauseAfterSeek) {
                    pendingPauseAfterSeek = false
                    println("[PLAYER] Playing pause after seek")
                    mediaPlayer.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                }
                hasReachedPlaying = true
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePaused ->
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            VLCMediaPlayerState.VLCMediaPlayerStateStopped ->
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            VLCMediaPlayerState.VLCMediaPlayerStateEnded ->
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                println("[PLAYER] state=Error")
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                onError(BoloPlayerError.UnknownError("VLC 播放错误"))
            }
            else -> Unit
        }
    }

    private fun updateTimeAndDuration() {
        val posMs = mediaPlayer.time.value?.longValue ?: return
        _state.value = _state.value.copy(currentPositionMs = posMs)
        // 仅当 remainingTime 有效时才更新 duration（VLCKit 有时返回 0）
        val remaining = mediaPlayer.remainingTime?.value?.longValue ?: return
        if (remaining > 0) {
            val durMs = posMs + remaining
            _state.value = _state.value.copy(durationMs = durMs)
        }
    }

    private fun savePositionFromCurrent() {
        val displayPos = _state.value.currentPositionMs
        val timeVal = mediaPlayer.time.value
        val posMs = timeVal?.longValue
        println("[PLAYER] savePosition  displayPos=$displayPos  timeVal=$timeVal  posMs=$posMs")
        if (posMs == null) {
            println("[PLAYER] savePosition  timeVal is null, keeping lastSaved=$lastSavedPositionMs")
            return
        }
        when {
            displayPos == 0L && posMs > 0 -> {
                lastSavedPositionMs = posMs
                println("[PLAYER] savePosition  first save: $posMs")
            }
            posMs >= displayPos && posMs <= displayPos + 1000 -> {
                lastSavedPositionMs = posMs
                println("[PLAYER] savePosition  accepted: $posMs")
            }
            posMs > 0 -> {
                lastSavedPositionMs = displayPos
                println("[PLAYER] savePosition  out of range ($posMs), fallback to display=$displayPos")
            }
        }
    }

    private fun restoreFromSavedState() {
        val videoUrl = lastVideoUrl
        if (videoUrl == null) {
            println("[PLAYER] restoreFromSavedState  no URL, skip")
            return
        }
        println("[PLAYER] restoreFromSavedState  url=$videoUrl  lastSaved=$lastSavedPositionMs  duration=${_state.value.durationMs}")
        hasReachedPlaying = false
        loadInternal(videoUrl, lastAudioUrl, restorePosition = true)
    }

    actual fun load(videoUrl: String, audioUrl: String?) {
        println("[PLAYER] load  videoUrl=$videoUrl")
        lastVideoUrl = videoUrl
        lastAudioUrl = audioUrl
        initialized = true
        hasReachedPlaying = false
        loadInternal(videoUrl, audioUrl, restorePosition = false)
    }

    private fun loadInternal(
        videoUrl: String,
        audioUrl: String?,
        restorePosition: Boolean
    ) {
        println("[PLAYER] loadInternal  restore=$restorePosition  lastSaved=$lastSavedPositionMs  duration=${_state.value.durationMs}  wasPlaying=$wasPlayingBeforeBackground")
        val nsUrl = NSURL.URLWithString(videoUrl)
        if (nsUrl == null) {
            onError(BoloPlayerError.NetworkError("视频 URL 无效: $videoUrl"))
            return
        }

        val media = VLCMedia(uRL = nsUrl)
        val options = mutableMapOf<Any?, Any?>()
        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> options["http-referrer"] = value
                "user-agent" -> options["http-user-agent"] = value
                else -> options["http-header-fields"] = "$key: $value"
            }
        }
        if (audioUrl != null) {
            options["input-slave"] = audioUrl
        }
        media.addOptions(options)
        mediaPlayer.media = media
        println("[PLAYER] loadInternal  media set")

        // 延迟 seek：此时 media.length 尚未就绪（为 0），必须在 Playing 事件中执行
        pendingSeekMs = 0L
        pendingPauseAfterSeek = false
        if (restorePosition && lastSavedPositionMs > 0) {
            pendingSeekMs = lastSavedPositionMs
            _state.value = _state.value.copy(currentPositionMs = lastSavedPositionMs)
            println("[PLAYER] loadInternal  deferred seek: $pendingSeekMs ms")
        }

        val shouldPlay = autoPlay || (restorePosition && wasPlayingBeforeBackground)
        val shouldPauseAfterSeek = !shouldPlay && pendingSeekMs > 0

        if (shouldPlay || shouldPauseAfterSeek) {
            println("[PLAYER] loadInternal  calling play(), shouldPauseAfterSeek=$shouldPauseAfterSeek")
            pendingPauseAfterSeek = shouldPauseAfterSeek
            play()
        } else {
            println("[PLAYER] loadInternal  not playing (autoPlay=$autoPlay restore=$restorePosition wasPlaying=$wasPlayingBeforeBackground)")
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
        val durationMs = mediaPlayer.media?.length?.value?.longValue ?: _state.value.durationMs
        if (durationMs > 0) {
            mediaPlayer.position = positionMs.toFloat() / durationMs.toFloat()
        }
    }

    actual fun setVolumeGain(gain: Int) {
        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayer.audio?.volume = vlcVolume
    }

    actual fun release() {
        println("[PLAYER] release  isPlaying=${_state.value.isPlaying}  displayPos=${_state.value.currentPositionMs}")
        wasPlayingBeforeBackground = _state.value.isPlaying
        savePositionFromCurrent()
        mediaPlayer.stop()
        mediaPlayer.media = null
        initialized = false
        println("[PLAYER] release  done  lastSaved=$lastSavedPositionMs  wasPlaying=$wasPlayingBeforeBackground")
    }
}
