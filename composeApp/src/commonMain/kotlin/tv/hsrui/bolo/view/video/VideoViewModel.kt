package tv.hsrui.bolo.view.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.video.fetchVideoInfo

class VideoViewModel(private val bvid: String) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadVideoInfo()
    }

    fun loadVideoInfo() {
        _uiState.value = VideoUiState.Loading
        viewModelScope.launch {
            try {
                val result = fetchVideoInfo(bvid)
                if (result.isSuccess) {
                    _uiState.value = VideoUiState.Success(result.data)
                } else {
                    _uiState.value = VideoUiState.Error("[${result.code}]: ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.value = VideoUiState.Error(e.message ?: "其他网络错误")
            }
        }
    }
}