@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val host = remember { UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller, host) {
        onDispose { controller.outputDetached(host) }
    }
    LaunchedEffect(backend, host) { backend?.bind(host); controller.outputAttached() }
    UIKitView(factory = { host }, modifier = modifier)
}
