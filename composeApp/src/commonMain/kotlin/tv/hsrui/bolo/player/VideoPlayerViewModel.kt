package tv.hsrui.bolo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.player.base.BoloPlayerController
import tv.hsrui.bolo.player.base.BoloPlayerError
import tv.hsrui.bolo.player.base.load
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.fetchVideoPlayInfo

class VideoPlayerViewModel(var avid: Long, var cid: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentVideoQuality = MutableStateFlow(VideoQuality.best)
    val currentVideoQuality = _currentVideoQuality.asStateFlow()

    var videoQuality: VideoQuality = VideoQuality.best
    var videoCodec: VideoCodec = VideoCodec.HEVC
    var audioQuality: AudioQuality? = AudioQuality.best

    var isLoading: Boolean = false

    val controller = BoloPlayerController(onError = { e ->
        when (e) {
            is BoloPlayerError.NetworkError -> println("网络错误: ${e.message}")
            is BoloPlayerError.DecoderError -> println("解码: ${e.message}")
            is BoloPlayerError.FormatNotSupported -> println("格式不支持: ${e.message}")
            is BoloPlayerError.UnknownError -> println("未知错误: ${e.message}")
        }
    })

    init {
        viewModelScope.launch {
            loadVideo()
            playVideo()
        }
    }

    private fun playVideo(startPosition: Int = 0) {
        val currentState = uiState.value
        if (currentState !is VideoPlayerUiState.Success) return

        val video = currentState.videoSource.getVideo(quality = videoQuality, codec = videoCodec)
        val audio = currentState.videoSource.getAudio(quality = audioQuality)

        videoQuality = video.quality as VideoQuality
        videoCodec = video.codec
        audioQuality = audio?.let { it.quality as AudioQuality }
        _currentVideoQuality.value = videoQuality

        controller.load(video = video, audio = audio, startPosition = startPosition)
    }

    fun switchQuality(newVideoQuality: VideoQuality) {
        videoQuality = newVideoQuality
        playVideo(controller.state.value.currentPosition)
    }

    suspend fun fetchPlayInfo(): VideoSource {
        return fetchVideoPlayInfo(
            avid = avid,
            cid = cid
        )
    }

    suspend fun loadVideo() {
        try {
            val result = fetchPlayInfo()
            if (result.isSuccess) {
                _uiState.value = VideoPlayerUiState.Success(result)
            } else {
                _uiState.value = VideoPlayerUiState.Error(result.message)
            }
        } catch (e: Exception) {
            _uiState.value = VideoPlayerUiState.Error(e.message ?: "其他网络错误")
        }
    }

    override fun onCleared() {
        controller.dispose()
        super.onCleared()
    }
}
