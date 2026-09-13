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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIView

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val host = remember { UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
                // BoloMpvView 独立观察同一通知，在进入后台前排空 GL；
                // 也覆盖已从 controller 移除、仍在异步关闭的原生宿主。
                controller.setForeground(false, outputWasReleased = true)
            },
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                controller.backend.value?.resumeRendering()
                controller.setForeground(true)
            },
            center.addObserverForName(UIApplicationDidReceiveMemoryWarningNotification, null, NSOperationQueue.mainQueue) {
                controller.releaseBackgroundResources()
            },
        )
        controller.outputAttached()
        onDispose { observers.forEach(center::removeObserver); controller.release() }
    }
    LaunchedEffect(backend, host) { backend?.bind(host) }
    UIKitView(factory = { host }, modifier = modifier)
}
