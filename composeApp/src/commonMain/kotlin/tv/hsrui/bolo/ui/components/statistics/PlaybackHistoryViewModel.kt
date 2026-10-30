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
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveHistoryCursor
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveHistorySort
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveHistoryType
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryCursor
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistorySort
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryType

enum class StatisticsHistoryType { Videos, Ups, LiveRooms }

data class StatisticsHistoryItem(
    val id: Long,
    val title: String,
    val totalPlayedMs: Long,
    val playCount: Long,
    val lastViewedAtMs: Long,
)

data class PlaybackHistoryUiState(
    val items: List<StatisticsHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val loadMoreError: String? = null,
    val hasUnsavedPlayback: Boolean = false,
    val sort: PlaybackHistorySort = PlaybackHistorySort.Time,
    val descendingBySort: Map<PlaybackHistorySort, Boolean> = PlaybackHistorySort.entries.associateWith { true },
) {
    val descending: Boolean get() = descendingBySort.getValue(sort)
}

class PlaybackHistoryViewModel(
    private val type: StatisticsHistoryType,
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
        val state = mutableUiState.value
        mutableUiState.value = PlaybackHistoryUiState(sort = state.sort, descendingBySort = state.descendingBySort)
        loadPage(append = false)
    }

    fun selectSort(sort: PlaybackHistorySort) {
        val state = mutableUiState.value
        mutableUiState.value = state.copy(
            sort = sort,
            descendingBySort = if (sort == state.sort) {
                state.descendingBySort + (sort to !state.descending)
            } else state.descendingBySort,
        )
        load()
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
        val state = mutableUiState.value
        loadJob = viewModelScope.launch {
            try {
                var hasUnsavedPlayback = state.hasUnsavedPlayback
                if (!append) {
                    try {
                        appData.statistics.flush()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        hasUnsavedPlayback = true
                    }
                }
                val page = readPage(pageCursor, state.sort, state.descending)
                currentCoroutineContext().ensureActive()
                if (version != requestVersion) return@launch
                page.lastOrNull()?.let {
                    val sortValue = when (state.sort) {
                        PlaybackHistorySort.Time -> it.lastViewedAtMs
                        PlaybackHistorySort.PlayCount -> it.playCount
                        PlaybackHistorySort.Duration -> it.totalPlayedMs
                    }
                    cursor = PlaybackHistoryCursor(sortValue, it.id)
                }
                mutableUiState.value = state.copy(
                    items = if (append) (state.items + page).distinctBy { it.id } else page,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = page.size == pageSize,
                    error = null,
                    loadMoreError = null,
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

    private suspend fun readPage(
        pageCursor: PlaybackHistoryCursor?,
        sort: PlaybackHistorySort,
        descending: Boolean,
    ): List<StatisticsHistoryItem> {
        if (type == StatisticsHistoryType.LiveRooms) {
            return appData.liveStatistics.historyPage(
                type = LiveHistoryType.Rooms,
                cursor = pageCursor?.let { LiveHistoryCursor(it.sortValue, it.id) },
                limit = pageSize,
                sort = when (sort) {
                    PlaybackHistorySort.Time -> LiveHistorySort.Time
                    PlaybackHistorySort.PlayCount -> LiveHistorySort.PlayCount
                    PlaybackHistorySort.Duration -> LiveHistorySort.Duration
                },
                descending = descending,
            ).map {
                StatisticsHistoryItem(
                    id = it.id,
                    title = formatLiveStatisticsTitle(it.id, it.shortRoomId, it.upName),
                    totalPlayedMs = it.totalPlayedMs,
                    playCount = it.playCount,
                    lastViewedAtMs = it.lastViewedAtMs,
                )
            }
        }
        return appData.playbackStatistics.historyPage(
            type = if (type == StatisticsHistoryType.Videos) PlaybackHistoryType.Videos else PlaybackHistoryType.Ups,
            cursor = pageCursor,
            limit = pageSize,
            sort = sort,
            descending = descending,
        ).map {
            val title = it.title?.takeIf(String::isNotBlank)
                ?: if (type == StatisticsHistoryType.Videos) "av${it.id}" else "UP ${it.id}"
            StatisticsHistoryItem(
                id = it.id,
                title = if (it.partTitle.isNullOrBlank()) title else "$title · ${it.partTitle}",
                totalPlayedMs = it.totalPlayedMs,
                playCount = it.playCount,
                lastViewedAtMs = it.lastViewedAtMs,
            )
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

internal fun formatLiveStatisticsTitle(roomId: Long, shortRoomId: Long?, upName: String): String =
    "${shortRoomId?.takeIf { it > 0L } ?: roomId} · $upName"
