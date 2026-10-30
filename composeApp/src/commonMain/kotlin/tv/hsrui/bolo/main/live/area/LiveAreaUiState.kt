package tv.hsrui.bolo.main.live.area

import tv.hsrui.network.liveFeature.area.LiveParentAreaData

sealed interface LiveAreaUiState {
    data object Loading : LiveAreaUiState
    data class Success(
        val parentAreas: List<LiveParentAreaData>,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : LiveAreaUiState
    data class Error(val message: String) : LiveAreaUiState
}
