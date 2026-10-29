package tv.hsrui.bolo.livePlayer.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import tv.hsrui.bolo.ui.components.image.ShowImage
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuEmoticonData
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private class LiveDanmakuLayout(
    val text: TextLayoutResult,
    val images: List<LiveDanmakuEmoticonData>,
)

@Composable
fun LiveDanmakuLayer(controller: LiveDanmakuController, modifier: Modifier = Modifier) {
    val settings = controller.settings
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val family = MaterialTheme.typography.bodyLarge.fontFamily
    val measurer = rememberTextMeasurer(cacheSize = 512)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var entries by remember(controller) { mutableStateOf<List<LiveDanmakuEngine.Entry>>(emptyList()) }
    var time by remember(controller) { mutableStateOf(0L) }
    val clock = remember(controller) { TimeSource.Monotonic.markNow() }
    val scale = settings.scale
    val cache = remember(measurer, density, direction, family, scale) {
        LinkedHashMap<LiveDanmakuData, LiveDanmakuLayout>()
    }
    fun layout(data: LiveDanmakuData): LiveDanmakuLayout = cache.getOrPut(data) {
        if (cache.size >= 512) cache.remove(cache.keys.first())
        val fontSize = (data.fontSize.takeIf { it.isFinite() && it > 0 } ?: 25f) * scale
        val text = StringBuilder()
        val images = mutableListOf<LiveDanmakuEmoticonData>()
        val placeholders = mutableListOf<AnnotatedString.Range<Placeholder>>()
        fun image(value: LiveDanmakuEmoticonData, factor: Float) {
            val start = text.length
            text.append('\uFFFC')
            images.add(value)
            val height = fontSize * factor
            placeholders.add(AnnotatedString.Range(
                Placeholder((height * value.width / value.height.coerceAtLeast(1)).sp, height.sp, PlaceholderVerticalAlign.TextCenter),
                start, start + 1,
            ))
        }
        val standalone = data.emoticon
        if (standalone != null) image(standalone, 2f) else {
            val tokens = data.inlineEmoticons.keys.filter(String::isNotEmpty).sortedByDescending(String::length)
            val matches = if (tokens.isEmpty()) emptyList() else
                Regex(tokens.joinToString("|", transform = Regex::escape)).findAll(data.content).toList()
            var cursor = 0
            for (match in matches) {
                text.append(data.content.substring(cursor, match.range.first))
                image(data.inlineEmoticons.getValue(match.value), 1f)
                cursor = match.range.last + 1
            }
            text.append(data.content.substring(cursor))
        }
        LiveDanmakuLayout(
            measurer.measure(
                AnnotatedString(text.toString()),
                style = TextStyle(fontFamily = family, fontSize = fontSize.sp),
                softWrap = false,
                maxLines = 1,
                placeholders = placeholders,
            ), images,
        )
    }
    DisposableEffect(controller) {
        controller.attach(true)
        onDispose { controller.attach(false) }
    }
    val running = controller.isActive
    val area = settings.displayAreaRatio
    val speed = settings.speed
    val verticalGap = if (settings.extraLineSpacingEnabled) 4 * density.density else 0f
    val topBottomScroll = settings.topBottomScrollEnabled
    val scroll = settings.scrollEnabled
    val top = settings.topEnabled
    val bottom = settings.bottomEnabled
    LaunchedEffect(controller, running, size, cache, area, speed, verticalGap, topBottomScroll, scroll, top, bottom) {
        if (!running) { entries = emptyList(); return@LaunchedEffect }
        while (isActive) {
            withFrameNanos {
                entries = controller.engine.frame(
                    clock.elapsedNow().inWholeNanoseconds / 1_000_000.0,
                    size.width.toFloat(), size.height.toFloat(), density.density,
                    scale * density.fontScale, area, speed, verticalGap, topBottomScroll, settings::allows,
                ) { data ->
                    val result = layout(data).text
                    (result.size.width + 4 * density.density) to (result.size.height + 4 * density.density)
                }
                time++
            }
        }
    }
    Box(modifier.fillMaxSize().clipToBounds().alpha(settings.effectiveAlpha).onSizeChanged { size = it }) {
        time
        if (running) entries.forEach { entry ->
            if (settings.allows(entry.data.mode)) key(entry.key) {
                LiveDanmakuOverlayItem(entry, layout(entry.data), entry.x, entry.y, entry.width, entry.height)
            }
        }
    }
}

@Composable
private fun LiveDanmakuOverlayItem(
    entry: LiveDanmakuEngine.Entry,
    layout: LiveDanmakuLayout,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    val density = LocalDensity.current
    val inset = with(density) { 2.dp.toPx() }
    Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        .size(with(density) { width.toDp() }, with(density) { height.toDp() })) {
        Canvas(Modifier.fillMaxSize()) {
            drawText(layout.text, color = Color.Black, topLeft = Offset(inset, inset), drawStyle = Stroke(inset))
            drawText(layout.text, color = Color(entry.data.colorRgb or 0xFF000000), topLeft = Offset(inset, inset))
        }
        layout.text.placeholderRects.forEachIndexed { index, rect ->
            if (rect != null) {
                Box(Modifier.offset { IntOffset((rect.left + inset).roundToInt(), (rect.top + inset).roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })) {
                    LiveDanmakuOverlayEmoticon(layout.images[index])
                }
            }
        }
    }
}

@Composable
private fun LiveDanmakuOverlayEmoticon(emoticon: LiveDanmakuEmoticonData) {
    ShowImage(
        url = emoticon.url,
        contentDescription = emoticon.text,
        modifier = Modifier.fillMaxSize(),
        animationEnabled = true,
        fallbackContent = { Text(emoticon.text.ifBlank { "表情" }, color = Color.White, maxLines = 1) },
    )
}
