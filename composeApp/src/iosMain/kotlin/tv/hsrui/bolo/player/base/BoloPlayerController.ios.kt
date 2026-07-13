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
    private enum class MediaLifecycle {
        Empty,
        Loading,
        Loaded,
        Released,
        Disposed
    }

    private class PlaybackSpeedApplyGate {
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

        fun onMediaChanged() {
            mediaGeneration += 1
            phase = Phase.Loading
        }

        fun onOpening() {
            phase = Phase.Loading
        }

        fun onBuffering() {
            phase = Phase.Buffering
        }

        fun onPlaying(): Boolean {
            phase = Phase.Playing
            return consumeIfNeeded()
        }

        fun onPaused() {
            phase = Phase.Paused
        }

        fun onInactive() {
            phase = Phase.Inactive
        }

        fun onSpeedRequested(): Boolean {
            requestRevision += 1
            return when (phase) {
                Phase.Playing, Phase.Paused -> consumeIfNeeded()
                Phase.Inactive, Phase.Loading, Phase.Buffering -> false
            }
        }

        private fun consumeIfNeeded(): Boolean {
            if (
                appliedGeneration == mediaGeneration &&
                appliedRevision == requestRevision
            ) {
                return false
            }
            appliedGeneration = mediaGeneration
            appliedRevision = requestRevision
            return true
        }
    }

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    internal val mediaPlayer = VLCMediaPlayer()
    private var wasPlayingBeforeBackground = false
    private var isInForeground = true
    private var mediaLifecycle = MediaLifecycle.Empty
    private var mediaGeneration = 0L
    private val speedApplyGate = PlaybackSpeedApplyGate()

    // 应用生命周期通知常驻到 dispose；播放器通知按 media generation 重建。
    private val applicationObservers = mutableListOf<Any>()
    private val playerObservers = mutableListOf<Any>()

    // ── 位置恢复 ──
    private var lastSavedPositionMs = 0L
    private var hasReceivedTimeForCurrentMedia = false

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

        // 后台 → 保存 + 释放
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            isInForeground = false
            release()
        }
        // 前台 → 重建
        applicationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification, null,
            NSOperationQueue.mainQueue
        ) { _ ->
            isInForeground = true
            restoreFromSavedState()
        }
    }

    /**
     * Composable 重建时调用 —— 绑定 drawable，若 VLC 已释放则重新加载媒体。
     * 类似 Android 的 bindVideo()。
     */
    fun bindDrawable(view: UIView) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        mediaPlayer.drawable = view
        if (isInForeground) {
            restoreFromSavedState()
        }
    }

    private fun onStateChanged(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        when (player.state) {
            VLCMediaPlayerState.VLCMediaPlayerStateOpening -> {
                mediaLifecycle = MediaLifecycle.Loading
                speedApplyGate.onOpening()
                _state.value = _state.value.copy(isBuffering = true)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateBuffering -> {
                speedApplyGate.onBuffering()
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
                
                val tracks = player.media?.tracksInformation as? List<Map<Any?, Any?>>
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
                if (speedApplyGate.onPlaying()) {
                    applyPlaybackSpeed(player, generation)
                }
                if (pendingSeekMs > 0) {
                    val ms = pendingSeekMs
                    pendingSeekMs = 0L
                    val mediaLength = player.media?.length?.value?.longValue ?: 0L
                    if (mediaLength > 0 && ms > 1000 && ms < mediaLength) {
                        val ratio = ms.toFloat() / mediaLength.toFloat()
                        player.position = ratio
                        _state.value = _state.value.copy(currentPosition = (ms / 1000).toInt())
                    }
                }
                if (pendingPauseAfterSeek) {
                    pendingPauseAfterSeek = false
                    player.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
            VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onPaused()
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateStopped -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            }
            VLCMediaPlayerState.VLCMediaPlayerStateEnded -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                val duration = getDurationForCompletion(player)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    currentPosition = duration
                )
                lastSavedPositionMs = duration * 1000L
            }
            VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                mediaLifecycle = MediaLifecycle.Loaded
                speedApplyGate.onInactive()
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                onError(BoloPlayerError.UnknownError("VLC 播放错误"))
            }
            else -> Unit
        }
    }

    private fun updateTimeAndDuration(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        val posMs = player.time.value?.longValue ?: return
        hasReceivedTimeForCurrentMedia = true
        
        _state.value = _state.value.copy(
            currentPosition = (posMs / 1000).toInt(),
            transferSpeed = 0L
        )
        // VLCKit 的 remainingTime 通常为负值（表示剩余时间），无效时返回 0
        val remaining = player.remainingTime?.value?.longValue ?: return
        if (remaining < 0) {
            val durMs = posMs - remaining  // 减去负数 = 加上绝对值
            _state.value = _state.value.copy(duration = (durMs / 1000).toInt())
        }
    }

    private fun savePositionFromCurrent() {
        if (pendingSeekMs > 0L) {
            lastSavedPositionMs = pendingSeekMs
            return
        }
        val displayPos = _state.value.currentPosition * 1000L
        if (!hasReceivedTimeForCurrentMedia) {
            if (displayPos > 0L) {
                lastSavedPositionMs = displayPos
            }
            return
        }
        val posMs = mediaPlayer.time.value?.longValue
        if (posMs == null) {
            if (displayPos > 0L) {
                lastSavedPositionMs = displayPos
            }
            return
        }
        lastSavedPositionMs = when {
            displayPos == 0L && posMs > 0 -> posMs
            posMs >= displayPos && posMs <= displayPos + 1000 -> posMs
            posMs > 0 && displayPos > 0L -> displayPos
            posMs > 0 -> posMs
            displayPos > 0L -> displayPos
            else -> lastSavedPositionMs
        }
    }

    private fun restoreFromSavedState() {
        if (!isInForeground) return
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

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        val normalizedStartPosition = startPosition.coerceAtLeast(0)
        lastMpd = mpd
        lastSavedPositionMs = normalizedStartPosition.toLong() * 1000L
        wasPlayingBeforeBackground = false
        hasReceivedTimeForCurrentMedia = false
        _state.value = _state.value.copy(currentPosition = normalizedStartPosition)
        if (!isInForeground) {
            mediaLifecycle = MediaLifecycle.Released
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                currentPosition = normalizedStartPosition
            )
            return
        }
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
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        mediaGeneration += 1
        val generation = mediaGeneration
        mediaLifecycle = MediaLifecycle.Loading
        hasReceivedTimeForCurrentMedia = false
        removePlayerObservers()
        speedApplyGate.onInactive()
        val player = mediaPlayer
        player.stop()
        player.media = null
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)

        val nsUrl = writeMpdFile(mpd)
        if (nsUrl == null) {
            mediaLifecycle = MediaLifecycle.Released
            _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            onError(BoloPlayerError.UnknownError("DASH MPD 文件写入失败"))
            return
        }

        installPlayerObservers(player, generation)
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
        speedApplyGate.onMediaChanged()
        player.media = media
        mediaLifecycle = MediaLifecycle.Loaded

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
            mediaLifecycle = MediaLifecycle.Loading
            player.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    actual fun play() {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        mediaLifecycle = MediaLifecycle.Loading
        mediaPlayer.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    actual fun pause() {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
        mediaPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    actual fun seekTo(position: Int) {
        if (
            mediaLifecycle == MediaLifecycle.Empty ||
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }
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
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        val vlcVolume = gain.coerceIn(0, 200)
        mediaPlayer.audio?.volume = vlcVolume
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        playbackSpeed = speed
        if (speedApplyGate.onSpeedRequested()) {
            applyPlaybackSpeed(mediaPlayer, mediaGeneration)
        } else {
            // 延迟到下一次 Playing 提交 native rate；UI 仍沿用现有的选中值展示语义。
            _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
        }
    }

    actual fun release() {
        if (
            mediaLifecycle == MediaLifecycle.Released ||
            mediaLifecycle == MediaLifecycle.Disposed
        ) {
            return
        }

        wasPlayingBeforeBackground = _state.value.isPlaying && !pendingPauseAfterSeek
        savePositionFromCurrent()
        mediaLifecycle = MediaLifecycle.Released
        mediaGeneration += 1
        hasReceivedTimeForCurrentMedia = false
        removePlayerObservers()
        speedApplyGate.onInactive()
        mediaPlayer.stop()
        mediaPlayer.media = null
        pendingSeekMs = 0L
        pendingPauseAfterSeek = false
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
    }

    actual fun dispose() {
        if (mediaLifecycle == MediaLifecycle.Disposed) return

        release()
        mediaLifecycle = MediaLifecycle.Disposed
        mediaGeneration += 1
        mediaPlayer.drawable = null
        removePlayerObservers()
        removeApplicationObservers()
        removeMpdFile()
        lastMpd = null
        pendingSeekMs = 0L
        pendingPauseAfterSeek = false
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
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerStateChanged, player, NSOperationQueue.mainQueue
        ) { _ ->
            if (isCurrentPlayerGeneration(player, generation)) {
                onStateChanged(player, generation)
            }
        }
        playerObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            VLCMediaPlayerTimeChanged, player, NSOperationQueue.mainQueue
        ) { _ ->
            if (isCurrentPlayerGeneration(player, generation)) {
                updateTimeAndDuration(player, generation)
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

    private fun getDurationForCompletion(player: VLCMediaPlayer): Int {
        val stateDuration = _state.value.duration
        if (stateDuration > 0) return stateDuration
        return ((player.media?.length?.value?.longValue ?: 0L) / 1000).toInt().coerceAtLeast(0)
    }

    private fun isSeekPositionValid(position: Int): Boolean {
        val duration = _state.value.duration
        return position >= 0 && (duration <= 0 || position <= duration)
    }

    private fun applyPlaybackSpeed(player: VLCMediaPlayer, generation: Long) {
        if (!isCurrentPlayerGeneration(player, generation)) return

        try {
            player.rate = playbackSpeed.rateNumber
            playbackSpeed = BoloPlayerSpeed.fromRateNumber(player.rate)
        } catch (_: Exception) {}
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }
}
