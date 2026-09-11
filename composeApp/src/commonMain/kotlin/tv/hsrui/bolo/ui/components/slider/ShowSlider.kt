package tv.hsrui.bolo.ui.components.slider

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNot
import kotlin.math.roundToInt

/**
 * 只负责滑块交互与外观；数值映射、预览和提交策略由调用方持有。
 * [uniformTrackColor] 让覆盖区域沿用未覆盖区域的轨道和刻度颜色。
 * [tickValues] 仅筛选可见的离散刻度（取最近档位）；null 显示全部，不改变 [steps]。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    centered: Boolean = false,
    uniformTrackColor: Boolean = false,
    showStops: Boolean = true,
    showTicks: Boolean = true,
    tickValues: List<Float>? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: SliderColors = SliderDefaults.colors(),
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.start < valueRange.endInclusive)
    require(steps >= 0)
    require(tickValues == null || tickValues.all { it.isFinite() && it in valueRange })
    val thumbInteractionSource = remember(interactionSource) {
        object : MutableInteractionSource by interactionSource {
            override val interactions = interactionSource.interactions.filterNot {
                it is FocusInteraction.Focus || it is FocusInteraction.Unfocus
            }
        }
    }
    val sliderColors = if (uniformTrackColor) {
        colors.copy(
            activeTrackColor = colors.inactiveTrackColor,
            activeTickColor = colors.inactiveTickColor,
            disabledActiveTrackColor = colors.disabledInactiveTrackColor,
            disabledActiveTickColor = colors.disabledInactiveTickColor,
        )
    } else colors
    val stopColor = if (uniformTrackColor) {
        if (enabled) sliderColors.inactiveTickColor else sliderColors.disabledInactiveTickColor
    } else {
        if (enabled) sliderColors.activeTrackColor else sliderColors.disabledActiveTrackColor
    }
    val visibleTickIndices = remember(tickValues, valueRange, steps) {
        tickValues?.map { tick ->
            ((tick - valueRange.start) / (valueRange.endInclusive - valueRange.start) * (steps + 1)).roundToInt()
        }?.toSet()
    }
    val drawStop: (DrawScope.(Offset) -> Unit)? = if (showStops) {
        { offset ->
            with(SliderDefaults) { drawStopIndicator(offset, TrackStopIndicatorSize, stopColor) }
        }
    } else null
    val drawTick: DrawScope.(Offset, Color) -> Unit = { offset, color ->
        if (showTicks) {
            // M3 将刻度放在两端圆角中心之间，按相同坐标还原档位；绘制和手柄避让仍由 M3 完成。
            val corner = size.height / 2f
            val tickSpan = size.width - corner * 2f
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - offset.x else offset.x
            val index = if (tickSpan > 0f) ((x - corner) / tickSpan * (steps + 1)).roundToInt() else 0
            if (visibleTickIndices == null || index in visibleTickIndices) {
                with(SliderDefaults) { drawStopIndicator(offset, TickSize, color) }
            }
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = modifier.height(32.dp),
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = sliderColors,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = thumbInteractionSource,
                    enabled = enabled,
                    colors = sliderColors,
                    thumbSize = DpSize(4.dp, 24.dp),
                )
            },
            track = { state ->
                if (centered) {
                    SliderDefaults.CenteredTrack(
                        sliderState = state,
                        modifier = Modifier.height(12.dp),
                        enabled = enabled,
                        colors = sliderColors,
                        drawStopIndicator = drawStop,
                        drawTick = drawTick,
                    )
                } else {
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(12.dp),
                        enabled = enabled,
                        colors = sliderColors,
                        drawStopIndicator = drawStop,
                        drawTick = drawTick,
                    )
                }
            },
        )
    }
}
