package tv.hsrui.bolo.view.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.bolo.model.Vid
import tv.hsrui.network.feature.video.fetchVideoInfo
import tv.hsrui.network.feature.video.collection.VideoCollectionEpisodeData

class VideoViewModel(private val vid: Vid) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0

    init {
        loadVideoInfo()
    }

    fun loadVideoInfo(episode: VideoCollectionEpisodeData? = null) {
        val version = ++generation
        loadJob?.cancel()
        val previous = _uiState.value as? VideoUiState.Success
        _uiState.value = if (episode != null && previous != null) {
            previous.copy(switchingEpisodeKey = episode.key, isSwitchingEpisode = true, episodeError = null)
        } else VideoUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15_000) {
                    if (episode != null) fetchVideoInfo(episode.avid) else when (vid) {
                        is Vid.AVid -> fetchVideoInfo(vid.value)
                        is Vid.BVid -> fetchVideoInfo(vid.value)
                    }
                } ?: error("视频详情请求超时")
                check(result.isSuccess) { "[${result.code}]: ${result.message}" }
                if (episode != null) check(result.data.avid == episode.avid) { "视频信息不匹配，请重试" }
                if (version != generation) return@launch
                val latest = _uiState.value as? VideoUiState.Success
                val video = if (episode != null) result.data.copy(cid = episode.cid) else result.data
                _uiState.value = VideoUiState.Success(video = video, isDescending = latest?.isDescending ?: false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                val message = e.message ?: "其他网络错误"
                val current = _uiState.value as? VideoUiState.Success
                _uiState.value = if (episode != null && current != null) {
                    current.copy(isSwitchingEpisode = false, episodeError = message)
                } else VideoUiState.Error(message)
            }
        }
    }

    fun selectCollectionEpisode(key: String) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        val episode = current.video.collection?.sections?.flatMap { it.episodes }?.firstOrNull { it.key == key && it.isAvailable } ?: return
        if (episode.avid == current.video.avid && episode.cid == current.video.cid) {
            ++generation
            loadJob?.cancel()
            _uiState.value = current.copy(switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null)
            return
        }
        loadVideoInfo(episode)
    }

    fun selectSection(sectionId: Long) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.video.collection?.sections?.any { it.sectionId == sectionId } == true) {
            _uiState.value = current.copy(selectedSectionId = sectionId)
        }
    }

    fun setDescending(descending: Boolean) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        _uiState.value = current.copy(isDescending = descending)
    }
}
