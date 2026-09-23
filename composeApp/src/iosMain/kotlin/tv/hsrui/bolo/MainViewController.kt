package tv.hsrui.bolo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController

internal class StatusBarAppearance {
    var controller: UIViewController? = null
    private var requests = 0

    val preferredStyle: UIStatusBarStyle?
        get() = if (requests > 0) UIStatusBarStyleLightContent else null

    fun acquire() {
        requests++
        if (requests == 1) controller?.setNeedsStatusBarAppearanceUpdate()
    }

    fun release() {
        requests--
        if (requests == 0) controller?.setNeedsStatusBarAppearanceUpdate()
    }
}

internal val LocalStatusBarAppearance = staticCompositionLocalOf<StatusBarAppearance> {
    error("Missing status bar appearance owner")
}

@Suppress("DEPRECATION")
fun MainViewController(): UIViewController {
    val appearance = StatusBarAppearance()
    val controller = ComposeUIViewController(
        configure = {
            delegate = object : ComposeUIViewControllerDelegate {
                override val preferredStatusBarStyle: UIStatusBarStyle?
                    get() = appearance.preferredStyle
            }
        },
        content = {
            CompositionLocalProvider(LocalStatusBarAppearance provides appearance) { App() }
        }
    )
    appearance.controller = controller
    return controller
}
