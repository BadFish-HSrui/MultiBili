package tv.hsrui.bolo.ui.common.player.danmaku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.search.ShowSearchInputField
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.reply.ShowReplyTextDialog
import tv.hsrui.bolo.ui.components.reply.replyTextLongPress
import tv.hsrui.network.feature.danmaku.DanmakuFilterType
import tv.hsrui.network.feature.danmaku.DanmakuItem
import tv.hsrui.network.utils.formatToDuration

@Composable
fun DanmakuListPage(
    items: List<DanmakuItem>,
    viewModel: DanmakuFilterViewModel,
    modifier: Modifier = Modifier,
) {
    val searchState = rememberTextFieldState()
    val query = searchState.text.toString()
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    val filteredItems = remember(items, query) {
        if (query.isEmpty()) items else items.filter { it.content.contains(query, ignoreCase = true) }
    }
    val gridState = rememberLazyGridState()
    val isWorking by viewModel.isWorking.collectAsState()
    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingUser by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        // 恢复页面时保留滚动位置，只在搜索内容改变后回到顶部。
        if (query != previousQuery) {
            previousQuery = query
            gridState.scrollToItem(0)
        }
    }

    Column(modifier.fillMaxSize()) {
        ShowSearchInputField(
            textFieldState = searchState,
            onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (isWorking) LinearProgressIndicator(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (filteredItems.isEmpty()) {
                Text(
                    text = if (items.isEmpty()) "暂无已加载弹幕" else "暂无匹配弹幕",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            ShowHorizontalCardGrid(
                cards = filteredItems,
                keySelector = { it.id },
                gridState = gridState,
                columns = GridCells.Fixed(1),
            ) { item ->
                ShowDanmakuListCard(
                    item = item,
                    enabled = !isWorking,
                    onBlockText = { pendingText = item.content },
                    onBlockUser = { pendingUser = item.senderHash },
                )
            }
        }
    }

    pendingText?.let { content ->
        ShowAddDanmakuFilterDialog(
            isWorking = isWorking,
            initialContent = content,
            onCancel = { pendingText = null },
            onConfirm = { text, type, upload ->
                viewModel.addFilter(
                    content = text,
                    type = type,
                    upload = upload,
                    onAdded = { pendingText = null },
                )
            },
        )
    }
    pendingUser?.let { senderHash ->
        ShowBlockDanmakuUserDialog(
            senderHash = senderHash,
            isWorking = isWorking,
            onCancel = { pendingUser = null },
            onConfirm = { upload ->
                viewModel.addFilter(
                    content = senderHash,
                    type = DanmakuFilterType.User,
                    upload = upload,
                    onAdded = { pendingUser = null },
                )
            },
        )
    }
}

@Composable
fun ShowDanmakuListCard(
    item: DanmakuItem,
    enabled: Boolean,
    onBlockText: () -> Unit,
    onBlockUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showTextDialog by remember(item.id, item.content) { mutableStateOf(false) }

    if (showTextDialog) {
        ShowReplyTextDialog(text = item.content, onDismiss = { showTextDialog = false })
    }
    Card(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.content,
                modifier = Modifier
                    .weight(1f)
                    .replyTextLongPress(item.content) { showTextDialog = true },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (item.progressMs.coerceAtLeast(0) / 1_000).formatToDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = enabled,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoNotDisturb,
                            contentDescription = "更多操作",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded && enabled,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("屏蔽文本") },
                        onClick = {
                            menuExpanded = false
                            onBlockText()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("屏蔽用户") },
                        enabled = item.senderHash.isNotBlank(),
                        onClick = {
                            menuExpanded = false
                            onBlockUser()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "权重: ${item.weight}",
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
fun ShowBlockDanmakuUserDialog(
    senderHash: String,
    isWorking: Boolean,
    onCancel: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var upload by rememberSaveable(senderHash) { mutableStateOf(true) }
    val windowSize = LocalWindowInfo.current.containerDpSize
    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = { onConfirm(upload) },
        cancelEnabled = !isWorking,
        confirmEnabled = !isWorking && senderHash.isNotBlank(),
        confirmText = if (isWorking) "处理中" else "确认",
        dismissOnClickOutside = !isWorking,
    ) {
        Column(
            modifier = Modifier
                .width(minOf(windowSize.width * 0.8f, 280.dp))
                .heightIn(max = windowSize.height * 0.65f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("屏蔽此用户？", style = MaterialTheme.typography.titleMedium)
            Text(senderHash, style = MaterialTheme.typography.bodyMedium)
            DanmakuFilterCheckbox(
                text = "上传云端",
                checked = upload,
                enabled = !isWorking,
                onCheckedChange = { upload = it },
            )
        }
    }
}
