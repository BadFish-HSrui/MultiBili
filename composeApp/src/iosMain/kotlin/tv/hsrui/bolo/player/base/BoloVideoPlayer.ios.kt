package tv.hsrui.bolo.player.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val host = LocalUIViewController.current
    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    Box(modifier = modifier) {
        UIKitView(
            factory = { PlayerDrawableView(host) },
            update = { view -> view.bind(controller) },
            onRelease = { view -> view.unbind() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlayerDrawableView(private val host: UIViewController) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private var controller: BoloPlayerController? = null
    private var updateQueued = false
    private var waitingForTransition = false

    fun bind(controller: BoloPlayerController) {
        if (this.controller !== controller) {
            unbind()
            this.controller = controller
        }
        updateBinding()
    }

    fun unbind() {
        controller?.unbindDrawable(this)
        controller = null
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        if (window == null) controller?.unbindDrawable(this) else updateBinding()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // 容器尺寸由 Compose 更新，VLC 自己管理内部视频的宽高比和位置。
        updateBinding()
    }

    private fun updateBinding() {
        if (controller == null || window == null || updateQueued || waitingForTransition) return
        updateQueued = true
        // 不在 UIKit 布局栈中启动 VLC；让方向更新先提交到窗口。
        NSOperationQueue.mainQueue.addOperationWithBlock {
            updateQueued = false
            if (controller != null && window != null) {
                val transition = host.transitionCoordinator
                if (transition != null) {
                    waitingForTransition = true
                    val scheduled = transition.animateAlongsideTransition(null) {
                        waitingForTransition = false
                        updateBinding()
                    }
                    if (!scheduled) waitingForTransition = false
                }
                if (!waitingForTransition) controller?.bindDrawable(this)
            }
        }
    }
}
