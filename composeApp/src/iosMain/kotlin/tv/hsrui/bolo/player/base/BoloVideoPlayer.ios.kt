package tv.hsrui.bolo.player.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val state by controller.state.collectAsState()

    // drawable 绑定标记：仅在 view 获得有效 frame 后绑定一次，避免零尺寸初始化
    // 及重复绑定打断 VLC 渲染导致白屏。
    var drawableBound by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose {
            drawableBound = false
            controller.release()
        }
    }

    Box(modifier = modifier) {
        UIKitView(
            factory = {
                val view = UIView()
                view.setAutoresizesSubviews(true)
                view
            },
            update = { view ->
                if (!drawableBound && view.window != null) {
                    drawableBound = true
                    controller.bindDrawable(view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
