package tv.hsrui.bolo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.addChildViewController
import platform.UIKit.childViewControllers
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.CoreGraphics.CGSize
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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

    val playerSupportedOrientations get() = fullscreen.supportedOrientations

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
        fullscreen.transitionStarted(size.useContents { width > height })
        super.viewWillTransitionToSize(size, withTransitionCoordinator)
        withTransitionCoordinator.animateAlongsideTransition(
            animation = null,
            completion = { fullscreen.transitionFinished() },
        )
    }
}

// UIKit 的方向 category 在 Kotlin 中不可重写，由 Swift 应用代理按窗口转发。
@OptIn(ExperimentalForeignApi::class)
fun playerSupportedInterfaceOrientations(window: UIWindow?): UIInterfaceOrientationMask {
    fun findHost(controller: UIViewController?): BoloHostingViewController? {
        if (controller is BoloHostingViewController) return controller
        controller?.childViewControllers?.filterIsInstance<UIViewController>()?.forEach { child ->
            findHost(child)?.let { return it }
        }
        return null
    }
    return findHost(window?.rootViewController)?.playerSupportedOrientations
        ?: if (getPlatform().isPhone) UIInterfaceOrientationMaskPortrait else UIInterfaceOrientationMaskAll
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
