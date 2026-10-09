package tv.hsrui.bolo.boloSetting.setting.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackDailyStats
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSnapshot
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSummary
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    model: StatisticsViewModel = viewModel { StatisticsViewModel() },
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var dayCount by rememberSaveable { mutableIntStateOf(7) }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner, dayCount, refreshKey) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.load(dayCount) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { if (!isExpanded()) ShowTopBarWithNavigationButton(title = { Text("统计数据") }) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column {
                        Text("累计概览", style = MaterialTheme.typography.titleLarge)
                        Text("本设备的观看记录", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.isLoading) {
                    item {
                        if (state.snapshot == null) {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (state.error != null) {
                    item {
                        if (state.snapshot == null) {
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
                }
                state.snapshot?.let { snapshot ->
                    item { StatisticsOverview(snapshot.summary) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("观看趋势", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 280.dp).fillMaxWidth()) {
                            listOf(7, 30).forEachIndexed { index, days ->
                                SegmentedButton(
                                    selected = dayCount == days,
                                    onClick = { dayCount = days },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                ) { Text("近 $days 天") }
                            }
                        }
                    }
                }
                // 筛选切换的首帧也不把旧范围的数据放在新标签下。
                if (state.dayCount == dayCount && state.days.isNotEmpty()) {
                    item { StatisticsTrend(state.days) }
                    state.snapshot?.let { snapshot ->
                        item { StatisticsRankings(snapshot) }
                    }
                }
                if (state.hasUnsavedPlayback) {
                    item {
                        Text("部分播放记录尚未保存，当前显示已保存的数据。可稍后刷新。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Text(
                        "所有统计数据均仅在本地存储",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Red.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsOverview(summary: PlaybackStatisticsSummary) {
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("累计观看时长", style = MaterialTheme.typography.labelLarge)
                Text(formatStatisticsDuration(summary.totalPlayedMs), style = MaterialTheme.typography.headlineMedium)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticsMetric("播放次数", summary.playCount.toString(), Modifier.weight(1f))
                StatisticsMetric("看过的视频", summary.videoCount.toString(), Modifier.weight(1f))
                StatisticsMetric("看过的 UP", summary.upCount.toString(), Modifier.weight(1f))
            }
            if (summary.playCount == 0L && summary.totalPlayedMs == 0L) {
                Text("还没有观看数据，播放视频后会在这里记录。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatisticsMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatisticsTrend(days: List<PlaybackDailyStats>) {
    var selectedIndex by rememberSaveable(days.first().localDate, days.last().localDate) { mutableIntStateOf(days.lastIndex) }
    val selectedDay = days[selectedIndex]
    val totalMs = days.sumOf { it.totalPlayedMs }
    val totalCount = days.sumOf { it.playCount }
    val maximum = days.maxOf { it.totalPlayedMs }.coerceAtLeast(1L)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${days.first().localDate} 至 ${days.last().localDate}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatStatisticsDuration(totalMs), style = MaterialTheme.typography.headlineSmall)
            Text("$totalCount 次播放 · 日均 ${formatStatisticsDuration(totalMs / days.size)} · " +
                "${days.count { it.totalPlayedMs > 0L || it.playCount > 0L }} 天有观看",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (totalMs == 0L) "这段时间还没有观看时长" else "单日最高 ${formatStatisticsDuration(maximum)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (days.size == 7) 6.dp else 2.dp)) {
                        days.forEachIndexed { index, day ->
                            val isSelected = selectedIndex == index
                            val fraction = (day.totalPlayedMs.toDouble() / maximum).toFloat()
                            Surface(
                                onClick = { selectedIndex = index },
                                color = Color.Transparent,
                                modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                                    selected = isSelected
                                    contentDescription = "${day.localDate}，${formatStatisticsDuration(day.totalPlayedMs)}，${day.playCount} 次播放"
                                },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                    if (day.totalPlayedMs > 0L) {
                                        Box(Modifier.widthIn(max = 32.dp).fillMaxWidth()
                                            .fillMaxHeight(fraction.coerceAtLeast(0.015f))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                            ))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(days.first(), days[days.size / 2], days.last()).forEach {
                    Text(it.localDate.substring(5).replace('-', '/'), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    IconButton(onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) }, enabled = selectedIndex > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "前一天", Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(selectedDay.localDate, style = MaterialTheme.typography.labelMedium)
                        Text("${formatStatisticsDuration(selectedDay.totalPlayedMs)} · ${selectedDay.playCount} 次播放",
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    IconButton(onClick = { selectedIndex = (selectedIndex + 1).coerceAtMost(days.lastIndex) }, enabled = selectedIndex < days.lastIndex, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "后一天", Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsRankings(snapshot: PlaybackStatisticsSnapshot) {
    var showUps by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("观看时长排行", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 280.dp).fillMaxWidth()) {
            listOf("视频", "UP 主").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = showUps == (index == 1), onClick = { showUps = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            if (showUps) {
                if (snapshot.ups.isEmpty()) {
                    Text("这段时间还没有 UP 主观看数据", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                }
                snapshot.ups.forEachIndexed { index, up ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    StatisticsRankingRow(index + 1, up.upName ?: "UP ${up.upMid}",
                        "${up.playCount} 次播放", up.totalPlayedMs)
                }
            } else {
                if (snapshot.videos.isEmpty()) {
                    Text("这段时间还没有视频观看数据", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                }
                snapshot.videos.forEachIndexed { index, video ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    val title = if (video.contentType == "media" && !video.partTitle.isNullOrBlank()) {
                        "${video.title} · ${video.partTitle}"
                    } else video.title
                    StatisticsRankingRow(index + 1, title,
                        "${video.playCount} 次播放 · ${if (video.contentType == "media") "番剧影视" else "视频"}", video.totalPlayedMs)
                }
            }
        }
    }
}

@Composable
private fun StatisticsRankingRow(rank: Int, title: String, subtitle: String, totalPlayedMs: Long) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
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
