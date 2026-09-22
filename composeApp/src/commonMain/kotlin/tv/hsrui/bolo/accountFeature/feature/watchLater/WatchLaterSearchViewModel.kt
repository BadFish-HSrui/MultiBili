package tv.hsrui.bolo.accountFeature.feature.watchLater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.feature.history.HistoryVideoCard
import tv.hsrui.network.feature.watchLater.deleteWatchLater
import tv.hsrui.network.feature.watchLater.fetchWatchLaterVideos
import tv.hsrui.network.login.storage.LoginStorage

data class WatchLaterSearchUiState(
    val keyword: String = "",
    val accountMid: Long = 0,
    val videos: List<HistoryVideoCard> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val revision: Int = 0,
)

class WatchLaterSearchViewModel(
    private val loginStorage: LoginStorage = getKoin().get(),
) : ViewModel() {
    private var currentUserMid = readCurrentUserMid()
    private val _uiState = MutableStateFlow(WatchLaterSearchUiState(
        accountMid = currentUserMid,
        error = if (currentUserMid > 0) null else "账号未登录",
    ))
    val uiState = _uiState.asStateFlow()
    private var requestVersion = 0
    private var accountVersion = 0
    private var requestJob: Job? = null
    private val removedIds = mutableSetOf<Long>()
    private val deletingIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect {
                val identity = readCurrentUserMid()
                if (identity != currentUserMid) {
                    currentUserMid = identity
                    accountVersion++
                    requestVersion++
                    requestJob?.cancel()
                    removedIds.clear()
                    deletingIds.clear()
                    val state = _uiState.value
                    _uiState.value = WatchLaterSearchUiState(
                        accountMid = identity,
                        revision = state.revision + 1,
                        error = if (identity > 0) null else "账号未登录",
                    )
                    if (state.keyword.isNotEmpty()) search(state.keyword)
                }
            }
        }
    }

    private fun readCurrentUserMid(): Long =
        if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L

    fun search(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        requestVersion++
        requestJob?.cancel()
        _uiState.value = WatchLaterSearchUiState(
            keyword = keyword, accountMid = currentUserMid, revision = _uiState.value.revision + 1,
        )
        if (currentUserMid <= 0 || currentUserMid != readCurrentUserMid()) {
            _uiState.value = _uiState.value.copy(error = "账号未登录")
            return
        }
        loadVideos()
    }

    fun refreshVideos() = search(_uiState.value.keyword)

    private fun loadVideos() {
        val version = ++requestVersion
        val identity = currentUserMid
        val keyword = _uiState.value.keyword
        _uiState.value = _uiState.value.copy(isSearching = true)
        requestJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15_000) { fetchWatchLaterVideos() } ?: error("搜索请求超时")
                if (version != requestVersion || identity != readCurrentUserMid()) return@launch
                check(result.isSuccess) { result.message.ifBlank { "稍后再看搜索失败" } }
                val videos = result.validData.list.filter { video ->
                    video.avid > 0 && video.avid !in removedIds &&
                        (video.title.contains(keyword, ignoreCase = true) || video.upName.contains(keyword, ignoreCase = true))
                }.distinctBy { it.avid }
                _uiState.value = _uiState.value.copy(videos = videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion && identity == readCurrentUserMid()) {
                    _uiState.value = _uiState.value.copy(error = e.message ?: "稍后再看搜索失败")
                }
            } finally {
                if (version == requestVersion) _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    suspend fun deleteVideo(avid: Long, accountMid: Long): String? {
        if (accountMid <= 0 || accountMid != currentUserMid || accountMid != readCurrentUserMid()) return "账号已变化，请重新搜索"
        if (avid <= 0 || avid in removedIds || !deletingIds.add(avid)) return null
        val identityVersion = accountVersion
        return try {
            val result = withTimeoutOrNull(15_000) { deleteWatchLater(avid) } ?: error("删除稍后再看请求超时")
            if (identityVersion != accountVersion || accountMid != readCurrentUserMid()) return null
            if (result.isSuccess) {
                removedIds += avid
                _uiState.value = _uiState.value.copy(videos = _uiState.value.videos.filter { it.avid != avid })
                null
            } else result.message.ifBlank { "删除稍后再看失败" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (identityVersion == accountVersion && accountMid == readCurrentUserMid()) e.message ?: "删除稍后再看失败"
            else null
        } finally {
            if (identityVersion == accountVersion) deletingIds -= avid
        }
    }
}
