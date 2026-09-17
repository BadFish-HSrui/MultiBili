package tv.hsrui.bolo.player.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings

@Composable
fun BoloDanmakuLayer(
    controller: BoloDanmakuController,
    modifier: Modifier = Modifier,
) {
    val settings: BoloSettings = koinInject()
    val danmakuScale = settings.danmakuScale
    val danmakuSpeed = settings.danmakuSpeed
    val danmakuDisplayAreaRatio = settings.danmakuDisplayAreaRatio
    val danmakuTopBottomScrollEnabled = settings.danmakuTopBottomScrollEnabled
    val danmakuFilterLevel = settings.danmakuFilterLevel
    val danmakuExtraLineSpacingEnabled = settings.danmakuExtraLineSpacingEnabled
    val danmakuScrollEnabled = settings.danmakuScrollEnabled
    val danmakuTopEnabled = settings.danmakuTopEnabled
    val danmakuBottomEnabled = settings.danmakuBottomEnabled
    val fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember { Any() }
    val frame = remember(controller) { mutableLongStateOf(0L) }
    val layouts = remember(controller, measurer, density, direction, fontFamily) {
        LinkedHashMap<BoloDanmakuItem, TextLayoutResult>()
    }

    DisposableEffect(controller, layouts) {
        controller.attach(owner)
        onDispose {
            layouts.clear()
            controller.detach(owner)
        }
    }
    LaunchedEffect(controller, lifecycleOwner, layouts) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                launch {
                    controller.changes.collect {
                        frame.longValue += 1
                        if (controller.isDisposed) layouts.clear()
                    }
                }
                controller.state.map { it.isPlaying && it.isVisible }.distinctUntilChanged().collectLatest { running ->
                    frame.longValue += 1
                    while (running && isActive && !controller.isDisposed) {
                        withFrameNanos { frame.longValue += 1 }
                    }
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        frame.longValue
        if (controller.isDisposed || !controller.state.value.isVisible) return@Canvas
        val padding = 2f * density.density
        controller.engine.resize(
            width = size.width,
            height = size.height,
            verticalGap = if (danmakuExtraLineSpacingEnabled) padding * 2 else 0f,
            horizontalGap = padding * 2,
            displayAreaRatio = danmakuDisplayAreaRatio,
            topBottomScrollEnabled = danmakuTopBottomScrollEnabled,
        )
        fun layout(item: BoloDanmakuItem): TextLayoutResult = layouts.getOrPut(item) {
            if (layouts.size >= 512) layouts.remove(layouts.keys.first())
            measurer.measure(
                text = item.content,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = (item.fontSize.takeIf { it.isFinite() && it > 0f } ?: 25f).sp,
                ),
                softWrap = false,
                maxLines = 1,
            )
        }
        val animationTimeMs = controller.animationTimeMs()
        val entries = controller.engine.frame(
            animationTimeMs,
            fontScale = danmakuScale,
            speedFactor = danmakuSpeed,
            filterLevel = danmakuFilterLevel,
            scrollEnabled = danmakuScrollEnabled,
            topEnabled = danmakuTopEnabled,
            bottomEnabled = danmakuBottomEnabled,
            baseSpeed = { viewportWidth, textWidth ->
                calculateBiliDanmakuSpeed(viewportWidth / density.density, textWidth / density.density) * density.density
            },
        ) { item ->
            val result = layout(item)
            (result.size.width + padding * 2) to result.size.height.toFloat()
        }
        clipRect {
            for (entry in entries) {
                val result = layout(entry.item)
                val offset = Offset(entry.x(animationTimeMs, size.width) + padding, entry.y)
                drawText(result, color = Color.Black, topLeft = offset, drawStyle = Stroke(width = padding))
                drawText(result, color = Color((entry.item.colorRgb and 0xFFFFFF) or 0xFF000000), topLeft = offset, drawStyle = Fill)
            }
        }
    }
}
