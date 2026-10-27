package tv.hsrui.bolo.ui.components.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import tv.hsrui.network.liveFeature.room.fetchLiveRoomInfo
import tv.hsrui.network.model.LiveCard

internal class LiveRoomInfoDialogViewModel(private val roomId: Long) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LiveRoomInfoDialogUiState>(LiveRoomInfoDialogUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var requestVersion = 0L

    init { load() }

    fun load() {
        val version = ++requestVersion
        loadJob?.cancel()
        mutableUiState.value = LiveRoomInfoDialogUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                require(roomId > 0L) { "直播间编号无效" }
                val room = withTimeoutOrNull(15_000) { fetchLiveRoomInfo(roomId) }
                    ?: error("直播间详情请求超时")
                currentCoroutineContext().ensureActive()
                if (version != requestVersion) return@launch
                check(room.roomId == roomId) { "直播间信息不匹配，请重试" }
                mutableUiState.value = LiveRoomInfoDialogUiState.Room(room.toLiveCard())
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                if (version == requestVersion) {
                    mutableUiState.value = LiveRoomInfoDialogUiState.Error("直播间详情请求超时")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (version == requestVersion) {
                    mutableUiState.value = LiveRoomInfoDialogUiState.Error(error.message ?: "直播间信息加载失败")
                }
            }
        }
    }

    override fun onCleared() {
        requestVersion++
        loadJob?.cancel()
        super.onCleared()
    }
}

internal fun LiveRoomInfoData.toLiveCard(): LiveCard = LiveCard(
    roomId = roomId,
    title = title,
    userCover = coverUrl,
    upMid = upMid,
    upName = upName,
    avatar = upAvatarUrl,
    areaId = areaId ?: 0,
    areaName = areaName,
    parentAreaId = parentAreaId ?: 0,
    parentAreaName = parentAreaName,
    liveStatus = if (isLive) 1 else 0,
    // 房间资料不提供下播时间，保留未知值，不能使用开播时间代替。
    recordLiveTime = 0L,
    popularity = popularity ?: -1L,
    watchedShow = LiveCard.WatchedShow(watchedCount ?: -1L),
)
