package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.favorite.FavoriteFolderInfoData
import tv.hsrui.network.feature.favorite.createFavoriteFolder

@Composable
fun ShowCreateFavoriteFolderDialog(
    onCancel: () -> Unit,
    onCreated: (FavoriteFolderInfoData) -> Unit,
    canCreate: () -> Boolean = { true },
) {
    var title by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()
    val windowSize = LocalWindowInfo.current.containerDpSize
    val contentMaxWidth = minOf(windowSize.width * 0.8F, 480.dp)
    val trimmedTitle = title.trim()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = {
            if (trimmedTitle.isEmpty() || isSubmitting || !canCreate()) {
                return@ShowConfirmDialog
            }

            isSubmitting = true
            scope.launch {
                try {
                    if (!canCreate()) return@launch
                    val result = createFavoriteFolder(
                        title = trimmedTitle,
                        isPrivate = isPrivate,
                    )
                    val createdFolder = result.folder
                    if (!result.isSuccess || createdFolder == null || createdFolder.id <= 0) {
                        snackbarManager.showMessage(
                            result.message.ifEmpty { "创建收藏夹失败" },
                        )
                        return@launch
                    }

                    onCreated(createdFolder)
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
        confirmEnabled = trimmedTitle.isNotEmpty() && !isSubmitting && canCreate(),
    ) {
        Column(
            modifier = Modifier.widthIn(max = contentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "新建收藏夹",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !isSubmitting,
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .focusRequester(focusRequester),
            )
            ListItem(
                headlineContent = { Text("设为私密") },
                trailingContent = {
                    Checkbox(
                        checked = isPrivate,
                        onCheckedChange = null,
                        enabled = !isSubmitting,
                    )
                },
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .toggleable(
                        value = isPrivate,
                        enabled = !isSubmitting,
                        role = Role.Checkbox,
                        onValueChange = { isPrivate = it },
                    ),
            )
        }
    }
}
