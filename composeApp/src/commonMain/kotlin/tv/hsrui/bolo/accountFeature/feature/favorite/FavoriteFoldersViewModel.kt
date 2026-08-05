package tv.hsrui.bolo.accountFeature.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.favorite.fetchCreatedFavoriteFolders

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
                val result = fetchCreatedFavoriteFolders()
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
}
