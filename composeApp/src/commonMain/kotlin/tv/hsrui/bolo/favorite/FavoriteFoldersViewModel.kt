package tv.hsrui.bolo.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.favorite.fetchMyFavoriteFolders

class FavoriteFoldersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<FavoriteFoldersUiState>(FavoriteFoldersUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refreshFolders()
    }

    fun refreshFolders() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = FavoriteFoldersUiState.Loading
            try {
                val result = fetchMyFavoriteFolders()
                _uiState.value = if (result.isSuccess) {
                    FavoriteFoldersUiState.Success(result.folders)
                } else {
                    FavoriteFoldersUiState.Error("[加载错误]: ${result.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = FavoriteFoldersUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }

    fun removeItem(id: Long) {
        val currentState = _uiState.value as? FavoriteFoldersUiState.Success ?: return
        _uiState.value = currentState.copy(
            folders = currentState.folders.filter { it.id != id },
        )
    }
}
