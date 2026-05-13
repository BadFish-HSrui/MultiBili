package tv.hsrui.bolo.accountFeature.feature.history

import tv.hsrui.network.feature.history.HistoryVideoCard

sealed class HistoryVideosUiState {
    data object Loading : HistoryVideosUiState()
    data class  Success(val videos: List<HistoryVideoCard>) : HistoryVideosUiState()
    data class  Error(val message: String) : HistoryVideosUiState()
}