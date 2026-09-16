package tv.hsrui.bolo.favorite.videos

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowDeleteFavoriteFolderDialog
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.network.feature.favorite.removeFavoriteVideo
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun FavoriteVideosScreen(
    mediaId: Long,
    modifier: Modifier = Modifier,
    viewModel: FavoriteVideosViewModel = viewModel(key = mediaId.toString()) {
        FavoriteVideosViewModel(mediaId = mediaId)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarManager: SnackbarManager = koinInject()
    val navigator: Navigator = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val currentUserMid by loginStorage.currentUserMidFlow.collectAsState(
        initial = if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L,
    )
    val canManageNow = {
        val ownerMid = (viewModel.uiState.value as? FavoriteVideosUiState.Success)?.ownerMid ?: 0L
        loginStorage.isLoggedIn && ownerMid > 0 && ownerMid == loginStorage.cookies.dedeUserID
    }
    val canManage = currentUserMid > 0 && canManageNow()
    var showDeleteFolderDialog by rememberSaveable(mediaId) { mutableStateOf(false) }
    val successState = uiState as? FavoriteVideosUiState.Success
    val folderTitle = successState
        ?.folderTitle
        ?.ifEmpty { "收藏夹" }
        ?: "收藏夹"

    Scaffold(
        modifier = modifier,
        topBar = {
            ShowTopBarWithNavigationButton(title = { Text(folderTitle) })
        }
    ) { innerPadding ->
        FavoriteVideosContent(
            uiState = uiState,
            isLoading = viewModel.isLoading,
            canManage = canManage,
            onLoadMore = viewModel::loadMoreVideos,
            onRefresh = viewModel::refreshVideos,
            onDeleteFolder = { showDeleteFolderDialog = true },
            onRemove = remove@{ video ->
                if (!canManageNow()) return@remove
                try {
                    val result = removeFavoriteVideo(
                        mediaId = mediaId,
                        avid = video.avid
                    )
                    if (result.isSuccess) {
                        viewModel.removeItem(video.avid)
                    } else {
                        snackbarManager.showMessage(result.message.ifEmpty { "取消收藏失败" })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbarManager.showMessage(e.message ?: "其他网络错误")
                }
            },
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        )
    }

    if (showDeleteFolderDialog && canManage && successState != null && !successState.isDefault) {
        ShowDeleteFavoriteFolderDialog(
            canDelete = canManageNow,
            mediaId = mediaId,
            folderTitle = folderTitle,
            onCancel = { showDeleteFolderDialog = false },
            onDeleted = {
                showDeleteFolderDialog = false
                navigator.goBack()
            },
        )
    }
}
