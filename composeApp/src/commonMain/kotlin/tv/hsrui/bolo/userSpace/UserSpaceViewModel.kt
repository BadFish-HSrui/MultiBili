package tv.hsrui.bolo.userSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.feature.favorite.fetchFavoriteFolders
import tv.hsrui.network.feature.user.space.UserSpacePrivacyData
import tv.hsrui.network.feature.user.space.fetchUserSpaceCollections
import tv.hsrui.network.feature.user.space.fetchUserSpaceCoins
import tv.hsrui.network.feature.user.space.fetchUserSpaceLikes
import tv.hsrui.network.feature.user.space.fetchUserSpacePrivacy
import tv.hsrui.network.feature.user.space.fetchUserSpaceUploads
import tv.hsrui.network.login.storage.LoginStorage

class UserSpaceViewModel(private val mid: Long, private val loginStorage: LoginStorage) : ViewModel() {
    private val _uiState = MutableStateFlow(UserSpaceUiState())
    val uiState = _uiState.asStateFlow()
    private var requestVersion = 0
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init { refreshSpace() }

    fun refreshSpace() {
        val version = ++requestVersion
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update { it.copy(refreshGeneration = version, isRefreshing = true, isLoadingMore = false, loadMoreError = null) }
        refreshJob = viewModelScope.launch {
            try {
                supervisorScope {
                    launch { loadUploads(version) }
                    launch { loadCollections(version) }
                    val privacy = loadPrivacy()
                    val isOwner = loginStorage.isLoggedIn && loginStorage.cookies.dedeUserID == mid
                    launch { loadLikes(version, !isOwner && privacy?.showsLikes == false) }
                    launch { loadCoins(version, !isOwner && privacy?.showsCoins == false) }
                    launch { loadFavorites(version, !isOwner && privacy?.showsFavorites == false) }
                }
            } finally {
                if (version == requestVersion) _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun refreshTab() = refreshSpace()

    private suspend fun loadPrivacy(): UserSpacePrivacyData? = try {
        fetchUserSpacePrivacy(mid).privacy
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // 权限未知时由各内容接口判定，不将请求失败视为隐藏。
    }

    private suspend fun loadUploads(version: Int, page: Int = 1) {
        try {
            val result = fetchUserSpaceUploads(mid, page)
            check(result.isSuccess) { result.message.ifBlank { "视频投稿加载失败（${result.code}）" } }
            val videos = result.videos.filter { it.avid > 0 }.distinctBy { it.avid }
            check(videos.isNotEmpty() || result.total == 0) { "投稿列表为空，但接口总数不为零，请重试" }
            if (version != requestVersion) return
            _uiState.update {
                val previous = (it.uploads as? UserSpaceSectionState.Success)?.data.orEmpty()
                it.copy(
                    uploads = UserSpaceSectionState.Success(if (page == 1) videos else (previous + videos).distinctBy { v -> v.avid }),
                    uploadPage = page, canLoadMore = result.hasMore, loadMoreError = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (version != requestVersion) return
            val message = e.message ?: "视频投稿加载失败"
            _uiState.update {
                if (page == 1) it.copy(uploads = UserSpaceSectionState.Error(message), canLoadMore = false)
                else it.copy(loadMoreError = message)
            }
        }
    }

    fun loadMoreUploads() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore || !state.canLoadMore || state.uploads !is UserSpaceSectionState.Success) return
        val version = requestVersion
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        loadMoreJob = viewModelScope.launch {
            try { loadUploads(version, state.uploadPage + 1) }
            finally { if (version == requestVersion) _uiState.update { it.copy(isLoadingMore = false) } }
        }
    }

    private suspend fun loadCollections(version: Int) {
        val state = try {
            val result = fetchUserSpaceCollections(mid)
            check(result.isSuccess) { result.message.ifBlank { "视频合集加载失败" } }
            UserSpaceSectionState.Success((result.total ?: 0) > 0)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { UserSpaceSectionState.Error(e.message ?: "视频合集加载失败") }
        if (version == requestVersion) _uiState.update { it.copy(collections = state) }
    }

    private suspend fun loadLikes(version: Int, hidden: Boolean) {
        val state = try {
            if (hidden) UserSpaceSectionState.Hidden else {
                val result = fetchUserSpaceLikes(mid)
                if (result.isHidden) UserSpaceSectionState.Hidden else {
                    check(result.isSuccess) { result.message.ifBlank { "最近点赞加载失败" } }
                    UserSpaceSectionState.Success(result.videos.filter { it.avid > 0 }.distinctBy { it.avid })
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { UserSpaceSectionState.Error(e.message ?: "最近点赞加载失败") }
        if (version == requestVersion) _uiState.update { it.copy(likes = state) }
    }

    private suspend fun loadCoins(version: Int, hidden: Boolean) {
        val state = try {
            if (hidden) UserSpaceSectionState.Hidden else {
                val result = fetchUserSpaceCoins(mid)
                if (result.isHidden) UserSpaceSectionState.Hidden else {
                    check(result.isSuccess) { result.message.ifBlank { "最近投币加载失败" } }
                    UserSpaceSectionState.Success(result.videos.filter { it.avid > 0 }.distinctBy { it.avid })
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { UserSpaceSectionState.Error(e.message ?: "最近投币加载失败") }
        if (version == requestVersion) _uiState.update { it.copy(coins = state) }
    }

    private suspend fun loadFavorites(version: Int, hidden: Boolean) {
        val state = try {
            if (hidden) UserSpaceSectionState.Hidden else {
                val result = withTimeoutOrNull(30_000) { fetchFavoriteFolders(mid) } ?: error("收藏夹请求超时")
                if (result.isHidden) UserSpaceSectionState.Hidden else {
                    check(result.isSuccess) { result.message.ifBlank { "收藏夹加载失败" } }
                    UserSpaceSectionState.Success(result.folders)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { UserSpaceSectionState.Error(e.message ?: "收藏夹加载失败") }
        if (version == requestVersion) _uiState.update { it.copy(favorites = state) }
    }
}
