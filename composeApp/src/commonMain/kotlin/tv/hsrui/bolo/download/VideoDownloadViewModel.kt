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
    val selectedTargets: Set<String> = emptySet(),
)

class VideoDownloadViewModel(
    private val group: DownloadGroup,
    private val mainTitle: String,
    targets: List<DownloadTarget>,
    private val settings: PlaybackSettings,
    private val loginStorage: LoginStorage,
    private val manager: DownloadManager,
) : ViewModel() {
    val targets = targets.distinctBy { it.key }.sortedBy { it.number }
    private val _state = MutableStateFlow(VideoDownloadUiState(selectedTargets = this.targets.mapTo(mutableSetOf()) { it.key }))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private var loadedSession: String? = null

    init { loadPlayInfo() }

    fun loadPlayInfo() {
        if (_state.value.isSubmitting) return
        loadJob?.cancel()
        val request = ++generation
        val session = loginStorage.cookies.sessData
        loadedSession = null
        _state.value = VideoDownloadUiState(selectedTargets = _state.value.selectedTargets)
        loadJob = viewModelScope.launch {
            try {
                check(loginStorage.isLoggedIn) { "请先登录" }
                val first = targets.firstOrNull() ?: error("没有可下载的内容")
                val source = withTimeout(15_000) {
                    when (group.type) {
                        DownloadType.Video -> fetchVideoPlayInfo(first.id, first.cid)
                        DownloadType.Media -> fetchMediaPlayInfo(first.id)
                    }
                }
                if (request != generation) return@launch
                check(session == loginStorage.cookies.sessData) { "登录状态已变化，请重试" }
                val selected = resolveDownloadStreams(source, DownloadSpec(
                    settings.defaultVideoQuality.code, settings.defaultVideoCodec.code, settings.defaultAudioQuality.code,
                ))
                loadedSession = session
                _state.update { it.copy(isLoading = false, source = source, spec = selected.spec) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (request == generation) _state.update { it.copy(isLoading = false,
                    error = if (error is IllegalStateException) error.message else "播放信息获取失败，请重试") }
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        val current = _state.value
        if (current.isSubmitting) return
        val source = current.source ?: return
        val spec = current.spec ?: return
        if (source.availableVideoCodecs(quality).isEmpty()) return
        _state.value = current.copy(spec = resolveDownloadStreams(source, spec.copy(videoQualityCode = quality.code)).spec, error = null)
    }
    fun selectCodec(codec: VideoCodec) {
        _state.update { current ->
            if (current.isSubmitting) return@update current
            val spec = current.spec ?: return@update current
            if (codec !in current.source?.availableVideoCodecs(spec.videoQuality).orEmpty()) current
            else current.copy(spec = spec.copy(videoCodecCode = codec.code), error = null)
        }
    }
    fun selectAudio(audio: AudioQuality) {
        _state.update { current ->
            if (current.isSubmitting) return@update current
            if (current.source?.getExactAudio(audio) == null) current
            else current.copy(spec = current.spec?.copy(audioQualityCode = audio.code), error = null)
        }
    }
    fun toggleTarget(key: String) {
        if (targets.none { it.key == key }) return
        _state.update { current ->
            if (current.isSubmitting) current else current.copy(selectedTargets =
                if (key in current.selectedTargets) current.selectedTargets - key else current.selectedTargets + key)
        }
    }
    fun selectAll(selected: Boolean) {
        _state.update { current ->
            if (current.isSubmitting) current else current.copy(selectedTargets =
                if (selected) targets.mapTo(mutableSetOf()) { it.key } else emptySet())
        }
    }
    fun submit() {
        val current = _state.value
        val spec = current.spec ?: return
        if (current.source == null || current.isSubmitting || current.isLoading || current.selectedTargets.isEmpty()) return
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                check(loadedSession == loginStorage.cookies.sessData) { "登录状态已变化，请重新获取规格" }
                val count = manager.enqueueBatch(group, mainTitle, targets.filter { it.key in current.selectedTargets }, spec)
                _state.update { it.copy(isSubmitting = false, submitted = count > 0,
                    error = if (count == 0) "已存在相同下载任务" else null) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(isSubmitting = false, error = error.message ?: "创建下载任务失败") } }
        }
    }
}
