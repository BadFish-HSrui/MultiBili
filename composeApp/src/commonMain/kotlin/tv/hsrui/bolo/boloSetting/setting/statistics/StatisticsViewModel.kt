package tv.hsrui.bolo.boloSetting.setting.statistics

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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackDailyStats
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSnapshot
import kotlin.time.Clock

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val snapshot: PlaybackStatisticsSnapshot? = null,
    val days: List<PlaybackDailyStats> = emptyList(),
    val error: String? = null,
    val hasUnsavedPlayback: Boolean = false,
)

class StatisticsViewModel(
    private val appData: AppDataStorage = getKoin().get(),
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StatisticsUiState())
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    /** 进入页面或读取失败后重试；统一读取近 30 天，查看期间复用同一快照。 */
    fun load() {
        if (loadJob?.isActive == true) return
        mutableUiState.value = StatisticsUiState()
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
                val dayCount = 30
                val end = today().plus(1, DateTimeUnit.DAY)
                val start = end.minus(dayCount, DateTimeUnit.DAY)
                val snapshot = appData.playbackStatistics.statistics(start, end, includeRankings = false)
                val byDate = snapshot.dailyStats.associateBy { it.localDate }
                val days = List(dayCount) { offset ->
                    val date = start.plus(offset, DateTimeUnit.DAY).toString()
                    byDate[date] ?: PlaybackDailyStats(localDate = date)
                }
                currentCoroutineContext().ensureActive()
                mutableUiState.value = StatisticsUiState(
                    isLoading = false, snapshot = snapshot,
                    days = days, hasUnsavedPlayback = hasUnsavedPlayback,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false, error = error.message ?: "统计数据读取失败",
                )
            }
        }
    }

    fun cancelLoading() {
        loadJob?.cancel()
        loadJob = null
    }
}
