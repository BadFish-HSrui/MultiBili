package tv.hsrui.bolo.main.media

import tv.hsrui.network.feature.media.MediaConditionsData

sealed class MediaFilterUiState {
    data object Loading : MediaFilterUiState()
    data class Success(val conditions: MediaConditionsData) : MediaFilterUiState()
    data class Error(val message: String) : MediaFilterUiState()
}
