package tv.hsrui.bolo.view.video.desc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.feature.video.tags.fetchVideoTags

class VideoTagsViewModel(private val avid: Long) : ViewModel() {
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags = _tags.asStateFlow()

    init {
        loadTags()
    }

    private fun loadTags() {
        if (avid <= 0) return
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(15_000) { fetchVideoTags(avid) }
                _tags.value = response?.tags.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _tags.value = emptyList()
            }
        }
    }
}
