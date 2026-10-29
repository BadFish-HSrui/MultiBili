package tv.hsrui.bolo.accountFeature.feature.history

import tv.hsrui.network.feature.history.HistoryItem

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Success(val items: List<HistoryItem>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}