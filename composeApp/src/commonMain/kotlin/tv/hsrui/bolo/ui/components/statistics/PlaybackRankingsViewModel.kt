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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackRankings

data class PlaybackRankingsUiState(
    val isLoading: Boolean = true,
    val rankings: PlaybackRankings? = null,
    val error: String? = null,
    val hasUnsavedPlayback: Boolean = false,
)

class PlaybackRankingsViewModel(
    private val dateRange: ClosedRange<LocalDate>? = null,
    private val appData: AppDataStorage = getKoin().get(),
    private val limit: Int = 10,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlaybackRankingsUiState())
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        require(dateRange == null || !dateRange.isEmpty())
        require(limit > 0)
    }

    /** 由页面或弹层在进入时调用；日期范围包含首尾两天，null 表示累计总计。 */
    fun load() {
        if (loadJob?.isActive == true) return
        mutableUiState.value = PlaybackRankingsUiState()
        loadJob = viewModelScope.launch {
            try {
                var hasUnsavedPlayback = false
                try {
                    appData.playbackStatistics.flush()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    hasUnsavedPlayback = true
                }
                val rankings = appData.playbackStatistics.rankings(
                    fromDate = dateRange?.start,
                    untilDateExclusive = dateRange?.endInclusive?.plus(1, DateTimeUnit.DAY),
                    limit = limit,
                )
                currentCoroutineContext().ensureActive()
                mutableUiState.value = PlaybackRankingsUiState(
                    isLoading = false, rankings = rankings, hasUnsavedPlayback = hasUnsavedPlayback,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false, error = error.message ?: "排行榜读取失败",
                )
            }
        }
    }

    fun cancelLoading() {
        loadJob?.cancel()
        loadJob = null
    }
}
