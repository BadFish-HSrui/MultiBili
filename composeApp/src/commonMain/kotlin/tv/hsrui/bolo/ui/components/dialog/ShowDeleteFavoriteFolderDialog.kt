package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.favorite.deleteFavoriteFolder

@Composable
fun ShowDeleteFavoriteFolderDialog(
    mediaId: Long,
    folderTitle: String,
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
    canDelete: () -> Boolean = { true },
) {
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()
    val windowSize = LocalWindowInfo.current.containerDpSize
    val contentMaxWidth = minOf(windowSize.width * 0.8F, 480.dp)
    val errorColor = MaterialTheme.colorScheme.error

    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = {
            if (mediaId <= 0 || isSubmitting || !canDelete()) {
                return@ShowConfirmDialog
            }

            isSubmitting = true
            scope.launch {
                try {
                    if (!canDelete()) return@launch
                    val result = deleteFavoriteFolder(mediaId)
                    if (!result.isSuccess) {
                        snackbarManager.showMessage(
                            result.message.ifEmpty { "删除收藏夹失败" },
                        )
                        return@launch
                    }

                    onDeleted()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbarManager.showMessage(e.message ?: "其他网络错误")
                } finally {
                    isSubmitting = false
                }
            }
        },
        cancelEnabled = !isSubmitting,
        confirmEnabled = mediaId > 0 && !isSubmitting && canDelete(),
    ) {
        Column(
            modifier = Modifier.widthIn(max = contentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = errorColor,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "删除收藏夹",
                color = errorColor,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
//            Text(
//                text = "确认删除收藏夹:",
//                textAlign = TextAlign.Center,
//                style = MaterialTheme.typography.bodyMedium,
//            )
            Text(
                text = folderTitle,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Text(
                text = "此收藏夹及其中的所有内容将从所有设备上永久移除",
                color = errorColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}
