package tv.hsrui.bolo.livePlayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.livePlayer.base.LivePlayerController
import tv.hsrui.bolo.livePlayer.session.LivePlaybackSession
import tv.hsrui.network.liveFeature.player.LivePlayInfoData
import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import tv.hsrui.network.liveFeature.player.fetchLiveHlsStreams
import tv.hsrui.network.liveFeature.player.fetchLivePlayInfo
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import tv.hsrui.network.liveFeature.room.fetchLiveRoomInfo

/** 房间资料和播放意图属于导航条目，原生资源由可重建的直播会话持有。 */
class LivePlayerViewModel internal constructor(
    val roomId: Long,
    private val roomInfoRequest: suspend (Long) -> LiveRoomInfoData = ::fetchLiveRoomInfo,
    private val playInfoRequest: suspend (Long, Int) -> LivePlayInfoData = ::fetchLivePlayInfo,
    private val hlsRequest: suspend (Long, LivePlayStreamData) -> List<LivePlayStreamData> = ::fetchLiveHlsStreams,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LivePlayerUiState>(LivePlayerUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    var playbackSession by mutableStateOf<LivePlaybackSession?>(null)
        private set
    var roomInfo by mutableStateOf<LiveRoomInfoData?>(null)
        private set
    var playWhenReady by mutableStateOf(true)
        private set
    var volumePercent by mutableIntStateOf(100)
        private set
    var muted by mutableStateOf(false)
        private set
    var availableQns by mutableStateOf<List<Int>>(emptyList())
        private set
    var selectedQn by mutableStateOf<Int?>(null)
        private set
    private var targetQn = LiveVideoQuality.ORIGINAL.code
    private var loadJob: Job? = null
    private var requestRevision = 0L
    private var closed = false

    init {
        require(roomId > 0)
    }

    fun bindPlayback() {
        if (closed || playbackSession?.isClosed == false) return
        lateinit var session: LivePlaybackSession
        session = LivePlaybackSession(
            controller = LivePlayerController(),
            onPlay = ::resumePlayback,
            onPause = ::pause,
            onVolume = ::setVolume,
            onClosed = {
                if (playbackSession === session) {
                    requestRevision++
                    loadJob?.cancel()
                    loadJob = null
                    playbackSession = null
                }
            },
        )
        playbackSession = session
        session.activate()
        syncPlayback()
        loadPlayback()
    }

    fun loadPlayback() {
        val session = playbackSession?.takeUnless { it.isClosed } ?: return
        loadJob?.cancel()
        val revision = ++requestRevision
        val previous = uiState.value as? LivePlayerUiState.Success
        mutableUiState.value = previous?.copy(isPreparing = true, error = null) ?: LivePlayerUiState.Loading
        syncPlayback()
        loadJob = viewModelScope.launch {
            try {
                val room = roomInfoRequest(roomId)
                if (!isCurrent(session, revision)) return@launch
                roomInfo = room
                if (!room.isLive) {
                    playWhenReady = false
                    session.controller.clearMedia()
                    mutableUiState.value = LivePlayerUiState.Offline(room)
                    session.clearMedia()
                    return@launch
                }
                val info = playInfoRequest(room.roomId, targetQn)
                check(info.roomId == room.roomId) { "直播播放信息与房间不匹配" }
                if (!isCurrent(session, revision)) return@launch
                if (!info.isLive) {
                    playWhenReady = false
                    session.controller.clearMedia()
                    mutableUiState.value = LivePlayerUiState.Offline(room)
                    session.clearMedia()
                    return@launch
                }
                val streams = coroutineScope {
                    val masters = info.streams.filter {
                        !it.isDrm && it.protocolName == "http_hls" && it.formatName == "fmp4" && it.masterUrl != null
                    }.map { stream ->
                        async {
                            try { hlsRequest(room.roomId, stream) }
                            catch (_: TimeoutCancellationException) { emptyList() }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten()
                    masters + info.streams
                }
                val stream = checkNotNull(selectStream(streams, targetQn, LiveVideoCodec.HEVC)) {
                    "暂无可用的直播播放源"
                }
                if (!isCurrent(session, revision)) return@launch
                val qns = (info.availableQns + streams.map { it.currentQn })
                    .filter { it > 0 }.distinct().sortedDescending()
                availableQns = qns
                selectedQn = stream.currentQn
                session.controller.setVolume(if (muted) 0 else volumePercent)
                session.controller.load(stream, room.roomId, playWhenReady, info.liveStartTimeEpochSeconds ?: room.liveStartTimeEpochSeconds)
                mutableUiState.value = LivePlayerUiState.Success(room, stream, qns)
                session.updateMedia(room)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (isCurrent(session, revision)) {
                    val message = if (error is TimeoutCancellationException) "直播加载超时，请重试"
                        else error.message ?: "直播加载失败，请重试"
                    mutableUiState.value = previous?.copy(isPreparing = false, error = message)
                        ?: LivePlayerUiState.Error(message)
                }
            } finally {
                if (isCurrent(session, revision)) {
                    loadJob = null
                    syncPlayback()
                }
            }
        }
    }

    fun selectQuality(qn: Int) {
        if (qn !in availableQns || qn == targetQn && qn == selectedQn) return
        targetQn = qn
        loadPlayback()
    }

    fun resumeLatest() {
        if (closed || playbackSession?.isClosed != false) return
        playWhenReady = true
        loadPlayback()
    }

    fun resumePlayback() {
        if (closed || playbackSession?.isClosed != false) return
        playWhenReady = true
        if (playbackSession?.controller?.resumePlayback() != true) loadPlayback()
        syncPlayback()
    }

    fun seekToMs(positionMs: Long) { playbackSession?.controller?.seekToMs(positionMs) }

    fun pause() {
        playWhenReady = false
        playbackSession?.controller?.pause()
        syncPlayback()
    }

    fun togglePlayback() {
        if (playWhenReady) pause() else resumePlayback()
    }

    fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 200)
        muted = false
        playbackSession?.controller?.setVolume(volumePercent)
        syncPlayback()
    }

    fun toggleMuted() {
        muted = !muted
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
