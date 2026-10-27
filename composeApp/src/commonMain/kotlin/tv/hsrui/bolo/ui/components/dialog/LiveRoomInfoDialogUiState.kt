package tv.hsrui.bolo.ui.components.dialog

import tv.hsrui.network.model.LiveCard

internal sealed interface LiveRoomInfoDialogUiState {
    data object Loading : LiveRoomInfoDialogUiState
    data class Room(val info: LiveCard) : LiveRoomInfoDialogUiState
    data class Error(val message: String) : LiveRoomInfoDialogUiState
}
