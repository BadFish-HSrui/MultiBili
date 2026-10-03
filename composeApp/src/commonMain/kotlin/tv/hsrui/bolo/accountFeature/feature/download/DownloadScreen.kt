package tv.hsrui.bolo.accountFeature.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.download.DownloadManager
import tv.hsrui.bolo.download.DownloadFiles
import tv.hsrui.bolo.download.DownloadTask
import tv.hsrui.bolo.download.DownloadStatus
import tv.hsrui.bolo.download.groupedDownloads
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.download.ShowDownloadTaskCard
import tv.hsrui.bolo.ui.components.download.ShowDownloadGroupCard
import tv.hsrui.bolo.ui.components.download.ShowDownloadGroupSheet
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun DownloadScreen(modifier: Modifier = Modifier, isEntryFromList: Boolean = true) {
    val manager: DownloadManager = koinInject()
    val snackbar: SnackbarManager = koinInject()
    val files = remember { DownloadFiles() }
    val scope = rememberCoroutineScope()
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val initializationError by manager.initializationError.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var removing by remember { mutableStateOf<String?>(null) }
    var viewingGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val checkedFiles = remember { mutableStateMapOf<DownloadTask, Unit>() }
    val fileChecks = remember { mutableStateMapOf<DownloadTask, Any>() }
    val completedTasks = tasks.filter { it.status == DownloadStatus.Completed && it.output != null }
    suspend fun checkFile(task: DownloadTask): Boolean? {
        if (task.status != DownloadStatus.Completed || task.output == null || task in fileChecks) return null
        val token = Any()
        fileChecks[task] = token
        try {
            val result = manager.checkFile(task)
            if (fileChecks[task] === token && manager.tasks.value.any { it === task }) {
                checkedFiles[task] = Unit
            }
            return result
        } finally {
            if (fileChecks[task] === token) fileChecks.remove(task)
        }
    }
    LaunchedEffect(completedTasks) {
        val current = completedTasks.toSet()
        checkedFiles.keys.retainAll(current)
        fileChecks.keys.retainAll(current)
        completedTasks.forEach { task ->
            if (task !in checkedFiles) checkFile(task)
        }
    }
    val isCheckingFile: (DownloadTask) -> Boolean = { task ->
        task.status == DownloadStatus.Completed && task.output != null && (task !in checkedFiles || task in fileChecks)
    }
    val groups = tasks.groupedDownloads()
    val viewedTasks = tasks.filter { it.displayGroupKey == viewingGroup }
    LaunchedEffect(viewingGroup, viewedTasks.isEmpty()) {
        if (viewingGroup != null && viewedTasks.isEmpty()) viewingGroup = null
    }
    fun openOutput(task: DownloadTask, showDirectory: Boolean) {
        val output = task.output ?: return
        if (task in fileChecks) return
        scope.launch {
            if (checkFile(task) != true) return@launch
            val opened = if (showDirectory) files.showFile(output) else files.openFile(output)
            if (!opened && checkFile(task) == true) {
                snackbar.showMessage(if (showDirectory) "无法查看文件位置" else "无法外部打开文件")
            }
        }
    }
    Scaffold(modifier = modifier, topBar = {
        if (!isEntryFromList || !isExpanded()) ShowTopBarWithNavigationButton(title = { Text("下载管理") })
    }) { padding ->
        ShowHorizontalCardGrid(
            cards = groups,
            keySelector = { it.first().displayGroupKey },
            gridState = gridState,
            modifier = Modifier.padding(padding.calculateWithoutBottom()),
            topContent = if (initializationError != null || tasks.isEmpty()) {
                {
                    if (initializationError != null) Text("下载记录加载失败：$initializationError", color = MaterialTheme.colorScheme.error)
                    else Text("暂无下载任务", modifier = Modifier.padding(12.dp))
                }
            } else null
        ) { group ->
            if (group.size > 1) {
                ShowDownloadGroupCard(group, onClick = { viewingGroup = group.first().displayGroupKey })
            } else {
                val task = group.single()
                ShowDownloadTaskCard(
                    task = task,
                    onOpenFile = { openOutput(task, false) },
                    onShowFile = { openOutput(task, true) },
                    onRetry = { manager.retry(task.id) },
                    onCancel = { manager.cancel(task.id) },
                    onRemove = { removing = task.id },
                    isCheckingFile = isCheckingFile(task),
                )
            }
        }
    }
    if (viewingGroup != null && viewedTasks.isNotEmpty()) {
        ShowDownloadGroupSheet(
            tasks = viewedTasks,
            onDismiss = { viewingGroup = null },
            onOpenFile = { openOutput(it, false) },
            onShowFile = { openOutput(it, true) },
            onRetry = manager::retry,
            onCancel = manager::cancel,
            onRemove = { removing = it },
            isCheckingFile = isCheckingFile,
        )
    }
    removing?.let { id ->
        val taskTitle = remember(id) { tasks.firstOrNull { it.id == id }?.title.orEmpty() }
        var deleteFile by remember(id) { mutableStateOf(false) }
        var isRemoving by remember(id) { mutableStateOf(false) }
        ShowConfirmDialog(
            title = { Text("移除该下载记录？") },
            text = taskTitle,
            textModifier = Modifier,
            onCancel = { if (!isRemoving) removing = null },
            onConfirm = {
                if (!isRemoving) {
                    isRemoving = true
                    scope.launch {
                        try {
                            if (manager.remove(id, deleteFile)) removing = null
                            else manager.tasks.value.firstOrNull { it.id == id }?.let { checkFile(it) }
                        } finally { isRemoving = false }
                    }
                }
            },
            cancelEnabled = !isRemoving,
            confirmEnabled = !isRemoving,
            dismissOnClickOutside = !isRemoving,
            confirmText = "移除",
        ) {
            Row(
                modifier = Modifier.toggleable(
                    value = deleteFile,
                    enabled = !isRemoving,
                    role = Role.Checkbox,
                    onValueChange = { deleteFile = it },
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = deleteFile,
                    onCheckedChange = null,
                    enabled = !isRemoving,
                    modifier = Modifier.size(16.dp).scale(0.8f),
                )
                Text("清理本地文件", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
