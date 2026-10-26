package tv.hsrui.bolo.view.live.danmaku

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuBubbleData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData
import kotlin.math.roundToInt

@Composable
internal fun rememberLiveDanmakuImagePainter(url: String?): Painter? {
    if (url.isNullOrBlank()) return null
    return key(url) {
        val context = LocalPlatformContext.current
        val request = remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .size(coil3.size.Size.ORIGINAL)
                .build()
        }
        val painter = rememberAsyncImagePainter(request)
        val state by painter.state.collectAsState()
        (state as? AsyncImagePainter.State.Success)?.painter?.takeIf {
            it.intrinsicSize.width.isFinite() && it.intrinsicSize.width > 0 &&
                it.intrinsicSize.height.isFinite() && it.intrinsicSize.height > 0
        }
    }
}

@Composable
internal fun LiveDanmakuGuardBadge(painter: Painter, modifier: Modifier = Modifier) {
    Image(painter = painter, contentDescription = null, modifier = modifier)
}

@Composable
internal fun LiveDanmakuFansMedal(
    medal: LiveDanmakuFansMedalData,
    modifier: Modifier = Modifier,
    guardPainter: Painter? = null,
) {
    val start = medal.startColorArgb?.let(::Color) ?: Color(0xCC979EBD)
    val end = medal.endColorArgb?.let(::Color) ?: start
    val border = medal.borderColorArgb?.let(::Color) ?: start
    val text = medal.textColorArgb?.let(::Color) ?: Color.White
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    Box(modifier.semantics { contentDescription = "粉丝勋章 ${medal.name} ${medal.level}级" }) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(16.dp),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = text,
            border = BorderStroke(1.dp, border),
        ) {
            Row(
                Modifier.background(Brush.horizontalGradient(listOf(start, end)))
                    .padding(start = if (guardPainter != null) 20.dp else 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = medal.name,
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = medal.level.toString(),
                    modifier = Modifier.padding(start = 2.dp),
                    style = textStyle.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        if (guardPainter != null) {
            LiveDanmakuGuardBadge(
                painter = guardPainter,
                modifier = Modifier.align(Alignment.CenterStart).size(20.dp),
            )
        }
    }
}

@Composable
internal fun LiveDanmakuRoleBadge(isAnchor: Boolean, modifier: Modifier = Modifier) {
    val color = if (isAnchor) BiliColor.ThemeColor else BiliColor.Blue
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
        contentColor = LocalContentColor.current,
        border = BorderStroke(1.dp, color),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isAnchor) "主播" else "房管",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                maxLines = 1,
            )
        }
    }
}

internal fun Modifier.liveDanmakuBubble(
    colorArgb: Long?,
    bubble: LiveDanmakuBubbleData?,
    painter: Painter?,
    firstLinePainter: Painter?,
): Modifier = drawWithCache {
    onDrawBehind {
        colorArgb?.let { drawRect(Color(it)) }
        if (bubble != null && painter != null) {
            val width = bubble.width.toFloat()
            val height = bubble.height.toFloat()
            drawLiveDanmakuBubblePatch(
                painter = painter,
                source = Size(width, height),
                target = size,
                left = width / 2 - (if (bubble.isFleet) 10 else 1),
                top = height / 2,
                right = width / 2 + (if (bubble.isFleet) 3 else 0),
                bottom = height / 2 - 1,
                horizontalBorder = (width / 5).dp.toPx(),
                verticalBorder = (height / 5).dp.toPx(),
            )
            if (bubble.isFleet && firstLinePainter != null) {
                clipRect {
                    drawLiveDanmakuBubblePatch(
                        painter = firstLinePainter,
                        source = firstLinePainter.intrinsicSize,
                        target = Size(size.width, 62.dp.toPx()),
                        left = 10f,
                        top = 167f,
                        right = 300f,
                        bottom = 0f,
                        horizontalBorder = 62.dp.toPx(),
                        verticalBorder = 31.dp.toPx(),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLiveDanmakuBubblePatch(
    painter: Painter,
    source: Size,
    target: Size,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    horizontalBorder: Float,
    verticalBorder: Float,
) {
    if (target.width <= 0 || target.height <= 0) return
    val sourceScaleX = minOf(1f, source.width / (left + right).coerceAtLeast(1f))
    val sourceScaleY = minOf(1f, source.height / (top + bottom).coerceAtLeast(1f))
    val borderScale = minOf(1f, target.width / (horizontalBorder * 2), target.height / (verticalBorder * 2))
    val borderX = (horizontalBorder * borderScale).roundToInt().toFloat().coerceAtMost(target.width / 2)
    val borderY = (verticalBorder * borderScale).roundToInt().toFloat().coerceAtMost(target.height / 2)
    val sx = floatArrayOf(0f, left.coerceAtLeast(0f) * sourceScaleX, source.width - right * sourceScaleX, source.width)
    val sy = floatArrayOf(0f, top * sourceScaleY, source.height - bottom * sourceScaleY, source.height)
    val dx = floatArrayOf(0f, borderX, target.width - borderX, target.width)
    val dy = floatArrayOf(0f, borderY, target.height - borderY, target.height)
    for (y in 0..2) {
        for (x in 0..2) {
            val sourceWidth = sx[x + 1] - sx[x]
            val sourceHeight = sy[y + 1] - sy[y]
            val targetWidth = dx[x + 1] - dx[x]
            val targetHeight = dy[y + 1] - dy[y]
            if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) continue
            val scaleX = targetWidth / sourceWidth
            val scaleY = targetHeight / sourceHeight
            clipRect(dx[x], dy[y], dx[x + 1], dy[y + 1]) {
                withTransform({
                    translate(dx[x] - sx[x] * scaleX, dy[y] - sy[y] * scaleY)
                    scale(scaleX, scaleY, Offset.Zero)
                }) {
                    with(painter) { draw(source) }
                }
            }
        }
    }
}
