package tv.hsrui.bolo.player.subtitle

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import kotlin.math.roundToInt

@Composable
fun BoloSubtitleLayer(
    controller: BoloSubtitleController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val settings: BoloSettings = koinInject()
    val scale = settings.effectiveSubtitleScale
    val heightRatio = settings.effectiveSubtitleHeightRatio
    val backgroundAlpha = settings.effectiveSubtitleBackgroundAlpha
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        textAlign = TextAlign.Center,
        shadow = Shadow(Color.Black, Offset(1f, 1f), 4f),
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (state.text.isEmpty()) return@BoxWithConstraints
        val horizontalMargin = with(density) { 24.dp.roundToPx() }
        val horizontalPadding = with(density) { 8.dp.roundToPx() * 2 }
        val verticalPadding = with(density) { 4.dp.roundToPx() * 2 }
        val cardWidth = (constraints.maxWidth - horizontalMargin * 2).coerceAtLeast(0)
        val textWidth = (cardWidth - horizontalPadding).coerceAtLeast(0)
        val textHeight = (constraints.maxHeight - verticalPadding).coerceAtLeast(0)
        if (textWidth == 0 || textHeight == 0) return@BoxWithConstraints
        val fittedScale = remember(state.text, scale, textWidth, textHeight, baseStyle, measurer, density) {
            fun fits(candidate: Float): Boolean {
                val result = measurer.measure(
                    text = state.text,
                    style = baseStyle.copy(fontSize = (32f * candidate).sp, lineHeight = (40f * candidate).sp),
                    constraints = Constraints(maxWidth = textWidth),
                )
                return result.size.height <= textHeight && !result.didOverflowWidth
            }
            if (fits(scale)) scale else {
                // 只缩小当前字幕；换行后的高度必须重新测量，不能仅按高度比例估算。
                var low = 0f
                var high = scale
                repeat(20) {
                    val middle = (low + high) / 2f
                    if (fits(middle)) low = middle else high = middle
                }
                low
            }
        }
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = backgroundAlpha))) {
                    Text(
                        text = state.text,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = baseStyle.copy(
                            fontSize = (32f * fittedScale).sp,
                            lineHeight = (40f * fittedScale).sp,
                        ),
                    )
                }
            },
        ) { measurables, bounds ->
            val card = measurables.single().measure(
                Constraints(maxWidth = cardWidth, maxHeight = bounds.maxHeight),
            )
            val top = (bounds.maxHeight * (1f - heightRatio) - card.height / 2f)
                .roundToInt().coerceIn(0, (bounds.maxHeight - card.height).coerceAtLeast(0))
            layout(bounds.maxWidth, bounds.maxHeight) {
                card.place((bounds.maxWidth - card.width) / 2, top)
            }
        }
    }
}
