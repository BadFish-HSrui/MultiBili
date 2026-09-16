package tv.hsrui.bolo.userSpace.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.video.collection.fetchVideoCollection

class UserCollectionViewModel(private val mid: Long, private val seasonId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<UserCollectionUiState>(UserCollectionUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0

    init { loadCollection() }

    fun loadCollection() {
        val version = ++generation
        loadJob?.cancel()
        val previous = _uiState.value as? UserCollectionUiState.Success
        _uiState.value = previous?.copy(isRefreshing = true, refreshError = null) ?: UserCollectionUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val collection = fetchVideoCollection(mid, seasonId)
                if (version != generation) return@launch
                val selected = (_uiState.value as? UserCollectionUiState.Success)?.selectedSectionId
                _uiState.value = UserCollectionUiState.Success(
                    collection = collection,
                    selectedSectionId = selected?.takeIf { id -> collection.sections.any { it.sectionId == id } }
                        ?: collection.sections.firstOrNull()?.sectionId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                val message = e.message ?: "合集加载失败"
                val current = _uiState.value as? UserCollectionUiState.Success
                _uiState.value = current?.copy(isRefreshing = false, refreshError = message) ?: UserCollectionUiState.Error(message)
            }
        }
    }

    fun selectSection(sectionId: Long) {
        val state = _uiState.value as? UserCollectionUiState.Success ?: return
        if (state.collection.sections.any { it.sectionId == sectionId }) {
            _uiState.value = state.copy(selectedSectionId = sectionId)
        }
    }
}
