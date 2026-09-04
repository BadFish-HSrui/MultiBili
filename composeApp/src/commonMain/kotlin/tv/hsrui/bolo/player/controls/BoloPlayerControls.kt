package tv.hsrui.bolo.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.player.base.BoloPlayerSpeed
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import tv.hsrui.network.feature.video.VideoInfoData
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
    val navigator: Navigator = koinInject()
    val playState by viewModel.controller.state.collectAsState()
    val playerUiState by viewModel.uiState.collectAsState()
    val currentVideoQuality by viewModel.currentVideoQuality.collectAsState()
    var sliderPreviewFraction by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    val videoQualities = (playerUiState as? VideoPlayerUiState.Success)
        ?.videoSource
        ?.videoQualities
        .orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = { controlsVisible = !controlsVisible }
            )
    ) {
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
                    val durationMs = playState.durationMs
                    val sliderValue = sliderPreviewFraction ?: if (durationMs > 0L) {
                        (playState.displayPositionMs.toDouble() / durationMs.toDouble())
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    } else {
                        0f
                    }
                    val previewPositionMs = sliderPreviewFraction?.let { fraction ->
                        (fraction.toDouble() * durationMs.toDouble())
                            .roundToLong()
                            .coerceIn(0L, durationMs.coerceAtLeast(0L))
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
                                viewModel.controller.seekToMs(targetPositionMs)
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
                            if (playState.isPlaying) viewModel.controller.pause() else viewModel.controller.play()
                        }) {
                            Icon(
                                imageVector = if (playState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playState.isPlaying) "暂停" else "播放",
                                tint = Color.White
                            )
                        }

                        // 时间显示
                        Text(
                            text = "${(previewPositionMs ?: playState.displayPositionMs).formatPlayerDuration()} / " +
                                playState.durationMs.formatPlayerDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )

                        Spacer(Modifier.weight(1f))

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
