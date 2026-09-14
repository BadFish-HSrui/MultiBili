package tv.hsrui.bolo.view.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.media.fetchMediaSeason
import tv.hsrui.network.feature.media.fetchRelatedMedia

class MediaPlaybackViewModel(private val seasonId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<MediaPlaybackUiState>(MediaPlaybackUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val seasons = mutableMapOf<Long, MediaSeasonData>()
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
                val response = fetchMediaSeason(selectedSeasonId)
                if (generation != seasonGeneration) return@launch
                val latest = _uiState.value as? MediaPlaybackUiState.Success ?: return@launch
                val season = response.season
                if (response.isSuccess && season?.seasonId == selectedSeasonId) {
                    seasons[selectedSeasonId] = season
                    _uiState.value = latest.copy(browsedSeason = season, seasonLoading = false)
                } else {
                    _uiState.value = latest.copy(
                        seasonLoading = false,
                        seasonError = if (response.isSuccess) "返回的分季信息不匹配" else response.message,
                    )
                }
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
        val episode = season.episodes.firstOrNull { it.episodeId == episodeId && it.isAvailable } ?: return
        if (current.episode?.episodeId == episodeId) return
        val seasonChanged = current.media.seasonId != season.seasonId
        _uiState.value = current.copy(
            media = season,
            episode = episode,
            seasons = (current.seasons + season.seasons).distinctBy { it.seasonId },
            recommendations = if (seasonChanged) emptyList() else current.recommendations,
            recommendationsLoading = seasonChanged || current.recommendationsLoading,
            recommendationsError = if (seasonChanged) null else current.recommendationsError,
        )
    }

    fun setDescending(descending: Boolean) {
        val current = _uiState.value as? MediaPlaybackUiState.Success ?: return
        _uiState.value = current.copy(isDescending = descending)
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
