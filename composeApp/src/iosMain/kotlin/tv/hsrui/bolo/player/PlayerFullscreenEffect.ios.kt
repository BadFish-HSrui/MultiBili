package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

@Composable
actual fun PlayerFullscreenEffect(isFullscreen: Boolean) {
    DisposableEffect(isFullscreen) {
        if (!isFullscreen) {
            onDispose {}
        } else {
            requestOrientation(UIInterfaceOrientationMaskLandscape)
            onDispose {
                requestOrientation(UIInterfaceOrientationMaskPortrait)
            }
        }
    }
}

private fun requestOrientation(orientationMask: ULong) {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull()
        ?: return

    windowScene.requestGeometryUpdateWithPreferences(
        geometryPreferences = UIWindowSceneGeometryPreferencesIOS(orientationMask),
        errorHandler = null
    )
}
