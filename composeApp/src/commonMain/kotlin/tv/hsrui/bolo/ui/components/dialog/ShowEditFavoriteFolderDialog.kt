package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.favorite.editFavoriteFolder

@Composable
fun ShowEditFavoriteFolderDialog(
    mediaId: Long,
    folderTitle: String,
    folderIntro: String,
    isPrivate: Boolean,
    canEdit: () -> Boolean,
    onCancel: () -> Unit,
    onEdited: () -> Unit,
) {
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()

    ShowFavoriteFolderInfoDialog(
        heading = "编辑信息",
        initialTitle = folderTitle,
        initialIntro = folderIntro,
        initialIsPublic = !isPrivate,
        isSubmitting = isSubmitting,
        canSubmit = { mediaId > 0 && canEdit() },
        onCancel = onCancel,
        onConfirm = { title, intro, isPublic ->
            if (mediaId <= 0 || isSubmitting || !canEdit()) return@ShowFavoriteFolderInfoDialog
            isSubmitting = true
            scope.launch {
                try {
                    if (!canEdit()) return@launch
                    val result = editFavoriteFolder(mediaId, title, intro, isPublic)
                    if (!result.isSuccess) {
                        snackbarManager.showMessage(result.message.ifEmpty { "编辑收藏夹失败" })
                        return@launch
                    }
                    onEdited()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbarManager.showMessage(e.message ?: "其他网络错误")
                } finally {
                    isSubmitting = false
                }
            }
        },
    )
}
