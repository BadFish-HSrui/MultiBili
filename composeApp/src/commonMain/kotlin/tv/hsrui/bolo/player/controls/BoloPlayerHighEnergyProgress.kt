package tv.hsrui.bolo.player.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.player.HighEnergyProgressData
import kotlin.math.floor

@Composable
internal fun BoloPlayerHighEnergyProgress(
    data: HighEnergyProgressData,
    durationMs: Long,
    positionFraction: () -> Float,
    controlsFraction: () -> Float,
    shownTrackBounds: () -> Rect?,
    hiddenBounds: () -> Rect,
    modifier: Modifier = Modifier,
) {
    val path = remember(data, durationMs) { buildHighEnergyProgressPath(data, durationMs) }
    Box(modifier.drawWithCache {
        // 路径使用归一化坐标；显隐动画与进度更新只改变变换和裁切。
        val height = 32.dp.toPx()
        onDrawBehind {
            if (path == null) return@onDrawBehind
            val hidden = hiddenBounds()
            if (hidden.width <= 0f || hidden.height <= 0f) return@onDrawBehind
            val shown = shownTrackBounds()
            val fraction = controlsFraction().coerceIn(0f, 1f)
            val left = hidden.left + ((shown?.left ?: hidden.left) - hidden.left) * fraction
            val right = hidden.right + ((shown?.right ?: hidden.right) - hidden.right) * fraction
            val bottom = hidden.bottom + ((shown?.top ?: hidden.bottom) - hidden.bottom) * fraction
            val width = right - left
            if (width <= 0f) return@onDrawBehind
            val played = positionFraction().coerceIn(0f, 1f)
            val rtl = layoutDirection == LayoutDirection.Rtl
            clipRect(hidden.left, hidden.top, hidden.right, hidden.bottom) {
                withTransform({
                    translate(if (rtl) right else left, bottom - height)
                    scale(if (rtl) -width else width, height, pivot = Offset.Zero)
                }) {
                    clipRect(0f, 0f, 1f, 1f) {
                        clipPath(path) {
                            // 两段分别填充，避免叠色使已播放区域透明度变大。
                            drawRect(BiliColor.ThemeColor.copy(alpha = 0.5f), size = Size(played, 1f))
                            drawRect(Color.White.copy(alpha = 0.5f), topLeft = Offset(played, 0f), size = Size(1f - played, 1f))
                        }
                    }
                }
            }
        }
    })
}

private fun buildHighEnergyProgressPath(data: HighEnergyProgressData, durationMs: Long): Path? {
    if (durationMs <= 0L || !data.stepSeconds.isFinite() || data.stepSeconds <= 0.0 || data.samples.size < 2) return null
    val sampleCount = maxOf(data.samples.size.toDouble(), floor(durationMs / 1_000.0 / data.stepSeconds))
    if (!sampleCount.isFinite()) return null
    val maximum = data.samples.maxOf { it.takeIf { value -> value.isFinite() && value >= 0.0 } ?: 0.0 }
    if (maximum <= 0.0) return null
    fun sampleY(index: Int): Float {
        val value = data.samples.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        return (0.8 * (1.0 - value / maximum)).toFloat()
    }
    return Path().apply {
        moveTo(0f, 1f)
        var previousY = 0.8f
        lineTo(0f, previousY)
        // 采样按总点数均匀铺满，末次真实采样之后始终保留一个零热度收尾点。
        for (index in 1..data.samples.size) {
            val x = (index / sampleCount).toFloat()
            val y = sampleY(index)
            val middleX = ((index - 0.5) / sampleCount).toFloat()
            cubicTo(middleX, previousY, middleX, y, x, y)
            previousY = y
        }
        // 缺失尾部的连续零采样是一条水平线，无需逐点分配或绘制。
        lineTo(1f, 0.8f)
        lineTo(1f, 1f)
        close()
    }
}
