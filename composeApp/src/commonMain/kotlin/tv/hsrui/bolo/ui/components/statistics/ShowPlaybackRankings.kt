package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.datetime.LocalDate
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.ui.components.dialog.ShowLiveRoomInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowUserInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowVideoInfoDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.network.utils.formatToDateTime

/** 日期范围包含首尾两天；不传范围时显示累计总计并隐藏范围文字。 */
@Composable
fun ShowPlaybackRankings(
    state: PlaybackRankingsUiState,
    onRetry: () -> Unit,
    dateRange: ClosedRange<LocalDate>? = null,
    modifier: Modifier = Modifier,
    showRank: Boolean = true,
    cardShape: Shape = CardDefaults.shape,
    segmentedButtonAlignment: Alignment.Horizontal = Alignment.Start,
    fillRemainingHeight: Boolean = false,
    showTitle: Boolean = true,
) {
    key(dateRange) {
        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
        var selectedVid by remember { mutableStateOf<Vid?>(null) }
        var selectedUpMid by remember { mutableStateOf<Long?>(null) }
        var selectedRoomId by remember { mutableStateOf<Long?>(null) }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            selectedVid = null
            selectedUpMid = null
            selectedRoomId = null
        }
        selectedVid?.let { vid ->
            ShowVideoInfoDialog(vid = vid, onDismissRequest = { selectedVid = null })
        }
        selectedUpMid?.let { mid ->
            ShowUserInfoDialog(mid = mid, onDismissRequest = { selectedUpMid = null })
        }
        selectedRoomId?.let { roomId ->
            ShowLiveRoomInfoDialog(roomId, onDismissRequest = { selectedRoomId = null })
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showTitle) Text("观看时长排行", style = MaterialTheme.typography.titleMedium)
            dateRange?.let { range ->
                Text(
                    text = if (range.start == range.endInclusive) range.start.toString()
                        else "${range.start} 至 ${range.endInclusive}",
                    modifier = if (showTitle) Modifier else Modifier.fillMaxWidth(),
                    style = if (showTitle) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                    color = if (showTitle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = if (showTitle) TextAlign.Start else TextAlign.Center,
                )
            }
            SingleChoiceSegmentedButtonRow(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().align(segmentedButtonAlignment)
                    .horizontalScroll(rememberScrollState()),
            ) {
                listOf("UP主", "视频", "番剧影视", "直播间").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedIndex == index, onClick = { selectedIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 4),
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (state.isLoading && !fillRemainingHeight) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (state.error != null && !fillRemainingHeight) {
                ShowErrorContent(message = state.error.orEmpty(), retry = onRetry)
            }
            if (fillRemainingHeight || state.rankings != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().then(if (fillRemainingHeight) Modifier.weight(1f) else Modifier),
                    shape = cardShape,
                ) {
                    when {
                        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        state.error != null -> ShowErrorContent(message = state.error.orEmpty(), retry = onRetry)
                        else -> state.rankings?.let { rankings ->
                            val videos = if (selectedIndex == 1) rankings.videos else rankings.media
                            val rooms = state.liveRankings?.rooms.orEmpty()
                            val isEmpty = when (selectedIndex) {
                                0 -> rankings.ups.isEmpty()
                                3 -> rooms.isEmpty()
                                else -> videos.isEmpty()
                            }
                            if (isEmpty) {
                                val contentLabel = when (selectedIndex) {
                                    0 -> " UP 主"
                                    1 -> "视频"
                                    3 -> "直播间"
                                    else -> "番剧影视"
                                }
                                Box(
                                    modifier = (if (fillRemainingHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(20.dp),
                                    contentAlignment = if (fillRemainingHeight) Alignment.Center else Alignment.TopStart,
                                ) {
                                    Text(
                                        text = if (dateRange == null) "还没有${contentLabel}观看数据" else "这段时间还没有${contentLabel}观看数据",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = if (fillRemainingHeight) TextAlign.Center else TextAlign.Start,
                                    )
                                }
                            } else Column(
                                if (fillRemainingHeight) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier,
                            ) {
                                if (selectedIndex == 0) {
                                    rankings.ups.forEachIndexed { index, up ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                        StatisticsRankingRow(
                                            rank = if (showRank) index + 1 else null,
                                            title = up.upName ?: "UP ${up.upMid}",
                                            subtitle = "${up.playCount} 次播放",
                                            totalPlayedMs = up.totalPlayedMs,
                                            modifier = Modifier.clickable(enabled = up.upMid > 0) {
                                                selectedVid = null
                                                selectedRoomId = null
                                                selectedUpMid = up.upMid
                                            },
                                        )
                                    }
                                } else if (selectedIndex == 3) {
                                    rooms.forEachIndexed { index, room ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                        StatisticsRankingRow(
                                            rank = if (showRank) index + 1 else null,
                                            title = formatLiveStatisticsTitle(room.roomId, room.shortRoomId, room.upName),
                                            subtitle = "${room.playCount} 次观看",
                                            totalPlayedMs = room.totalPlayedMs,
                                            modifier = Modifier.clickable(enabled = room.roomId > 0) {
                                                selectedVid = null
                                                selectedUpMid = null
                                                selectedRoomId = room.roomId
                                            },
                                        )
                                    }
                                } else {
                                    videos.forEachIndexed { index, video ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                        val title = if (video.contentType == "media" && !video.partTitle.isNullOrBlank()) {
                                            "${video.title} · ${video.partTitle}"
                                        } else video.title
                                        StatisticsRankingRow(
                                            rank = if (showRank) index + 1 else null,
                                            title = title,
                                            subtitle = "${video.playCount} 次播放",
                                            totalPlayedMs = video.totalPlayedMs,
                                            modifier = Modifier.clickable(enabled = video.avid > 0) {
                                                selectedUpMid = null
                                                selectedRoomId = null
                                                selectedVid = Vid.AVid(video.avid)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.hasUnsavedPlayback) {
                Text("部分播放记录尚未保存，当前显示已保存的数据。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun StatisticsRankingRow(
    rank: Int?,
    title: String,
    subtitle: String,
    totalPlayedMs: Long,
    modifier: Modifier = Modifier,
    lastViewedAtMs: Long? = null,
) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rank != null) {
            Surface(color = if (rank == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp)) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Text(rank.toString(), style = MaterialTheme.typography.labelLarge)
                }
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
            lastViewedAtMs?.let {
                Text("最近观看：${(it / 1_000L).formatToDateTime(second = true)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
