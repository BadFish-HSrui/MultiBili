package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.livePlayer.base.LivePlayerController
import tv.hsrui.bolo.player.session.BoloSystemMediaAction
import tv.hsrui.bolo.player.session.BoloSystemMediaCommand
import tv.hsrui.bolo.player.session.BoloSystemMediaMetadata
import tv.hsrui.bolo.player.session.BoloSystemMediaOwner
import tv.hsrui.bolo.player.session.BoloSystemMediaPlaybackStatus
import tv.hsrui.bolo.player.session.BoloSystemMediaSession
import tv.hsrui.bolo.player.session.BoloSystemMediaState
import tv.hsrui.bolo.player.session.createBoloSystemMediaSession
import tv.hsrui.network.feature.player.fetchSystemMediaArtwork
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData

/** 直播页保留业务选择；本会话独占可释放的播放器和系统媒体适配。仅在 Main 使用。 */
class LivePlaybackSession(
    val controller: LivePlayerController,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onVolume: (Int) -> Unit,
    private val onClosed: () -> Unit,
) : BoloSystemMediaOwner() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BoloSystemMediaState(isLive = true, canSetRate = false))
    override val state = mutableState.asStateFlow()
    override var isClosed = false
        private set
    internal override val outputController get() = controller
    private var adapter: BoloSystemMediaSession? = null
    private var metadata = BoloSystemMediaMetadata()
    private var artwork: ByteArray? = null
    private var artworkJob: Job? = null
    private var roomIsLive = false
    private var requested = false
    private var loading = false
    private var requestError: String? = null
    private var volume = 100
    private var muted = false
    private var foreground = true
    private var interruptionRevision: Long? = null
    private var interruptionMedia = ""

    init {
        scope.launch { controller.state.collect { publish() } }
    }

    override fun activate() {
        if (isClosed || isCurrent) return
        BoloSystemMediaOwner.acquire(this)
        adapter = try { createBoloSystemMediaSession(this) }
        catch (error: Exception) { println("系统媒体控件不可用：${error.message}"); null }
        publish()
    }

    fun updateMedia(info: LiveRoomInfoData) {
        if (isClosed) return
        val value = BoloSystemMediaMetadata(
            mediaId = "live:${info.roomId}",
            title = info.title.ifBlank { "直播间 ${info.roomId}" },
            artist = info.upName,
            artworkUrl = info.coverUrl,
        )
        val artworkChanged = value.mediaId != metadata.mediaId || value.artworkUrl != metadata.artworkUrl
        if (value.mediaId != metadata.mediaId) interruptionRevision = null
        metadata = value
        roomIsLive = info.isLive
        if (artworkChanged) {
            artworkJob?.cancel()
            artwork = null
            if (value.artworkUrl.isNotBlank()) artworkJob = scope.launch {
                val bytes = try { fetchSystemMediaArtwork(value.artworkUrl) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                if (isCurrent && metadata.mediaId == value.mediaId && metadata.artworkUrl == value.artworkUrl) {
                    artwork = bytes
                    publish()
                }
            }
        }
        publish()
    }

    fun clearMedia() {
        if (isClosed) return
        artworkJob?.cancel()
        artworkJob = null
        artwork = null
        metadata = BoloSystemMediaMetadata()
        roomIsLive = false
        interruptionRevision = null
        publish()
    }

    fun updatePlayback(playWhenReady: Boolean, loading: Boolean, volume: Int, muted: Boolean, error: String? = null) {
        if (isClosed) return
        if (requested != playWhenReady) interruptionRevision = null
        requested = playWhenReady
        this.loading = loading
        requestError = error
        this.volume = volume.coerceIn(0, 200)
        this.muted = muted
        publish()
    }

    override fun setForeground(active: Boolean) {
        if (!isCurrent) return
        try { adapter?.setForeground(active) }
        catch (error: Exception) { println("系统媒体控件恢复失败：${error.message}") }
        if (getPlatform().type == PlatformType.Desktop || foreground == active) return
        foreground = active
        interruptionRevision = null
        if (!active) onPause()
        controller.setForeground(active)
        publish()
    }

    override fun interruptAudio(mayResume: Boolean) {
        if (!isCurrent) return
        val resume = mayResume && requested && foreground
        onPause()
        interruptionRevision = controller.playIntentRevision.takeIf { resume }
        interruptionMedia = metadata.mediaId
        publish()
    }

    override fun resumeAudioAfterInterruption(allowed: Boolean) {
        val revision = interruptionRevision
        interruptionRevision = null
        if (allowed && foreground && isCurrent && revision != null && interruptionMedia == metadata.mediaId &&
            controller.playIntentRevision == revision) onPlay()
    }

    override fun dispatch(command: BoloSystemMediaCommand) {
        scope.launch {
            val value = state.value
            if (!isCurrent || value.metadata.mediaId.isEmpty() || command.mediaId != value.metadata.mediaId) return@launch
            when (command.action) {
                BoloSystemMediaAction.Play -> if (value.canPlay && foreground &&
                    !(requested && (loading || controller.state.value.isPlaying || controller.state.value.isBuffering))) onPlay()
                BoloSystemMediaAction.Pause -> if (value.canPause) onPause()
                BoloSystemMediaAction.TogglePlayPause -> if (requested) onPause() else if (value.canPlay && foreground) onPlay()
                BoloSystemMediaAction.Stop -> onPause()
                BoloSystemMediaAction.SetVolume -> if (command.value.isFinite())
                    onVolume((command.value.coerceIn(0.0, 2.0) * 100).toInt())
                BoloSystemMediaAction.SeekTo, BoloSystemMediaAction.SeekBy, BoloSystemMediaAction.Previous,
                BoloSystemMediaAction.Next, BoloSystemMediaAction.SetRate, BoloSystemMediaAction.Raise -> Unit
            }
            publish()
        }
    }

    private fun publish() {
        if (isClosed) return
        val playback = controller.state.value
        val hasMedia = metadata.mediaId.isNotEmpty()
        val playable = roomIsLive || playback.isBroadcastEnded && (!playback.isEnded || controller.timeline.value.canSeek)
        val status = when {
            !hasMedia || !playable -> BoloSystemMediaPlaybackStatus.Stopped
            playback.error != null || (requestError != null && !playback.isPlaying) -> BoloSystemMediaPlaybackStatus.Error
            playback.isEnded -> BoloSystemMediaPlaybackStatus.Ended
            !requested || playback.isPlaybackSuspended -> BoloSystemMediaPlaybackStatus.Paused
            loading || playback.isBuffering -> BoloSystemMediaPlaybackStatus.Buffering
            playback.isPlaying -> BoloSystemMediaPlaybackStatus.Playing
            else -> BoloSystemMediaPlaybackStatus.Paused
        }
        val value = BoloSystemMediaState(
            metadata = metadata,
            status = status,
            playWhenReady = hasMedia && playable && requested && !playback.isEnded,
            volume = if (muted) 0.0 else volume / 100.0,
            canPlay = hasMedia && playable && foreground && !playback.isEnded,
            canPause = hasMedia && playable && !playback.isEnded,
            artwork = artwork,
            isLive = true,
            canSetRate = false,
        )
        mutableState.value = value
        if (isCurrent) try { adapter?.publish(value) }
        catch (error: Exception) { println("系统媒体状态更新失败：${error.message}") }
    }

    internal override fun setVideoOutputActive(active: Boolean) = controller.setVideoOutputActive(active)

    internal override fun releaseBackgroundResources() = controller.releaseBackgroundResources()

    internal override suspend fun pauseAndObserve() {
        if (!isClosed) {
            onPause()
            controller.pauseAndObserve()
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        BoloSystemMediaOwner.release(this)
        try { adapter?.close() } catch (error: Exception) { println("系统媒体会话释放失败：${error.message}") }
        adapter = null
        scope.cancel()
        controller.dispose()
        onClosed()
    }
}
