package tv.hsrui.bolo.livePlayer.controls

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.livePlayer.session.LivePlaybackTimelineState
import tv.hsrui.bolo.ui.theme.BiliColor
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveProgressBar(
    timeline: LivePlaybackTimelineState,
    onSeek: (Long) -> Unit,
    onPreview: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    var window by remember { mutableStateOf<LivePlaybackTimelineState?>(null) }
    var fraction by remember { mutableStateOf<Float?>(null) }
    val preview by rememberUpdatedState(onPreview)
    DisposableEffect(Unit) { onDispose { preview(null) } }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            if (it is DragInteraction.Cancel) { fraction = null; window = null; preview(null) }
        }
    }
    val shown = window ?: timeline
    val start = shown.ranges.firstOrNull()?.startMs ?: 0
    val end = (shown.latestPositionMs ?: start).coerceAtLeast(start)
    val length = (end - start).coerceAtLeast(1)
    val colors = SliderDefaults.colors(activeTrackColor = BiliColor.ThemeColor, thumbColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.5f))
    Slider(
        value = fraction ?: (((shown.pendingPositionMs ?: shown.positionMs ?: start) - start).toDouble() / length).toFloat().coerceIn(0f, 1f),
        onValueChange = {
            if (window == null) window = timeline
            fraction = it
            val fixed = checkNotNull(window)
            val left = fixed.ranges.firstOrNull()?.startMs ?: 0
            val right = (fixed.latestPositionMs ?: left).coerceAtLeast(left)
            preview(left + ((right - left) * it.toDouble()).roundToLong())
        },
        onValueChangeFinished = {
            fraction?.let { onSeek(if (it >= 1f) Long.MAX_VALUE else start + (length * it.toDouble()).roundToLong()) }
            fraction = null
            window = null
            preview(null)
        },
        enabled = enabled && shown.canSeek && end > start,
        interactionSource = interactions,
        modifier = modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "直播缓存进度" },
        colors = colors,
        thumb = {
            Box(Modifier.size(24.dp)) {
                SliderDefaults.Thumb(interactions, colors = colors, thumbSize = DpSize(12.dp, 12.dp), modifier = Modifier.align(Alignment.Center))
            }
        },
        track = { state ->
            Box(Modifier.height(4.dp)) {
                SliderDefaults.Track(state, colors = colors.copy(activeTrackColor = Color.White.copy(alpha = 0.15f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)), thumbTrackGapSize = 0.dp, modifier = Modifier.height(4.dp))
                SliderDefaults.Track(state, colors = colors, thumbTrackGapSize = 0.dp,
                    modifier = Modifier.height(4.dp).drawWithContent {
                        for (range in shown.ranges) {
                            val from = ((range.startMs - start).toDouble() / length).toFloat().coerceIn(0f, 1f)
                            val to = ((range.endMs - start).toDouble() / length).toFloat().coerceIn(0f, 1f)
                            val left = if (layoutDirection == LayoutDirection.Rtl) 1f - to else from
                            val right = if (layoutDirection == LayoutDirection.Rtl) 1f - from else to
                            clipRect(left = left * size.width, right = right * size.width) { this@drawWithContent.drawContent() }
                        }
                    })
            }
        },
    )
}
