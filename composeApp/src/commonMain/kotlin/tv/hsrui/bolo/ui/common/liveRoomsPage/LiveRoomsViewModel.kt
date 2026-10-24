package tv.hsrui.bolo.ui.common.liveRoomsPage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.model.LiveRoomsResult

abstract class LiveRoomsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LiveRoomsUiState>(LiveRoomsUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0

    private var hasRequested = false

    protected abstract suspend fun fetchRooms(page: Int): LiveRoomsResult
    protected open val loadErrorMessage: String = "直播加载失败"

    protected fun resetRooms(message: String) {
        requestVersion++
        requestJob?.cancel()
        requestJob = null
        hasRequested = false
        _uiState.value = LiveRoomsUiState.Error(message)
    }

    fun loadRooms() {
        if (!hasRequested) refreshRooms()
    }

    fun refreshRooms() {
        hasRequested = true
        val version = ++requestVersion
        requestJob?.cancel()
        val previous = _uiState.value as? LiveRoomsUiState.Success
        _uiState.value = previous?.copy(
            isRefreshing = true,
            refreshError = null,
            isLoadingMore = false,
            loadMoreError = null,
        ) ?: LiveRoomsUiState.Loading

        requestJob = viewModelScope.launch {
            try {
                val result = fetchRooms(page = 1)
                currentCoroutineContext().ensureActive()
                check(result.isSuccess) { result.message.ifBlank { "$loadErrorMessage（${result.code}）" } }
                if (version != requestVersion) return@launch
                _uiState.value = LiveRoomsUiState.Success(
                    rooms = result.rooms.distinctBy { it.roomId },
                    page = 1,
                    hasMore = result.hasMore,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                val message = error.message ?: loadErrorMessage
                _uiState.value = previous?.copy(
                    isRefreshing = false,
                    refreshError = message,
                    isLoadingMore = false,
                    loadMoreError = null,
                ) ?: LiveRoomsUiState.Error(message)
            }
        }
    }

    fun loadMoreRooms() {
        val current = _uiState.value as? LiveRoomsUiState.Success ?: return
        if (current.isRefreshing || current.isLoadingMore || !current.hasMore || current.refreshError != null) return
        val version = requestVersion
        val nextPage = current.page + 1
        _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)

        requestJob = viewModelScope.launch {
            try {
                val result = fetchRooms(page = nextPage)
                currentCoroutineContext().ensureActive()
                check(result.isSuccess) { result.message.ifBlank { "$loadErrorMessage（${result.code}）" } }
                if (version != requestVersion) return@launch
                _uiState.value = LiveRoomsUiState.Success(
                    rooms = (current.rooms + result.rooms).distinctBy { it.roomId },
                    page = nextPage,
                    hasMore = result.hasMore,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                _uiState.value = current.copy(
                    isLoadingMore = false,
                    loadMoreError = error.message ?: loadErrorMessage,
                )
            }
        }
    }
}
