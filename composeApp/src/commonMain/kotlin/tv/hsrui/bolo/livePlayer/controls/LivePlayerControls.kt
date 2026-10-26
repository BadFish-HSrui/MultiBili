package tv.hsrui.bolo.livePlayer.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.livePlayer.LivePlayerViewModel
import tv.hsrui.bolo.livePlayer.base.LivePlayerInfo
import tv.hsrui.bolo.livePlayer.base.LivePlayerState
import tv.hsrui.bolo.livePlayer.session.LivePlaybackTimelineState
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.player.PlayerKeyboardEffect
import tv.hsrui.bolo.player.settings.PlayerShortcutAction
import tv.hsrui.bolo.player.settings.shortcutAction
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import tv.hsrui.network.utils.formatToDuration

@Composable
fun LivePlayerControls(
    viewModel: LivePlayerViewModel,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    navigationOnly: Boolean = false,
) {
    val navigator: Navigator = koinInject()
    val platform = getPlatform()
    val isDesktop = fullscreenState.isDesktop
    val isFullscreen = fullscreenState.isFullscreen
    val isIpad = platform.type == PlatformType.Ios && !platform.isPhone
    val windowInfo = LocalWindowInfo.current
    val windowSize = windowInfo.containerSize
    val windowFocused = windowInfo.isWindowFocused
    val insets = if (platform.type == PlatformType.Ios && platform.isPhone && isFullscreen && windowSize.width > windowSize.height) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    } else WindowInsets.safeDrawing
    val topInsets = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val bottomInsets = insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    val applyInsets = isFullscreen || isIpad
    val showTitle = isFullscreen || isExpanded()
    var settingsOpen by remember(viewModel, navigationOnly) { mutableStateOf(false) }
    val danmakuSettings = viewModel.danmakuController.settings
    val sender = viewModel.danmakuSender
    val inputOpen = sender.isOpen
    val pageActive = viewModel.danmakuController.isPageActive
    LaunchedEffect(pageActive) { if (!pageActive) settingsOpen = false }
    var infoOpen by remember(viewModel, isFullscreen, showTitle, navigationOnly) { mutableStateOf(false) }
    val controller = viewModel.playbackSession?.controller
    val emptyInfo = remember { MutableStateFlow(LivePlayerInfo()) }
    val playerInfo by (controller?.info ?: emptyInfo).collectAsState()
    val emptyTimeline = remember { MutableStateFlow(LivePlaybackTimelineState()) }
    val timeline by (viewModel.playbackSession?.controller?.timeline ?: emptyTimeline).collectAsState()
    val emptyPlayback = remember { MutableStateFlow(LivePlayerState()) }
    val playback by (viewModel.playbackSession?.controller?.state ?: emptyPlayback).collectAsState()
    var previewPosition by remember(viewModel) { mutableStateOf<Long?>(null) }
    val displayedOffset = timeline.latestPositionMs?.let { latest ->
        (previewPosition ?: timeline.positionMs)?.let { (latest - it).coerceAtLeast(0) }
    }
    var controlsVisible by remember(viewModel) { mutableStateOf(false) }
    val controlsTransition = updateTransition(controlsVisible, label = "livePlayerControls")
    val bottomControlsVisible = controlsTransition.currentState || controlsTransition.targetState
    val playbackControlsPinned = !playback.isEnded && !viewModel.playWhenReady
    val controlsReveal by controlsTransition.animateFloat(transitionSpec = { tween(200) }, label = "liveControlsPosition") {
        if (it) 1f else 0f
    }
    var bottomControlsHeight by remember { mutableIntStateOf(0) }
    val controlsSlideOffset = bottomControlsHeight * (1f - controlsReveal)
    var mouseInside by remember { mutableStateOf(false) }
    var mouseMovementRevision by remember { mutableIntStateOf(0) }
    var pointerPressed by remember(viewModel) { mutableStateOf(false) }
    var primaryPress by remember(viewModel) { mutableStateOf(false) }
    var qualityMenuOpen by remember(viewModel, isFullscreen) { mutableStateOf(false) }
    var volumeMenuOpen by remember(viewModel, isFullscreen) { mutableStateOf(false) }
    val controlsInteractionActive = pointerPressed || previewPosition != null || qualityMenuOpen || volumeMenuOpen || settingsOpen || inputOpen
    val latestControlsInteractionActive by rememberUpdatedState(controlsInteractionActive)
    val pressedKeys = remember(viewModel) { mutableSetOf<Key>() }
    val cancelledKeys = remember(viewModel) { mutableSetOf<Key>() }
    val shortcutBindings = remember {
        listOf(
            PlayerShortcutAction.TogglePlayback,
            PlayerShortcutAction.ToggleFullscreen,
            PlayerShortcutAction.VolumeUp,
            PlayerShortcutAction.VolumeDown,
            PlayerShortcutAction.ToggleMuted,
        ).associate { it.id to it.defaultBindings }
    }
    fun revealControls() {
        controlsVisible = true
    }
    fun toggleFullscreen() {
        if (isDesktop && fullscreenState.isManualSystemFullscreen) fullscreenState.toggleWindowFullscreen()
        else fullscreenState.toggleFullscreen()
    }
    fun cancelKeyboard() {
        cancelledKeys.addAll(pressedKeys)
        pressedKeys.clear()
    }
    fun handleKey(event: KeyEvent, volumeOnly: Boolean = false): Boolean {
        if (inputOpen) return false
        if (event.type == KeyEventType.KeyUp) {
            val pressed = pressedKeys.remove(event.key)
            return cancelledKeys.remove(event.key) || pressed
        }
        if (event.type != KeyEventType.KeyDown || navigationOnly || !isDesktop || !windowFocused) return false
        if (event.key in cancelledKeys) return true
        val action = shortcutBindings.shortcutAction(event) ?: return false
        if (volumeOnly) {
            if (!action.isVolume) return false
        } else if (settingsOpen || qualityMenuOpen || volumeMenuOpen || previewPosition != null || pointerPressed) return false
        val firstDown = pressedKeys.add(event.key)
        when (action) {
            PlayerShortcutAction.TogglePlayback -> if (firstDown) viewModel.togglePlayback()
            PlayerShortcutAction.ToggleFullscreen -> if (firstDown) toggleFullscreen()
            PlayerShortcutAction.ToggleMuted -> if (firstDown) viewModel.toggleMuted()
            PlayerShortcutAction.VolumeUp -> viewModel.setVolume((viewModel.volumePercent + 10).coerceAtMost(200))
            PlayerShortcutAction.VolumeDown -> viewModel.setVolume((viewModel.volumePercent - 10).coerceAtLeast(0))
            else -> return false
        }
        return true
    }
    val onKeyEvent by rememberUpdatedState<(KeyEvent) -> Boolean> { handleKey(it) }
    val onVolumeKeyEvent by rememberUpdatedState<(KeyEvent) -> Boolean> { handleKey(it, volumeOnly = true) }
    val onTap by rememberUpdatedState {
        if (isDesktop) {
            if (primaryPress) {
                viewModel.togglePlayback()
            }
        } else controlsVisible = !controlsVisible
    }
    val onDoubleTap by rememberUpdatedState {
        if (isDesktop) {
            if (primaryPress) toggleFullscreen()
        } else {
            viewModel.togglePlayback()
        }
    }
    LaunchedEffect(isDesktop, mouseInside, mouseMovementRevision, controlsInteractionActive) {
        if (!isDesktop) return@LaunchedEffect
        if (controlsInteractionActive) {
            controlsVisible = true
        } else if (!mouseInside) {
            controlsVisible = false
        } else {
            controlsVisible = true
            delay(2_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(inputOpen) { if (inputOpen) cancelKeyboard() }
    LaunchedEffect(windowFocused) {
        if (!windowFocused) {
            pressedKeys.clear()
            cancelledKeys.clear()
            mouseInside = false
            pointerPressed = false
            previewPosition = null
        }
    }
    SideEffect {
        fullscreenState.hasVisibleControls = navigationOnly || bottomControlsVisible || controlsInteractionActive || infoOpen
    }
    DisposableEffect(controller, infoOpen) {
        controller?.setInfoPanelVisible(infoOpen)
        onDispose { controller?.setInfoPanelVisible(false) }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = showTitle && infoOpen,
        onBackCompleted = { infoOpen = false },
    )
    DisposableEffect(viewModel, fullscreenState) {
        onDispose {
            cancelKeyboard()
            fullscreenState.hasVisibleControls = true
        }
    }
    if (isDesktop && !navigationOnly && !inputOpen) {
        PlayerKeyboardEffect(shortcutBindings, onKeyEvent, ::cancelKeyboard)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Box(
            modifier.fillMaxSize().clipToBounds()
                .then(if (isIpad) Modifier.recalculateWindowInsets() else Modifier)
                .onPreviewKeyEvent { event ->
                    val action = shortcutBindings.shortcutAction(event)
                    isDesktop && (action?.isVolume == true || action == PlayerShortcutAction.TogglePlayback) && onKeyEvent(event)
                }
                .pointerInput(viewModel, navigationOnly) {
                    if (navigationOnly) return@pointerInput
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                pointerPressed = event.changes.any { it.pressed }
                                if (event.type == PointerEventType.Press) {
                                    primaryPress = event.changes.any { it.type != PointerType.Mouse } || event.buttons.isPrimaryPressed
                                }
                                if (isDesktop) {
                                    val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                                    when (event.type) {
                                        PointerEventType.Move, PointerEventType.Enter -> {
                                            mouseInside = mouse.position.x >= 0f && mouse.position.x < size.width &&
                                                mouse.position.y >= 0f && mouse.position.y < size.height
                                            if (mouseInside) { revealControls(); mouseMovementRevision++ }
                                        }
                                        PointerEventType.Exit -> {
                                            mouseInside = false
                                            if (!latestControlsInteractionActive) controlsVisible = false
                                        }
                                        PointerEventType.Press -> cancelKeyboard()
                                    }
                                }
                            }
                        }
                    } finally { mouseInside = false; pointerPressed = false }
                },
        ) {
            if (!navigationOnly) {
                Box(Modifier.fillMaxSize().pointerInput(viewModel, isDesktop) {
                    detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() })
                })
            }
            if (showTitle && infoOpen) {
                BoxWithConstraints(
                    Modifier.fillMaxSize()
                        .then(if (applyInsets) Modifier.windowInsetsPadding(insets) else Modifier)
                        .padding(16.dp),
                ) {
                    LivePlayerInfoPanel(
                        info = playerInfo,
                        onClose = { infoOpen = false },
                        modifier = Modifier.align(Alignment.TopStart).heightIn(max = maxHeight),
                    )
                }
            }
            controlsTransition.AnimatedVisibility(
                visible = { navigationOnly || it },
                enter = slideInVertically(tween(200)) { -it },
                exit = slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        .then(if (applyInsets) Modifier.windowInsetsPadding(topInsets) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isDesktop || !isFullscreen) {
                        IconButton(
                            onClick = {
                                if (!isDesktop && fullscreenState.isFullscreen) fullscreenState.exitFullscreen()
                                else fullscreenState.goBack()
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBackIos,
                                when {
                                    !isDesktop && isFullscreen -> "退出全屏"
                                    infoOpen -> "关闭播放信息"
                                    fullscreenState.shouldHandleFullscreenBack -> "退出全屏"
                                    else -> "返回"
                                },
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (!isFullscreen && navigator.currentDepth > 1) {
                        IconButton(onClick = navigator::goHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Home, "回到主页", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (!navigationOnly && showTitle) {
                        Text(
                            viewModel.roomInfo?.title.orEmpty(),
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { infoOpen = !infoOpen }) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = if (infoOpen) "隐藏播放信息" else "显示播放信息",
                                tint = if (infoOpen) BiliColor.ThemeColor else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = { infoOpen = false; settingsOpen = true; revealControls() }) {
                            Icon(Icons.Rounded.Settings, "播放器设置", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (!navigationOnly && (bottomControlsVisible || playbackControlsPinned)) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .onSizeChanged { bottomControlsHeight = it.height }
                            .drawBehind {
                                translate(top = controlsSlideOffset) {
                                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                                }
                            }
                            .then(if (applyInsets) Modifier.windowInsetsPadding(bottomInsets) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (playback.localReplayEnabled && timeline.canSeek) {
                            LiveProgressBar(
                                timeline = timeline,
                                onSeek = viewModel::seekToMs,
                                onPreview = { previewPosition = it },
                                modifier = Modifier.graphicsLayer { translationY = controlsSlideOffset }
                                    .then(if (!controlsVisible) Modifier.clearAndSetSemantics {} else Modifier),
                                enabled = controlsVisible,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        LivePlayerBottomControlsRow(
                            playbackControlsPinned = playbackControlsPinned,
                            controlsSlideOffset = { controlsSlideOffset },
                        ) { hiddenControls ->
                            IconButton(
                                onClick = viewModel::togglePlayback,
                                enabled = !(viewModel.broadcastEnded && playback.isEnded),
                                modifier = Modifier.size(32.dp).layoutId("playback"),
                            ) {
                                Icon(
                                    if (viewModel.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (viewModel.playWhenReady) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            if (bottomControlsVisible) {
                                Text(
                                    text = (if (playback.localReplayEnabled)
                                        "${displayedOffset?.let { "-${(it / 1000).formatToDuration()}" } ?: "—"} / " else "") +
                                        (timeline.latestElapsedMs?.let { (it / 1000).formatToDuration() } ?: "—"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.layoutId("time"),
                                )
                                TextButton(
                                    onClick = { danmakuSettings.enabled = !danmakuSettings.enabled; revealControls() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(if (danmakuSettings.enabled) "弹幕 - 开" else "弹幕 - 关",
                                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                                TextButton(
                                    onClick = sender::open,
                                    enabled = sender.isLoggedIn,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.White,
                                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                                    ),
                                    modifier = Modifier.height(32.dp).layoutId("send"),
                                ) {
                                    Text(if (sender.isLoggedIn) "发送弹幕" else "登录后发送弹幕",
                                        style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.layoutId("spacer"))
                                if (isDesktop) {
                                    Box(Modifier.layoutId("volume")
                                        .focusProperties { canFocus = "volume" !in hiddenControls }
                                        .then(if ("volume" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier)) {
                                        LiveVolumePopup(
                                            volumePercent = viewModel.volumePercent,
                                            muted = viewModel.muted,
                                            isFullscreen = isFullscreen,
                                            onVolumeSelected = { viewModel.setVolume(it); revealControls() },
                                            onToggleMuted = { viewModel.toggleMuted(); revealControls() },
                                            onExpandedChange = { volumeMenuOpen = it },
                                            onKeyEvent = onVolumeKeyEvent,
                                            visible = "volume" !in hiddenControls,
                                        )
                                    }
                                }
                                if (viewModel.availableQns.isNotEmpty()) {
                                    Box(Modifier.layoutId("quality")
                                        .focusProperties { canFocus = "quality" !in hiddenControls }
                                        .then(if ("quality" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier)) {
                                        LiveQualityMenu(
                                            enabled = !viewModel.broadcastEnded,
                                            qns = viewModel.availableQns,
                                            currentQn = viewModel.selectedQn,
                                            isFullscreen = isFullscreen,
                                            onQualitySelected = { viewModel.selectQuality(it); revealControls() },
                                            onExpandedChange = { qualityMenuOpen = it },
                                            visible = "quality" !in hiddenControls,
                                        )
                                    }
                                }
                                if (isDesktop) {
                                    IconButton(
                                        onClick = { fullscreenState.toggleWindowFullscreen(); revealControls() },
                                        modifier = Modifier.size(32.dp).semantics {
                                            contentDescription = if (isFullscreen) "退出窗口全屏" else "窗口全屏"
                                        },
                                    ) {
                                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.CropSquare, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                            Icon(
                                                if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                if (!fullscreenState.isManualSystemFullscreen) {
                                    val fullscreen = if (isDesktop) fullscreenState.isSystemFullscreen else isFullscreen
                                    IconButton(
                                        onClick = { toggleFullscreen() },
                                        enabled = !fullscreenState.isChangingSystemFullscreen,
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                            if (fullscreen) "退出全屏" else "全屏",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
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
    LivePlayerSettingsSheet(settingsOpen, danmakuSettings, onDismissRequest = { settingsOpen = false })
}

@Composable
private fun LivePlayerBottomControlsRow(
    playbackControlsPinned: Boolean,
    controlsSlideOffset: () -> Float,
    content: @Composable (Set<String>) -> Unit,
) {
    var hiddenControls by remember { mutableStateOf(emptySet<String>()) }
    Layout(
        content = { content(hiddenControls) },
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
    ) { measurables, constraints ->
        val timeIndex = measurables.indexOfFirst { it.layoutId == "time" }
        val sendIndex = measurables.indexOfFirst { it.layoutId == "send" }
        val naturalTimeWidth = measurables.getOrNull(timeIndex)?.maxIntrinsicWidth(constraints.maxHeight) ?: 0
        val naturalSendWidth = measurables.getOrNull(sendIndex)?.maxIntrinsicWidth(constraints.maxHeight) ?: 0
        val fixed = measurables.mapIndexed { index, measurable ->
            if (index == timeIndex || index == sendIndex) null
            else measurable.measure(Constraints(maxHeight = constraints.maxHeight))
        }
        val hidden = mutableSetOf<String>()
        var requiredWidth = fixed.sumOf { it?.width ?: 0 } + naturalTimeWidth + naturalSendWidth
        for (id in listOf("volume", "quality")) {
            val index = measurables.indexOfFirst { it.layoutId == id }
            if (requiredWidth > constraints.maxWidth && index >= 0) {
                hidden.add(id)
                requiredWidth -= fixed[index]?.width ?: 0
            }
        }
        hiddenControls = hidden
        val fixedWidth = measurables.indices.sumOf { if (measurables[it].layoutId in hidden) 0 else fixed[it]?.width ?: 0 }
        val sendWidth = naturalSendWidth.coerceAtMost((constraints.maxWidth - fixedWidth).coerceAtLeast(0))
        val timeWidth = naturalTimeWidth.coerceAtMost((constraints.maxWidth - fixedWidth - sendWidth).coerceAtLeast(0))
        val placeables = measurables.mapIndexed { index, measurable ->
            fixed[index] ?: measurable.measure(Constraints(
                maxWidth = if (index == timeIndex) timeWidth else sendWidth, maxHeight = constraints.maxHeight,
            ))
        }
        val visible = measurables.indices.filter { measurables[it].layoutId !in hidden }
        val spacerWidth = (constraints.maxWidth - visible.sumOf { placeables[it].width }).coerceAtLeast(0)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        layout(constraints.maxWidth, height) {
            var x = 0
            for (index in visible) {
                val placeable = placeables[index]
                placeable.placeRelativeWithLayer(x, (height - placeable.height) / 2) {
                    translationY = if (playbackControlsPinned && measurables[index].layoutId == "playback") 0f else controlsSlideOffset()
                }
                x += placeable.width
                if (measurables[index].layoutId == "spacer") x += spacerWidth
            }
        }
    }
}

@Composable
private fun LiveQualityMenu(
    enabled: Boolean,
    qns: List<Int>,
    currentQn: Int?,
    isFullscreen: Boolean,
    onQualitySelected: (Int) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    visible: Boolean = true,
) {
    var expanded by remember(isFullscreen, enabled, visible) { mutableStateOf(false) }
    val onExpanded by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded) {
        onExpanded(expanded)
        onDispose { onExpanded(false) }
    }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            modifier = Modifier.height(32.dp),
        ) {
            Text(
                currentQn?.let { LiveVideoQuality.fromCode(it)?.title ?: it.toString() } ?: "画质",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            qns.distinct().sortedDescending().forEach { qn ->
                DropdownMenuItem(
                    text = { Text(LiveVideoQuality.fromCode(qn)?.title ?: qn.toString()) },
                    trailingIcon = {
                        if (qn == currentQn) Icon(Icons.Rounded.Check, "当前画质")
                    },
                    onClick = {
                        expanded = false
                        if (qn != currentQn) onQualitySelected(qn)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveVolumePopup(
    volumePercent: Int,
    muted: Boolean,
    isFullscreen: Boolean,
    onVolumeSelected: (Int) -> Unit,
    onToggleMuted: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    visible: Boolean = true,
) {
    var expanded by remember(isFullscreen, visible) { mutableStateOf(false) }
    val onExpanded by rememberUpdatedState(onExpandedChange)
    val latestOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(expanded) {
        onExpanded(expanded)
        onDispose { onExpanded(false) }
    }
    val volumeText = if (muted) "已静音" else "$volumePercent%"
    val volumeIcon = if (muted || volumePercent == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val margin = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
                val y = anchorBounds.top - popupContentSize.height - margin
                return IntOffset(
                    x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
                )
            }
        }
    }
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(32.dp).semantics { stateDescription = volumeText },
        ) {
            Icon(volumeIcon, "播放器音量", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Card(
                    shape = RoundedCornerShape(percent = 50),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.onPreviewKeyEvent { latestOnKeyEvent(it) }
                        .focusRequester(focusRequester).focusable()
                        .widthIn(max = (windowWidth - 16.dp).coerceAtLeast(1.dp)).width(320.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onToggleMuted, modifier = Modifier.size(32.dp)) {
                            Icon(volumeIcon, if (muted) "取消静音" else "静音", modifier = Modifier.size(24.dp))
                        }
                        Text(volumeText, modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center, maxLines = 1)
                        ShowSlider(
                            value = volumePercent.toFloat(),
                            onValueChange = { onVolumeSelected(it.roundToInt().coerceIn(0, 200)) },
                            valueRange = 0f..200f,
                            centered = true,
                            showStops = false,
                            showTicks = false,
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "播放器音量"
                                stateDescription = volumeText
                            },
                        )
                        IconButton(onClick = { onVolumeSelected(100) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.RestartAlt, "重置音量为 100%", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
