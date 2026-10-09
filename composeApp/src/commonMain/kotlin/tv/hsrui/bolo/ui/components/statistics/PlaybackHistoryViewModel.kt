package tv.hsrui.bolo.ui.components.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryCursor
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryItem
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryType

data class PlaybackHistoryUiState(
    val items: List<PlaybackHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val loadMoreError: String? = null,
    val hasUnsavedPlayback: Boolean = false,
)

class PlaybackHistoryViewModel(
    private val type: PlaybackHistoryType,
    private val appData: AppDataStorage = getKoin().get(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlaybackHistoryUiState())
    val uiState = mutableUiState.asStateFlow()
    private val pageSize = 50
    private var cursor: PlaybackHistoryCursor? = null
    private var loadJob: Job? = null
    private var requestVersion = 0L

    fun load() {
        cancelLoading()
        cursor = null
        mutableUiState.value = PlaybackHistoryUiState()
        loadPage(append = false)
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.error != null) return
        mutableUiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val version = ++requestVersion
        val pageCursor = if (append) cursor else null
        loadJob = viewModelScope.launch {
            try {
                var hasUnsavedPlayback = mutableUiState.value.hasUnsavedPlayback
                if (!append) {
                    try {
                        appData.playbackStatistics.flush()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        hasUnsavedPlayback = true
                    }
                }
                val page = appData.playbackStatistics.historyPage(type, pageCursor, pageSize)
                currentCoroutineContext().ensureActive()
                if (version != requestVersion) return@launch
                page.lastOrNull()?.let { cursor = PlaybackHistoryCursor(it.lastViewedAtMs, it.id) }
                mutableUiState.value = PlaybackHistoryUiState(
                    items = if (append) (mutableUiState.value.items + page).distinctBy { it.id } else page,
                    isLoading = false,
                    hasMore = page.size == pageSize,
                    hasUnsavedPlayback = hasUnsavedPlayback,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (version == requestVersion) {
                    val message = error.message ?: "观看记录读取失败"
                    mutableUiState.value = if (append) {
                        mutableUiState.value.copy(isLoadingMore = false, loadMoreError = message)
                    } else mutableUiState.value.copy(isLoading = false, error = message)
                }
            } finally {
                if (version == requestVersion) loadJob = null
            }
        }
    }

    fun cancelLoading() {
        requestVersion++
        loadJob?.cancel()
        loadJob = null
    }

    override fun onCleared() {
        cancelLoading()
        super.onCleared()
    }
}
