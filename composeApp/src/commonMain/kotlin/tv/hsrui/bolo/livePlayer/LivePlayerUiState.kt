package tv.hsrui.bolo.livePlayer

import tv.hsrui.network.liveFeature.player.LivePlayStreamData
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData

sealed class LivePlayerUiState {
    data object Loading : LivePlayerUiState()
    data class Success(
        val room: LiveRoomInfoData,
        val stream: LivePlayStreamData,
        val availableQns: List<Int>,
        val isPreparing: Boolean = false,
        val error: String? = null,
    ) : LivePlayerUiState()
    data class Error(val message: String) : LivePlayerUiState()
    data class Offline(val room: LiveRoomInfoData) : LivePlayerUiState()
}
