package tv.hsrui.bolo.view.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.parseExternalLink
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import tv.hsrui.bolo.player.session.BoloSystemMediaMetadata
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.fetchVideoInfo
import tv.hsrui.network.feature.video.collection.VideoCollectionEpisodeData
import tv.hsrui.network.feature.video.list.fetchVideoListInfo
import tv.hsrui.network.feature.video.list.fetchVideoListVideos
import tv.hsrui.network.feature.player.PlayerInfoResponse
import tv.hsrui.network.feature.player.fetchPlayerInfo
import tv.hsrui.network.login.storage.LoginStorage

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
    private var hasOpenedVideo = false
    private var hasBoundPlayback = false

    var playbackSession by mutableStateOf<BoloPlaybackSession?>(null)
        private set
    private var detailKey: Long? = null
    private val detailOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    init {
        loadPlayback()
    }

    // 页面数据属于导航条目；只有栈顶页面绑定可释放的播放会话。
    fun bindPlayback() {
        if (playbackSession?.player?.playbackClosed == false) return
        val session = BoloPlaybackSession.obtain(request.key)
        playbackSession = session
        val useInitialPlayerInfo = !hasBoundPlayback
        hasBoundPlayback = true
        session.bindPlayback {
            try {
                uiState.collectLatest { value ->
                    val state = value as? VideoUiState.Success ?: return@collectLatest
                    val video = state.video
                    val player = session.player
                    player.switchMedia(video.avid, video.cid, initialPlayerInfo = state.initialPlayerInfo.takeIf { useInitialPlayerInfo })
                    session.updateMedia(
                        BoloSystemMediaMetadata("${video.avid}:${video.cid}", video.title, video.upName, artworkUrl = video.coverUrl),
                        if (state.hasPreviousEpisode) ::selectPreviousEpisode else null,
                        if (state.hasNextEpisode) ::selectNextEpisode else null,
                        state.episodeNavigationEnabled,
                    )
                    val result = player.uiState.first { it !is VideoPlayerUiState.Loading }
                    onEpisodePlaybackResult(video.avid, video.cid, result is VideoPlayerUiState.Error)
                }
            } finally {
                if (playbackSession === session) {
                    playbackSession = null
                    cancelEpisodeNavigation()
                }
            }
        }
    }

    fun getDetailOwner(key: Long?): ViewModelStoreOwner {
        if (detailKey != key) {
            detailOwner.viewModelStore.clear()
            detailKey = key
        }
        return detailOwner
    }

    override fun onCleared() {
        playbackSession?.close()
        detailOwner.viewModelStore.clear()
    }

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
            _uiState.value = current.copy(isSwitchingEpisode = false, switchingEpisodeKey = null, episodeError = null, episodeNavigationPrevious = null)
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
        loadListPage(before)
    }

    private fun loadListPage(before: Boolean): Job? {
        val source = request as? VideoPlaybackRequest.VideoList ?: return null
        val state = listState ?: return null
        if (state.isReloading || state.reloadError != null) return null
        if (before && state.isLoadingPrevious || !before && state.isLoadingNext) {
            return if (before) previousJob else nextJob
        }
        if (before && !state.hasPrevious || !before && !state.hasNext) return null
        val cursor = (if (before) state.headCursor else state.tailCursor) ?: return null
        val version = listGeneration
        updateList { if (before) it.copy(isLoadingPrevious = true, previousError = null)
            else it.copy(isLoadingNext = true, nextError = null) }
        val job = viewModelScope.launch {
            try {
                check(cursor.id > 0 && cursor.cursorType > 0) { "无效的列表游标" }
                val page = withTimeoutOrNull(15_000) {
                    fetchVideoListVideos(source.type, source.id, source.sort, !state.isDescending,
                        cursorId = cursor.id, cursorType = cursor.cursorType, withCurrent = false, before = before)
                } ?: error("列表请求超时")
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
        return job
    }

    fun selectPreviousEpisode() = selectAdjacentEpisode(before = true)

    fun selectNextEpisode() = selectAdjacentEpisode(before = false)

    fun cancelEpisodeNavigation() {
        val current = _uiState.value as? VideoUiState.Success ?: return
        val navigating = current.isSwitchingEpisode && current.switchingEpisodeKey == null
        if (navigating) {
            ++generation
            loadJob?.cancel()
        }
        _uiState.value = current.copy(
            isSwitchingEpisode = if (navigating) false else current.isSwitchingEpisode,
            episodeNavigationPrevious = null,
        )
    }

    private fun selectAdjacentEpisode(before: Boolean) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (!current.episodeNavigationEnabled) return
        if (before && !current.hasPreviousEpisode || !before && !current.hasNextEpisode) return
        val version = ++generation
        loadJob?.cancel()
        val part = current.adjacentPart(before)
        if (part != null) {
            _uiState.value = current.copy(
                video = current.video.copy(cid = part),
                initialPlayerInfo = null,
                selectedSectionId = current.video.collection?.sections?.firstOrNull { section ->
                    section.episodes.any { it.avid == current.video.avid }
                }?.sectionId ?: current.selectedSectionId,
                switchingEpisodeKey = null, episodeError = null, episodeNavigationPrevious = before,
            )
            return
        }
        _uiState.value = current.copy(isSwitchingEpisode = true, switchingEpisodeKey = null, episodeError = null)
        loadJob = viewModelScope.launch {
            try {
                val targetAvid: Long
                var sectionId: Long? = null
                if (current.videoList != null) {
                    var latest = _uiState.value as? VideoUiState.Success ?: return@launch
                    var target = latest.adjacentListVideo(before)
                    while (target == null) {
                        val list = latest.videoList ?: return@launch
                        if (!(if (before) list.hasPrevious else list.hasNext)) break
                        val pageJob = checkNotNull(loadListPage(before)) { "无法加载列表" }
                        pageJob.join()
                        if (version != generation) return@launch
                        latest = _uiState.value as? VideoUiState.Success ?: return@launch
                        val updated = checkNotNull(latest.videoList)
                        check((if (before) updated.previousError else updated.nextError) == null) { "列表加载失败" }
                        check(updated.items.size > list.items.size || !(if (before) updated.hasPrevious else updated.hasNext)) {
                            "列表游标未推进"
                        }
                        target = latest.adjacentListVideo(before)
                    }
                    if (target == null) {
                        _uiState.value = latest.copy(isSwitchingEpisode = false)
                        showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
                        return@launch
                    }
                    targetAvid = target.id
                } else {
                    val target = current.adjacentCollectionEpisode(before)
                    if (target == null) {
                        val latest = _uiState.value as? VideoUiState.Success ?: return@launch
                        _uiState.value = latest.copy(isSwitchingEpisode = false)
                        return@launch
                    }
                    targetAvid = target.avid
                    sectionId = current.video.collection?.sections?.firstOrNull { section ->
                        section.episodes.any { it.key == target.key }
                    }?.sectionId
                }
                val loaded = loadVideo(Vid.AVid(targetAvid), version = version) ?: return@launch
                if (version != generation) return@launch
                // 从两个方向进入新稿件都从 P1 开始，不采用合集条目的 CID。
                val video = loaded.copy(
                    cid = loaded.parts.firstOrNull()?.cid ?: loaded.cid,
                    collection = if (current.videoList == null) current.video.collection else loaded.collection,
                )
                _uiState.value = VideoUiState.Success(
                    video = video,
                    isDescending = current.isDescending,
                    videoList = listState,
                    episodeNavigationPrevious = before,
                ).let { if (sectionId != null) it.copy(selectedSectionId = sectionId) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version != generation) return@launch
                val latest = _uiState.value as? VideoUiState.Success ?: return@launch
                _uiState.value = latest.copy(isSwitchingEpisode = false)
                showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
            }
        }
    }

    fun onEpisodePlaybackResult(avid: Long, cid: Long, failed: Boolean) {
        val current = _uiState.value as? VideoUiState.Success ?: return
        if (current.video.avid != avid || current.video.cid != cid) return
        val before = current.episodeNavigationPrevious ?: return
        _uiState.value = current.copy(episodeNavigationPrevious = null)
        if (failed) showSnackbarMessage(if (before) "无法加载上一集" else "无法加载下一集")
    }

    private suspend fun loadVideo(vid: Vid, cid: Long? = null, version: Int): VideoInfoData? {
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
        currentCoroutineContext().ensureActive()
        if (version != generation) return null
        val redirect = parseExternalLink(result.data.redirectUrl) as? BoloRoute.View.Media
        if (redirect != null) {
            _uiState.value = VideoUiState.RedirectToMedia(redirect)
            return null
        }
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
            previous.copy(switchingEpisodeKey = episode.key, isSwitchingEpisode = true, episodeError = null, episodeNavigationPrevious = null)
        } else VideoUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val originalVideo = loadVideo(vid, episode?.cid, version) ?: return@launch
                if (version != generation) return@launch
                var video = originalVideo
                var playerInfo: PlayerInfoResponse? = null
                if (!hasOpenedVideo) {
                    val settings = getKoin().get<BoloSettings>()
                    val loginStorage = getKoin().get<LoginStorage>()
                    val accountSession = loginStorage.cookies.sessData
                    val initialInfo = fetchPlayerInfo(video.avid, video.cid)
                    if (version != generation) return@launch
                    playerInfo = initialInfo
                    if (settings.playback.resumeFromHistoryEnabled &&
                        accountSession.isNotEmpty() && loginStorage.cookies.sessData == accountSession &&
                        initialInfo.matchesRequest(video.avid, video.cid, accountSession)
                    ) {
                        val historyPart = video.parts.firstOrNull { it.cid == initialInfo.lastPlayCid }
                        if (historyPart != null && historyPart.cid != video.cid) {
                            video = video.copy(cid = historyPart.cid)
                            // 字幕属于请求的 CID；只在实际换 P 时加载目标自己的播放器信息。
                            playerInfo = fetchPlayerInfo(video.avid, video.cid)
                        }
                    }
                    if (!settings.playback.resumeFromHistoryEnabled || loginStorage.cookies.sessData != accountSession) {
                        video = originalVideo
                        playerInfo = initialInfo
                    }
                }
                if (version != generation) return@launch
                val latest = _uiState.value as? VideoUiState.Success
                hasOpenedVideo = true
                _uiState.value = VideoUiState.Success(video = video, isDescending = latest?.isDescending ?: false,
                    videoList = listState, initialPlayerInfo = playerInfo)
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
            _uiState.value = current.copy(switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null, episodeNavigationPrevious = null)
            return
        }
        _uiState.value = current.copy(switchingEpisodeKey = key, isSwitchingEpisode = true, episodeError = null, episodeNavigationPrevious = null)
        loadJob = viewModelScope.launch {
            try {
                val video = loadVideo(Vid.AVid(item.id), version = version) ?: return@launch
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
            _uiState.value = current.copy(switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null, episodeNavigationPrevious = null)
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
            initialPlayerInfo = current.initialPlayerInfo.takeIf { current.video.cid == cid },
            switchingEpisodeKey = null, isSwitchingEpisode = false, episodeError = null,
            episodeNavigationPrevious = null,
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
        } else {
            ++generation
            loadJob?.cancel()
            _uiState.value = current.copy(isDescending = descending, isSwitchingEpisode = false,
                switchingEpisodeKey = null, episodeError = null, episodeNavigationPrevious = null)
        }
    }
}
