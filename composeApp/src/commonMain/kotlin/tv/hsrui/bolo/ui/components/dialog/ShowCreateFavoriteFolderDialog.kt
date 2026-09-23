package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()

    ShowFavoriteFolderInfoDialog(
        heading = "新建收藏夹",
        initialTitle = "",
        initialIntro = "",
        initialIsPublic = true,
        isSubmitting = isSubmitting,
        canSubmit = canCreate,
        onCancel = onCancel,
        onConfirm = { title, intro, isPublic ->
            if (isSubmitting || !canCreate()) return@ShowFavoriteFolderInfoDialog
            isSubmitting = true
            scope.launch {
                try {
                    if (!canCreate()) return@launch
                    val result = createFavoriteFolder(title, intro, isPublic)
                    val createdFolder = result.folder
                    if (!result.isSuccess || createdFolder == null || createdFolder.id <= 0) {
                        snackbarManager.showMessage(result.message.ifEmpty { "创建收藏夹失败" })
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
    )
}

@Composable
internal fun ShowFavoriteFolderInfoDialog(
    heading: String,
    initialTitle: String,
    initialIntro: String,
    initialIsPublic: Boolean,
    isSubmitting: Boolean,
    canSubmit: () -> Boolean,
    onCancel: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var intro by rememberSaveable { mutableStateOf(initialIntro) }
    var isPublic by rememberSaveable { mutableStateOf(initialIsPublic) }
    val focusRequester = remember { FocusRequester() }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val contentWidth = minOf(windowSize.width * 0.8F, 280.dp)
    val trimmedTitle = title.trim()
    val titleLength = trimmedTitle.unicodeCodePointCount()
    val introLength = intro.unicodeCodePointCount()
    val titleValid = titleLength in 1..20
    val introValid = introLength <= 200

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = {
            if (titleValid && introValid && !isSubmitting && canSubmit()) {
                onConfirm(trimmedTitle, intro, isPublic)
            }
        },
        cancelEnabled = !isSubmitting,
        confirmEnabled = titleValid && introValid && !isSubmitting && canSubmit(),
    ) {
        Column(
            modifier = Modifier
                .width(contentWidth)
                .heightIn(max = windowSize.height * 0.65F)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !isSubmitting,
                label = { Text("收藏夹名称") },
                singleLine = true,
                isError = titleLength > 20 || (title.isNotEmpty() && !titleValid),
                supportingText = {
                    Text(if (titleLength > 20) "名称最多20字 · $titleLength/20" else "$titleLength/20")
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            ListItem(
                headlineContent = { Text("公开") },
                trailingContent = {
                    Switch(checked = isPublic, onCheckedChange = null, enabled = !isSubmitting)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isPublic,
                        enabled = !isSubmitting,
                        role = Role.Switch,
                        onValueChange = { isPublic = it },
                    ),
            )
            OutlinedTextField(
                value = intro,
                onValueChange = { intro = it },
                enabled = !isSubmitting,
                label = { Text("简介") },
                minLines = 3,
                maxLines = 5,
                isError = !introValid,
                supportingText = {
                    Text(if (!introValid) "简介最多200字 · $introLength/200" else "$introLength/200")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun String.unicodeCodePointCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val current = this[index].code
        if (current in 0xD800..0xDBFF && index + 1 < length && this[index + 1].code in 0xDC00..0xDFFF) {
            index++
        }
        index++
        count++
    }
    return count
}
