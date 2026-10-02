package tv.hsrui.bolo.accountFeature.feature.download

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.download.DownloadManager
import tv.hsrui.bolo.download.DownloadFiles
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.download.ShowDownloadTaskCard
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
    Scaffold(modifier = modifier, topBar = {
        if (!isEntryFromList || !isExpanded()) ShowTopBarWithNavigationButton(title = { Text("下载管理") })
    }) { padding ->
        ShowHorizontalCardGrid(
            cards = tasks,
            keySelector = { it.id },
            gridState = gridState,
            modifier = Modifier.padding(padding.calculateWithoutBottom()),
            topContent = if (initializationError != null || tasks.isEmpty()) {
                {
                    if (initializationError != null) Text("下载记录加载失败：$initializationError", color = MaterialTheme.colorScheme.error)
                    else Text("暂无下载任务", modifier = Modifier.padding(12.dp))
                }
            } else null
        ) { task ->
            ShowDownloadTaskCard(
                task = task,
                onOpenFile = {
                    task.output?.let { output ->
                        scope.launch {
                            if (!files.openFile(output)) snackbar.showMessage("无法外部打开文件")
                        }
                    }
                },
                onShowFile = {
                    task.output?.let { output ->
                        scope.launch {
                            if (!files.showFile(output)) snackbar.showMessage("无法查看文件位置")
                        }
                    }
                },
                onRetry = { manager.retry(task.id) },
                onCancel = { manager.cancel(task.id) },
                onRemove = { removing = task.id }
            )
        }
    }
    removing?.let { id ->
        ShowConfirmDialog(onCancel = { removing = null }, onConfirm = { manager.remove(id); removing = null }, confirmText = "移除") {
            Text("移除该下载记录？已下载的文件将保留。", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}
