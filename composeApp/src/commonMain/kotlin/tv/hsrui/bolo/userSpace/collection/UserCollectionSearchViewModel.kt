package tv.hsrui.bolo.userSpace.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.feature.video.collection.fetchVideoCollection
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.VideoCard

data class UserCollectionSearchUiState(
    val keyword: String = "",
    val videos: List<VideoCard> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val revision: Int = 0,
)

class UserCollectionSearchViewModel(
    private val mid: Long,
    private val seasonId: Long,
    private val loginStorage: LoginStorage = getKoin().get(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserCollectionSearchUiState())
    val uiState = _uiState.asStateFlow()
    private var currentUserMid = readCurrentUserMid()
    private var requestVersion = 0
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect {
                val identity = readCurrentUserMid()
                if (identity != currentUserMid) {
                    currentUserMid = identity
                    requestVersion++
                    requestJob?.cancel()
                    val state = _uiState.value
                    _uiState.value = UserCollectionSearchUiState(revision = state.revision + 1)
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
        _uiState.value = UserCollectionSearchUiState(keyword = keyword, revision = _uiState.value.revision + 1)
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
                check(mid > 0 && seasonId > 0) { "合集信息无效" }
                val collection = fetchVideoCollection(mid, seasonId)
                if (version != requestVersion || identity != readCurrentUserMid()) return@launch
                val videos = collection.sections.flatMap { it.episodes }
                    .filter { it.isAvailable && it.bvid.isNotBlank() }
                    .map { it.videoCard }
                    .distinctBy { it.avid }
                    .filter { it.title.contains(keyword, ignoreCase = true) }
                _uiState.value = _uiState.value.copy(videos = videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion && identity == readCurrentUserMid()) {
                    _uiState.value = _uiState.value.copy(error = e.message ?: "合集搜索失败")
                }
            } finally {
                if (version == requestVersion) _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }
}
