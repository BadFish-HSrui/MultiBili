package tv.hsrui.bolo.view.media

import tv.hsrui.network.feature.media.MediaEpisode
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.media.MediaSeasonSummary
import tv.hsrui.network.model.MediaCard

sealed interface MediaPlaybackUiState {
    data object Loading : MediaPlaybackUiState
    data class Error(val message: String) : MediaPlaybackUiState
    data class Success(
        val media: MediaSeasonData,
        val episode: MediaEpisode?,
        val selectedSeasonId: Long,
        val browsedSeason: MediaSeasonData?,
        val seasons: List<MediaSeasonSummary> = media.seasons,
        val seasonLoading: Boolean = false,
        val seasonError: String? = null,
        val isDescending: Boolean = false,
        val recommendations: List<MediaCard> = emptyList(),
        val recommendationsLoading: Boolean = true,
        val recommendationsError: String? = null,
    ) : MediaPlaybackUiState
}
