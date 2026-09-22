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
import kotlin.math.ceil
import kotlin.math.min

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
    val stepFraction = data.stepSeconds / (durationMs.toDouble() / 1_000.0)
    if (!stepFraction.isFinite() || stepFraction <= 0.0) return null
    // 保留越过右边界的首个点，以裁切曲线而非压缩整片时间轴。
    val lastIndex = min(data.samples.lastIndex, ceil(1.0 / stepFraction).toInt())
    val maximum = (0..lastIndex).maxOf { data.samples[it].takeIf { value -> value.isFinite() && value >= 0.0 } ?: 0.0 }
    if (lastIndex < 1 || maximum <= 0.0) return null
    fun sampleY(index: Int): Float {
        val value = data.samples[index].takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        return (0.8 * (1.0 - value / maximum)).toFloat()
    }
    return Path().apply {
        moveTo(0f, 1f)
        var previousX = 0f
        var previousY = sampleY(0)
        lineTo(previousX, previousY)
        for (index in 1..lastIndex) {
            val x = (index * stepFraction).toFloat()
            val y = sampleY(index)
            val middleX = (previousX + x) / 2f
            cubicTo(middleX, previousY, middleX, y, x, y)
            previousX = x
            previousY = y
        }
        if (previousX < 1f) {
            val zeroX = min(1.0, (lastIndex + 1.0) * stepFraction).toFloat()
            val middleX = (previousX + zeroX) / 2f
            cubicTo(middleX, previousY, middleX, 0.8f, zeroX, 0.8f)
            lineTo(1f, 0.8f)
        }
        lineTo(maxOf(1f, previousX), 1f)
        close()
    }
}
