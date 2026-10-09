package tv.hsrui.bolo.main.home.following

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.network.feature.dynamic.fetchFollowingVideoUpdates
import tv.hsrui.network.feature.dynamic.fetchFollowingVideos
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class FollowingVideosViewModel(
    private val loginStorage: LoginStorage = getKoin().get(),
    private val snackbarManager: SnackbarManager = getKoin().get(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<VideosUiState>(VideosUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _hasNewVideos = MutableStateFlow(false)
    val hasNewVideos = _hasNewVideos.asStateFlow()
    private val checkRevision = MutableStateFlow(0L)

    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    private var accountMid: Long? = null
    private var generation = 0L
    private var pageNumber = 1
    private var offset = ""
    private var updateBaseline = ""
    private var canLoadMore = false
    private var loginExpired = false
    private var listJob: Job? = null
    private var updateJob: Job? = null

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect { mid ->
                if (accountMid == mid) return@collect
                accountMid = mid
                generation++
                listJob?.cancel()
                updateJob?.cancel()
                pageNumber = 1
                offset = ""
                updateBaseline = ""
                canLoadMore = false
                loginExpired = false
                isLoading = false
                isRefreshing = false
                _hasNewVideos.value = false
                _uiState.value = if (mid > 0) VideosUiState.Loading else VideosUiState.Error("账号未登录")
                checkRevision.value++
                if (mid > 0) loadVideos()
            }
        }
    }

    fun loadVideos() = refreshVideos()

    fun refreshVideos() {
        if (isRefreshing || !isCurrentAccount()) return
        generation++
        listJob?.cancel()
        updateJob?.cancel()
        isLoading = true
        isRefreshing = true
        checkRevision.value++
        if (_uiState.value !is VideosUiState.Success) _uiState.value = VideosUiState.Loading
        val requestGeneration = generation
        listJob = viewModelScope.launch { requestVideos(1, "", requestGeneration) }
    }

    fun loadMoreVideos() {
        if (isLoading || !canLoadMore || loginExpired || !isCurrentAccount()) return
        isLoading = true
        val requestGeneration = generation
        val nextPage = pageNumber + 1
        val nextOffset = offset
        listJob = viewModelScope.launch { requestVideos(nextPage, nextOffset, requestGeneration) }
    }

    private suspend fun requestVideos(page: Int, requestOffset: String, requestGeneration: Long) {
        try {
            val result = fetchFollowingVideos(pn = page, offset = requestOffset)
            currentCoroutineContext().ensureActive()
            if (requestGeneration != generation || !isCurrentAccount()) return
            if (!result.isSuccess) {
                if (result.isLoginExpired) loginExpired = true
                showLoadError(result.message, page == 1)
                return
            }
            val previous = (_uiState.value as? VideosUiState.Success)?.videos.orEmpty()
            val videos = if (page == 1) result.validData.videosList else previous + result.validData.videosList
            _uiState.value = VideosUiState.Success(videos.distinctBy { it.avid })
            pageNumber = page
            offset = result.offset
            canLoadMore = result.validData.canLoadMore
            if (page == 1) {
                updateBaseline = result.updateBaseline
                _hasNewVideos.value = false
                loginExpired = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (requestGeneration == generation && isCurrentAccount()) {
                showLoadError(e.message ?: "其他网络错误", page == 1)
            }
        } finally {
            if (requestGeneration == generation) {
                isLoading = false
                isRefreshing = false
                if (page == 1 || loginExpired) checkRevision.value++
            }
        }
    }

    private fun showLoadError(message: String, refreshing: Boolean) {
        if (_uiState.value is VideosUiState.Success) {
            snackbarManager.showMessage((if (refreshing) "刷新失败：" else "加载失败：") + message)
        } else {
            _uiState.value = VideosUiState.Error("[加载错误]: $message")
        }
    }

    private fun isCurrentAccount(): Boolean =
        accountMid != null && accountMid != 0L && loginStorage.isLoggedIn &&
            loginStorage.cookies.dedeUserID == accountMid

    suspend fun runUpdateChecks() {
        var checkedAccount: Long? = null
        checkRevision.collectLatest {
            if (_hasNewVideos.value || loginExpired || (isRefreshing && updateBaseline.isNotBlank())) {
                checkedAccount = accountMid
            }
            if (!isCurrentAccount() || isRefreshing || loginExpired ||
                updateBaseline.isBlank() || _hasNewVideos.value
            ) return@collectLatest
            if (checkedAccount == accountMid) delay(30_000)
            checkedAccount = accountMid
            while (currentCoroutineContext().isActive) {
                val started = TimeSource.Monotonic.markNow()
                checkForUpdates()
                if (_hasNewVideos.value || loginExpired) return@collectLatest
                delay((30.seconds - started.elapsedNow()).coerceAtLeast(Duration.ZERO))
            }
        }
    }

    private suspend fun checkForUpdates() = coroutineScope {
        if (!isCurrentAccount() || isRefreshing || loginExpired ||
            updateBaseline.isBlank() || _hasNewVideos.value
        ) return@coroutineScope
        val requestGeneration = generation
        val baseline = updateBaseline
        val job = currentCoroutineContext().job
        updateJob = job
        try {
            val result = withTimeoutOrNull(15_000) { fetchFollowingVideoUpdates(baseline) }
                ?: return@coroutineScope
            currentCoroutineContext().ensureActive()
            if (requestGeneration != generation || !isCurrentAccount() || baseline != updateBaseline) {
                return@coroutineScope
            }
            if (result.isLoginExpired) loginExpired = true
            if (result.isSuccess && result.hasUpdates) _hasNewVideos.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 检测失败保留列表，在下一周期重试。
        } finally {
            if (updateJob === job) updateJob = null
        }
    }
}
