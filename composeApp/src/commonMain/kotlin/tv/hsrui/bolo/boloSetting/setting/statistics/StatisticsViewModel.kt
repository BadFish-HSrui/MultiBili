package tv.hsrui.bolo.boloSetting.setting.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveStatisticsSnapshot
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSnapshot
import tv.hsrui.bolo.storage.appData.playbackStatistics.addPlaybackStatistics
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.utils.file.LocalFileAccess
import kotlin.time.Clock

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val snapshot: PlaybackStatisticsSnapshot? = null,
    val liveSnapshot: LiveStatisticsSnapshot? = null,
    val days: List<StatisticsDailyStats> = emptyList(),
    val error: String? = null,
    val hasUnsavedPlayback: Boolean = false,
)

data class StatisticsDailyStats(
    val localDate: String,
    val videoPlayedMs: Long = 0L,
    val livePlayedMs: Long = 0L,
    val videoPlayCount: Long = 0L,
    val livePlayCount: Long = 0L,
) {
    val totalPlayedMs: Long = addPlaybackStatistics(videoPlayedMs, livePlayedMs)
    val hasPlayback: Boolean get() = totalPlayedMs > 0L || videoPlayCount > 0L || livePlayCount > 0L
}

class StatisticsViewModel(
    private val appData: AppDataStorage = getKoin().get(),
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StatisticsUiState())
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private val files = LocalFileAccess()
    private val mutableDataOperationRunning = MutableStateFlow(false)
    val dataOperationRunning = mutableDataOperationRunning.asStateFlow()
    private val mutableAwaitingRestoreConfirmation = MutableStateFlow(false)
    val awaitingRestoreConfirmation = mutableAwaitingRestoreConfirmation.asStateFlow()
    private val mutableAwaitingClearConfirmation = MutableStateFlow(false)
    val awaitingClearConfirmation = mutableAwaitingClearConfirmation.asStateFlow()
    private var dataOperationJob: Job? = null
    private var dataConfirmation: CompletableDeferred<Boolean>? = null

    fun exportData() = runDataOperation("数据导出失败") {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val time = listOf(now.hour, now.minute, now.second).joinToString("-") { it.toString().padStart(2, '0') }
        val name = "multibili-statistics-${now.date}-$time.db"
        val path = files.temporaryPath(name)
        try {
            appData.statistics.exportBackup(path)
            val saved = files.saveFile(path, name) ?: return@runDataOperation
            currentCoroutineContext().ensureActive()
            val opened = files.showFileInFolder(saved)
            showSnackbarMessage(if (opened) "数据已导出" else "数据已导出，无法打开所在文件夹")
        } finally {
            releaseTemporaryFile(path)
        }
    }

    fun restoreData(onRestored: () -> Unit) = runDataOperation("数据恢复失败") {
        val path = files.importFile() ?: return@runDataOperation
        try {
            appData.statistics.validateBackup(path)
            if (!awaitConfirmation(mutableAwaitingRestoreConfirmation)) return@runDataOperation
            appData.statistics.restoreBackup(path)
            currentCoroutineContext().ensureActive()
            onRestored()
            showSnackbarMessage("数据已恢复")
        } finally {
            releaseTemporaryFile(path)
        }
    }

    fun clearData(onCleared: () -> Unit) = runDataOperation("数据清除失败") {
        if (!awaitConfirmation(mutableAwaitingClearConfirmation)) return@runDataOperation
        appData.statistics.clear()
        currentCoroutineContext().ensureActive()
        onCleared()
        showSnackbarMessage("数据已清除")
    }

    private suspend fun awaitConfirmation(awaiting: MutableStateFlow<Boolean>): Boolean {
        currentCoroutineContext().ensureActive()
        val confirmation = CompletableDeferred<Boolean>()
        dataConfirmation = confirmation
        awaiting.value = true
        return try { confirmation.await() } finally {
            awaiting.value = false
            dataConfirmation = null
        }
    }

    fun confirmDataOperation(confirmed: Boolean) {
        dataConfirmation?.complete(confirmed)
    }

    private fun runDataOperation(failureMessage: String, action: suspend () -> Unit) {
        if (mutableDataOperationRunning.value) return
        mutableDataOperationRunning.value = true
        dataOperationJob = viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                showSnackbarMessage("$failureMessage：${error.message ?: "未知错误"}")
            } finally {
                mutableDataOperationRunning.value = false
            }
        }
    }

    private suspend fun releaseTemporaryFile(path: String) = withContext(NonCancellable) {
        // 清理失败不改变已完成的导出或恢复结果。
        runCatching { files.deleteTemporaryFile(path) }.onFailure { println("统计临时文件清理失败：${it.message}") }
    }

    fun cancelDataOperation() {
        dataOperationJob?.cancel()
    }

    /** 进入页面或读取失败后重试；统一读取近 30 天，查看期间复用同一快照。 */
    fun load() {
        if (loadJob?.isActive == true) return
        mutableUiState.value = StatisticsUiState()
        loadJob = viewModelScope.launch {
            try {
                var hasUnsavedPlayback = false
                try {
                    appData.statistics.flush()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    hasUnsavedPlayback = true
                }
                val dayCount = 30
                val end = today().plus(1, DateTimeUnit.DAY)
                val start = end.minus(dayCount, DateTimeUnit.DAY)
                val snapshot = appData.playbackStatistics.statistics(start, end, includeRankings = false)
                val liveSnapshot = appData.liveStatistics.statistics(start, end, includeRankings = false)
                addPlaybackStatistics(snapshot.summary.totalPlayedMs, liveSnapshot.summary.totalPlayedMs)
                val byDate = snapshot.dailyStats.associateBy { it.localDate }
                val liveByDate = liveSnapshot.dailyStats.associateBy { it.localDate }
                val days = List(dayCount) { offset ->
                    val date = start.plus(offset, DateTimeUnit.DAY).toString()
                    StatisticsDailyStats(
                        localDate = date,
                        videoPlayedMs = byDate[date]?.totalPlayedMs ?: 0L,
                        livePlayedMs = liveByDate[date]?.totalPlayedMs ?: 0L,
                        videoPlayCount = byDate[date]?.playCount ?: 0L,
                        livePlayCount = liveByDate[date]?.playCount ?: 0L,
                    )
                }
                // 在发布 UI 状态前检查整个区间，避免趋势组件中出现溢出。
                days.fold(0L) { sum, day -> addPlaybackStatistics(sum, day.totalPlayedMs) }
                days.fold(0L) { sum, day -> addPlaybackStatistics(sum, day.videoPlayCount) }
                days.fold(0L) { sum, day -> addPlaybackStatistics(sum, day.livePlayCount) }
                currentCoroutineContext().ensureActive()
                mutableUiState.value = StatisticsUiState(
                    isLoading = false, snapshot = snapshot, liveSnapshot = liveSnapshot,
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
