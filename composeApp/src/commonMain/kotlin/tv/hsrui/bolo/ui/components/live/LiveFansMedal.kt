package tv.hsrui.bolo.ui.components.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData

@Composable
fun rememberLiveDecorationPainter(url: String?): Painter? {
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
fun ShowLiveFansMedal(
    medal: LiveDanmakuFansMedalData?,
    modifier: Modifier = Modifier,
    guardPainter: Painter? = null,
    scale: Float = 1f,
) {
    val name = medal?.name ?: "null"
    val level = medal?.level?.toString()
    val fallbackColor = if (medal == null) Color(0xFF9E9E9E) else Color(0xCC979EBD)
    val start = medal?.startColorArgb?.let(::Color) ?: fallbackColor
    val end = medal?.endColorArgb?.let(::Color) ?: start
    val border = medal?.borderColorArgb?.let(::Color) ?: start
    val text = medal?.textColorArgb?.let(::Color) ?: Color.White
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp * scale,
        lineHeight = 12.sp * scale,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val levelStyle = textStyle.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val nameWidth = measurer.measure(name, textStyle, maxLines = 1).size.width
    val levelWidth = level?.let { measurer.measure(it, levelStyle, maxLines = 1).size.width } ?: 0
    val startPadding = (if (guardPainter != null) 20.dp else 6.dp) * scale
    val endPadding = 6.dp * scale
    val levelSpacing = if (level != null) 2.dp * scale else 0.dp
    val iconSize = 20.dp * scale
    val badgeHeight = 16.dp * scale
    val textWidth = with(LocalDensity.current) { (nameWidth + levelWidth).toDp() }
    val width = textWidth + startPadding + endPadding + levelSpacing
    Box(
        modifier.width(width).height(if (guardPainter != null) iconSize else badgeHeight).semantics {
            contentDescription = if (medal == null) "未佩戴粉丝勋章" else "粉丝勋章 ${medal.name} ${medal.level}级"
        },
    ) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(badgeHeight),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = text,
            border = BorderStroke(1.dp * scale, border),
        ) {
            Row(
                Modifier.background(Brush.horizontalGradient(listOf(start, end)))
                    .padding(start = startPadding, end = endPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (level != null) Text(
                    text = level,
                    modifier = Modifier.padding(start = levelSpacing),
                    style = levelStyle,
                    maxLines = 1,
                )
            }
        }
        if (guardPainter != null) {
            Image(
                contentDescription = null,
                painter = guardPainter,
                modifier = Modifier.align(Alignment.CenterStart).size(iconSize),
            )
        }
    }
}
