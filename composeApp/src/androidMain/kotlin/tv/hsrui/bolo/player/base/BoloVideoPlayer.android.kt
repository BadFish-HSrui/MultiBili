package tv.hsrui.bolo.player.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val boundController = remember { arrayOfNulls<BoloPlayerController>(1) }

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindLifecycle(lifecycleOwner)
        onDispose {
            if (controller.unbindLifecycle(lifecycleOwner)) {
                controller.release()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    layout.layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    boundController[0] = controller
                    controller.bindVideo(layout)
                }
            },
            update = { layout ->
                val previousController = boundController[0]
                if (previousController !== controller) {
                    previousController?.unbindVideo(layout)
                    boundController[0] = controller
                    controller.bindVideo(layout)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { layout ->
                boundController[0]?.unbindVideo(layout)
                boundController[0] = null
            }
        )
    }
}
