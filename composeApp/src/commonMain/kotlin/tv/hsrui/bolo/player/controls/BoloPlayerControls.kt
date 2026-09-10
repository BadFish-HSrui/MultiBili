package tv.hsrui.bolo.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.player.base.BoloPlayerSpeed
import tv.hsrui.bolo.ui.theme.BiliColor
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
    val seekGestureEnabled = settings.playerSeekGestureEnabled
    val brightnessGestureEnabled = settings.playerBrightnessGestureEnabled
    val volumeGestureEnabled = settings.playerVolumeGestureEnabled
    val playState by viewModel.controller.state.collectAsState()
    val playerUiState by viewModel.uiState.collectAsState()
    val subtitleState by viewModel.subtitleController.state.collectAsState()
    val currentVideoQuality by viewModel.currentVideoQuality.collectAsState()
    var sliderPreviewFraction by remember(viewModel, videoInfo, playerUiState, currentVideoQuality, isFullscreen) {
        mutableStateOf<Float?>(null)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember(isFullscreen) { mutableStateOf(false) }
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
                    settings.playerSideDoubleTapSeekEnabled,
                    settings.playerDoubleTapSeekSeconds,
                ) {
                    if (!settingsOpen) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { position ->
                                val direction = when {
                                    position.x < size.width / 3f -> -1
                                    position.x >= size.width * 2f / 3f -> 1
                                    else -> 0
                                }
                                val playback = viewModel.controller.state.value
                                if (
                                    settings.playerSideDoubleTapSeekEnabled &&
                                    direction != 0 &&
                                    playback.isSeekable &&
                                    playback.durationMs > 0L
                                ) {
                                    val offsetMs = direction * settings.playerDoubleTapSeekSeconds * 1_000L
                                    viewModel.seekToMs(
                                        (playback.displayPositionMs + offsetMs)
                                            .coerceIn(0L, playback.durationMs),
                                    )
                                } else if (direction == 0 || !settings.playerSideDoubleTapSeekEnabled) {
                                    if (playback.isPlaying) viewModel.pause()
                                    else viewModel.play()
                                }
                            },
                        )
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
                                    gesturePreviewMs?.let { viewModel.seekToMs(it.coerceIn(0L, latestPlayState.durationMs)) }
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
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
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
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 导航按钮
                    IconButton(
                        onClick = {
                            if (isFullscreen) {
                                onFullscreenChange(false)
                            } else {
                                navigator.goBack()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = if (isFullscreen) "退出全屏" else "返回",
                            tint = Color.White
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
                        IconButton(
                            onClick = { settingsOpen = true },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "播放器设置",
                                tint = Color.White,
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
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
                                viewModel.seekToMs(targetPositionMs)
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

                    // 下方播放控件
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 播放按钮
                        IconButton(onClick = {
                            if (playState.isPlaying) viewModel.pause() else viewModel.play()
                        }) {
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

                            SpeedMenu(
                                currentSpeed = playState.playbackSpeed,
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
                            modifier = Modifier.size(40.dp)
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
        if (devicePreview != null || previewPositionMs != null) {
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
                }
            }
        }
        if (isFullscreen) {
            BoloPlayerSettingsSheet(
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
private fun SpeedMenu(
    currentSpeed: BoloPlayerSpeed,
    onSpeedSelected: (BoloPlayerSpeed) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Text(
                text = currentSpeed.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            BoloPlayerSpeed.entries.forEach { speed ->
                val selected = speed == currentSpeed
                DropdownMenuItem(
                    text = { Text(speed.title) },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前倍速"
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!selected) {
                            onSpeedSelected(speed)
                        }
                    }
                )
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
