package tv.hsrui.bolo.ui.components.dialog

import tv.hsrui.network.model.MediaCard
import tv.hsrui.network.model.VideoCard

internal sealed interface VideoInfoDialogUiState {
    data object Loading : VideoInfoDialogUiState

    data class Video(val info: VideoCard) : VideoInfoDialogUiState

    data class Media(val info: MediaCard, val episodeId: Long) : VideoInfoDialogUiState

    data class Error(val message: String) : VideoInfoDialogUiState
}
