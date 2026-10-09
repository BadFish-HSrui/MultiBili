package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.ui.components.dialog.ShowUserInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowVideoInfoDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

/** 日期范围包含首尾两天；不传范围时显示累计总计并隐藏范围文字。 */
@Composable
fun ShowPlaybackRankings(
    dateRange: ClosedRange<LocalDate>? = null,
    modifier: Modifier = Modifier,
    limit: Int = 10,
) {
    key(dateRange, limit) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val model = viewModel(viewModelStoreOwner = owner) { PlaybackRankingsViewModel(dateRange, limit = limit) }
        val state by model.uiState.collectAsStateWithLifecycle()
        var refreshKey by remember { mutableIntStateOf(0) }
        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
        var selectedVid by remember { mutableStateOf<Vid?>(null) }
        var selectedUpMid by remember { mutableStateOf<Long?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(model, lifecycleOwner, refreshKey) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.load() }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            selectedVid = null
            selectedUpMid = null
        }
        selectedVid?.let { vid ->
            ShowVideoInfoDialog(vid = vid, onDismissRequest = { selectedVid = null })
        }
        selectedUpMid?.let { mid ->
            ShowUserInfoDialog(mid = mid, onDismissRequest = { selectedUpMid = null })
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("观看时长排行", style = MaterialTheme.typography.titleMedium)
            dateRange?.let { range ->
                Text(
                    text = if (range.start == range.endInclusive) range.start.toString()
                        else "${range.start} 至 ${range.endInclusive}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                listOf("UP主", "视频", "番剧影视").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedIndex == index, onClick = { selectedIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) { Text(label) }
                }
            }
            if (state.isLoading) {
                if (state.rankings == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.error != null) {
                if (state.rankings == null) {
                    ShowErrorContent(message = state.error.orEmpty(), retry = { refreshKey++ })
                } else {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("刷新失败，仍显示上次读取的数据", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { refreshKey++ }) { Text("重试") }
                        }
                    }
                }
            }
            state.rankings?.let { rankings ->
                Card(Modifier.fillMaxWidth()) {
                    if (selectedIndex == 0) {
                        if (rankings.ups.isEmpty()) {
                            Text(if (dateRange == null) "还没有 UP 主观看数据" else "这段时间还没有 UP 主观看数据",
                                Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        rankings.ups.forEachIndexed { index, up ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            StatisticsRankingRow(
                                rank = index + 1,
                                title = up.upName ?: "UP ${up.upMid}",
                                subtitle = "${up.playCount} 次播放",
                                totalPlayedMs = up.totalPlayedMs,
                                modifier = Modifier.clickable(enabled = up.upMid > 0) {
                                    selectedVid = null
                                    selectedUpMid = up.upMid
                                },
                            )
                        }
                    } else {
                        val videos = if (selectedIndex == 1) rankings.videos else rankings.media
                        if (videos.isEmpty()) {
                            val contentLabel = if (selectedIndex == 1) "视频" else "番剧影视"
                            Text(if (dateRange == null) "还没有${contentLabel}观看数据" else "这段时间还没有${contentLabel}观看数据",
                                Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        videos.forEachIndexed { index, video ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            val title = if (video.contentType == "media" && !video.partTitle.isNullOrBlank()) {
                                "${video.title} · ${video.partTitle}"
                            } else video.title
                            StatisticsRankingRow(
                                rank = index + 1,
                                title = title,
                                subtitle = "${video.playCount} 次播放",
                                totalPlayedMs = video.totalPlayedMs,
                                modifier = Modifier.clickable(enabled = video.avid > 0) {
                                    selectedUpMid = null
                                    selectedVid = Vid.AVid(video.avid)
                                },
                            )
                        }
                    }
                }
            }
            if (state.hasUnsavedPlayback) {
                Text("部分播放记录尚未保存，当前显示已保存的数据。可稍后刷新。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatisticsRankingRow(
    rank: Int,
    title: String,
    subtitle: String,
    totalPlayedMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = if (rank == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp)) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(rank.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatStatisticsDuration(totalPlayedMs), modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1)
                Text(subtitle, modifier = Modifier.weight(1f).alignByBaseline(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun formatStatisticsDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0 分钟"
    if (milliseconds < 1_000L) return "不足 1 秒"
    val seconds = milliseconds / 1_000L
    if (seconds < 60L) return "$seconds 秒"
    val minutes = seconds / 60L
    if (minutes < 60L) return "$minutes 分钟"
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return if (remainder == 0L) "$hours 小时" else "$hours 小时 $remainder 分钟"
}
