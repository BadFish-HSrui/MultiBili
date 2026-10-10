package tv.hsrui.bolo.ui.components.statistics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var requestVersion = 0L

    init {
        require(dateRange == null || !dateRange.isEmpty())
        require(limit > 0)
    }

    /** 由组件生命周期调用；日期范围包含首尾两天，null 表示累计总计。 */
    suspend fun load() {
        val version = ++requestVersion
        mutableUiState.value = mutableUiState.value.copy(isLoading = true, error = null)
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
            if (version == requestVersion) {
                mutableUiState.value = PlaybackRankingsUiState(
                    isLoading = false, rankings = rankings, hasUnsavedPlayback = hasUnsavedPlayback,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (version == requestVersion) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false, error = error.message ?: "排行榜读取失败",
                )
            }
        }
    }
}
