package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.BorderLayout
import java.io.File
import java.net.URI
import javax.swing.JPanel
import javax.swing.SwingUtilities

actual class BoloPlayerController actual constructor(
    private val autoPlay: Boolean,
    private val onError: (BoloPlayerError) -> Unit
) {
    private enum class LifecycleState {
        Released,
        Active,
        Disposed
    }

    /** JVM actual 内部的倍速调用门控，不改变 common API 或 requested/applied 展示语义。 */
    private class SpeedApplyState {
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
        private var phaseBeforeBuffering = Phase.Inactive
        private var mediaGeneration = 0L
        private var requestRevision = 0L
        private var appliedGeneration = -1L
        private var appliedRevision = -1L
        private var scheduledGeneration = -1L
        private var scheduledRevision = -1L

        fun onMediaChanged() {
            mediaGeneration += 1
            phase = Phase.Loading
            phaseBeforeBuffering = Phase.Loading
            clearScheduled()
        }

        fun onOpening() {
            phase = Phase.Loading
            phaseBeforeBuffering = Phase.Loading
            clearScheduled()
        }

        fun onBuffering() {
            if (phase != Phase.Buffering) {
                phaseBeforeBuffering = phase
            }
            phase = Phase.Buffering
            clearScheduled()
        }

        fun onBufferingCompleted() {
            if (phase == Phase.Buffering) {
                phase = phaseBeforeBuffering
            }
        }

        fun onPlaying(): ApplyTicket? {
            phase = Phase.Playing
            return scheduleIfNeeded()
        }

        fun onPaused() {
            phase = Phase.Paused
        }

        fun onInactive() {
            phase = Phase.Inactive
            phaseBeforeBuffering = Phase.Inactive
            clearScheduled()
        }

        fun onSpeedRequested(): ApplyTicket? {
            requestRevision += 1
            return when (phase) {
                Phase.Playing, Phase.Paused -> scheduleIfNeeded()
                Phase.Inactive, Phase.Loading, Phase.Buffering -> null
            }
        }

        fun canApply(ticket: ApplyTicket): Boolean =
            isCurrent(ticket) &&
                scheduledGeneration == ticket.mediaGeneration &&
                scheduledRevision == ticket.requestRevision &&
                (phase == Phase.Playing || phase == Phase.Paused)

        fun isCurrent(ticket: ApplyTicket): Boolean =
            mediaGeneration == ticket.mediaGeneration &&
                requestRevision == ticket.requestRevision

        fun onAttempted(ticket: ApplyTicket) {
            if (!isCurrent(ticket)) return
            appliedGeneration = ticket.mediaGeneration
            appliedRevision = ticket.requestRevision
            clearScheduledIf(ticket)
        }

        fun onDeferred(ticket: ApplyTicket) {
            clearScheduledIf(ticket)
        }

        private fun scheduleIfNeeded(): ApplyTicket? {
            if (appliedGeneration == mediaGeneration && appliedRevision == requestRevision) {
                return null
            }
            if (scheduledGeneration == mediaGeneration && scheduledRevision == requestRevision) {
                return null
            }
            scheduledGeneration = mediaGeneration
            scheduledRevision = requestRevision
            return ApplyTicket(mediaGeneration, requestRevision)
        }

        private fun clearScheduledIf(ticket: ApplyTicket) {
            if (
                scheduledGeneration == ticket.mediaGeneration &&
                scheduledRevision == ticket.requestRevision
            ) {
                clearScheduled()
            }
        }

        private fun clearScheduled() {
            scheduledGeneration = -1L
            scheduledRevision = -1L
        }
    }

    private data class PlayerHandle(
        val component: CallbackMediaPlayerComponent,
        val player: MediaPlayer,
        val playerGeneration: Long
    )

    private data class InitializedPlayer(
        val handle: PlayerHandle,
        val wasCreated: Boolean
    )

    private data class PendingLoad(
        val mpd: BoloDashMpd,
        val loadGeneration: Long
    )

    private data class MediaToken(
        val playerGeneration: Long,
        val loadGeneration: Long,
        val mrl: String
    )

    private data class StartupToken(
        val media: MediaToken,
        val revision: Long
    )

    private data class PollToken(
        val media: MediaToken,
        val revision: Long
    )

    private data class TrackSnapshot(
        val videoCodec: String,
        val videoWidth: Int,
        val videoHeight: Int,
        val videoBitrate: Long,
        val audioCodec: String,
        val audioBitrate: Long
    )

    private val _state = MutableStateFlow(BoloPlayerState())
    actual val state: StateFlow<BoloPlayerState> = _state.asStateFlow()

    private val lock = Any()
    private val controllerJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + controllerJob)
    private val speedApplyState = SpeedApplyState()

    private var lifecycleState = LifecycleState.Released
    private var playerGeneration = 0L
    private var loadGeneration = 0L
    private var activeMediaMrl: String? = null
    private var mediaLoading = false
    private var mediaReady = false

    private var startupRevision = 0L
    private var startupPendingLoadGeneration = -1L
    private var startupAppliedLoadGeneration = -1L
    private var playIntentRevision = 0L
    private var seekRevision = 0L
    private var pollingRevision = 0L

    internal var mediaPlayerComponent: CallbackMediaPlayerComponent? = null
        private set
    private var boundHost: JPanel? = null
    private var positionPollingJob: Job? = null
    private var pollTaskInFlight: PollToken? = null

    private var hasAcceptedInitialLoad = false
    private var lastSavedPositionMs = 0L
    private var playWhenReady = autoPlay
    private var pendingSeekMs = 0L

    private var lastMpd: BoloDashMpd? = null
    private var lastMpdFile: File? = null
    private var pendingLoad: PendingLoad? = null
    private var playbackSpeed = BoloPlayerSpeed.default

    internal fun bindVideo(host: JPanel) {
        val canBind = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) {
                false
            } else {
                boundHost = host
                true
            }
        }
        if (!canBind) return

        val initialized = ensureInitialized() ?: return
        attachToHost(host, initialized.handle.component)

        synchronized(lock) {
            if (
                initialized.wasCreated &&
                pendingLoad == null &&
                lastMpd != null &&
                isCurrentLocked(initialized.handle)
            ) {
                scheduleLoadLocked(
                    mpd = lastMpd!!,
                    startPositionMs = lastSavedPositionMs
                )
            }
        }
        consumePendingLoad(initialized.handle)
    }

    internal fun unbindVideo(host: JPanel) {
        val component = synchronized(lock) {
            if (boundHost !== host) return
            boundHost = null
            mediaPlayerComponent
        }
        detachFromHost(host, component)
    }

    private fun ensureInitialized(): InitializedPlayer? {
        try {
            return synchronized(lock) {
                if (lifecycleState == LifecycleState.Disposed) return@synchronized null

                mediaPlayerComponent?.let { component ->
                    return@synchronized InitializedPlayer(
                        handle = PlayerHandle(component, component.mediaPlayer(), playerGeneration),
                        wasCreated = false
                    )
                }

                NativeDiscovery().discover()
                val component = CallbackMediaPlayerComponent()
                val player = component.mediaPlayer()
                val generation = ++playerGeneration
                val handle = PlayerHandle(component, player, generation)
                player.events().addMediaPlayerEventListener(createEventListener(handle))
                mediaPlayerComponent = component
                lifecycleState = LifecycleState.Active
                mediaLoading = false
                mediaReady = false
                InitializedPlayer(handle = handle, wasCreated = true)
            }
        } catch (error: LinkageError) {
            reportInitializationError(error)
            return null
        } catch (error: Exception) {
            reportInitializationError(error)
            return null
        }
    }

    private fun reportInitializationError(error: Throwable) {
        onError(
            BoloPlayerError.UnknownError(
                "VLC 初始化失败，请确认已安装 VLC Player: ${error.message}",
                error
            )
        )
    }

    private fun createEventListener(handle: PlayerHandle): MediaPlayerEventAdapter =
        object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    if (startupAppliedLoadGeneration == token.loadGeneration) {
                        return@submitVerifiedEvent
                    }
                    mediaLoading = true
                    speedApplyState.onOpening()
                    updateStateIfCurrentLocked(token) { it.copy(isBuffering = true) }
                }
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                val startup = synchronized(lock) {
                    val token = currentMediaTokenLocked(handle, mediaPlayer) ?: return
                    if (
                        startupAppliedLoadGeneration != token.loadGeneration &&
                        startupPendingLoadGeneration == token.loadGeneration
                    ) {
                        return
                    }
                    if (startupAppliedLoadGeneration != token.loadGeneration) {
                        startupPendingLoadGeneration = token.loadGeneration
                    }
                    StartupToken(token, startupRevision)
                }
                submitPlayingTransition(handle, startup)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    if (startupAppliedLoadGeneration != token.loadGeneration) return@submitVerifiedEvent
                    mediaLoading = false
                    mediaReady = true
                    speedApplyState.onPaused()
                    updateStateIfCurrentLocked(token) {
                        it.copy(isPlaying = false, isBuffering = false)
                    }
                }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    // 切换媒体时旧媒体通常会迟到一个 stopped；Loading 由新媒体事件负责收口。
                    if (mediaLoading || startupAppliedLoadGeneration != token.loadGeneration) {
                        return@submitVerifiedEvent
                    }
                    mediaReady = false
                    speedApplyState.onInactive()
                    stopPositionPollingLocked()
                    updateStateIfCurrentLocked(token) {
                        it.copy(isPlaying = false, isBuffering = false)
                    }
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    if (mediaLoading || startupAppliedLoadGeneration != token.loadGeneration) {
                        return@submitVerifiedEvent
                    }
                    val current = _state.value
                    val duration = current.duration.takeIf { it > 0 }
                        ?: current.currentPosition.coerceAtLeast(0)
                    mediaReady = false
                    speedApplyState.onInactive()
                    lastSavedPositionMs = duration * 1000L
                    stopPositionPollingLocked()
                    updateStateIfCurrentLocked(token) {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            currentPosition = duration
                        )
                    }
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    if (newCache < 100f) {
                        speedApplyState.onBuffering()
                    } else {
                        speedApplyState.onBufferingCompleted()
                    }
                    updateStateIfCurrentLocked(token) {
                        it.copy(isBuffering = newCache < 100f)
                    }
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    mediaLoading = false
                    mediaReady = false
                    speedApplyState.onInactive()
                    stopPositionPollingLocked()
                    updateStateIfCurrentLocked(token) {
                        it.copy(isPlaying = false, isBuffering = false)
                    }
                    scope.launch {
                        onError(BoloPlayerError.UnknownError("VLC 播放错误"))
                    }
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                val token = synchronized(lock) {
                    currentMediaTokenLocked(handle, mediaPlayer)
                } ?: return
                submitVerifiedEvent(handle, token) {
                    updateStateIfCurrentLocked(token) {
                        it.copy(duration = (newLength / 1000L).toInt().coerceAtLeast(0))
                    }
                }
            }
        }

    internal actual fun load(mpd: BoloDashMpd, startPosition: Int) {
        val shouldStart = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            lastMpd = mpd
            if (!hasAcceptedInitialLoad) {
                playWhenReady = autoPlay
                hasAcceptedInitialLoad = true
            }
            val normalizedStartMs = startPosition.coerceAtLeast(0) * 1000L
            // 显式新媒体必须从自己的起点建立恢复基线，不能继承上一媒体的位置。
            lastSavedPositionMs = normalizedStartMs
            scheduleLoadLocked(
                mpd = mpd,
                startPositionMs = normalizedStartMs
            )
            boundHost != null
        }
        if (!shouldStart) return

        val initialized = ensureInitialized() ?: return
        synchronized(lock) { boundHost }?.let { attachToHost(it, initialized.handle.component) }
        consumePendingLoad(initialized.handle)
    }

    internal actual fun reportLoadError(error: BoloPlayerError) {
        onError(error)
    }

    private fun scheduleLoadLocked(mpd: BoloDashMpd, startPositionMs: Long) {
        val generation = ++loadGeneration
        activeMediaMrl = null
        mediaLoading = true
        mediaReady = false
        startupRevision += 1
        startupPendingLoadGeneration = -1L
        startupAppliedLoadGeneration = -1L
        pendingSeekMs = startPositionMs.coerceAtLeast(0L)
        seekRevision += 1
        speedApplyState.onMediaChanged()
        stopPositionPollingLocked()
        pendingLoad = PendingLoad(mpd, generation)
        _state.update {
            it.copy(
                isPlaying = false,
                isBuffering = true,
                currentPosition = (pendingSeekMs / 1000L).toInt(),
                duration = 0,
                videoCodec = "",
                videoWidth = 0,
                videoHeight = 0,
                videoBitrate = 0L,
                audioCodec = "",
                audioBitrate = 0L,
                transferSpeed = 0L
            )
        }
    }

    private fun consumePendingLoad(handle: PlayerHandle) {
        val request = synchronized(lock) {
            if (!isCurrentLocked(handle)) return
            val current = pendingLoad ?: return
            pendingLoad = null
            current
        }
        loadInternal(handle, request)
    }

    private fun loadInternal(handle: PlayerHandle, request: PendingLoad) {
        if (!isCurrentLoadRequest(handle, request.loadGeneration)) return

        val mpdFile = try {
            writeMpdFile(request.mpd)
        } catch (error: Exception) {
            failLoadRequest(
                request.loadGeneration,
                BoloPlayerError.UnknownError("DASH MPD 文件写入失败: ${error.message}", error)
            )
            return
        }

        val setup = synchronized(lock) {
            if (!isCurrentLoadRequestLocked(handle, request.loadGeneration)) return@synchronized null
            val mrl = mpdFile.toURI().toString()
            val oldFile = lastMpdFile
            lastMpdFile = mpdFile
            activeMediaMrl = mrl
            Pair(MediaToken(handle.playerGeneration, request.loadGeneration, mrl), oldFile)
        }
        if (setup == null) {
            runCatching { mpdFile.delete() }
            return
        }
        runCatching { setup.second?.delete() }

        val options = mutableListOf<String>()
        videoPlayHeaders.forEach { (key, value) ->
            when (key.lowercase()) {
                "referer" -> options.add(":http-referrer=$value")
                "user-agent" -> options.add(":http-user-agent=$value")
                else -> options.add(":http-header-fields=$key: $value")
            }
        }

        val token = setup.first
        val submitted = submitPlayerTask(handle, token) { player, isValid ->
            if (!isValid()) return@submitPlayerTask
            val result = runCatching {
                player.media().play(token.mrl, *options.toTypedArray())
            }
            if (!isValid()) return@submitPlayerTask
            when {
                result.isFailure -> failActiveLoad(
                    token,
                    result.exceptionOrNull() ?: IllegalStateException("VLC media.play failed")
                )
                result.getOrDefault(false).not() -> failActiveLoad(
                    token,
                    IllegalStateException("VLC media.play 返回 false")
                )
            }
        }
        if (!submitted && isCurrentMedia(handle, token)) {
            failActiveLoad(token, IllegalStateException("VLC 播放任务提交失败"))
        }
    }

    private fun failLoadRequest(load: Long, error: BoloPlayerError) {
        val shouldReport = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed || loadGeneration != load) return@synchronized false
            mediaLoading = false
            mediaReady = false
            speedApplyState.onInactive()
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            true
        }
        if (shouldReport) onError(error)
    }

    private fun failActiveLoad(token: MediaToken, error: Throwable) {
        val outcome = synchronized(lock) {
            val handle = currentHandleLocked() ?: return@synchronized null
            if (!isCurrentMediaLocked(handle, token)) return@synchronized null
            mediaLoading = false
            mediaReady = false
            activeMediaMrl = null
            speedApplyState.onInactive()
            stopPositionPollingLocked()
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            Pair(true, lastMpdFile.also { lastMpdFile = null })
        } ?: return
        runCatching { outcome.second?.delete() }
        // 不在 vlcj 的单线程 submit executor 内调用外部回调，避免回调中 release 自等待。
        scope.launch { reportPlaybackError(error) }
    }

    private fun reportPlaybackError(error: Throwable) {
        val message = error.message.orEmpty()
        when {
            message.contains("404", ignoreCase = true) || message.contains("403", ignoreCase = true) ->
                onError(BoloPlayerError.NetworkError("HTTP 错误: $message", error))
            message.contains("timeout", ignoreCase = true) || message.contains("connection", ignoreCase = true) ->
                onError(BoloPlayerError.NetworkError("网络错误: $message", error))
            message.contains("codec", ignoreCase = true) || message.contains("format", ignoreCase = true) ->
                onError(BoloPlayerError.FormatNotSupported("格式不支持: $message"))
            else -> onError(BoloPlayerError.UnknownError("VLC 播放错误: $message", error))
        }
    }

    /**
     * MediaPlayerEvent 只给出 player，不能直接证明事件属于哪次 load。回调线程只捕获
     * 当前 token；实际处理串到 player.submit 后，再用当前 native MRL 和 token 双重确认。
     */
    private fun submitVerifiedEvent(
        handle: PlayerHandle,
        media: MediaToken,
        event: () -> Unit
    ) {
        submitPlayerTask(handle, media) { player, isValid ->
            if (!isValid()) return@submitPlayerTask
            val nativeMrl = runCatching { player.media().info().mrl() }.getOrNull()
                ?: return@submitPlayerTask
            if (!isValid() || !mediaMrlMatches(media.mrl, nativeMrl)) return@submitPlayerTask
            synchronized(lock) {
                if (isCurrentMediaLocked(handle, media)) {
                    event()
                }
            }
        }
    }

    private fun submitPlayingTransition(handle: PlayerHandle, startup: StartupToken) {
        val submitted = submitPlayerTask(handle, startup.media) { player, isValid ->
            try {
                if (!isValid() || !isCurrentStartup(startup)) return@submitPlayerTask

                val nativeState = runCatching {
                    player.media().info().mrl() to player.status().isPlaying
                }.getOrNull() ?: return@submitPlayerTask
                if (!isValid() || !isCurrentStartup(startup)) return@submitPlayerTask
                if (!mediaMrlMatches(startup.media.mrl, nativeState.first)) {
                    return@submitPlayerTask
                }
                if (!nativeState.second) return@submitPlayerTask

                val startupPlan = synchronized(lock) {
                    if (!isCurrentStartupLocked(startup)) return@synchronized null
                    mediaLoading = false
                    mediaReady = true
                    val isFirstPlaying = startupAppliedLoadGeneration != startup.media.loadGeneration
                    val seek = if (isFirstPlaying) pendingSeekMs else 0L
                    val seekRevisionAtStart = seekRevision
                    val speedTicket = speedApplyState.onPlaying()
                    updateStateIfCurrentLocked(startup.media) {
                        it.copy(isPlaying = true, isBuffering = false)
                    }
                    Triple(isFirstPlaying, seek to seekRevisionAtStart, speedTicket)
                } ?: return@submitPlayerTask

                if (startupPlan.first && isValid() && isCurrentStartup(startup)) {
                    val tracks = readTrackInfo(player)
                    synchronized(lock) {
                        if (isCurrentStartupLocked(startup)) {
                            updateStateIfCurrentLocked(startup.media) {
                                it.copy(
                                    videoCodec = tracks.videoCodec,
                                    videoWidth = tracks.videoWidth,
                                    videoHeight = tracks.videoHeight,
                                    videoBitrate = tracks.videoBitrate,
                                    audioCodec = tracks.audioCodec,
                                    audioBitrate = tracks.audioBitrate
                                )
                            }
                        }
                    }
                }

                val seekMs = startupPlan.second.first
                val startupSeekRevision = startupPlan.second.second
                if (seekMs > 0L && shouldApplySeek(startup, startupSeekRevision, seekMs)) {
                    runCatching { player.controls().setTime(seekMs) }
                    synchronized(lock) {
                        if (
                            isCurrentStartupLocked(startup) &&
                            seekRevision == startupSeekRevision &&
                            pendingSeekMs == seekMs
                        ) {
                            pendingSeekMs = 0L
                        }
                    }
                }

                startupPlan.third?.let { ticket ->
                    applyPlaybackSpeedInPlayerQueue(handle, startup.media, ticket, player)
                }

                synchronized(lock) {
                    if (isCurrentStartupLocked(startup) && startupPlan.first) {
                        startupAppliedLoadGeneration = startup.media.loadGeneration
                        startupPendingLoadGeneration = -1L
                    }
                }

                val pauseIntent = synchronized(lock) {
                    if (!isCurrentStartupLocked(startup)) return@synchronized null
                    playIntentRevision to !playWhenReady
                }
                if (pauseIntent?.second == true && shouldApplyPause(startup, pauseIntent.first)) {
                    runCatching { player.controls().setPause(true) }
                    synchronized(lock) {
                        if (isCurrentStartupLocked(startup) && !playWhenReady) {
                            updateStateIfCurrentLocked(startup.media) { it.copy(isPlaying = false) }
                        }
                    }
                }

                if (isValid() && isCurrentStartup(startup)) {
                    startPositionPolling(handle, startup.media)
                }
            } finally {
                synchronized(lock) {
                    if (
                        isCurrentMediaLocked(handle, startup.media) &&
                        startupPendingLoadGeneration == startup.media.loadGeneration
                    ) {
                        startupPendingLoadGeneration = -1L
                    }
                }
            }
        }
        if (!submitted) {
            synchronized(lock) {
                if (
                    isCurrentMediaLocked(handle, startup.media) &&
                    startupPendingLoadGeneration == startup.media.loadGeneration
                ) {
                    startupPendingLoadGeneration = -1L
                }
            }
        }
    }

    private fun readTrackInfo(player: MediaPlayer): TrackSnapshot {
        var videoCodec = ""
        var audioCodec = ""
        var videoWidth = 0
        var videoHeight = 0
        var videoBitrate = 0L
        var audioBitrate = 0L

        runCatching {
            player.media().info().videoTracks().forEach { track ->
                videoCodec = track.codecName().orEmpty().uppercase()
                videoWidth = track.width()
                videoHeight = track.height()
                videoBitrate = track.bitRate().toLong().takeIf { it > 0L } ?: videoBitrate
            }
            player.media().info().audioTracks().forEach { track ->
                audioCodec = track.codecName().orEmpty().uppercase()
                audioBitrate = track.bitRate().toLong().takeIf { it > 0L } ?: audioBitrate
            }
        }
        return TrackSnapshot(
            videoCodec,
            videoWidth,
            videoHeight,
            videoBitrate,
            audioCodec,
            audioBitrate
        )
    }

    actual fun play() {
        val command = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            playWhenReady = true
            val revision = ++playIntentRevision
            val handle = currentHandleLocked()
            val token = handle?.let { currentMediaTokenLocked(it) }
            if (mediaLoading) null else Triple(handle, token, revision)
        } ?: return
        val handle = command.first ?: return
        val token = command.second ?: return
        submitPlayerTask(handle, token) { player, isValid ->
            val shouldPlay = synchronized(lock) {
                isValid() && playIntentRevision == command.third && playWhenReady
            }
            if (!shouldPlay) return@submitPlayerTask
            runCatching { player.controls().play() }
            if (!isValid()) return@submitPlayerTask
        }
    }

    actual fun pause() {
        val command = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            playWhenReady = false
            val revision = ++playIntentRevision
            _state.update { it.copy(isPlaying = false) }
            val handle = currentHandleLocked()
            val token = handle?.let { currentMediaTokenLocked(it) }
            if (!mediaReady) null else Triple(handle, token, revision)
        } ?: return
        val handle = command.first ?: return
        val token = command.second ?: return
        submitPlayerTask(handle, token) { player, isValid ->
            val shouldPause = synchronized(lock) {
                isValid() && playIntentRevision == command.third && !playWhenReady
            }
            if (!shouldPause) return@submitPlayerTask
            runCatching { player.controls().setPause(true) }
            if (!isValid()) return@submitPlayerTask
        }
    }

    actual fun seekTo(position: Int) {
        if (!isSeekPositionValid(position)) return
        val targetMs = position * 1000L
        val command = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            lastSavedPositionMs = targetMs
            pendingSeekMs = targetMs
            val revision = ++seekRevision
            _state.update { it.copy(currentPosition = position) }
            val handle = currentHandleLocked()
            val token = handle?.let { currentMediaTokenLocked(it) }
            if (!mediaReady) null else Triple(handle, token, revision)
        } ?: return
        val handle = command.first ?: return
        val token = command.second ?: return
        submitPlayerTask(handle, token) { player, isValid ->
            val shouldSeek = synchronized(lock) {
                isValid() && seekRevision == command.third && pendingSeekMs == targetMs
            }
            if (!shouldSeek) return@submitPlayerTask
            runCatching { player.controls().setTime(targetMs) }
            synchronized(lock) {
                if (
                    isCurrentMediaLocked(handle, token) &&
                    seekRevision == command.third &&
                    pendingSeekMs == targetMs
                ) {
                    pendingSeekMs = 0L
                }
            }
        }
    }

    actual fun setVolumeGain(gain: Int) {
        val command = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            val handle = currentHandleLocked() ?: return
            val token = currentMediaTokenLocked(handle) ?: return
            handle to token
        }
        submitPlayerTask(command.first, command.second) { player, isValid ->
            if (!isValid()) return@submitPlayerTask
            runCatching { player.audio().setVolume(gain.coerceIn(0, 200)) }
            if (!isValid()) return@submitPlayerTask
        }
    }

    actual fun setPlaybackSpeed(speed: BoloPlayerSpeed) {
        val command = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            playbackSpeed = speed
            // 保留当前 UI 语义：请求即展示，不新增 requested/applied/failed 状态。
            _state.update { it.copy(playbackSpeed = speed) }
            val ticket = speedApplyState.onSpeedRequested() ?: return@synchronized null
            val handle = currentHandleLocked() ?: return@synchronized null
            val token = currentMediaTokenLocked(handle) ?: return@synchronized null
            Triple(handle, token, ticket)
        } ?: return
        submitPlaybackSpeed(command.first, command.second, command.third)
    }

    actual fun release() {
        releaseResources(finalRelease = false)
    }

    actual fun dispose() {
        releaseResources(finalRelease = true)
    }

    private fun releaseResources(finalRelease: Boolean) {
        val release = synchronized(lock) {
            if (lifecycleState == LifecycleState.Disposed) return
            if (!finalRelease && lifecycleState == LifecycleState.Released && mediaPlayerComponent == null) return

            val component = mediaPlayerComponent
            val host = boundHost
            val displayPositionMs = _state.value.currentPosition.coerceAtLeast(0) * 1000L
            if (displayPositionMs > 0L || lastSavedPositionMs == 0L) {
                lastSavedPositionMs = displayPositionMs
            }

            mediaPlayerComponent = null
            playerGeneration += 1
            loadGeneration += 1
            activeMediaMrl = null
            mediaLoading = false
            mediaReady = false
            startupRevision += 1
            startupPendingLoadGeneration = -1L
            startupAppliedLoadGeneration = -1L
            speedApplyState.onInactive()
            stopPositionPollingLocked()
            pendingSeekMs = 0L
            pendingLoad = null
            lifecycleState = if (finalRelease) LifecycleState.Disposed else LifecycleState.Released
            if (finalRelease) {
                boundHost = null
                lastMpd = null
            }
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            Triple(component, host, lastMpdFile.also { lastMpdFile = null })
        }

        val component = release.first
        val host = release.second
        if (host != null) detachFromHost(host, component)
        // CallbackMediaPlayerComponent.release() 会先关闭并排空其 player.submit 单线程队列。
        try {
            component?.release()
        } catch (_: Exception) {
        }
        runCatching { release.third?.delete() }

        if (finalRelease) controllerJob.cancel()
    }

    private fun writeMpdFile(mpd: BoloDashMpd): File {
        val mpdDir = File(System.getProperty("java.io.tmpdir"), "bolo_dash_mpd").apply { mkdirs() }
        val file = File.createTempFile("bolo_", ".mpd", mpdDir)
        return try {
            file.apply { writeText(mpd.xml, Charsets.UTF_8) }
        } catch (error: Exception) {
            runCatching { file.delete() }
            throw error
        }
    }

    private fun startPositionPolling(handle: PlayerHandle, media: MediaToken) {
        synchronized(lock) {
            if (!isCurrentMediaLocked(handle, media)) return
            stopPositionPollingLocked()
            val poll = PollToken(media, ++pollingRevision)
            positionPollingJob = scope.launch {
                while (isActive) {
                    val shouldSubmit = synchronized(lock) {
                        isCurrentPollLocked(handle, poll) && pollTaskInFlight == null
                    }
                    if (shouldSubmit) {
                        synchronized(lock) {
                            if (isCurrentPollLocked(handle, poll) && pollTaskInFlight == null) {
                                pollTaskInFlight = poll
                            }
                        }
                        val submitted = submitPlayerTask(handle, media) { player, isValid ->
                            try {
                                if (!isValid() || !isCurrentPoll(handle, poll)) return@submitPlayerTask
                                val seekRevisionBeforeSample = synchronized(lock) { seekRevision }
                                val sample = runCatching {
                                    val positionMs = player.status().time()
                                    val statistics = player.media().info().statistics()
                                    val transferSpeed = statistics?.inputBitrate()?.toLong()
                                        ?.let { if (it > 0L) it * 8L else 0L }
                                        ?: 0L
                                    positionMs to transferSpeed
                                }.getOrNull() ?: return@submitPlayerTask
                                synchronized(lock) {
                                    if (
                                        isCurrentPollLocked(handle, poll) &&
                                        seekRevision == seekRevisionBeforeSample
                                    ) {
                                        lastSavedPositionMs = sample.first.coerceAtLeast(0L)
                                        updateStateIfCurrentLocked(media) {
                                            it.copy(
                                                currentPosition = (sample.first / 1000L).toInt().coerceAtLeast(0),
                                                transferSpeed = sample.second
                                            )
                                        }
                                    }
                                }
                            } finally {
                                synchronized(lock) {
                                    if (pollTaskInFlight == poll) pollTaskInFlight = null
                                }
                            }
                        }
                        if (!submitted) {
                            synchronized(lock) {
                                if (pollTaskInFlight == poll) pollTaskInFlight = null
                            }
                        }
                    }
                    delay(500L)
                }
            }
        }
    }

    private fun stopPositionPollingLocked() {
        pollingRevision += 1
        positionPollingJob?.cancel()
        positionPollingJob = null
        pollTaskInFlight = null
    }

    private fun isSeekPositionValid(position: Int): Boolean {
        val duration = _state.value.duration
        return position >= 0 && (duration <= 0 || position <= duration)
    }

    private fun submitPlaybackSpeed(
        handle: PlayerHandle,
        media: MediaToken,
        ticket: SpeedApplyState.ApplyTicket
    ) {
        submitPlayerTask(handle, media) { player, _ ->
            applyPlaybackSpeedInPlayerQueue(handle, media, ticket, player)
        }
    }

    private fun applyPlaybackSpeedInPlayerQueue(
        handle: PlayerHandle,
        media: MediaToken,
        ticket: SpeedApplyState.ApplyTicket,
        player: MediaPlayer
    ) {
        val requestedSpeed = synchronized(lock) {
            if (!isCurrentMediaLocked(handle, media) || !speedApplyState.canApply(ticket)) {
                speedApplyState.onDeferred(ticket)
                return
            }
            playbackSpeed
        }

        val nativeRate = runCatching {
            if (player.controls().setRate(requestedSpeed.rateNumber)) {
                player.status().rate()
            } else {
                null
            }
        }.getOrNull()

        synchronized(lock) {
            if (!isCurrentMediaLocked(handle, media) || !speedApplyState.isCurrent(ticket)) return
            if (nativeRate != null) {
                playbackSpeed = BoloPlayerSpeed.fromRateNumber(nativeRate)
            }
            speedApplyState.onAttempted(ticket)
            // ticket 当前性、readback 与 UI 回写在同一把锁内，避免旧任务覆盖新请求。
            _state.update { it.copy(playbackSpeed = playbackSpeed) }
        }
    }

    private fun shouldApplySeek(startup: StartupToken, revision: Long, targetMs: Long): Boolean =
        synchronized(lock) {
            isCurrentStartupLocked(startup) && seekRevision == revision && pendingSeekMs == targetMs
        }

    private fun shouldApplyPause(startup: StartupToken, revision: Long): Boolean =
        synchronized(lock) {
            isCurrentStartupLocked(startup) && playIntentRevision == revision && !playWhenReady
        }

    private fun submitPlayerTask(
        handle: PlayerHandle,
        media: MediaToken,
        block: (MediaPlayer, () -> Boolean) -> Unit
    ): Boolean {
        if (!isCurrentMedia(handle, media)) return false
        return try {
            handle.player.submit {
                val isValid = { isCurrentMedia(handle, media) }
                if (!isValid()) return@submit
                runCatching { block(handle.player, isValid) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun currentHandleLocked(): PlayerHandle? {
        val component = mediaPlayerComponent ?: return null
        return PlayerHandle(component, component.mediaPlayer(), playerGeneration)
    }

    private fun currentMediaTokenLocked(
        handle: PlayerHandle,
        player: MediaPlayer = handle.player
    ): MediaToken? {
        if (!isCurrentLocked(handle, player)) return null
        val mrl = activeMediaMrl ?: return null
        return MediaToken(handle.playerGeneration, loadGeneration, mrl)
    }

    private fun isCurrentLoadRequest(handle: PlayerHandle, generation: Long): Boolean =
        synchronized(lock) { isCurrentLoadRequestLocked(handle, generation) }

    private fun isCurrentLoadRequestLocked(handle: PlayerHandle, generation: Long): Boolean =
        isCurrentLocked(handle) && loadGeneration == generation

    private fun isCurrentMedia(handle: PlayerHandle, media: MediaToken): Boolean =
        synchronized(lock) { isCurrentMediaLocked(handle, media) }

    private fun isCurrentMediaLocked(handle: PlayerHandle, media: MediaToken): Boolean =
        isCurrentLocked(handle) &&
            handle.playerGeneration == media.playerGeneration &&
            loadGeneration == media.loadGeneration &&
            activeMediaMrl == media.mrl

    private fun isCurrentStartup(startup: StartupToken): Boolean =
        synchronized(lock) { isCurrentStartupLocked(startup) }

    private fun isCurrentStartupLocked(startup: StartupToken): Boolean {
        val handle = currentHandleLocked() ?: return false
        return startupRevision == startup.revision && isCurrentMediaLocked(handle, startup.media)
    }

    private fun isCurrentPoll(handle: PlayerHandle, poll: PollToken): Boolean =
        synchronized(lock) { isCurrentPollLocked(handle, poll) }

    private fun isCurrentPollLocked(handle: PlayerHandle, poll: PollToken): Boolean =
        pollingRevision == poll.revision &&
            positionPollingJob != null &&
            isCurrentMediaLocked(handle, poll.media)

    private fun isCurrentLocked(
        handle: PlayerHandle,
        player: MediaPlayer = handle.player
    ): Boolean =
        lifecycleState == LifecycleState.Active &&
            playerGeneration == handle.playerGeneration &&
            mediaPlayerComponent === handle.component &&
            handle.player === player

    private fun mediaMrlMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        if (expected == actual) return true
        return runCatching {
            val expectedUri = URI(expected).normalize()
            val actualUri = URI(actual).normalize()
            if (
                expectedUri.scheme.equals("file", ignoreCase = true) &&
                actualUri.scheme.equals("file", ignoreCase = true)
            ) {
                File(expectedUri).canonicalFile == File(actualUri).canonicalFile
            } else {
                expectedUri == actualUri
            }
        }.getOrDefault(false)
    }

    private fun updateStateIfCurrentLocked(
        media: MediaToken,
        transform: (BoloPlayerState) -> BoloPlayerState
    ) {
        val handle = currentHandleLocked() ?: return
        if (isCurrentMediaLocked(handle, media)) {
            _state.update(transform)
        }
    }

    private fun attachToHost(host: JPanel, component: CallbackMediaPlayerComponent) {
        runOnEdt {
            val shouldAttach = synchronized(lock) {
                boundHost === host && mediaPlayerComponent === component && lifecycleState == LifecycleState.Active
            }
            if (!shouldAttach) return@runOnEdt
            if (component.parent !== host) {
                component.parent?.remove(component)
                host.removeAll()
                host.add(component, BorderLayout.CENTER)
            }
            host.revalidate()
            host.repaint()
        }
    }

    private fun detachFromHost(host: JPanel, component: CallbackMediaPlayerComponent?) {
        runOnEdt {
            if (component != null && component.parent === host) {
                host.remove(component)
            } else if (component == null) {
                host.removeAll()
            }
            host.revalidate()
            host.repaint()
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}
