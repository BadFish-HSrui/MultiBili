package tv.hsrui.bolo.boloSetting.setting.statistics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val dayCount: Int = 7,
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
    private var requestVersion = 0L

    /** 由页面生命周期调用；离开页面或切换日期范围时取消读取。 */
    suspend fun load(dayCount: Int) {
        require(dayCount == 7 || dayCount == 30)
        val version = ++requestVersion
        val previous = mutableUiState.value
        mutableUiState.value = if (previous.dayCount == dayCount) {
            previous.copy(isLoading = true, error = null)
        } else StatisticsUiState(dayCount = dayCount)
        try {
            var hasUnsavedPlayback = false
            try {
                appData.playbackStatistics.flush()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                hasUnsavedPlayback = true
            }
            val end = today().plus(1, DateTimeUnit.DAY)
            val start = end.minus(dayCount, DateTimeUnit.DAY)
            val snapshot = appData.playbackStatistics.statistics(start, end, includeRankings = false)
            val byDate = snapshot.dailyStats.associateBy { it.localDate }
            val days = List(dayCount) { offset ->
                val date = start.plus(offset, DateTimeUnit.DAY).toString()
                byDate[date] ?: PlaybackDailyStats(localDate = date)
            }
            currentCoroutineContext().ensureActive()
            if (version == requestVersion) {
                mutableUiState.value = StatisticsUiState(
                    dayCount = dayCount, isLoading = false, snapshot = snapshot,
                    days = days, hasUnsavedPlayback = hasUnsavedPlayback,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (version == requestVersion) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false, error = error.message ?: "统计数据读取失败",
                )
            }
        }
    }
}
