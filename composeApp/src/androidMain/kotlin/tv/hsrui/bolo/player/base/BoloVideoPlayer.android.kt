package tv.hsrui.bolo.player.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(controller) {
        controller.bindLifecycle(lifecycle)
        onDispose {
            controller.release()
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
                    controller.bindVideo(layout)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { layout ->
                controller.unbindVideo(layout)
            }
        )
    }
}
