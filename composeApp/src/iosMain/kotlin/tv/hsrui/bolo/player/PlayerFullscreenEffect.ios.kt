package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlayerFullscreenEffect(isFullscreen: Boolean) {
    val host = LocalUIViewController.current
    DisposableEffect(host, isFullscreen) {
        var active = true
        val mask = if (isFullscreen) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait
        fun applyOrientation() {
            if (active) requestOrientation(host, mask) { active }
        }
        val windowObserver = object : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
            override fun didMoveToWindow() {
                super.didMoveToWindow()
                if (window != null) {
                    NSOperationQueue.mainQueue.addOperationWithBlock { applyOrientation() }
                }
            }
        }
        windowObserver.userInteractionEnabled = false
        host.view.addSubview(windowObserver)
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UISceneDidActivateNotification, null, NSOperationQueue.mainQueue
        ) { notification ->
            if (notification?.`object` == host.view.window?.windowScene) applyOrientation()
        }
        applyOrientation()
        onDispose {
            active = false
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            windowObserver.removeFromSuperview()
            if (isFullscreen) requestOrientation(host, UIInterfaceOrientationMaskPortrait)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun requestOrientation(
    host: UIViewController,
    orientationMask: ULong,
    isCurrent: () -> Boolean = { true },
) {
    val windowScene = host.view.window?.windowScene ?: return
    if (windowScene.activationState != UISceneActivationStateForegroundActive) return
    host.setNeedsUpdateOfSupportedInterfaceOrientations()
    windowScene.requestGeometryUpdateWithPreferences(
        geometryPreferences = UIWindowSceneGeometryPreferencesIOS(orientationMask),
        errorHandler = { error ->
            if (isCurrent()) println("[PlayerFullscreen] orientation=$orientationMask: ${error?.localizedDescription}")
        }
    )
}
