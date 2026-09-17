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
        val isNavigatingEpisode: Boolean = false,
        val episodeNavigationPrevious: Boolean? = null,
        val emptySeasonIds: Set<Long> = emptySet(),
    ) : MediaPlaybackUiState {
        val episodeNavigationEnabled get() = !isNavigatingEpisode && episodeNavigationPrevious == null
        val hasPreviousEpisode get() = hasAdjacentEpisode(before = true)
        val hasNextEpisode get() = hasAdjacentEpisode(before = false)

        internal fun adjacentEpisode(before: Boolean): MediaEpisode? {
            val episodes = media.episodes.let { if (isDescending) it.reversed() else it }
            val index = episodes.indexOfFirst { it.episodeId == episode?.episodeId }
            if (index < 0) return null
            return (if (before) episodes.take(index).asReversed() else episodes.drop(index + 1)).firstOrNull { it.isAvailable }
        }

        internal fun adjacentSeasons(before: Boolean): List<MediaSeasonSummary> {
            val index = seasons.indexOfFirst { it.seasonId == media.seasonId }
            if (index < 0 || episode == null) return emptyList()
            return (if (before) seasons.take(index).asReversed() else seasons.drop(index + 1))
                .filter { it.seasonId !in emptySeasonIds }
        }

        private fun hasAdjacentEpisode(before: Boolean): Boolean =
            adjacentEpisode(before) != null || adjacentSeasons(before).isNotEmpty()
    }
}
