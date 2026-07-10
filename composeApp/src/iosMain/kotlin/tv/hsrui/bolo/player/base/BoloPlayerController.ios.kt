package tv.hsrui.bolo.player.base

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
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
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
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
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal val mediaPlayer = VLCMediaPlayer()
    private var wasPlayingBeforeBackground = false
    private var hasReachedPlaying = false
    private var initialized = false

    // NSNotification observer tokens，用于注销通知
    private val observers = mutableListOf<Any>()

    // ── 位置恢复 ──
    private var lastSavedPositionMs = 0L

    // ── 重建所需数据 ──
    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFilePath: String? = null
    private var playbackSpeed = BoloPlayerSpeed.default

    // ── 延迟 seek ── (VLC 需要时间打开媒体，seek 必须在 Playing 事件中执行)
    private var pendingSeekMs = 0L
    private var pendingPauseAfterSeek = false

    init {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, null)
            setActive(true, null)
        }

        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerStateChanged, null, NSOperationQueue.mainQueue
        ) { _ -> onStateChanged() }

        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerTimeChanged, null, NSOperationQueue.mainQueue
        ) { _ -> updateTimeAndDuration() }

        // 后台 → 保存 + 释放
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            release()
        }
        // 前台 → 重建
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            restoreFromSavedState()
        }
    }

    /**
     * Composable 重建时调用 —— 绑定 drawable，若 VLC 已释放则重新加载媒体。
     * 类似 Android 的 bindVideo()。
     */
    fun bindDrawable(view: UIView) {
        mediaPlayer.drawable = view
        if (!initialized || mediaPlayer.media == null) {
            // VLC 已释放（release() 清空了 media），需要重建
            val mpd = lastMpd
            if (mpd != null) {
                initialized = true
                hasReachedPlaying = false
                loadInternal(mpd, restorePosition = true)
            } else {
                initialized = true
            }
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
                var videoCodec = ""
                var audioCodec = ""
                var videoWidth = 0
                var videoHeight = 0
                var videoBr: Long = 0L
                var audioBr: Long = 0L
                
                val tracks = mediaPlayer.media?.tracksInformation as? List<Map<Any?, Any?>>
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
                applyPlaybackSpeed()
                if (pendingSeekMs > 0) {
                    val ms = pendingSeekMs
                    pendingSeekMs = 0L
                    val mediaLength = mediaPlayer.media?.length?.value?.longValue ?: 0L
                    if (mediaLength > 0 && ms > 1000 && ms < mediaLength) {
                        val ratio = ms.toFloat() / mediaLength.toFloat()
                        mediaPlayer.position = ratio
                        _state.value = _state.value.copy(currentPosition = (ms / 1000).toInt())
                    }
                }
                if (pendingPauseAfterSeek) {
                    pendingPauseAfterSeek = false
                    mediaPlayer.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                }
                hasReachedPlaying = true
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePaused ->
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            VLCMediaPlayerState.VLCMediaPlayerStateStopped ->
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            VLCMediaPlayerState.VLCMediaPlayerStateEnded -> {
                val duration = getDurationForCompletion()
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    currentPosition = duration
                )
                lastSavedPositionMs = duration * 1000L
            }
            VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                onError(BoloPlayerError.UnknownError("VLC 播放错误"))
            }
            else -> Unit
        }
    }

    private fun updateTimeAndDuration() {
        val posMs = mediaPlayer.time.value?.longValue ?: return
        
        _state.value = _state.value.copy(
            currentPosition = (posMs / 1000).toInt(),
            transferSpeed = 0L
        )
        // VLCKit 的 remainingTime 通常为负值（表示剩余时间），无效时返回 0
        val remaining = mediaPlayer.remainingTime?.value?.longValue ?: return
        if (remaining < 0) {
            val durMs = posMs - remaining  // 减去负数 = 加上绝对值
            _state.value = _state.value.copy(duration = (durMs / 1000).toInt())
        }
    }

    private fun savePositionFromCurrent() {
        val displayPos = _state.value.currentPosition * 1000L
        val timeVal = mediaPlayer.time.value
        val posMs = timeVal?.longValue
        if (posMs == null) {
            return
        }
        when {
            displayPos == 0L && posMs > 0 -> lastSavedPositionMs = posMs
            posMs >= displayPos && posMs <= displayPos + 1000 -> lastSavedPositionMs = posMs
            posMs > 0 -> lastSavedPositionMs = displayPos
        }
    }

    private fun restoreFromSavedState() {
        val mpd = lastMpd
        if (mpd == null) {
            return
        }
        hasReachedPlaying = false
        loadInternal(mpd, restorePosition = true)
    }

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        lastMpd = mpd
        initialized = true
        hasReachedPlaying = false
        loadInternal(
            mpd = mpd,
            restorePosition = false,
            startPosition = startPosition
        )
    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        onError(error)
    }

    private fun loadInternal(
        mpd: BoloDashMpd,
        restorePosition: Boolean,
        startPosition: Int = 0
    ) {
        val nsUrl = writeMpdFile(mpd)
        if (nsUrl == null) {
            onError(BoloPlayerError.UnknownError("DASH MPD 文件写入失败"))
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
        media.addOptions(options)
        mediaPlayer.media = media
        applyPlaybackSpeed()

        // 延迟 seek：此时 media.length 尚未就绪（为 0），必须在 Playing 事件中执行
        pendingSeekMs = 0L
        pendingPauseAfterSeek = false
        val startPositionMs = startPosition.takeIf { it > 0 }?.let { it * 1000L } ?: 0L
        val seekPositionMs = when {
            startPositionMs > 0 -> startPositionMs
            restorePosition && lastSavedPositionMs > 0 -> lastSavedPositionMs
            else -> 0L
        }
        if (seekPositionMs > 0) {
            pendingSeekMs = seekPositionMs
            _state.value = _state.value.copy(currentPosition = (seekPositionMs / 1000).toInt())
        }

        val shouldPlay = autoPlay || (restorePosition && wasPlayingBeforeBackground)
        val shouldPauseAfterSeek = !shouldPlay && pendingSeekMs > 0

        if (shouldPlay || shouldPauseAfterSeek) {
            pendingPauseAfterSeek = shouldPauseAfterSeek
            play()
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
        val durationMs = mediaPlayer.media?.length?.value?.longValue ?: (_state.value.duration * 1000L)
        if (durationMs > 0) {
            mediaPlayer.position = (position * 1000f) / durationMs.toFloat()
            _state.value = _state.value.copy(currentPosition = position)
        }
    }

    actual fun setVolumeGain(gain: Int) {
        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayer.audio?.volume = vlcVolume
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        playbackSpeed = speed
        applyPlaybackSpeed()
    }

    actual fun release() {
        wasPlayingBeforeBackground = _state.value.isPlaying
        savePositionFromCurrent()
        mediaPlayer.stop()
        mediaPlayer.media = null
        initialized = false
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

    /** 注销所有 NSNotification 监听器，应在 Composable dispose 时调用 */
    fun removeObservers() {
        lastMpdFilePath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        lastMpdFilePath = null
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
    }

    private fun intToFourCC(codec: Int): String {
        return charArrayOf(
            (codec and 0xFF).toChar(),
            ((codec shr 8) and 0xFF).toChar(),
            ((codec shr 16) and 0xFF).toChar(),
            ((codec shr 24) and 0xFF).toChar()
        ).concatToString().uppercase()
    }

    private fun getDurationForCompletion(): Int {
        val stateDuration = _state.value.duration
        if (stateDuration > 0) return stateDuration
        return ((mediaPlayer.media?.length?.value?.longValue ?: 0L) / 1000).toInt().coerceAtLeast(0)
    }

    private fun isSeekPositionValid(position: Int): Boolean {
        val duration = _state.value.duration
        return position >= 0 && (duration <= 0 || position <= duration)
    }

    private fun applyPlaybackSpeed() {
        try {
            mediaPlayer.rate = playbackSpeed.rateNumber
            playbackSpeed = BoloPlayerSpeed.fromRateNumber(mediaPlayer.rate)
        } catch (_: Exception) {}
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }
}
