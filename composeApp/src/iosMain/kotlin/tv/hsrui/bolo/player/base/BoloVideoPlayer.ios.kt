package tv.hsrui.bolo.player.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // 控制器按 UIView 身份去重绑定，并在前台且窗口有效后恢复输出。

    DisposableEffect(controller) {
        onDispose {
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
                if (view.window != null) {
                    controller.bindDrawable(view)
                }
            },
            onRelease = { view -> controller.unbindDrawable(view) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
