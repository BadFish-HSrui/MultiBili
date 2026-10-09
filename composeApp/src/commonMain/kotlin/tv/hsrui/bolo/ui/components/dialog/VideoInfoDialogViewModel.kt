package tv.hsrui.bolo.ui.components.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.parseExternalLink
import tv.hsrui.network.feature.media.fetchMediaSeason
import tv.hsrui.network.feature.video.fetchVideoInfo

internal class VideoInfoDialogViewModel(private val vid: Vid) : ViewModel() {
    private val mutableUiState = MutableStateFlow<VideoInfoDialogUiState>(VideoInfoDialogUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var requestVersion = 0

    init {
        load()
    }

    fun load() {
        val version = ++requestVersion
        loadJob?.cancel()
        mutableUiState.value = VideoInfoDialogUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                require(when (vid) {
                    is Vid.AVid -> vid.value > 0
                    is Vid.BVid -> Regex("BV[0-9A-Za-z]{10}").matches(vid.value)
                }) { "视频编号无效" }
                val response = withTimeoutOrNull(15_000) {
                    when (vid) {
                        is Vid.AVid -> fetchVideoInfo(vid.value)
                        is Vid.BVid -> fetchVideoInfo(vid.value)
                    }
                } ?: error("视频详情请求超时")
                currentCoroutineContext().ensureActive()
                if (version != requestVersion) return@launch
                check(response.isSuccess) { "[${response.code}]: ${response.message.ifBlank { "视频信息加载失败" }}" }
                val video = response.data
                check(when (vid) {
                    is Vid.AVid -> video.avid == vid.value
                    is Vid.BVid -> video.bvid == vid.value
                }) { "视频信息不匹配，请重试" }
                check(video.avid > 0) { "视频响应缺少有效编号" }

                val state = if (video.redirectUrl.isBlank()) {
                    check(video.bvid.isNotBlank()) { "视频响应缺少 BV 号" }
                    VideoInfoDialogUiState.Video(video.toVideoCard())
                } else {
                    val route = parseExternalLink(video.redirectUrl) as? BoloRoute.View.Media
                        ?: error("无法识别番剧影视信息")
                    val mediaResponse = withTimeoutOrNull(15_000) {
                        fetchMediaSeason(seasonId = route.seasonId, episodeId = route.episodeId)
                    } ?: error("媒体详情请求超时")
                    currentCoroutineContext().ensureActive()
                    if (version != requestVersion) return@launch
                    check(mediaResponse.isSuccess) { mediaResponse.message }
                    val media = checkNotNull(mediaResponse.season) { "媒体响应缺少剧集信息" }
                    check(route.seasonId <= 0 || media.seasonId == route.seasonId) { "媒体信息不匹配，请重试" }
                    val episode = media.episodes.firstOrNull {
                        if (route.episodeId > 0) it.episodeId == route.episodeId else it.avid == video.avid
                    }
                    check(episode == null || episode.avid <= 0 || episode.avid == video.avid) {
                        "媒体信息不匹配，请重试"
                    }
                    VideoInfoDialogUiState.Media(
                        info = media.toMediaCard(episode),
                        episodeId = episode?.episodeId ?: route.episodeId,
                    )
                }
                currentCoroutineContext().ensureActive()
                if (version == requestVersion) mutableUiState.value = state
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version == requestVersion) {
                    mutableUiState.value = VideoInfoDialogUiState.Error(error.message ?: "视频信息加载失败")
                }
            }
        }
    }

    override fun onCleared() {
        requestVersion++
        loadJob?.cancel()
        super.onCleared()
    }
}
