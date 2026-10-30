package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.storage.appData.playbackStatistics.addPlaybackStatistics

@Composable
fun ShowStatisticsDuration(
    videoPlayedMs: Long,
    livePlayedMs: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Text(
                text = formatStatisticsDuration(addPlaybackStatistics(videoPlayedMs, livePlayedMs)),
                style = style,
            )
            Text(
                text = formatStatisticsDurationBreakdown(videoPlayedMs, livePlayedMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        },
        measurePolicy = statisticsDurationMeasurePolicy,
    )
}

internal fun formatStatisticsDurationBreakdown(videoPlayedMs: Long, livePlayedMs: Long): String =
    "视频 · ${formatStatisticsDuration(videoPlayedMs)} + 直播 · ${formatStatisticsDuration(livePlayedMs)}"

internal fun formatStatisticsPlaybackCounts(videoPlayCount: Long, livePlayCount: Long): String =
    "$videoPlayCount 次视频 · $livePlayCount 次直播"

private val statisticsDurationMeasurePolicy = MeasurePolicy { measurables, constraints ->
    // 两项都先按整行宽度测量，明细放不下时整项换行，而不挤占主时长。
    val children = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    val (main, detail) = children
    val gap = 8.dp.roundToPx()
    val rowWidth = main.width + gap + detail.width
    val sameLine = rowWidth <= constraints.maxWidth
    val width = constraints.constrainWidth(if (sameLine) rowWidth else maxOf(main.width, detail.width))
    val height = constraints.constrainHeight(
        if (sameLine) maxOf(main.height, detail.height) else main.height + 4.dp.roundToPx() + detail.height,
    )
    layout(width, height) {
        if (sameLine) {
            main.placeRelative(0, height - main.height)
            detail.placeRelative(main.width + gap, height - detail.height)
        } else {
            main.placeRelative(0, 0)
            detail.placeRelative(0, main.height + 4.dp.roundToPx())
        }
    }
}
