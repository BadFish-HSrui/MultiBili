package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 视频播放器 Composable — 通过 expect/actual 在各平台有对应底层渲染实现。
 *
 * @param controller 播放器输出控制器
 * @param modifier   布局修饰符
 */
@Composable
expect fun BoloVideoPlayer(
    controller: BoloPlayerOutputController,
    modifier: Modifier = Modifier
)
