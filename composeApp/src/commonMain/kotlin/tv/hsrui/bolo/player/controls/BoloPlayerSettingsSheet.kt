package tv.hsrui.bolo.player.controls

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scrim
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.ui.components.slider.ShowSlider
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
    mergeAudioChannelsEnabled: Boolean,
    onMergeAudioChannelsEnabledChange: (Boolean) -> Unit,
    rebuildEnabled: Boolean,
    onRebuild: () -> Unit,
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
    danmakuScrollEnabled: Boolean,
    onDanmakuScrollEnabledChange: (Boolean) -> Unit,
    danmakuTopEnabled: Boolean,
    onDanmakuTopEnabledChange: (Boolean) -> Unit,
    danmakuBottomEnabled: Boolean,
    onDanmakuBottomEnabledChange: (Boolean) -> Unit,
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

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
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
                                                    Column(Modifier.padding(4.dp)) {
                                                        Row(
                                                            Modifier.fillMaxWidth()
                                                                .toggleable(
                                                                    value = autoReplayEnabled,
                                                                    enabled = isOpen,
                                                                    role = Role.Switch,
                                                                    onValueChange = onAutoReplayEnabledChange,
                                                                )
                                                                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                                    modifier = Modifier.size(16.dp),
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
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                                        modifier = Modifier.size(16.dp),
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
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp)) {
                                                        PlayerGestureSwitch(
                                                            label = "合并多声道",
                                                            checked = mergeAudioChannelsEnabled,
                                                            enabled = isOpen,
                                                            onCheckedChange = onMergeAudioChannelsEnabledChange,
                                                        )
                                                    }
                                                }
                                                PlayerRebuildCard(
                                                    enabled = isOpen && rebuildEnabled &&
                                                        pagerState.currentPage == page && !pagerState.isScrollInProgress,
                                                    onRebuild = onRebuild,
                                                )
                                            }
                                            BoloPlayerSettingsTab.Danmaku -> {
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp)) {
                                                        val levelText = if (danmakuFilterLevel == 0) "关闭" else danmakuFilterLevel.toString()
                                                        val interactionSource = remember { MutableInteractionSource() }
                                                        Row(
                                                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                                                        ShowSlider(
                                                            value = danmakuFilterLevel.toFloat(),
                                                            onValueChange = { onDanmakuFilterLevelChange(it.roundToInt().coerceIn(0, 10)) },
                                                            valueRange = 0f..10f,
                                                            steps = 9,
                                                            enabled = isOpen,
                                                            interactionSource = interactionSource,
                                                            modifier = Modifier.fillMaxWidth().semantics {
                                                                contentDescription = "弹幕过滤"
                                                                stateDescription = levelText
                                                            },
                                                        )
                                                    }
                                                }
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp)) {
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
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.padding(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().toggleable(
                                                                value = danmakuScrollEnabled,
                                                                enabled = isOpen,
                                                                role = Role.Switch,
                                                                onValueChange = onDanmakuScrollEnabledChange,
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("滚动弹幕", style = MaterialTheme.typography.bodyMedium)
                                                            Switch(
                                                                checked = danmakuScrollEnabled,
                                                                onCheckedChange = null,
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                        HorizontalDivider(thickness = 1.dp)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().toggleable(
                                                                value = danmakuTopEnabled,
                                                                enabled = isOpen,
                                                                role = Role.Switch,
                                                                onValueChange = onDanmakuTopEnabledChange,
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("顶部弹幕", style = MaterialTheme.typography.bodyMedium)
                                                            Switch(
                                                                checked = danmakuTopEnabled,
                                                                onCheckedChange = null,
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                        HorizontalDivider(thickness = 1.dp)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().toggleable(
                                                                value = danmakuBottomEnabled,
                                                                enabled = isOpen,
                                                                role = Role.Switch,
                                                                onValueChange = onDanmakuBottomEnabledChange,
                                                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("底部弹幕", style = MaterialTheme.typography.bodyMedium)
                                                            Switch(
                                                                checked = danmakuBottomEnabled,
                                                                onCheckedChange = null,
                                                                enabled = isOpen,
                                                                modifier = Modifier.size(39.dp, 24.dp).scale(0.75f),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            BoloPlayerSettingsTab.Subtitle -> {
                                                val subtitlePageActive = isOpen && pagerState.currentPage == page &&
                                                    !pagerState.isScrollInProgress
                                                Card(Modifier.fillMaxWidth()) {
                                                    Column(Modifier.fillMaxWidth().padding(4.dp).animateContentSize()) {
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
                                                    Column(Modifier.padding(4.dp)) {
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
private fun PlayerRebuildCard(
    enabled: Boolean,
    onRebuild: () -> Unit,
) {
    var startedAt by remember(enabled) { mutableStateOf<TimeMark?>(null) }
    var progress by remember(enabled) { mutableFloatStateOf(0f) }
    val active by rememberUpdatedState(enabled)
    val rebuild by rememberUpdatedState(onRebuild)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    LaunchedEffect(startedAt, enabled) {
        progress = 0f
        val start = startedAt ?: return@LaunchedEffect
        if (!enabled) return@LaunchedEffect
        try {
            while (isActive && active && startedAt === start) {
                withFrameNanos { }
                progress = (start.elapsedNow().inWholeMilliseconds / 3_000f).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    // 先绘制满格，再触发一次；松开或关闭仍可取消本次按压。
                    withFrameNanos { }
                    if (active && startedAt === start) rebuild()
                    break
                }
            }
        } finally {
            progress = 0f
        }
    }
    Card(
        onClick = {},
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) startedAt = null }
            .onPreviewKeyEvent { event ->
                val activationKey = event.key == Key.Enter || event.key == Key.NumPadEnter ||
                    event.key == Key.Spacebar || event.key == Key.DirectionCenter
                if (!enabled || !activationKey) false else {
                    if (event.type == KeyEventType.KeyDown && startedAt == null)
                        startedAt = TimeSource.Monotonic.markNow()
                    if (event.type == KeyEventType.KeyUp) startedAt = null
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        startedAt = TimeSource.Monotonic.markNow()
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                val outside = change.position.x < 0 || change.position.x >= size.width ||
                                    change.position.y < 0 || change.position.y >= size.height
                                if (!change.pressed || outside || moved || event.changes.count { it.pressed } > 1) break
                            }
                        } finally {
                            startedAt = null
                        }
                    }
                } finally {
                    startedAt = null
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                stateDescription = if (progress > 0f) "重建进度 ${(progress * 100).roundToInt()}%" else "长按 3 秒重建"
            },
    ) {
        Box(Modifier.fillMaxWidth().drawBehind {
            drawRect(fillColor, size = Size(size.width * progress, size.height))
        }) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp).heightIn(min = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("重建播放器", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("长按", style = MaterialTheme.typography.bodySmall)
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
        ShowSlider(
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
            showTicks = false,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "快进快退时长"
                stateDescription = "$displayedSeconds 秒"
            },
        )
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
        ShowSlider(
            value = longPressSpeedSliderPosition(displayedSpeed),
            onValueChange = { position -> previewSpeed = longPressSpeedFromSliderPosition(position) },
            onValueChangeFinished = {
                if (active) previewSpeed?.let(onChange)
                previewSpeed = null
            },
            valueRange = 0f..(LongPressSpeedStepCount - 1).toFloat(),
            steps = LongPressSpeedStepCount - 2,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().semantics {
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

private const val LongPressSpeedPercentMinimum = 125
private const val LongPressSpeedPercentMaximum = 300
private const val LongPressSpeedPercentStep = 25
private const val LongPressSpeedPercentSpan = LongPressSpeedPercentMaximum - LongPressSpeedPercentMinimum
private const val LongPressSpeedStepCount = LongPressSpeedPercentSpan / LongPressSpeedPercentStep + 1

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
    return if (fraction == 0) "${whole}x" else "$whole.${fraction.toString().padStart(2, '0').trimEnd('0')}x"
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
        ShowSlider(
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
            centered = centeredAt100,
            showTicks = false,
            modifier = Modifier.fillMaxWidth()
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
