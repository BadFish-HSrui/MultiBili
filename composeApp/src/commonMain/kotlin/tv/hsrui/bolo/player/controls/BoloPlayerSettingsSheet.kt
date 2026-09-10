package tv.hsrui.bolo.player.controls

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scrim
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNot
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoloPlayerSettingsSheet(
    isOpen: Boolean,
    supportsDeviceGestures: Boolean,
    resumeAfterBackgroundEnabled: Boolean,
    onResumeAfterBackgroundEnabledChange: (Boolean) -> Unit,
    autoPlayAfterSeekEnabled: Boolean,
    onAutoPlayAfterSeekEnabledChange: (Boolean) -> Unit,
    autoReplayEnabled: Boolean,
    onAutoReplayEnabledChange: (Boolean) -> Unit,
    seekGestureEnabled: Boolean,
    onSeekGestureEnabledChange: (Boolean) -> Unit,
    brightnessGestureEnabled: Boolean,
    onBrightnessGestureEnabledChange: (Boolean) -> Unit,
    volumeGestureEnabled: Boolean,
    onVolumeGestureEnabledChange: (Boolean) -> Unit,
    sideDoubleTapSeekEnabled: Boolean,
    onSideDoubleTapSeekEnabledChange: (Boolean) -> Unit,
    doubleTapSeekSeconds: Int,
    onDoubleTapSeekSecondsChange: (Int) -> Unit,
    longPressSpeedGestureEnabled: Boolean,
    onLongPressSpeedGestureEnabledChange: (Boolean) -> Unit,
    longPressSpeed: Float,
    onLongPressSpeedChange: (Float) -> Unit,
    danmakuFilterLevel: Int,
    onDanmakuFilterLevelChange: (Int) -> Unit,
    danmakuScale: Float,
    onDanmakuScaleChange: (Float) -> Unit,
    danmakuSpeed: Float,
    onDanmakuSpeedChange: (Float) -> Unit,
    danmakuDisplayAreaRatio: Float,
    onDanmakuDisplayAreaRatioChange: (Float) -> Unit,
    danmakuTopBottomScrollEnabled: Boolean,
    onDanmakuTopBottomScrollEnabledChange: (Boolean) -> Unit,
    danmakuExtraLineSpacingEnabled: Boolean,
    onDanmakuExtraLineSpacingEnabledChange: (Boolean) -> Unit,
    subtitleAlwaysOn: Boolean,
    onSubtitleAlwaysOnChange: (Boolean) -> Unit,
    subtitleAutoChineseOnly: Boolean,
    onSubtitleAutoChineseOnlyChange: (Boolean) -> Unit,
    subtitleAutoExcludeAi: Boolean,
    onSubtitleAutoExcludeAiChange: (Boolean) -> Unit,
    subtitleScale: Float,
    onSubtitleScaleChange: (Float) -> Unit,
    onSubtitleScalePreview: (Float?) -> Unit,
    subtitleHeightRatio: Float,
    onSubtitleHeightRatioChange: (Float) -> Unit,
    onSubtitleHeightRatioPreview: (Float?) -> Unit,
    subtitleBackgroundAlpha: Float,
    onSubtitleBackgroundAlphaChange: (Float) -> Unit,
    onSubtitleBackgroundAlphaPreview: (Float?) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { BoloPlayerSettingsTab.entries.size }
    val pagerScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val layoutDirection = LocalLayoutDirection.current
    val onDismiss by rememberUpdatedState(onDismissRequest)
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    val scrimAlpha by animateFloatAsState(if (isOpen) 1f else 0f)
    var showResumeAfterBackgroundInfo by remember(isOpen) { mutableStateOf(false) }
    var showAutoPlayAfterSeekInfo by remember(isOpen) { mutableStateOf(false) }
    var showFilterInfo by remember(isOpen) { mutableStateOf(false) }
    var showTopBottomScrollInfo by remember(isOpen) { mutableStateOf(false) }
    var showSubtitlePositionInfo by remember(isOpen) { mutableStateOf(false) }

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
        isBackEnabled = (isOpen || !drawerState.isClosed) &&
            !showFilterInfo && !showTopBottomScrollInfo && !showSubtitlePositionInfo && !showAutoPlayAfterSeekInfo && !showResumeAfterBackgroundInfo,
        onBackCompleted = onDismissRequest,
    )

    if (!isOpen && drawerState.isClosed && !drawerState.isAnimationRunning) return

    if (isOpen && showResumeAfterBackgroundInfo) {
        ShowInfoDialog(onConfirm = { showResumeAfterBackgroundInfo = false }) {
            Text(
                text = "当播放状态下切出应用，恢复后自动继续播放",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (isOpen && showAutoPlayAfterSeekInfo) {
        ShowInfoDialog(onConfirm = { showAutoPlayAfterSeekInfo = false }) {
            Text(
                text = "暂停时调整进度后，自动重新播放",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (isOpen && showFilterInfo) {
        ShowInfoDialog(
            onConfirm = { showFilterInfo = false },
        ) {
            Text(
                text = "弹幕权重来自官方Api返回结果，由B站AI判断",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (isOpen && showTopBottomScrollInfo) {
        ShowInfoDialog(
            onConfirm = { showTopBottomScrollInfo = false },
        ) {
            Text(
                text = "当开启此选项，并设置弹幕显示区域小于50%时，会同时在上下显示滚动弹幕，中间留空。\n" +
                    "通过调整设置，可以在折叠屏或平板上将所有弹幕控制在黑边中，不阻挡显示内容",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (isOpen && showSubtitlePositionInfo) {
        ShowInfoDialog(
            onConfirm = { showSubtitlePositionInfo = false },
        ) {
            Text(
                text = "基于播放器窗口高度，从底部开始计算的字幕中心高度百分比",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

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
                            Column(Modifier.fillMaxSize()) {
                                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                                    BoloPlayerSettingsTab.entries.forEachIndexed { index, tab ->
                                        Tab(
                                            selected = pagerState.currentPage == index,
                                            enabled = isOpen,
                                            onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                                            text = { Text(tab.title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                        )
                                    }
                                }
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    userScrollEnabled = isOpen,
                                    verticalAlignment = Alignment.Top,
                                ) { page ->
                                    Column(
                                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        when (BoloPlayerSettingsTab.entries[page]) {
                                            BoloPlayerSettingsTab.Playback -> {
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column {
                                                        Row(
                                                            Modifier.fillMaxWidth()
                                                                .toggleable(
                                                                    value = autoReplayEnabled,
                                                                    enabled = isOpen,
                                                                    role = Role.Switch,
                                                                    onValueChange = onAutoReplayEnabledChange,
                                                                )
                                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = "自动重播",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            Switch(
                                                                checked = autoReplayEnabled,
                                                                onCheckedChange = null,
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                        HorizontalDivider(thickness = 1.dp)
                                                        Row(
                                                            Modifier.fillMaxWidth()
                                                                .toggleable(
                                                                    value = autoPlayAfterSeekEnabled,
                                                                    enabled = isOpen,
                                                                    role = Role.Switch,
                                                                    onValueChange = onAutoPlayAfterSeekEnabledChange,
                                                                )
                                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text("跳转后自动播放", style = MaterialTheme.typography.bodyMedium)
                                                                IconButton(
                                                                    onClick = { showAutoPlayAfterSeekInfo = true },
                                                                    enabled = isOpen,
                                                                    modifier = Modifier.size(20.dp),
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Info,
                                                                        contentDescription = "跳转后自动播放说明",
                                                                        modifier = Modifier.size(16.dp),
                                                                    )
                                                                }
                                                            }
                                                            Switch(
                                                                checked = autoPlayAfterSeekEnabled,
                                                                onCheckedChange = null,
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                        if (getPlatform().type != PlatformType.Desktop) {
                                                            HorizontalDivider(thickness = 1.dp)
                                                            Row(
                                                                Modifier.fillMaxWidth()
                                                                    .toggleable(
                                                                        value = resumeAfterBackgroundEnabled,
                                                                        enabled = isOpen,
                                                                        role = Role.Switch,
                                                                        onValueChange = onResumeAfterBackgroundEnabledChange,
                                                                    )
                                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.weight(1f),
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                ) {
                                                                    Text("恢复后继续播放", style = MaterialTheme.typography.bodyMedium)
                                                                    IconButton(
                                                                        onClick = { showResumeAfterBackgroundInfo = true },
                                                                        enabled = isOpen,
                                                                        modifier = Modifier.size(20.dp),
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Outlined.Info,
                                                                            contentDescription = "恢复后继续播放说明",
                                                                            modifier = Modifier.size(16.dp),
                                                                        )
                                                                    }
                                                                }
                                                                Switch(
                                                                    checked = resumeAfterBackgroundEnabled,
                                                                    onCheckedChange = null,
                                                                    enabled = isOpen,
                                                                    modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp).animateContentSize()) {
                                                        PlayerGestureSwitch(
                                                            label = "启用进度调节手势",
                                                            checked = seekGestureEnabled,
                                                            enabled = isOpen,
                                                            onCheckedChange = onSeekGestureEnabledChange,
                                                        )
                                                        HorizontalDivider(thickness = 1.dp)
                                                        PlayerGestureSwitch(
                                                            label = "启用亮度调节手势",
                                                            checked = brightnessGestureEnabled,
                                                            enabled = isOpen && supportsDeviceGestures,
                                                            onCheckedChange = onBrightnessGestureEnabledChange,
                                                        )
                                                        HorizontalDivider(thickness = 1.dp)
                                                        PlayerGestureSwitch(
                                                            label = "启用音量调节手势",
                                                            checked = volumeGestureEnabled,
                                                            enabled = isOpen && supportsDeviceGestures,
                                                            onCheckedChange = onVolumeGestureEnabledChange,
                                                        )
                                                        HorizontalDivider(thickness = 1.dp)
                                                        PlayerGestureSwitch(
                                                            label = "双击两侧调节进度",
                                                            checked = sideDoubleTapSeekEnabled,
                                                            enabled = isOpen,
                                                            onCheckedChange = onSideDoubleTapSeekEnabledChange,
                                                        )
                                                        if (sideDoubleTapSeekEnabled) {
                                                            HorizontalDivider(thickness = 1.dp)
                                                            PlayerSeekDurationSlider(
                                                                seconds = doubleTapSeekSeconds,
                                                                enabled = isOpen,
                                                                onValueChange = onDoubleTapSeekSecondsChange,
                                                            )
                                                        }
                                                        HorizontalDivider(thickness = 1.dp)
                                                        PlayerGestureSwitch(
                                                            label = "启用长按倍速手势",
                                                            checked = longPressSpeedGestureEnabled,
                                                            enabled = isOpen,
                                                            onCheckedChange = onLongPressSpeedGestureEnabledChange,
                                                        )
                                                        if (longPressSpeedGestureEnabled) {
                                                            HorizontalDivider(thickness = 1.dp)
                                                            PlayerLongPressSpeedSlider(
                                                                speed = longPressSpeed,
                                                                enabled = isOpen,
                                                                onValueChange = onLongPressSpeedChange,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            BoloPlayerSettingsTab.Danmaku -> {
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp)) {
                                                        val levelText = if (danmakuFilterLevel == 0) "关闭" else danmakuFilterLevel.toString()
                                                        val interactionSource = remember { MutableInteractionSource() }
                                                        val thumbInteractionSource = remember(interactionSource) {
                                                            object : MutableInteractionSource by interactionSource {
                                                                override val interactions = interactionSource.interactions.filterNot { interaction ->
                                                                    interaction is FocusInteraction.Focus || interaction is FocusInteraction.Unfocus
                                                                }
                                                            }
                                                        }
                                                        Row(
                                                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text("弹幕过滤", style = MaterialTheme.typography.bodyMedium)
                                                                IconButton(
                                                                    onClick = { showFilterInfo = true },
                                                                    enabled = isOpen,
                                                                    modifier = Modifier.size(16.dp),
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Info,
                                                                        contentDescription = "弹幕过滤说明",
                                                                        modifier = Modifier.size(16.dp),
                                                                    )
                                                                }
                                                            }
                                                            Text(levelText, style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                                                            Slider(
                                                                value = danmakuFilterLevel.toFloat(),
                                                                onValueChange = { onDanmakuFilterLevelChange(it.roundToInt().coerceIn(0, 10)) },
                                                                valueRange = 0f..10f,
                                                                steps = 9,
                                                                enabled = isOpen,
                                                                interactionSource = interactionSource,
                                                                thumb = {
                                                                    SliderDefaults.Thumb(
                                                                        interactionSource = thumbInteractionSource,
                                                                        enabled = isOpen,
                                                                        thumbSize = DpSize(4.dp, 24.dp),
                                                                    )
                                                                },
                                                                track = { sliderState ->
                                                                    SliderDefaults.Track(
                                                                        sliderState = sliderState,
                                                                        enabled = isOpen,
                                                                        modifier = Modifier.height(12.dp),
                                                                    )
                                                                },
                                                                modifier = Modifier.fillMaxWidth().height(32.dp).semantics {
                                                                    contentDescription = "弹幕过滤"
                                                                    stateDescription = levelText
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                                Card(Modifier.fillMaxWidth()) {
                                                    PlayerPercentageSlider(
                                                        label = "弹幕缩放",
                                                        value = danmakuScale,
                                                        isOpen = isOpen,
                                                        onValueChange = onDanmakuScaleChange,
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerPercentageSlider(
                                                        label = "弹幕速度",
                                                        value = danmakuSpeed,
                                                        isOpen = isOpen,
                                                        onValueChange = onDanmakuSpeedChange,
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerPercentageSlider(
                                                        label = "弹幕显示区域",
                                                        value = danmakuDisplayAreaRatio,
                                                        isOpen = isOpen,
                                                        onValueChange = onDanmakuDisplayAreaRatioChange,
                                                        percentRange = 20..100,
                                                        percentStep = 5,
                                                        centeredAt100 = false,
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().toggleable(
                                                            value = danmakuTopBottomScrollEnabled,
                                                            enabled = isOpen,
                                                            role = Role.Switch,
                                                            onValueChange = onDanmakuTopBottomScrollEnabledChange,
                                                        ).padding(4.dp).padding(horizontal = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("上下显示滚动弹幕", style = MaterialTheme.typography.bodyMedium)
                                                            IconButton(
                                                                onClick = { showTopBottomScrollInfo = true },
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(16.dp),
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Info,
                                                                    contentDescription = "上下显示滚动弹幕说明",
                                                                    modifier = Modifier.size(16.dp),
                                                                )
                                                            }
                                                        }
                                                        Switch(
                                                            checked = danmakuTopBottomScrollEnabled,
                                                            onCheckedChange = null,
                                                            enabled = isOpen,
                                                            modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                        )
                                                    }
                                                    HorizontalDivider(thickness = 1.dp)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().toggleable(
                                                            value = danmakuExtraLineSpacingEnabled,
                                                            enabled = isOpen,
                                                            role = Role.Switch,
                                                            onValueChange = onDanmakuExtraLineSpacingEnabledChange,
                                                        ).padding(4.dp).padding(horizontal = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text("增大弹幕行距", style = MaterialTheme.typography.bodyMedium)
                                                        Switch(
                                                            checked = danmakuExtraLineSpacingEnabled,
                                                            onCheckedChange = null,
                                                            enabled = isOpen,
                                                            modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                        )
                                                    }
                                                }
                                            }
                                            BoloPlayerSettingsTab.Subtitle -> {
                                                val subtitlePageActive = isOpen && pagerState.currentPage == page &&
                                                    !pagerState.isScrollInProgress
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.fillMaxWidth().animateContentSize()) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().toggleable(
                                                                value = subtitleAlwaysOn,
                                                                enabled = subtitlePageActive,
                                                                role = Role.Switch,
                                                                onValueChange = onSubtitleAlwaysOnChange,
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("总是显示字幕", style = MaterialTheme.typography.bodyMedium)
                                                            Switch(
                                                                checked = subtitleAlwaysOn,
                                                                onCheckedChange = null,
                                                                enabled = subtitlePageActive,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                        if (subtitleAlwaysOn) {
                                                            HorizontalDivider(thickness = 1.dp)
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().toggleable(
                                                                    value = subtitleAutoChineseOnly,
                                                                    enabled = subtitlePageActive,
                                                                    role = Role.Switch,
                                                                    onValueChange = onSubtitleAutoChineseOnlyChange,
                                                                ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text(
                                                                    text = "仅自动开启中文字幕",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    modifier = Modifier.weight(1f),
                                                                )
                                                                Switch(
                                                                    checked = subtitleAutoChineseOnly,
                                                                    onCheckedChange = null,
                                                                    enabled = subtitlePageActive,
                                                                    modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                                )
                                                            }
                                                            HorizontalDivider(thickness = 1.dp)
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().toggleable(
                                                                    value = subtitleAutoExcludeAi,
                                                                    enabled = subtitlePageActive,
                                                                    role = Role.Switch,
                                                                    onValueChange = onSubtitleAutoExcludeAiChange,
                                                                ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text(
                                                                    text = "不自动开启AI字幕",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    modifier = Modifier.weight(1f),
                                                                )
                                                                Switch(
                                                                    checked = subtitleAutoExcludeAi,
                                                                    onCheckedChange = null,
                                                                    enabled = subtitlePageActive,
                                                                    modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Card(Modifier.fillMaxWidth()) {
                                                    PlayerPercentageSlider(
                                                        label = "字幕缩放",
                                                        value = subtitleScale,
                                                        isOpen = subtitlePageActive,
                                                        onValueChange = onSubtitleScaleChange,
                                                        onValuePreview = onSubtitleScalePreview,
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerPercentageSlider(
                                                        label = "字幕位置",
                                                        onInfoClick = { showSubtitlePositionInfo = true },
                                                        value = subtitleHeightRatio,
                                                        isOpen = subtitlePageActive,
                                                        onValueChange = onSubtitleHeightRatioChange,
                                                        onValuePreview = onSubtitleHeightRatioPreview,
                                                        percentRange = 0..100,
                                                        centeredAt100 = false,
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerPercentageSlider(
                                                        label = "背景不透明度",
                                                        value = subtitleBackgroundAlpha,
                                                        isOpen = subtitlePageActive,
                                                        onValueChange = onSubtitleBackgroundAlphaChange,
                                                        onValuePreview = onSubtitleBackgroundAlphaPreview,
                                                        percentRange = 0..100,
                                                        centeredAt100 = false,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Scrim(
                        contentDescription = "关闭播放器设置",
                        onClick = onDismissRequest,
                        alpha = { scrimAlpha },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerGestureSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekDurationSlider(
    seconds: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    var previewSeconds by remember { mutableStateOf<Int?>(null) }
    val active by rememberUpdatedState(enabled)
    val onChange by rememberUpdatedState(onValueChange)
    val interactionSource = remember { MutableInteractionSource() }
    val thumbInteractionSource = remember(interactionSource) {
        object : MutableInteractionSource by interactionSource {
            override val interactions = interactionSource.interactions.filterNot { interaction ->
                interaction is FocusInteraction.Focus || interaction is FocusInteraction.Unfocus
            }
        }
    }
    DisposableEffect(enabled) {
        previewSeconds = null
        onDispose { previewSeconds = null }
    }
    val displayedSeconds = previewSeconds ?: seconds
    Column(Modifier.padding(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("快进快退时长", style = MaterialTheme.typography.bodyMedium)
            Text("$displayedSeconds 秒", style = MaterialTheme.typography.bodySmall)
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Slider(
                value = displayedSeconds.toFloat(),
                onValueChange = { previewSeconds = it.roundToInt().coerceIn(5, 30) },
                onValueChangeFinished = {
                    if (active) previewSeconds?.let(onChange)
                    previewSeconds = null
                },
                valueRange = 5f..30f,
                steps = 24,
                enabled = enabled,
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = thumbInteractionSource,
                        enabled = enabled,
                        thumbSize = DpSize(4.dp, 24.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        modifier = Modifier.height(12.dp),
                        drawTick = { _, _ -> },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(32.dp).semantics {
                    contentDescription = "快进快退时长"
                    stateDescription = "$displayedSeconds 秒"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerLongPressSpeedSlider(
    speed: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    var previewSpeed by remember { mutableStateOf<Float?>(null) }
    val active by rememberUpdatedState(enabled)
    val onChange by rememberUpdatedState(onValueChange)
    val interactionSource = remember { MutableInteractionSource() }
    val thumbInteractionSource = remember(interactionSource) {
        object : MutableInteractionSource by interactionSource {
            override val interactions = interactionSource.interactions.filterNot { interaction ->
                interaction is FocusInteraction.Focus || interaction is FocusInteraction.Unfocus
            }
        }
    }
    // 使用 Material 3 默认配色，档位仍由离散值和档位点明确表达。
    val sliderColors = SliderDefaults.colors()
    val stopIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant
    DisposableEffect(enabled) {
        previewSpeed = null
        onDispose { previewSpeed = null }
    }
    val displayedSpeed = previewSpeed ?: speed
    val displayedText = speedMultiplierText((displayedSpeed * 100f).roundToInt())
    Column(Modifier.padding(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("长按快进速度", style = MaterialTheme.typography.bodyMedium)
            Text(displayedText, style = MaterialTheme.typography.bodySmall)
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Slider(
                value = longPressSpeedSliderPosition(displayedSpeed),
                onValueChange = { position -> previewSpeed = longPressSpeedFromSliderPosition(position) },
                onValueChangeFinished = {
                    if (active) previewSpeed?.let(onChange)
                    previewSpeed = null
                },
                valueRange = 0f..(LongPressSpeedStepCount - 1).toFloat(),
                steps = LongPressSpeedStepCount - 2,
                enabled = enabled,
                colors = sliderColors,
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = thumbInteractionSource,
                        colors = sliderColors,
                        enabled = enabled,
                        thumbSize = DpSize(4.dp, 24.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        colors = sliderColors,
                        modifier = Modifier.height(12.dp),
                        drawTick = { offset, color ->
                            drawCircle(
                                color = color,
                                radius = LongPressSpeedStopIndicatorRadius.toPx(),
                                center = offset,
                            )
                        },
                        drawStopIndicator = { offset ->
                            drawCircle(
                                color = stopIndicatorColor,
                                radius = LongPressSpeedStopIndicatorRadius.toPx(),
                                center = offset,
                            )
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(32.dp).semantics {
                    contentDescription = "长按快进速度"
                    stateDescription = displayedText
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        longPressSpeedSliderPosition(displayedSpeed),
                        longPressSpeedSliderPosition(LongPressSpeedPercentMinimum.toFloat())..
                            longPressSpeedSliderPosition(LongPressSpeedPercentMaximum.toFloat()),
                    )
                },
            )
        }
    }
}

private const val LongPressSpeedPercentMinimum = 125
private const val LongPressSpeedPercentMaximum = 300
private const val LongPressSpeedPercentStep = 25
private const val LongPressSpeedPercentSpan = LongPressSpeedPercentMaximum - LongPressSpeedPercentMinimum
private const val LongPressSpeedStepCount = LongPressSpeedPercentSpan / LongPressSpeedPercentStep + 1

// M3 滑块档位点为直径 4dp 的圆点，这里按半径 2dp 绘制。
private val LongPressSpeedStopIndicatorRadius = 2.dp

// 长按倍速滑块按 0.25x 一档映射到整数档位，避免浮点误差影响档位对齐。
private fun longPressSpeedSliderPosition(speed: Float): Float =
    ((speed * 100f).roundToInt() - LongPressSpeedPercentMinimum) / LongPressSpeedPercentStep.toFloat()

private fun longPressSpeedFromSliderPosition(position: Float): Float =
    (LongPressSpeedPercentMinimum +
        (position.roundToInt() * LongPressSpeedPercentStep).coerceIn(0, LongPressSpeedPercentSpan)) / 100f

private fun longPressSpeedSliderPosition(percent: Int): Float =
    (percent - LongPressSpeedPercentMinimum) / LongPressSpeedPercentStep.toFloat()

// 支持 0.25x 精度，与滑块档位和控制器实际速率保持一致。
internal fun speedMultiplierText(percent: Int): String {
    val whole = percent / 100
    val fraction = percent % 100
    return if (fraction == 0) "${whole}x" else "$whole.${fraction.toString().padStart(2, '0')}x"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerPercentageSlider(
    label: String,
    value: Float,
    isOpen: Boolean,
    onValueChange: (Float) -> Unit,
    percentRange: IntRange = 50..200,
    percentStep: Int = 1,
    centeredAt100: Boolean = true,
    onValuePreview: ((Float?) -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    var previewPercent by remember { mutableStateOf<Int?>(null) }
    val onPreview by rememberUpdatedState(onValuePreview)
    val active by rememberUpdatedState(isOpen)
    fun discardPreview() {
        previewPercent = null
        onPreview?.invoke(null)
    }
    val interactionSource = remember {
        val source = MutableInteractionSource()
        object : MutableInteractionSource by source {
            // Slider 在拖动取消后也可能调用结束回调，先同步丢弃预览以免误保存。
            override suspend fun emit(interaction: Interaction) {
                if (interaction is DragInteraction.Cancel) discardPreview()
                source.emit(interaction)
            }

            override fun tryEmit(interaction: Interaction): Boolean {
                if (interaction is DragInteraction.Cancel) discardPreview()
                return source.tryEmit(interaction)
            }
        }
    }
    val thumbInteractionSource = remember(interactionSource) {
        object : MutableInteractionSource by interactionSource {
            override val interactions = interactionSource.interactions.filterNot { interaction ->
                interaction is FocusInteraction.Focus || interaction is FocusInteraction.Unfocus
            }
        }
    }
    DisposableEffect(isOpen) {
        discardPreview()
        onDispose { discardPreview() }
    }
    val percent = previewPercent ?: (value * 100f).roundToInt()
    fun snapPercent(value: Float): Int =
        (percentRange.first + ((value - percentRange.first) / percentStep).roundToInt() * percentStep)
            .coerceIn(percentRange.first, percentRange.last)
    // 缩放和速度分段映射，使 100% 对应轨道正中；显示区域使用线性映射。
    val sliderPosition = if (centeredAt100) {
        if (percent <= 100) (percent - 100) / 50f else (percent - 100) / 100f
    } else percent.toFloat()
    fun commitPreview() {
        if (active) {
            previewPercent?.let { onValueChange(it / 100f) }
        }
        discardPreview()
    }
    Column(Modifier.padding(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (onInfoClick != null) {
                    IconButton(
                        onClick = onInfoClick,
                        enabled = isOpen,
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "${label}说明",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text("$percent%", style = MaterialTheme.typography.bodySmall)
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Slider(
                value = sliderPosition,
                onValueChange = { position ->
                    previewPercent = snapPercent(
                        if (centeredAt100) 100f + position * if (position <= 0f) 50f else 100f
                        else position,
                    )
                    onPreview?.invoke(previewPercent?.div(100f))
                },
                onValueChangeFinished = ::commitPreview,
                valueRange = if (centeredAt100) -1f..1f
                else percentRange.first.toFloat()..percentRange.last.toFloat(),
                steps = if (centeredAt100) 0 else (percentRange.last - percentRange.first) / percentStep - 1,
                enabled = isOpen,
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = thumbInteractionSource,
                        enabled = isOpen,
                        thumbSize = DpSize(4.dp, 24.dp),
                    )
                },
                track = { sliderState ->
                    if (centeredAt100) {
                        SliderDefaults.CenteredTrack(
                            sliderState = sliderState,
                            enabled = isOpen,
                            modifier = Modifier.height(12.dp),
                        )
                    } else {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            enabled = isOpen,
                            modifier = Modifier.height(12.dp),
                            drawTick = { _, _ -> },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .height(32.dp)
                    .onPreviewKeyEvent { event ->
                        if (!isOpen) return@onPreviewKeyEvent false
                        val forward = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
                        val target = when (event.key) {
                            Key.DirectionRight -> percent + forward * percentStep
                            Key.DirectionLeft -> percent - forward * percentStep
                            Key.MoveHome -> percentRange.first
                            Key.MoveEnd -> percentRange.last
                            Key.PageUp -> percent + 10
                            Key.PageDown -> percent - 10
                            else -> return@onPreviewKeyEvent false
                        }
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                previewPercent = snapPercent(target.toFloat())
                                onPreview?.invoke(previewPercent?.div(100f))
                            }
                            KeyEventType.KeyUp -> commitPreview()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    .semantics {
                        contentDescription = label
                        stateDescription = "$percent%"
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            percent.toFloat(),
                            percentRange.first.toFloat()..percentRange.last.toFloat(),
                            (percentRange.last - percentRange.first) / percentStep - 1,
                        )
                        setProgress { target ->
                            if (!isOpen) return@setProgress false
                            val next = snapPercent(target)
                            if (next == percent) return@setProgress false
                            previewPercent = next
                            commitPreview()
                            true
                        }
                    },
            )
        }
    }
}
