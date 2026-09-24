package tv.hsrui.bolo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.CoreGraphics.CGSize
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import tv.hsrui.bolo.player.IosPlayerFullscreenCoordinator
import tv.hsrui.bolo.player.LocalIosPlayerFullscreenCoordinator

@OptIn(ExperimentalNativeApi::class)
internal class StatusBarAppearance {
    private var controllerReference: WeakReference<UIViewController>? = null
    var controller: UIViewController?
        get() = controllerReference?.get()
        set(value) { controllerReference = value?.let(::WeakReference) }
    private var requests = 0
    private var isDarkTheme: Boolean? = null

    val preferredStyle: UIStatusBarStyle?
        get() = when {
            requests > 0 || isDarkTheme == true -> UIStatusBarStyleLightContent
            isDarkTheme == false -> UIStatusBarStyleDarkContent
            else -> null
        }

    fun setTheme(isDarkTheme: Boolean?) {
        if (this.isDarkTheme == isDarkTheme) return
        this.isDarkTheme = isDarkTheme
        controller?.setNeedsStatusBarAppearanceUpdate()
    }

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

@OptIn(ExperimentalForeignApi::class)
private class BoloHostingViewController(
    private val appearance: StatusBarAppearance,
    private val fullscreen: IosPlayerFullscreenCoordinator,
    private val contentController: UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(contentController)
        contentController.view.setFrame(view.bounds)
        contentController.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(contentController.view)
        contentController.didMoveToParentViewController(this)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        appearance.preferredStyle ?: super.preferredStatusBarStyle()

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        fullscreen.geometryChanged()
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        fullscreen.geometryChanged()
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol,
    ) {
        fullscreen.transitionStarted(size)
        super.viewWillTransitionToSize(size, withTransitionCoordinator)
        withTransitionCoordinator.animateAlongsideTransition(
            animation = null,
            completion = { fullscreen.transitionFinished() },
        )
    }
}

fun MainViewController(): UIViewController {
    val appearance = StatusBarAppearance()
    val fullscreen = IosPlayerFullscreenCoordinator()
    val contentController = ComposeUIViewController {
        CompositionLocalProvider(
            LocalStatusBarAppearance provides appearance,
            LocalIosPlayerFullscreenCoordinator provides fullscreen,
        ) { App() }
    }
    val controller = BoloHostingViewController(appearance, fullscreen, contentController)
    appearance.controller = controller
    fullscreen.host = controller
    return controller
}
