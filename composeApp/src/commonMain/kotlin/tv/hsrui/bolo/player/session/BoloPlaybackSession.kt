package tv.hsrui.bolo.player.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.settings.BoloPlayerSettings
import tv.hsrui.bolo.boloSetting.PlaybackEndBehavior
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.network.feature.player.fetchSystemMediaArtwork
import kotlin.time.TimeSource

/** 页面与系统服务共享所有权，输出宿主的创建/销毁不改变会话寿命。仅在 Main 使用。 */
class BoloPlaybackSession private constructor(val key: String) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val player = VideoPlayerViewModel(0L, 0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings: BoloSettings = getKoin().get()
    private val playerSettings: BoloPlayerSettings = getKoin().get()
    private val mutableState = MutableStateFlow(BoloSystemMediaState())
    val state = mutableState.asStateFlow()
    private var adapter: BoloSystemMediaSession? = null
    private var artworkJob: Job? = null
    private var artwork: ByteArray? = null
    private var metadata = BoloSystemMediaMetadata()
    private var previous: (() -> Unit)? = null
    private var next: (() -> Unit)? = null
    private var navigationEnabled = false
    private var closed = false
    private var foreground = true
    private var stopped = false
    private var interruptionRevision: Long? = null
    private var interruptionMedia = ""
    private var seekRevision = 0L
    private var wasSeeking = false
    private var wasPlaying = false
    private var wasEnded = false
    private var lastPublish = TimeSource.Monotonic.markNow()

    fun <T : ViewModel> getViewModel(key: String, create: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return (viewModelStore[key] as? T) ?: create().also { viewModelStore.put(key, it) }
    }

    init {
        player.onPlaybackPageEntered()
        scope.launch {
            snapshotFlow { settings.playback.backgroundPlaybackEnabled to playerSettings.playback.resumeAfterBackgroundEnabled }
                .collect { (background, resume) ->
                    player.controller.setBackgroundPlaybackEnabled(background && getPlatform().type != PlatformType.Desktop)
                    player.controller.setResumeAfterBackgroundEnabled(resume)
                    player.setBackgroundPlaybackAllowed(background)
                    publish()
                }
        }
        scope.launch {
            player.controller.state.collect { playback ->
                if (stopped && playback.playWhenReady) stopped = false
                if (wasSeeking && !playback.isSeeking && playback.hasConfirmedPosition) seekRevision++
                wasSeeking = playback.isSeeking
                val ended = (player.singleEpisodeLoopEnabled || wasPlaying) &&
                    !wasEnded && playback.isEnded && !playback.isPlaybackSuspended
                wasPlaying = playback.isPlaying && !playback.isPlaybackSuspended
                wasEnded = playback.isEnded
                publish()
                if (ended) {
                    val behavior = if (player.singleEpisodeLoopEnabled) PlaybackEndBehavior.Replay
                        else settings.playback.endBehavior
                    when (behavior) {
                        PlaybackEndBehavior.Off -> Unit
                        PlaybackEndBehavior.Replay -> player.play()
                        PlaybackEndBehavior.NextEpisode -> if (navigationEnabled) next?.invoke()
                    }
                }
            }
        }
        scope.launch { player.uiState.collect { publish(force = true) } }
        scope.launch { snapshotFlow { player.pendingPlayWhenReady }.collect { publish(force = true) } }
        scope.launch {
            snapshotFlow { playerSettings.controls.desktopVolumePercent to playerSettings.controls.desktopMuted }
                .collect { publish(force = true) }
        }
        scope.launch { while (isActive) { delay(1_000); publish(force = true) } }
    }

    private fun connect() {
        adapter = try { createBoloSystemMediaSession(this) }
        catch (error: Exception) { println("系统媒体控件不可用：${error.message}"); null }
        publish(force = true)
    }

    fun updateMedia(value: BoloSystemMediaMetadata, previous: (() -> Unit)?, next: (() -> Unit)?, enabled: Boolean) {
        if (closed) return
        this.previous = previous
        this.next = next
        navigationEnabled = enabled
        val artworkChanged = value.artworkUrl != metadata.artworkUrl || value.mediaId != metadata.mediaId
        if (value.mediaId != metadata.mediaId) {
            stopped = false
            interruptionRevision = null
            wasPlaying = false
            wasEnded = false
            seekRevision++
        }
        metadata = value
        if (artworkChanged) {
            artworkJob?.cancel()
            artwork = null
            if (value.artworkUrl.isNotBlank()) artworkJob = scope.launch {
                val bytes = try { fetchSystemMediaArtwork(value.artworkUrl) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                if (!closed && metadata.mediaId == value.mediaId && metadata.artworkUrl == value.artworkUrl) {
                    artwork = bytes
                    publish(force = true)
                }
            }
        }
        publish(force = true)
    }

    fun setForeground(active: Boolean) {
        if (closed || foreground == active) return
        foreground = active
        player.onPlaybackForegroundChanged(active)
        player.controller.setForeground(active)
        publish(force = true)
    }

    fun interruptAudio(mayResume: Boolean) {
        if (closed) return
        val resume = mayResume && state.value.playWhenReady
        player.pause()
        interruptionRevision = player.controller.playIntentRevision.takeIf { resume }
        interruptionMedia = metadata.mediaId
        publish(force = true)
    }

    fun resumeAudioAfterInterruption(allowed: Boolean) {
        val revision = interruptionRevision
        interruptionRevision = null
        if (allowed && revision != null && !closed && interruptionMedia == metadata.mediaId &&
            player.controller.playIntentRevision == revision && (foreground || settings.playback.backgroundPlaybackEnabled)) {
            player.play()
        }
    }

    fun dispatch(command: BoloSystemMediaCommand) {
        scope.launch {
            val snapshot = state.value
            if (closed || command.mediaId != snapshot.metadata.mediaId || snapshot.metadata.mediaId.isEmpty()) return@launch
            when (command.action) {
                BoloSystemMediaAction.Play -> if (snapshot.canPlay) { stopped = false; player.play() }
                BoloSystemMediaAction.Pause -> if (snapshot.canPause) player.pause()
                BoloSystemMediaAction.TogglePlayPause -> if (snapshot.playWhenReady) player.pause()
                    else if (snapshot.canPlay) { stopped = false; player.play() }
                BoloSystemMediaAction.SeekTo -> if (snapshot.canSeek) player.seekToMs(command.positionMs)
                BoloSystemMediaAction.SeekBy -> if (snapshot.canSeek) {
                    val target = (snapshot.positionMs.toDouble() + command.positionMs).coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
                    if (snapshot.durationMs > 0 && target > snapshot.durationMs) {
                        if (snapshot.canNext) next?.invoke()
                    } else player.seekToMs(target)
                }
                BoloSystemMediaAction.Previous -> if (snapshot.canPrevious) previous?.invoke()
                BoloSystemMediaAction.Next -> if (snapshot.canNext) next?.invoke()
                BoloSystemMediaAction.Stop -> { player.pause(); player.seekToMs(0L); stopped = true }
                BoloSystemMediaAction.SetRate -> if (snapshot.canPlay && command.value.isFinite())
                    player.controller.setPlaybackSpeed(command.value.coerceIn(0.25, 3.0).toFloat())
                BoloSystemMediaAction.SetVolume -> if (command.value.isFinite())
                    player.setDesktopVolume((command.value.coerceIn(0.0, 2.0) * 100).toInt())
                BoloSystemMediaAction.Raise -> Unit // 窗口激活由桌面适配负责。
            }
            publish(force = true)
        }
    }

    private fun publish(force: Boolean = false) {
        if (closed) return
        val playback = player.controller.state.value
        val hasMedia = metadata.mediaId.isNotEmpty()
        val loading = player.uiState.value is VideoPlayerUiState.Loading || player.pendingPlayWhenReady != null
        val permitted = hasMedia && !playback.isPlaybackSuspended && (foreground || settings.playback.backgroundPlaybackEnabled)
        val status = when {
            !hasMedia || stopped -> BoloSystemMediaPlaybackStatus.Stopped
            player.uiState.value is VideoPlayerUiState.Error -> BoloSystemMediaPlaybackStatus.Error
            playback.isPlaybackSuspended -> BoloSystemMediaPlaybackStatus.Paused
            loading -> BoloSystemMediaPlaybackStatus.Buffering
            playback.isEnded -> BoloSystemMediaPlaybackStatus.Ended
            playback.isBuffering -> BoloSystemMediaPlaybackStatus.Buffering
            playback.isPlaying -> BoloSystemMediaPlaybackStatus.Playing
            else -> BoloSystemMediaPlaybackStatus.Paused
        }
        // 只清空系统媒体输出，保留应用内媒体与进度，回到前台后重新发布。
        val hideMedia = getPlatform().type != PlatformType.Desktop && !foreground && !settings.playback.backgroundPlaybackEnabled
        val value = if (hideMedia) BoloSystemMediaState() else BoloSystemMediaState(
            metadata, status, permitted && (player.pendingPlayWhenReady ?: playback.playWhenReady),
            if (loading) 0 else playback.currentPositionMs.coerceAtLeast(0), if (loading) 0 else playback.durationMs.coerceAtLeast(0),
            playback.playbackSpeed.toDouble(),
            if (playerSettings.controls.desktopMuted) 0.0 else playerSettings.controls.desktopVolumePercent / 100.0,
            canPlay = permitted && (loading || player.uiState.value is VideoPlayerUiState.Success),
            canPause = permitted,
            canSeek = permitted && !loading && playback.isSeekable && playback.hasConfirmedPosition,
            canPrevious = permitted && navigationEnabled && previous != null,
            canNext = permitted && navigationEnabled && next != null,
            artwork = artwork, seekRevision = seekRevision,
        )
        val old = state.value
        mutableState.value = value
        val significant = old.copy(positionMs = value.positionMs) != value
        if (force || significant || lastPublish.elapsedNow().inWholeMilliseconds >= 1_000) {
            lastPublish = TimeSource.Monotonic.markNow()
            try { adapter?.publish(value) }
            catch (error: Exception) { println("系统媒体状态更新失败：${error.message}") }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        if (current === this) current = null
        try { adapter?.close() } catch (error: Exception) { println("系统媒体会话释放失败：${error.message}") }
        adapter = null
        scope.cancel()
        viewModelStore.clear()
        player.closePlayback()
    }

    companion object {
        var current: BoloPlaybackSession? = null
            private set

        fun obtain(key: String): BoloPlaybackSession {
            current?.takeIf { it.key == key && !it.closed }?.let { return it }
            current?.close()
            return BoloPlaybackSession(key).also { current = it; it.connect() }
        }

        /** 预见式返回只预览页面；确认成为栈顶后才获取会话，离场动画保留原引用。 */
        @Composable
        internal fun <T : ViewModel> rememberViewModel(key: String, isActive: Boolean, create: () -> T): T? {
            var retained by remember(key) { mutableStateOf<Pair<BoloPlaybackSession, T>?>(null) }
            if (isActive && retained?.first?.closed != false) {
                val session = obtain(key)
                retained = session to session.getViewModel(key, create)
            }
            return retained?.second
        }
    }
}
