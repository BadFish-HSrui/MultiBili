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
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.fetchVideoInfo
import tv.hsrui.network.feature.video.collection.VideoCollectionEpisodeData
import tv.hsrui.network.feature.video.list.fetchVideoListInfo
import tv.hsrui.network.feature.video.list.fetchVideoListVideos

class VideoViewModel(private val request: VideoPlaybackRequest) : ViewModel() {
    constructor(vid: Vid) : this(VideoPlaybackRequest.Single(vid))

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0
    private var listJob: Job? = null
    private var previousJob: Job? = null
    private var nextJob: Job? = null
    private var listGeneration = 0
    private var listState: VideoListUiState? = null

    init { loadPlayback() }

    fun loadPlayback() {
        if (request is VideoPlaybackRequest.VideoList) loadVideoList() else loadVideoInfo()
    }

    private fun updateList(transform: (VideoListUiState) -> VideoListUiState) {
        val updated = listState?.let(transform) ?: return
        listState = updated
        val current = _uiState.value as? VideoUiState.Success ?: return
        _uiState.value = current.copy(videoList = updated)
    }

    fun loadVideoList(descending: Boolean = listState?.isDescending ?: false) {
        val source = request as? VideoPlaybackRequest.VideoList ?: return
        val version = ++listGeneration
        listJob?.cancel()
        previousJob?.cancel()
        nextJob?.cancel()
        ++generation
        loadJob?.cancel()
        val current = _uiState.value as? VideoUiState.Success
        val anchor = current?.video?.avid
        if (current != null) {
            _uiState.value = current.copy(isSwitchingEpisode = false, switchingEpisodeKey = null, episodeError = null)
            updateList { it.copy(isReloading = true, pendingDescending = descending, reloadError = null,
                isLoadingPrevious = false, isLoadingNext = false) }
        } else {
            listState = null
            _uiState.value = VideoUiState.Loading
        }
        listJob = viewModelScope.launch {
            try {
                check(source.isValid) { "无效的列表参数" }
                val title = listState?.title ?: fetchVideoListInfo(source.type, source.id).title.ifBlank { "视频列表" }
                var page = fetchVideoListVideos(source.type, source.id, source.sort, !descending, cursorId = anchor ?: 0)
                var items = page.items.distinctBy { it.key }
                var tail = page.items.lastOrNull()
                var head = page.items.firstOrNull()
                var hasPrevious = false
                check(page.items.isNotEmpty() || page.hasMore == false) { "列表游标未推进，请重试" }
                if (anchor != null) {
                    check(items.any { it.isVideo && it.id == anchor }) { "当前视频已不在列表中，请重试" }
                    val previous = fetchVideoListVideos(source.type, source.id, source.sort, !descending,
                        cursorId = anchor, withCurrent = false, before = true)
                    check(previous.items.isNotEmpty() || previous.hasMore == false) { "列表游标未推进，请重试" }
                    items = (previous.items + items).distinctBy { it.key }
                    head = previous.items.firstOrNull() ?: head
                    hasPrevious = previous.hasMore == true
                } else {
                    // 原始资源可能全是失效稿件或非视频，继续推进游标寻找首个可播放稿件。
                    while (items.none { it.isAvailable } && page.hasMore == true) {
                        val cursor = checkNotNull(tail) { "列表游标未推进，请重试" }
                        check(cursor.id > 0 && cursor.cursorType > 0) { "无效的列表游标" }
                        val next = fetchVideoListVideos(source.type, source.id, source.sort, !descending,
                            cursorId = cursor.id, cursorType = cursor.cursorType, withCurrent = false)
                        val merged = (items + next.items).distinctBy { it.key }
                        check(next.items.isEmpty() && next.hasMore == false ||
                            merged.size > items.size && next.items.lastOrNull()?.key != cursor.key) { "列表游标未推进，请重试" }
                        items = merged
                        tail = next.items.lastOrNull() ?: tail
                        page = next
                    }
                }
                if (version != listGeneration) return@launch
                val updated = VideoListUiState(title = title, total = page.total ?: 0, items = items,
                    headCursor = head, tailCursor = tail, hasPrevious = hasPrevious, hasNext = page.hasMore == true,
                    isDescending = descending, revision = (listState?.revision ?: 0) + 1)
                listState = updated
                if (current != null) {
                    val latest = _uiState.value as? VideoUiState.Success ?: return@launch
                    _uiState.value = latest.copy(videoList = updated)
                } else {
                    val first = items.firstOrNull { it.isAvailable }
                    if (first == null) _uiState.value = VideoUiState.Empty
                    else loadVideoInfo()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != listGeneration) return@launch
                val message = e.message ?: "列表加载失败"
                if (_uiState.value is VideoUiState.Success) {
                    updateList { it.copy(isReloading = false, reloadError = message) }
                } else _uiState.value = VideoUiState.Error(message)
            }
        }
    }

    fun loadMoreListVideos(before: Boolean = false) {
        val source = request as? VideoPlaybackRequest.VideoList ?: return
        val state = listState ?: return
        if (state.isReloading || state.reloadError != null) return
        if (before && (!state.hasPrevious || state.isLoadingPrevious) ||
            !before && (!state.hasNext || state.isLoadingNext)) return
        val cursor = (if (before) state.headCursor else state.tailCursor) ?: return
        val version = listGeneration
        updateList { if (before) it.copy(isLoadingPrevious = true, previousError = null)
            else it.copy(isLoadingNext = true, nextError = null) }
        val job = viewModelScope.launch {
            try {
                check(cursor.id > 0 && cursor.cursorType > 0) { "无效的列表游标" }
                val page = fetchVideoListVideos(source.type, source.id, source.sort, !state.isDescending,
                    cursorId = cursor.id, cursorType = cursor.cursorType, withCurrent = false, before = before)
                if (version != listGeneration) return@launch
                val latest = listState ?: return@launch
                val merged = (if (before) page.items + latest.items else latest.items + page.items).distinctBy { it.key }
                val boundary = (if (before) page.items.firstOrNull() else page.items.lastOrNull()) ?: cursor
                check(page.items.isEmpty() && page.hasMore == false ||
                    merged.size > latest.items.size && boundary.key != cursor.key) { "列表游标未推进，请重试" }
                updateList {
                    if (before) it.copy(items = merged, total = page.total ?: it.total, headCursor = boundary,
                        hasPrevious = page.hasMore == true, isLoadingPrevious = false, previousError = null)
                    else it.copy(items = merged, total = page.total ?: it.total, tailCursor = boundary,
                        hasNext = page.hasMore == true, isLoadingNext = false, nextError = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != listGeneration) return@launch
                updateList { if (before) it.copy(isLoadingPrevious = false, previousError = e.message ?: "列表加载失败")
                    else it.copy(isLoadingNext = false, nextError = e.message ?: "列表加载失败") }
            }
        }
        if (before) previousJob = job else nextJob = job
    }

    private suspend fun loadVideo(vid: Vid, cid: Long? = null): VideoInfoData {
        val result = withTimeoutOrNull(15_000) {
            when (vid) {
                is Vid.AVid -> fetchVideoInfo(vid.value)
                is Vid.BVid -> fetchVideoInfo(vid.value)
            }
        } ?: error("视频详情请求超时")
        check(result.isSuccess) { "[${result.code}]: ${result.message}" }
        check(when (vid) {
            is Vid.AVid -> result.data.avid == vid.value
            is Vid.BVid -> result.data.bvid == vid.value
        }) { "视频信息不匹配，请重试" }
        check(result.data.avid > 0 && result.data.cid > 0) { "视频暂无可播放内容" }
        return if (cid != null) result.data.copy(cid = cid) else result.data
    }

    fun loadVideoInfo(episode: VideoCollectionEpisodeData? = null) {
        val vid = episode?.let { Vid.AVid(it.avid) }
            ?: (request as? VideoPlaybackRequest.Single)?.vid
            ?: listState?.items?.firstOrNull { it.isAvailable }?.let { Vid.AVid(it.id) }
            ?: return
        val version = ++generation
        loadJob?.cancel()
        val previous = _uiState.value as? VideoUiState.Success
        _uiState.value = if (episode != null && previous != null) {
            previous.copy(switchingEpisodeKey = episode.key, isSwitchingEpisode = true, episodeError = null)
        } else VideoUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val video = loadVideo(vid, episode?.cid)
                if (version != generation) return@launch
                val latest = _uiState.value as? VideoUiState.Success
                _uiState.value = VideoUiState.Success(video = video, isDescending = latest?.isDescending ?: false, videoList = listState)
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

    fun selectListVideo(key: String) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (listState?.isReloading == true) return
        val item = listState?.items?.firstOrNull { it.key == key && it.isAvailable } ?: return
        val version = ++generation
        loadJob?.cancel()
        if (item.id == current.video.avid) {
            _uiState.value = current.copy(switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null)
            return
        }
        _uiState.value = current.copy(switchingEpisodeKey = key, isSwitchingEpisode = true, episodeError = null)
        loadJob = viewModelScope.launch {
            try {
                val video = loadVideo(Vid.AVid(item.id))
                if (version != generation) return@launch
                _uiState.value = VideoUiState.Success(video = video, videoList = listState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                val latest = _uiState.value as? VideoUiState.Success ?: return@launch
                _uiState.value = latest.copy(isSwitchingEpisode = false, episodeError = e.message ?: "视频加载失败")
            }
        }
    }

    fun selectCollectionEpisode(key: String) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.videoList != null) return
        val episode = current.video.collection?.sections?.flatMap { it.episodes }?.firstOrNull { it.key == key && it.isAvailable } ?: return
        if (episode.avid == current.video.avid) {
            ++generation
            loadJob?.cancel()
            _uiState.value = current.copy(switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null)
            return
        }
        loadVideoInfo(episode)
    }

    fun selectVideoPart(cid: Long) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.video.parts.none { it.cid == cid }) return
        ++generation
        loadJob?.cancel()
        _uiState.value = current.copy(
            video = if (current.video.cid == cid) current.video else current.video.copy(cid = cid),
            switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null,
        )
    }

    fun selectSection(sectionId: Long) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.video.collection?.sections?.any { it.sectionId == sectionId } == true) {
            _uiState.value = current.copy(selectedSectionId = sectionId)
        }
    }

    fun setDescending(descending: Boolean) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.videoList != null) {
            if (current.videoList.isReloading || current.videoList.isDescending != descending || current.videoList.reloadError != null) {
                loadVideoList(descending)
            }
        } else _uiState.value = current.copy(isDescending = descending)
    }
}
