package tv.hsrui.bolo.ui.components.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.download.DownloadTask
import tv.hsrui.bolo.download.orderedDownloadEpisodes
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDownloadGroupSheet(
    tasks: List<DownloadTask>,
    onDismiss: () -> Unit,
    onOpenFile: (DownloadTask) -> Unit,
    onShowFile: (DownloadTask) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    isFileMissing: (DownloadTask) -> Boolean,
    isCheckingFile: (DownloadTask) -> Boolean,
    onCheckFile: suspend (DownloadTask) -> Unit,
) {
    val first = tasks.firstOrNull() ?: return
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Text(
                text = first.mainTitle,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            ShowHorizontalCardGrid(
                cards = tasks.orderedDownloadEpisodes(),
                keySelector = { it.id },
                gridState = rememberLazyGridState(),
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(300.dp),
            ) { task ->
                ShowDownloadTaskCard(
                    task = task,
                    title = task.subtitle.ifBlank { task.mainTitle },
                    onOpenFile = { onOpenFile(task) },
                    onShowFile = { onShowFile(task) },
                    onRetry = { onRetry(task.id) },
                    onCancel = { onCancel(task.id) },
                    onRemove = { onRemove(task.id) },
                    fileMissing = isFileMissing(task),
                    isCheckingFile = isCheckingFile(task),
                    onCheckFile = { onCheckFile(task) },
                )
            }
        }
    }
}
