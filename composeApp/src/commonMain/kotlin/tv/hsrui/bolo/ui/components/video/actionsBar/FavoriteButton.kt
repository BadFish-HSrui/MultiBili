package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.favorite_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowCreateFavoriteFolderDialog
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.favorite.FavoriteFolderInfoData
import tv.hsrui.network.feature.favorite.fetchMyFavoriteFolders
import tv.hsrui.network.feature.favorite.modifyVideoFavoriteFolders
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.utils.formatCountToString

@Composable
fun FavoriteButton(
    videoInfo: VideoInfoData,
    isFavorite: Boolean,
    canClick: Boolean,
    reloadState: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable(videoInfo.avid) { mutableStateOf(false) }
    var loadTrigger by rememberSaveable(videoInfo.avid) { mutableStateOf(0) }
    var folders by remember(videoInfo.avid) {
        mutableStateOf(emptyList<FavoriteFolderInfoData>())
    }
    var initialSelectedIds by remember(videoInfo.avid) {
        mutableStateOf(emptySet<Long>())
    }
    var selectedIds by remember(videoInfo.avid) {
        mutableStateOf(emptySet<Long>())
    }
    var isLoading by remember(videoInfo.avid) { mutableStateOf(false) }
    var loadError by remember(videoInfo.avid) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(videoInfo.avid) { mutableStateOf(false) }
    var showCreateFolderDialog by rememberSaveable(videoInfo.avid) { mutableStateOf(false) }
    var isRefreshingAfterCreate by remember(videoInfo.avid) { mutableStateOf(false) }
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowInfo.current.containerDpSize
    val dialogContentMaxWidth = minOf(windowSize.width * 0.8F, 480.dp)
    val dialogContentMaxHeight = windowSize.height / 2

    LaunchedEffect(showDialog, loadTrigger, videoInfo.avid) {
        if (!showDialog) return@LaunchedEffect

        isLoading = true
        loadError = null
        try {
            val result = fetchMyFavoriteFolders(targetAvid = videoInfo.avid)
            if (result.isSuccess) {
                folders = result.folders
                initialSelectedIds = result.folders
                    .filter(FavoriteFolderInfoData::containsTargetVideo)
                    .mapTo(mutableSetOf(), FavoriteFolderInfoData::id)
                selectedIds = initialSelectedIds
            } else {
                loadError = result.message.ifEmpty { "收藏夹加载失败" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: "其他网络错误"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier) {
        Surface(
            onClick = {
                folders = emptyList()
                initialSelectedIds = emptySet()
                selectedIds = emptySet()
                isLoading = true
                loadError = null
                showCreateFolderDialog = false
                isRefreshingAfterCreate = false
                showDialog = true
                loadTrigger++
            },
            enabled = canClick && videoInfo.avid > 0,
            color = Color.Transparent,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.favorite_icon),
                    contentDescription = "收藏",
                    tint = if (isFavorite) BiliColor.ThemeColor else Color.Gray,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = videoInfo.stateCount.favorite.formatCountToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showDialog && !showCreateFolderDialog) {
        val hasChanges = selectedIds != initialSelectedIds
        ShowConfirmDialog(
            onCancel = { showDialog = false },
            onConfirm = {
                if (!hasChanges || isLoading || loadError != null || isSubmitting) {
                    return@ShowConfirmDialog
                }

                val addMediaIds = selectedIds - initialSelectedIds
                val removeMediaIds = initialSelectedIds - selectedIds
                isSubmitting = true
                scope.launch {
                    try {
                        val result = modifyVideoFavoriteFolders(
                            avid = videoInfo.avid,
                            addMediaIds = addMediaIds,
                            removeMediaIds = removeMediaIds,
                        )
                        if (result.isSuccess) {
                            showDialog = false
                            reloadState()
                            snackbarManager.showMessage("收藏状态已更改")
                        } else {
                            snackbarManager.showMessage(
                                result.message.ifEmpty { "收藏状态更改失败" },
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.message ?: "其他网络错误")
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            cancelEnabled = !isSubmitting && !isRefreshingAfterCreate,
            confirmEnabled = hasChanges && !isLoading && loadError == null && !isSubmitting,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = dialogContentMaxWidth)
                    .heightIn(max = dialogContentMaxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "选择收藏夹",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }

                    loadError != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = loadError.orEmpty(),
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = {
                                    isLoading = true
                                    loadError = null
                                    loadTrigger++
                                },
                            ) {
                                Text("重试")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1F, fill = false),
                        ) {
                            if (folders.isEmpty()) {
                                item(key = "empty-folders") {
                                    Text(
                                        text = "暂无收藏夹",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    )
                                }
                            }

                            items(
                                items = folders,
                                key = FavoriteFolderInfoData::id,
                            ) { folder ->
                                val isSelected = folder.id in selectedIds
                                ListItem(
                                    headlineContent = { Text(folder.title) },
                                    supportingContent = { Text("${folder.mediaCount} 个内容") },
                                    trailingContent = {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            enabled = !isSubmitting,
                                        )
                                    },
                                    modifier = Modifier.toggleable(
                                        value = isSelected,
                                        enabled = !isSubmitting,
                                        role = Role.Checkbox,
                                        onValueChange = { selected ->
                                            selectedIds = if (selected) {
                                                selectedIds + folder.id
                                            } else {
                                                selectedIds - folder.id
                                            }
                                        },
                                    ),
                                )
                            }

                            item(key = "create-folder") {
                                ListItem(
                                    headlineContent = { Text("新建收藏夹") },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier.clickable(
                                        enabled = !isSubmitting,
                                        role = Role.Button,
                                    ) {
                                        showCreateFolderDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && showCreateFolderDialog) {
        ShowCreateFavoriteFolderDialog(
            onCancel = {
                showCreateFolderDialog = false
            },
            onCreated = { createdFolder ->
                val pendingAddIds = (selectedIds - initialSelectedIds) + createdFolder.id
                val pendingRemoveIds = initialSelectedIds - selectedIds
                folders = folders.filterNot { it.id == createdFolder.id } + createdFolder
                selectedIds += createdFolder.id
                showCreateFolderDialog = false
                isLoading = true
                loadError = null
                isRefreshingAfterCreate = true

                scope.launch {
                    try {
                        val refreshResult = fetchMyFavoriteFolders(
                            targetAvid = videoInfo.avid,
                        )
                        if (refreshResult.isSuccess) {
                            val refreshedFolders = refreshResult.folders
                            val mergedFolders = if (
                                refreshedFolders.any { it.id == createdFolder.id }
                            ) {
                                refreshedFolders
                            } else {
                                refreshedFolders + createdFolder
                            }
                            val availableIds = mergedFolders
                                .mapTo(mutableSetOf(), FavoriteFolderInfoData::id)
                            val refreshedInitialSelectedIds = refreshedFolders
                                .filter(FavoriteFolderInfoData::containsTargetVideo)
                                .mapTo(mutableSetOf(), FavoriteFolderInfoData::id)
                            folders = mergedFolders
                            initialSelectedIds = refreshedInitialSelectedIds
                            selectedIds = (
                                (refreshedInitialSelectedIds + pendingAddIds) - pendingRemoveIds
                                ).intersect(availableIds)
                        } else {
                            snackbarManager.showMessage(
                                refreshResult.message.ifEmpty { "收藏夹刷新失败" },
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.message ?: "收藏夹刷新失败")
                    } finally {
                        isLoading = false
                        isRefreshingAfterCreate = false
                    }
                }
            },
        )
    }
}
