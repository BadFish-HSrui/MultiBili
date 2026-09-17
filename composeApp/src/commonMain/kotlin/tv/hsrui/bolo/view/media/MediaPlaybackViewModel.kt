package tv.hsrui.bolo.view.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.media.fetchMediaSeason
import tv.hsrui.network.feature.media.fetchRelatedMedia

class MediaPlaybackViewModel(private val seasonId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<MediaPlaybackUiState>(MediaPlaybackUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val seasons = mutableMapOf<Long, MediaSeasonData>()
    private val seasonLocks = mutableMapOf<Long, Mutex>()
    private var navigationJob: Job? = null
    private var navigationGeneration = 0L
    private var mediaJob: Job? = null
    private var seasonJob: Job? = null
    private var recommendationsJob: Job? = null
    private var mediaGeneration = 0L
    private var seasonGeneration = 0L
    private var recommendationsGeneration = 0L

    init {
        loadMedia()
    }

    fun loadMedia() {
        cancelEpisodeNavigation()
        mediaJob?.cancel()
        seasonJob?.cancel()
        recommendationsJob?.cancel()
        val generation = ++mediaGeneration
        ++seasonGeneration
        ++recommendationsGeneration
        _uiState.value = MediaPlaybackUiState.Loading
        mediaJob = viewModelScope.launch {
            try {
                val response = fetchMediaSeason(seasonId)
                if (generation != mediaGeneration) return@launch
                val media = response.season
                if (!response.isSuccess || media == null) {
                    _uiState.value = MediaPlaybackUiState.Error(response.message)
                    return@launch
                }
                seasons[media.seasonId] = media
                _uiState.value = MediaPlaybackUiState.Success(
                    media = media,
                    episode = media.episodes.firstOrNull { it.isAvailable },
                    selectedSeasonId = media.seasonId,
                    browsedSeason = media,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == mediaGeneration) {
                    _uiState.value = MediaPlaybackUiState.Error(e.message ?: "媒体信息加载失败")
                }
            }
        }
    }

    private suspend fun loadSeasonData(selectedSeasonId: Long): MediaSeasonData =
        seasonLocks.getOrPut(selectedSeasonId) { Mutex() }.withLock {
            val season = seasons[selectedSeasonId] ?: run {
                val response = withTimeoutOrNull(15_000) { fetchMediaSeason(selectedSeasonId) }
                    ?: error("分季请求超时")
                check(response.isSuccess) { response.message }
                val season = checkNotNull(response.season)
                check(season.seasonId == selectedSeasonId) { "返回的分季信息不匹配" }
                seasons[selectedSeasonId] = season
                season
            }
            val current = _uiState.value as? MediaPlaybackUiState.Success
            if (current != null && season.episodes.none { it.isAvailable }) {
                _uiState.value = current.copy(emptySeasonIds = current.emptySeasonIds + selectedSeasonId)
            }
            season
        }

    fun loadSeason(selectedSeasonId: Long) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        if (current.seasons.none { it.seasonId == selectedSeasonId }) return
        if (current.selectedSeasonId == selectedSeasonId && (current.browsedSeason != null || current.seasonLoading)) return
        seasonJob?.cancel()
        val generation = ++seasonGeneration
        val cached = seasons[selectedSeasonId]
        _uiState.value = current.copy(
            selectedSeasonId = selectedSeasonId,
            browsedSeason = cached,
            seasonLoading = cached == null,
            seasonError = null,
        )
        if (cached != null) return
        seasonJob = viewModelScope.launch {
            try {
                val season = loadSeasonData(selectedSeasonId)
                if (generation != seasonGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                _uiState.value = latest.copy(browsedSeason = season, seasonLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != seasonGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                _uiState.value = latest.copy(seasonLoading = false, seasonError = e.message ?: "分季加载失败")
            }
        }
    }

    fun selectEpisode(episodeId: Long) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        val season = current.browsedSeason ?: return
        if (season.episodes.none { it.episodeId == episodeId && it.isAvailable }) return
        cancelEpisodeNavigation()
        commitEpisode(season, episodeId)
    }

    private fun commitEpisode(season: MediaSeasonData, episodeId: Long, navigationPrevious: Boolean? = null) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        val episode = season.episodes.firstOrNull { it.episodeId == episodeId && it.isAvailable } ?: return
        seasonJob?.cancel()
        ++seasonGeneration
        val seasonChanged = current.media.seasonId != season.seasonId
        _uiState.value = current.copy(
            media = season,
            episode = episode,
            selectedSeasonId = season.seasonId,
            browsedSeason = season,
            seasonLoading = false,
            seasonError = null,
            isNavigatingEpisode = false,
            episodeNavigationPrevious = navigationPrevious,
            seasons = (current.seasons + season.seasons).distinctBy { it.seasonId },
            recommendations = if (seasonChanged) emptyList() else current.recommendations,
            recommendationsLoading = seasonChanged || current.recommendationsLoading,
            recommendationsError = if (seasonChanged) null else current.recommendationsError,
        )
    }

    fun setDescending(descending: Boolean) {
        cancelEpisodeNavigation()
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        _uiState.value = current.copy(isDescending = descending)
    }

    fun cancelEpisodeNavigation() {
        ++navigationGeneration
        navigationJob?.cancel()
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        _uiState.value = current.copy(isNavigatingEpisode = false, episodeNavigationPrevious = null)
    }

    fun selectPreviousEpisode() = selectAdjacentEpisode(before = true)

    fun selectNextEpisode() = selectAdjacentEpisode(before = false)

    private fun selectAdjacentEpisode(before: Boolean) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        if (!current.episodeNavigationEnabled) return
        if (before && !current.hasPreviousEpisode || !before && !current.hasNextEpisode) return
        val generation = ++navigationGeneration
        navigationJob?.cancel()
        _uiState.value = current.copy(isNavigatingEpisode = true)
        navigationJob = viewModelScope.launch {
            try {
                val adjacent = current.adjacentEpisode(before)
                if (adjacent != null) {
                    commitEpisode(current.media, adjacent.episodeId, before)
                    return@launch
                }
                for (summary in current.adjacentSeasons(before)) {
                    val season = loadSeasonData(summary.seasonId)
                    if (generation != navigationGeneration) return@launch
                    val episodes = season.episodes.let { if (current.isDescending) it.reversed() else it }
                    val episode = if (before) episodes.lastOrNull { it.isAvailable } else episodes.firstOrNull { it.isAvailable }
                    if (episode != null) {
                        commitEpisode(season, episode.episodeId, before)
                        return@launch
                    }
                }
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                _uiState.value = latest.copy(isNavigatingEpisode = false)
                showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != navigationGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                _uiState.value = latest.copy(isNavigatingEpisode = false)
                showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
            }
        }
    }

    fun onEpisodePlaybackResult(episodeId: Long, failed: Boolean) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        if (current.episode?.episodeId != episodeId) return
        val before = current.episodeNavigationPrevious ?: return
        _uiState.value = current.copy(episodeNavigationPrevious = null)
        if (failed) showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
    }

    fun loadRecommendations(selectedSeasonId: Long) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        if (current.media.seasonId != selectedSeasonId) return
        recommendationsJob?.cancel()
        val generation = ++recommendationsGeneration
        _uiState.value = current.copy(recommendationsLoading = true, recommendationsError = null)
        recommendationsJob = viewModelScope.launch {
            try {
                val response = fetchRelatedMedia(selectedSeasonId)
                if (generation != recommendationsGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                if (latest.media.seasonId != selectedSeasonId) return@launch
                _uiState.value = latest.copy(
                    recommendations = if (response.isSuccess) response.media else emptyList(),
                    recommendationsLoading = false,
                    recommendationsError = if (response.isSuccess) null else response.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != recommendationsGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                if (latest.media.seasonId != selectedSeasonId) return@launch
                _uiState.value = latest.copy(
                    recommendationsLoading = false,
                    recommendationsError = e.message ?: "媒体推荐加载失败",
                )
            }
        }
    }
}
