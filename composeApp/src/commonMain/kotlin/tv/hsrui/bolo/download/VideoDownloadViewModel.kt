package tv.hsrui.bolo.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tv.hsrui.bolo.boloSetting.setting.playback.PlaybackSettings
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.player.fetchMediaPlayInfo
import tv.hsrui.network.feature.player.fetchVideoPlayInfo
import tv.hsrui.network.login.storage.LoginStorage

data class VideoDownloadUiState(
    val isLoading: Boolean = true,
    val source: VideoSource? = null,
    val spec: DownloadSpec? = null,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
)

class VideoDownloadViewModel(
    private val id: Long,
    private val cid: Long,
    private val title: String,
    private val settings: PlaybackSettings,
    private val loginStorage: LoginStorage,
    private val manager: DownloadManager,
    private val type: DownloadType = DownloadType.Video,
) : ViewModel() {
    private val _state = MutableStateFlow(VideoDownloadUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L

    init { loadPlayInfo() }

    fun loadPlayInfo() {
        loadJob?.cancel()
        val request = ++generation
        val session = loginStorage.cookies.sessData
        _state.value = VideoDownloadUiState()
        loadJob = viewModelScope.launch {
            try {
                check(loginStorage.isLoggedIn) { "请先登录" }
                val source = withTimeout(15_000) {
                    when (type) {
                        DownloadType.Video -> fetchVideoPlayInfo(id, cid)
                        DownloadType.Media -> fetchMediaPlayInfo(id)
                    }
                }
                if (request != generation) return@launch
                check(session == loginStorage.cookies.sessData) { "登录状态已变化，请重试" }
                check(source.isSuccess) { source.message }
                check(!source.isPreview) { "试看内容不支持完整下载" }
                val qualities = source.videoQualities.filter { source.availableVideoCodecs(it).isNotEmpty() }
                val quality = settings.defaultVideoQuality.takeIf { it in qualities } ?: qualities.firstOrNull()
                    ?: error("没有可下载的视频规格")
                val codecs = source.availableVideoCodecs(quality)
                val codec = settings.defaultVideoCodec.takeIf { it in codecs } ?: codecs.first()
                val audioQualities = source.audioQualities.filter { source.getExactAudio(it) != null }
                check(source.audioQualities.isEmpty() || audioQualities.isNotEmpty()) { "没有可下载的音频规格" }
                val audio = settings.defaultAudioQuality.takeIf { it in audioQualities } ?: audioQualities.firstOrNull()
                _state.value = VideoDownloadUiState(isLoading = false, source = source, spec = DownloadSpec(quality.code, codec.code, audio?.code))
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (request == generation) _state.value = VideoDownloadUiState(isLoading = false,
                    error = if (error is IllegalStateException) error.message else "播放信息获取失败，请重试")
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        val current = _state.value
        val source = current.source ?: return
        val spec = current.spec ?: return
        val codecs = source.availableVideoCodecs(quality)
        val codec = spec.videoCodec.takeIf { it in codecs } ?: codecs.firstOrNull() ?: return
        _state.value = current.copy(spec = spec.copy(videoQualityCode = quality.code, videoCodecCode = codec.code), error = null)
    }
    fun selectCodec(codec: VideoCodec) {
        _state.update { current ->
            val spec = current.spec ?: return@update current
            if (codec !in current.source?.availableVideoCodecs(spec.videoQuality).orEmpty()) current
            else current.copy(spec = spec.copy(videoCodecCode = codec.code), error = null)
        }
    }
    fun selectAudio(audio: AudioQuality) {
        _state.update { current ->
            if (current.source?.getExactAudio(audio) == null) current
            else current.copy(spec = current.spec?.copy(audioQualityCode = audio.code), error = null)
        }
    }
    fun submit() {
        val current = _state.value
        val spec = current.spec ?: return
        val source = current.source ?: return
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val submitted = manager.enqueue(DownloadRequest(id, cid, title, spec, type), source)
                _state.update { it.copy(isSubmitting = false, submitted = submitted) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(isSubmitting = false, error = error.message ?: "创建下载任务失败") } }
        }
    }
}
