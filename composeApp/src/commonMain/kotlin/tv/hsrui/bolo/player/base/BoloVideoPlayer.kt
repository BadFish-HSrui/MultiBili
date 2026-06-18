package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 视频播放器 Composable — 通过 expect/actual 在各平台有对应底层渲染实现。
 *
 * @param controller 播放控制器（[BoloPlayerController]）
 * @param modifier   布局修饰符
 */
@Composable
expect fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier = Modifier
)
