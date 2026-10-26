package tv.hsrui.bolo.livePlayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.setting.live.LiveSettings
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.livePlayer.base.LivePlayerController
import tv.hsrui.bolo.livePlayer.danmaku.LiveDanmakuController
import tv.hsrui.bolo.livePlayer.danmaku.LiveDanmakuSender
import tv.hsrui.bolo.livePlayer.session.LivePlaybackSession
import tv.hsrui.network.liveFeature.message.LiveMessageConnectionStatus
import tv.hsrui.network.liveFeature.message.LiveMessageManager
import tv.hsrui.network.liveFeature.player.LivePlayInfoData
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import tv.hsrui.network.liveFeature.player.fetchLiveHlsStreams
import tv.hsrui.network.liveFeature.player.fetchLivePlayInfo
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import tv.hsrui.network.liveFeature.room.fetchLiveRoomInfo

/** 房间资料、信息流与播放意图属于导航条目，原生资源由可重建的直播会话持有。 */
class LivePlayerViewModel internal constructor(
    val roomId: Long,
    private val roomInfoRequest: suspend (Long) -> LiveRoomInfoData = ::fetchLiveRoomInfo,
    private val playInfoRequest: suspend (Long, Int) -> LivePlayInfoData = ::fetchLivePlayInfo,
    private val hlsRequest: suspend (Long, LivePlayStreamData) -> List<LivePlayStreamData> = ::fetchLiveHlsStreams,
    private val settings: LiveSettings = getKoin().get<BoloSettings>().live,
    messageManagerFactory: (CoroutineScope) -> LiveMessageManager = { LiveMessageManager(it) },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LivePlayerUiState>(LivePlayerUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    val messageManager = messageManagerFactory(viewModelScope)
    var playbackSession by mutableStateOf<LivePlaybackSession?>(null)
        private set
    var roomInfo by mutableStateOf<LiveRoomInfoData?>(null)
        private set
    val danmakuSender = LiveDanmakuSender(viewModelScope, roomId = { roomInfo?.roomId })
    val danmakuController = LiveDanmakuController(
        viewModelScope, messageManager, settings.danmaku, roomId = { roomInfo?.roomId },
        sentMessages = danmakuSender.sentMessages,
    )
    var playWhenReady by mutableStateOf(true)
        private set
    var broadcastEnded by mutableStateOf(false)
        private set
    private val isDesktop = getPlatform().type == PlatformType.Desktop
    private var localVolumePercent by mutableIntStateOf(100)
    private var localMuted by mutableStateOf(false)
    val volumePercent: Int
        get() = if (isDesktop) settings.desktopVolumePercent else localVolumePercent
    val muted: Boolean
        get() = if (isDesktop) settings.desktopMuted else localMuted
    var availableQns by mutableStateOf<List<Int>>(emptyList())
        private set
    var selectedQn by mutableStateOf<Int?>(null)
        private set
    private var targetQn = settings.defaultVideoQuality.code
    private val targetCodec = settings.defaultVideoCodec
    private var loadJob: Job? = null
    private var streamRetryJob: Job? = null
    private var streamRetryPending = false
    private var statusJob: Job? = null
    private var metadataJob: Job? = null
    private var playbackStateJob: Job? = null
    private var requestRevision = 0L
    private var statusRevision = 0L
    private var statusCheckRevision = 0L
    private var messageBroadcastRevision = 0L
    private var pageActive = false
    private var awaitingForegroundSync = false
    private var observedLive: Boolean? = null
    private var observedStartTime: Long? = null
    private var hasSeenLive = false
    private var closed = false

    init {
        require(roomId > 0)
        if (isDesktop) viewModelScope.launch {
            snapshotFlow { settings.desktopVolumePercent to settings.desktopMuted }.collect { (volume, muted) ->
                playbackSession?.controller?.setVolume(if (muted) 0 else volume)
                syncPlayback()
            }
        }
        viewModelScope.launch {
            messageManager.connectionState.collect { connection ->
                val connectedRoom = connection.roomId
                if (pageActive && connection.status == LiveMessageConnectionStatus.CONNECTED &&
                    connectedRoom != null && connectedRoom == roomInfo?.roomId && messageManager.isCurrent(connectedRoom, connection.generation))
                    loadPlayback(null, false, true)
            }
        }
        viewModelScope.launch {
            messageManager.roomStatus.collect { status ->
                val room = roomInfo
                if (status != null && pageActive && room != null && status.roomId == room.roomId &&
                    messageManager.isCurrent(status.roomId, status.generation)) {
                    statusRevision++
                    val reopened = status.isLive && status.broadcastRevision > messageBroadcastRevision
                    messageBroadcastRevision = status.broadcastRevision
                    acceptRoomStatus(room.copy(isLive = status.isLive,
                        liveStartTimeEpochSeconds = status.liveStartTimeEpochSeconds ?: room.liveStartTimeEpochSeconds), reopened)
                }
            }
        }
    }

    fun setMessageActive(active: Boolean) {
        if (closed || pageActive == active) return
        pageActive = active
        if (active) roomInfo?.let {
            messageManager.start(it.roomId)
            if (streamRetryPending && it.isLive) loadPlayback(it, false, false)
        }
        else {
            awaitingForegroundSync = true
            statusRevision++
            statusCheckRevision++
            statusJob?.cancel()
            metadataJob?.cancel()
            streamRetryJob?.cancel()
            streamRetryJob = null
            if (observedLive == true && loadJob?.isActive == true) {
                streamRetryPending = true
                requestRevision++
                loadJob?.cancel()
                loadJob = null
            }
            messageManager.stop()
        }
    }

    fun bindPlayback() {
        if (closed || playbackSession?.isClosed == false) return
        lateinit var session: LivePlaybackSession
        session = LivePlaybackSession(
            controller = LivePlayerController(settings),
            onPlay = ::resumePlayback,
            onPause = ::pause,
            onVolume = ::setVolume,
            onClosed = {
                if (playbackSession === session) {
                    requestRevision++
                    loadJob?.cancel()
                    streamRetryJob?.cancel()
                    streamRetryJob = null
                    metadataJob?.cancel()
                    playbackStateJob?.cancel()
                    loadJob = null
                    playbackSession = null
                }
            },
        )
        playbackSession = session
        session.controller.setVolume(if (muted) 0 else volumePercent)
        session.activate()
        playbackStateJob = viewModelScope.launch {
            session.controller.state.collect { state ->
                if (playbackSession === session && state.isBroadcastEnded && state.isEnded) {
                    playWhenReady = false
                    syncPlayback()
                }
            }
        }
        syncPlayback()
        loadPlayback()
    }

    fun loadPlayback() { loadPlayback(null, false, false) }

    private fun loadPlayback(knownRoom: LiveRoomInfoData?, opening: Boolean, statusOnly: Boolean) {
        val session = playbackSession?.takeUnless { it.isClosed } ?: return
        if (statusOnly) {
            statusJob?.cancel()
            val revision = statusRevision
            val checkRevision = ++statusCheckRevision
            statusJob = viewModelScope.launch {
                try {
                    val room = roomInfoRequest(roomId)
                    if (closed || !pageActive || playbackSession !== session || revision != statusRevision) return@launch
                    statusRevision++
                    acceptRoomStatus(room)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* 信息流恢复检查失败不打断现有播放。 */ }
                finally { if (pageActive && playbackSession === session && statusCheckRevision == checkRevision) awaitingForegroundSync = false }
            }
            return
        }
        streamRetryJob?.cancel()
        streamRetryJob = null
        streamRetryPending = false
        loadJob?.cancel()
        metadataJob?.cancel()
        val revision = ++requestRevision
        val previous = uiState.value as? LivePlayerUiState.Success
        mutableUiState.value = previous?.copy(isPreparing = true, error = null) ?: LivePlayerUiState.Loading
        syncPlayback()
        loadJob = viewModelScope.launch {
            try {
                var room = knownRoom ?: roomInfoRequest(roomId)
                if (!isCurrent(session, revision)) return@launch
                roomInfo = room
                if (pageActive) messageManager.start(room.roomId)
                if (!room.isLive) { acceptRoomStatus(room); return@launch }
                observedLive = true
                observedStartTime = room.liveStartTimeEpochSeconds
                hasSeenLive = true
                broadcastEnded = false
                if (opening) metadataJob = viewModelScope.launch {
                    try {
                        val updated = roomInfoRequest(roomId)
                        if (isCurrent(session, revision)) {
                            val current = updated.copy(isLive = true,
                                liveStartTimeEpochSeconds = roomInfo?.liveStartTimeEpochSeconds ?: updated.liveStartTimeEpochSeconds)
                            roomInfo = current
                            (uiState.value as? LivePlayerUiState.Success)?.let {
                                mutableUiState.value = it.copy(room = current)
                                session.updateMedia(current)
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { }
                }
                val playInfo = playInfoRequest(room.roomId, targetQn)
                check(playInfo.roomId == room.roomId) { "直播播放信息与房间不匹配" }
                if (!isCurrent(session, revision)) return@launch
                check(playInfo.isLive && playInfo.streams.isNotEmpty()) { "暂无可用的直播播放源" }
                val streams = coroutineScope {
                    val masters = playInfo.streams.filter {
                        !it.isDrm && it.protocolName == "http_hls" && it.formatName == "fmp4" && it.masterUrl != null
                    }.map { stream -> async {
                        try { hlsRequest(room.roomId, stream) }
                        catch (_: TimeoutCancellationException) { emptyList() }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { emptyList() }
                    } }.awaitAll().flatten()
                    masters + playInfo.streams
                }
                val stream = checkNotNull(selectStream(streams, targetQn, targetCodec)) { "暂无可用的直播播放源" }
                if (!isCurrent(session, revision)) return@launch
                room = (roomInfo ?: room).copy(isLive = true,
                    liveStartTimeEpochSeconds = playInfo.liveStartTimeEpochSeconds ?: roomInfo?.liveStartTimeEpochSeconds)
                roomInfo = room
                val qns = (playInfo.availableQns + streams.map { it.currentQn }).filter { it > 0 }.distinct().sortedDescending()
                availableQns = qns
                selectedQn = stream.currentQn
                session.controller.setVolume(if (muted) 0 else volumePercent)
                session.controller.load(stream, room.roomId, playWhenReady, room.liveStartTimeEpochSeconds)
                mutableUiState.value = LivePlayerUiState.Success(room, stream, qns)
                session.updateMedia(room)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (isCurrent(session, revision)) {
                    val message = if (error is TimeoutCancellationException) "直播加载超时，请重试" else error.message ?: "直播加载失败，请重试"
                    mutableUiState.value = previous?.copy(isPreparing = false, error = message) ?: LivePlayerUiState.Error(message)
                    streamRetryPending = roomInfo?.isLive == true
                    if (streamRetryPending && pageActive) streamRetryJob = viewModelScope.launch {
                        delay(5_000)
                        if (pageActive && isCurrent(session, revision) && roomInfo?.isLive == true) {
                            loadPlayback(roomInfo, false, false)
                        }
                    }
                }
            } finally {
                if (isCurrent(session, revision)) { loadJob = null; syncPlayback() }
            }
        }
    }

    private fun acceptRoomStatus(incoming: LiveRoomInfoData, reopened: Boolean = false) {
        val previousStart = observedStartTime
        val nextStart = incoming.liveStartTimeEpochSeconds
        val restart = incoming.isLive && nextStart != null && previousStart != null && nextStart > previousStart
        var room = incoming
        val changed = observedLive != room.isLive || restart || reopened
        if (room.isLive) {
            if (changed || observedStartTime == null) observedStartTime = nextStart
            if (!changed && uiState.value is LivePlayerUiState.Success) room = room.copy(
                liveStartTimeEpochSeconds = roomInfo?.liveStartTimeEpochSeconds ?: nextStart)
        }
        roomInfo = room
        if (!room.isLive) {
            observedLive = false
            broadcastEnded = hasSeenLive
            requestRevision++
            loadJob?.cancel()
            streamRetryJob?.cancel()
            streamRetryJob = null
            streamRetryPending = false
            metadataJob?.cancel()
            loadJob = null
            val success = uiState.value as? LivePlayerUiState.Success
            if (success != null && hasSeenLive) {
                playbackSession?.controller?.finishBroadcast()
                mutableUiState.value = success.copy(room = room, isPreparing = false, error = null)
                playbackSession?.updateMedia(room)
            } else {
                playWhenReady = false
                playbackSession?.controller?.clearMedia()
                playbackSession?.clearMedia()
                mutableUiState.value = LivePlayerUiState.Offline(room)
            }
            syncPlayback()
        } else if (changed) {
            observedLive = true
            hasSeenLive = true
            broadcastEnded = false
            playWhenReady = !awaitingForegroundSync
            playbackSession?.controller?.clearMedia()
            loadPlayback(room, true, false)
        } else {
            (uiState.value as? LivePlayerUiState.Success)?.let { mutableUiState.value = it.copy(room = room) }
            if (uiState.value is LivePlayerUiState.Success) playbackSession?.updateMedia(room)
        }
    }

    fun selectQuality(qn: Int) {
        if (broadcastEnded || qn !in availableQns || qn == targetQn && qn == selectedQn) return
        targetQn = qn
        if (settings.recordQualitySelectionEnabled) LiveVideoQuality.fromCode(qn)?.let { settings.defaultVideoQuality = it }
        loadPlayback()
    }

    fun resumeLatest() {
        if (closed || broadcastEnded || playbackSession?.isClosed != false) return
        playWhenReady = true
        loadPlayback()
    }

    fun resumePlayback() {
        if (closed || playbackSession?.isClosed != false) return
        val controller = playbackSession?.controller ?: return
        if (broadcastEnded && (controller.state.value.isEnded || !controller.timeline.value.canSeek)) return
        playWhenReady = true
        if (!controller.resumePlayback() && !broadcastEnded) loadPlayback()
        syncPlayback()
    }

    fun seekToMs(positionMs: Long) { playbackSession?.controller?.seekToMs(positionMs) }

    fun pause() {
        playWhenReady = false
        playbackSession?.controller?.pause()
        syncPlayback()
    }

    fun togglePlayback() { if (playWhenReady) pause() else resumePlayback() }

    fun setVolume(percent: Int) {
        if (isDesktop) Snapshot.withMutableSnapshot {
            settings.desktopVolumePercent = percent
            settings.desktopMuted = false
        } else {
            localVolumePercent = percent.coerceIn(0, 200)
            localMuted = false
        }
        playbackSession?.controller?.setVolume(volumePercent)
        syncPlayback()
    }

    fun toggleMuted() {
        if (isDesktop) Snapshot.withMutableSnapshot {
            if (settings.desktopVolumePercent == 0) {
                settings.desktopVolumePercent = 100
                settings.desktopMuted = false
            } else {
                settings.desktopMuted = !settings.desktopMuted
            }
        } else {
            localMuted = !localMuted
        }
        playbackSession?.controller?.setVolume(if (muted) 0 else volumePercent)
        syncPlayback()
    }

    private fun syncPlayback() {
        val state = uiState.value
        playbackSession?.updatePlayback(
            playWhenReady = playWhenReady,
            loading = state is LivePlayerUiState.Loading || state is LivePlayerUiState.Success && state.isPreparing,
            volume = volumePercent,
            muted = muted,
            error = when (state) {
                is LivePlayerUiState.Error -> state.message
                is LivePlayerUiState.Success -> state.error
                else -> null
            },
        )
    }

    private fun isCurrent(session: LivePlaybackSession, revision: Long): Boolean =
        !closed && playbackSession === session && !session.isClosed && requestRevision == revision

    override fun onCleared() {
        closed = true
        requestRevision++
        loadJob?.cancel()
        streamRetryJob?.cancel()
        statusJob?.cancel()
        metadataJob?.cancel()
        danmakuController.close()
        danmakuSender.close()
        messageManager.close()
        playbackSession?.close()
    }

    internal companion object {
        fun selectStream(streams: List<LivePlayStreamData>, qn: Int, codec: LiveVideoCodec): LivePlayStreamData? {
            val candidates = streams.filter { !it.isDrm && it.currentQn > 0 && it.urls.isNotEmpty() }
            val qns = candidates.map { it.currentQn }.distinct()
            val selectedQn = qn.takeIf { it in qns } ?: qns.filter { it < qn }.maxOrNull()
                ?: qns.filter { it > qn }.minOrNull() ?: return null
            val qualities = candidates.filter { it.currentQn == selectedQn }
            val codecs = qualities.map { it.codec }.distinct()
            val selectedCodec = codec.takeIf { it in codecs }
                ?: codecs.filter { it.ordinal < codec.ordinal }.maxByOrNull { it.ordinal }
                ?: codecs.minByOrNull { it.ordinal } ?: return null
            return qualities.filter { it.codec == selectedCodec }.minByOrNull {
                when {
                    it.protocolName == "http_hls" && it.formatName == "fmp4" -> 0
                    it.protocolName == "http_hls" && it.formatName == "ts" -> 1
                    it.protocolName == "http_stream" && it.formatName == "flv" -> 2
                    else -> 3
                }
            }
        }
    }
}
