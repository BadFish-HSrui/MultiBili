package tv.hsrui.bolo.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scrim
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoloPlayerSettingsSheet(
    isOpen: Boolean,
    danmakuScale: Float,
    onDanmakuScaleChange: (Float) -> Unit,
    danmakuSpeed: Float,
    onDanmakuSpeedChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val layoutDirection = LocalLayoutDirection.current
    val onDismiss by rememberUpdatedState(onDismissRequest)
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    val scrimAlpha by animateFloatAsState(if (isOpen) 1f else 0f)

    LaunchedEffect(isOpen) {
        if (isOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState) {
        var wasOpen = false
        snapshotFlow { drawerState.currentValue }.collect { value ->
            if (value == DrawerValue.Open) {
                wasOpen = true
            } else if (wasOpen) {
                wasOpen = false
                onDismiss()
            }
        }
    }
    NavigationBackHandler(
        state = backState,
        isBackEnabled = isOpen || !drawerState.isClosed,
        onBackCompleted = onDismissRequest,
    )

    if (!isOpen && drawerState.isClosed && !drawerState.isAnimationRunning) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetWidth = min(300.dp, maxWidth * 0.85f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                // 默认遮罩的关闭操作受 gesturesEnabled 限制，单独复用 Scrim 处理点击。
                scrimColor = Color.Unspecified,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        ModalDrawerSheet(
                            modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                            drawerShape = AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp),
                            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
                        ) {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DanmakuPercentageSlider(
                                    label = "弹幕缩放",
                                    value = danmakuScale,
                                    isOpen = isOpen,
                                    onValueChange = onDanmakuScaleChange,
                                )
                                DanmakuPercentageSlider(
                                    label = "弹幕速度",
                                    value = danmakuSpeed,
                                    isOpen = isOpen,
                                    onValueChange = onDanmakuSpeedChange,
                                )
                            }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Scrim(
                        contentDescription = "关闭播放设置",
                        onClick = onDismissRequest,
                        alpha = { scrimAlpha },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DanmakuPercentageSlider(
    label: String,
    value: Float,
    isOpen: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    var previewPercent by remember { mutableStateOf<Int?>(null) }
    val interactionSource = remember {
        val source = MutableInteractionSource()
        object : MutableInteractionSource by source {
            // Slider 在拖动取消后也可能调用结束回调，先同步丢弃预览以免误保存。
            override suspend fun emit(interaction: Interaction) {
                if (interaction is DragInteraction.Cancel) previewPercent = null
                source.emit(interaction)
            }

            override fun tryEmit(interaction: Interaction): Boolean {
                if (interaction is DragInteraction.Cancel) previewPercent = null
                return source.tryEmit(interaction)
            }
        }
    }
    LaunchedEffect(isOpen) { previewPercent = null }
    val percent = previewPercent ?: (value * 100f).roundToInt()
    // 两侧数值跨度不同，分段映射使 100% 对应轨道正中。
    val sliderPosition = if (percent <= 100) (percent - 100) / 50f
    else (percent - 100) / 100f
    fun commitPreview() {
        if (isOpen) {
            previewPercent?.let { onValueChange(it / 100f) }
        }
        previewPercent = null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text("$percent%", style = MaterialTheme.typography.bodyMedium)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Slider(
            value = sliderPosition,
            onValueChange = { position ->
                previewPercent = (100f + position * if (position <= 0f) 50f else 100f)
                    .roundToInt().coerceIn(50, 200)
            },
            onValueChangeFinished = ::commitPreview,
            valueRange = -1f..1f,
            enabled = isOpen,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = isOpen,
                    thumbSize = DpSize(4.dp, 32.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.CenteredTrack(
                    sliderState = sliderState,
                    enabled = isOpen,
                    modifier = Modifier.height(12.dp),
                )
            },
            modifier = Modifier.fillMaxWidth()
                .height(32.dp)
                .onPreviewKeyEvent { event ->
                    if (!isOpen) return@onPreviewKeyEvent false
                    val forward = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
                    val target = when (event.key) {
                        Key.DirectionRight -> percent + forward
                        Key.DirectionLeft -> percent - forward
                        Key.MoveHome -> 50
                        Key.MoveEnd -> 200
                        Key.PageUp -> percent + 10
                        Key.PageDown -> percent - 10
                        else -> return@onPreviewKeyEvent false
                    }
                    when (event.type) {
                        KeyEventType.KeyDown -> previewPercent = target.coerceIn(50, 200)
                        KeyEventType.KeyUp -> commitPreview()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .semantics {
                    contentDescription = label
                    stateDescription = "$percent%"
                    progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), 50f..200f, 149)
                    setProgress { target ->
                        if (!isOpen) return@setProgress false
                        val next = target.roundToInt().coerceIn(50, 200)
                        if (next == percent) return@setProgress false
                        previewPercent = next
                        commitPreview()
                        true
                    }
                },
        )
    }
}
