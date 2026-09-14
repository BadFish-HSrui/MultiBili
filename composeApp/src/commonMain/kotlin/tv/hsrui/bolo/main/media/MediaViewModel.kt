package tv.hsrui.bolo.main.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.media.MediaFilterSelection
import tv.hsrui.network.feature.media.fetchMediaConditions
import tv.hsrui.network.feature.media.fetchMediaIndex

class MediaViewModel(private val seasonType: Int = 1) : ViewModel() {
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _filterUiState = MutableStateFlow<MediaFilterUiState>(MediaFilterUiState.Loading)
    val filterUiState = _filterUiState.asStateFlow()
    private val _appliedFilters = MutableStateFlow(MediaFilterSelection())
    val appliedFilters = _appliedFilters.asStateFlow()

    private var pageNumber = 0
    private var requestJob: Job? = null
    private var requestVersion = 0
    private var conditionsJob: Job? = null

    init {
        refreshMedia()
    }

    fun loadMediaConditions() {
        if (conditionsJob?.isActive == true || _filterUiState.value is MediaFilterUiState.Success) return
        _filterUiState.value = MediaFilterUiState.Loading
        conditionsJob = viewModelScope.launch {
            try {
                val result = fetchMediaConditions(seasonType = seasonType)
                val conditions = result.conditions
                _filterUiState.value = if (result.isSuccess && conditions != null) {
                    MediaFilterUiState.Success(conditions)
                } else {
                    MediaFilterUiState.Error(result.message.ifBlank { "筛选条件加载失败" })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _filterUiState.value = MediaFilterUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun applyMediaFilters(selection: MediaFilterSelection) {
        val conditions = (_filterUiState.value as? MediaFilterUiState.Success)?.conditions ?: return
        _appliedFilters.value = selection.normalized(conditions)
        _uiState.value = MediaUiState.Loading
        refreshMedia()
    }

    fun refreshMedia() {
        val version = ++requestVersion
        requestJob?.cancel()
        pageNumber = 0
        val currentState = _uiState.value as? MediaUiState.Success
        _uiState.value = currentState?.copy(
            isRefreshing = true,
            isLoadingMore = false,
            loadMoreError = null,
        ) ?: MediaUiState.Loading
        val selection = _appliedFilters.value

        requestJob = viewModelScope.launch {
            try {
                val result = fetchMediaIndex(seasonType = seasonType, page = 1, selection = selection)
                if (version != requestVersion) return@launch
                if (result.isSuccess) {
                    pageNumber = 1
                    _uiState.value = MediaUiState.Success(
                        media = result.media.distinctBy { it.seasonId },
                        hasMore = result.hasMore,
                    )
                } else {
                    _uiState.value = MediaUiState.Error("[加载错误]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = MediaUiState.Error(e.message ?: "其他网络错误")
                }
            }
        }
    }

    fun loadMoreMedia() {
        val currentState = _uiState.value as? MediaUiState.Success ?: return
        if (currentState.isRefreshing || currentState.isLoadingMore || !currentState.hasMore) return

        val version = ++requestVersion
        val nextPage = pageNumber + 1
        val selection = _appliedFilters.value
        _uiState.value = currentState.copy(isLoadingMore = true, loadMoreError = null)
        requestJob = viewModelScope.launch {
            try {
                val result = fetchMediaIndex(seasonType = seasonType, page = nextPage, selection = selection)
                if (version != requestVersion) return@launch
                if (result.isSuccess) {
                    pageNumber = nextPage
                    _uiState.value = MediaUiState.Success(
                        media = (currentState.media + result.media).distinctBy { it.seasonId },
                        hasMore = result.hasMore,
                    )
                } else {
                    _uiState.value = currentState.copy(loadMoreError = "[加载错误]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = currentState.copy(loadMoreError = e.message ?: "其他网络错误")
                }
            }
        }
    }
}
