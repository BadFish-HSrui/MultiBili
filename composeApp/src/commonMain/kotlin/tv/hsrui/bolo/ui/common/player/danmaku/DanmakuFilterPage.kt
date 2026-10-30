package tv.hsrui.bolo.ui.common.player.danmaku

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.scroll.containScroll
import tv.hsrui.network.feature.danmaku.DanmakuFilterData
import tv.hsrui.network.feature.danmaku.DanmakuFilterType

enum class DanmakuFilterAction {
    Upload,
    RemoveFromCloud,
    Delete,
}

@Composable
fun DanmakuFilterPage(
    viewModel: DanmakuFilterViewModel,
    modifier: Modifier = Modifier,
) {
    val isWorking by viewModel.isWorking.collectAsState()
    var selectedType by rememberSaveable { mutableStateOf(DanmakuFilterType.Text) }
    val textGridState = rememberLazyGridState()
    val regexGridState = rememberLazyGridState()
    val userGridState = rememberLazyGridState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingFilter by remember { mutableStateOf<DanmakuFilterData?>(null) }
    var pendingAction by remember { mutableStateOf<DanmakuFilterAction?>(null) }
    val filters = viewModel.filters.filter { it.type == selectedType }

    Column(modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().containScroll(Orientation.Horizontal),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DanmakuFilterType.entries, key = { it.name }) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = {
                        Text(
                            when (type) {
                                DanmakuFilterType.Text -> "文本"
                                DanmakuFilterType.Regex -> "正则"
                                DanmakuFilterType.User -> "用户"
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (isWorking) LinearProgressIndicator(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (filters.isEmpty()) {
                Text("暂无屏蔽规则", modifier = Modifier.align(Alignment.Center))
            }
            ShowHorizontalCardGrid(
                cards = filters,
                keySelector = { "${it.type.code}:${it.content}" },
                gridState = when (selectedType) {
                    DanmakuFilterType.Text -> textGridState
                    DanmakuFilterType.Regex -> regexGridState
                    DanmakuFilterType.User -> userGridState
                },
                columns = GridCells.Fixed(1),
                bottomContent = { Spacer(Modifier.height(144.dp)) },
            ) { filter ->
                ShowDanmakuFilterCard(
                    filter = filter,
                    enabled = !isWorking,
                    onToggle = { viewModel.toggleFilter(filter) },
                    onAction = { action ->
                        pendingFilter = filter
                        pendingAction = action
                    },
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                val buttonModifier = Modifier
                    .size(56.dp)
                    .alpha(if (isWorking) 0.5f else 1f)
                    .semantics { if (isWorking) disabled() }
                FloatingActionButton(
                    onClick = { if (!isWorking) viewModel.syncFilters() },
                    modifier = buttonModifier,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "同步", modifier = Modifier.size(24.dp))
                }
                FloatingActionButton(
                    onClick = { if (!isWorking) showAddDialog = true },
                    modifier = buttonModifier,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        ShowAddDanmakuFilterDialog(
            isWorking = isWorking,
            onCancel = { showAddDialog = false },
            onConfirm = { content, type, upload ->
                viewModel.addFilter(content, type, upload) { addedType ->
                    selectedType = addedType
                    showAddDialog = false
                }
            },
        )
    }
    val filter = pendingFilter
    val action = pendingAction
    if (filter != null && action != null) {
        val closeDialog = {
            pendingFilter = null
            pendingAction = null
        }
        ShowConfirmDialog(
            title = {
                Text(
                    when (action) {
                        DanmakuFilterAction.Upload -> "将此规则上传到云端？"
                        DanmakuFilterAction.RemoveFromCloud -> "从云端删除此规则？"
                        DanmakuFilterAction.Delete -> "删除此屏蔽规则？"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = when (action) {
                DanmakuFilterAction.Upload -> "保留本地启用状态。"
                DanmakuFilterAction.RemoveFromCloud -> "本地规则和启用状态将保留。"
                DanmakuFilterAction.Delete -> if (filter.id > 0L) "同时删除云端和本地规则。" else "删除本地规则。"
            },
            onCancel = closeDialog,
            onConfirm = {
                when (action) {
                    DanmakuFilterAction.Upload -> viewModel.uploadFilter(filter, onSuccess = closeDialog)
                    DanmakuFilterAction.RemoveFromCloud -> viewModel.removeFilterFromCloud(filter, onSuccess = closeDialog)
                    DanmakuFilterAction.Delete -> viewModel.deleteFilter(filter, onSuccess = closeDialog)
                }
            },
            cancelEnabled = !isWorking,
            confirmEnabled = !isWorking,
            confirmText = if (isWorking) "处理中" else "确认",
            dismissOnClickOutside = !isWorking,
        )
    }
}

@Composable
fun ShowDanmakuFilterCard(
    filter: DanmakuFilterData,
    enabled: Boolean,
    onToggle: () -> Unit,
    onAction: (DanmakuFilterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSynced = filter.id > 0L
    Card(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = filter.content,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                IconButton(
                    onClick = onToggle,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).semantics {
                        stateDescription = if (filter.isOpened) "已启用" else "已停用"
                    },
                ) {
                    Icon(
                        if (filter.isOpened) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        contentDescription = if (filter.isOpened) "停用规则" else "启用规则",
                        modifier = Modifier.size(24.dp),
                        tint = if (filter.isOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        onAction(if (isSynced) DanmakuFilterAction.RemoveFromCloud else DanmakuFilterAction.Upload)
                    },
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).semantics {
                        stateDescription = if (isSynced) "已同步" else "未同步"
                    },
                ) {
                    Icon(
                        if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = if (isSynced) "从云端删除" else "上传云端",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = { onAction(DanmakuFilterAction.Delete) },
                    enabled = enabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除规则", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun ShowAddDanmakuFilterDialog(
    isWorking: Boolean,
    onCancel: () -> Unit,
    onConfirm: (String, DanmakuFilterType, Boolean) -> Unit,
    initialContent: String = "",
) {
    var content by rememberSaveable(initialContent) { mutableStateOf(initialContent) }
    var isRegex by rememberSaveable { mutableStateOf(false) }
    var upload by rememberSaveable { mutableStateOf(true) }
    val regexError = remember(content, isRegex) {
        if (isRegex) danmakuFilterRegexError(content) else null
    }
    val focusRequester = remember { FocusRequester() }
    val windowSize = LocalWindowInfo.current.containerDpSize
    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = {
            val type = if (isRegex) DanmakuFilterType.Regex else DanmakuFilterType.Text
            onConfirm(content, type, upload)
        },
        cancelEnabled = !isWorking,
        confirmEnabled = !isWorking && content.isNotBlank() && regexError == null,
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
            Text("添加屏蔽规则", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("屏蔽内容") },
                isError = regexError != null,
                supportingText = regexError?.let { message -> { Text(message) } },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            DanmakuFilterCheckbox(
                text = "正则匹配",
                checked = isRegex,
                enabled = !isWorking,
                onCheckedChange = { isRegex = it },
            )
            DanmakuFilterCheckbox(
                text = "上传云端",
                checked = upload,
                enabled = !isWorking,
                onCheckedChange = { upload = it },
            )
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun DanmakuFilterCheckbox(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.size(16.dp).scale(0.8f),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
