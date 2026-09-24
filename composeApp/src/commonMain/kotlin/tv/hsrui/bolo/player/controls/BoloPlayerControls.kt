package tv.hsrui.bolo.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.player.PlayerKeyboardEffect
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.components.reply.ShowReplyInput
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.PlayerChapterData
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.subtitle.SubtitleItem
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoloPlayerControls(
    title: String,
    viewModel: VideoPlayerViewModel,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    navigationOnly: Boolean = false,
    navigationContentColor: Color = Color.White,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    episodeNavigationEnabled: Boolean = true,
) {
    val isFullscreen = fullscreenState.isFullscreen
    val isDesktop = fullscreenState.isDesktop
    val windowSize = LocalWindowInfo.current.containerSize
    val controlsInsets = if (
        getPlatform().type == PlatformType.Ios && isFullscreen && windowSize.width > windowSize.height
    ) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }
    val showExtendedControls = isFullscreen || isExpanded()
    val navigator: Navigator = koinInject()
    var infoOpen by remember(viewModel, isFullscreen, showExtendedControls, navigationOnly) { mutableStateOf(false) }
    val navigationButtons: @Composable () -> Unit = {
        // 导航按钮
        if (!isDesktop || !isFullscreen) {
            IconButton(
                onClick = {
                    if (!isDesktop && fullscreenState.isFullscreen) {
                        fullscreenState.exitFullscreen()
                    } else {
                        fullscreenState.goBack()
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = when {
                        !isDesktop && isFullscreen -> "退出全屏"
                        infoOpen -> "关闭播放信息"
                        fullscreenState.shouldHandleFullscreenBack -> "退出全屏"
                        else -> "返回"
                    },
                    tint = navigationContentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (!isFullscreen && navigator.currentDepth > 1) {
            IconButton(
                onClick = { navigator.goHome() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "回到主页",
                    tint = navigationContentColor
                )
            }
        }
    }
    if (navigationOnly) {
        Box(modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart)
                    .then(if (isFullscreen) Modifier.windowInsetsPadding(controlsInsets) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationButtons()
            }
        }
        return
    }
    val deviceControls = rememberPlayerDeviceControls()
    val settings: BoloSettings = koinInject()
    val hapticFeedback = LocalHapticFeedback.current
    val seekGestureEnabled = settings.playerSeekGestureEnabled
    val brightnessGestureEnabled = settings.playerBrightnessGestureEnabled
    val volumeGestureEnabled = settings.playerVolumeGestureEnabled
    val longPressSpeedGestureEnabled = settings.playerLongPressSpeedGestureEnabled
    val longPressSpeed = settings.playerLongPressSpeed
    val desktopDoubleClickPauseEnabled = settings.playerDesktopDoubleClickPauseEnabled
    val desktopDefaultWindowFullscreenEnabled = settings.playerDesktopDefaultWindowFullscreenEnabled
    val desktopFastForwardHoldSpeedEnabled = settings.playerDesktopFastForwardHoldSpeedEnabled
    val playState by viewModel.controller.state.collectAsState()
    val playerInfo by viewModel.controller.info.collectAsState()
    val playerUiState by viewModel.uiState.collectAsState()
    val subtitleState by viewModel.subtitleController.state.collectAsState()
    val danmakuState by viewModel.danmakuController.state.collectAsState()
    val danmakuClosed by viewModel.danmakuClosed.collectAsState()
    val highEnergyProgress by viewModel.highEnergyProgress.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val currentVideoQuality by viewModel.currentVideoQuality.collectAsState()
    val currentAudioQuality by viewModel.currentAudioQuality.collectAsState()
    val danmakuInputOpen = viewModel.danmakuInputOpen
    val danmakuSendError = viewModel.danmakuSendError
    DisposableEffect(viewModel) {
        onDispose { viewModel.dismissDanmakuInput(resumePlayback = false) }
    }
    LaunchedEffect(danmakuSendError) {
        if (!viewModel.danmakuInputOpen) danmakuSendError?.let { showSnackbarMessage(it) }
    }
    var sliderPreviewFraction by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
        mutableStateOf<Float?>(null)
    }
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val cancelSliderPreview by rememberUpdatedState({ sliderPreviewFraction = null })
    LaunchedEffect(sliderInteractionSource, isDesktop) {
        if (!isDesktop) return@LaunchedEffect
        sliderInteractionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Cancel || interaction is PressInteraction.Cancel) {
                cancelSliderPreview()
            }
        }
    }
    var controlsVisible by remember { mutableStateOf(false) }
    val controlsTransition = updateTransition(controlsVisible, label = "playerControls")
    val controlsReveal by controlsTransition.animateFloat(
        transitionSpec = { tween(200) }, label = "highEnergyProgressPosition",
    ) { if (it) 1f else 0f }
    var controlsRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bottomControlsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var progressTrackCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var highEnergyTrackBounds by remember(isFullscreen, showExtendedControls) { mutableStateOf<Rect?>(null) }
    var highEnergyHiddenBounds by remember(isFullscreen, showExtendedControls) { mutableStateOf(Rect.Zero) }
    fun updateHighEnergyTrackBounds() {
        val root = controlsRootCoordinates?.takeIf { it.isAttached } ?: return
        val bottom = bottomControlsCoordinates?.takeIf { it.isAttached } ?: return
        val track = progressTrackCoordinates?.takeIf { it.isAttached } ?: return
        val bottomBounds = root.localBoundingBoxOf(bottom, clipBounds = false)
        val trackBounds = root.localBoundingBoxOf(track, clipBounds = false)
        // 去掉控制区显隐位移，得到曲线插值所需的稳定终点。
        highEnergyTrackBounds = trackBounds.translate(
            Offset(0f, root.size.height - bottom.size.height - bottomBounds.top),
        )
    }
    var settingsOpen by remember(isFullscreen, showExtendedControls) { mutableStateOf(false) }
    var mouseInside by remember { mutableStateOf(false) }
    var mousePressed by remember { mutableStateOf(false) }
    var mouseMovementRevision by remember { mutableIntStateOf(0) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var volumeMenuOpen by remember { mutableStateOf(false) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var audioQualityMenuOpen by remember { mutableStateOf(false) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var chapterMenuOpen by remember { mutableStateOf(false) }
    val controlsInteractionActive = mousePressed || sliderPreviewFraction != null ||
        settingsOpen || speedMenuOpen || volumeMenuOpen || qualityMenuOpen || audioQualityMenuOpen || subtitleMenuOpen || chapterMenuOpen || danmakuInputOpen
    val latestControlsInteractionActive by rememberUpdatedState(controlsInteractionActive)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
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
    LaunchedEffect(isDesktop, windowFocused) {
        if (isDesktop && !windowFocused) {
            mouseInside = false
            mousePressed = false
            sliderPreviewFraction = null
        }
    }
    DisposableEffect(viewModel, infoOpen) {
        viewModel.controller.setInfoPanelVisible(infoOpen)
        onDispose { viewModel.controller.setInfoPanelVisible(false) }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = showExtendedControls && infoOpen,
        onBackCompleted = { infoOpen = false },
    )
    val latestPlayState by rememberUpdatedState(playState)
    var actionFeedback by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
        mutableStateOf<Pair<ImageVector, String>?>(null)
    }
    var actionFeedbackRevision by remember { mutableIntStateOf(0) }
    fun showActionFeedback(icon: ImageVector, description: String) {
        actionFeedback = icon to description
        actionFeedbackRevision++
    }
    fun togglePlayback() {
        val playback = viewModel.controller.state.value
        if (playback.isPlaybackSuspended) return
        if (playback.isPlaying) {
            viewModel.pause()
        } else {
            viewModel.play()
        }
    }
    fun showSeekFeedback(direction: Int) {
        if (direction < 0) {
            showActionFeedback(Icons.Rounded.FastRewind, "快退")
        } else {
            showActionFeedback(Icons.Rounded.FastForward, "快进")
        }
    }
    LaunchedEffect(actionFeedbackRevision, viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
        // 淡入 100ms + 保持 200ms，随后由可见性动画淡出 200ms，总计 500ms。
        delay(300)
        actionFeedback = null
    }
    var gesturePreviewMs by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Long?>(null)
    }
    var brightnessPreview by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    var volumePreview by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    var desktopVolumeFeedbackVisible by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
        mutableStateOf(false)
    }
    var desktopVolumeFeedbackRevision by remember { mutableIntStateOf(0) }
    val desktopVolumeText = if (settings.playerDesktopMuted) "已静音" else "${settings.playerDesktopVolumePercent}%"
    val desktopVolumeIcon = if (settings.playerDesktopMuted || settings.playerDesktopVolumePercent == 0) {
        Icons.AutoMirrored.Rounded.VolumeOff
    } else Icons.AutoMirrored.Rounded.VolumeUp
    fun showDesktopVolumeFeedback() {
        desktopVolumeFeedbackVisible = true
        desktopVolumeFeedbackRevision++
    }
    fun adjustDesktopVolume(delta: Int) {
        viewModel.adjustDesktopVolume(delta)
        showDesktopVolumeFeedback()
    }
    LaunchedEffect(desktopVolumeFeedbackRevision, viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
        delay(500)
        desktopVolumeFeedbackVisible = false
    }
    // 长按倍速期间的状态：临时倍速用于预览，原倍速用于松手恢复。
    var gestureSpeedBoost by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    var gestureBaseSpeed by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    val restoreBaseSpeedOnDispose by rememberUpdatedState(gestureBaseSpeed)
    DisposableEffect(viewModel) {
        onDispose {
            // 全屏切换或离开播放器时手势循环会被取消，兜底恢复长按前的倍速。
            restoreBaseSpeedOnDispose?.let { viewModel.controller.setPlaybackSpeed(it) }
        }
    }
    val keyboardScope = rememberCoroutineScope()
    val keyboardPressedKeys = remember(viewModel) { mutableSetOf<Key>() }
    val keyboardCancelledKeys = remember(viewModel) { mutableSetOf<Key>() }
    val keyboardSeekTimes = remember(viewModel) { mutableMapOf<Int, TimeMark>() }
    var rightHoldJob by remember(viewModel) { mutableStateOf<Job?>(null) }
    var rightHoldPending by remember(viewModel) { mutableStateOf(false) }
    var keyboardBaseSpeed by remember(viewModel) { mutableStateOf<Float?>(null) }
    var keyboardSpeedBoost by remember(viewModel) { mutableStateOf<Float?>(null) }
    fun cancelKeyboardInteraction() {
        rightHoldJob?.cancel()
        rightHoldJob = null
        rightHoldPending = false
        keyboardCancelledKeys.addAll(keyboardPressedKeys)
        keyboardPressedKeys.clear()
        keyboardSeekTimes.clear()
        keyboardBaseSpeed?.let { viewModel.controller.setPlaybackSpeed(it) }
        keyboardBaseSpeed = null
        keyboardSpeedBoost = null
    }
    fun seekByKeyboard(direction: Int, isRepeat: Boolean = false) {
        if (isRepeat && keyboardSeekTimes[direction]?.elapsedNow()?.inWholeMilliseconds?.let { it < 500L } == true) return
        val playback = viewModel.controller.state.value
        if (!playback.isSeekable || playback.durationMs <= 0L || playback.isPlaybackSuspended) return
        keyboardSeekTimes[direction] = TimeSource.Monotonic.markNow()
        viewModel.seekToMs(
            (playback.displayPositionMs + direction * settings.playerDoubleTapSeekSeconds * 1_000L)
                .coerceIn(0L, playback.durationMs),
            autoPlayAfterSeek = settings.playerAutoPlayAfterSeekEnabled,
        )
        showSeekFeedback(direction)
    }
    fun toggleDesktopFullscreen() {
        if (fullscreenState.isManualSystemFullscreen || settings.playerDesktopDefaultWindowFullscreenEnabled) {
            fullscreenState.toggleWindowFullscreen()
        } else {
            fullscreenState.toggleFullscreen()
        }
    }
    val keyboardBlocked = settingsOpen || speedMenuOpen || volumeMenuOpen || qualityMenuOpen || audioQualityMenuOpen ||
        subtitleMenuOpen || chapterMenuOpen || danmakuInputOpen || mousePressed || sliderPreviewFraction != null
    fun onVolumeKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.DirectionUp && event.key != Key.DirectionDown && event.key != Key.M) return false
        if (event.type == KeyEventType.KeyUp) {
            val cancelled = keyboardCancelledKeys.remove(event.key)
            val handled = keyboardPressedKeys.remove(event.key)
            return cancelled || handled
        }
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        if (event.key in keyboardCancelledKeys) return true
        val firstDown = keyboardPressedKeys.add(event.key)
        when (event.key) {
            Key.DirectionUp -> adjustDesktopVolume(10)
            Key.DirectionDown -> adjustDesktopVolume(-10)
            Key.M -> if (firstDown) {
                viewModel.toggleDesktopMuted()
                showDesktopVolumeFeedback()
            }
        }
        return true
    }
    DisposableEffect(viewModel) {
        onDispose { cancelKeyboardInteraction() }
    }
    LaunchedEffect(
        viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen,
        desktopFastForwardHoldSpeedEnabled, longPressSpeed,
    ) {
        cancelKeyboardInteraction()
    }
    LaunchedEffect(
        playState.isPlaying, playState.isPlaybackSuspended, playState.isRebuilding,
        keyboardBlocked, infoOpen, windowFocused,
    ) {
        if (!playState.isPlaying || playState.isPlaybackSuspended || playState.isRebuilding ||
            keyboardBlocked || infoOpen || !windowFocused) cancelKeyboardInteraction()
    }
    val onPlayerKeyEvent: (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyUp) {
            val cancelled = keyboardCancelledKeys.remove(event.key)
            val handled = keyboardPressedKeys.remove(event.key)
            if (event.key == Key.DirectionRight && handled) {
                val shouldSeek = rightHoldPending && keyboardBaseSpeed == null
                rightHoldJob?.cancel()
                rightHoldJob = null
                rightHoldPending = false
                keyboardBaseSpeed?.let { viewModel.controller.setPlaybackSpeed(it) }
                keyboardBaseSpeed = null
                keyboardSpeedBoost = null
                if (shouldSeek) seekByKeyboard(1)
            }
            when (event.key) {
                Key.DirectionLeft -> keyboardSeekTimes.remove(-1)
                Key.DirectionRight -> keyboardSeekTimes.remove(1)
            }
            cancelled || handled
        } else if (
            event.type != KeyEventType.KeyDown || keyboardBlocked || !windowFocused ||
            (infoOpen && event.key != Key.Spacebar) ||
            event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        ) {
            false
        } else if (event.key in keyboardCancelledKeys) {
            true
        } else {
            when (event.key) {
                Key.Spacebar -> {
                    if (keyboardPressedKeys.add(Key.Spacebar)) {
                        togglePlayback()
                    }
                    true
                }
                Key.F -> {
                    if (keyboardPressedKeys.add(Key.F)) toggleDesktopFullscreen()
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.M -> onVolumeKeyEvent(event)
                Key.D -> {
                    if (keyboardPressedKeys.add(Key.D)) {
                        viewModel.setDanmakuVisible(!viewModel.danmakuController.state.value.isVisible)
                    }
                    true
                }
                Key.DirectionLeft -> {
                    val firstDown = keyboardPressedKeys.add(Key.DirectionLeft)
                    seekByKeyboard(-1, isRepeat = !firstDown)
                    true
                }
                Key.DirectionRight -> {
                    val firstDown = keyboardPressedKeys.add(Key.DirectionRight)
                    val playback = viewModel.controller.state.value
                    if (firstDown && desktopFastForwardHoldSpeedEnabled && playback.isPlaying &&
                        !playback.isPlaybackSuspended && !playback.isRebuilding) {
                        rightHoldPending = true
                        rightHoldJob = keyboardScope.launch {
                            delay(300)
                            val current = viewModel.controller.state.value
                            if (current.isPlaying && !current.isPlaybackSuspended && !current.isRebuilding) {
                                keyboardBaseSpeed = current.playbackSpeed
                                keyboardSpeedBoost = longPressSpeed
                                viewModel.controller.setPlaybackSpeed(longPressSpeed)
                            }
                        }
                    } else if (!rightHoldPending) {
                        seekByKeyboard(1, isRepeat = !firstDown)
                    }
                    true
                }
                else -> false
            }
        }
    }
    if (isDesktop) {
        PlayerKeyboardEffect(
            onCancel = {
                cancelKeyboardInteraction()
                keyboardCancelledKeys.clear()
            },
            onKeyEvent = onPlayerKeyEvent,
        )
    }
    val devicePreview = brightnessPreview ?: volumePreview
    val deviceFeedbackVisible = devicePreview != null || desktopVolumeFeedbackVisible
    val onDesktopVolumeScroll by rememberUpdatedState<(Float) -> Boolean> { delta ->
        if (isDesktop && windowFocused && !keyboardBlocked && !infoOpen && delta != 0f) {
            adjustDesktopVolume(if (delta < 0f) 2 else -2)
            true
        } else false
    }
    val durationMs = playState.durationMs
    val previewPositionMs = gesturePreviewMs ?: sliderPreviewFraction?.let { fraction ->
        (fraction.toDouble() * durationMs.toDouble())
            .roundToLong()
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
    val displayedPositionMs = previewPositionMs ?: playState.displayPositionMs
    val visibleChapters = remember(chapters, durationMs, showExtendedControls, playerUiState) {
        if (showExtendedControls && playerUiState is VideoPlayerUiState.Success) {
            chapters.mapNotNull { it.clippedTo(durationMs) }
        } else emptyList()
    }
    val currentChapter = visibleChapters.lastOrNull { it.contains(displayedPositionMs, durationMs) }
    val chapterBoundaries = remember(visibleChapters, durationMs) {
        val chapterEndMs = visibleChapters.maxOfOrNull { it.endMs }
        visibleChapters.flatMap { listOf(it.startMs, it.endMs) }
            .filter { it > 0L && it < durationMs && it != chapterEndMs }.distinct().sorted()
    }
    val sliderValue = if (durationMs > 0L) {
        (displayedPositionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val videoQualities = (playerUiState as? VideoPlayerUiState.Success)
        ?.videoSource
        ?.videoQualities
        .orEmpty()
    val audioQualities = (playerUiState as? VideoPlayerUiState.Success)
        ?.videoSource
        ?.audioQualities
        .orEmpty()

    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned {
            controlsRootCoordinates = it
            updateHighEnergyTrackBounds()
        }.onPreviewKeyEvent { event ->
            // 播放器内优先接管音量与开关按键，播放器外的输入框仍自行处理。
            isDesktop && (event.key == Key.Spacebar || event.key == Key.DirectionUp ||
                event.key == Key.DirectionDown || event.key == Key.M || event.key == Key.D) && onPlayerKeyEvent(event)
        }.pointerInput(isDesktop, viewModel) {
            if (!isDesktop) return@pointerInput
            try {
                awaitPointerEventScope {
                    while (true) {
                        // 父容器观察鼠标；仅符合条件的音量滚轮会消费事件。
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            if (onDesktopVolumeScroll(delta)) event.changes.forEach { it.consume() }
                        }
                        val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                        mousePressed = event.changes.any { it.type == PointerType.Mouse && it.pressed }
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                mouseInside = mouse.position.x >= 0f && mouse.position.x < size.width &&
                                    mouse.position.y >= 0f && mouse.position.y < size.height
                                if (mouseInside) {
                                    controlsVisible = true
                                    mouseMovementRevision++
                                }
                            }
                            PointerEventType.Exit -> {
                                mouseInside = false
                                if (!latestControlsInteractionActive) controlsVisible = false
                            }
                            PointerEventType.Press -> cancelKeyboardInteraction()
                        }
                    }
                }
            } finally {
                mouseInside = false
                mousePressed = false
            }
        },
    ) {
        // 背景独立命中：上层按钮、滑块与面板不会把事件传给手势层。
        Box(
            Modifier.fillMaxSize()
                .pointerInput(
                    viewModel,
                    title,
                    playerUiState,
                    currentVideoQuality,
                    currentAudioQuality,
                    settingsOpen,
                    isFullscreen,
                    longPressSpeedGestureEnabled,
                    longPressSpeed,
                    isDesktop,
                    desktopDoubleClickPauseEnabled,
                    desktopDefaultWindowFullscreenEnabled,
                    settings.playerSideDoubleTapSeekEnabled,
                    settings.playerDoubleTapSeekSeconds,
                ) {
                    if (settingsOpen) return@pointerInput
                    if (isDesktop) {
                        detectTapGestures(
                            onTap = {
                                if (!desktopDoubleClickPauseEnabled) {
                                    togglePlayback()
                                }
                            },
                            onDoubleTap = {
                                if (desktopDoubleClickPauseEnabled) {
                                    togglePlayback()
                                } else {
                                    toggleDesktopFullscreen()
                                }
                            },
                        )
                        return@pointerInput
                    }
                    // 说明面板或全屏切换会重启本手势循环，同一手势内的判定都在一个循环里完成。
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        // null 表示超时前已松手或手势被取消，继续按单双击语义判定。
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                awaitFirstDown(requireUnconsumed = true)
                            }
                            if (secondDown != null) {
                                val playback = viewModel.controller.state.value
                                if (
                                    settings.playerSideDoubleTapSeekEnabled &&
                                    playback.isSeekable &&
                                    playback.durationMs > 0L
                                ) {
                                    val direction = when {
                                        down.position.x < size.width / 3f -> -1
                                        down.position.x >= size.width * 2f / 3f -> 1
                                        else -> 0
                                    }
                                    if (direction != 0) {
                                        val offsetMs = direction * settings.playerDoubleTapSeekSeconds * 1_000L
                                        viewModel.seekToMs(
                                            (playback.displayPositionMs + offsetMs)
                                                .coerceIn(0L, playback.durationMs),
                                            autoPlayAfterSeek = settings.playerAutoPlayAfterSeekEnabled,
                                        )
                                        showSeekFeedback(direction)
                                    } else {
                                        togglePlayback()
                                    }
                                } else {
                                    togglePlayback()
                                }
                            } else {
                                controlsVisible = !controlsVisible
                            }
                            return@awaitEachGesture
                        }
                        val playback = viewModel.controller.state.value
                        if (
                            controlsVisible ||
                            !isFullscreen ||
                            !longPressSpeedGestureEnabled ||
                            !playback.isPlaying ||
                            playback.isPlaybackSuspended
                        ) {
                            return@awaitEachGesture
                        }
                        var speedBoostApplied = false
                        try {
                            longPress.consume()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            gestureBaseSpeed = playback.playbackSpeed
                            val boostSpeed = longPressSpeed
                            gestureSpeedBoost = boostSpeed
                            speedBoostApplied = true
                            viewModel.controller.setPlaybackSpeed(boostSpeed)
                            // 等待松手；拖动或指针取消都会在这里结束并恢复原倍速。
                            waitForUpOrCancellation()
                        } finally {
                            if (speedBoostApplied) {
                                gestureBaseSpeed?.let { viewModel.controller.setPlaybackSpeed(it) }
                            }
                            gestureSpeedBoost = null
                            gestureBaseSpeed = null
                        }
                    }
                }
                .pointerInput(
                    viewModel,
                    title,
                    playerUiState,
                    currentVideoQuality,
                    currentAudioQuality,
                    isFullscreen,
                    settingsOpen,
                    deviceControls,
                    seekGestureEnabled,
                    brightnessGestureEnabled,
                    volumeGestureEnabled,
                ) {
                    if (isDesktop || !isFullscreen || settingsOpen) return@pointerInput
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPositionMs = latestPlayState.displayPositionMs
                            val leftSide = down.position.x < size.width / 2f
                            val deviceGestureEnabled = deviceControls.supportsDeviceGestures &&
                                if (leftSide) brightnessGestureEnabled else volumeGestureEnabled
                            val startDeviceValue = if (deviceGestureEnabled) {
                                if (leftSide) deviceControls.readBrightness() else deviceControls.readVolume()
                            } else null
                            var movement = Offset.Zero
                            var horizontal: Boolean? = null
                            var completed = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (event.changes.any { it.id != down.id && it.pressed } || change.isConsumed) break
                                    if (change.changedToUpIgnoreConsumed()) {
                                        completed = true
                                        if (horizontal != null) change.consume()
                                        break
                                    }
                                    if (!change.pressed) break
                                    movement += change.positionChange()
                                    if (horizontal == null) {
                                        if (movement.getDistance() <= viewConfiguration.touchSlop) continue
                                        horizontal = abs(movement.x) >= abs(movement.y)
                                        if (horizontal == true) {
                                            if (
                                                !seekGestureEnabled ||
                                                !latestPlayState.isSeekable ||
                                                latestPlayState.durationMs <= 0L
                                            ) break
                                        } else {
                                            if (!deviceGestureEnabled || startDeviceValue == null) break
                                        }
                                    }
                                    change.consume()
                                    if (horizontal == true) {
                                        if (
                                            !seekGestureEnabled ||
                                            !latestPlayState.isSeekable ||
                                            latestPlayState.durationMs <= 0L
                                        ) break
                                        gesturePreviewMs = (startPositionMs +
                                            (movement.x.toDouble() / size.width.coerceAtLeast(1) * 120_000.0).roundToLong())
                                            .coerceIn(0L, latestPlayState.durationMs)
                                    } else if (startDeviceValue != null) {
                                        val value = (startDeviceValue - movement.y / size.height.coerceAtLeast(1))
                                            .coerceIn(0f, 1f)
                                        if (leftSide) {
                                            deviceControls.setBrightness(value)
                                            brightnessPreview = value
                                        } else {
                                            deviceControls.setVolume(value)
                                            volumePreview = value
                                        }
                                    }
                                }
                                if (completed && horizontal == true && latestPlayState.isSeekable && latestPlayState.durationMs > 0L) {
                                    gesturePreviewMs?.let {
                                        viewModel.seekToMs(
                                            it.coerceIn(0L, latestPlayState.durationMs),
                                            autoPlayAfterSeek = settings.playerAutoPlayAfterSeekEnabled,
                                        )
                                    }
                                }
                            } finally {
                                gesturePreviewMs = null
                                brightnessPreview = null
                                volumePreview = null
                            }
                        }
                    } finally {
                        gesturePreviewMs = null
                        brightnessPreview = null
                        volumePreview = null
                    }
                }
        )
        if (showExtendedControls && infoOpen) {
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .then(if (isFullscreen) Modifier.windowInsetsPadding(controlsInsets) else Modifier)
                    .padding(16.dp),
            ) {
                BoloPlayerInfoPanel(
                    info = playerInfo,
                    onClose = { infoOpen = false },
                    modifier = Modifier.align(Alignment.TopStart).heightIn(max = maxHeight),
                )
            }
        }
        controlsTransition.AnimatedVisibility(
            visible = { it },
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize().clipToBounds()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .animateEnterExit(
                            enter = slideInVertically(tween(200)) { -it },
                            exit = slideOutVertically(tween(200)) { -it },
                        )
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(tween(200)) { it },
                            exit = slideOutVertically(tween(200)) { it },
                        )
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )
            }
        }
        if (showExtendedControls && settings.playerHighEnergyProgressEnabled &&
            highEnergyProgress != null && durationMs > 0L &&
            (settings.playerHighEnergyProgressAlwaysVisible || controlsTransition.currentState || controlsTransition.targetState)
        ) {
            // 使用同一套 Insets 消费规则测量可用区域，不叠加控制区内边距。
            Box(
                Modifier.matchParentSize()
                    .then(if (isFullscreen) Modifier.windowInsetsPadding(controlsInsets) else Modifier),
            ) {
                Box(Modifier.matchParentSize().onGloballyPositioned { coordinates ->
                    val root = controlsRootCoordinates
                    if (root != null && root.isAttached && coordinates.isAttached) {
                        highEnergyHiddenBounds = root.localBoundingBoxOf(coordinates, clipBounds = false)
                    }
                })
            }
            BoloPlayerHighEnergyProgress(
                data = highEnergyProgress!!,
                durationMs = durationMs,
                positionFraction = { sliderValue },
                controlsFraction = { controlsReveal },
                shownTrackBounds = { highEnergyTrackBounds },
                hiddenBounds = { highEnergyHiddenBounds },
                modifier = Modifier.matchParentSize().graphicsLayer {
                    alpha = if (settings.playerHighEnergyProgressAlwaysVisible) 1f else controlsReveal
                },
            )
        }
        controlsTransition.AnimatedVisibility(
            visible = { it },
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize().clipToBounds(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .animateEnterExit(
                            enter = slideInVertically(tween(200)) { -it },
                            exit = slideOutVertically(tween(200)) { -it },
                        )
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(controlsInsets) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationButtons()

                    if (isFullscreen) {
                        Text(
                            text = title,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showExtendedControls) {
                        if (!isFullscreen) Spacer(Modifier.weight(1f))
                        IconButton(onClick = { infoOpen = !infoOpen }) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = if (infoOpen) "隐藏播放信息" else "显示播放信息",
                                tint = if (infoOpen) BiliColor.ThemeColor else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = { infoOpen = false; settingsOpen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "播放器设置",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(tween(200)) { it },
                            exit = slideOutVertically(tween(200)) { it },
                        )
                        .onGloballyPositioned {
                            bottomControlsCoordinates = it
                            updateHighEnergyTrackBounds()
                        }
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(controlsInsets) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val sliderColors = SliderDefaults.colors(
                        activeTrackColor = BiliColor.ThemeColor,
                        thumbColor = BiliColor.ThemeColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                    // 下方播放进度条
                    Slider(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        value = sliderValue,
                        valueRange = 0f..1f,
                        enabled = durationMs > 0L && playState.isSeekable,
                        onValueChange = { sliderPreviewFraction = it },
                        onValueChangeFinished = {
                            sliderPreviewFraction?.let { fraction ->
                                val targetPositionMs =
                                    (fraction.toDouble() * durationMs.toDouble())
                                        .roundToLong()
                                        .coerceIn(0L, durationMs)
                                viewModel.seekToMs(targetPositionMs, autoPlayAfterSeek = settings.playerAutoPlayAfterSeekEnabled)
                                sliderPreviewFraction = null
                            }
                        },
                        colors = sliderColors,
                        interactionSource = sliderInteractionSource,
                        thumb = {
                            Box(Modifier.size(24.dp)) {
                                SliderDefaults.Thumb(
                                    interactionSource = sliderInteractionSource,
                                    colors = sliderColors.copy(thumbColor = Color.White),
                                    thumbSize = DpSize(12.dp, 12.dp),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                colors = sliderColors,
                                sliderState = sliderState,
                                thumbTrackGapSize = 0.dp,
                                modifier = Modifier.height(4.dp)
                                    .onGloballyPositioned {
                                        progressTrackCoordinates = it
                                        updateHighEnergyTrackBounds()
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        for (boundary in chapterBoundaries) {
                                            val fraction = (boundary.toDouble() / durationMs).toFloat()
                                            val x = size.width * if (layoutDirection == LayoutDirection.Rtl) {
                                                1f - fraction
                                            } else fraction
                                            with(SliderDefaults) {
                                                drawStopIndicator(
                                                    offset = Offset(x, size.height / 2f),
                                                    size = TrackStopIndicatorSize,
                                                    color = if (boundary <= displayedPositionMs) Color.White else BiliColor.ThemeColor,
                                                )
                                            }
                                        }
                                    },
                            )
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // 下方播放控件
                    PlayerBottomControlsRow(
                        collapseControls = showExtendedControls && !isFullscreen,
                    ) { hiddenControls ->
                        if (showExtendedControls && onPreviousEpisode != null) {
                            IconButton(
                                onClick = onPreviousEpisode,
                                enabled = episodeNavigationEnabled,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "上一集",
                                    tint = Color.White.copy(alpha = if (episodeNavigationEnabled) 1f else 0.38f),
                                )
                            }
                        }

                        // 播放按钮
                        IconButton(
                            onClick = { togglePlayback() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (playState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playState.isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        if (showExtendedControls && onNextEpisode != null) {
                            IconButton(
                                onClick = onNextEpisode,
                                enabled = episodeNavigationEnabled,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "下一集",
                                    tint = Color.White.copy(alpha = if (episodeNavigationEnabled) 1f else 0.38f),
                                )
                            }
                        }

                        // 时间显示
                        Text(
                            text = "${displayedPositionMs.formatPlayerDuration()} / " +
                                playState.durationMs.formatPlayerDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )

                        if (visibleChapters.isNotEmpty()) {
                            key(viewModel, viewModel.avid, viewModel.cid, viewModel.episodeId, isFullscreen) {
                                ChapterMenu(
                                    chapters = visibleChapters,
                                    currentChapter = currentChapter,
                                    seekEnabled = playState.isSeekable && !playState.isPlaybackSuspended,
                                    onChapterSelected = {
                                        viewModel.seekToMs(it.startMs, autoPlayAfterSeek = settings.playerAutoPlayAfterSeekEnabled)
                                    },
                                    onExpandedChange = { chapterMenuOpen = it },
                                    modifier = Modifier.layoutId("chapter"),
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.setDanmakuVisible(!viewModel.danmakuController.state.value.isVisible)
                            },
                            enabled = !danmakuClosed,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.38f),
                            ),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                text = when {
                                    danmakuClosed -> "UP已关闭弹幕"
                                    danmakuState.isVisible -> "弹幕 - 开"
                                    else -> "弹幕 - 关"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }

                        if (viewModel.canSendDanmaku) {
                            TextButton(
                                onClick = viewModel::openDanmakuInput,
                                enabled = viewModel.danmakuInputEnabled,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                                ),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text("发送弹幕", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }

                        Spacer(Modifier.layoutId("spacer"))

                        if (showExtendedControls && subtitleState.subtitles.isNotEmpty()) {
                            Box(
                                Modifier.layoutId("subtitle")
                                    .focusProperties { canFocus = "subtitle" !in hiddenControls }
                                    .then(if ("subtitle" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier),
                            ) {
                                SubtitleMenu(
                                    visible = "subtitle" !in hiddenControls,
                                    subtitles = subtitleState.subtitles,
                                    selectedSubtitle = subtitleState.selected,
                                    onSubtitleSelected = viewModel.subtitleController::loadSubtitleContent,
                                    onExpandedChange = { subtitleMenuOpen = it },
                                )
                            }
                        }

                        if (isDesktop) {
                            Box(
                                Modifier.layoutId("volume")
                                    .focusProperties { canFocus = "volume" !in hiddenControls }
                                    .then(if ("volume" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier),
                            ) {
                                VolumeSliderPopup(
                                    visible = "volume" !in hiddenControls,
                                    volumePercent = settings.playerDesktopVolumePercent,
                                    muted = settings.playerDesktopMuted,
                                    isFullscreen = isFullscreen,
                                    onVolumeSelected = {
                                        viewModel.setDesktopVolume(it)
                                        showDesktopVolumeFeedback()
                                    },
                                    onKeyEvent = ::onVolumeKeyEvent,
                                    onScroll = { delta -> adjustDesktopVolume(if (delta < 0f) 2 else -2) },
                                    onExpandedChange = { volumeMenuOpen = it },
                                )
                            }
                        }

                        if (showExtendedControls) {
                            Box(
                                Modifier.layoutId("speed")
                                    .focusProperties { canFocus = "speed" !in hiddenControls }
                                    .then(if ("speed" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier),
                            ) {
                                SpeedSliderPopup(
                                    visible = "speed" !in hiddenControls,
                                    currentSpeed = keyboardSpeedBoost ?: gestureSpeedBoost ?: playState.playbackSpeed,
                                    isFullscreen = isFullscreen,
                                    onSpeedSelected = viewModel.controller::setPlaybackSpeed,
                                    onExpandedChange = { speedMenuOpen = it },
                                )
                            }

                            if (videoQualities.isNotEmpty()) {
                                QualityMenu(
                                    qualities = videoQualities,
                                    currentQuality = currentVideoQuality,
                                    onQualitySelected = viewModel::switchQuality,
                                    onExpandedChange = { qualityMenuOpen = it },
                                )
                            }

                            if (!settings.playerHideAudioQualitySelectorEnabled && audioQualities.isNotEmpty()) {
                                Box(
                                    Modifier.layoutId("audio")
                                        .focusProperties { canFocus = "audio" !in hiddenControls }
                                        .then(if ("audio" in hiddenControls) Modifier.clearAndSetSemantics {} else Modifier),
                                ) {
                                    AudioQualityMenu(
                                        qualities = audioQualities,
                                        currentQuality = currentAudioQuality,
                                        visible = "audio" !in hiddenControls,
                                        onQualitySelected = viewModel::switchAudioQuality,
                                        onExpandedChange = { audioQualityMenuOpen = it },
                                    )
                                }
                            }
                        }

                        if (fullscreenState.isDesktop) {
                            IconButton(
                                onClick = fullscreenState::toggleWindowFullscreen,
                                modifier = Modifier.size(32.dp).semantics {
                                    contentDescription = if (isFullscreen) "退出窗口全屏" else "窗口全屏"
                                },
                            ) {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CropSquare,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (!fullscreenState.isManualSystemFullscreen) {
                            val isSystemFullscreen = if (isDesktop) fullscreenState.isSystemFullscreen else isFullscreen
                            IconButton(
                                onClick = fullscreenState::toggleFullscreen,
                                enabled = !fullscreenState.isChangingSystemFullscreen,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSystemFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = if (isSystemFullscreen) "退出全屏" else "全屏",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
        val gestureSpeedPreview = keyboardSpeedBoost ?: gestureSpeedBoost
        val centerFeedback = when {
            gestureSpeedPreview != null -> Icons.Rounded.FastForward to "长按快进"
            previewPositionMs != null -> if (previewPositionMs < playState.displayPositionMs) {
                Icons.Rounded.FastRewind to "快退"
            } else {
                Icons.Rounded.FastForward to "快进"
            }
            actionFeedback != null -> actionFeedback
            else -> null
        }
        val feedbackVisibility = remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
            MutableTransitionState(false)
        }
        var retainedFeedback by remember(viewModel, title, playerUiState, currentVideoQuality, currentAudioQuality, isFullscreen) {
            mutableStateOf<Pair<ImageVector, String>?>(null)
        }
        SideEffect {
            if (centerFeedback != null) retainedFeedback = centerFeedback
            feedbackVisibility.targetState = centerFeedback != null && !deviceFeedbackVisible && !settingsOpen
        }
        if (playState.isBuffering && !settingsOpen && !deviceFeedbackVisible &&
            centerFeedback == null && feedbackVisibility.isIdle && !feedbackVisibility.currentState) {
            BoloPlayerBufferingIndicator(
                downloadBytesPerSecond = playerInfo.downloadBytesPerSecond,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        AnimatedVisibility(
            visibleState = feedbackVisibility,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            (centerFeedback ?: retainedFeedback)?.let { feedback ->
                Icon(
                    imageVector = feedback.first,
                    contentDescription = feedback.second,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White,
                )
            }
        }
        if (deviceFeedbackVisible || previewPositionMs != null || gestureSpeedPreview != null) {
            Card(
                modifier = Modifier.align(Alignment.Center)
                    .then(if (!deviceFeedbackVisible) Modifier.offset(y = 52.dp) else Modifier),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                ),
            ) {
                if (deviceFeedbackVisible) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when {
                                desktopVolumeFeedbackVisible -> desktopVolumeIcon
                                brightnessPreview != null -> Icons.Rounded.Brightness6
                                devicePreview != null && devicePreview <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = if (brightnessPreview != null) "亮度" else "音量",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = if (desktopVolumeFeedbackVisible) desktopVolumeText
                                else "${((devicePreview ?: 0f).coerceIn(0f, 1f) * 100).roundToInt()}%",
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                } else if (previewPositionMs != null) {
                    Text(
                        text = "${previewPositionMs.formatPlayerDuration()} / ${playState.durationMs.formatPlayerDuration()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                } else if (gestureSpeedPreview != null) {
                    Text(
                        text = speedMultiplierText((gestureSpeedPreview * 100f).roundToInt()),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
        if (danmakuInputOpen) {
            ShowReplyInput(
                text = viewModel.danmakuDraft,
                labelText = "发送弹幕",
                onSend = viewModel::sendDanmaku,
                onDismiss = { viewModel.dismissDanmakuInput() },
                maxLength = viewModel.danmakuMaxLength,
                sendContentDescription = "发送弹幕",
                sendEnabled = viewModel.danmakuSubmitEnabled,
                errorText = danmakuSendError,
            )
        }
        if (showExtendedControls) {
            BoloPlayerSettingsSheet(
                resumeAfterBackgroundEnabled = settings.playerResumeAfterBackgroundEnabled,
                onResumeAfterBackgroundEnabledChange = { settings.playerResumeAfterBackgroundEnabled = it },
                autoPlayAfterSeekEnabled = settings.playerAutoPlayAfterSeekEnabled,
                onAutoPlayAfterSeekEnabledChange = { settings.playerAutoPlayAfterSeekEnabled = it },
                highEnergyProgressEnabled = settings.playerHighEnergyProgressEnabled,
                onHighEnergyProgressEnabledChange = { settings.playerHighEnergyProgressEnabled = it },
                highEnergyProgressAlwaysVisible = settings.playerHighEnergyProgressAlwaysVisible,
                onHighEnergyProgressAlwaysVisibleChange = { settings.playerHighEnergyProgressAlwaysVisible = it },
                mergeAudioChannelsEnabled = settings.playerMergeAudioChannelsEnabled,
                onMergeAudioChannelsEnabledChange = { settings.playerMergeAudioChannelsEnabled = it },
                rebuildEnabled = !playState.isRebuilding && !playState.isPlaybackSuspended,
                onRebuild = {
                    viewModel.controller.rebuild()
                    settingsOpen = false
                    controlsVisible = true
                },
                singleEpisodeLoopEnabled = viewModel.singleEpisodeLoopEnabled,
                onSingleEpisodeLoopEnabledChange = { viewModel.singleEpisodeLoopEnabled = it },
                isOpen = settingsOpen,
                supportsDeviceGestures = deviceControls.supportsDeviceGestures,
                desktopDoubleClickPauseEnabled = desktopDoubleClickPauseEnabled,
                onDesktopDoubleClickPauseEnabledChange = { settings.playerDesktopDoubleClickPauseEnabled = it },
                desktopDefaultWindowFullscreenEnabled = desktopDefaultWindowFullscreenEnabled,
                onDesktopDefaultWindowFullscreenEnabledChange = { settings.playerDesktopDefaultWindowFullscreenEnabled = it },
                desktopFastForwardHoldSpeedEnabled = desktopFastForwardHoldSpeedEnabled,
                onDesktopFastForwardHoldSpeedEnabledChange = { settings.playerDesktopFastForwardHoldSpeedEnabled = it },
                seekGestureEnabled = seekGestureEnabled,
                onSeekGestureEnabledChange = { settings.playerSeekGestureEnabled = it },
                brightnessGestureEnabled = brightnessGestureEnabled,
                onBrightnessGestureEnabledChange = { settings.playerBrightnessGestureEnabled = it },
                volumeGestureEnabled = volumeGestureEnabled,
                onVolumeGestureEnabledChange = { settings.playerVolumeGestureEnabled = it },
                sideDoubleTapSeekEnabled = settings.playerSideDoubleTapSeekEnabled,
                onSideDoubleTapSeekEnabledChange = { settings.playerSideDoubleTapSeekEnabled = it },
                doubleTapSeekSeconds = settings.playerDoubleTapSeekSeconds,
                onDoubleTapSeekSecondsChange = { settings.playerDoubleTapSeekSeconds = it },
                longPressSpeedGestureEnabled = longPressSpeedGestureEnabled,
                onLongPressSpeedGestureEnabledChange = { settings.playerLongPressSpeedGestureEnabled = it },
                longPressSpeed = longPressSpeed,
                onLongPressSpeedChange = { settings.playerLongPressSpeed = it },
                danmakuFilterLevel = settings.danmakuFilterLevel,
                onDanmakuFilterLevelChange = { settings.danmakuFilterLevel = it },
                danmakuScale = settings.danmakuScale,
                onDanmakuScaleChange = { settings.danmakuScale = it },
                danmakuSpeed = settings.danmakuSpeed,
                onDanmakuSpeedChange = { settings.danmakuSpeed = it },
                danmakuDisplayAreaRatio = settings.danmakuDisplayAreaRatio,
                onDanmakuDisplayAreaRatioChange = { settings.danmakuDisplayAreaRatio = it },
                danmakuTopBottomScrollEnabled = settings.danmakuTopBottomScrollEnabled,
                onDanmakuTopBottomScrollEnabledChange = { settings.danmakuTopBottomScrollEnabled = it },
                danmakuExtraLineSpacingEnabled = settings.danmakuExtraLineSpacingEnabled,
                onDanmakuExtraLineSpacingEnabledChange = { settings.danmakuExtraLineSpacingEnabled = it },
                danmakuScrollEnabled = settings.danmakuScrollEnabled,
                onDanmakuScrollEnabledChange = { settings.danmakuScrollEnabled = it },
                danmakuTopEnabled = settings.danmakuTopEnabled,
                onDanmakuTopEnabledChange = { settings.danmakuTopEnabled = it },
                danmakuBottomEnabled = settings.danmakuBottomEnabled,
                onDanmakuBottomEnabledChange = { settings.danmakuBottomEnabled = it },
                subtitleScale = settings.subtitleScale,
                onSubtitleScaleChange = { settings.subtitleScale = it },
                onSubtitleScalePreview = settings::previewSubtitleScale,
                subtitleHeightRatio = settings.subtitleHeightRatio,
                onSubtitleHeightRatioChange = { settings.subtitleHeightRatio = it },
                onSubtitleHeightRatioPreview = settings::previewSubtitleHeightRatio,
                subtitleBackgroundAlpha = settings.subtitleBackgroundAlpha,
                onSubtitleBackgroundAlphaChange = { settings.subtitleBackgroundAlpha = it },
                onSubtitleBackgroundAlphaPreview = settings::previewSubtitleBackgroundAlpha,
                onDismissRequest = {
                    settingsOpen = false
                    controlsVisible = true
                },
            )
        }
    }
}

private fun Long.formatPlayerDuration(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        if (hours > 0L) {
            append(hours.toString().padStart(2, '0'))
            append(':')
        }
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}

@Composable
private fun PlayerBottomControlsRow(
    collapseControls: Boolean,
    content: @Composable (Set<String>) -> Unit,
) {
    var hiddenControls by remember { mutableStateOf(emptySet<String>()) }
    Layout(
        content = { content(hiddenControls) },
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
    ) { measurables, constraints ->
        // 隐藏项仍以自然宽度参与测量，恢复时不依赖上一次显示结果。
        val chapterIndex = measurables.indexOfFirst { it.layoutId == "chapter" }
        val otherPlaceables = measurables.mapIndexed { index, measurable ->
            if (index == chapterIndex) null else measurable.measure(Constraints(maxHeight = constraints.maxHeight))
        }
        val chapterNaturalWidth = if (chapterIndex >= 0) {
            measurables[chapterIndex].maxIntrinsicWidth(constraints.maxHeight)
                .coerceIn(64.dp.roundToPx(), 160.dp.roundToPx())
        } else 0
        val otherWidth = otherPlaceables.sumOf { it?.width ?: 0 }
        val chapterWidth = if (chapterIndex >= 0) {
            (constraints.maxWidth - otherWidth).coerceIn(64.dp.roundToPx(), chapterNaturalWidth)
        } else 0
        val widths = measurables.mapIndexed { index, measurable ->
            (measurable.layoutId as? String) to (otherPlaceables[index]?.width ?: chapterWidth)
        }
        val hidden = if (collapseControls) {
            hiddenPlayerControls(widths, constraints.maxWidth)
        } else emptySet()
        hiddenControls = hidden
        val placeables = measurables.mapIndexed { index, measurable ->
            otherPlaceables[index] ?: measurable.measure(
                Constraints(maxWidth = chapterWidth, maxHeight = constraints.maxHeight),
            )
        }
        val visibleIndices = measurables.indices.filter { widths[it].first !in hidden }
        val contentWidth = visibleIndices.sumOf { placeables[it].width }
        val width = constraints.constrainWidth(contentWidth)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        val spacerWidth = (width - contentWidth).coerceAtLeast(0)
        layout(width, height) {
            var x = 0
            for (index in visibleIndices) {
                val placeable = placeables[index]
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width
                if (widths[index].first == "spacer") x += spacerWidth
            }
        }
    }
}

private fun hiddenPlayerControls(
    widths: List<Pair<String?, Int>>,
    availableWidth: Int,
): Set<String> {
    val hidden = mutableSetOf<String>()
    var requiredWidth = widths.sumOf { it.second }
    for (id in listOf("audio", "subtitle", "volume", "speed")) {
        if (requiredWidth <= availableWidth) break
        val width = widths.firstOrNull { it.first == id }?.second ?: continue
        hidden.add(id)
        requiredWidth -= width
    }
    return hidden
}

@Composable
private fun ChapterMenu(
    chapters: List<PlayerChapterData>,
    currentChapter: PlayerChapterData?,
    seekEnabled: Boolean,
    onChapterSelected: (PlayerChapterData) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(chapters) { mutableStateOf(false) }
    var openingIndex by remember(chapters) { mutableIntStateOf(0) }
    var rowHeights by remember(chapters) { mutableStateOf(List(chapters.size) { 0 }) }
    val scrollState = rememberScrollState()
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded) {
        latestOnExpandedChange(expanded)
        onDispose { latestOnExpandedChange(false) }
    }
    LaunchedEffect(expanded, rowHeights) {
        if (expanded && rowHeights.all { it > 0 }) {
            scrollState.scrollTo(rowHeights.take(openingIndex).sum())
        }
    }
    Box(modifier.widthIn(max = 160.dp)) {
        TextButton(
            onClick = {
                openingIndex = chapters.indexOf(currentChapter).coerceAtLeast(0)
                expanded = true
            },
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = currentChapter?.let { "章节 · ${it.title}" } ?: "章节",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 320.dp),
            scrollState = scrollState,
        ) {
            chapters.forEachIndexed { index, chapter ->
                DropdownMenuItem(
                    text = {
                        Text(
                            chapter.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Text(chapter.startMs.formatPlayerDuration(), style = MaterialTheme.typography.labelMedium) },
                    trailingIcon = {
                        if (chapter == currentChapter) Icon(Icons.Rounded.Check, contentDescription = "当前章节")
                    },
                    enabled = seekEnabled,
                    onClick = {
                        expanded = false
                        onChapterSelected(chapter)
                    },
                    modifier = Modifier.onSizeChanged { size ->
                        if (rowHeights[index] != size.height) {
                            rowHeights = rowHeights.toMutableList().also { it[index] = size.height }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeedSliderPopup(
    visible: Boolean,
    currentSpeed: Float,
    isFullscreen: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    var lastRequestedSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }
    val speedText = speedMultiplierText((currentSpeed * 100f).roundToInt())
    PlayerSliderPopup(
        visible = visible,
        isFullscreen = isFullscreen,
        onExpandedChange = onExpandedChange,
        anchor = { toggle ->
            TextButton(
                onClick = toggle,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                modifier = Modifier.height(32.dp).semantics { contentDescription = "播放速度" },
            ) {
                Text(speedText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        },
    ) {
        Text(
            text = speedText,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        ShowSlider(
            value = currentSpeed,
            onValueChange = { value ->
                val speed = (value * 4f).roundToInt().coerceIn(2, 12) / 4f
                if (speed != lastRequestedSpeed) {
                    lastRequestedSpeed = speed
                    onSpeedSelected(speed)
                }
            },
            valueRange = 0.5f..3f,
            steps = 9,
            uniformTrackColor = true,
            showStops = false,
            tickValues = listOf(0.5f, 1f, 2f, 3f),
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "播放速度"
                stateDescription = speedText
            },
        )
        IconButton(
            onClick = {
                if (lastRequestedSpeed != 1f) {
                    lastRequestedSpeed = 1f
                    onSpeedSelected(1f)
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = "重置播放速度为 1x",
                modifier = Modifier.size(20.dp).graphicsLayer {
                    // 24×24 路径的圆环中心为 (12, 13)，向上补偿一个矢量单位。
                    translationY = -size.height / 24f
                },
            )
        }
    }
}

@Composable
private fun VolumeSliderPopup(
    visible: Boolean,
    volumePercent: Int,
    muted: Boolean,
    isFullscreen: Boolean,
    onVolumeSelected: (Int) -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    onScroll: (Float) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    val volumeText = if (muted) "已静音" else "$volumePercent%"
    val latestOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val latestOnScroll by rememberUpdatedState(onScroll)
    PlayerSliderPopup(
        visible = visible,
        isFullscreen = isFullscreen,
        onExpandedChange = onExpandedChange,
        requestInitialFocus = true,
        popupModifier = Modifier.onPreviewKeyEvent { latestOnKeyEvent(it) }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (delta != 0f) {
                            latestOnScroll(delta)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        },
        anchor = { toggle ->
            IconButton(
                onClick = toggle,
                modifier = Modifier.size(32.dp).semantics { stateDescription = volumeText },
            ) {
                Icon(
                    imageVector = if (muted || volumePercent == 0) Icons.AutoMirrored.Rounded.VolumeOff
                        else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "播放器音量",
                    tint = Color.White,
                )
            }
        },
    ) {
        Text(
            text = volumeText,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = "重置音量为 100%",
                modifier = Modifier.size(20.dp).graphicsLayer {
                    translationY = -size.height / 24f
                },
            )
        }
    }
}

@Composable
private fun PlayerSliderPopup(
    visible: Boolean,
    isFullscreen: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    popupModifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
    anchor: @Composable (() -> Unit) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var expanded by remember(isFullscreen) { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded, visible) {
        latestOnExpandedChange(expanded && visible)
        onDispose { latestOnExpandedChange(false) }
    }
    val visibility = remember(isFullscreen) { MutableTransitionState(false) }
    visibility.targetState = expanded && visible
    val transformOrigin = TransformOrigin(
        pivotFractionX = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else 0f,
        pivotFractionY = 1f,
    )
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
                val x = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else anchorBounds.left
                val y = anchorBounds.top - popupContentSize.height - margin
                return IntOffset(
                    x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
                )
            }
        }
    }
    Box {
        anchor { expanded = !expanded }
        if (visible && (visibility.currentState || visibility.targetState || !visibility.isIdle)) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                LaunchedEffect(visibility.targetState, requestInitialFocus) {
                    if (visibility.targetState && requestInitialFocus) focusRequester.requestFocus()
                }
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(180)) + scaleIn(
                        animationSpec = tween(180),
                        initialScale = 0.92f,
                        transformOrigin = transformOrigin,
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(
                        animationSpec = tween(120),
                        targetScale = 0.92f,
                        transformOrigin = transformOrigin,
                    ),
                ) {
                    Card(
                        shape = RoundedCornerShape(percent = 50),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = popupModifier.then(
                            if (requestInitialFocus) Modifier.focusRequester(focusRequester).focusable() else Modifier,
                        ).widthIn(max = (windowWidth - 16.dp).coerceAtLeast(1.dp)).width(320.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityMenu(
    qualities: List<VideoQuality>,
    currentQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded) {
        latestOnExpandedChange(expanded)
        onDispose { latestOnExpandedChange(false) }
    }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Text(
                text = currentQuality.shortTitle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            qualities.forEach { quality ->
                val selected = quality == currentQuality
                DropdownMenuItem(
                    text = { Text(quality.title) },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前画质"
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!selected) {
                            onQualitySelected(quality)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioQualityMenu(
    qualities: List<AudioQuality>,
    currentQuality: AudioQuality?,
    visible: Boolean,
    onQualitySelected: (AudioQuality) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded, visible) {
        latestOnExpandedChange(expanded && visible)
        onDispose { latestOnExpandedChange(false) }
    }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Text(
                text = currentQuality?.shortTitle ?: "音质",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = expanded && visible,
            onDismissRequest = { expanded = false }
        ) {
            qualities.forEach { quality ->
                val selected = quality == currentQuality
                DropdownMenuItem(
                    text = { Text(quality.title) },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前音质"
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!selected) {
                            onQualitySelected(quality)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SubtitleMenu(
    visible: Boolean,
    subtitles: List<SubtitleItem>,
    selectedSubtitle: SubtitleItem?,
    onSubtitleSelected: (SubtitleItem?) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember(subtitles) { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded, visible) {
        latestOnExpandedChange(expanded && visible)
        onDispose { latestOnExpandedChange(false) }
    }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = "字幕 - ${selectedSubtitle?.displayName ?: "关"}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded && visible, onDismissRequest = { expanded = false }) {
            (listOf<SubtitleItem?>(null) + subtitles).forEach { subtitle ->
                DropdownMenuItem(
                    text = { Text(subtitle?.displayName ?: "关") },
                    trailingIcon = {
                        if (subtitle == selectedSubtitle) {
                            Icon(Icons.Rounded.Check, contentDescription = "当前字幕")
                        }
                    },
                    onClick = {
                        expanded = false
                        onSubtitleSelected(subtitle)
                    },
                )
            }
        }
    }
}
