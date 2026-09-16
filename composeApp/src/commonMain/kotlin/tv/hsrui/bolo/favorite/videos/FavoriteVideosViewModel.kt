package tv.hsrui.bolo.favorite.videos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.favorite.FavoriteVideoCard
import tv.hsrui.network.feature.favorite.fetchFavoriteFolderContent

class FavoriteVideosViewModel(private val mediaId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<FavoriteVideosUiState>(FavoriteVideosUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    private var canLoadMore: Boolean = false
    private var pageNumber: Int = 0
    private var requestJob: Job? = null
    private var requestVersion: Int = 0

    init {
        refreshVideos()
    }

    fun refreshVideos() {
        val version = ++requestVersion
        requestJob?.cancel()
        pageNumber = 0
        canLoadMore = false
        isLoading = true
        _uiState.value = FavoriteVideosUiState.Loading

        requestJob = viewModelScope.launch {
            try {
                var nextPageNumber = 1
                var folderTitle = ""
                var isDefault = true
                var ownerMid = 0L
                val videos = mutableListOf<FavoriteVideoCard>()

                do {
                    val result = fetchFavoriteFolderContent(
                        mediaId = mediaId,
                        pageNumber = nextPageNumber
                    )
                    if (version != requestVersion) return@launch
                    if (!result.isSuccess) {
                        _uiState.value = FavoriteVideosUiState.Error("[加载错误]: ${result.message}")
                        return@launch
                    }

                    if (folderTitle.isEmpty()) {
                        folderTitle = result.folderInfo?.title.orEmpty()
                    }
                    result.folderInfo?.let { isDefault = it.isDefault; ownerMid = it.mid }
                    videos += result.videos
                    pageNumber = nextPageNumber
                    canLoadMore = result.hasMore
                    nextPageNumber++
                } while (videos.isEmpty() && canLoadMore)

                if (version != requestVersion) return@launch
                _uiState.value = FavoriteVideosUiState.Success(
                    folderTitle = folderTitle,
                    isDefault = isDefault,
                    videos = videos.distinctBy { it.avid },
                    ownerMid = ownerMid,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = FavoriteVideosUiState.Error(e.message ?: "其他网络错误")
                }
            } finally {
                if (version == requestVersion) {
                    isLoading = false
                }
            }
        }
    }

    fun loadMoreVideos() {
        val currentState = _uiState.value as? FavoriteVideosUiState.Success ?: return
        if (isLoading || !canLoadMore) return

        val version = ++requestVersion
        isLoading = true
        requestJob = viewModelScope.launch {
            try {
                var nextPageNumber = pageNumber + 1
                var folderTitle = currentState.folderTitle
                var isDefault = currentState.isDefault
                var ownerMid = currentState.ownerMid
                val videos = mutableListOf<FavoriteVideoCard>()

                do {
                    val result = fetchFavoriteFolderContent(
                        mediaId = mediaId,
                        pageNumber = nextPageNumber
                    )
                    if (version != requestVersion) return@launch
                    if (!result.isSuccess) {
                        _uiState.value = FavoriteVideosUiState.Error("[加载错误]: ${result.message}")
                        return@launch
                    }

                    if (folderTitle.isEmpty()) {
                        folderTitle = result.folderInfo?.title.orEmpty()
                    }
                    result.folderInfo?.let { isDefault = it.isDefault; ownerMid = it.mid }
                    videos += result.videos
                    pageNumber = nextPageNumber
                    canLoadMore = result.hasMore
                    nextPageNumber++
                } while (videos.isEmpty() && canLoadMore)

                if (version != requestVersion) return@launch
                _uiState.value = FavoriteVideosUiState.Success(
                    folderTitle = folderTitle,
                    isDefault = isDefault,
                    videos = (currentState.videos + videos).distinctBy { it.avid },
                    ownerMid = ownerMid,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = FavoriteVideosUiState.Error(e.message ?: "其他网络错误")
                }
            } finally {
                if (version == requestVersion) {
                    isLoading = false
                }
            }
        }
    }

    fun removeItem(id: Long) {
        val currentState = _uiState.value as? FavoriteVideosUiState.Success ?: return
        _uiState.value = currentState.copy(
            videos = currentState.videos.filter { it.avid != id }
        )
    }
}
