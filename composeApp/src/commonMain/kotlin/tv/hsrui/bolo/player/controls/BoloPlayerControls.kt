package tv.hsrui.bolo.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.subtitle.SubtitleItem
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoloPlayerControls(
    videoInfo: VideoInfoData,
    viewModel: VideoPlayerViewModel,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceControls = rememberPlayerDeviceControls()
    val navigator: Navigator = koinInject()
    val settings: BoloSettings = koinInject()
    val hapticFeedback = LocalHapticFeedback.current
    val seekGestureEnabled = settings.playerSeekGestureEnabled
    val brightnessGestureEnabled = settings.playerBrightnessGestureEnabled
    val volumeGestureEnabled = settings.playerVolumeGestureEnabled
    val longPressSpeedGestureEnabled = settings.playerLongPressSpeedGestureEnabled
    val longPressSpeed = settings.playerLongPressSpeed
    val playState by viewModel.controller.state.collectAsState()
    val playerInfo by viewModel.controller.info.collectAsState()
    val playerUiState by viewModel.uiState.collectAsState()
    val subtitleState by viewModel.subtitleController.state.collectAsState()
    val currentVideoQuality by viewModel.currentVideoQuality.collectAsState()
    var sliderPreviewFraction by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen) {
        mutableStateOf<Float?>(null)
    }
    var controlsVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember(isFullscreen) { mutableStateOf(false) }
    var infoOpen by remember(viewModel, isFullscreen) { mutableStateOf(false) }
    DisposableEffect(viewModel, infoOpen) {
        viewModel.controller.setInfoPanelVisible(infoOpen)
        onDispose { viewModel.controller.setInfoPanelVisible(false) }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isFullscreen && infoOpen,
        onBackCompleted = { infoOpen = false },
    )
    val latestPlayState by rememberUpdatedState(playState)
    var gesturePreviewMs by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Long?>(null)
    }
    var brightnessPreview by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    var volumePreview by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    // 长按倍速期间的状态：临时倍速用于预览，原倍速用于松手恢复。
    var gestureSpeedBoost by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    var gestureBaseSpeed by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen, settingsOpen) {
        mutableStateOf<Float?>(null)
    }
    val restoreBaseSpeedOnDispose by rememberUpdatedState(gestureBaseSpeed)
    DisposableEffect(viewModel) {
        onDispose {
            // 全屏切换或离开播放器时手势循环会被取消，兜底恢复长按前的倍速。
            restoreBaseSpeedOnDispose?.let { viewModel.controller.setPlaybackSpeed(it) }
        }
    }
    val devicePreview = brightnessPreview ?: volumePreview
    val durationMs = playState.durationMs
    val previewPositionMs = gesturePreviewMs ?: sliderPreviewFraction?.let { fraction ->
        (fraction.toDouble() * durationMs.toDouble())
            .roundToLong()
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
    val displayedPositionMs = previewPositionMs ?: playState.displayPositionMs
    val videoQualities = (playerUiState as? VideoPlayerUiState.Success)
        ?.videoSource
        ?.videoQualities
        .orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        // 背景独立命中：上层按钮、滑块与面板不会把事件传给手势层。
        Box(
            Modifier.fillMaxSize()
                .pointerInput(
                    viewModel,
                    videoInfo,
                    playerUiState,
                    settingsOpen,
                    isFullscreen,
                    longPressSpeedGestureEnabled,
                    longPressSpeed,
                    settings.playerSideDoubleTapSeekEnabled,
                    settings.playerDoubleTapSeekSeconds,
                ) {
                    if (settingsOpen) return@pointerInput
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
                                    } else {
                                        if (playback.isPlaying) viewModel.pause() else viewModel.play()
                                    }
                                } else {
                                    if (playback.isPlaying) viewModel.pause() else viewModel.play()
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
                    videoInfo,
                    playerUiState,
                    currentVideoQuality,
                    isFullscreen,
                    settingsOpen,
                    deviceControls,
                    seekGestureEnabled,
                    brightnessGestureEnabled,
                    volumeGestureEnabled,
                ) {
                    if (!isFullscreen || settingsOpen) return@pointerInput
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
        if (isFullscreen && infoOpen) {
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                BoloPlayerInfoPanel(
                    info = playerInfo,
                    onClose = { infoOpen = false },
                    modifier = Modifier.align(Alignment.TopStart).heightIn(max = maxHeight),
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
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


                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .animateEnterExit(
                            enter = slideInVertically(tween(200)) { -it },
                            exit = slideOutVertically(tween(200)) { -it },
                        )
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 导航按钮
                    IconButton(
                        onClick = {
                            if (infoOpen) {
                                infoOpen = false
                            } else if (isFullscreen) {
                                onFullscreenChange(false)
                            } else {
                                navigator.goBack()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = if (infoOpen) "关闭播放信息" else if (isFullscreen) "退出全屏" else "返回",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    if (!isFullscreen && navigator.currentDepth > 1) {
                        IconButton(
                            onClick = { navigator.goHome() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = "回到主页",
                                tint = Color.White
                            )
                        }
                    }

                    if (isFullscreen) {
                        Text(
                            text = videoInfo.title,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    val sliderInteractionSource = remember { MutableInteractionSource() }
                    val sliderColors = SliderDefaults.colors(
                        activeTrackColor = BiliColor.ThemeColor,
                        thumbColor = BiliColor.ThemeColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                    val sliderValue = if (durationMs > 0L) {
                        (displayedPositionMs.toDouble() / durationMs.toDouble())
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    } else {
                        0f
                    }

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
                            )
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // 下方播放控件
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 播放按钮
                        IconButton(
                            onClick = {
                                if (playState.isPlaying) viewModel.pause() else viewModel.play()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (playState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playState.isPlaying) "暂停" else "播放",
                                tint = Color.White
                            )
                        }

                        // 时间显示
                        Text(
                            text = "${displayedPositionMs.formatPlayerDuration()} / " +
                                playState.durationMs.formatPlayerDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )

                        Spacer(Modifier.weight(1f))

                        if (isFullscreen) {
                            if (subtitleState.subtitles.isNotEmpty()) {
                                SubtitleMenu(
                                    subtitles = subtitleState.subtitles,
                                    selectedSubtitle = subtitleState.selected,
                                    onSubtitleSelected = viewModel.subtitleController::loadSubtitleContent,
                                )
                            }

                            SpeedSliderPopup(
                                currentSpeed = gestureSpeedBoost ?: playState.playbackSpeed,
                                isFullscreen = isFullscreen,
                                onSpeedSelected = viewModel.controller::setPlaybackSpeed
                            )

                            if (videoQualities.isNotEmpty()) {
                                QualityMenu(
                                    qualities = videoQualities,
                                    currentQuality = currentVideoQuality,
                                    onQualitySelected = viewModel::switchQuality
                                )
                            }

                        }

                        IconButton(
                            onClick = { onFullscreenChange(!isFullscreen) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        val gestureSpeedPreview = gestureSpeedBoost
        if (playState.isBuffering && !settingsOpen && devicePreview == null &&
            previewPositionMs == null && gestureSpeedPreview == null) {
            BoloPlayerBufferingIndicator(
                downloadBytesPerSecond = playerInfo.downloadBytesPerSecond,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (devicePreview != null || previewPositionMs != null || gestureSpeedPreview != null) {
            Card(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                ),
            ) {
                if (devicePreview != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when {
                                brightnessPreview != null -> Icons.Rounded.Brightness6
                                devicePreview <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = if (brightnessPreview != null) "亮度" else "音量",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = "${(devicePreview.coerceIn(0f, 1f) * 100).roundToInt()}%",
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
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "长按快进",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = speedMultiplierText((gestureSpeedPreview * 100f).roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        if (isFullscreen) {
            BoloPlayerSettingsSheet(
                resumeAfterBackgroundEnabled = settings.playerResumeAfterBackgroundEnabled,
                onResumeAfterBackgroundEnabledChange = { settings.playerResumeAfterBackgroundEnabled = it },
                autoPlayAfterSeekEnabled = settings.playerAutoPlayAfterSeekEnabled,
                onAutoPlayAfterSeekEnabledChange = { settings.playerAutoPlayAfterSeekEnabled = it },
                mergeAudioChannelsEnabled = settings.playerMergeAudioChannelsEnabled,
                onMergeAudioChannelsEnabledChange = { settings.playerMergeAudioChannelsEnabled = it },
                rebuildEnabled = !playState.isRebuilding && !playState.isPlaybackSuspended,
                onRebuild = {
                    viewModel.controller.rebuild()
                    settingsOpen = false
                    controlsVisible = true
                },
                autoReplayEnabled = settings.playerAutoReplayEnabled,
                onAutoReplayEnabledChange = { settings.playerAutoReplayEnabled = it },
                isOpen = settingsOpen,
                supportsDeviceGestures = deviceControls.supportsDeviceGestures,
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
                subtitleAlwaysOn = settings.subtitleAlwaysOn,
                onSubtitleAlwaysOnChange = { settings.subtitleAlwaysOn = it },
                subtitleAutoChineseOnly = settings.subtitleAutoChineseOnly,
                onSubtitleAutoChineseOnlyChange = { settings.subtitleAutoChineseOnly = it },
                subtitleAutoExcludeAi = settings.subtitleAutoExcludeAi,
                onSubtitleAutoExcludeAiChange = { settings.subtitleAutoExcludeAi = it },
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
private fun SpeedSliderPopup(
    currentSpeed: Float,
    isFullscreen: Boolean,
    onSpeedSelected: (Float) -> Unit,
) {
    var expanded by remember(isFullscreen) { mutableStateOf(false) }
    val visibility = remember(isFullscreen) { MutableTransitionState(false) }
    visibility.targetState = expanded
    val transformOrigin = TransformOrigin(
        pivotFractionX = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else 0f,
        pivotFractionY = 1f,
    )
    var lastRequestedSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }
    val speedText = speedMultiplierText((currentSpeed * 100f).roundToInt())
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
        TextButton(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            modifier = Modifier.height(32.dp).semantics { contentDescription = "播放速度" },
        ) {
            Text(speedText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (visibility.currentState || visibility.targetState || !visibility.isIdle) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
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
                        modifier = Modifier.widthIn(max = (windowWidth - 16.dp).coerceAtLeast(1.dp)).width(320.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                }
            }
        }
    }
}

@Composable
private fun QualityMenu(
    qualities: List<VideoQuality>,
    currentQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
private fun SubtitleMenu(
    subtitles: List<SubtitleItem>,
    selectedSubtitle: SubtitleItem?,
    onSubtitleSelected: (SubtitleItem?) -> Unit,
) {
    var expanded by remember(subtitles) { mutableStateOf(false) }
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
