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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.utils.formatToDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoloPlayerControls(
    videoInfo: VideoInfoData,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val navigator: Navigator = koinInject()
    val playState by viewModel.controller.state.collectAsState()
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }

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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 导航按钮
                    IconButton(
                        onClick = { navigator.goBack() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }

                    if (navigator.currentDepth > 1) {
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

                    // TODO: 接入全屏状态后，仅在全屏播放时显示视频名称。
                    // Text(
                    //     text = videoInfo.title,
                    //     modifier = Modifier
                    //         .padding(start = 8.dp)
                    //         .weight(1f),
                    //     style = MaterialTheme.typography.titleMedium,
                    //     color = Color.White,
                    //     maxLines = 1,
                    //     overflow = TextOverflow.Ellipsis
                    // )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    val sliderInteractionSource = remember { MutableInteractionSource() }
                    val sliderColors = SliderDefaults.colors(
                        activeTrackColor = BiliColor.ThemeColor,
                        thumbColor = BiliColor.ThemeColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )

                    // 下方播放进度条
                    Slider(
                        value = sliderPosition ?: playState.currentPosition.toFloat(),
                        valueRange = 0f..playState.duration.toFloat(),
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            sliderPosition?.let { targetPosition ->
                                viewModel.controller.seekTo(targetPosition.toInt())
                                sliderPosition = null
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
                            text = "${playState.currentPosition.formatToDuration()} / ${playState.duration.formatToDuration()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
