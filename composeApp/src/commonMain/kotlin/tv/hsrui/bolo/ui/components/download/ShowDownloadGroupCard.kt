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
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import tv.hsrui.bolo.download.DownloadType
import tv.hsrui.bolo.download.completedDownloadEpisodes
import tv.hsrui.network.utils.formatBytes

@Composable
fun ShowDownloadGroupCard(
    tasks: List<DownloadTask>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = tasks.firstOrNull() ?: return
    val total = tasks.distinctBy { it.episodeKey }.size
    val completed = tasks.completedDownloadEpisodes()
    val speed = tasks.filter { it.status == DownloadStatus.Downloading }.sumOf { it.bytesPerSecond }
    val downloadedBytes = tasks.sumOf {
        if (it.status == DownloadStatus.Completed) it.output?.size ?: it.downloadedBytes else it.downloadedBytes
    }
    val unit = if (first.request.type == DownloadType.Media) "集" else "P"
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().height(108.dp)) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Text(first.mainTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
                LinearProgressIndicator(progress = { completed.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "已完成 $completed / $total $unit · ${speed.formatBytes()}/s · ${downloadedBytes.formatBytes()}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "移除记录", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
