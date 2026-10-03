package tv.hsrui.bolo.ui.components.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.download.DownloadStatus
import tv.hsrui.bolo.download.DownloadTask

@Composable
fun ShowDownloadTaskCard(
    task: DownloadTask,
    onOpenFile: () -> Unit,
    onShowFile: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = task.title,
    isCheckingFile: Boolean = false,
) {
    val total = task.totalBytes?.takeIf { it > 0 }
    val downloadProgress = total?.let { (task.downloadedBytes.toDouble() / it).coerceIn(0.0, 1.0) } ?: 0.0
    val mergeProgress = task.mergeProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val showProgress = task.status != DownloadStatus.Completed
    val statusText = when (task.status) {
        DownloadStatus.Downloading -> if (total != null) {
            "${(downloadProgress * 100).toInt()}% · ${formatDownloadBytes(task.bytesPerSecond)}/s · " +
                "${formatDownloadBytes(task.downloadedBytes)} / ${formatDownloadBytes(total)}"
        } else {
            "—% · ${formatDownloadBytes(task.bytesPerSecond)}/s · ${formatDownloadBytes(task.downloadedBytes)} / 未知"
        }
        DownloadStatus.Merging -> "合成中 · ${(mergeProgress * 100).toInt()}%"
        DownloadStatus.Completed -> task.output?.let { "已完成 · ${formatDownloadBytes(it.size)}" } ?: "已完成"
        DownloadStatus.Failed -> task.error?.takeIf { it.isNotBlank() } ?: "下载失败"
        DownloadStatus.Queued, DownloadStatus.WaitingForMerge, DownloadStatus.Saving, DownloadStatus.Canceled -> task.status.title
    }

    Card(modifier.fillMaxWidth().height(108.dp)) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (showProgress) 1 else 2,
                softWrap = !showProgress,
                overflow = TextOverflow.Ellipsis
            )
            if (showProgress) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
                    when (task.status) {
                        DownloadStatus.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        DownloadStatus.Downloading -> if (total != null) {
                            LinearProgressIndicator(progress = { downloadProgress.toFloat() }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        DownloadStatus.Queued -> LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
                        DownloadStatus.WaitingForMerge -> LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                        DownloadStatus.Merging -> LinearProgressIndicator(progress = { mergeProgress }, modifier = Modifier.fillMaxWidth())
                        DownloadStatus.Failed, DownloadStatus.Canceled -> LinearProgressIndicator(
                            progress = { downloadProgress.toFloat() }, modifier = Modifier.fillMaxWidth()
                        )
                        DownloadStatus.Completed -> Unit
                    }
                }
            }
            Text(
                text = statusText,
                color = if (task.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else LocalContentColor.current,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (task.status == DownloadStatus.Completed) {
                        IconButton(onClick = onOpenFile, enabled = task.output != null && !isCheckingFile, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = "外部打开", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onShowFile, enabled = task.output != null && !isCheckingFile, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "查看文件", modifier = Modifier.size(24.dp))
                        }
                    }
                    if (task.status == DownloadStatus.Failed || task.status == DownloadStatus.Canceled) IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Replay, contentDescription = "重试", modifier = Modifier.size(24.dp))
                    }
                    if (!task.status.isTerminal) IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.HighlightOff, contentDescription = "取消", modifier = Modifier.size(24.dp))
                    }
                    if (task.status.isTerminal) IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "移除记录", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

internal fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${(bytes / (1024.0 * 1024 * 1024) * 10).toLong() / 10.0} GB"
    bytes >= 1024L * 1024 -> "${(bytes / (1024.0 * 1024) * 10).toLong() / 10.0} MB"
    bytes >= 1024 -> "${(bytes / 1024.0 * 10).toLong() / 10.0} KB"
    else -> "$bytes B"
}
