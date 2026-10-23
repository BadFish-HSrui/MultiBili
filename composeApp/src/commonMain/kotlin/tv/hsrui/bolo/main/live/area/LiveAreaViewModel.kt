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
import tv.hsrui.network.liveFeature.area.LiveAreaListResponse
import tv.hsrui.network.liveFeature.area.fetchLiveAreaList

class LiveAreaViewModel(
    private val fetchAreas: suspend () -> LiveAreaListResponse = ::fetchLiveAreaList,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LiveAreaUiState>(LiveAreaUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0

    init {
        refreshAreas()
    }

    fun refreshAreas() {
        val version = ++requestVersion
        requestJob?.cancel()
        val previous = _uiState.value as? LiveAreaUiState.Success
        _uiState.value = previous?.copy(
            isRefreshing = true,
            refreshError = null,
        ) ?: LiveAreaUiState.Loading

        requestJob = viewModelScope.launch {
            try {
                val result = fetchAreas()
                currentCoroutineContext().ensureActive()
                check(result.isSuccess) { result.message.ifBlank { "直播分区加载失败（${result.code}）" } }
                if (version != requestVersion) return@launch
                _uiState.value = LiveAreaUiState.Success(parentAreas = result.parentAreas)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                val message = error.message ?: "直播分区加载失败"
                _uiState.value = previous?.copy(
                    isRefreshing = false,
                    refreshError = message,
                ) ?: LiveAreaUiState.Error(message)
            }
        }
    }
}
