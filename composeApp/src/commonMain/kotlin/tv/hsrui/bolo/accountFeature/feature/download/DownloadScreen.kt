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
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import tv.hsrui.bolo.download.DownloadManager
import tv.hsrui.bolo.download.DownloadFiles
import tv.hsrui.bolo.download.DownloadTask
import tv.hsrui.bolo.download.DownloadOutput
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
    val fileStates = remember { mutableStateMapOf<Pair<String, DownloadOutput>, Result<Boolean>>() }
    val fileChecks = remember { mutableStateMapOf<Pair<String, DownloadOutput>, Any>() }
    val currentOutputs = tasks.filter { it.status == DownloadStatus.Completed }
        .mapNotNull { task -> task.output?.let { task.id to it } }.toSet()
    LaunchedEffect(currentOutputs) {
        fileStates.keys.retainAll(currentOutputs)
        fileChecks.keys.retainAll(currentOutputs)
    }
    suspend fun checkFile(task: DownloadTask): Boolean? {
        val output = task.output ?: return null
        if (task.status != DownloadStatus.Completed) return null
        val key = task.id to output
        val token = Any()
        fileChecks[key] = token
        fun isCurrent(): Boolean = fileChecks[key] === token && manager.tasks.value.any {
            it.id == task.id && it.status == DownloadStatus.Completed && it.output == output
        }
        try {
            val exists = files.exists(output)
            if (!isCurrent()) return null
            fileStates[key] = Result.success(exists)
            return exists
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent()) {
                fileStates[key] = Result.failure(error)
                snackbar.showMessage("无法检查下载文件：${error.message?.take(180) ?: "未知错误"}")
            }
            return null
        } finally {
            if (fileChecks[key] === token) fileChecks.remove(key)
        }
    }
    val isFileMissing: (DownloadTask) -> Boolean = { task ->
        task.output?.let { fileStates[task.id to it]?.getOrNull() == false } == true
    }
    val isCheckingFile: (DownloadTask) -> Boolean = { task ->
        task.output?.let { (task.id to it) !in fileStates || (task.id to it) in fileChecks } == true
    }
    val groups = tasks.groupedDownloads()
    val viewedTasks = tasks.filter { it.displayGroupKey == viewingGroup }
    LaunchedEffect(viewingGroup, viewedTasks.isEmpty()) {
        if (viewingGroup != null && viewedTasks.isEmpty()) viewingGroup = null
    }
    fun openOutput(task: DownloadTask, showDirectory: Boolean) {
        val output = task.output ?: return
        if ((task.id to output) in fileChecks) return
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
                    fileMissing = isFileMissing(task),
                    isCheckingFile = isCheckingFile(task),
                    onCheckFile = { checkFile(task) },
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
            isFileMissing = isFileMissing,
            isCheckingFile = isCheckingFile,
            onCheckFile = { checkFile(it) },
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
