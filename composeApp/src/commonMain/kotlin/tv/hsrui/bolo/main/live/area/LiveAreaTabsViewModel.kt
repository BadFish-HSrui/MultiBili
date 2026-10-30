package tv.hsrui.bolo.main.live.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.liveFeature.area.LiveAreaData
import tv.hsrui.network.liveFeature.area.fetchLiveAreaList
import tv.hsrui.network.liveFeature.area.fetchLiveSubAreaList

sealed interface LiveAreaTabsUiState {
    data object Loading : LiveAreaTabsUiState
    data class Success(val parentTitle: String, val areas: List<LiveAreaData>) : LiveAreaTabsUiState
    data class Error(val message: String) : LiveAreaTabsUiState
}

class LiveAreaTabsViewModel(private val parentAreaId: Int) : ViewModel() {
    private val _uiState = MutableStateFlow<LiveAreaTabsUiState>(LiveAreaTabsUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null

    init {
        refreshAreas()
    }

    fun refreshAreas() {
        requestJob?.cancel()
        _uiState.value = LiveAreaTabsUiState.Loading
        requestJob = viewModelScope.launch {
            try {
                val result = fetchLiveSubAreaList(parentAreaId)
                check(result.isSuccess) { result.message.ifBlank { "子分区加载失败（${result.code}）" } }
                val parentTitle = result.areas.firstOrNull { it.parentTitle.isNotBlank() }?.parentTitle ?: run {
                    val directory = fetchLiveAreaList()
                    check(directory.isSuccess) { directory.message.ifBlank { "分区名称加载失败（${directory.code}）" } }
                    directory.parentAreas.firstOrNull { it.id == parentAreaId }?.title.orEmpty()
                }
                currentCoroutineContext().ensureActive()
                _uiState.value = LiveAreaTabsUiState.Success(
                    parentTitle = parentTitle.ifBlank { "直播分区" },
                    areas = result.areas.filter { it.id > 0 }.distinctBy { it.id },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.value = LiveAreaTabsUiState.Error(error.message ?: "子分区加载失败")
            }
        }
    }
}
